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

import java.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

/**
 * The main class offers a list of games, read from the file "carts.txt". It
 * also handles logging.
 */

public class MeBoy extends MIDlet implements CommandListener {
	public static final boolean debug = false;
	// public static boolean timing;
	public static int rotations = 0;
	public static int maxFrameSkip = 3;
	public static boolean enableScaling = false;
	public static boolean keepProportions = false;
	public static boolean fullScreen = false;
	public static boolean disableColor = false;
	public static int lazyLoadingThreshold = 64; // number of banks, each
													// 0x4000 bytes = 16kB
	public static int suspendCounter = 1; // next index for saved games
	public static String[] suspendIndex = new String[0];

	public static String[] literal = new String[63];
	public static int language;

	private List mainMenu;

	private static Display display;
	private GBCanvas gbCanvas;
	private List cartList;
	private List suspendList;
	private Form settingsForm;

	private TextField frameSkipField;
	private TextField rotationField;
	private TextField loadThresholdField;
	private ChoiceGroup scalingGroup;
	private ChoiceGroup gbcGroup;
	private ChoiceGroup languageGroup;

	private static Form logForm = new Form("log");

	private static MeBoy instance;

	public void startApp() {
		instance = this;
		display = Display.getDisplay(this);
		logForm.addCommand(new Command("Return", Command.BACK, 0));

		GBCanvas.readSettings();

		try {
			// copy previous version's suspended game
			RecordStore rs = RecordStore.openRecordStore("suspended", false);
			byte[] b = rs.getRecord(1);
			rs.closeRecordStore();

			RecordStore.deleteRecordStore("suspended");

			String newName = suspendCounter++ + ": (from 1.1/1.2)";
			rs = RecordStore.openRecordStore("s" + newName, true);
			rs.addRecord(b, 0, b.length);
			rs.closeRecordStore();

			addSuspendedGame(newName);
		} catch (Exception e) {
			// no problem, probably just no suspended game from previous game
		}
		
		readLangFile();

		mainMenuAction();
	}

	public void readLangFile() {
		try {
			InputStreamReader isr = new InputStreamReader(getClass()
					.getResourceAsStream("/lang/" + language + ".txt"), "UTF-8");
			StringBuffer sb = new StringBuffer();
			int c;
			int counter = 0;
			while ((c = isr.read()) != -1) {
				if (c >= 32) {
					sb.append((char) c);
				} else if (sb.length() > 0) {
					literal[counter++] = sb.toString();
					sb.setLength(0);
				}
			}
			if (sb.length() > 0) {
				literal[counter++] = sb.toString();
			}
			isr.close();
			if (counter < 60)
				throw new RuntimeException("c = " + counter);
		} catch (Exception e) {
			if (language > 0) {
				language = 0;
				readLangFile();
				return;
			}
			
			log(literal[31] == null ? "Failed to read the language file." : literal[31]);
			log(e.toString());
			showLog();
			if (debug)
				e.printStackTrace();
		}
	}

	public void pauseApp() {
	}

	public void destroyApp(boolean unconditional) {
	}

	public static void showLog() {
		logForm.setCommandListener(instance);
		display.setCurrent(logForm);
	}

	public static void showAbout() {
		Form aboutForm = new Form(literal[1]);
		aboutForm.addCommand(new Command(literal[0], Command.BACK, 0));
		aboutForm.append(literal[2] + "\n"
				+ literal[3]);

		aboutForm.setCommandListener(instance);
		display.setCurrent(aboutForm);
	}

	public void unloadCart() {
		mainMenuAction();
		gbCanvas.releaseReferences();
		gbCanvas = null;
	}

	private void mainMenuAction() {
		mainMenu = new List(literal[4], List.IMPLICIT);
		mainMenu.append(literal[5], null);
		if (suspendIndex.length > 0)
			mainMenu.append(literal[6], null);
		mainMenu.append(literal[7], null);
		mainMenu.append(literal[8], null);
		mainMenu.append(literal[1], null);
		mainMenu.append(literal[9], null);
		mainMenu.setCommandListener(this);
		display.setCurrent(mainMenu);
	}

	private void newGameAction() {
		if (cartList == null) {
			cartList = new List(literal[10], List.IMPLICIT);
			StringBuffer sb = new StringBuffer();

			try {
				java.io.InputStream is = getClass().getResourceAsStream(
						"/carts.txt");
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

				cartList.addCommand(new Command(literal[0], Command.BACK, 1));
				if (debug)
					cartList.addCommand(new Command("time", Command.SCREEN, 2));

				cartList.setCommandListener(this);
				display.setCurrent(cartList);
			} catch (Exception e) {
				log(literal[11]);
				log(e.toString());
				showLog();
				if (debug)
					e.printStackTrace();
			}
		} else {
			display.setCurrent(cartList);
		}
	}

	private void resumeGameAction() {
		if (suspendIndex.length == 0) {
			mainMenuAction();
			return;
		}
		suspendList = new List(literal[10], List.IMPLICIT);

		for (int i = 0; i < suspendIndex.length; i++)
			suspendList.append(suspendIndex[i], null);

		suspendList.addCommand(new Command(literal[12], Command.SCREEN, 2));
		suspendList.addCommand(new Command(literal[13], Command.SCREEN, 2));
		suspendList.addCommand(new Command(literal[0], Command.BACK, 1));
		suspendList.setCommandListener(this);
		display.setCurrent(suspendList);
	}

	public void commandAction(Command com, Displayable s) {
		String label = com.getLabel();

		if ("".equals(label) && s == mainMenu) {
			String item = mainMenu.getString(mainMenu.getSelectedIndex());
			if (item == literal[5]) {
				newGameAction();
			} else if (item == literal[6]) {
				resumeGameAction();
			} else if (item == literal[7]) {
				settingsForm = new Form(literal[7]);

				frameSkipField = new TextField(literal[17], Integer
						.toString(maxFrameSkip), 2, TextField.NUMERIC);
				settingsForm.append(frameSkipField);
				rotationField = new TextField(literal[18], Integer
						.toString(rotations), 2, TextField.NUMERIC);
				settingsForm.append(rotationField);
				loadThresholdField = new TextField(literal[19], Integer
								.toString(lazyLoadingThreshold), 3,
						TextField.NUMERIC);
				settingsForm.append(loadThresholdField);

				scalingGroup = new ChoiceGroup(literal[20], ChoiceGroup.MULTIPLE,
						new String[] { literal[21], literal[22], literal[23] }, null);
				scalingGroup.setSelectedIndex(0, enableScaling);
				scalingGroup.setSelectedIndex(1, keepProportions);
				scalingGroup.setSelectedIndex(2, fullScreen);
				settingsForm.append(scalingGroup);

				gbcGroup = new ChoiceGroup(literal[24],
						ChoiceGroup.MULTIPLE, new String[] { literal[25] },
						null);
				gbcGroup.setSelectedIndex(0, disableColor);
				settingsForm.append(gbcGroup);
				
			    languageGroup = new ChoiceGroup(literal[14], ChoiceGroup.EXCLUSIVE, new String[] {
			    		literal[15], literal[16], literal[54], literal[55], literal[56], literal[57], literal[58], literal[59]}, null);
                languageGroup.setSelectedIndex(language, true);
		        settingsForm.append(languageGroup);

				settingsForm.addCommand(new Command(literal[26], Command.OK, 1));
				settingsForm.setCommandListener(this);
				display.setCurrent(settingsForm);
			} else if (item == literal[8]) {
				log(literal[27] + Runtime.getRuntime().freeMemory() + "/"
						+ Runtime.getRuntime().totalMemory());
				showLog();
			} else if (item == literal[1]) {
				showAbout();
			} else if (item == literal[9]) {
				destroyApp(true);
				notifyDestroyed();
			}
		} else if ("".equals(label) && s == cartList) {
			String cart = cartList.getString(cartList.getSelectedIndex());
			try {
				gbCanvas = new GBCanvas(this, cart, false);
				display.setCurrent(gbCanvas);
			} catch (Exception e) {
				log(literal[28]);
				log(e.toString());
				showLog();
				if (debug)
					e.printStackTrace();
			}
		} else if (label == "time" && s == cartList) {
			String cart = cartList.getString(cartList.getSelectedIndex());
			try {
				gbCanvas = new GBCanvas(this, cart, true);
				display.setCurrent(gbCanvas);
			} catch (Exception e) {
				log(literal[29]);
				log(e.toString());
				showLog();
				if (debug)
					e.printStackTrace();
			}
		} else if ("".equals(label) && s == suspendList) {
			try {
				gbCanvas = new GBCanvas(this, suspendIndex[suspendList
						.getSelectedIndex()]);
				display.setCurrent(gbCanvas);
			} catch (Exception e) {
				log(literal[30]);
				log(e.toString());
				showLog();
				if (debug)
					e.printStackTrace();
			}
		} else if (label == literal[12]) {
			try {
				int index = suspendList.getSelectedIndex();
				String name = suspendIndex[index];

				// update index:
				String[] oldIndex = suspendIndex;
				suspendIndex = new String[oldIndex.length - 1];
				System.arraycopy(oldIndex, 0, suspendIndex, 0, index);
				System.arraycopy(oldIndex, index + 1, suspendIndex, index,
						suspendIndex.length - index);
				GBCanvas.writeSettings();

				// delete the state itself
				RecordStore.deleteRecordStore("s" + name);
			} catch (Exception e) {
				log(literal[31]);
				log(e.toString());
				showLog();
				if (debug)
					e.printStackTrace();
			}
			resumeGameAction();
		} else if (label == literal[13]) {
			try {
				int index = suspendList.getSelectedIndex();
				String oldName = suspendIndex[index];
				String newName = suspendCounter++
						+ oldName.substring(oldName.indexOf(':'));

				RecordStore rs = RecordStore.openRecordStore("s" + oldName,
						true);
				byte[] b = rs.getRecord(1);
				rs.closeRecordStore();

				rs = RecordStore.openRecordStore("s" + newName, true);
				rs.addRecord(b, 0, b.length);
				rs.closeRecordStore();

				addSuspendedGame(newName);
				resumeGameAction();
			} catch (Exception e) {
				log(literal[32]);
				log(e.toString());
				showLog();
				if (debug)
					e.printStackTrace();
			}
		} else if (com.getCommandType() == Command.BACK) {
			if (gbCanvas != null) {
				display.setCurrent(gbCanvas);
			} else {
				mainMenuAction();
			}
		} else if (s == settingsForm) {
			int f = Integer.parseInt(frameSkipField.getString());
			maxFrameSkip = Math.max(Math.min(f, 59), 0);
			rotations = Integer.parseInt(rotationField.getString()) & 3;
			lazyLoadingThreshold = Math.max(Integer.parseInt(loadThresholdField
					.getString()), 20);
			enableScaling = scalingGroup.isSelected(0);
			keepProportions = scalingGroup.isSelected(1);
			fullScreen = scalingGroup.isSelected(2);
			disableColor = gbcGroup.isSelected(0);
			
			int oldLanguage = language;
			for (int i = 0; i < languageGroup.size(); i++)
				if (languageGroup.isSelected(i))
					language = i;
			
			GBCanvas.writeSettings();
			if (oldLanguage != language) {
				readLangFile();
				cartList = null;
			}
			mainMenuAction();
		}
	}

	public static void log(String s) {
		if (s == null)
			return;
		logForm.append(s + '\n');
		if (debug)
			System.out.println(s);
	}

	// name should include number prefix
	public static void addSuspendedGame(String name) {
		String[] oldIndex = suspendIndex;
		suspendIndex = new String[oldIndex.length + 1];
		System.arraycopy(oldIndex, 0, suspendIndex, 0, oldIndex.length);
		suspendIndex[oldIndex.length] = name;
		GBCanvas.writeSettings();
	}
}
