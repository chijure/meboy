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
import javax.microedition.midlet.*;
import javax.microedition.rms.*;


public class GBCanvas extends Canvas implements CommandListener {
	public MeBoy parent;
	private Dmgcpu cpu;
	private int w, h;
	private boolean clear = true;
	boolean showFps;
	
	private int[] key = new int[] {KEY_NUM6, KEY_NUM4, KEY_NUM2, KEY_NUM8, KEY_NUM7, KEY_NUM9, KEY_POUND, KEY_STAR};
	private static String[] keyName = {"right", "left", "up", "down", "a", "b", "select", "start"};
	private int keySetCounter;
	private boolean settingKeys;
	
	public GBCanvas(MeBoy p, String cart, boolean timer) {
		parent = p;
		
		w = getWidth();
		h = getHeight();
		
		readSettings();
		
		addCommand(new Command("Pause", Command.SCREEN, 0));
		addCommand(new Command("Exit", Command.EXIT, 1));
		
		addCommand(new Command("Show framerate", Command.SCREEN, 10));
		addCommand(new Command("Set buttons", Command.SCREEN, 11));
		addCommand(new Command("Show log", Command.SCREEN, 12));
		addCommand(new Command("Memory", Command.SCREEN, 14));
		setCommandListener(this);
		
		cpu = new Dmgcpu(cart, this);
		
		MeBoy.log("freemem: " + Runtime.getRuntime().freeMemory());
		
		if (timer) {
			cpu.graphicsChip.frameSkip = 0x7fffffff;
		}
		
		new Thread(cpu).start();
	}
	
	public void keyReleased(int keyCode) {
		for (int i = 0; i < 8; i++)
			if (keyCode == key[i]) {
				cpu.padDown[i] = false;
			}
		cpu.triggerInterruptIfEnabled(cpu.INT_P10);
	}
	
	public void keyPressed(int keyCode) {
		if (settingKeys) {
			key[keySetCounter++] = keyCode;
			if (keySetCounter == 8) {
				writeSettings();
				settingKeys = false;
				clear = true;
			}
			repaint();
			return;
		}
		
		for (int i = 0; i < 8; i++)
			if (keyCode == key[i]) {
				cpu.padDown[i] = true;
			}
		cpu.triggerInterruptIfEnabled(cpu.INT_P10);
		
		if (keyCode == KEY_NUM0 && MeBoy.debug)
			showFps = !showFps;
	}
	
	public void commandAction(Command c, Displayable s) {
		try {
			String label = c.getLabel();
			if (label == "Exit") {
				parent.destroyApp(true);
				parent.notifyDestroyed();
			} else if (label == "Pause") {
				removeCommand(c);
				addCommand(new Command("Resume", Command.SCREEN, 0));
				
				cpu.terminate = true;
			} else if (label == "Resume" && !settingKeys) {
				removeCommand(c);
				addCommand(new Command("Pause", Command.SCREEN, 0));
				
				new Thread(cpu).start();
			} else if (label == "Memory") {
				MeBoy.log("total: " + Runtime.getRuntime().totalMemory());
				MeBoy.log("free: " + Runtime.getRuntime().freeMemory());
				parent.showLog();
			} else if (label == "Show framerate") {
				removeCommand(c);
				addCommand(new Command("Hide framerate", Command.SCREEN, 10));
				showFps = true;
				clear = true;
			} else if (label == "Hide framerate") {
				removeCommand(c);
				addCommand(new Command("Show framerate", Command.SCREEN, 10));
				showFps = false;
				clear = true;
			} else if (label == "Set buttons" && !settingKeys) {
				settingKeys = true;
				keySetCounter = 0;
			} else if (label == "Show log") {
				parent.showLog();
			}
		} catch (Throwable t) {
			MeBoy.log(t.toString());
		}
		repaint();
	}
	
	public final void repaintSmall() {
		if (showFps)
			repaint(0, 0, w, 160);
		else {
			repaint(0, 0, 160, 144);
		}
	}
	
	public final void showNotify() {
		clear = true;
		repaint();
	}
	
	public final void paint(Graphics g) {
		if (g.getClipWidth() == 160) {
			cpu.graphicsChip.draw(g, 0, 0);
		} else {
			if (settingKeys) {
				g.setColor(0x446688);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(-1);
				g.drawString("press the", w/2, 18, 65);
				g.drawString("button for", w/2, 36, 65);
				g.drawString(keyName[keySetCounter], w/2, 54, 65);
			} else {
				if (clear) {
					g.setColor(-1);
					g.fillRect(0, 0, 160, 144);
					g.setColor(0x446688);
					g.fillRect(160, 0, w-160, 144);
					g.fillRect(0, 144, w, h-144);
					clear = false;
				}
				
				if (showFps) {
					g.setClip(0, 144, 100, 16);
					g.setColor(-1);
					g.fillRect(0, 144, 100, 16);
					g.setColor(0);
					if (cpu != null)
						g.drawString(((10000 + (cpu.graphicsChip.lastFrameLength >> 1)) / cpu.graphicsChip.lastFrameLength) + "fps", 0, 144, 20);
				}
				
				g.setClip(0, 0, 160, 144);
				
				if (cpu != null) {
					cpu.graphicsChip.draw(g, 0, 0);
				}
			}
		}
	}
	
	void writeSettings() {
		try {
			RecordStore rs = RecordStore.openRecordStore("set", true);
			
			byte[] b = new byte[32];
			
			for (int i = 0; i < 8; i++)
				setInt(b, i*4, key[i]);
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, 32);
			} else {
				rs.setRecord(1, b, 0, 32);
			}
			rs.closeRecordStore();
		} catch (Throwable e) {
		}
	}
	
	void setInt(byte[] b, int i, int v) {
		b[i++] = (byte) (v >> 24);
		b[i++] = (byte) (v >> 16);
		b[i++] = (byte) (v >> 8);
		b[i++] = (byte) (v);
	}
	
	int getInt(byte[] b, int i) {
		int r = b[i++] & 0xFF;
		r = (r << 8) + (b[i++] & 0xFF);
		r = (r << 8) + (b[i++] & 0xFF);
		return (r << 8) + (b[i++] & 0xFF);
	}
	
	void readSettings() {
		try {
			RecordStore rs = RecordStore.openRecordStore("set", true);
			if (rs.getNumRecords() > 0) {
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < 8; i++)
					key[i] = getInt(b, i*4);
			}
			rs.closeRecordStore();
		} catch (Throwable e) {
		}
	}
}