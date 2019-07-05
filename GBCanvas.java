/*

MeBoy

Copyright 2005-2008 Bjorn Carlin
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
import javax.microedition.rms.*;


public class GBCanvas extends Canvas implements CommandListener {
	public MeBoy parent;
	private ICpu cpu;
	private int w, h, l, t;
	private int sw, sh, trans; // translation, and screen size (on screen, i.e. scaled and rotated if applicable)
	private int ssw, ssh; // source screen width and height (possibly scaled, not rotated)
	private boolean clear = true;
	boolean showFps = MeBoy.debug;
	private int[] previousTime = new int[8];
	private int previousTimeIx;
	private boolean fullScreen;
	
	private Command pause = new Command(MeBoy.literal[34], Command.SCREEN, 0);
	private Command resume = new Command(MeBoy.literal[35], Command.SCREEN, 0);
	
	private static int[] key = new int[] {KEY_NUM6, KEY_NUM4, KEY_NUM2, KEY_NUM8, KEY_NUM7, KEY_NUM9, KEY_POUND, KEY_STAR};
	private String[] keyName = {
		MeBoy.literal[36],
		MeBoy.literal[37],
		MeBoy.literal[38],
		MeBoy.literal[39],
		MeBoy.literal[40],
		MeBoy.literal[41],
		MeBoy.literal[42],
		MeBoy.literal[43]};
	private int keySetCounter;
	private boolean settingKeys;
	
	private String cartName;
	private String suspendName;
	
	private Image buf;
	private Graphics bufg;
	
    private Thread cpuThread;
	
	
	// Common constructor
	private GBCanvas() {
		addCommand(pause);
		addCommand(new Command(MeBoy.literal[9], Command.EXIT, 1));
		if (showFps)
			addCommand(new Command(MeBoy.literal[44], Command.SCREEN, 10));
		else
			addCommand(new Command(MeBoy.literal[45], Command.SCREEN, 10));
		addCommand(new Command(MeBoy.literal[46], Command.SCREEN, 11));
		addCommand(new Command(MeBoy.literal[47], Command.SCREEN, 16));
		addCommand(new Command(MeBoy.literal[48], Command.SCREEN, 17));
		addCommand(new Command(MeBoy.literal[49], Command.SCREEN, 2));
		setCommandListener(this);
		
		if (MeBoy.rotations != 0) {
			buf = Image.createImage(160, 144);
			bufg = buf.getGraphics();
		}
		
		fullScreen = MeBoy.fullScreen;
		setFullScreenMode(fullScreen);
	}
	
	// Constructor for loading suspended games
	public GBCanvas(MeBoy p, String suspendName) throws RecordStoreException {
		this();
		parent = p;
		this.suspendName = suspendName;
		
		RecordStore rs = RecordStore.openRecordStore("s" + suspendName, false);
		byte[] b = rs.getRecord(1);
		rs.closeRecordStore();
		
		StringBuffer sb = new StringBuffer();
		int i = 0;
		while (b[i] != 0) {
			sb.append((char) b[i]);
			i++;
		}
		cartName = sb.toString();
		
		if (isColor(cartName) && !MeBoy.disableColor)
			cpu = new DmgcpuColor(cartName, this, b, i+1);
		else
			cpu = new DmgcpuBW(cartName, this, b, i+1);
		setDimensions();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
	}
	
	// Constructor for new games
	public GBCanvas(MeBoy p, String cart, boolean timing) {
		this();
		parent = p;
		cartName = cart;
		
		// MeBoy.timing = timing;
		
		if (isColor(cart) && !MeBoy.disableColor)
			cpu = new DmgcpuColor(cart, this);
		else
			cpu = new DmgcpuBW(cart, this);
		
		if (cpu.hasBattery())
			loadCartRam();
		
		setDimensions();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
	}
	
	private boolean isColor(String cartName) {
		java.io.InputStream is = null;
		
		try {
			is = getClass().getResourceAsStream(cartName + 0);
			
			if (is.skip(0x143) != 0x143)
				throw new RuntimeException("failed skipping to 0x143");
			int i = is.read();
			if (i >= 0 && (i & 0x80) == 0) {
				return false;
			}
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		} finally {
			try {
			if (is != null)
				is.close();
			} catch (Exception ex) {}
		}
		return true;
	}
	
	public void setDimensions() {
		w = getWidth();
		h = getHeight();
		
		int[] transs = new int[] {0, 5, 3, 6};
		trans = transs[MeBoy.rotations];
		boolean rotate = (MeBoy.rotations & 1) != 0;
		
		if (MeBoy.enableScaling) {
			int deltah = showFps ? -16: 0;
			cpu.setScale(rotate ? (h+deltah) : w, rotate ? w : (h+deltah));
		}
		ssw = sw = 20 * cpu.getTileWidth();
		ssh = sh = 18 * cpu.getTileHeight();
		
		if (MeBoy.rotations != 0) {
			buf = Image.createImage(ssw, ssh);
			bufg = buf.getGraphics();
		}
		
		if (rotate) {
			sw = ssh;
			sh = ssw;
		}
		
		l = (w - sw) / 2;
		t = (h - sh) / 2;
		if (showFps)
			t -= 8;
		
		if (l < 0)
			l = 0;
		if (t < 0)
			t = 0;
		
		cpu.setTranslation(trans == 0 ? l : 0, trans == 0 ? t : 0);
	}
	
	public void keyReleased(int keyCode) {
		for (int i = 0; i < 8; i++) {
			if (keyCode == key[i]) {
				cpu.buttonUp(i);
			}
		}
	}
	
	public void keyPressed(int keyCode) {
		if (settingKeys) {
			key[keySetCounter++] = keyCode;
			if (keySetCounter == 8) {
				writeSettings();
				settingKeys = false;
			}
			clear = true;
			repaint();
			return;
		}
		
		for (int i = 0; i < 8; i++) {
			if (keyCode == key[i]) {
				cpu.buttonDown(i);
			}
		}
		
//		if (keyCode == KEY_NUM0 && MeBoy.debug)
//			showFps = !showFps;
	}
	
	public void commandAction(Command c, Displayable s) {
		try {
			String label = c.getLabel();
			if (label == MeBoy.literal[9]) {
				if (cpu.hasBattery())
					saveCartRam();
				parent.destroyApp(true);
				parent.notifyDestroyed();
			} else if (label == MeBoy.literal[49]) {
				if (cpu.hasBattery())
					saveCartRam();
				
				parent.unloadCart();
				Runtime.getRuntime().gc();
			} else if (label == MeBoy.literal[34]) {
				removeCommand(pause);
				addCommand(resume);
				
				cpu.terminate();
			} else if (label == MeBoy.literal[35] && !settingKeys) {
				removeCommand(resume);
				addCommand(pause);
				
				cpuThread = new Thread(cpu);
				cpuThread.start();
			} else if (label == MeBoy.literal[45]) {
				removeCommand(c);
				addCommand(new Command(MeBoy.literal[44], Command.SCREEN, 10));
				showFps = true;
				setDimensions();
			} else if (label == MeBoy.literal[44]) {
				removeCommand(c);
				addCommand(new Command(MeBoy.literal[45], Command.SCREEN, 10));
				showFps = false;
				setDimensions();
			} else if (label == MeBoy.literal[46] && !settingKeys) {
				settingKeys = true;
				keySetCounter = 0;
			} else if (label == MeBoy.literal[47] && !settingKeys) {
				if (!cpu.isTerminated()) {
					cpu.terminate();
					while(cpuThread.isAlive()) {
						Thread.yield();
					}
					suspend();
					
					cpuThread = new Thread(cpu);
					cpuThread.start();
				} else {
					suspend();
				}
			} else if (label == MeBoy.literal[48] && !settingKeys) {
				fullScreen = !fullScreen;
				setFullScreenMode(fullScreen);
				
				setDimensions();
			}
		} catch (Throwable t) {
			MeBoy.log(t.toString());
			MeBoy.showLog();
		}
		clear = true;
		repaint();
	}
	
	public final void redrawSmall() {
		if (showFps)
			repaint(l, t, sw, sh+16);
		else
			repaint(l, t, sw, sh);
	}
	
	public final void showNotify() {
		clear = true;
		repaint();
	}
	
	public final void paintFps(Graphics g) {
		g.setClip(l, t+sh, 80, 16);
		g.setColor(-1);
		g.fillRect(l, t+sh, 80, 16);
		g.setColor(0);
		
		int now = (int) System.currentTimeMillis();
		// calculate moving-average fps
		// 17 ms * 60 fps * 2*8 seconds = 16320 ms
		int estfps = ((16320 + now - previousTime[previousTimeIx]) / (now - previousTime[previousTimeIx])) >> 1;
		previousTime[previousTimeIx] = now;
		previousTimeIx = (previousTimeIx + 1) & 7;
		
		g.drawString(estfps + " fps * " + (cpu.getLastSkipCount() + 1), l+1, t+sh, 20);
	}
	
	public final void paint(Graphics g) {
		if (cpu == null)
			return;
		
		if (!clear) {
			if (showFps) {
				paintFps(g);
			}
			g.setClip(l, t, sw, sh);
			if (trans == 0) {
				cpu.draw(g);
			} else {
				cpu.draw(bufg);
				g.drawRegion(buf, 0, 0, ssw, ssh, trans, l, t, 20);
			}
		} else if (settingKeys) {
			g.setColor(0x446688);
			g.fillRect(0, 0, w, h);
			g.setColor(-1);
			g.drawString(MeBoy.literal[52], w/2, h/2-18, 65);
			g.drawString(MeBoy.literal[53], w/2, h/2, 65);
			g.drawString(keyName[keySetCounter], w/2, h/2+18, 65);
		} else {
			if (g.getClipHeight() == h)
				clear = false;
			// full redraw
			g.setColor(0x666666);
			g.fillRect(0, 0, w, h);
			g.setColor(-1);
			g.fillRect(l, t, sw, sh);
			
			if (showFps) {
				paintFps(g);
			}
			
			g.setClip(l, t, sw, sh);
			if (trans == 0) {
				cpu.draw(g);
			} else {
				cpu.draw(bufg);
				g.drawRegion(buf, 0, 0, ssw, ssh, trans, l, t, 20);
			}
		}
	}
	
	public static final void setInt(byte[] b, int i, int v) {
		b[i++] = (byte) (v >> 24);
		b[i++] = (byte) (v >> 16);
		b[i++] = (byte) (v >> 8);
		b[i++] = (byte) (v);
	}
	
	public static final int getInt(byte[] b, int i) {
		int r = b[i++] & 0xFF;
		r = (r << 8) + (b[i++] & 0xFF);
		r = (r << 8) + (b[i++] & 0xFF);
		return (r << 8) + (b[i++] & 0xFF);
	}
	
	public static final void writeSettings() {
		try {
			RecordStore rs = RecordStore.openRecordStore("set", true);
			
			int bLength = 53;
			for (int i = 0; i < MeBoy.suspendIndex.length; i++)
				bLength += 1 + MeBoy.suspendIndex[i].length();
			bLength++;
			
			byte[] b = new byte[bLength];
			
			for (int i = 0; i < 8; i++)
				setInt(b, i * 4, key[i]);
			setInt(b, 32, MeBoy.maxFrameSkip);
			setInt(b, 36, MeBoy.rotations);
			setInt(b, 40, MeBoy.lazyLoadingThreshold);
			setInt(b, 44, MeBoy.suspendCounter);
			setInt(b, 48, MeBoy.suspendIndex.length);
			
			int index = 52;
			for (int i = 0; i < MeBoy.suspendIndex.length; i++) {
				String s = MeBoy.suspendIndex[i];
				b[index++] = (byte) (s.length());
				for (int j = 0; j < s.length(); j++)
					b[index++] = (byte) s.charAt(j);
			}
			
			b[index++] = (byte) ((MeBoy.enableScaling ? 1 : 0) + (MeBoy.keepProportions ? 2 : 0) +
				(MeBoy.fullScreen ? 4 : 0) + (MeBoy.disableColor ? 8 : 0));
			b[index++] = (byte) MeBoy.language;
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, bLength);
			} else {
				rs.setRecord(1, b, 0, bLength);
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
		}
	}
	
	public static final void readSettings() {
		try {
			RecordStore rs = RecordStore.openRecordStore("set", true);
			if (rs.getNumRecords() > 0) {
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < 8; i++)
					key[i] = getInt(b, i * 4);
				if (b.length >= 36)
					MeBoy.maxFrameSkip = getInt(b, 32);
				if (b.length >= 40)
					MeBoy.rotations = getInt(b, 36);
				if (b.length >= 44)
					MeBoy.lazyLoadingThreshold = getInt(b, 40);
				
				if (b.length >= 48) {
					// suspended games index
					MeBoy.suspendCounter = getInt(b, 44);
					MeBoy.suspendIndex = new String[getInt(b, 48)];
					
					int index = 52;
					for (int i = 0; i < MeBoy.suspendIndex.length; i++) {
						int slen = b[index++] & 0xff;
						
						MeBoy.suspendIndex[i] = new String(b, index, slen);
						index += slen;
					}
					
					if (b.length > index) {
						MeBoy.enableScaling = (b[index] & 1) != 0;
						MeBoy.keepProportions = (b[index] & 2) != 0;
						MeBoy.fullScreen = (b[index] & 4) != 0;
						MeBoy.disableColor = (b[index] & 8) != 0;
						MeBoy.language = (b[index] & 16) != 0 ? 1 : 0;
						index++;
					}
					
					if (b.length > index) {
						MeBoy.language = b[index++];
					}
				} else
					MeBoy.suspendIndex = new String[0];
			} else {
				writeSettings();
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
		}
	}
	
	private final void saveCartRam() {
		try {
			RecordStore rs = RecordStore.openRecordStore(cartName, true);
			
			byte[][] ram = cpu.getCartRam();
			
			int bankCount = ram.length;
			int bankSize = ram[0].length;
			int size = bankCount * bankSize + 13;
			
			byte[] b = new byte[size];
			
			for (int i = 0; i < bankCount; i++)
				System.arraycopy(ram[i], 0, b, i * bankSize, bankSize);
			
			System.arraycopy(cpu.getRtcReg(), 0, b, bankCount * bankSize, 5);
			long now = System.currentTimeMillis();
			setInt(b, bankCount * bankSize + 5, (int) (now >> 32));
			setInt(b, bankCount * bankSize + 9, (int) now);
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, size);
			} else {
				rs.setRecord(1, b, 0, size);
			}
			
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
	
	private final void loadCartRam() {
		try {
			RecordStore rs = RecordStore.openRecordStore(cartName, true);
			
			if (rs.getNumRecords() > 0) {
				byte[][] ram = cpu.getCartRam();
				int bankCount = ram.length;
				int bankSize = ram[0].length;
				
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < bankCount; i++)
					System.arraycopy(b, i * bankSize, ram[i], 0, bankSize);
				
				if (b.length == bankCount * bankSize + 13) {
					// load real time clock
					System.arraycopy(b, bankCount * bankSize, cpu.getRtcReg(), 0, 5);
					long time = getInt(b, bankCount * bankSize + 5);
					time = (time << 32) + ((long) getInt(b, bankCount * bankSize + 9) & 0xffffffffL);
					time = System.currentTimeMillis() - time;
					cpu.rtcSkip((int) (time / 1000));
				}
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
		}
	}
	
	private final void suspend() {
		try {
			boolean insertIndex = false;
			if (suspendName == null) {
				suspendName = (MeBoy.suspendCounter++) + ": " + cartName;
				insertIndex = true;
			}
			RecordStore rs = RecordStore.openRecordStore("s" + suspendName, true);
			
			byte[] b = cpu.flatten();
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, b.length);
			} else {
				rs.setRecord(1, b, 0, b.length);
			}
			
			rs.closeRecordStore();
			if (insertIndex)
				MeBoy.addSuspendedGame(suspendName);
			MeBoy.log(MeBoy.literal[50] + suspendName);
		} catch (Exception e) {
			MeBoy.log(MeBoy.literal[51] + e);
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.showLog();
		}
	}
	
    public void releaseReferences() {
		// This code helps the garbage collector on some platforms.
		// (contributed by Alberto Simon)
        cpu.terminate();
        while(cpuThread.isAlive()) {
            Thread.yield();
        }
        cpu.releaseReferences();
        cpu = null;
        
        buf = null;
        bufg = null;
        cartName = null;
        pause = null;
        resume = null;
        previousTime = null;
        parent = null;
        System.gc();
    }
}

