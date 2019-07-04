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

/** This is the main controlling class for the emulation
*  It contains the code to emulate the Z80-like processor
*  found in the Gameboy, and code to provide the locations
*  in CPU address space that points to the correct area of
*  ROM/RAM/IO.
*/
public class Dmgcpu implements Runnable {
	// Constants for flags register
	
	/** Zero flag */
	private final int F_ZERO = 0x80;
	/** Subtract/negative flag */
	private final int F_SUBTRACT = 0x40;
	/** Half carry flag */
	private final int F_HALFCARRY = 0x20;
	/** Carry flag */
	private final int F_CARRY = 0x10;
	
	/** Used to set the speed of the emulator.  This controls how
		*  many instructions are executed for each horizontal line scanned
		*  on the screen.  Multiply by 154 to find out how many instructions
		*  per frame.
		*/
	protected int INSTRS_PER_HBLANK = 60; /* around 8 cycles per instruction, which is an average for several games... */
	protected int INSTRS_IN_MODE_0 = 30; // instructions in mode 0
	protected int INSTRS_IN_MODE_0_2 = 40; // sum of instructions in mode 0 and 2
	protected int INSTRS_PER_DIV = 33;

	// single speed values:
	protected final int BASE_INSTRS_PER_HBLANK = 60;
	protected final int BASE_INSTRS_IN_MODE_0 = 30;
	protected final int BASE_INSTRS_IN_MODE_0_2 = 40;
	protected final int BASE_INSTRS_PER_DIV = 33;
	
	// Constants for interrupts
	
	/** Vertical blank interrupt */
	public final int INT_VBLANK = 0x01;
	
	/** LCD Coincidence interrupt */
	public final int INT_LCDC = 0x02;
	
	/** TIMA (programmable timer) interrupt */
	public final int INT_TIMA = 0x04;
	
	/** Serial interrupt */
	public final int INT_SER = 0x08;
	
	/** P10 - P13 (Joypad) interrupt */
	public final int INT_P10 = 0x10;
	
	
	/** Registers: 8-bit */
	private int a, b, c, d, e, f;
	/** Registers: 16-bit */
	private int sp, hl;
	
	// decoder variables
	private byte[] decoderMemory;
	private int localPC;
	private int globalPC;
	private int decoderMaxCruise; // if localPC exceeds this, a new (half)bank should be found
	
	/** The number of instructions that have been executed since the last reset */
	private int instrCount;
	
	private int nextHBlank;
	private int nextTimaOverflow;
	private int nextInterruptEnable;
	private int nextTimedInterrupt;
	
	public boolean interruptsEnabled = false;
	public boolean interruptsArmed = false;
	protected boolean p10Requested;
	
	protected boolean gbcFeatures;
	protected int gbcRamBank;
	protected boolean hdmaRunning;

	
	// 0,1 = rom bank 0
	// 2,3 = mapped rom bank
	// 4 = vram (read only)
	// 5 = mapped cartram
	// 6 = main ram
	// 7 = main ram again (+ oam+reg)
	public byte[][] memory = new byte[8][];
	
	// 8kB main system RAM appears at 0xC000 in address space
	// 32kB for GBC
	private byte[] mainRam;
	
	// sprite ram, at 0xfe00
	public byte[] oam = new byte[0x100];
	
	/** registers, at 0xff00 */
	public byte[] registers = new byte[0x100];
	
	/** instrCount at the time register[4] was reset */
	private int divReset;

	private int instrsPerTima = 131;
	
	/** Current state of the buttons, bit set = pressed. */
	private int buttonState = 0;
	
	
	
	public GraphicsChip graphicsChip;
	public GBCanvas screen;
	public boolean terminate;
	
	public static boolean timing; // for performance testing
	
	
	// Cartridge:
	private String cartName;
	
	private int cartType;
	
	/** Contains the complete ROM image of the cartridge */
	// split into halfbanks of 0x2000 bytes
	private byte[][] rom;
	
	/** Contains the RAM on the cartridge */
	public byte[][] cartRam;
	
	/** The bank number which is currently mapped at 0x4000 in CPU address space */
	private int currentRomBank = 1;
	int loadedRomBanks; // number of lazily loaded ROM banks, including 0
	private int[] romBankQueue; // FIFO queue for lazily loaded ROM banks, exluding 0
	private int romBankQueueMark; // next index to remove from rom bank queue
	
	/** The RAM bank number which is currently mapped at 0xA000 in CPU address space */
	private int currentRamBank;
	
	private boolean mbc1LargeRamMode;
	private boolean cartRamEnabled;

	/** realtime clock */
	public byte[] rtcReg = new byte[5];
	private int lastRtcUpdate; // ms when rtc were synchronized
	
	private int[] incflags = new int[256];
	private int[] decflags = new int[256];
	
	
	public Dmgcpu(String cart, GBCanvas gbc) {
		cartName = cart;
		initCartridge();
		screen = gbc;
		
		graphicsChip = new GraphicsChip(this);
		
		memory[6] = mainRam;
		memory[7] = mainRam;
		
		interruptsEnabled = false;
		
		f = 0xB0;
		a = gbcFeatures ? 0x11 : 0x01;
		b = 0x00;
		c = 0x13;
		d = 0x00;
		e = 0xd8;
		hl = 0x014D;
		setPC(0x0100);
		sp = 0xFFFE;
		
		nextHBlank = INSTRS_PER_HBLANK;
		nextTimaOverflow = 0x7fffffff;
		nextInterruptEnable = 0x7fffffff;
		nextTimedInterrupt = INSTRS_PER_HBLANK;
		
		initIncDecFlags();

		ioHandlerReset();
	}
	
	public Dmgcpu(String cart, GBCanvas gbc, byte[] flatState, int offset) {
		cartName = cart;
		initCartridge();
		screen = gbc;
		
		graphicsChip = new GraphicsChip(this);
		
		memory[6] = mainRam;
		memory[7] = mainRam;
		
		unflatten(flatState, offset);
		
		initIncDecFlags();
	}
	
	private void initIncDecFlags() {
		incflags[0] = F_ZERO + F_HALFCARRY;
		for (int i = 0x10; i < 0x100; i += 0x10)
			incflags[i] = F_HALFCARRY;
		
		decflags[0] = F_ZERO + F_SUBTRACT;
		for (int i = 1; i < 0x100; i++)
			decflags[i] = F_SUBTRACT + (((i & 0x0f) == 0x0f) ? F_HALFCARRY : 0);
	}
	
	private void unflatten(byte[] flatState, int offset) {
		a = flatState[offset++] & 0xff;
		b = flatState[offset++] & 0xff;
		c = flatState[offset++] & 0xff;
		d = flatState[offset++] & 0xff;
		e = flatState[offset++] & 0xff;
		f = flatState[offset++] & 0xff;
		sp = flatState[offset++] & 0xff;
		sp = (sp << 8) + (flatState[offset++] & 0xff);
		hl = flatState[offset++] & 0xff;
		hl = (hl << 8) + (flatState[offset++] & 0xff);
		int pc = flatState[offset++] & 0xff;
		pc = (pc << 8) + (flatState[offset++] & 0xff);
		
		instrCount = GBCanvas.getInt(flatState, offset);
		offset += 4;
		nextHBlank = GBCanvas.getInt(flatState, offset);
		offset += 4;
		nextTimaOverflow = GBCanvas.getInt(flatState, offset);
		offset += 4;
		nextInterruptEnable = GBCanvas.getInt(flatState, offset);
		offset += 4;
		nextTimedInterrupt = GBCanvas.getInt(flatState, offset);
		offset += 4;
		
		int version = flatState[offset++] & 0xff;
		
		if (version <= 1) {
			// version 0 and 1 is original version, with interruptsEnabled encoded in version
			interruptsEnabled = (version != 0);
			if (gbcFeatures) {
				throw new RuntimeException("Incompatible saved game");
			}
		} else if (version == 2) {
			// new byte for interruptsEnabled, support suspended gbc games
			interruptsEnabled = (flatState[offset++] != 0);
			// gbc is ok
		}
		interruptsArmed = (flatState[offset++] != 0);
		
		System.arraycopy(flatState, offset, mainRam, 0, mainRam.length);
		offset += mainRam.length;
		System.arraycopy(flatState, offset, oam, 0, 0x0100);
		offset += 0x0100;
		System.arraycopy(flatState, offset, registers, 0, 0x0100);
		offset += 0x0100;

		divReset = GBCanvas.getInt(flatState, offset);
		offset += 4;
		instrsPerTima = GBCanvas.getInt(flatState, offset);
		offset += 4;
		
		// cartridge
		cartType = GBCanvas.getInt(flatState, offset);
		offset += 4;
		
		for (int i = 0; i < cartRam.length; i++) {
			System.arraycopy(flatState, offset, cartRam[i], 0, 0x2000);
			offset += 0x2000;
		}
		
		currentRomBank = GBCanvas.getInt(flatState, offset);
		offset += 4;
		mapRom(currentRomBank);
		
		currentRamBank = GBCanvas.getInt(flatState, offset);
		offset += 4;
		if (currentRamBank != 0)
			mapRam(currentRamBank);
		
		mbc1LargeRamMode = (flatState[offset++] != 0);
		cartRamEnabled = (flatState[offset++] != 0);
		
		if (version == 2) {
			// realtime clock
			System.arraycopy(flatState, offset, rtcReg, 0, rtcReg.length);
			offset += rtcReg.length;
		}
		
		offset = graphicsChip.unflatten(flatState, offset);
		
		if (gbcFeatures) {
			gbcRamBank = flatState[offset++] & 0xff;
			hdmaRunning = flatState[offset++] != 0;
		}

		setPC(pc);
		
		if (offset != flatState.length)
			throw new RuntimeException("unflatten offset error:" + offset + ", " + flatState.length);
		
	}
	
	public byte[] flatten() {
		int size = cartName.length() + 1 + 35 + mainRam.length + 0x0200 + 12 
				+ 0x2000 * cartRam.length + 10 + 0x2000 + 48 + 6 + rtcReg.length;
		if (gbcFeatures)
			size += 2 + 130 + 0x2000;
		
		byte[] flatState = new byte[size];
		int offset = 0;
		
		for (; offset < cartName.length(); offset++)
			flatState[offset] = (byte) cartName.charAt(offset);
		flatState[offset++] = (byte) 0; // terminate cart name
		
		flatState[offset++] = (byte) a;
		flatState[offset++] = (byte) b;
		flatState[offset++] = (byte) c;
		flatState[offset++] = (byte) d;
		flatState[offset++] = (byte) e;
		flatState[offset++] = (byte) f;
		flatState[offset++] = (byte) (sp >> 8);
		flatState[offset++] = (byte) sp;
		flatState[offset++] = (byte) (hl >> 8);
		flatState[offset++] = (byte) hl;
		int pc = localPC + globalPC;
		flatState[offset++] = (byte) (pc >> 8);
		flatState[offset++] = (byte) pc;
		
		GBCanvas.setInt(flatState, offset, instrCount);
		offset+=4;
		GBCanvas.setInt(flatState, offset, nextHBlank);
		offset+=4;
		GBCanvas.setInt(flatState, offset, nextTimaOverflow);
		offset+=4;
		GBCanvas.setInt(flatState, offset, nextInterruptEnable);
		offset+=4;
		GBCanvas.setInt(flatState, offset, nextTimedInterrupt);
		offset+=4;
		
		flatState[offset++] = 2; // version
		flatState[offset++] = (byte) (interruptsEnabled ? 1 : 0);
		flatState[offset++] = (byte) (interruptsArmed ? 1 : 0);
		
		System.arraycopy(mainRam, 0, flatState, offset, mainRam.length);
		offset += mainRam.length;
		System.arraycopy(oam, 0, flatState, offset, 0x0100);
		offset += 0x0100;
		System.arraycopy(registers, 0, flatState, offset, 0x0100);
		offset += 0x0100;
		
		GBCanvas.setInt(flatState, offset, divReset);
		offset+=4;
		GBCanvas.setInt(flatState, offset, instrsPerTima);
		offset+=4;
		
		// cartridge
		GBCanvas.setInt(flatState, offset, cartType);
		offset+=4;
		
		for (int j = 0; j < cartRam.length; j++) {
			System.arraycopy(cartRam[j], 0, flatState, offset, 0x2000);
			offset += 0x2000;
		}
		
		GBCanvas.setInt(flatState, offset, currentRomBank);
		offset+=4;
		GBCanvas.setInt(flatState, offset, currentRamBank);
		offset+=4;
		
		flatState[offset++] = (byte) (mbc1LargeRamMode ? 1 : 0);
		flatState[offset++] = (byte) (cartRamEnabled ? 1 : 0);
		
		System.arraycopy(rtcReg, 0, flatState, offset, rtcReg.length);
		offset += rtcReg.length;
		
		offset = graphicsChip.flatten(flatState, offset);
		
		if (gbcFeatures) {
			flatState[offset++] = (byte) gbcRamBank;
			flatState[offset++] = (byte) (hdmaRunning ? 1 : 0);
		}

		if (offset != flatState.length)
			throw new RuntimeException("flatten offset error:" + Integer.toString(offset, 16) + ", " + Integer.toString(flatState.length, 16));
		
		return flatState;
	}
	
	/** Perform a CPU address space read.  This maps all the relevant objects into the correct parts of
		*  the memory
		*/
	public final int addressRead(int addr) {
		if (addr < 0xa000) {
			return memory[addr >> 13][addr & 0x1fff];
		} else if (addr < 0xc000) {
			if (currentRamBank >= 8) { // real time clock
				rtcSync();
				return rtcReg[currentRamBank-8];
			} else
				return memory[addr >> 13][addr & 0x1fff];
		} else if ((addr & 0x1000) == 0) {
			return mainRam[addr & 0x0fff];
		} else if (addr < 0xfe00) {
			return mainRam[(addr & 0x0fff) + gbcRamBank * 0x1000];
		} else if (addr < 0xFF00) {
			return (oam[addr - 0xFE00] & 0x00FF);
		} else {
			return ioRead(addr - 0xFF00);
		}
	}
	
	/** Performs a CPU address space write.  Maps all of the relevant object into the right parts of
		*  memory.
		*/
	public final void addressWrite(int addr, int data) {
		int bank = (addr >> 12) & 0x0f;
		switch (bank) {
			case 0x0:
			case 0x1:
			case 0x2:
			case 0x3:
			case 0x4:
			case 0x5:
			case 0x6:
			case 0x7:
				cartridgeWrite(addr, data);
				break;

			case 0x8:
			case 0x9:
				graphicsChip.addressWrite(addr - 0x8000, (byte) data);
				break;

			case 0xA:
			case 0xB:
				cartridgeWrite(addr, data);
				break;

			case 0xC:
				mainRam[addr - 0xC000] = (byte) data;
				break;
				
			case 0xD:
				mainRam[addr - 0xD000 + gbcRamBank * 0x1000] = (byte) data;
				break;

			case 0xE:
				mainRam[addr - 0xE000] = (byte) data;
				break;
				
			case 0xF:
				if (addr < 0xFE00) {
					mainRam[addr - 0xF000 + gbcRamBank * 0x1000] = (byte) data;
				} else if (addr < 0xFF00) {
					oam[addr - 0xFE00] = (byte) data;
				} else {
					ioWrite(addr - 0xFF00, data);
				}
				break;
		}
	}
	
	private final void pushPC() {
		int pc = globalPC + localPC;
		if ((sp >> 13) == 6) {
			mainRam[--sp - 0xC000] = (byte) (pc >> 8);
			mainRam[--sp - 0xC000] = (byte) (pc);
		} else {
			addressWrite(--sp, pc >> 8);
			addressWrite(--sp, pc & 0xFF);
		}
	}
	
	private final void popPC() {
		if (sp >> 13 == 6) {
			setPC((mainRam[sp++ - 0xc000] & 0xff) + ((mainRam[sp++ - 0xc000] & 0xff) << 8));
		} else {
			setPC((addressRead(sp++) & 0xff) + ((addressRead(sp++) & 0xff) << 8));
		}
	}
	
	/** Performs a read of a register by internal register number */
	private final int registerRead(int regNum) {
		switch (regNum) {
			case 0:
				return b;
			case 1:
				return c;
			case 2:
				return d;
			case 3:
				return e;
			case 4:
				return (hl >> 8);
			case 5:
				return (hl & 0xFF);
			case 6:
				return addressRead(hl) & 0xff;
			case 7:
				return a;
			default:
				return -1;
		}
	}
	
	/** Performs a write of a register by internal register number */
	private final void registerWrite(int regNum, int data) {
		switch (regNum) {
			case 0:
				b = data;
				return;
			case 1:
				c = data;
				return;
			case 2:
				d = data;
				return;
			case 3:
				e = data;
				return;
			case 4:
				// h
				hl = (hl & 0x00FF) | (data << 8);
				return;
			case 5:
				// l
				hl = (hl & 0xFF00) | data;
				return;
			case 6:
				// (hl)
				addressWrite(hl, data);
				return;
			case 7:
				a = data;
				return;
			default:
				return;
		}
	}
	
	private void performHdma() {
		int dmaSrc = ((registers[0x51] & 0xff) << 8)
				+ ((registers[0x52] & 0xff) & 0xF0);
		int dmaDst = ((registers[0x53] & 0x1F) << 8)
				+ (registers[0x54] & 0xF0) + 0x8000;

		for (int r = 0; r < 16; r++) {
			addressWrite(dmaDst + r, addressRead(dmaSrc + r));
		}

		dmaSrc += 16;
		dmaDst += 16;
		registers[0x51] = (byte) ((dmaSrc & 0xFF00) >> 8);
		registers[0x52] = (byte) (dmaSrc & 0x00F0);
		registers[0x53] = (byte) ((dmaDst & 0x1F00) >> 8);
		registers[0x54] = (byte) (dmaDst & 0x00F0);

		if (registers[0x55] == 0)
			hdmaRunning = false;
		registers[0x55]--;
	}

	/** If an interrupt is enabled an the interrupt register shows that it has occured, jump to
		*  the relevant interrupt vector address
		*/
	private final void checkInterrupts() {
		if (p10Requested) {
			p10Requested = false;
			if ((registers[0xff] & INT_P10) != 0) {
				registers[0x0f] |= INT_P10;
			}
			
			interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
			if (!interruptsArmed) {
				// button pressed, but game wasn't interested 
				return;
			}
		}
		
		pushPC();
		
		int mask = registers[0xff] & registers[0x0f];
		
		if ((mask & INT_VBLANK) != 0) {
			setPC(0x40);
			registers[0x0f] -= INT_VBLANK;
		} else if ((mask & INT_LCDC) != 0) {
			setPC(0x48);
			registers[0x0f] -= INT_LCDC;
		} else if ((mask & INT_TIMA) != 0) {
			setPC(0x50);
			registers[0x0f] -= INT_TIMA;
		} else if ((mask & INT_SER) != 0) {
			setPC(0x58);
			registers[0x0f] -= INT_SER;
		} else if ((mask & INT_P10) != 0) {
			setPC(0x60);
			registers[0x0f] -= INT_P10;
		} else {
			throw new RuntimeException("concurrent modification exception: " + mask + " " +  + registers[0xff] + " " +  + registers[0x0f]);
		}
		
		interruptsEnabled = false;
		interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
	}
	
	/** Check for interrupts that need to be initiated */
	private final void initiateInterrupts() {
		if (instrCount - nextHBlank >= 0) {
			nextHBlank += INSTRS_PER_HBLANK;
			
			// belated increase, since the game hopefully only cares during blanks
			// this *should* have been done when exiting the last hblank and moving
			// to mode 2, but this is faster.
			registers[0x44]++;
			
			if ((gbcFeatures) && (hdmaRunning)) {
				performHdma();
			}

			// send the line to graphic chip
			int line = registers[0x44] & 0xff;
			
			if (line == 153) {
				// Resetting at line 154 would seem more correct, but seems to work less well.
				// (And all timing is approximate anyways.)
				line = registers[0x44] = 0;
				// falls through to the line < 144 case below
			}

			if (line < 144) {
				graphicsChip.notifyScanline(line);

				// do a hblank (mode 0)
				if (((registers[0x40] & 0x80) != 0) && ((registers[0xff] & INT_LCDC) != 0)) {
					if (((registers[0x41] & 0x40) != 0) && ((registers[0x45] & 0xff) == line)) {
						// trigger "lyc coincidence" interrupt
						interruptsArmed = true;
						registers[0x0f] |= INT_LCDC;
					} else if (((registers[0x41] & 0x08) != 0)) {
						// trigger "mode 0 entered" interrupt
						interruptsArmed = true;
						registers[0x0f] |= INT_LCDC;
					}
				}
			} else if (line == 144) {
				// (note: the vblank is not sent at the beginning of line 144, but rather when the
				// next hblank would occur on that line. I don't know if it makes a difference.)
				
				// whole frame done, draw buffer and start vblank
				graphicsChip.vBlank();
				
				if (((registers[0x40] & 0x80) != 0) && ((registers[0xff] & INT_VBLANK) != 0)) {
					interruptsArmed = true;
					registers[0x0f] |= INT_VBLANK;
					
					if (((registers[0x41] & 0x10) != 0) && ((registers[0xff] & INT_LCDC) != 0)) {
						// VBLANK LCDC
						// armed is already set
						registers[0x0f] |= INT_LCDC;
					}
				}
			}
			
			nextTimedInterrupt = nextHBlank - nextTimaOverflow < 0 ? nextHBlank : nextTimaOverflow;
			nextTimedInterrupt = nextInterruptEnable - nextTimedInterrupt < 0 ? nextInterruptEnable
					: nextTimedInterrupt;
		} else if (instrCount - nextTimaOverflow >= 0) {
			nextTimaOverflow += instrsPerTima * (0x100 - registers[0x06]);
			
			if ((registers[0xff] & INT_TIMA) != 0) {
				interruptsArmed = true;
				registers[0x0f] |= INT_TIMA;
			}
			
			nextTimedInterrupt = nextHBlank - nextTimaOverflow < 0 ? nextHBlank : nextTimaOverflow;
			nextTimedInterrupt = nextInterruptEnable - nextTimedInterrupt < 0 ? nextInterruptEnable
					: nextTimedInterrupt;
		} else if (instrCount - nextInterruptEnable >= 0) {
			interruptsEnabled = true;
			
			nextInterruptEnable = 0x7fffffff;
			nextTimedInterrupt = nextHBlank - nextTimaOverflow < 0 ? nextHBlank : nextTimaOverflow;
		}
	}
	
	public final void setPC(int pc) {
		if (pc < 0xff00) {
			decoderMemory = memory[pc >> 13];
			localPC = pc & 0x1fff;
			globalPC = pc & 0xe000;
			decoderMaxCruise = (pc < 0xe000) ? 0x1ffd : 0x1dfd;
			if (gbcRamBank > 1 && pc >= 0xC000)
				decoderMaxCruise &= 0x0fff; // can't cruise in switched ram bank
		} else {
			decoderMemory = registers;
			localPC = pc & 0xff;
			globalPC = 0xff00;
			decoderMaxCruise = 0xfd;
		}
	}
	
	public final void run() {
		terminate = false;
		
		int newf = 0;
		int b1, b2, offset, b3;
		
		int f_szhc = F_SUBTRACT + F_ZERO + F_HALFCARRY;
		
		int startTime = (int) System.currentTimeMillis();
		
		graphicsChip.timer = startTime;
		
		while (!terminate) {
			instrCount++;
			
			/* debug code for timing 18000000 instructions, which is roughly the time for a demo for SML
			if (timing && instrCount >= 18000000) {
				int time = ((int) System.currentTimeMillis() - startTime + 50)/100;
				MeBoy.log("18M time: " + time);
				terminate = true;
				screen.showFps = true;
				graphicsChip.lastSkipCount = time;
				screen.repaint();
			}*/
			
			if (localPC <= decoderMaxCruise) {
				b1 = decoderMemory[localPC++] & 0xff;
				offset = decoderMemory[localPC];
				b2 = offset & 0xff;
				b3 = decoderMemory[localPC + 1] & 0xff;
			} else {
				int pc = localPC + globalPC;
				b1 = addressRead(pc++) & 0xff;
				offset = addressRead(pc);
				b2 = offset & 0xff;
				b3 = addressRead(pc + 1) & 0xff;
				setPC(pc);
			}
			
			switch (b1) {
				case 0x00: // NOP
					break;
				case 0x01: // LD BC, nn
					localPC += 2;
					b = b3;
					c = b2;
					break;
				case 0x02: // LD (BC), A
					addressWrite((b << 8) | c, a);
					break;
				case 0x03: // INC BC
					c++;
					if (c == 0x0100) {
						c = 0;
						b = (b + 1) & 0xff;
					}
					break;
				case 0x04: // INC B
					b = (b + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b];
					break;
				case 0x05: // DEC B
					b = (b - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b];
					
					break;
				case 0x06: // LD B, nn
					localPC++;
					b = b2;
					break;
				case 0x07: // RLC A
					if (a >= 0x80) {
						f = F_CARRY;
						a = ((a << 1) + 1) & 0xff;
					} else if (a == 0) {
						f = F_ZERO;
					} else {
						a <<= 1;
						f = 0;
					}
					break;
				case 0x08: // LD (nnnn), SP
					localPC += 2;
					
					addressWrite((b3 << 8) + b2, sp & 0xFF);
					addressWrite((b3 << 8) + b2 + 1, sp >> 8);
					break;
				case 0x09: // ADD HL, BC
					hl = (hl + ((b << 8) + c));
					if ((hl & 0xffff0000) != 0) {
						f = (f & F_ZERO) | F_CARRY; // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x0A: // LD A, (BC)
					a = addressRead((b << 8) + c) & 0xff;
					break;
				case 0x0B: // DEC BC
					c--;
					if (c < 0) {
						c = 0xFF;
						b = (b - 1) & 0xff;
					}
					break;
				case 0x0C: // INC C
					c = (c + 1) & 0xff;
					f = (f & F_CARRY) | incflags[c];
					break;
				case 0x0D: // DEC C
					c = (c - 1) & 0xff;
					f = (f & F_CARRY) | decflags[c];
					break;
				case 0x0E: // LD C, nn
					localPC++;
					c = b2;
					break;
				case 0x0F: // RRC A
					if ((a & 0x01) == 0x01) {
						f = F_CARRY;
					} else {
						f = 0;
					}
					a >>= 1;
					if ((f & F_CARRY) != 0) {
						a |= 0x80;
					}
					if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0x10: // STOP
					localPC++;
					
					if (gbcFeatures) {
						if ((registers[0x4D] & 0x01) == 1) {
							int newKey1Reg = registers[0x4D] & 0xFE;
							int multiplier = 1;
							if ((newKey1Reg & 0x80) == 0x80) {
								newKey1Reg &= 0x7F;
							} else {
								multiplier = 2;
								newKey1Reg |= 0x80;
							}
							
							INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK * multiplier;
							INSTRS_PER_DIV = BASE_INSTRS_PER_DIV * multiplier;
							INSTRS_IN_MODE_0 = BASE_INSTRS_IN_MODE_0 * multiplier;
							INSTRS_IN_MODE_0_2 = BASE_INSTRS_IN_MODE_0_2 * multiplier;

							registers[0x4D] = (byte) newKey1Reg;
						}
					}

					break;
				case 0x11: // LD DE, nnnn
					localPC += 2;
					d = b3;
					e = b2;
					break;
				case 0x12: // LD (DE), A
					addressWrite((d << 8) + e, a);
					break;
				case 0x13: // INC DE
					e++;
					if (e == 0x0100) {
						e = 0;
						d = (d + 1) & 0xff;
					}
					break;
				case 0x14: // INC D
					d = (d + 1) & 0xff;
					f = (f & F_CARRY) | incflags[d];
					break;
				case 0x15: // DEC D
					d = (d - 1) & 0xff;
					f = (f & F_CARRY) | decflags[d];
					break;
				case 0x16: // LD D, nn
					localPC++;
					d = b2;
					break;
				case 0x17: // RL A
					if ((a & 0x80) == 0x80) {
						newf = F_CARRY;
					} else {
						newf = 0;
					}
					a <<= 1;

					if ((f & F_CARRY) != 0) {
						a |= 1;
					}
					
					a &= 0xFF;
					if (a == 0) {
						newf |= F_ZERO;
					}
					f = newf;
					break;
				case 0x18: // JR nn
					localPC += 1 + offset;
					if (localPC < 0 || localPC > decoderMaxCruise) {
						// switch bank
						setPC(localPC + globalPC);
					}
					
					break;
				case 0x19: // ADD HL, DE
					hl += ((d << 8) + e);
					if ((hl & 0xFFFF0000) != 0) {
						f = ((f & F_ZERO) | F_CARRY); // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x1A: // LD A, (DE)
					a = (addressRead((d << 8) + e)) & 0xff;
					break;
				case 0x1B: // DEC DE
					e--;
					if (e < 0) {
						e = 0xFF;
						d = (d-1) & 0xff;
					}
					break;
				case 0x1C: // INC E
					e = (e + 1) & 0xff;
					f = (f & F_CARRY) | incflags[e];
					break;
				case 0x1D: // DEC E
					e = (e - 1) & 0xff;
					f = (f & F_CARRY) | decflags[e];
					break;
				case 0x1E: // LD E, nn
					localPC++;
					e = b2;
					break;
				case 0x1F: // RR A
					if ((a & 0x01) == 0x01) {
						newf = F_CARRY;
					} else {
						newf = 0;
					}
					a >>= 1;

					if ((f & F_CARRY) != 0) {
						a |= 0x80;
					}

					if (a == 0) {
						newf |= F_ZERO;
					}
					f = newf;
					break;
				case 0x20: // JR NZ, nn
					if (f < F_ZERO) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC++;
					}
					break;
				case 0x21: // LD HL, nnnn
					localPC += 2;
					hl = (b3 << 8) + b2;
					break;
				case 0x22: // LD (HL+), A
					addressWrite(hl++, a);
					break;
				case 0x23: // INC HL
					hl = (hl + 1) & 0xFFFF;
					break;
				case 0x24: // INC H
					b2 = ((hl >> 8) + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b2];
					hl = (hl & 0xff) + (b2 << 8);
					break;
				case 0x25: // DEC H
					b2 = ((hl >> 8) - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b2];
					hl = (hl & 0xff) + (b2 << 8);
					break;
				case 0x26: // LD H, nn
					localPC++;
					hl = (hl & 0xFF) | (b2 << 8);
					break;
				case 0x27: // DAA
					int upperNibble = (a >> 4) & 0x0f;
					int lowerNibble = a & 0x0f;
					
					newf = (f & (F_SUBTRACT | F_CARRY));
					
					if ((f & F_SUBTRACT) == 0) {
						if ((f & F_CARRY) == 0) {
							if ((upperNibble <= 8) && (lowerNibble >= 0xA) && ((f & F_HALFCARRY) == 0)) {
								a += 0x06;
							}
							
							if ((upperNibble <= 9) && (lowerNibble <= 0x3) && ((f & F_HALFCARRY) != 0)) {
								a += 0x06;
							}
							
							if ((upperNibble >= 0xA) && (lowerNibble <= 0x9) && ((f & F_HALFCARRY) == 0)) {
								a += 0x60;
								newf |= F_CARRY;
							}
							
							if ((upperNibble >= 0x9) && (lowerNibble >= 0xA) && ((f & F_HALFCARRY) == 0)) {
								a += 0x66;
								newf |= F_CARRY;
							}
							
							if ((upperNibble >= 0xA) && (lowerNibble <= 0x3) && ((f & F_HALFCARRY) != 0)) {
								a += 0x66;
								newf |= F_CARRY;
							}
							
						} else { // carry is set
							
							if ((upperNibble <= 0x2) && (lowerNibble <= 0x9) && ((f & F_HALFCARRY) == 0)) {
								a += 0x60;
							}
							
							if ((upperNibble <= 0x2) && (lowerNibble >= 0xA) && ((f & F_HALFCARRY) == 0)) {
								a += 0x66;
							}
							
							if ((upperNibble <= 0x3) && (lowerNibble <= 0x3) && ((f & F_HALFCARRY) != 0)) {
								a += 0x66;
							}
						}
						
					} else { // subtract is set
						
						if ((f & F_CARRY) == 0) {
							if ((upperNibble <= 0x8) && (lowerNibble >= 0x6) && ((f & F_HALFCARRY) != 0)) {
								a += 0xFA;
							}
						} else { // Carry is set
							if ((upperNibble >= 0x7) && (lowerNibble <= 0x9) && ((f & F_HALFCARRY) == 0)) {
								a += 0xA0;
							}
							
							if ((upperNibble >= 0x6) && (lowerNibble >= 0x6) && ((f & F_HALFCARRY) != 0)) {
								a += 0x9A;
							}
						}
					}
						
					a &= 0xff;
					if (a == 0)
						newf |= F_ZERO;
						
					// halfcarry is wrong
					f = newf;
					
					break;
				case 0x28: // JR Z, nn
					if (f >= F_ZERO) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC++;
					}
					break;
				case 0x29: // ADD HL, HL
					hl *= 2;
					if ((hl & 0xFFFF0000) != 0) {
						f = (f & F_ZERO) | F_CARRY; // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x2A: // LDI A, (HL)
					a = addressRead(hl++) & 0xff;
					break;
				case 0x2B: // DEC HL
					hl = (hl - 1) & 0xffff;
					break;
				case 0x2C: // INC L
					b2 = (hl + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b2];
					hl = (hl & 0xff00) + b2;
					break;
				case 0x2D: // DEC L
					b2 = (hl - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b2];
					hl = (hl & 0xff00) + b2;
					break;
				case 0x2E: // LD L, nn
					localPC++;
					hl = (hl & 0xFF00) | b2;
					break;
				case 0x2F: // CPL A
					a = ((~a) & 0x00FF);
					f |= F_SUBTRACT | F_HALFCARRY;
					break;
				case 0x30: // JR NC, nn
					if ((f & F_CARRY) == 0) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC++;
					}
					break;
				case 0x31: // LD SP, nnnn
					localPC += 2;
					sp = (b3 << 8) + b2;
					break;
				case 0x32:
					addressWrite(hl--, a); // LD (HL-), A
					break;
				case 0x33: // INC SP
					sp = (sp + 1) & 0xFFFF;
					break;
				case 0x34: // INC (HL)
					b2 = (addressRead(hl) + 1) & 0xff;
					f = (f & F_CARRY) | incflags[b2];
					addressWrite(hl, b2);
					break;
				case 0x35: // DEC (HL)
					b2 = (addressRead(hl) - 1) & 0xff;
					f = (f & F_CARRY) | decflags[b2];
					addressWrite(hl, b2);
					break;
				case 0x36: // LD (HL), nn
					localPC++;
					addressWrite(hl, b2);
					break;
				case 0x37: // SCF
					f = (f & F_ZERO) | F_CARRY;
					break;
				case 0x38: // JR C, nn
					if ((f & F_CARRY) != 0) {
						localPC += 1 + offset;
						if (localPC < 0 || localPC > decoderMaxCruise) {
							// switch bank
							setPC(localPC + globalPC);
						}
					} else {
						localPC += 1;
					}
					break;
				case 0x39: // ADD HL, SP
					hl += sp;
					if ((hl & 0xFFFF0000) != 0) {
						f = (f & F_ZERO) | F_CARRY; // halfcarry is wrong
						hl &= 0xFFFF;
					} else {
						f &= F_ZERO; // halfcarry is wrong
					}
					break;
				case 0x3A: // LD A, (HL-)
					a = addressRead(hl--) & 0xff;
					break;
				case 0x3B: // DEC SP
					sp = (sp - 1) & 0xFFFF;
					break;
				case 0x3C: // INC A
					a = (a + 1) & 0xff;
					f = (f & F_CARRY) | incflags[a];
					break;
				case 0x3D: // DEC A
					a = (a - 1) & 0xff;
					f = (f & F_CARRY) | decflags[a];
					break;
				case 0x3E: // LD A, nn
					localPC++;
					a = b2;
					break;
				case 0x3F: // CCF
					f = (f & (F_CARRY | F_ZERO)) ^ F_CARRY;
					break;
					
					// B = r
				case 0x40: break;
				case 0x41: b = c; break;
				case 0x42: b = d; break;
				case 0x43: b = e; break;
				case 0x44: b = hl >> 8; break;
				case 0x45: b = hl & 0xFF; break;
				case 0x46: b = addressRead(hl) & 0xff; break;
				case 0x47: b = a; break;
					
					// C = r
				case 0x48: c = b; break;
				case 0x49: break;
				case 0x4a: c = d; break;
				case 0x4b: c = e; break;
				case 0x4c: c = hl >> 8; break;
				case 0x4d: c = hl & 0xFF; break;
				case 0x4e: c = addressRead(hl) & 0xff; break;
				case 0x4f: c = a; break;
					
					// D = r
				case 0x50: d = b; break;
				case 0x51: d = c; break;
				case 0x52: break;
				case 0x53: d = e; break;
				case 0x54: d = hl >> 8; break;
				case 0x55: d = hl & 0xFF; break;
				case 0x56: d = addressRead(hl) & 0xff; break;
				case 0x57: d = a; break;
					
					// E = r
				case 0x58: e = b; break;
				case 0x59: e = c; break;
				case 0x5a: e = d; break;
				case 0x5b: break;
				case 0x5c: e = hl >> 8; break;
				case 0x5d: e = hl & 0xFF; break;
				case 0x5e: e = addressRead(hl) & 0xff; break;
				case 0x5f: e = a; break;
					
					
					// h = r
				case 0x60: hl = (hl & 0xFF) | (b << 8); break;
				case 0x61: hl = (hl & 0xFF) | (c << 8); break;
				case 0x62: hl = (hl & 0xFF) | (d << 8); break;
				case 0x63: hl = (hl & 0xFF) | (e << 8); break;
				case 0x64: break;
				case 0x65: hl = (hl & 0xFF) * 0x0101; break;
				case 0x66: hl = (hl & 0xFF) | ((addressRead(hl) & 0xff) << 8); break;
				case 0x67: hl = (hl & 0xFF) | (a << 8); break;
					
					// l = r
				case 0x68: hl = (hl & 0xFF00) | b; break;
				case 0x69: hl = (hl & 0xFF00) | c; break;
				case 0x6a: hl = (hl & 0xFF00) | d; break;
				case 0x6b: hl = (hl & 0xFF00) | e; break;
				case 0x6c: hl = (hl >> 8) * 0x0101; break;
				case 0x6d: break;
				case 0x6e: hl = (hl & 0xFF00) | (addressRead(hl) & 0xff); break;
				case 0x6f: hl = (hl & 0xFF00) | a; break;
					
					// (hl) = r
				case 0x70: addressWrite(hl, b); break;
				case 0x71: addressWrite(hl, c); break;
				case 0x72: addressWrite(hl, d); break;
				case 0x73: addressWrite(hl, e); break;
				case 0x74: addressWrite(hl, hl >> 8); break;
				case 0x75: addressWrite(hl, hl & 0xFF); break;
				case 0x76: // HALT
					interruptsEnabled = true;
					
					while (!interruptsArmed) {
						instrCount = nextTimedInterrupt;
						
						initiateInterrupts();
					}
						break;
				case 0x77: addressWrite(hl, a); break;
					
					// LD A, n:
				case 0x78: a = b; break;
				case 0x79: a = c; break;
				case 0x7a: a = d; break;
				case 0x7b: a = e; break;
				case 0x7c: a = (hl >> 8); break;
				case 0x7d: a = (hl & 0xFF); break;
				case 0x7e: a = addressRead(hl) & 0xff; break;
				case 0x7f: break;
					
				// ALU, 0x80 - 0xbf
					
				case 0xA7: // AND A, A
					if (a == 0) {
						f = F_HALFCARRY + F_ZERO;
					} else {
						f = F_HALFCARRY;
					}
					break;
					
				case 0xAF: // XOR A, A (== LD A, 0)
					a = 0;
					f = F_ZERO; // Set zero flag
					break;
				case 0xC0: // RET NZ
					if (f < F_ZERO) {
						popPC();
					}
					break;
				case 0xC1: // POP BC
					c = addressRead(sp++) & 0xff;
					b = addressRead(sp++) & 0xff;
					break;
				case 0xC2: // JP NZ, nnnn
					if (f < F_ZERO) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xC3: // JP nnnn
					setPC((b3 << 8) + b2);
					break;
				case 0xC4: // CALL NZ, nnnn
					localPC += 2;
					if (f < F_ZERO) {
						pushPC();
						setPC((b3 << 8) + b2);
					}
					break;
				case 0xC5: // PUSH BC
					addressWrite(--sp, b);
					addressWrite(--sp, c);
					break;
				case 0xC6: // ADD A, nn
					localPC++;
					if ((a & 0x0F) + (b2 & 0x0F) >= 0x10) {
						f = F_HALFCARRY;
					} else {
						f = 0;
					}
					
					a += b2;
					
					if (a > 0xff) {
						f |= F_CARRY;
						a &= 0xff;
					}
					
					if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xC7: // RST 00
					pushPC();
					setPC(0x00);
					break;
				case 0xC8: // RET Z
					if (f >= F_ZERO) {
						popPC();
					}
					break;
				case 0xC9: // RET
					popPC();
					break;
				case 0xCA: // JP Z, nnnn
					if (f >= F_ZERO) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xCB: // Shift/bit test
					localPC++;
					int regNum = b2 & 0x07;
					int data = registerRead(regNum);
					
					/*
					
					00ooorrr = operation ooo on register rrr
					01bbbrrr = test bit bbb of register rrr
					10bbbrrr = reset bit bbb of register rrr
					11bbbrrr = set bit bbb of register rrr
					
					*/
					
					if ((b2 & 0xC0) == 0) {
						switch ((b2 & 0xF8)) {
							case 0x00: // RLC A
								f = 0;
								if (data >= 0x80) {
									f = F_CARRY;
								}
								data = (data << 1) & 0xff;
								if ((f & F_CARRY) != 0) {
									data |= 1;
								}
								
								break;
							case 0x08: // RRC A
								f = 0;
								if ((data & 0x01) != 0) {
									f = F_CARRY;
								}
								data >>= 1;
								if ((f & F_CARRY) != 0) {
									data |= 0x80;
								}
								break;
							case 0x10: // RL r
								if (data >= 0x80) {
									newf = F_CARRY;
								} else {
									newf = 0;
								}
								data = (data << 1) & 0xff;
								
								if ((f & F_CARRY) != 0) {
									data |= 1;
								}
								f = newf;
								break;
							case 0x18: // RR r
								if ((data & 0x01) != 0) {
									newf = F_CARRY;
								} else {
									newf = 0;
								}
								data >>= 1;
								
								if ((f & F_CARRY) != 0) {
									data |= 0x80;
								}
									
								f = newf;
								break;
							case 0x20: // SLA r
								f = 0;
								if ((data & 0x80) != 0) {
									f = F_CARRY;
								}
								
								data = (data << 1) & 0xff;
								break;
							case 0x28: // SRA r
								f = 0;
								if ((data & 0x01) != 0) {
									f = F_CARRY;
								}
								
								data = (data & 0x80) + (data >> 1); // i.e. duplicate high bit=sign
								break;
							case 0x30: // SWAP r
								data = (((data & 0x0F) << 4) | (data >> 4));
								f = 0;
								
								break;
							case 0x38: // SRL r
								f = 0;
								if ((data & 0x01) != 0) {
									f = F_CARRY;
								}
								
								data >>= 1;
								break;
						}
						
						if (data == 0) {
							f |= F_ZERO;
						}
						registerWrite(regNum, data);
					} else {
						int bitMask = 1 << ((b2 & 0x38) >> 3);
						
						if ((b2 & 0xC0) == 0x40) { // BIT n, r
							f = ((f & F_CARRY) | F_HALFCARRY);
							if ((data & bitMask) == 0) {
								f |= F_ZERO;
							}
						} else if ((b2 & 0xC0) == 0x80) { // RES n, r
							registerWrite(regNum, (data & (0xFF - bitMask)));
						} else if ((b2 & 0xC0) == 0xC0) { // SET n, r
							registerWrite(regNum, (data | bitMask));
						}
					}
					
					break;
				case 0xCC: // CALL Z, nnnnn
					localPC += 2;
					if (f >= F_ZERO) {
						pushPC();
						setPC((b3 << 8) + b2);
					}
					break;
				case 0xCD: // CALL nnnn
					localPC += 2;
					pushPC();
					setPC((b3 << 8) + b2);
					break;
				case 0xCE: // ADC A, nn
					localPC++;
					
					if ((f & F_CARRY) != 0) {
						b2++;
					}
					if ((a & 0x0F) + (b2 & 0x0F) >= 0x10) {
						f = F_HALFCARRY;
					} else {
						f = 0;
					}
					
					a += b2;
				
					if (a > 0xff) {
						f |= F_CARRY;
						a &= 0xff;
					}
					
					if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xCF: // RST 08
					pushPC();
					setPC(0x08);
					break;
				case 0xD0: // RET NC
					if ((f & F_CARRY) == 0) {
						popPC();
					}
					break;
				case 0xD1: // POP DE
					e = addressRead(sp++) & 0xff;
					d = addressRead(sp++) & 0xff;
					break;
				case 0xD2: // JP NC, nnnn
					if ((f & F_CARRY) == 0) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xD4: // CALL NC, nnnn
					localPC += 2;
					if ((f & F_CARRY) == 0) {
						pushPC();
						setPC((b3 << 8) + b2);
					}
					break;
				case 0xD5: // PUSH DE
					addressWrite(--sp, d);
					addressWrite(--sp, e);
					break;
				case 0xD6: // SUB A, nn
					localPC++;
					
					f = F_SUBTRACT;
					
					if ((a & 0x0F) < (b2 & 0x0F)) {
						f |= F_HALFCARRY;
					}
					
					a -= b2;
					
					if (a < 0) {
						f |= F_CARRY;
						a &= 0xff;
					} else if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xD7: // RST 10
					pushPC();
					setPC(0x10);
					break;
				case 0xD8: // RET C
					if ((f & F_CARRY) != 0) {
						popPC();
					}
					break;
				case 0xD9: // RETI
					interruptsEnabled = true;
					popPC();
					break;
				case 0xDA: // JP C, nnnn
					if ((f & F_CARRY) != 0) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xDC: // CALL C, nnnn
					localPC += 2;
					if ((f & F_CARRY) != 0) {
						pushPC();
						setPC((b3 << 8) + b2);
					}
					break;
				case 0xDE : // SBC A, nn
					localPC++;
					if ((f & F_CARRY) != 0) {
						b2++;
					}
					
					f = F_SUBTRACT;
					if ((a & 0x0F) < (b2 & 0x0F)) {
						f |= F_HALFCARRY;
					}
					
					a -= b2;
					
					if (a < 0) {
						f |= F_CARRY;
						a &= 0xff;
					} else if (a == 0) {
						f |= F_ZERO;
					}
					break;
				case 0xDF: // RST 18
					pushPC();
					setPC(0x18);
					break;
				case 0xE0: // LDH (FFnn), A
					localPC++;
					ioWrite(b2, a);
					break;
				case 0xE1: // POP HL
					hl = ((addressRead(sp + 1) & 0xff) << 8) + (addressRead(sp) & 0xff);
					sp += 2;
					break;
				case 0xE2: // LDH (FF00 + C), A
					ioWrite(c, a);
					break;
				case 0xE5: // PUSH HL
					if ((sp >> 13) == 6) {
						mainRam[--sp - 0xC000] = (byte) (hl >> 8);
						mainRam[--sp - 0xC000] = (byte) (hl);
					} else {
						addressWrite(--sp, hl >> 8);
						addressWrite(--sp, hl & 0x00FF);
					}
					break;
				case 0xE6: // AND nn
					localPC++;
					a &= b2;
					f = 0;
					if (a == 0)
						f = F_ZERO;
					break;
				case 0xE7: // RST 20
					pushPC();
					setPC(0x20);
					break;
				case 0xE8: // ADD SP, nn
					localPC++;
					
					sp += offset;
					f = 0;
					if (sp > 0xffff || sp < 0) {
						sp &= 0xffff;
						f = F_CARRY;
					}
					break;
				case 0xE9: // JP (HL)
					setPC(hl);
					break;
				case 0xEA: // LD (nnnn), A
					localPC += 2;
					addressWrite((b3 << 8) + b2, a);
					break;
				case 0xEE: // XOR A, nn
					localPC++;
					a ^= b2;
					f = 0;
					if (a == 0)
						f = F_ZERO;
					break;
				case 0xEF: // RST 28
					pushPC();
					setPC(0x28);
					break;
				case 0xF0: // LDH A, (FFnn)
					localPC++;
					if (b2 > 0x41)
						a = registers[b2] & 0xff;
					else
						a = ioRead(b2) & 0xff;
					
					break;
				case 0xF1: // POP AF
					f = addressRead(sp++) & 0xf0;
					a = addressRead(sp++) & 0xff;
					break;
				case 0xF2: // LD A, (FF00 + C)
					a = ioRead(c) & 0xff;
					break;
				case 0xF3: // DI
					interruptsEnabled = false;
					break;
				case 0xF5: // PUSH AF
					addressWrite(--sp, a);
					addressWrite(--sp, f);
					break;
				case 0xF6: // OR A, nn
					localPC++;
					a |= b2;
					f = 0;
					if (a == 0) {
						f = F_ZERO;
					}
					break;
				case 0xF7: // RST 30
					pushPC();
					setPC(0x30);
					break;
				case 0xF8: // LD HL, SP + nn  ** HALFCARRY FLAG NOT SET ***
					localPC++;
					hl = (sp + offset);
					f = 0;
					if ((hl & 0xffff0000) != 0) {
						f = F_CARRY;
						hl &= 0xFFFF;
					}
					break;
				case 0xF9: // LD SP, HL
					sp = hl;
					break;
				case 0xFA: // LD A, (nnnn)
					localPC += 2;
					a = addressRead((b3 << 8) + b2) & 0xff;
					break;
				case 0xFB: // EI
					nextInterruptEnable = instrCount + 1;
					nextTimedInterrupt = nextInterruptEnable;
					break;
				case 0xFE: // CP nn
					localPC++;
					f = ((a & 0x0F) < (b2 & 0x0F)) ? F_HALFCARRY | F_SUBTRACT : F_SUBTRACT;
					if (a == b2) {
						f |= F_ZERO;
					} else if (a < b2) {
						f |= F_CARRY;
					}
					break;
				case 0xFF: // RST 38
					pushPC();
					setPC(0x38);
					break;
					
				default:
					if ((b1 & 0xC0) == 0x80) { // Byte 0x10?????? indicates ALU op, i.e. 0x80 - 0xbf
						int operand = registerRead(b1 & 0x07);
						switch ((b1 & 0x38) >> 3) {
							case 1: // ADC A, r
								if ((f & F_CARRY) != 0) {
									operand++;
								}
								// Note!  No break!
							case 0: // ADD A, r
								
								if ((a & 0x0F) + (operand & 0x0F) >= 0x10) {
									f = F_HALFCARRY;
								} else {
									f = 0;
								}
									
								a += operand;
								
								if (a > 0xff) {
									f |= F_CARRY;
									a &= 0xff;
								}
								
								if (a == 0) {
									f |= F_ZERO;
								}
								break;
							case 3: // SBC A, r
								if ((f & F_CARRY) != 0) {
									operand++;
								}
								// Note! No break!
							case 2: // SUB A, r
								
								f = F_SUBTRACT;
								
								if ((a & 0x0F) < (operand & 0x0F)) {
									f |= F_HALFCARRY;
								}
								
								a -= operand;
								
								if (a < 0) {
									f |= F_CARRY;
									a &= 0xff;
								}
								if (a == 0) {
									f |= F_ZERO;
								}
									
								break;
							case 4: // AND A, r
								a &= operand;
								if (a == 0) {
									f = F_HALFCARRY + F_ZERO;
								} else {
									f = F_HALFCARRY;
								}
								break;
							case 5: // XOR A, r
								a ^= operand;
								f = (a == 0) ? F_ZERO : 0;
								break;
							case 6: // OR A, r
								a |= operand;
								f = (a == 0) ? F_ZERO : 0;
								break;
							case 7: // CP A, r (compare)
								f = F_SUBTRACT;
								if (a == operand) {
									f |= F_ZERO;
								} else if (a < operand) {
									f |= F_CARRY;
								}
								if ((a & 0x0F) < (operand & 0x0F)) {
									f |= F_HALFCARRY;
								}
								break;
						}
					} else {
						MeBoy.log("Unrecognized opcode (" + Integer.toHexString(b1) + ")");
						terminate = true;
						screen.parent.showLog();
						break;
					}
			}
			
			if (interruptsArmed && interruptsEnabled) {
				checkInterrupts();
			}
			
			if (instrCount - nextTimedInterrupt >= 0) {
				initiateInterrupts();
			}
		}
	}
	
	// IOHandler
	
	/** Initialize IO to initial power on state */
	private final void ioHandlerReset() {
		for (int r = 0; r < 0xFF; r++) {
			ioWrite(r, 0);
		}
		ioWrite(0x40, 0x91);
		ioWrite(0x0F, 0x01);
		hdmaRunning = false;
	}
	
	/** Read data from IO Ram */
	private final int ioRead(int num) {
		if (num == 0x41) {
			// LCDSTAT
			int output = registers[0x41];
			
			if (registers[0x44] == registers[0x45]) {
				output |= 4;
			}
			
			if ((registers[0x44] & 0xff) >= 144) {
				output |= 1; // mode 1
			} else {
				int cyclePos = instrCount - nextHBlank + INSTRS_PER_HBLANK; // instrCount % INSTRS_PER_HBLANK;
				
				if (cyclePos > INSTRS_IN_MODE_0_2) {
					// Mode 3
					output |= 3;
				} else if (cyclePos > INSTRS_IN_MODE_0) {
					// Mode 2
					output |= 2;
				} // else mode 0
			}
			
			return output;
		} else if (num == 0x04) {
			// DIV
			return (byte) ((instrCount - divReset) / INSTRS_PER_DIV);
		} else if (num == 0x05) {
			// TIMA
			if (nextTimaOverflow == 0x7fffffff)
				return 0;
			
			return ((instrCount + instrsPerTima * 0x100 - nextTimaOverflow) / instrsPerTima);
		} else if (num == 0x69 && gbcFeatures) {
			graphicsChip.getGBCPalette(false, registers[0x68] & 0x3f);
		} else if (num == 0x6B && gbcFeatures) {
			graphicsChip.getGBCPalette(true, registers[0x6A] & 0x3f);
		}
		
		return registers[num];
	}
	
	/** Write data to IO Ram */
	public void ioWrite(int num, int data) {
		switch (num) {
			case 0x00: // FF00 - Joypad
				int output = 0;
				if ((data & 0x10) == 0) {
					// P14
					output |= buttonState & 0x0f;
				}

				if ((data & 0x20) == 0) {
					// P15
					output |= buttonState >> 4;
				}
				// the high nybble is unused for reading (according to gbcpuman), but Japanese Pokemon
				// seems to require it to be set to f
				registers[0x00] = (byte) ((0xf0) | (~output & 0x0f));
				
				break;
				
			case 0x02: // Serial
				
				registers[0x02] = (byte) data;
				
				if ((registers[0x02] & 0x01) == 1) {
					registers[0x01] = (byte) 0xFF; // when no LAN connection, always receive 0xFF from port.  Simulates empty socket.
					if ((registers[0xff] & INT_SER) != 0) {
						interruptsArmed = true;
						registers[0x0f] |= INT_SER;
					}
					registers[0x02] &= 0x7F;
				}
				
				break;
				
			case 0x04: // DIV
				divReset = instrCount;
				break;
				
			case 0x05: // TIMA
				if (nextTimaOverflow < 0x7fffffff)
					nextTimaOverflow = instrCount + instrsPerTima * (0x100 - (data & 0xff));
				break;
				
			case 0x07: // TAC
				if ((data & 0x04) != 0) {
					int instrsPerSecond = INSTRS_PER_HBLANK * 154 * 60;
					int clockFrequency = (data & 0x03);
					
					switch (clockFrequency) {
						case 0:
							instrsPerTima = (instrsPerSecond / 4096);
							break;
						case 1:
							instrsPerTima = (instrsPerSecond / 262144);
							break;
						case 2:
							instrsPerTima = (instrsPerSecond / 65536);
							break;
						case 3:
							instrsPerTima = (instrsPerSecond / 16384);
							break;
					}
					nextTimaOverflow = instrCount + instrsPerTima * (0x100 - registers[0x06]); // this should probably read from whatever register[0x05] was before tima stopped/reset?
				} else {
					nextTimaOverflow = 0x7fffffff;
				}
				
				break;
				
			case 0x0f:
				registers[0x0f] = (byte) data;
				interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
				break;
				
				// sound registers: 10 11 12 13 14 16 17 18 19 1a 1b 1c 1d 1e 20 21 22 23 24 25 26 30-3f
				
			case 0x1a:
				// sound mode 3, on/off
				registers[num] = (byte) data;
				if ((data & 0x80) == 0)
					registers[0x26] &= 0xfb; // clear bit 2 of sound status register
				break;
				
			case 0x40: // LCDC
				graphicsChip.bgEnabled = true;
				
				// BIT 7
				graphicsChip.lcdEnabled = ((data & 0x80) != 0);
				
				// BIT 6
				graphicsChip.hiWinTileMapAddress = ((data & 0x40) != 0);
				
				// BIT 5
				graphicsChip.winEnabled = ((data & 0x20) != 0);
				
				// BIT 4
				graphicsChip.bgWindowDataSelect = ((data & 0x10) != 0);
				
				// BIT 3
				graphicsChip.hiBgTileMapAddress = ((data & 0x08) != 0);
				
				// BIT 2
				graphicsChip.doubledSprites = ((data & 0x04) != 0);
				
				// BIT 1
				if ((data & 0x02) != 0) {
					graphicsChip.spritesEnabled = true;
					graphicsChip.spritesEnabledThisFrame = true;
				} else {
					graphicsChip.spritesEnabled = false;
				}
				
				// BIT 0
				if ((data & 0x01) == 0) {
					if (gbcFeatures) {
						graphicsChip.spritePriorityEnabled = false;
					} else {
						// this emulates the gbc-in-gb-mode, not the original gb-mode
						graphicsChip.bgEnabled = false;
						graphicsChip.winEnabled = false;
					}
				}
				
				registers[0x40] = (byte) data;
				break;
				
			case 0x41: // LCDC
				registers[0x41] = (byte) (data & 0xf8);
				break;
				
				// fixme, case 0x42 with ((data-reg) % 8) != 0: do something about the messed up drawing.
				
			case 0x46: // DMA
				System.arraycopy(memory[data >> 5], (data << 8) & 0x1f00, oam, 0, 0xa0);
				// This is meant to be run at the same time as the CPU is executing
				// instructions, but I don't think it's crucial.
				break;
			case 0x47: // FF47 - BKG and WIN palette
				graphicsChip.decodePalette(0, data);
				if (registers[0x47] != (byte) data) {
					registers[0x47] = (byte) data;
					graphicsChip.invalidateAll(0);
				}
					break;
				
			case 0x48: // FF48 - OBJ1 palette
				graphicsChip.decodePalette(4, data);
				if (registers[0x48] != (byte) data) {
					registers[0x48] = (byte) data;
					graphicsChip.invalidateAll(1);
				}
					break;
				
			case 0x49: // FF49 - OBJ2 palette
				graphicsChip.decodePalette(8, data);
				if (registers[0x49] != (byte) data) {
					registers[0x49] = (byte) data;
					graphicsChip.invalidateAll(2);
				}
				break;
				
			case 0x4A: // FF4A - Window Position Y
				if ((data & 0xff) >= 144)
					graphicsChip.stopWindowFromLine();
				registers[num] = (byte) data;
				break;
				
			case 0x4B: // FF4B - Window Position X
				if ((data & 0xff) >= 167)
					graphicsChip.stopWindowFromLine();
				registers[num] = (byte) data;
				break;
				
			case 0x4F: // FF4F - VRAM Bank - GBC only
				if (gbcFeatures) {
					graphicsChip.setVRamBank(data & 0x01);
				}
				registers[0x4F] = (byte) data;
				break;
				
			case 0x55: // FF55 - HDMA5 - GBC only
				if ((!hdmaRunning) && ((registers[0x55] & 0x80) == 0) && ((data & 0x80) == 0)) {
					int dmaSrc = ((registers[0x51] & 0xff) << 8) + (registers[0x52] & 0xF0);
					int dmaDst = ((registers[0x53] & 0x1F) << 8) + (registers[0x54] & 0xF0) + 0x8000;
					int dmaLen = ((data & 0x7F) * 16) + 16;

					for (int r = 0; r < dmaLen; r++) {
						addressWrite(dmaDst + r, addressRead(dmaSrc + r));
					}
				} else {
					if ((data & 0x80) == 0x80) {
						hdmaRunning = true;
						registers[0x55] = (byte) (data & 0x7F);
						break;
					} else if ((hdmaRunning) && ((data & 0x80) == 0)) {
						hdmaRunning = false;
					}
				}

				registers[0x55] = (byte) data;
				break;


			case 0x69: // FF69 - Background Palette Data - GBC only
				if (gbcFeatures) {
					graphicsChip.setGBCPalette(false, registers[0x68] & 0x3f, data & 0xff);

					if ((registers[0x68] & 0x80) != 0) {
						registers[0x68]++;
					}
				}
				break;

			case 0x6B: // FF6B - Sprite Palette Data - GBC only
				if (gbcFeatures) {
					graphicsChip.setGBCPalette(true, registers[0x6A] & 0x3f, data & 0xff);

					if ((registers[0x6A] & 0x80) != 0) {
						if ((registers[0x6A] & 0x3F) == 0x3F) {
							registers[0x6A] = (byte) 0x80;
						} else {
							registers[0x6A]++;
						}
					}
				}
				break;

			case 0x70: // FF70 - GBC Work RAM bank
				if (gbcFeatures) {
					if (((data & 0x07) == 0) || ((data & 0x07) == 1)) {
						gbcRamBank = 1;
					} else {
						gbcRamBank = data & 0x07;
					}
					
					if (globalPC >= 0xC000) {
						// verify cruising if executing in RAM
						setPC(globalPC + localPC);
					}
				}
				registers[0x70] = (byte) data;
				break;
				
			case 0xff:
				registers[0xff] = (byte) data;
				interruptsArmed = (registers[0xff] & registers[0x0f]) != 0;
				break;
				
				
			default:
				registers[num] = (byte) data;
				break;
		}
	}
	
	
	
	/** Create a cartridge object, loading ROM and any associated battery RAM from the cartridge
		*  filename given. */
	private final void initCartridge() {
		java.io.InputStream is = getClass().getResourceAsStream(cartName + '0');
		if (is == null) {
			MeBoy.log("ERROR: The cart \"" + cartName + "\" does not exist.");
			throw new RuntimeException();
		}
		try {
			byte[] firstBank = new byte[0x2000];
			
			int total = 0x2000;
			do {
				total -= is.read(firstBank, 0x2000 - total, total); // Read the first bank (bank 0)
			} while (total > 0);
			
			cartType = firstBank[0x0147];
			int numRomBanks = lookUpCartSize(firstBank[0x0148]); // Determine the number of 16kb rom banks
			gbcFeatures = ((firstBank[0x143] & 0x80) == 0x80);
			
			if (gbcFeatures)
				mainRam = new byte[0x8000]; // 32 kB
			else
				mainRam = new byte[0x2000]; // 8 kB
			gbcRamBank = 1;
			
			if (numRomBanks <= MeBoy.lazyLoadingThreshold) {
				rom = new byte[numRomBanks * 2][0x2000]; // Recreate the ROM array with the correct size
				rom[0] = firstBank;
				
				// Read ROM into memory
				for (int i = 1; i < numRomBanks * 2; i++) {
					if ((i & 15) == 0) {
						// open next file
						is.close();
						is = getClass().getResourceAsStream(cartName + (i >> 4));
					}
					
					total = 0x2000;
					do {
						total -= is.read(rom[i], 0x2000 - total, total);
					} while (total > 0);
				}
			} else {
				rom = new byte[numRomBanks * 2][]; // Recreate the ROM array with the correct size
				rom[0] = firstBank;
				rom[1] = new byte[0x2000];
				
				MeBoy.log("Partial loading active.");
				// Read halfbank 1 (second half of bank 0) into memory
				total = 0x2000;
				do {
					total -= is.read(rom[1], 0x2000 - total, total);
				} while (total > 0);
				loadedRomBanks = 1;
				romBankQueue = new int[MeBoy.lazyLoadingThreshold];
				romBankQueueMark = 1;
			}
			is.close();
			
			memory[0] = rom[0];
			memory[1] = rom[1];
			romTouch = new int[rom.length/2];
			mapRom(1);
			
			int numRamBanks = getNumRAMBanks();
			
			MeBoy.log("Loaded '" + cartName + "'. " + numRomBanks + " banks = "
							+ (numRomBanks * 16) + " kB, " + numRamBanks + " RAM banks.");
			MeBoy.log("Type: " + cartType);
			
			if (cartType == 6 && numRamBanks == 0)
				numRamBanks = 1; // fixme, this is not ideal. carttype6 has battery but not ram?
			cartRam = new byte[numRamBanks][0x2000];
			if (numRamBanks > 0)
				memory[5] = cartRam[0];
			
			lastRtcUpdate = (int) System.currentTimeMillis();
		} catch (Exception e) {
			MeBoy.log("ERROR: Loading the cart \"" + cartName + "\" failed.");
			MeBoy.log(e.toString());

			throw new RuntimeException();
		}
	}
	
	private int[] romTouch;
	
	/** Maps a ROM bank into the CPU address space at 0x4000 */
	private final void mapRom(int bankNo) {
		currentRomBank = bankNo;
		
		romTouch[bankNo] = instrCount;
		if (rom[bankNo * 2] == null) {
			try {
				byte[][] newmem = new byte[2][];
				if (loadedRomBanks >= MeBoy.lazyLoadingThreshold) {
					// overwrite previous bank
					int excluded = romBankQueue[romBankQueueMark];
					
					newmem[0] = rom[excluded*2];
					newmem[1] = rom[excluded*2+1];
					rom[excluded*2] = null;
					rom[excluded*2+1] = null;
					
					romBankQueue[romBankQueueMark++] = bankNo;
					if (romBankQueueMark == MeBoy.lazyLoadingThreshold)
						romBankQueueMark = 1; // i.e. don't kick out bank 0
				} else {
					newmem[0] = new byte[0x2000];
					newmem[1] = new byte[0x2000];
					
					romBankQueue[loadedRomBanks] = bankNo;
					loadedRomBanks++;
				}
				
				int file = bankNo >> 3;
				int offset = (bankNo & 7) * 0x4000;
				java.io.InputStream is = getClass().getResourceAsStream(cartName + file);
				
				if (is.skip(offset) != offset)
					throw new RuntimeException("failed skipping to " + bankNo);
				
				for (int i = bankNo*2; i < bankNo*2+2; i++) {
					int total = 0x2000;
					rom[i] = newmem[i & 1];
					do {
						total -= is.read(rom[i], 0x2000 - total, total);
					} while (total > 0);
				}
				// MeBoy.log("loaded bank " + bankNo + " from " + file + " -> " + loadedRomBanks + "/" + tc);
				
				is.close();
			} catch (Exception e) {
				MeBoy.log("ERROR: Lazy loading the cart \"" + cartName + "\" failed.");
				MeBoy.log(e.toString());
				if (MeBoy.debug)
					e.printStackTrace();
	
				throw new RuntimeException();
			}
		} else {
			tc++;
		}
		
		memory[2] = rom[bankNo*2];
		memory[3] = rom[bankNo*2+1];
		if ((globalPC & 0xC000) == 0x4000) {
			setPC(localPC + globalPC);
		}
	}
	int tc;
	
	private final void mapRam(int bankNo) {
		currentRamBank = bankNo;
		if (currentRamBank <= cartRam.length)
			memory[5] = cartRam[bankNo];
	}
	
	/** Writes to an address in CPU address space.  Writes to ROM may cause a mapping change.
		*/
	private final void cartridgeWrite(int addr, int data) {
		int halfbank = addr >> 13;
		int subaddr = addr & 0x1fff;
		
		switch (cartType) {
			case 0:
				// ROM Only
				break;

			case 1:
			case 2:
			case 3:
				// MBC1
				if (halfbank == 5) {
					if (cartRamEnabled) {
						memory[halfbank][subaddr] = (byte) data;
					}
				} else if (halfbank == 1) {
					int bankNo = data & 0x1F;
					if (bankNo == 0)
						bankNo = 1;
					mapRom((currentRomBank & 0x60) | bankNo);
				} else if (halfbank == 3) {
					mbc1LargeRamMode = ((data & 1) == 1);
				} else if (halfbank == 0) {
					cartRamEnabled = ((data & 0x0F) == 0x0A);
				} else if (halfbank == 2) {
					if (mbc1LargeRamMode) {
						mapRam(data & 0x03);
					} else {
						mapRom((currentRomBank & 0x1F) | ((data & 0x03) << 5));
					}
				}
				break;
				
			case 5:
			case 6:
				// MBC2
				if ((halfbank == 1)) {
					if ((addr & 0x0100) != 0) {
						int bankNo = data & 0x0F;
						if (bankNo == 0)
							bankNo = 1;
						mapRom(bankNo);
					} else {
						cartRamEnabled = ((data & 0x0F) == 0x0A);
					}
				}
				if (halfbank == 5) {
					if (cartRamEnabled)
						memory[halfbank][subaddr] = (byte) data;
				}
				
				break;
				
			case 0x0F:
			case 0x10:
			case 0x11:
			case 0x12:
			case 0x13:
				// MBC3
				if (halfbank == 1) {
					// Select ROM bank
					int bankNo = data & 0x7F;
					if (bankNo == 0)
						bankNo = 1;
					mapRom(bankNo);
				} else if (halfbank == 2) {
					// Select RAM bank
					if (cartRam.length > 0)
						mapRam(data);
				} else if (halfbank == 5) {
					// memory write
					if (currentRamBank >= 8) {
						// rtc register
						rtcSync();
						rtcReg[currentRamBank-8] = (byte) data;
					} else {
						// normal memory
						memory[halfbank][subaddr] = (byte) data;
					}
				}
				break;
				
				
			case 0x19:
			case 0x1A:
			case 0x1B:
			case 0x1C:
			case 0x1D:
			case 0x1E:
				// MBC5
				if ((addr >= 0x2000) && (addr <= 0x2FFF)) {
					int bankNo = (currentRomBank & 0xFF00) | data;
					mapRom(bankNo);
				} else if ((addr >= 0x3000) && (addr <= 0x3FFF)) {
					int bankNo = (currentRomBank & 0x00FF) | ((data & 0x01) << 8);
					mapRom(bankNo);
				} else if (halfbank == 2) {
					if (cartRam.length > 0)
						mapRam(data & 0x07);
				} else if (halfbank == 5) {
					if (memory[5] != null)
						memory[halfbank][subaddr] = (byte) data;
					else {
						// shouldn't be here, the cart type specifies no cart ram,
						// but the game tries to access it
					}
				}
				break;
		}
	}
	
	// Update the RTC registers before reading/writing (if active) with small delta
	protected final void rtcSync() {
		if ((rtcReg[4] & 0x40) == 0) {
			// active
			int now = (int) System.currentTimeMillis();
			while (now - lastRtcUpdate > 1000) {
				lastRtcUpdate += 1000;
				
				if (++rtcReg[0] == 60) {
					rtcReg[0] = 0;
					
					if (++rtcReg[1] == 60) {
						rtcReg[1] = 0;
						
						if (++rtcReg[2] == 24) {
							rtcReg[2] = 0;
							
							if (++rtcReg[3] == 0) {
								rtcReg[4] = (byte) ((rtcReg[4] | (rtcReg[4] << 7)) ^ 1);
							}
						}
					}
				}
			}
		}
	}
	
	// Update the RTC registers after resuming (large delta)
	protected final void rtcSkip(int s) {
		// seconds
		int sum = s + rtcReg[0];
		rtcReg[0] = (byte) (sum % 60);
		sum = sum / 60;
		if (sum == 0)
			return;
		
		// minutes
		sum = sum + rtcReg[1];
		rtcReg[1] = (byte) (sum % 60);
		sum = sum / 60;
		if (sum == 0)
			return;
		
		// hours
		sum = sum + rtcReg[2];
		rtcReg[2] = (byte) (sum % 24);
		sum = sum / 24;
		if (sum == 0)
			return;
		
		// days, bit 0-7
		sum = sum + (rtcReg[3] & 0xff) + ((rtcReg[4] & 1) << 8);
		rtcReg[3] = (byte) (sum);
		
		// overflow & day bit 8
		if (sum > 511)
			rtcReg[4] |= 0x80;
		rtcReg[4] = (byte) ((rtcReg[4] & 0xfe) + ((sum >> 8) & 1));
	}

	private final int getNumRAMBanks() {
		switch (rom[0][0x149]) {
			case 1:
			case 2:
				return 1;
			
			case 3:
				return 4;
			
			case 4:
				return 16;
		}
		return 0;
	}
	
	/** Returns the number of 16Kb banks in a cartridge from the header size byte. */
	private final int lookUpCartSize(int sizeByte) {
		/** Translation between ROM size byte contained in the ROM header, and the number
		*  of 16kB ROM banks the cartridge will contain
		*/
		if (sizeByte < 8)
			return 2 << sizeByte;
		else if (sizeByte == 0x52)
			return 72;
		else if (sizeByte == 0x53)
			return 80;
		else if (sizeByte == 0x54)
			return 96;
		return -1;
	}
	
	public final boolean hasBattery() {
		return (cartType == 3) || (cartType == 9) || (cartType == 0x1B) || (cartType == 0x1E) ||
				(cartType == 6) || (cartType == 0x10) || (cartType == 0x13);
	}
	
	public void buttonDown(int buttonIndex) {
		buttonState |= 1 << buttonIndex;
		p10Requested = true;
		interruptsArmed = true;
	}

	public void buttonUp(int buttonIndex) {
		buttonState &= 0xff - (1 << buttonIndex);
		p10Requested = true;
		interruptsArmed = true;
	}
}
