/*

MeBoy

Copyright 2005-2008 Bjorn Carlin
http://www.arktos.se/

Based on JavaBoy, COPYRIGHT (C) 2001 Neil Millstone and The Victoria
University of Manchester. Bluetooth support based on code contributed by
Martin Neumann.

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;


public class SimpleGraphicsChip extends GraphicsChip {
	boolean winEnabledThisFrame = true;
	boolean spritesEnabledThisFrame = true;
	private boolean screenFilled;
	private boolean windowStopped;
	private boolean winEnabledThisLine = false;
	private int windowStopLine = 144;
	private int windowStopX;
	private int windowStopY;
	private boolean frameDone; // ugly framebuffer "mutex"

	// Hacks to allow some raster effects to work.  Or at least not to break as badly.
	boolean savedWindowDataSelect = false;
	
	// tiles & image cache
	private Image transparentImage;
	private Image[] tileImage;
	private boolean[] tileReadState; // true if there are any images to be invalidated
	
	private int[] tempPix;

	int tileWidth = 8;
	int tileHeight = 8;

	private Graphics g;
	
	
	public SimpleGraphicsChip(Dmgcpu d) {
		super(d);
		
		colors = new int[] { 0xffffffff, 0xffaaaaaa, 0xff555555, 0xff000000};
		gbcMask = 0xff000000;
		
		tileImage = new Image[tileCount * colorCount];
		tileReadState = new boolean[tileCount];
		
		cpu.memory[4] = videoRam;
		
		tempPix = new int[tileWidth * tileHeight];
		transparentImage = Image.createRGBImage(tempPix, tileWidth, tileHeight, true);
		
		frameBufferImage = Image.createImage(scaledWidth, scaledHeight);
		g = frameBufferImage.getGraphics();
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
				
				if (cpu.gbcFeatures) {
					spriteAttrib += 0x20 + ((attributes & 0x07) << 2); // palette
					tileNum += 384 * ((attributes >> 3) & 0x01); // tile vram bank
				} else {
					if ((attributes & 0x10) != 0) {
						spriteAttrib |= TILE_OBJ2;
					} else {
						spriteAttrib |= TILE_OBJ1;
					}
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
	
	/** This must be called by the CPU for each scanline drawn by the display hardware. It
		* handles drawing of the background layer
		*/
	public final void notifyScanline(int line) {
		if (skipping) {
			return;
		}
		
		if (line == 0) {
			if (!cpu.gbcFeatures) {
				g.setColor(gbPalette[0]);
				g.fillRect(0, 0, scaledWidth, scaledHeight);
				
				// for SimpleGraphics, sprite prio is enabled iff !gbcFeatures
				drawSprites(0x80);
			}
			
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
		
		// Fix to screwed up status bars. Record which data area is selected on the
		// first line the window is to be displayed. Will work unless this is changed
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
				if (cpu.gbcFeatures) {
					int mapAttrib = videoRamBanks[1][memStart + (tileX & 0x1f)];
					tileAttrib += (mapAttrib & 0x07) << 2; // palette
					tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
					tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
					// bit 7 (priority) is ignored
				}
				tileX++;

				draw(tileNum, screenX, screenY, tileAttrib);

				screenX += 8;
			}

			if (screenY >= 136)
				screenFilled = true;
		}
		if (line == 143) {
			if (!screenFilled)
				notifyScanline(144); // fudge to update last part of screen when scrolling in y direction
			updateFrameBufferImage();
		}
	}
	
	private final void updateFrameBufferImage() {
		if (lcdEnabled) {
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

				if (!cpu.gbcFeatures) {
					g.setColor(gbPalette[0]);
					int h = windowStopLine - wy;
					int w = 160 - wx;
					g.fillRect(((wx * tileWidth) >> 3),
							((wy * tileHeight) >> 3),
							(w * tileWidth) >> 3,
							(h * tileHeight) >> 3);
				}
				
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
						if (cpu.gbcFeatures) {
							int mapAttrib = videoRamBanks[1][tileAddress];
							tileAttrib += (mapAttrib & 0x07) << 2; // palette
							tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
							tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
							// bit 7 (priority) is ignored
						}

						draw(tileNum, screenX, screenY, tileAttrib);
						
						screenX += 8;
					}
					screenY += 8;
				}
			}
			
			if (cpu.gbcFeatures) {
				// for SimpleGraphics, sprite prio is enabled iff !gbcFeatures
				drawSprites(0x80);
			}
			drawSprites(0);
		} else {
			// lcd disabled
			if (cpu.gbcFeatures) {
				g.setColor(-1);
			} else {
				g.setColor(gbPalette[0]);
			}
			g.fillRect(0, 0, scaledWidth, scaledHeight);
		}
		
		spritesEnabledThisFrame = spritesEnabled;
	}
	
	public final void repaint() {
		frameDone = false;
		cpu.screen.redrawSmall();
		while (!frameDone && !cpu.terminate) {
			Thread.yield();
		}
	}
	
	/** Create the image of a tile in the tile cache by reading the relevant data from video
	* memory
	*/
	private final Image updateImage(int tileIndex, int attribs) {
		int index = tileIndex + tileCount * attribs;
		
		boolean otherBank = (tileIndex >= 384);

		int offset = otherBank ? ((tileIndex - 384) << 4) : (tileIndex << 4);
		
		int paletteStart = attribs & 0xfc;
		
		byte[] vram = otherBank ? videoRamBanks[1] : videoRamBanks[0];
		int[] palette;
		if (cpu.gbcFeatures) {
			palette = gbcPalette;
		} else {
			palette = gbPalette;
		}
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

		int startx, starty;		
		int deltax, deltay;
		
		// set up scaling constants, for upscaling and downscaling
		if (tileWidth > 8) {
			startx = tileWidth - 5;
			deltax = 8;
		} else {
			startx = tileWidth - 1;
			deltax = 9;
		}
		if (tileHeight > 8) {
			starty = tileHeight - 5;
			deltay = 8;
		} else {
			starty = tileHeight - 1;
			deltay = 9;
		}
		
		y1c = starty;
		y2c = 0;
		for (int y = 0; y < tileHeight; y++) {
			int num = weaveLookup[vram[offset] & 0xff] + (weaveLookup[vram[offset + 1] & 0xff] << 1);
			if (num != 0)
				transparent = false;
			
			x1c = startx;
			x2c = 0;
			for (int x = tileWidth; --x >= 0; ) {
				tempPix[pixix] = palette[paletteStart + (num & 3)];
				pixix += pixixdx;
				
				x2c += deltax;
				while (x2c > x1c) {
					x1c += tileWidth;
					num >>= 2;
				}
			}
			pixix += pixixdy;
			
			y2c += deltay;
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
		
		g.drawImage(im, x, y, 20);
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
		
		scaledWidth = tileWidth * 20;
		scaledHeight = tileHeight * 18;
		
		if (tileWidth != oldTW || tileHeight != oldTH) {
			// invalidate cache
			for (int r = 0; r < tileImage.length; r++)
				tileImage[r] = null;
			
			for (int r = 0; r < tileReadState.length; r++)
				tileReadState[r] = false;
		}
		
		tempPix = new int[tileWidth * tileHeight];
		
		frameBufferImage = Image.createImage(scaledWidth, scaledHeight);
		g = frameBufferImage.getGraphics();
	}
	
	public final void notifyRepainted() {
		frameDone = true;
	}
}
