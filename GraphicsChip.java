/*

MeBoy

Copyright 2005 Bjorn Carlin
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
	/** Tile uses the background palette */
	private final int TILE_BKG = 0;
	
	/** Tile is flipped horizontally */
	private final int TILE_FLIPX = 1; // 0x20 in oam attributes
	
	/** Tile is flipped vertically */
	private final int TILE_FLIPY = 2; // 0x40 in oam attributes
	
	/** Tile uses the first sprite palette */
	private final int TILE_OBJ1 = 4; // reset 0x10 in oam attributes
	
	/** Tile uses the second sprite palette */
	private final int TILE_OBJ2 = 8; // set 0x10 in oam attributes
	
	/** The current contents of the video memory, mapped in at 0x8000 - 0x9FFF */
	private byte[] videoRam = new byte[0x2000];
	
	
	/** RGB color values */
	private final int[] colours = { 0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000};
	private int[] palette = new int[12];
	
	boolean bgEnabled = true;
	boolean winEnabled = true;
	boolean spritesEnabled = true;
	boolean spritesEnabledThisFrame = true;
	
	
	private Graphics g;
	
	// skipping, timing:
	public static int maxFrameSkip = 3;
	
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
	
	/** Selection of one of two address for the BG tile map. */
	boolean hiBgTileMapAddress = false;
	private Dmgcpu cpu;
	
	// Hacks to allow some raster effects to work.  Or at least not to break as badly.
	boolean savedWindowDataSelect = false;
	
	boolean windowEnableThisLine = false;
	int windowStopLine = 144;
	
	// tiles & image cache
	private Image[] tileImage = new Image[384*12];
	private boolean[] tileTransparent = new boolean[384*12];
	private boolean[] tileReadState = new boolean[384];
	
	private int[] tempPix = new int[64];
	
	public int left, top;
	
	private boolean screenFilled;
	
	
	/** Create a new GraphicsChip connected to the speicfied CPU */
	public GraphicsChip(Dmgcpu d) {
		cpu = d;
		
		cpu.memory[4] = videoRam;
	}
	
	public int unflatten(byte[] flatState, int offset) {
		System.arraycopy(flatState, offset, videoRam, 0, 0x2000);
		offset += 0x2000;
		
		for (int i = 0; i < 12; i++) {
			palette[i] = GBCanvas.getInt(flatState, offset);
			offset += 4;
		}
		
		bgEnabled = (flatState[offset++] != 0);
		winEnabled = (flatState[offset++] != 0);
		spritesEnabled = (flatState[offset++] != 0);
		
		bgWindowDataSelect = (flatState[offset++] != 0);
		doubledSprites = (flatState[offset++] != 0);
		hiBgTileMapAddress = (flatState[offset++] != 0);
		
		return offset;
	}
	
	public int flatten(byte[] flatState, int offset) {
		System.arraycopy(videoRam, 0, flatState, offset, 0x2000);
		offset += 0x2000;
		
		for (int j = 0; j < 12; j++) {
			GBCanvas.setInt(flatState, offset, palette[j]);
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
			int tileIndex = (addr >> 4);
			
			if (tileReadState[tileIndex]) {
				int r = 4224+tileIndex; // 384*11=4224
				while (r >= 0) {
					tileImage[r] = null;
					tileTransparent[r] = false;
					r -= 384;
				}
				tileReadState[tileIndex] = false;
			}
		}
		videoRam[addr] = data;
	}
	
	/** Invalidate all tiles in the tile cache for the given palette */
	public final void invalidateAll(int palette) {
		int start = palette * 384 * 4;
		int stop = (palette+1) * 384 * 4;
		
		for (int r = start; r < stop; r++) {
			tileImage[r] = null;
			tileTransparent[r] = false;
		}
	}
	
	// called by notifyscanline, and draw
	private final void drawSprites(int priorityFlag) {
		if (!spritesEnabledThisFrame)
			return;
		
		for (int i = 0; i < 40; i++) {
			int attributes = 0xff & cpu.oam[i * 4 + 3];
			
			if ((attributes & 0x80) == priorityFlag) {
				int spriteX = (0xff & cpu.oam[i * 4 + 1]) - 8 + left;
				int spriteY = (0xff & cpu.oam[i * 4]) - 16 + top;
				int tileNum = (0xff & cpu.oam[i * 4 + 2]);
				
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
			g.setClip(left, top, 160, 144);
			g.setColor(palette[0]);
			g.fillRect(left, top, 160, 144);
			
			drawSprites(0x80);
			
			windowStopLine = 144;
			windowEnableThisLine = winEnabled;
			screenFilled = false;
		}
		
		if (windowEnableThisLine) {
			if (!winEnabled) {
				windowStopLine = line & 0xff;
				windowEnableThisLine = false;
			}
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
			
			int screenY = (line & 0xf8) - yPixelOfs + top;
			int screenX = -xPixelOfs + left;
			int screenRight = 160 + left;
			
			int tileY = (line >> 3) + yTileOfs;
			int tileX = xTileOfs;
			
			int tileNumAddress;
			int memStart = bgStartAddress + ((tileY & 0x1f) << 5);
			
			if (bgWindowDataSelect) {
				while (screenX < screenRight) {
					tileNum = videoRam[memStart + (tileX++ & 0x1f)] & 0xff;
					
					if (!tileTransparent[tileNum]) {
						Image im = tileImage[tileNum];
						if (im == null) {
							im = updateImage(tileNum, 0);
						}
						
						g.drawImage(im, screenX, screenY, 20);
					}
					
					screenX += 8;
				}
			} else {
				while (screenX < screenRight) {
					tileNum = 256 + videoRam[memStart + (tileX++ & 0x1f)];
					
					if (!tileTransparent[tileNum]) {
						Image im = tileImage[tileNum];
						if (im == null) {
							im = updateImage(tileNum, 0);
						}
						
						g.drawImage(im, screenX, screenY, 20);
					}
					
					screenX += 8;
				}
			}
			
			if (screenY >= 136+top)
				screenFilled = true;
		}
		if (line == 143 && !screenFilled) {
			notifyScanline(144); // fudge to update last part of screen when scrolling in y direction
		}
	}
	
	public final void vBlank() {
		timer += 17;
		
		if (skipping && g != null) { // if g == null, we need to do a redraw to set it
			skipCount++;
			if (skipCount >= maxFrameSkip) {
				// can't keep up, draw and reset timer
				skipping = false;
				timer = (int) System.currentTimeMillis();
			} else
				skipping = (timer - ((int) System.currentTimeMillis()) < 0);
			return;
		}
		
		lastSkipCount = skipCount;
		frameDone = false;
		cpu.screen.repaint();
		while (!frameDone && !cpu.terminate) {
			Thread.yield();
		}
		
		// theoretically, should sleep if too far ahead
		
		int now = (int) System.currentTimeMillis();
		
		if (maxFrameSkip == 0)
			skipping = false;
		else
			skipping = timer - now < 0;
		
		skipCount = 0;
	}
	
	public final void draw(Graphics g) {
		this.g = g;
		
		/* Draw window */
		if (winEnabled) {
			int wx, wy;
			int windowStartAddress;
			
			if ((cpu.registers[0x40] & 0x40) != 0) {
				windowStartAddress = 0x1C00;
			} else {
				windowStartAddress = 0x1800;
			}
			wx = (cpu.registers[0x4B] & 0xff) - 7;
			wy = (cpu.registers[0x4A] & 0xff);
			
			g.setColor(palette[0]);
			g.fillRect(wx+left, wy+top, 160-wx, windowStopLine-wy);
			
			int tileNum, tileAddress;
			int attribData, attribs, tileDataAddress;
			int screenY = wy + top;
			
			for (int y = 0; y < 19 - (wy >> 3); y++) {
				if (wy + y * 8 >= windowStopLine)
					break;
				
				int screenX = wx + left;
				
				for (int x = 0; x < 21 - (wx >> 3); x++) {
					tileAddress = windowStartAddress + (y * 32) + x;
					
					if (!savedWindowDataSelect) {
						tileNum = 256 + videoRam[tileAddress];
					} else {
						tileNum = videoRam[tileAddress] & 0xff;
					}
					
					if (!tileTransparent[tileNum]) {
						Image im = tileImage[tileNum];
						if (im == null) {
							im = updateImage(tileNum, 0);
						}
						
						g.drawImage(im, screenX, screenY, 20);
					}
					
					screenX += 8;
				}
				screenY += 8;
			}
		}
		
		drawSprites(0);
		
		frameDone = true;
		spritesEnabledThisFrame = spritesEnabled;
	}
	
	/** Create the image of a tile in the tile cache by reading the relevant data from video
		*  memory
		*/
	private final Image updateImage(int tileIndex, int attribs) {
		int index = tileIndex + 384 * attribs;
		
		int rgbValue;
		int offset = tileIndex << 4;
		
		int pixix = 0;
		int paletteStart = attribs & 0xf4;
		boolean transparent = true;
		
		int lower, upper;
		int entryNumber;
		
		boolean flipx = (attribs & TILE_FLIPX) != 0;
		boolean flipy = (attribs & TILE_FLIPY) != 0;
		
		for (int y = 0; y < 8; y++) {
			if (flipy) {
				lower = videoRam[offset + ((7 - y) << 1)];
				upper = videoRam[offset + ((7 - y) << 1) + 1] << 1;
			} else {
				lower = videoRam[offset + (y << 1)];
				upper = videoRam[offset + (y << 1) + 1] << 1;
			}
			
			for (int x = 0; x < 8; x++) {
				if (flipx) {
					entryNumber = ((upper >> x) & 2) + ((lower >> x) & 1);
				} else {
					entryNumber = ((upper >> (7-x)) & 2) + ((lower >> (7-x)) & 1);
				}
				
				if (entryNumber == 0) {
					tempPix[pixix++] = 0;
				} else {
					tempPix[pixix++] = palette[paletteStart + entryNumber];
					transparent = false;
				}
			}
		}
		
		tileTransparent[index] = transparent;
		tileImage[index] = Image.createRGBImage(tempPix, 8, 8, true);
		tileReadState[tileIndex] = true;
		
		return tileImage[index];
	}
	
	private final void draw(int tileIndex, int left, int top, int attribs) {
		int ix = tileIndex + 384 * attribs;
		
		if (tileTransparent[ix])
			return;
		
		Image im = tileImage[ix];
		if (im == null) {
			im = updateImage(tileIndex, attribs);
		}
		
		g.drawImage(im, left, top, 20);
	}

	/** Set the palette from the internal Gameboy format */
	public void decodePalette(int startIndex, int data) {
		for (int i = 0; i < 4; i++)
			palette[startIndex + i] = colours[((data >> (2*i)) & 0x03)];
	}
}
