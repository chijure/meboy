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

/**
 The main class offers a list of games, read from the file "carts.txt".
 It also handles logging.
*/

public class MeBoy extends MIDlet implements CommandListener {
	public static final boolean debug = false;
	
	private Display display;
	private GBCanvas gbCanvas;
	private List cartList;
	
	private static Form logForm = new Form("log");
	public static String lastLog = "";
	
	public void startApp() {
		display = Display.getDisplay(this);
		logForm.addCommand(new Command("Return", Command.BACK, 0));
		cartList = new List("Select Game", List.IMPLICIT);
		
		StringBuffer sb = new StringBuffer();
		
		try {
			java.io.InputStream is = getClass().getResourceAsStream("/carts.txt");
			int c;
			
			while ((c = is.read()) != -1) {
				if (c >= 32)
					sb.append((char) c);
				else if (sb.length() > 0) {
					cartList.append(sb.toString(), null);
					sb.setLength(0);
				}
			}
			if (sb.length() > 0)
				cartList.append(sb.toString(), null);
			
			is.close();
			cartList.append("Exit", null);
			if (debug)
				cartList.addCommand(new Command("time", Command.SCREEN, 1));
			
			cartList.setCommandListener(this);
			display.setCurrent(cartList);
		} catch (Exception e) {
			MeBoy.log("cartlist error");
			MeBoy.log(e.toString());
			showLog();
		}
	}
	
	public void pauseApp() {
	}
	
	
	public void destroyApp(boolean unconditional) {
	}
	
	public void showLog() {
		logForm.setCommandListener(this);
		display.setCurrent(logForm);
	}
	
	public void commandAction(Command c, Displayable s) {
		String cart = "/" + cartList.getString(cartList.getSelectedIndex());
		String label = c.getLabel();
		
		if (label == "Return") {
			if (gbCanvas != null) {
				display.setCurrent(gbCanvas);
			} else {
				display.setCurrent(cartList);
			}
		} else if (cart.equals("/Exit")) {
			destroyApp(true);
			notifyDestroyed();
		} else if (label == "time") {
			gbCanvas = new GBCanvas(this, cart, true);
			display.setCurrent(gbCanvas);
		} else {
			try {
				gbCanvas = new GBCanvas(this, cart, false);
				display.setCurrent(gbCanvas);
			} catch (Exception e) {
				if (e.getMessage() != null)
					log(e.getMessage());
				showLog();
			}
		}
	}
	
	public static void log(String s) {
		logForm.append(s + '\n');
		lastLog = s;
		if (debug)
			System.out.println(s);
	}
}

