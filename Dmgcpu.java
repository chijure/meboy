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
	
	public final int INSTRS_PER_VBLANK = 9000; /* 10000  */
	
	/** Used to set the speed of the emulator.  This controls how
		*  many instructions are executed for each horizontal line scanned
		*  on the screen.  Multiply by 154 to find out how many instructions
		*  per frame.
		*/
	public final int INSTRS_PER_HBLANK = 60; /* 60    */
	private final int INSTRS_IN_MODE_0 = 30;
	private final int INSTRS_IN_MODE_0_2 = 40;
	
	private final int INSTRS_PER_DIV = 33;
	
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
	private int decoderMaxCruise; // index is index of next instruction, and whether it can be decodec by a cruising decoder.
	
	/** The number of instructions that have been executed since the
		*  last reset
		*/
	private int instrCount;
	
	private int nextHBlank;
	private int nextTimaOverflow;
	private int nextInterruptEnable;
	private int nextTimedInterrupt;
	
	private int divReset; // instrCount at the time register[4] was reset
	
	public boolean interruptsEnabled = false;
	private boolean interruptsArmed = false;
	
	private int instrsPerTima = 131;
	
	
	
	// 0,1 = rom bank 0
	// 2,3 = mapped rom bank
	// 4 = vram (read only)
	// 5 = mapped cartram
	// 6 = main ram
	// 7 = main ram again (+ oam+reg)
	public byte[][] memory = new byte[8][];
	
	// 8Kb main system RAM appears at 0xC000 in address space
	private byte[] mainRam = new byte[0x2000];
	
	// 256 bytes at top of RAM are used mainly for registers
	public byte[] oam = new byte[0x100];
	
	public GraphicsChip graphicsChip;
	public GBCanvas screen;
	public boolean terminate;
	
	
	// IOHandler:
	/** Data contained in the handled memory area */
	public byte[] registers = new byte[0x100];
	
	/** Current state of the button, true = pressed. */
	public boolean[] padDown = new boolean[8];
	
	
	// Cartridge:
	
	private int cartType;
	
	
	/** Contains the complete ROM image of the cartridge */
	// split into halfbanks of 0x2000 bytes
	private byte[][] rom;
	
	/** Contains the RAM on the cartridge */
	private byte[][] cartRam;
	
	
	/** The bank number which is currently mapped at 0x4000 in CPU address space */
	private int currentRomBank = 1;
	
	/** The RAM bank number which is currently mapped at 0xA000 in CPU address space */
	private int currentRamBank;
	
	private boolean mbc1LargeRamMode;
	private boolean cartRamEnabled;
		
	
	
	public Dmgcpu(String cart, GBCanvas gbc) {
		initCartridge(cart);
		screen = gbc;
		
		graphicsChip = new GraphicsChip(this);
		
		memory[6] = mainRam;
		memory[7] = mainRam;
		
		interruptsEnabled = false;
		
		f = 0xB0;
		a = 0x01;
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
		
		ioHandlerReset();
	}
	
	/** Perform a CPU address space read.  This maps all the relevant objects into the correct parts of
		*  the memory
		*/
	public final int addressRead(int addr) {
		if (addr < 0xfe00) {
			return memory[addr >> 13][addr & 0x1fff];
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
		int f = (addr >> 13) & 0x7;
		switch (f) {
			case 0x0:
			case 0x1:
			case 0x2:
			case 0x3:
				cartridgeWrite(addr, data);
				break;
				
			case 0x4:
				graphicsChip.addressWrite(addr - 0x8000, (byte) data);
				break;
				
			case 0x5:
				cartridgeWrite(addr, data);
				break;
				
			case 0x6:
				mainRam[addr - 0xC000] = (byte) data;
				break;
				
			case 0x7:
				if (addr < 0xFE00) {
					mainRam[addr - 0xE000] = (byte) data;
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
	
	/** If an interrupt is enabled an the interrupt register shows that it has occured, jump to
		*  the relevant interrupt vector address
		*/
	private final void checkInterrupts() {
		int intFlags = registers[0x0F];
		int ieReg = registers[0xFF];
		if ((intFlags & ieReg) != 0) {
			pushPC();
			interruptsEnabled = false;
			
			if ((intFlags & ieReg & INT_VBLANK) != 0) {
				setPC(0x40); // Jump to Vblank interrupt address
				intFlags -= INT_VBLANK;
			} else if ((intFlags & ieReg & INT_LCDC) != 0) {
				setPC(0x48);
				intFlags -= INT_LCDC;
			} else if ((intFlags & ieReg & INT_TIMA) != 0) {
				setPC(0x50);
				intFlags -= INT_TIMA;
			} else if ((intFlags & ieReg & INT_SER) != 0) {
				setPC(0x58);
				intFlags -= INT_SER;
			} else if ((intFlags & ieReg & INT_P10) != 0) {
				// Joypad interrupt
				setPC(0x60);
				intFlags -= INT_P10;
			}
			
			
			registers[0x0F] = (byte) intFlags;
			interruptsArmed = ((registers[0x0f] & registers[0xff]) != 0);
		}
	}
	
	/** Initiate an interrupt of the specified type */
	private final void triggerInterrupt(int intr) {
		registers[0x0F] |= intr;
		interruptsArmed = ((registers[0x0f] & registers[0xff]) != 0);
	}
	
	public final void triggerInterruptIfEnabled(int intr) {
		if ((registers[0xFF] & intr) != 0) {
			registers[0x0F] |= intr;
			interruptsArmed = ((registers[0x0f] & registers[0xff]) != 0);
		}
	}
	
	/** Check for interrupts that need to be initiated */
	private final void initiateInterrupts() {
		if (instrCount >= nextTimaOverflow) {
			nextTimaOverflow += instrsPerTima * (0x100 - registers[0x06]);
			if ((registers[0xFF] & INT_TIMA) != 0) {
				registers[0x0F] |= INT_TIMA;
				interruptsArmed = true;
			}
			
			nextTimedInterrupt = nextHBlank < nextTimaOverflow ? nextHBlank : nextTimaOverflow;
			nextTimedInterrupt = nextInterruptEnable < nextTimedInterrupt ? nextInterruptEnable : nextTimedInterrupt;
		} else if (instrCount >= nextHBlank) {
			nextHBlank += INSTRS_PER_HBLANK;
			
			// Fixme, what is this about cline = 152 <=> 0?
			
			// LCY Coincidence
			// The +1 is due to the LCY register being just about to be incremented
			int cline = (registers[0x44] & 0xff) + 1;
			if (cline == 152)
				cline = 0;
			
			// if (instrCount < 1000000)
			// 	System.out.println("cline:" + cline);
			
			if ((registers[0xff] & INT_LCDC) != 0 && cline < 144 && ((registers[0x40] & 0x80) != 0)) {
				if (((registers[0x41] & 0x40) != 0) && ((registers[0x45] & 0xff) == cline)) {
					triggerInterrupt(INT_LCDC);
				} else if (((registers[0x41] & 0x08) != 0)) {
					triggerInterrupt(INT_LCDC);
				}
			}
			
			if (cline == 144) {
				// vblank
				for (int r = 144; r < 170; r++) {
					graphicsChip.notifyScanline(r);
				}
				if (((registers[0x40] & 0x80) != 0) && ((registers[0xFF] & INT_VBLANK) != 0)) {
					triggerInterrupt(INT_VBLANK);
					
					if (((registers[0x41] & 0x10) != 0) && ((registers[0xFF] & INT_LCDC) != 0)) {
						// VBLANK LCDC
						triggerInterrupt(INT_LCDC);
					}
				}
			}
			
			graphicsChip.notifyScanline(registers[0x44]);
			registers[0x44]++;
			
			if ((registers[0x44] & 0xff) >= 153) {
				// vblank
				
				registers[0x44] = 0;
				graphicsChip.drawOrSkip();
			}
			
			nextTimedInterrupt = nextHBlank < nextTimaOverflow ? nextHBlank : nextTimaOverflow;
			nextTimedInterrupt = nextInterruptEnable < nextTimedInterrupt ? nextInterruptEnable : nextTimedInterrupt;
		} else if (instrCount >= nextInterruptEnable) {
			interruptsEnabled = true;
			
			nextInterruptEnable = 0x7fffffff;
			nextTimedInterrupt = nextHBlank < nextTimaOverflow ? nextHBlank : nextTimaOverflow;
		}
	}
	
	public final void setPC(int pc) {
		if (pc < 0xfe00) {
			decoderMemory = memory[pc >> 13];
			localPC = pc & 0x1fff;
			globalPC = pc & 0xe000;
			decoderMaxCruise = (pc < 0xe000) ? 0x1ffd : 0x1dfd;
		} else if (pc < 0xff00) {
			// note: pc in sprite ram would be weird...
			decoderMemory = oam;
			localPC = pc & 0xff;
			globalPC = 0xfe00;
			decoderMaxCruise = 0xfd;
		} else {
			// note: pc in registers happens
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
		
		int f_c = F_CARRY, f_hc = F_HALFCARRY, f_z = F_ZERO, f_sub = F_SUBTRACT;
		int f_szhc = f_sub + f_z + f_hc;
		
		int[] incflags = new int[256];
		incflags[0] = f_z + f_hc;
		for (int i = 0x10; i < 0xff; i += 0x10)
			incflags[i] = f_hc;
		
		int[] decflags = new int[256];
		decflags[0] = f_z + f_sub;
		for (int i = 1; i < 0xff; i++)
			decflags[i] = f_sub + (((i & 0x0f) == 0x0f) ? f_hc : 0);
		
		boolean timing = graphicsChip.frameSkip == 0x7fffffff;
		int startTime = (int) System.currentTimeMillis();
		
		
		while (!terminate) {
			instrCount++;
			
			/* debug code for timing 18000000 instructions
			if (timing && instrCount >= 18000000) {
				MeBoy.log("18M time: " + ((int) System.currentTimeMillis() - startTime + 50)/100);
				terminate = true;
				screen.showFps = true;
				screen.repaint();
				printStat();
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
					f = (f & f_c) | incflags[b];
					break;
				case 0x05: // DEC B
					b = (b - 1) & 0xff;
					
					if (b2 == 0x20) {
						instrCount++;
						localPC += 2;
						if (b != 0) {
							localPC += (byte) b3;
							if (localPC < 0 || localPC > decoderMaxCruise) {
								// switch bank
								setPC(localPC + globalPC);
							}
						}
					} else {
						f = (f & f_c) | decflags[b];
					}
					
					break;
				case 0x06: // LD B, nn
					localPC++;
					b = b2;
					break;
				case 0x07: // RLC A
					if (a >= 0x80) {
						f = f_c;
						a = ((a << 1) + 1) & 0xff;
					} else if (a == 0) {
						f = f_z;
					} else {
						a <<= 1;
						f = 0;
					}
					break;
				case 0x08: // LD (nnnn), SP   /* **** May be wrong! **** */
					localPC += 2;
					
					addressWrite((b3 << 8) + b2, sp & 0xFF);
					addressWrite((b3 << 8) + b2 + 1, sp >> 8);
					break;
				case 0x09: // ADD HL, BC
					hl = (hl + ((b << 8) + c));
					if ((hl & 0xffff0000) != 0) {
						f = (f & f_szhc) | f_c;
						hl &= 0xFFFF;
					} else {
						f &= f_szhc;
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
					f = (f & f_c) | incflags[c];
					break;
				case 0x0D: // DEC C
					c = (c - 1) & 0xff;
					f = (f & f_c) | decflags[c];
					break;
				case 0x0E: // LD C, nn
					localPC++;
					c = b2;
					break;
				case 0x0F: // RRC A
					if ((a & 0x01) == 0x01) {
						f = f_c;
					} else {
						f = 0;
					}
					a >>= 1;
					if ((f & f_c) != 0) {
						a |= 0x80;
					}
					if (a == 0) {
						f |= f_z;
					}
					break;
				case 0x10: // STOP
					localPC++;
					
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
					f = (f & f_c) | incflags[d];
					break;
				case 0x15: // DEC D
					d = (d - 1) & 0xff;
					f = (f & f_c) | decflags[d];
					break;
				case 0x16: // LD D, nn
					localPC++;
					d = b2;
					break;
				case 0x17: // RL A
					if ((a & 0x80) == 0x80) {
						newf = f_c;
					} else {
						newf = 0;
					}
					a <<= 1;

					if ((f & f_c) != 0) {
						a |= 1;
					}
					
					a &= 0xFF;
					if (a == 0) {
						newf |= f_z;
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
						f = ((f & f_szhc) | f_c);
						hl &= 0xFFFF;
					} else {
						f &= f_szhc;
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
					f = (f & f_c) | incflags[e];
					break;
				case 0x1D: // DEC E
					e = (e - 1) & 0xff;
					f = (f & f_c) | decflags[e];
					break;
				case 0x1E: // LD E, nn
					localPC++;
					e = b2;
					break;
				case 0x1F: // RR A
					if ((a & 0x01) == 0x01) {
						newf = f_c;
					} else {
						newf = 0;
					}
					a >>= 1;

					if ((f & f_c) != 0) {
						a |= 0x80;
					}

					if (a == 0) {
						newf |= f_z;
					}
					f = newf;
					break;
				case 0x20: // JR NZ, nn
					if (f < f_z) {
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
					f = (f & f_c) | incflags[b2];
					hl = (hl & 0xff) + (b2 << 8);
					break;
				case 0x25: // DEC H
					b2 = ((hl >> 8) - 1) & 0xff;
					f = (f & f_c) | decflags[b2];
					hl = (hl & 0xff) + (b2 << 8);
					break;
				case 0x26: // LD H, nn
					localPC++;
					hl = (hl & 0xFF) | (b2 << 8);
					break;
				case 0x27: // DAA         ** This could be wrong! **
					int upperNibble = (a & 0xF0) >> 4;
					int lowerNibble = a & 0x0F;
					
					newf = (f & f_sub);
					
					if ((f & f_sub) == 0) {
						if ((f & f_c) == 0) {
							if ((upperNibble <= 8) && (lowerNibble >= 0xA)
								&& ((f & f_hc) == 0)) {
								a += 0x06;
							}
							
							if ((upperNibble <= 9) && (lowerNibble <= 0x3)
								&& ((f & f_hc) != 0)) {
								a += 0x06;
							}
							
							if ((upperNibble >= 0xA) && (lowerNibble <= 0x9)
								&& ((f & f_hc) == 0)) {
								a += 0x60;
								newf |= f_c;
							}
							
							if ((upperNibble >= 0x9) && (lowerNibble >= 0xA)
								&& ((f & f_hc) == 0)) {
								a += 0x66;
								newf |= f_c;
							}
							
							if ((upperNibble >= 0xA) && (lowerNibble <= 0x3)
								&& ((f & f_hc) != 0)) {
								a += 0x66;
								newf |= f_c;
							}
							
						} else { // If carry set
							
							if ((upperNibble <= 0x2) && (lowerNibble <= 0x9)
								&& ((f & f_hc) == 0)) {
								a += 0x60;
								newf |= f_c;
							}
							
							if ((upperNibble <= 0x2) && (lowerNibble >= 0xA)
								&& ((f & f_hc) == 0)) {
								a += 0x66;
								newf |= f_c;
							}
							
							if ((upperNibble <= 0x3) && (lowerNibble <= 0x3)
								&& ((f & f_hc) != 0)) {
								a += 0x66;
								newf |= f_c;
							}
							
						}
						
					} else { // Subtract is set
						
						if ((f & f_c) == 0) {
							
							if ((upperNibble <= 0x8) && (lowerNibble >= 0x6)
								&& ((f & f_hc) != 0)) {
								a += 0xFA;
							}
							
						} else { // Carry is set
							
							if ((upperNibble >= 0x7) && (lowerNibble <= 0x9)
								&& ((f & f_hc) == 0)) {
								a += 0xA0;
								newf |= f_c;
							}
							
							if ((upperNibble >= 0x6) && (lowerNibble >= 0x6)
								&& ((f & f_hc) != 0)) {
								a += 0x9A;
								newf |= f_c;
							}
							
						}
						
					}
						
						a &= 0x00FF;
					if (a == 0)
						newf |= f_z;
						
						f = newf;
					
					break;
				case 0x28: // JR Z, nn
					if (f >= f_z) {
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
						f = (f & f_z) | f_c;
						hl &= 0xFFFF;
					} else {
						f &= f_z;
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
					f = (f & f_c) | incflags[b2];
					hl = (hl & 0xff00) + b2;
					break;
				case 0x2D: // DEC L
					b2 = (hl - 1) & 0xff;
					f = (f & f_c) | decflags[b2];
					hl = (hl & 0xff00) + b2;
					break;
				case 0x2E: // LD L, nn
					localPC++;
					hl = (hl & 0xFF00) | b2;
					break;
				case 0x2F: // CPL A
					a = ((~a) & 0x00FF);
					f = ((f & (f_c | f_z)) | f_sub | f_hc);
					break;
				case 0x30: // JR NC, nn
					if ((f & f_c) == 0) {
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
					f = (f & f_c) | incflags[b2];
					addressWrite(hl, b2);
					break;
				case 0x35: // DEC (HL)
					b2 = (addressRead(hl) - 1) & 0xff;
					f = (f & f_c) | decflags[b2];
					addressWrite(hl, b2);
					break;
				case 0x36: // LD (HL), nn
					localPC++;
					addressWrite(hl, b2);
					break;
				case 0x37: // SCF
					f = (f & f_z) | f_c;
					break;
				case 0x38: // JR C, nn
					if ((f & f_c) != 0) {
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
						f = (f & f_z) | f_c;
						hl &= 0xFFFF;
					} else {
						f &= f_z;
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
					f = (f & f_c) | incflags[a];
					break;
				case 0x3D: // DEC A
					a = (a - 1) & 0xff;
					f = (f & f_c) | decflags[a];
					break;
				case 0x3E: // LD A, nn
					localPC++;
					a = b2;
					break;
				case 0x3F: // CCF
					f = (f & (f_c | f_z)) ^ f_c;
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
					
					while (registers[0x0F] == 0) {
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
						f = f_hc + f_z;
					} else {
						f = f_hc;
					}
					break;
					
				case 0xAF: // XOR A, A (== LD A, 0)
					a = 0;
					f = f_z; // Set zero flag
					break;
				case 0xC0: // RET NZ
					if ((f & f_z) == 0) {
						popPC();
					}
					break;
				case 0xC1: // POP BC
					c = addressRead(sp++) & 0xff;
					b = addressRead(sp++) & 0xff;
					break;
				case 0xC2: // JP NZ, nnnn
					if (f < f_z) {
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
					if (f < f_z) {
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
						f = f_hc;
					} else {
						f = 0;
					}
						
						a += b2;
					
					if (a > 0xff) {
						f |= f_c;
						a &= 0xff;
					}
						
						if (a == 0) {
							f |= f_z;
						}
						break;
				case 0xCF: // RST 08
					pushPC();
					setPC(0x08);
					break;
				case 0xC8: // RET Z
					if (f >= f_z) {
						popPC();
					}
					break;
				case 0xC9: // RET
					popPC();
					break;
				case 0xCA: // JP Z, nnnn
					if (f >= f_z) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xCB: // Shift/bit test
					localPC++;
					int regNum = b2 & 0x07;
					int data = registerRead(regNum);
					
					if ((b2 & 0xC0) == 0) {
						switch ((b2 & 0xF8)) {
							case 0x00: // RLC A
								f = 0;
								if (data >= 0x80) {
									f = f_c;
								}
								data = (data << 1) & 0xff;
								if ((f & f_c) != 0) {
									data |= 1;
								}
								
								break;
							case 0x08: // RRC A
								f = 0;
								if ((data & 0x01) != 0) {
									f = f_c;
								}
								data >>= 1;
								if ((f & f_c) != 0) {
									data |= 0x80;
								}
								break;
							case 0x10: // RL r
								if (data >= 0x80) {
									newf = f_c;
								} else {
									newf = 0;
								}
								data = (data << 1) & 0xff;
								
								if ((f & f_c) != 0) {
									data |= 1;
								}
								f = newf;
								break;
							case 0x18: // RR r
								if ((data & 0x01) != 0) {
									newf = f_c;
								} else {
									newf = 0;
								}
								data >>= 1;
								
								if ((f & f_c) != 0) {
									data |= 0x80;
								}
									
								f = newf;
								break;
							case 0x20: // SLA r
								f = 0;
								if ((data & 0x80) != 0) {
									f = f_c;
								}
								
								data = (data << 1) & 0xff;
								break;
							case 0x28: // SRA r
								f = 0;
								if ((data & 0x01) != 0) {
									f = f_c;
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
									f = f_c;
								}
								
								data >>= 1;
								break;
						}
						
						if (data == 0) {
							f |= f_z;
						}
						registerWrite(regNum, data);
					} else {
						int bitMask = 1 << ((b2 & 0x38) >> 3);
						
						if ((b2 & 0xC0) == 0x40) { // BIT n, r
							f = ((f & f_c) | f_hc);
							if ((data & bitMask) == 0) {
								f |= f_z;
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
					if (f >= f_z) {
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
					
					if ((f & f_c) != 0) {
						b2++;
					}
						if ((a & 0x0F) + (b2 & 0x0F) >= 0x10) {
							f = f_hc;
						} else {
							f = 0;
						}
						
						a += b2;
					
					if (a > 0xff) {
						f |= f_c;
						a &= 0xff;
					}
						
						if (a == 0) {
							f |= f_z;
						}
						break;
				case 0xC7: // RST 00
					pushPC();
					setPC(0x00);
					break;
				case 0xD0: // RET NC
					if ((f & f_c) == 0) {
						popPC();
					}
					break;
				case 0xD1: // POP DE
					e = addressRead(sp++) & 0xff;
					d = addressRead(sp++) & 0xff;
					break;
				case 0xD2: // JP NC, nnnn
					if ((f & f_c) == 0) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xD4: // CALL NC, nnnn
					localPC += 2;
					if ((f & f_c) == 0) {
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
					
					f = f_sub;
					
					if ((a & 0x0F) < (b2 & 0x0F)) {
						f |= f_hc;
					}
						
						a -= b2;
					
					if (a < 0) {
						f |= f_c;
						a &= 0xff;
					}
						if (a == 0) {
							f |= f_z;
						}
						break;
				case 0xD7: // RST 10
					pushPC();
					setPC(0x10);
					break;
				case 0xD8: // RET C
					if ((f & f_c) != 0) {
						popPC();
					}
					break;
				case 0xD9: // RETI
					interruptsEnabled = true;
					popPC();
					break;
				case 0xDA: // JP C, nnnn
					if ((f & f_c) != 0) {
						setPC((b3 << 8) + b2);
					} else {
						localPC += 2;
					}
					break;
				case 0xDC: // CALL C, nnnn
					localPC += 2;
					if ((f & f_c) != 0) {
						pushPC();
						setPC((b3 << 8) + b2);
					}
						break;
				case 0xDE: // SBC A, nn
					localPC++;
					if ((f & f_c) != 0) {
						b2++;
					}
						
						f = f_sub;
					
					if ((a & 0x0F) < (b2 & 0x0F)) {
						f |= f_hc;
					}
						
						a -= b2;
					
					if (a < 0) {
						f |= f_c;
						a &= 0xff;
					}
						if (a == 0) {
							f |= f_z;
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
					f = 0; if (a == 0) f = f_z;
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
						f = f_c;
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
						f = f_z;
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
					f = addressRead(sp++) & 0xff;
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
						f = f_z;
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
						f = f_c;
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
					f = ((a & 0x0F) < (b2 & 0x0F)) ? f_hc | f_sub : f_sub;
					if (a == b2) {
						f |= f_z;
					} else if (a < b2) {
						f |= f_c;
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
								if ((f & f_c) != 0) {
									operand++;
								}
								// Note!  No break!
							case 0: // ADD A, r
								
								if ((a & 0x0F) + (operand & 0x0F) >= 0x10) {
									f = f_hc;
								} else {
									f = 0;
								}
									
								a += operand;
								
								if (a > 0xff) {
									f |= f_c;
									a &= 0xff;
								}
								
								if (a == 0) {
									f |= f_z;
								}
								break;
							case 3: // SBC A, r
								if ((f & f_c) != 0) {
									operand++;
								}
								// Note! No break!
							case 2: // SUB A, r
								
								f = f_sub;
								
								if ((a & 0x0F) < (operand & 0x0F)) {
									f |= f_hc;
								}
								
								a -= operand;
								
								if (a < 0) {
									f |= f_c;
									a &= 0xff;
								}
								if (a == 0) {
									f |= f_z;
								}
									
								break;
							case 4: // AND A, r
								a &= operand;
								if (a == 0) {
									f = f_z;
								} else {
									f = 0;
								}
								break;
							case 5: // XOR A, r
								a ^= operand;
								f = (a == 0) ? f_z : 0;
								break;
							case 6: // OR A, r
								a |= operand;
								f = (a == 0) ? f_z : 0;
								break;
							case 7: // CP A, r (compare)
								f = f_sub;
								if (a == operand) {
									f |= f_z;
								} else if (a < operand) {
									f |= f_c;
								}
								if ((a & 0x0F) < (operand & 0x0F)) {
									f |= f_hc;
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
			
			if (instrCount >= nextTimedInterrupt) {
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
	}
	
	/** Read data from IO Ram */
	private final int ioRead(int num) {
		if (num == 0x41) {
			// LCDSTAT
			int output = registers[0x41];
			
			if (registers[0x44] == registers[0x45]) {
				output |= 4;
			}
			
			if ((registers[0x44] & 0xff) > 144) {
				output |= 1;
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
		}
		
		return registers[num];
	}
	
	/** Write data to IO Ram */
	public void ioWrite(int num, int data) {
		switch (num) {
			case 0x00: // FF00 - Joypad
				short output = 0;
				if ((data & 0x10) == 0) {
					// P14
					for (int i = 0; i < 4; i++) {
						if (padDown[i]) {
							output |= (1<<i);
						}
					}
				}
					
					if ((data & 0x20) == 0) {
						// P15
						for (int i = 0; i < 4; i++) {
							if (padDown[i+4])
								output |= (1<<i);
						}
					}
					registers[0x00] = (byte) ~output;
				break;
				
			case 0x02: // Serial
				
				registers[0x02] = (byte) data;
				
				if ((registers[0x02] & 0x01) == 1) {
					registers[0x01] = (byte) 0xFF; // when no LAN connection, always receive 0xFF from port.  Simulates empty socket.
					triggerInterruptIfEnabled(INT_SER);
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
					int instrsPerSecond = INSTRS_PER_VBLANK * 60;
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
				interruptsArmed = ((registers[0x0f] & registers[0xff]) != 0);
				
				break;
				
				// sound registers: 10 11 12 13 14 16 17 18 19 1a 1b 1c 1d 1e 20 21 22 23 24 25 26 30-3f
				
			case 0x40: // LCDC
				graphicsChip.bgEnabled = true;
				
				// BIT 5
				graphicsChip.winEnabled = ((data & 0x20) != 0);
				
				// BIT 4
				graphicsChip.bgWindowDataSelect = ((data & 0x10) != 0);
				
				// BIT 3
				graphicsChip.hiBgTileMapAddress = ((data & 0x08) != 0);
				
				// BIT 2
				graphicsChip.doubledSprites = ((data & 0x04) != 0);
				
				// BIT 1
				graphicsChip.spritesEnabled = ((data & 0x02) != 0);
				
				if ((data & 0x01) == 0) { // BIT 0
					graphicsChip.bgEnabled = false;
					graphicsChip.winEnabled = false;
				}
					
					registers[0x40] = (byte) data;
				break;
				
			case 0x41: // LCDC
				registers[0x41] = (byte) (data & 0xf8);
				break;
				
			case 0x46: // DMA
				System.arraycopy(memory[data >> 5], (data << 8) & 0x1f00, oam, 0, 0xa0);
				// This is meant to be run at the same time as the CPU is executing
				// instructions, but I don't think it's crucial.
				break;
			case 0x47: // FF47 - BKG and WIN palette
				graphicsChip.decodePalette(0, data);
				if (registers[0x47] != (byte) data) {
					registers[0x47] = (byte) data;
					graphicsChip.invalidateAll();
				}
					break;
				
			case 0x48: // FF48 - OBJ1 palette
				graphicsChip.decodePalette(4, data);
				if (registers[0x48] != (byte) data) {
					registers[0x48] = (byte) data;
					graphicsChip.invalidateAll();
				}
					break;
				
			case 0x49: // FF49 - OBJ2 palette
				graphicsChip.decodePalette(8, data);
				if (registers[0x49] != (byte) data) {
					registers[0x49] = (byte) data;
					graphicsChip.invalidateAll();
				}
					break;
				
			case 0xff:
				registers[0xff] = (byte) data;
				interruptsArmed = ((registers[0x0f] & registers[0xff]) != 0);
				break;
				
				
			default:
				registers[num] = (byte) data;
				break;
		}
	}
	
	
	
	/** Create a cartridge object, loading ROM and any associated battery RAM from the cartridge
		*  filename given. */
	private final void initCartridge(String romFileName) {
		java.io.InputStream is = getClass().getResourceAsStream(romFileName);
		if (is == null) {
			MeBoy.log("ERROR: The cart \"" + romFileName.substring(1) + "\" does not exist.");
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
			
			rom = new byte[numRomBanks * 2][0x2000]; // Recreate the ROM array with the correct size
			rom[0] = firstBank;
			
			// Read ROM into memory
			for (int i = 1; i < numRomBanks * 2; i++) {
				total = 0x2000;
				do {
					total -= is.read(rom[i], 0x2000 - total, total); // Read the entire ROM
				} while (total > 0);
			}
			is.close();
			
			memory[0] = rom[0];
			memory[1] = rom[1];
			mapRom(1);
			
			int numRamBanks = getNumRAMBanks();
			
			MeBoy.log("Loaded '" + romFileName + "'.  " + numRomBanks + " banks, "
							+ (numRomBanks * 16) + "Kb.  " + numRamBanks + " RAM banks.");
			MeBoy.log("Type: " + cartType);
			
			if (numRamBanks == 0)
				numRamBanks = 1;
			cartRam = new byte[numRamBanks][0x2000];
			memory[5] = cartRam[0];
		} catch (Exception e) {
			MeBoy.log("ERROR: Loading the cart \"" + romFileName.substring(1) + "\" failed.");
			MeBoy.log(e.toString());

			throw new RuntimeException();
		}
	}
	
	/** Maps a ROM bank into the CPU address space at 0x4000 */
	private final void mapRom(int bankNo) {
		currentRomBank = bankNo;
		memory[2] = rom[bankNo*2];
		memory[3] = rom[bankNo*2+1];
		if ((globalPC & 0xC000) == 0x4000) {
			setPC(localPC + globalPC);
		}
	}
	
	private final void mapRam(int bankNo) {
		currentRamBank = bankNo;
		memory[5] = cartRam[bankNo];
	}
	
	/** Writes to an address in CPU address space.  Writes to ROM may cause a mapping change.
		*/
	private final void cartridgeWrite(int addr, int data) {
		int halfbank = addr >> 13;
		int subaddr = addr & 0x1fff;
		int ramAddress = 0;
		
		switch (cartType) {
			case 0: /* ROM Only */
				break;
				
			case 1: /* MBC1 */
			case 2:
			case 3:
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
			case 0x13: /* MBC3 */
				
				// Select ROM bank
				if (halfbank == 1) {
					int bankNo = data & 0x7F;
					if (bankNo == 0)
						bankNo = 1;
					mapRom(bankNo);
				} else if (halfbank == 2) {
					// Select RAM bank
					mapRam(data);
				}
				if (halfbank == 5) {
					memory[halfbank][subaddr] = (byte) data;
				}
				break;
				
				
			case 0x19:
			case 0x1A:
			case 0x1B:
			case 0x1C:
			case 0x1D:
			case 0x1E:
				
				if ((addr >= 0x2000) && (addr <= 0x2FFF)) {
					int bankNo = (currentRomBank & 0xFF00) | data;
					mapRom(bankNo);
				} else if ((addr >= 0x3000) && (addr <= 0x3FFF)) {
					int bankNo = (currentRomBank & 0x00FF) | ((data & 0x01) << 8);
					mapRom(bankNo);
				} else if (halfbank == 2) {
					mapRam(data & 0x07);
				} else if (halfbank == 5) {
					memory[halfbank][subaddr] = (byte) data;
				}
				break;
		}
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
		*  of 16Kb ROM banks the cartridge will contain
		*/
		int[][] romSizeTable = { { 0, 2}, { 1, 4}, { 2, 8}, { 3, 16}, { 4, 32}, { 5, 64}, { 6, 128}, { 7, 256}, { 0x52, 72}, { 0x53, 80}, { 0x54, 96}};
		
		int i = 0;
		while ((i < romSizeTable.length) && (romSizeTable[i][0] != sizeByte)) {
			i++;
		}
		
		if (romSizeTable[i][0] == sizeByte) {
			return romSizeTable[i][1];
		} else {
			return -1;
		}
	}
}
