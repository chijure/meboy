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
import javax.microedition.midlet.*;
import javax.microedition.rms.*;
import javax.microedition.lcdui.game.Sprite;


public class GBCanvas extends Canvas implements CommandListener {
	public MeBoy parent;
	private Dmgcpu cpu;
	private int w, h, l, t, sw, sh, trans;
	private boolean clear = true;
	boolean showFps = MeBoy.debug;
	private boolean fullScreen;
	
	private Command pause = new Command("Pause", Command.SCREEN, 0);
	private Command resume = new Command("Resume", Command.SCREEN, 0);
	
	private static int[] key = new int[] {KEY_NUM6, KEY_NUM4, KEY_NUM2, KEY_NUM8, KEY_NUM7, KEY_NUM9, KEY_POUND, KEY_STAR};
	private static String[] keyName = {"right", "left", "up", "down", "a", "b", "select", "start"};
	private int keySetCounter;
	private boolean settingKeys;
	
	private String cartName;
	
	private Image buf;
	private Graphics bufg;
	
	
	private GBCanvas() {
		addCommand(pause);
		addCommand(new Command("Exit", Command.EXIT, 1));
		if (showFps)
			addCommand(new Command("Hide framerate", Command.SCREEN, 10));
		else
			addCommand(new Command("Show framerate", Command.SCREEN, 10));
		addCommand(new Command("Set buttons", Command.SCREEN, 11));
		addCommand(new Command("Show log", Command.SCREEN, 12));
		addCommand(new Command("Suspend", Command.SCREEN, 16));
		addCommand(new Command("Full screen", Command.SCREEN, 17));
		addCommand(new Command("Unload cart", Command.SCREEN, 2));
		setCommandListener(this);
		
		if (MeBoy.rotations != 0) {
			buf = Image.createImage(160, 144);
			bufg = buf.getGraphics();
		}
	}
	
	public GBCanvas(MeBoy p) {
		this();
		parent = p;
		
		try {
			RecordStore rs = RecordStore.openRecordStore("suspended", true);
			byte[] b = rs.getRecord(1);
			rs.closeRecordStore();
			
			StringBuffer sb = new StringBuffer();
			int i = 0;
			while (b[i] != 0) {
				sb.append((char) b[i]);
				i++;
			}
			cartName = sb.toString();
			
			cpu = new Dmgcpu(cartName, this, b, i+1);
			
			setDimensions();
			
			new Thread(cpu).start();
		} catch (Throwable e) {
			if (MeBoy.debug)
				e.printStackTrace();
			throw new RuntimeException("resumption error");
		}
	}
	
	public GBCanvas(MeBoy p, String cart, boolean timing) {
		this();
		parent = p;
		cartName = cart;
		
		cpu = new Dmgcpu(cart, this);
		cpu.timing = timing;
		
		if (cpu.hasBattery())
			loadCartRam();
		
		setDimensions();
		
		new Thread(cpu).start();
	}
	
	public void setDimensions() {
		w = getWidth();
		h = getHeight();
		
		int[] transs = new int[] {0, 5, 3, 6};
		trans = transs[MeBoy.rotations];
		
		if ((MeBoy.rotations & 1) == 1) {
			sw = 144;
			sh = 160;
		} else {
			sw = 160;
			sh = 144;
		}
		
		l = (w - sw) / 2;
		t = (h - sh) / 2;
		if (showFps)
			t -= 8;
		
		if (l < 0)
			l = 0;
		if (t < 0)
			t = 0;
		
		cpu.graphicsChip.left = trans == 0 ? l : 0;
		cpu.graphicsChip.top = trans == 0 ? t : 0;
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
			if (label == "Exit") {
				if (cpu.hasBattery())
					saveCartRam();
				parent.destroyApp(true);
				parent.notifyDestroyed();
			} else if (label == "Unload cart") {
				if (cpu.hasBattery())
					saveCartRam();
				
				parent.unloadCart();
				Runtime.getRuntime().gc();
			} else if (label == "Pause") {
				removeCommand(pause);
				addCommand(resume);
				
				cpu.terminate = true;
			} else if (label == "Resume" && !settingKeys) {
				removeCommand(resume);
				addCommand(pause);
				
				new Thread(cpu).start();
			} else if (label == "Show framerate") {
				removeCommand(c);
				addCommand(new Command("Hide framerate", Command.SCREEN, 10));
				showFps = true;
				setDimensions();
			} else if (label == "Hide framerate") {
				removeCommand(c);
				addCommand(new Command("Show framerate", Command.SCREEN, 10));
				showFps = false;
				setDimensions();
			} else if (label == "Set buttons" && !settingKeys) {
				settingKeys = true;
				keySetCounter = 0;
			} else if (label == "Suspend" && !settingKeys) {
				if (!cpu.terminate) {
					removeCommand(pause);
					addCommand(resume);
					cpu.terminate = true;
					Thread.sleep(100);
				}
				
				suspend();
			} else if (label == "Full screen" && !settingKeys) {
				fullScreen = !fullScreen;
				setFullScreenMode(fullScreen);
				
				setDimensions();
			} else if (label == "Show log" && !settingKeys) {
				MeBoy.log("memory: " + Runtime.getRuntime().freeMemory() + "/" + Runtime.getRuntime().totalMemory());
				parent.showLog();
			}
		} catch (Throwable t) {
			MeBoy.log(t.toString());
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
	
	public final void paint(Graphics g) {
		if (!clear) {
			if (showFps) {
				g.setClip(l, t+sh, 80, 16);
				g.setColor(-1);
				g.fillRect(l, t+sh, 80, 16);
				g.setColor(0);
				g.drawString("skip: " + cpu.graphicsChip.lastSkipCount, l, t+sh, 20);
			}
			g.setClip(l, t, sw, sh);
			if (trans == 0) {
				cpu.graphicsChip.draw(g);
			} else {
				cpu.graphicsChip.draw(bufg);
				g.drawRegion(buf, 0, 0, 160, 144, trans, l, t, 20);
			}
		} else if (settingKeys) {
			g.setColor(0x446688);
			g.fillRect(0, 0, w, h);
			g.setColor(-1);
			g.drawString("press the", w/2, h/2-18, 65);
			g.drawString("button for", w/2, h/2, 65);
			g.drawString(keyName[keySetCounter], w/2, h/2+18, 65);
		} else {
			if (g.getClipHeight() == h)
				clear = false;
			// full redraw
			g.setColor(0x446688);
			g.fillRect(0, 0, w, h);
			g.setColor(-1);
			g.fillRect(l, t, sw, sh);
			
			if (showFps) {
				g.setClip(l, t+sh, 80, 16);
				g.setColor(-1);
				g.fillRect(l, t+sh, 80, 16);
				g.setColor(0);
				g.drawString("skip: " + cpu.graphicsChip.lastSkipCount, l, t+sh, 20);
			}
			
			g.setClip(l, t, sw, sh);
			if (trans == 0) {
				cpu.graphicsChip.draw(g);
			} else {
				cpu.graphicsChip.draw(bufg);
				g.drawRegion(buf, 0, 0, 160, 144, trans, l, t, 20);
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
			
			byte[] b = new byte[40];
			
			for (int i = 0; i < 8; i++)
				setInt(b, i * 4, key[i]);
			setInt(b, 32, GraphicsChip.maxFrameSkip);
			setInt(b, 36, MeBoy.rotations);
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, 40);
			} else {
				rs.setRecord(1, b, 0, 40);
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
	
	public static final void readSettings() {
		try {
			RecordStore rs = RecordStore.openRecordStore("set", true);
			if (rs.getNumRecords() > 0) {
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < 8; i++)
					key[i] = getInt(b, i * 4);
				GraphicsChip.maxFrameSkip = getInt(b, 32);
				if (b.length >= 40)
					MeBoy.rotations = getInt(b, 36);
			} else {
				writeSettings();
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
	
	private final void saveCartRam() {
		try {
			RecordStore rs = RecordStore.openRecordStore(cartName, true);
			
			byte[][] ram = cpu.cartRam;
			
			int bankCount = ram.length;
			int bankSize = ram[0].length;
			int size = bankCount * bankSize;
			
			byte[] b = new byte[size];
			
			for (int i = 0; i < bankCount; i++)
				System.arraycopy(ram[i], 0, b, i * bankSize, bankSize);
			
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
				byte[][] ram = cpu.cartRam;
				int bankCount = ram.length;
				int bankSize = ram[0].length;
				int size = bankCount * bankSize;
				
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < bankCount; i++)
					System.arraycopy(b, i * bankSize, ram[i], 0, bankSize);
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
	
	private final void suspend() {
		try {
			RecordStore rs = RecordStore.openRecordStore("suspended", true);
			
			byte[] b = cpu.flatten();
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, b.length);
			} else {
				rs.setRecord(1, b, 0, b.length);
			}
			
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
}

