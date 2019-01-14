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
	
	
	private Graphics g;
	
	/** The current frame skip value */
	int frameSkip = 4;
	private boolean skipping = true;
	
	// some statistics
	int framesDrawn, lastFrameLength, framesDrawnOrSkipped;
	int[] lastFrameTime = new int[10];
	private int lastDrawOrSkip;
	
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
	
	private int[] tempPix = new int[64];
	
	
	/** Create a new GraphicsChip connected to the speicfied CPU */
	public GraphicsChip(Dmgcpu d) {
		cpu = d;
		
		cpu.memory[4] = videoRam;
	}
	
	/** Writes data to the specified video RAM address */
	public final void addressWrite(int addr, byte data) {
		if (addr < 0x1800) { // Bkg Tile data area
			int min = (addr >> 4);
			for (int r = 0; r < 12; r++) {
				tileImage[r*384+min] = null;
				tileTransparent[r*384+min] = false;
			}
		}
		videoRam[addr] = data;
	}
	
	/** Invalidate all tiles in the tile cache */
	public final void invalidateAll() {
		for (int r = 384*12; --r >= 0; ) {
			tileImage[r] = null;
			tileTransparent[r] = false;
		}
	}
	
	// called by notifyscanline, and draw
	private final void drawSprites(int priorityFlag) {
		if (!spritesEnabled)
			return;
		
		for (int i = 0; i < 40; i++) {
			int attributes = 0xff & cpu.oam[i * 4 + 3];
			
			if ((attributes & 0x80) == priorityFlag) {
				int spriteX = (0xff & cpu.oam[i * 4 + 1]) - 8;
				int spriteY = (0xff & cpu.oam[i * 4]) - 16;
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
			g.setColor(palette[0]);
			g.fillRect(0, 0, 160, 144);
			
			drawSprites(0x80);
			
			windowStopLine = 144;
			windowEnableThisLine = winEnabled;
		}
		
		if (windowEnableThisLine) {
			if (!winEnabled) {
				windowStopLine = line;
				windowEnableThisLine = false;
			}
		}
		
		line &= 0xff;
		
		// Fix to screwed up status bars.  Record which data area is selected on the
		// first line the window is to be displayed.  Will work unless this is changed
		// after window is started
		// NOTE: Still no real support for hblank effects on window/sprites
		if (line == (cpu.registers[0x4A] & 0xff) + 1) { // Compare against WY reg
			savedWindowDataSelect = bgWindowDataSelect;
		}
		
		if (!bgEnabled)
			return;
		
		int yPixelOfs = cpu.registers[0x42] & 7;
		
		if (((yPixelOfs + line) % 8 == 4) || (line == 0)) {
			if ((line >= 144) && (line < 152))
				notifyScanline(line + 8);
			
			int xPixelOfs = cpu.registers[0x43] & 7;
			int xTileOfs = (cpu.registers[0x43] & 0xff) >> 3;
			int yTileOfs = (cpu.registers[0x42] & 0xff) >> 3;
			int bgStartAddress, tileNum;
			
			int y = (line + yPixelOfs) >> 3;
			
			if (hiBgTileMapAddress) {
				bgStartAddress = 0x1C00;
			} else {
				bgStartAddress = 0x1800;
			}
			
			int tileNumAddress, vidMemAddr;
			
			int screenY = y * 8 - yPixelOfs;
			int screenX = - xPixelOfs;
			int memStart = bgStartAddress + (((y + yTileOfs) & 0x1f) << 5);
			
			for (int x = 0; x < 21; x++) {
				tileNumAddress = memStart + ((x + xTileOfs) & 0x1f);
				
				if (bgWindowDataSelect) {
					tileNum = videoRam[tileNumAddress] & 0xff;
				} else {
					tileNum = 256 + videoRam[tileNumAddress];
				}
				
				if (!tileTransparent[tileNum])
					draw(tileNum, screenX, screenY);
				screenX += 8;
			}
		}
	}
	
	public final void drawOrSkip() {
		if (skipping && g != null) {
			framesDrawnOrSkipped++;
			if (framesDrawnOrSkipped % frameSkip == 0)
				skipping = false;
		} else {
			frameDone = false;
			cpu.screen.repaintSmall();
			try {
				int now = (int) System.currentTimeMillis();
				if (lastDrawOrSkip == 0)
					lastDrawOrSkip = now - 1000;
				
				// throttle speed if running faster than 17 ms per (skipped/nonskipped) frame
				int delay = lastDrawOrSkip + frameSkip * 17 - now;
				if (delay > 0) {
					java.lang.Thread.sleep(delay);
					lastDrawOrSkip += 17 * frameSkip;
				} else
					lastDrawOrSkip = now;
				
				// wait for update to be done
				while (!frameDone) {
					java.lang.Thread.sleep(1);
				}
			} catch (InterruptedException exc) {}
			
			skipping = (framesDrawnOrSkipped % frameSkip != 0);
		}
	}
	
	public final void draw(Graphics g, int startX, int startY) {
		this.g = g;
		framesDrawn++;
		framesDrawnOrSkipped++;
		
		lastFrameLength = (int) System.currentTimeMillis() - lastFrameTime[framesDrawn % 10];
		lastFrameTime[framesDrawn % 10] += lastFrameLength;
		
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
			g.fillRect(wx, wy, 160-wx, 144-wy);
			
			int tileNum, tileAddress;
			int attribData, attribs, tileDataAddress;
			
			for (int y = 0; y < 19 - (wy / 8); y++) {
				if (wy + y * 8 >= windowStopLine)
					break;
				
				for (int x = 0; x < 21 - (wx / 8); x++) {
					tileAddress = windowStartAddress + (y * 32) + x;
					
					if (!savedWindowDataSelect) {
						tileNum = 256 + videoRam[tileAddress];
					} else {
						tileNum = videoRam[tileAddress] & 0xff;
					}
					
					if (!tileTransparent[tileNum])
						draw(tileNum, wx + x * 8, wy + y * 8);
				}
			}
		}
		
		drawSprites(0);
		
		frameDone = true;
	}
	
	/** Create the image of a tile in the tile cache by reading the relevant data from video
		*  memory
		*/
	private final Image updateImage(int tileIndex, int attribs) {
		int index = tileIndex + 384 * attribs;
		
		int px, py;
		int rgbValue;
		int offset = tileIndex << 4;
		
		int pixix = 0;
		int paletteStart;
		if (attribs == 0)
			paletteStart = 0;
		else if (attribs >= 8)
			paletteStart = 8;
		else
			paletteStart = 4;
		
		boolean transparent = true;
		
		for (int y = 0; y < 8; y++) {
			if ((attribs & TILE_FLIPY) != 0) {
				py = 7 - y;
			} else {
				py = y;
			}
			
			for (int x = 0; x < 8; x++) {
				if ((attribs & TILE_FLIPX) != 0) {
					px = 7 - x;
				} else {
					px = x;
				}
				
				int pixelColorLower = (videoRam[offset + (py * 2)] & (0x80 >> px)) >> (7 - px);
				int pixelColorUpper = (videoRam[offset + (py * 2) + 1] & (0x80 >> px)) >> (7 - px);
				
				int entryNumber = (pixelColorUpper * 2) + pixelColorLower;
				
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
		return tileImage[index];
	}
	
	private final void draw(int tileIndex, int left, int top) {
		Image im = tileImage[tileIndex];
		if (im == null) {
			im = updateImage(tileIndex, 0);
		}
		
		g.drawImage(im, left, top, 20);
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
