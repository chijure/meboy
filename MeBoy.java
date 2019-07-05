/*

MeBoy

Copyright 2005-2009 Bjorn Carlin
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

import java.io.*;
import java.util.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

/**
 * The main class offers a list of games, read from the file "carts.txt". It
 * also handles logging.
 */
public class MeBoy extends MIDlet implements CommandListener {
	// Settings, etc.
	public static final boolean debug = false;
	public static int rotations = 0;
	public static int maxFrameSkip = 3;
	public static boolean enableScaling = true;
	public static int scalingMode = 0;
	public static boolean keepProportions = true;
	public static boolean fullScreen = false;
	public static boolean disableColor = false;
	public static boolean enableSound = false;
	public static boolean advancedSound = false;
	public static boolean advancedGraphics = false;
	public static boolean showFps = false;
	public static boolean showLogItem = false;
	public static int lazyLoadingThreshold = 64; // number of banks, each 0x4000 bytes = 16kB
	public static int language;
	private static boolean bluetoothAvailable = false;
	
	public static int suspendCounter = 1; // next index for saved games
	public static String[] suspendName10 = new String[0]; // v1-style suspended games
	public static String[] suspendName20 = new String[0]; // v2-style suspended games
	public static String[] literal = new String[80];
	private static int languageCount = 1;
	private static String[] languages = new String[] {"English"}; // if index.txt fails
	private static int[] languageLookup = new int[] {0};
	private String[] cartDisplayName = null;
	private String[] cartFileName = null;
	private String[] cartID = null;
	private int numCarts;
	private boolean fatalError;
	
	public static String logString = "";
	private static MeBoy instance;
	
	// UI components
	public static Display display;
	private List mainMenu;
	private Form messageForm;
	private GBCanvas gbCanvas;
	private List cartList;
	private List suspendList;
	
	// Settings
	private Form settingsForm;
	private TextField frameSkipField;
	private TextField rotationField;
	private TextField loadThresholdField;
	private TextField scalingModeField;
	private ChoiceGroup graphicsGroup;
	private ChoiceGroup miscSettingsGroup;
	private ChoiceGroup soundGroup;
	private ChoiceGroup languageGroup;
	
	private Bluetooth bluetooth;
	
	
	public void startApp() {
		if (instance == this) {
			return;
		}
		
		try {
			bluetoothAvailable = Bluetooth.available();
		} catch (NoClassDefFoundError t) {
		}
		
		instance = this;
		display = Display.getDisplay(this);

		GBCanvas.readSettings();

		readLangIndexFile();
		
		if (!readLiteralsFile())
			return;
		if (!readCartNames())
			return;
		if (!upgradeSavegames())
			return;

		showMainMenu();
	}

	private boolean readLiteralsFile() {
		try {
			InputStreamReader isr = new InputStreamReader(getClass().getResourceAsStream("/lang/" + language + ".txt"), "UTF-8");
			int counter = 0;
			String s;
			while ((s = next(isr)) != null)
				literal[counter++] = s;
			isr.close();
			while (counter < literal.length)
				literal[counter++] = "?";
		} catch (Exception e) {
			e.printStackTrace();
			if (language > 0) {
				language = 0;
				return readLiteralsFile();
			}
			
			showError("Failed to read the language file.", null, e);
			fatalError = true;
			return false;
		}
		return true;
	}

	private void readLangIndexFile() {
		try {
			InputStreamReader isr = new InputStreamReader(getClass().getResourceAsStream("/lang/index.txt"), "UTF-8");
			
			String s;
			languageLookup = new int[20];
			Vector langVector = new Vector();
			languageCount = 0;
			while ((s = next(isr)) != null) {
				languageLookup[languageCount++] = s.charAt(0) - 'a';
				langVector.addElement(next(isr));
			}
			languages = new String[languageCount];
			langVector.copyInto(languages);
			
			isr.close();
		} catch (Exception e) {
			languages = new String[] {"English"};
			languageLookup = new int[] {0};
			languageCount = 1;
			
			if (debug) {
				e.printStackTrace();
			}
		}
	}
	
	private String next(InputStreamReader isr) throws IOException {
		StringBuffer sb = new StringBuffer();
		int c;
		while ((c = isr.read()) != -1) {
			if (c >= 32) {
				sb.append((char) c);
			} else if (sb.length() > 0) {
				return sb.toString();
			}
		}
		if (sb.length() > 0) {
			return sb.toString();
		}
		return null;
	}
	
	private boolean readCartNames() {
		try {
			InputStream is = getClass().getResourceAsStream("/carts");
			DataInputStream dis = new DataInputStream(is);
			
			numCarts = dis.readInt();
			
			cartDisplayName = new String[numCarts];
			cartFileName = new String[numCarts];
			cartID = new String[numCarts];
			
			for (int i = 0; i < numCarts; i++) {
				cartDisplayName[i] = dis.readUTF();
				cartFileName[i] = dis.readUTF();
				cartID[i] = dis.readUTF();
			}
			is.close();
		} catch (Exception e) {
			fatalError = true;
			showError(literal[51], null, e);
			return false;
		}
		return true;
	}
	
	public boolean upgradeSavegames() {
		// upgrade cart ram:
		for (int i = 0; i < numCarts; i++) {
			String oldSaveName = cartFileName[i];
			String newSaveName = cartID[i];
			
			try {
				RecordStore rs = RecordStore.openRecordStore(oldSaveName, false);
				if (rs.getNumRecords() > 0) {
					byte[] b = rs.getRecord(1);
					
					RecordStore rs2 = RecordStore.openRecordStore("20R_" + newSaveName, true);
					
					if (rs2.getNumRecords() == 0) {
						rs2.addRecord(b, 0, b.length);
					} else {
						// Don't overwrite the new-style saveram
					}
					
					rs2.closeRecordStore();
					rs.deleteRecord(1);  // in case of exception, prevent overwriting new 
				}
				rs.closeRecordStore();
			} catch (Exception e) {
			}
		}
		
		try {
			// upgrade suspended games:
			for (int i = 0; i < suspendName10.length; ) {
				if (upgradeSuspendedGame(i)) {
					// don't update i, since the old entry has been removed
				} else {
					log("Could not upgrade " + suspendName10[i]);
					i++;
				}
			}
		} catch (Exception e) {
			fatalError = true;
			showError(literal[50], null, e);
			return false;
		}
		return true;
	}
	
	private boolean upgradeSuspendedGame(int index) throws RecordStoreException {
		boolean suspendName10Shrunk = false;
		String suspendName = suspendName10[index];
		RecordStore rs = RecordStore.openRecordStore("s" + suspendName, false);
		byte[] oldStyle = rs.getRecord(1);

		StringBuffer sb = new StringBuffer();
		int j = 0;
		while (oldStyle[j] != 0) {
			sb.append((char) oldStyle[j]);
			j++;
		}

		String cartFileNameTemp = sb.toString();

		// see if we can find the corresponding cart filename
		for (int k = 0; k < numCarts; k++) {
			if (cartFileNameTemp.equals(cartFileName[k])) {
				// remove suspended game from 1.0-style index:
				String[] oldNameIndex = suspendName10;
				suspendName10 = new String[oldNameIndex.length - 1];
				System.arraycopy(oldNameIndex, 0, suspendName10, 0, index);
				System.arraycopy(oldNameIndex, index + 1, suspendName10, index,
						suspendName10.length - index);
				suspendName10Shrunk = true;
				GBCanvas.writeSettings();

				// delete the old suspended game
				rs.deleteRecord(1);
				rs.closeRecordStore();
				rs = null;

				// write the new suspended game
				RecordStore rs2 = RecordStore.openRecordStore("20S_" + suspendName, true);

				boolean addToIndex = false;
				if (rs2.getNumRecords() == 0) {
					// copy oldstyle to newstyle savegame buffers
					byte[] cartIDBuffer = cartID[k].getBytes();

					boolean gbcFeatures;
					int gbSizeModBank = cartFileNameTemp.length() + 629;
					if ((oldStyle.length - gbSizeModBank) % 0x2000 == 132) {
						gbcFeatures = true;
					} else if ((oldStyle.length - gbSizeModBank) % 0x2000 == 0) {
						gbcFeatures = false;
					} else {
						rs2.closeRecordStore();
						throw new RuntimeException("1 " + oldStyle.length + " " + gbSizeModBank);
					}

					int sizeDiff = 107 + cartFileNameTemp.length() + (gbcFeatures ? 1 : 0);
					byte[] newStyle = new byte[oldStyle.length - sizeDiff];

					int newIndex = 0;
					int oldIndex = cartFileNameTemp.length() + 1;
					newStyle[newIndex++] = 1; // version
					newStyle[newIndex++] = (byte) (gbcFeatures ? 1 : 0);

					// registers + 3 interrupt times
					System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, 24);
					newIndex += 24;
					oldIndex += 24;

					// nextTimaOverflow, nextInterruptEnabled
					int nextTimaOverflow = GBCanvas.getInt(oldStyle, oldIndex-4);
					int nextInterruptEnable = GBCanvas.getInt(oldStyle, oldIndex);
					oldIndex += 4;

					// nextTimedInterrupt, version
					System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, 4);
					newIndex += 4;
					oldIndex += 5;

					newStyle[newIndex++] = (byte) ((nextTimaOverflow == 0x7fffffff) ? 1 : 0);
					newStyle[newIndex++] = 0; // graphicsChipMode
					newStyle[newIndex++] = oldStyle[oldIndex++]; // interruptsEnabled
					newStyle[newIndex++] = oldStyle[oldIndex++]; // interruptsArmed
					newStyle[newIndex++] = (byte) ((nextInterruptEnable == 0x7fffffff) ? 1 : 0);

					// mainRam + oam (0x100 bytes -> 0xa0)
					int mainRamLength = gbcFeatures ? 0x8000 : 0x2000;
					System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, mainRamLength + 0xa0);
					newIndex += mainRamLength + 0xa0;
					oldIndex += mainRamLength + 0x100;

					// registers, divReset, instrsPerTima, (cartType)
					System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, 0x108);
					newIndex += 0x108;
					oldIndex += 0x108 + 4;

					if (gbcFeatures) {
						int untilNextSkip = newStyle.length - newIndex - 131;
						System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, untilNextSkip);
						newIndex += untilNextSkip;
						oldIndex += untilNextSkip + 7;
						
						System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, 131);
						newIndex += 131;
						oldIndex += 131;
					} else {
						int untilNextSkip = newStyle.length - newIndex;
						System.arraycopy(oldStyle, oldIndex, newStyle, newIndex, untilNextSkip);
						newIndex += untilNextSkip;
						oldIndex += untilNextSkip + 6;
					}

					if (oldIndex != oldStyle.length) {
						rs2.closeRecordStore();
						throw new RuntimeException(suspendName + " " + oldIndex + "/" + oldStyle.length);
					}

					rs2.addRecord(cartIDBuffer, 0, cartIDBuffer.length);
					rs2.addRecord(newStyle, 0, newStyle.length);
					addToIndex = true;
				} else {
					// Don't overwrite new-style suspended game, and don't add to index
				}
				rs2.closeRecordStore();

				if (addToIndex) {
					// update 2.0-style index:
					addSuspendedGame(suspendName);
				}
				break; // don't keep looking for filename match
			}
		}
		if (rs != null)
			rs.closeRecordStore();
		return suspendName10Shrunk;
	}

	public void pauseApp() {
		if (gbCanvas != null) {
			gbCanvas.pause();
		}
	}

	public void destroyApp(boolean unconditional) {
	}

	public void unloadCart() {
		showMainMenu();
		gbCanvas.releaseReferences();
		gbCanvas = null;
	}
	
	private static String formatDetails(String details, Throwable exception) {
		if (debug && exception != null) {
			exception.printStackTrace();
		}
		
		if (exception != null) {
			if (details == null || details.length() == 0)
				details = exception.toString();
			else
				details += ", " + exception;
		}
		if (details != null && details.length() > 0)
			return " (" + details + ")";
		return "";
	}
	
	public static void showError(String message, String details, Throwable exception) {
		if (message == null)
			message = literal[47];
		instance.showMessage(literal[46], message + formatDetails(details, exception));
		if (instance.gbCanvas != null)
			instance.gbCanvas.releaseReferences();
		instance.gbCanvas = null;
	}

	public static void showLog() {
		instance.showMessage(literal[28], logString);
	}
	
	public void showMessage(String title, String message) {
		messageForm = new Form(title);
		messageForm.append(message);
		messageForm.setCommandListener(this);
		messageForm.addCommand(new Command(literal[10], Command.BACK, 0));
		display.setCurrent(messageForm);
	}
	
	private void messageCommand() {
		if (fatalError) {
			destroyApp(true);
			notifyDestroyed();
		} else {
			messageForm = null;
			showMainMenu();
		}
	}

	private void showMainMenu() {
		mainMenu = new List("MeBoy 2.2", List.IMPLICIT);
		mainMenu.append(literal[0], null);
		if (suspendName20.length > 0) {
			mainMenu.append(literal[1], null);
		}
		mainMenu.append(literal[2], null);
		if (bluetoothAvailable) {
			mainMenu.append(literal[4], null);
		}
		mainMenu.append(literal[5], null);
		if (showLogItem) {
			mainMenu.append(literal[3], null);
		}
		mainMenu.append(literal[6], null);
		mainMenu.setCommandListener(this);
		display.setCurrent(mainMenu);
	}

	private void mainMenuCommand(Command com) {
		String item = mainMenu.getString(mainMenu.getSelectedIndex());
		if (item == literal[0]) {
			showCartList();
		} else if (item == literal[1]) {
			showResumeGame();
		} else if (item == literal[2]) {
			showSettings();
		} else if (item == literal[4]) {
			bluetooth = new Bluetooth(this);
		} else if (item == literal[5]) {
			showMessage(literal[5], "MeBoy 2.2 © Björn Carlin, 2005-2009.\nhttp://arktos.se/meboy/");
		} else if (item == literal[3]) {
			log(literal[29] + " " + Runtime.getRuntime().freeMemory() + "/" + Runtime.getRuntime().totalMemory());
			showLog();
		} else if (item == literal[6]) {
			destroyApp(true);
			notifyDestroyed();
		} else {
			showError(null, "Unknown command: " + com.getLabel(), null);
		}
	}

	private void showCartList() {
		cartList = new List(literal[7], List.IMPLICIT);
		
		for (int i = 0; i < numCarts; i++)
			cartList.append(cartDisplayName[i], null);

		cartList.addCommand(new Command(literal[10], Command.BACK, 1));
		cartList.setCommandListener(this);
		display.setCurrent(cartList);
	}

	private void cartListCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			cartList = null;
			showMainMenu();
			return;
		}
		
		int ix = cartList.getSelectedIndex();
		String selectedCartID = cartID[ix];
		String selectedCartDisplayName = cartDisplayName[ix];
		try {
			gbCanvas = new GBCanvas(selectedCartID, this, selectedCartDisplayName);
			cartList = null;
			display.setCurrent(gbCanvas);
		} catch (Exception e) {
			showError(null, "error#1", e);
		}
	}

	private void showResumeGame() {
		if (suspendName20.length == 0) {
			showMainMenu();
			return;
		}
		suspendList = new List(literal[7], List.IMPLICIT);

		for (int i = 0; i < suspendName20.length; i++) {
			suspendList.append(suspendName20[i], null);
		}
		suspendList.addCommand(new Command(literal[8], Command.SCREEN, 2));
		suspendList.addCommand(new Command(literal[9], Command.SCREEN, 2));
		suspendList.addCommand(new Command(literal[10], Command.BACK, 1));
		suspendList.setCommandListener(this);
		display.setCurrent(suspendList);
	}

	private void resumeGameCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			suspendList = null;
			showMainMenu();
			return;
		}
		
		String label = com.getLabel();
		int index = suspendList.getSelectedIndex();
		String selectedName = suspendName20[index];
		
		if (label == literal[8]) {
			try {
				// update index:
				String[] oldIndex = suspendName20;
				suspendName20 = new String[oldIndex.length - 1];
				System.arraycopy(oldIndex, 0, suspendName20, 0, index);
				System.arraycopy(oldIndex, index + 1, suspendName20, index,
						suspendName20.length - index);
				GBCanvas.writeSettings();

				// delete the state itself
				RecordStore.deleteRecordStore("20S_" + selectedName);
			} catch (Exception e) {
				showError(null, "error#2", e);
			}
			showResumeGame();
		} else if (label == literal[9]) {
			try {
				String oldName = selectedName;
				String newName = suspendCounter++ + oldName.substring(oldName.indexOf(':'));

				RecordStore rs = RecordStore.openRecordStore("20S_" + oldName,
						true);
				byte[] b1 = rs.getRecord(1); // cartid
				byte[] b2 = rs.getRecord(2); // data
				rs.closeRecordStore();

				rs = RecordStore.openRecordStore("20S_" + newName, true);
				rs.addRecord(b1, 0, b1.length);
				rs.addRecord(b2, 0, b2.length);
				rs.closeRecordStore();

				addSuspendedGame(newName);
				showResumeGame();
			} catch (Exception e) {
				showError(null, "error#3", e);
			}
		} else {
			try {
				String suspendName = selectedName;
				RecordStore rs = RecordStore.openRecordStore("20S_" + suspendName, false);
				String suspendCartID = new String(rs.getRecord(1));
				byte[] suspendState = rs.getRecord(2);
				rs.closeRecordStore();
				String suspendCartDisplayName = null;
				for (int i = 0; i < numCarts; i++)
					if (suspendCartID.equals(cartID[i]))
						suspendCartDisplayName = cartDisplayName[i];
				if (suspendCartDisplayName == null) {
					showError(literal[11], suspendCartID, null);
				} else {
					gbCanvas = new GBCanvas(suspendCartID, this, suspendCartDisplayName,
										suspendName, suspendState);
					display.setCurrent(gbCanvas);
				}
				suspendList = null;
			} catch (Exception e) {
				showError(null, "error#4", e);
			}
		}
	}

	private void showSettings() {
		settingsForm = new Form(literal[2]);

		frameSkipField = new TextField(literal[12], "" + maxFrameSkip, 3, TextField.NUMERIC);
		settingsForm.append(frameSkipField);
		rotationField = new TextField(literal[13], "" + rotations, 2, TextField.NUMERIC);
		settingsForm.append(rotationField);

		graphicsGroup = new ChoiceGroup(literal[14], ChoiceGroup.MULTIPLE,
				new String[]{literal[15], literal[16], literal[17]}, null);
		graphicsGroup.setSelectedIndex(0, enableScaling);
		graphicsGroup.setSelectedIndex(1, keepProportions);
		graphicsGroup.setSelectedIndex(2, advancedGraphics);
		settingsForm.append(graphicsGroup);
		
		scalingModeField = new TextField(literal[18], Integer.toString(scalingMode), 2, TextField.NUMERIC);
		settingsForm.append(scalingModeField);

		soundGroup = new ChoiceGroup(literal[19],
				ChoiceGroup.MULTIPLE, new String[]{literal[20], literal[21]},
				null);
		soundGroup.setSelectedIndex(0, enableSound);
		soundGroup.setSelectedIndex(1, advancedSound);
		settingsForm.append(soundGroup);

		languageGroup = new ChoiceGroup(literal[22], ChoiceGroup.EXCLUSIVE,
				languages, null);
		for (int i = 0; i < languages.length; i++)
			languageGroup.setSelectedIndex(i, language == languageLookup[i]);
		settingsForm.append(languageGroup);
		
		miscSettingsGroup = new ChoiceGroup(literal[23],
				ChoiceGroup.MULTIPLE, new String[]{literal[24], literal[25]},
				null);
		miscSettingsGroup.setSelectedIndex(0, disableColor);
		miscSettingsGroup.setSelectedIndex(1, showLogItem);
		settingsForm.append(miscSettingsGroup);
		
		loadThresholdField = new TextField(literal[26], "" + lazyLoadingThreshold * 16, 5, TextField.NUMERIC);
		settingsForm.append(loadThresholdField);

		settingsForm.addCommand(new Command(literal[10], Command.BACK, 0));
		settingsForm.addCommand(new Command(literal[27], Command.OK, 1));
		settingsForm.setCommandListener(this);
		display.setCurrent(settingsForm);
	}

	private void settingsCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			settingsForm = null;
			showMainMenu();
			return;
		}
		
		int f = Integer.parseInt(frameSkipField.getString());
		maxFrameSkip = Math.max(Math.min(f, 59), 0);
		rotations = Integer.parseInt(rotationField.getString()) & 3;
		lazyLoadingThreshold = Math.max(Integer.parseInt(loadThresholdField.getString()) / 16, 20);
		enableScaling = graphicsGroup.isSelected(0);
		keepProportions = graphicsGroup.isSelected(1);
		advancedGraphics = graphicsGroup.isSelected(2);
		f = Integer.parseInt(scalingModeField.getString());
		scalingMode = Math.max(Math.min(f, 3), 0);
		disableColor = miscSettingsGroup.isSelected(0);
		showLogItem = miscSettingsGroup.isSelected(1);
		enableSound = soundGroup.isSelected(0);
		advancedSound = soundGroup.isSelected(1);

		int oldLanguage = language;
		language = languageLookup[languageGroup.getSelectedIndex()];
		
		GBCanvas.writeSettings();
		if (oldLanguage != language) {
			readLiteralsFile();
			cartList = null;
		}
		settingsForm = null;
		showMainMenu();
	}
	
	public void addSavegamesToList(List list, Vector cartIDs, Vector filenames) {
		for (int i = 0; i < numCarts; i++) {
			try {
				RecordStore rs = RecordStore.openRecordStore("20R_" + cartID[i], true);

				if (rs.getNumRecords() > 0) {
					list.append(cartDisplayName[i], null);
					cartIDs.addElement(cartID[i]);
					filenames.addElement(cartFileName[i]);
				}
				rs.closeRecordStore();
			} catch (Exception e) {
				if (MeBoy.debug) {
					e.printStackTrace();
				}
				MeBoy.log(e.toString());
			}
		}
	}
	
	private boolean tearDownBluetooth() {
		if (bluetooth != null) {
			bluetooth.tearDown();
			bluetooth = null;
			return true;
		}
		return false;
	}
	
	// Cancel without message
	public synchronized void cancelBluetooth() {
		if (tearDownBluetooth())
			showMainMenu();
	}
	
	// Cancel with custom message
	public synchronized void cancelBluetooth(String message) {
		if (tearDownBluetooth())
			showMessage(literal[4], message);
	}
	
	// Cancel with generic message, log exception
	public synchronized void cancelBluetooth(Exception e) {
		if (!tearDownBluetooth())
			return;
		
		log("BT: " + e);
		if (debug) {
			e.printStackTrace();
			showLog();
		} else {
			showMessage(literal[4], literal[59]);
		}
	}
	
	// Successfully finished bluetooth operation
	public synchronized void finishBluetooth() {
		if (tearDownBluetooth())
			showMessage(literal[4], literal[58]);
	}

	public synchronized void finishBluetoothReceive(String mode, String gameDisplayName,
			String gameFileName, String gameCartID, byte[] savegame) {
		tearDownBluetooth();
		if (mode.equals("r")) {
			// cart ram
			
			if (gameCartID.length() == 0) {
				int ix = -1;
				if (ix == -1 && gameFileName.length() > 0)
					ix = findMatch(gameFileName, cartFileName, true);
				if (ix == -1 && gameDisplayName.length() > 0)
					ix = findMatch(gameDisplayName, cartDisplayName, true);
				if (ix > -1)
					gameCartID = cartID[ix];
			}
			
			if (gameCartID.length() > 0 && findMatch(gameCartID, cartID, false) > -1) {
				addGameRAM(gameCartID, savegame);
				showMessage(literal[4], literal[58]);
			} else {
				String cart = "";
				if (gameDisplayName.length() > 0)
					cart = gameDisplayName;
				else if (gameFileName.length() > 0)
					cart = gameFileName;
				else if (gameCartID.length() > 0)
					cart = gameCartID;
				showMessage(literal[4], literal[63] + formatDetails(cart, null));
			}
		} else {
			// suspended game
			
			if (gameCartID.length() > 0 && findMatch(gameCartID, cartID, false) > -1) {
				try {
					String suspendName = (MeBoy.suspendCounter++) + ": " + gameDisplayName;
					
					RecordStore rs = RecordStore.openRecordStore("20S_" + suspendName, true);

					if (rs.getNumRecords() == 0) {
						rs.addRecord(gameCartID.getBytes(), 0, gameCartID.length());
						rs.addRecord(savegame, 0, savegame.length);
					} else {
						rs.setRecord(1, gameCartID.getBytes(), 0, gameCartID.length());
						rs.setRecord(2, savegame, 0, savegame.length);
					}

					rs.closeRecordStore();
					addSuspendedGame(suspendName);
					showMessage(literal[4], literal[58]);
				} catch (Exception e) {
					if (debug)
						e.printStackTrace();
					showMessage(literal[4], literal[64] + formatDetails(gameCartID, e));
				}
			} else {
				showMessage(literal[4], literal[63] + formatDetails(gameCartID, null));
			}
		}
	}
	
	public void commandAction(Command com, Displayable s) {
		if (s == messageForm)
			messageCommand();
		else if (s == mainMenu)
			mainMenuCommand(com);
		else if (s == cartList)
			cartListCommand(com);
		else if (s == suspendList)
			resumeGameCommand(com);
		else if (s == settingsForm)
			settingsCommand(com);
	}

	public static void log(String s) {
		if (s == null) {
			return;
		}
		logString = logString + s + '\n';
		if (debug) {
			System.out.println(s);
		}
	}
	
	public static void addGameRAM(String name, byte[] data) {
		try {
			RecordStore rs = RecordStore.openRecordStore("20R_" + name, true);
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(data, 0, data.length);
			} else {
				rs.setRecord(1, data, 0, data.length);
			}
			
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}

	// name should include number prefix
	public static void addSuspendedGame(String name) {
		String[] oldIndex = suspendName20;
		suspendName20 = new String[oldIndex.length + 1];
		System.arraycopy(oldIndex, 0, suspendName20, 0, oldIndex.length);
		suspendName20[oldIndex.length] = name;
		GBCanvas.writeSettings();
	}
	
	private int findMatch(String match, String[] list, boolean fuzzy) {
		// exact match
		for (int i = 0; i < list.length; i++)
			if (list[i].equals(match))
				return i;
		if (!fuzzy)
			return -1;
		// prefix match, sans extension
		int lastdot = match.lastIndexOf('.');
		if (lastdot != -1 && lastdot > match.length() - 5)
			match = match.substring(0, lastdot);
		for (int i = 0; i < list.length; i++)
			if (list[i].startsWith(match))
				return i;
		return -1;
	}
}
