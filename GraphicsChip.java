/*

MeBoy

Copyright 2005-2007 Bjorn Carlin
http://www.arktos.se/

Based on JavaBoy, COPYRIGHT (C) 2001 Neil Millstone and The Victoria
University of Manchester

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.        

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.
 
 */

import javax.microedition.lcdui.*;


public class GraphicsChip {
	private final int MS_PER_FRAME = 17;

	/** Tile uses the background palette */
	// private final int TILE_BKG = 0;
	
	/** Tile is flipped horizontally */
	private final int TILE_FLIPX = 1; // 0x20 in oam attributes
	
	/** Tile is flipped vertically */
	private final int TILE_FLIPY = 2; // 0x40 in oam attributes
	
	/** Tile uses the first sprite palette */
	private final int TILE_OBJ1 = 4; // reset 0x10 in oam attributes
	
	/** Tile uses the second sprite palette */
	private final int TILE_OBJ2 = 8; // set 0x10 in oam attributes
	
	/** The current contents of the video memory, mapped in at 0x8000 - 0x9FFF */
	private byte[] videoRam;
	
	private byte[][] videoRamBanks; // one for gb, two for gbc
	
	
	/** RGB color values */
	private final int[] colors = { 0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000};
	private int[] gbPalette = new int[12];
	//#if +gbc
	private int[] gbcRawPalette = new int[128];
	private int[] gbcPalette = new int[64];
	//#if
	boolean bgEnabled = true;
	boolean winEnabled = true;
	boolean winEnabledThisFrame = true;
	boolean spritesEnabled = true;
	boolean spritesEnabledThisFrame = true;
	boolean lcdEnabled = true;
	boolean spritePriorityEnabled = true;
	
	
	private Graphics g;
	
	// skipping, timing:
	public int timer;
	private boolean skipping = true; // until graphics is set
	private int skipCount;
	
	// some statistics
	int lastSkipCount;
	
	/** The current frame has finished drawing */
	boolean frameDone = false;
	
	/** Selection of one of two addresses for the BG and Window tile data areas */
	boolean bgWindowDataSelect = true;
	
	/** If true, 8x16 sprites are being used.  Otherwise, 8x8. */
	boolean doubledSprites = false;
	
	/** Selection of one of two address for the BG/win tile map. */
	boolean hiBgTileMapAddress = false;
	boolean hiWinTileMapAddress = false;
	private int tileOffset; // 384 when in vram bank 1 in gbc mode
	private int tileCount; // 384 for gb, 384*2 for gbc
	private int colorCount; // number of "logical" colors = palette indices, 12 for gb, 64 for gbc
	private Dmgcpu cpu;
	
	// Hacks to allow some raster effects to work.  Or at least not to break as badly.
	boolean savedWindowDataSelect = false;
	
	private boolean windowStopped;
	private boolean winEnabledThisLine = false;
	private int windowStopLine = 144;
	private int windowStopX;
	private int windowStopY;
	
	// tiles & image cache
	private Image transparentImage;
	private Image[] tileImage;
	private boolean[] tileReadState; // true if there are any images to be invalidated
	
	private int[] tempPix;
	
	public int left, top;
	
	private boolean screenFilled;
	
	// scaling the screen
	private boolean scale;

	int tileWidth = 8;
	int tileHeight = 8;
	
	// lookup table for fast image decoding
	private static int[] weaveLookup = new int[256];
	static {
		for (int i = 1; i < 256; i++) {
			for (int d = 0; d < 8; d++)
				weaveLookup[i] += ((i >> d) & 1) << (d * 2);
		}
	}
	
	
	/** Create a new GraphicsChip connected to the speicfied CPU */
	public GraphicsChip(Dmgcpu d) {
		cpu = d;
		
		if (cpu.gbcFeatures) { //#if +gbc
			videoRamBanks = new byte[2][0x2000];
			tileCount = 384*2;
			colorCount = 64;
			//     1: image
			// * 384: all images
			// *   2: memory banks
			// *   4: mirrored images
			// *  16: all palettes
		} else { //#if -gbc
			videoRamBanks = new byte[1][0x2000];
			tileCount = 384;
			colorCount = 12;
			//     1: image
			// * 384: all images
			// *   4: mirrored images
			// *   3: all palettes
		} //#if

		videoRam = videoRamBanks[0];
		tileImage = new Image[tileCount * colorCount];
		tileReadState = new boolean[tileCount];
		
		cpu.memory[4] = videoRam;
		
		tempPix = new int[tileWidth * tileHeight];
		transparentImage = Image.createRGBImage(tempPix, tileWidth, tileHeight, true);
		
		if (cpu.gbcFeatures) { //#if +gbc
			for (int i = 0; i < gbcRawPalette.length; i++)
				gbcRawPalette[i] = -1000; // non-initialized
			for (int i = 0; i < (gbcPalette.length >> 1); i++)
				gbcPalette[i] = -1; // white
			for (int i = (gbcPalette.length >> 1); i < gbcPalette.length; i++)
				gbcPalette[i] = 0; // transparent
		} //#if
	}
	
	public int unflatten(byte[] flatState, int offset) {
		for (int i = 0; i < videoRamBanks.length; i++) {
			System.arraycopy(flatState, offset, videoRamBanks[i], 0, 0x2000);
			offset += 0x2000;
		}
		
		for (int i = 0; i < 12; i++) {
			if ((i & 3) == 0)
				gbPalette[i] = 0x00ffffff & GBCanvas.getInt(flatState, offset);
			else
				gbPalette[i] = 0xff000000 | GBCanvas.getInt(flatState, offset);
			offset += 4;
		}
		
		bgEnabled = (flatState[offset++] != 0);
		winEnabled = (flatState[offset++] != 0);
		spritesEnabled = (flatState[offset++] != 0);
		
		bgWindowDataSelect = (flatState[offset++] != 0);
		doubledSprites = (flatState[offset++] != 0);
		hiBgTileMapAddress = (flatState[offset++] != 0);
		
		hiWinTileMapAddress = ((cpu.registers[0x40] & 0x40) != 0);
		lcdEnabled = ((cpu.registers[0x40] & 0x80) != 0);
		
		if (cpu.gbcFeatures) { //#if +gbc
			spritePriorityEnabled = (flatState[offset++] != 0);
			setVRamBank(flatState[offset++] & 0xff);
			for (int i = 0; i < 128; i++) {
				setGBCPalette(i, flatState[offset++] & 0xff);
			}
		} else { //#if -gbc
			invalidateAll(0);
			invalidateAll(1);
			invalidateAll(2);
		} //#if
		
		return offset;
	}
	
	public int flatten(byte[] flatState, int offset) {
		for (int i = 0; i < videoRamBanks.length; i++) {
			System.arraycopy(videoRamBanks[i], 0, flatState, offset, 0x2000);
			offset += 0x2000;
		}
		
		for (int j = 0; j < 12; j++) {
			GBCanvas.setInt(flatState, offset, gbPalette[j]);
			offset += 4;
		}
		
		flatState[offset++] = (byte) (bgEnabled ? 1 : 0);
		flatState[offset++] = (byte) (winEnabled ? 1 : 0);
		flatState[offset++] = (byte) (spritesEnabled ? 1 : 0);
		flatState[offset++] = (byte) (bgWindowDataSelect ? 1 : 0);
		flatState[offset++] = (byte) (doubledSprites ? 1 : 0);
		flatState[offset++] = (byte) (hiBgTileMapAddress ? 1 : 0);
		
		if (cpu.gbcFeatures) { //#if +gbc
			flatState[offset++] = (byte) (spritePriorityEnabled ? 1 : 0);
			flatState[offset++] = (byte) ((tileOffset != 0) ? 1 : 0);
			
			for (int i = 0; i < 128; i++) {
				flatState[offset++] = (byte) getGBCPalette(i);
			}
		} //#if
		
		return offset;
	}
	
	/** Writes data to the specified video RAM address */
	public final void addressWrite(int addr, byte data) {
		if (addr < 0x1800) { // Bkg Tile data area
			int tileIndex = (addr >> 4) + tileOffset;
			
			if (tileReadState[tileIndex]) {
				int r = tileImage.length - tileCount + tileIndex;
				
				do {
					tileImage[r] = null;
					r -= tileCount;
				} while (r >= 0);
				tileReadState[tileIndex] = false;
			}
		}
		videoRam[addr] = data;
	}
	
	/** Invalidate all tiles in the tile cache for the given palette */
	public final void invalidateAll(int pal) {
		int start = pal * tileCount * 4;
		int stop = (pal + 1) * tileCount * 4;
		
		for (int r = start; r < stop; r++) {
			tileImage[r] = null;
		}
	}
	
	// called by notifyscanline, and draw
	private final void drawSprites(int priorityFlag) {
		if (!spritesEnabledThisFrame)
			return;
		
		for (int i = 0; i < 40; i++) {
			int attributes = 0xff & cpu.oam[i * 4 + 3];
			
			if ((attributes & 0x80) == priorityFlag) {
				int spriteX = (0xff & cpu.oam[i * 4 + 1]) - 8;
				int spriteY = (0xff & cpu.oam[i * 4]) - 16;
				int tileNum = (0xff & cpu.oam[i * 4 + 2]);
				
				if (spriteX >= 160 || spriteY >= 144 || spriteY == -16)
					continue;
				
				if (doubledSprites) {
					tileNum &= 0xFE;
				}
				
				int spriteAttrib = 0;
				
				if (cpu.gbcFeatures) { //#if +gbc
					spriteAttrib += 0x20 + ((attributes & 0x07) << 2); // palette
					tileNum += 384 * ((attributes >> 3) & 0x01); // tile vram bank
				} else { //#if -gbc
					if ((attributes & 0x10) != 0) {
						spriteAttrib |= TILE_OBJ2;
					} else {
						spriteAttrib |= TILE_OBJ1;
					}
				} //#if
				if ((attributes & 0x20) != 0) {
					spriteAttrib |= TILE_FLIPX;
				}
				if ((attributes & 0x40) != 0) {
					spriteAttrib |= TILE_FLIPY;
				}
				
				if (doubledSprites) {
					if ((spriteAttrib & TILE_FLIPY) != 0) {
						draw(tileNum + 1, spriteX, spriteY, spriteAttrib);
						draw(tileNum, spriteX, spriteY + 8, spriteAttrib);
					} else {
						draw(tileNum, spriteX, spriteY, spriteAttrib);
						draw(tileNum + 1, spriteX, spriteY + 8, spriteAttrib);
					}
				} else {
					draw(tileNum, spriteX, spriteY, spriteAttrib);
				}
			}
		}
	}
	
	/** This must be called by the CPU for each scanline drawn by the display hardware.  It
		*  handles drawing of the background layer
		*/
	public final void notifyScanline(int line) {
		if (skipping) {
			return;
		}
		
		if (line == 0) {
			g.setClip(left, top, 20 * tileWidth, 18 * tileHeight);
			if (!cpu.gbcFeatures) { //#if -gbc
				g.setColor(gbPalette[0]);
				g.fillRect(left, top, 20 * tileWidth, 18 * tileHeight);
			} //#if

			if (spritePriorityEnabled)
				drawSprites(0x80);
			
			windowStopLine = 144;
			winEnabledThisFrame = winEnabled;
			winEnabledThisLine = winEnabled;
			screenFilled = false;
			windowStopped = false;
		}
		
		if (winEnabledThisLine && !winEnabled) {
			windowStopLine = line & 0xff;
			winEnabledThisLine = false;
		}
		
		// Fix to screwed up status bars.  Record which data area is selected on the
		// first line the window is to be displayed.  Will work unless this is changed
		// after window is started
		// NOTE: Still no real support for hblank effects on window/sprites
		if (line == (cpu.registers[0x4A] & 0xff) + 1) { // Compare against WY reg
			savedWindowDataSelect = bgWindowDataSelect;
		}
		
		if (!bgEnabled)
			return;
		
		if (winEnabledThisLine && 
				!windowStopped && 
				(cpu.registers[0x4B] & 0xff) - 7 == 0 && // at left edge
				(cpu.registers[0x4A] & 0xff) <= line - 7) { // starts at or above top line of this row of tiles
			
			// the entire row is covered by the window, so it can be safely skipped
			int yPixelOfs = cpu.registers[0x42] & 7;
			int screenY = (line & 0xf8) - yPixelOfs;
			if (screenY >= 136)
				screenFilled = true;
		} else if ((((cpu.registers[0x42] + line) & 7) == 7) || (line == 144)) {
			int xPixelOfs = cpu.registers[0x43] & 7;
			int yPixelOfs = cpu.registers[0x42] & 7;
			int xTileOfs = (cpu.registers[0x43] & 0xff) >> 3;
			int yTileOfs = (cpu.registers[0x42] & 0xff) >> 3;
			
			int bgStartAddress = hiBgTileMapAddress ? 0x1c00 : 0x1800; 
			int tileNum;
			
			int screenY = (line & 0xf8) - yPixelOfs;
			int screenX = -xPixelOfs;
			int screenRight = 160;
			
			int tileY = (line >> 3) + yTileOfs;
			int tileX = xTileOfs;
			
			int memStart = bgStartAddress + ((tileY & 0x1f) << 5);

			while (screenX < screenRight) {
				if (bgWindowDataSelect) {
					tileNum = videoRamBanks[0][memStart + (tileX & 0x1f)] & 0xff;
				} else {
					tileNum = 256 + videoRamBanks[0][memStart + (tileX & 0x1f)];
				}
				
				int tileAttrib = 0;
				if (cpu.gbcFeatures) { //#if +gbc
					int mapAttrib = videoRamBanks[1][memStart + (tileX & 0x1f)];
					tileAttrib += (mapAttrib & 0x07) << 2; // palette
					tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
					tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
					// bit 7 (priority) is ignored
				} //#if
				tileX++;

				draw(tileNum, screenX, screenY, tileAttrib);

				screenX += 8;
			}

			if (screenY >= 136)
				screenFilled = true;
		}
		if (line == 143 && !screenFilled) {
			notifyScanline(144); // fudge to update last part of screen when scrolling in y direction
		}
	}
	
	public final void vBlank() {
		// if (MeBoy.timing) return;
		
		timer += MS_PER_FRAME;
		
		if (skipping && g != null) { // if g == null, we need to do a redraw to set it
			skipCount++;
			if (skipCount >= MeBoy.maxFrameSkip) {
				// can't keep up, force draw next frame and reset timer (if lagging)
				skipping = false;
				int lag = (int) System.currentTimeMillis() - timer;
				
				if (lag > MS_PER_FRAME)
					timer += lag - MS_PER_FRAME;
			} else
				skipping = (timer - ((int) System.currentTimeMillis()) < 0);
			return;
		}
		
		if (!lcdEnabled && g != null) {
			// clear screen
			g.setClip(left, top, 20 * tileWidth, 18 * tileHeight);
			if (cpu.gbcFeatures) { //#if +gbc
				g.setColor(-1);
			} else { //#if -gbc
				g.setColor(gbPalette[0]);
			} //#if
			g.fillRect(left, top, 20 * tileWidth, 18 * tileHeight);
		}
		
		lastSkipCount = skipCount;
		frameDone = false;
		cpu.screen.repaint();
		while (!frameDone && !cpu.terminate) {
			Thread.yield();
		}
		
		int now = (int) System.currentTimeMillis();
		
		if (MeBoy.maxFrameSkip == 0)
			skipping = false;
		else
			skipping = timer - now < 0;
		
		// sleep if too far ahead
		try {
			while (timer > now + MS_PER_FRAME) {
				Thread.sleep(1);
				now = (int) System.currentTimeMillis();
			}
		} catch (InterruptedException e) {
			// e.printStackTrace();
		}
		
		skipCount = 0;
	}
	
	public final void draw(Graphics g) {
		if (lcdEnabled) {
			this.g = g;
			
			/* Draw window */
			if (winEnabledThisFrame) {
				int wx, wy;
				
				int windowStartAddress = hiWinTileMapAddress ? 0x1c00 : 0x1800;

				if (windowStopped) {
					wx = windowStopX;
					wy = windowStopY;
				} else {
					wx = (cpu.registers[0x4B] & 0xff) - 7;
					wy = (cpu.registers[0x4A] & 0xff);
				}

				if (!cpu.gbcFeatures) { //#if -gbc
					g.setColor(gbPalette[0]);
					int h = windowStopLine - wy;
					int w = 160 - wx;
					g.fillRect(((wx * tileWidth) >> 3) + left,
							((wy * tileHeight) >> 3) + top,
							(w * tileWidth) >> 3,
							(h * tileHeight) >> 3);
				} //#if
				
				int tileNum, tileAddress;
				int screenY = wy;
				
				int maxy = 19 - (wy >> 3);
				for (int y = 0; y < maxy; y++) {
					if (wy + y * 8 >= windowStopLine)
						break;
					
					int screenX = wx;
					
					int maxx = 21 - (wx >> 3);
					for (int x = 0; x < maxx; x++) {
						tileAddress = windowStartAddress + (y * 32) + x;
						
						if (savedWindowDataSelect) {
							tileNum = videoRamBanks[0][tileAddress] & 0xff;
						} else {
							tileNum = 256 + videoRamBanks[0][tileAddress];
						}
						
						int tileAttrib = 0;
						if (cpu.gbcFeatures) { //#if +gbc
							int mapAttrib = videoRamBanks[1][tileAddress];
							tileAttrib += (mapAttrib & 0x07) << 2; // palette
							tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
							tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
							// bit 7 (priority) is ignored
						} //#if

						draw(tileNum, screenX, screenY, tileAttrib);
						
						screenX += 8;
					}
					screenY += 8;
				}
			}
			
			if (!spritePriorityEnabled)
				drawSprites(0x80);
			drawSprites(0);
		}
		
		frameDone = true;
		spritesEnabledThisFrame = spritesEnabled;
	}
	
	/** Create the image of a tile in the tile cache by reading the relevant data from video
		*  memory
		*/
	private final Image updateImage(int tileIndex, int attribs) {
		int index = tileIndex + tileCount * attribs;
		
		boolean otherBank = (tileIndex >= 384);

		int offset = otherBank ? ((tileIndex - 384) << 4) : (tileIndex << 4);
		
		int paletteStart = attribs & 0xfc;
		
		byte[] vram = otherBank ? videoRamBanks[1] : videoRamBanks[0];
		int[] palette;
		if (cpu.gbcFeatures) { //#if +gbc
			palette = gbcPalette;
		} else { //#if -gbc
			palette = gbPalette;
		} //#if
		boolean transparent = palette[paletteStart] >= 0; // i.e. if high byte of color #0 is ff, image can never be transparent

		int pixix = 0;
		int pixixdx = 1;
		int pixixdy = 0;
		
		if ((attribs & TILE_FLIPY) != 0) {
			pixixdy = -2*tileWidth;
			pixix = tileWidth * (tileHeight - 1);
		}
		if ((attribs & TILE_FLIPX) == 0) {
			pixixdx = -1;
			pixix += tileWidth - 1;
			pixixdy += tileWidth * 2;
		}
		
		int x1c, x2c, y1c, y2c;
		
		y1c = tileHeight >> 1;
		y2c = 0;
		for (int y = 0; y < tileHeight; y++) {
			int num = weaveLookup[vram[offset] & 0xff] + (weaveLookup[vram[offset + 1] & 0xff] << 1);
			if (num != 0)
				transparent = false;
			
			x1c = tileWidth >> 1;
			x2c = 0;
			for (int x = tileWidth; --x >= 0; ) {
				tempPix[pixix] = palette[paletteStart + (num & 3)];
				pixix += pixixdx;
				
				x2c += 8;
				while (x2c > x1c) {
					x1c += tileWidth;
					num >>= 2;
				}
			}
			pixix += pixixdy;
			
			y2c += 8;
			while (y2c > y1c) {
				y1c += tileHeight;
				offset += 2;
			}
		}
		
		
		if (transparent) {
			tileImage[index] = transparentImage;
		} else {
			tileImage[index] = Image.createRGBImage(tempPix, tileWidth, tileHeight, true);
		}
		
		tileReadState[tileIndex] = true;
		
		return tileImage[index];
	}
	
	private final void draw(int tileIndex, int x, int y, int attribs) {
		int ix = tileIndex + tileCount * attribs;
		
		Image im = tileImage[ix];
		
		if (im == null) {
			im = updateImage(tileIndex, attribs);
		}
		
		if (im == transparentImage) {
			return;
		}
		
		if (scale) {
			y = (y * tileHeight) >> 3;
			x = (x * tileWidth) >> 3;
		}
		
		g.drawImage(im, left + x, top + y, 20);
	}

	/** Set the palette from the internal Gameboy format */
	public void decodePalette(int startIndex, int data) {
		for (int i = 0; i < 4; i++)
			gbPalette[startIndex + i] = colors[((data >> (2 * i)) & 0x03)];
		gbPalette[startIndex] &= 0x00ffffff;
	}
	
	public final void stopWindowFromLine() {
		windowStopped = true;
		windowStopLine = (cpu.registers[0x44] & 0xff);
		windowStopX = (cpu.registers[0x4B] & 0xff) - 7;
		windowStopY = (cpu.registers[0x4A] & 0xff);
	}
	
	public void setScale(int screenWidth, int screenHeight) {
		int oldTW = tileWidth;
		int oldTH = tileHeight;
		
		tileWidth = screenWidth / 20;
		tileHeight = screenHeight / 18;
		
		if (MeBoy.keepProportions)
			if (tileWidth < tileHeight)
				tileHeight = tileWidth;
			else
				tileWidth = tileHeight;
		
		scale = tileWidth != 8 || tileHeight != 8;
		
		if (tileWidth != oldTW || tileHeight != oldTH) {
			// invalidate cache
			for (int r = 0; r < tileImage.length; r++)
				tileImage[r] = null;
			
			for (int r = 0; r < tileReadState.length; r++)
				tileReadState[r] = false;
		}
		
		tempPix = new int[tileWidth * tileHeight];
	}
	
	//#if +gbc
	public void setGBCPalette(int index, int data) {
		if (gbcRawPalette[index] == data)
			return;
		
		gbcRawPalette[index] = data;
		if (index >= 0x40 && (index & 0x6) == 0) {
			// stay transparent
			return;
		}
		
		int value = (gbcRawPalette[index | 1] << 8) + gbcRawPalette[index & -2];
		
		gbcPalette[index >> 1] = 0xff000000 + ((value & 0x001F) << 19) + ((value & 0x03E0) << 6) + ((value & 0x7C00) >> 7);
		
		invalidateAll(index >> 3);
	}

	public int getGBCPalette(int index) {
		return gbcRawPalette[index];
	}

	public void setVRamBank(int value) {
		tileOffset = value * 384;
		videoRam = videoRamBanks[value];
		cpu.memory[4] = videoRam;
	}
	//#if
}
