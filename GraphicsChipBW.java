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


public class GraphicsChipBW {
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
	private DmgcpuBW cpu;
	
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
	
	// scaling the screen (downscaling only)
	private int scaleXShift = 10;
	private int scaleYShift = 10;

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
	
	
	/** Create a new GraphicsChipBW connected to the speicfied CPU */
	public GraphicsChipBW(DmgcpuBW d) {
		cpu = d;
		
			videoRamBanks = new byte[1][0x2000];
			tileCount = 384;
			colorCount = 12;
			//     1: image
			// * 384: all images
			// *   4: mirrored images
			// *   3: all palettes

		videoRam = videoRamBanks[0];
		tileImage = new Image[tileCount * colorCount];
		tileReadState = new boolean[tileCount];
		
		cpu.memory[4] = videoRam;
		
		tempPix = new int[tileWidth * tileHeight];
		transparentImage = Image.createRGBImage(tempPix, tileWidth, tileHeight, true);
		
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
		
			invalidateAll(0);
			invalidateAll(1);
			invalidateAll(2);
		
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
				
				if (spriteX >= 160 || spriteY >= 144)
					continue;
				
				if (doubledSprites) {
					tileNum &= 0xFE;
				}
				
				int spriteAttrib = 0;
				
					if ((attributes & 0x10) != 0) {
						spriteAttrib |= TILE_OBJ2;
					} else {
						spriteAttrib |= TILE_OBJ1;
					}
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
				g.setColor(gbPalette[0]);
				g.fillRect(left, top, 20 * tileWidth, 18 * tileHeight);

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
		
		if ((((cpu.registers[0x42] + line) & 7) == 7) || (line == 144)) {
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
				tileX++;

				draw(tileNum, screenX, screenY, tileAttrib);

				screenX += 8;
			}

			if (screenY >= 136 + top)
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
				g.setColor(gbPalette[0]);
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

					g.setColor(gbPalette[0]);
					int h = windowStopLine - wy;
					int w = 160 - wx;
					g.fillRect((wx - (wx >> scaleXShift)) + left, (wy - (wy >> scaleYShift)) + top, w - (w >> scaleXShift), h - (h >> scaleYShift));
				
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
			palette = gbPalette;
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
		
		int ymask = (1 << scaleYShift) - 1;
		int yhit = (1 << (scaleYShift-1));
		int xmask = (1 << scaleXShift) - 1;
		int xhit = (1 << (scaleXShift-1));
		
		for (int y = 7; y >= 0; y--) {
			int num = weaveLookup[vram[offset++] & 0xff] + (weaveLookup[vram[offset++] & 0xff] << 1);
			if (num != 0)
				transparent = false;
			
			for (int x = 7; x >= 0; x--) {
				tempPix[pixix] = palette[paletteStart + (num & 3)];
				pixix += pixixdx;
				num >>= 2;
				
				if ((x & xmask) == xhit) {
					num >>= 2;
					x--;
				}
			}
			pixix += pixixdy;
			
			if ((y & ymask) == yhit) {
				offset += 2;
				y--;
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
		
		g.drawImage(im, left + x - (x >> scaleXShift), top + y - (y >> scaleYShift), 20);
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
		if (screenWidth < 140)
			scaleXShift = 2;
		else if (screenWidth < 160)
			scaleXShift = 3;
		else
			scaleXShift = 10; // i.e. not at all
		
		if (screenHeight < 126)
			scaleYShift = 2;
		else if (screenHeight < 144)
			scaleYShift = 3;
		else
			scaleYShift = 10; // i.e. not at all
		
		if (MeBoy.keepProportions)
			if (scaleYShift < scaleXShift)
				scaleXShift = scaleYShift;
			else
				scaleYShift = scaleXShift;
		
		if (tileWidth != 8 - (8 >> scaleXShift) || tileHeight != 8 - (8 >> scaleYShift)) {
			// invalidate cache
			for (int r = 0; r < tileImage.length; r++)
				tileImage[r] = null;
			
			for (int r = 0; r < tileReadState.length; r++)
				tileReadState[r] = false;
		}
		
		tileWidth = 8 - (8 >> scaleXShift);
		tileHeight = 8 - (8 >> scaleYShift);
		tempPix = new int[tileWidth * tileHeight];
	}
	
}
