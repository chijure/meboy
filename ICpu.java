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

public interface ICpu extends Runnable {
	public boolean hasBattery();
	
	public void buttonDown(int buttonIndex);

	public void buttonUp(int buttonIndex);
	
	public void setScale(int screenWidth, int screenHeight);
	
	public int getTileWidth();
	public int getTileHeight();
	
	public void setTranslation(int left, int top);
	
	public int getLastSkipCount();
	public void draw(Graphics g);
	
	public void terminate();
	public boolean isTerminated();
	
	public byte[][] getCartRam();
	public byte[] getRtcReg();
	public void rtcSkip(int s);
	
	public byte[] flatten();
	
	public void releaseReferences();
}
