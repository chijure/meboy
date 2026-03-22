package meboy;

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
import javax.microedition.io.*;
import javax.microedition.io.file.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;
import meboy.app.AppInfo;
import meboy.io.ExternalRomStore;
import meboy.io.FileBrowserUtil;
import meboy.io.SuspendedGameStore;
import meboy.ui.FileBrowserController;
import meboy.ui.ResumeGameController;
import meboy.ui.SettingsController;
import meboy.util.StringArrayUtil;

/**
 * The main class offers a list of games, read from the file "carts.txt". It
 * also handles logging.
 */
public class MeBoy extends MIDlet implements CommandListener, ResumeGameController.Host, SettingsController.Host, FileBrowserController.Host {
	// Settings, etc.
	public static final boolean debug = true;
	public static int rotations = 0;
	public static int maxFrameSkip = 7;
	public static boolean enableScaling = false;
	public static int scalingMode = 0;
	public static boolean keepProportions = false;
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
	private boolean fileSystemAvailable = false;
	
	public static String logString = "";
	private static MeBoy instance;
	
	// UI components
	public static Display display;
	private javax.microedition.lcdui.List mainMenu;
	private Form messageForm;
	private GBCanvas gbCanvas;
	private javax.microedition.lcdui.List cartList;
	private ResumeGameController resumeGameController;
	private SettingsController settingsController;
	private FileBrowserController fileBrowserController;
	
	private Bluetooth bluetooth;
	
	
	public void startApp() {
		if (instance == this) {
			return;
		}
		
		try {
			bluetoothAvailable = Bluetooth.available();
		} catch (NoClassDefFoundError t) {
		}
		try {
			fileSystemAvailable = FileSystemRegistry.listRoots() != null;
		} catch (Throwable t) {
			fileSystemAvailable = false;
		}
		
		instance = this;
		display = Display.getDisplay(this);
		resumeGameController = new ResumeGameController(this);
		settingsController = new SettingsController(this);
		fileBrowserController = new FileBrowserController(this);

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
			log("Language literals: loading /lang/" + language + ".txt");
			InputStreamReader isr = openLangReader(language + ".txt");
			int counter = 0;
			String s;
			while ((s = next(isr)) != null)
				literal[counter++] = s;
			isr.close();
			while (counter < literal.length)
				literal[counter++] = "?";
			log("Language literals: loaded " + counter + " entries for language id " + language);
		} catch (Exception e) {
			e.printStackTrace();
			if (language > 0) {
				log("Language literals: failed for language id " + language + ", falling back to 0");
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
			InputStreamReader isr = openLangReader("index.txt");
			
			String code;
			Vector langVector = new Vector();
			Vector lookupVector = new Vector();
			languageCount = 0;
			while ((code = next(isr)) != null) {
				String langName = next(isr);
				if (code.length() == 0 || langName == null) {
					continue;
				}
				lookupVector.addElement(new Integer(code.charAt(0) - 'a'));
				langVector.addElement(langName);
				languageCount++;
			}
			languages = new String[languageCount];
			langVector.copyInto(languages);
			languageLookup = new int[languageCount];
			for (int i = 0; i < languageCount; i++) {
				languageLookup[i] = ((Integer) lookupVector.elementAt(i)).intValue();
			}
			log("Language index: loaded " + languageCount + " languages, current language id=" + language);
			
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

	static int detectLanguageFromLocale() {
		String[] localeProps = new String[] {
			"microedition.locale",
			"user.language",
			"user.locale"
		};
		for (int i = 0; i < localeProps.length; i++) {
			String locale = readSystemProperty(localeProps[i]);
			log("Language detect: " + localeProps[i] + "=" + (locale == null ? "<null>" : locale));
			int detected = mapLocaleToLanguage(locale);
			if (detected >= 0) {
				log("Language detect: matched locale from " + localeProps[i] + " -> language id " + detected);
				return detected;
			}
		}
		log("Language detect: no locale match, defaulting to language id 0");
		return 0;
	}

	private static String readSystemProperty(String key) {
		try {
			String value = System.getProperty(key);
			if (value != null && value.length() > 0) {
				return value.toLowerCase();
			}
		} catch (Throwable t) {
		}
		return null;
	}

	private static boolean hasLanguagePrefix(String locale, String prefix) {
		if (locale == null || prefix == null || !locale.startsWith(prefix)) {
			return false;
		}
		if (locale.length() == prefix.length()) {
			return true;
		}
		char separator = locale.charAt(prefix.length());
		return separator == '_' || separator == '-' || separator == '.' || separator == '@';
	}

	private static int mapLocaleToLanguage(String locale) {
		if (locale == null || locale.length() == 0) {
			return -1;
		}
		// Some emulators report names like "Spanish_Peru.1252" instead of ISO codes.
		if (hasLanguagePrefix(locale, "es") || locale.indexOf("spanish") >= 0 || locale.indexOf("espan") >= 0
				|| locale.indexOf("castell") >= 0) {
			return 1; // Espanol
		}
		if (hasLanguagePrefix(locale, "el") || locale.indexOf("greek") >= 0) {
			return 2;
		}
		if (hasLanguagePrefix(locale, "pt") || locale.indexOf("portugu") >= 0) {
			return 3;
		}
		if (hasLanguagePrefix(locale, "zh") || locale.indexOf("chinese") >= 0) {
			return 4;
		}
		if (hasLanguagePrefix(locale, "pl") || locale.indexOf("polish") >= 0) {
			return 5;
		}
		if (hasLanguagePrefix(locale, "de") || locale.indexOf("german") >= 0 || locale.indexOf("deutsch") >= 0) {
			return 6;
		}
		if (hasLanguagePrefix(locale, "nl") || locale.indexOf("dutch") >= 0 || locale.indexOf("neder") >= 0) {
			return 7;
		}
		if (hasLanguagePrefix(locale, "id") || hasLanguagePrefix(locale, "in") || locale.indexOf("indones") >= 0) {
			return 8;
		}
		if (hasLanguagePrefix(locale, "fr") || locale.indexOf("french") >= 0 || locale.indexOf("franc") >= 0) {
			return 9;
		}
		if (hasLanguagePrefix(locale, "it") || locale.indexOf("ital") >= 0) {
			return 10;
		}
		if (hasLanguagePrefix(locale, "cs") || hasLanguagePrefix(locale, "cz") || locale.indexOf("czech") >= 0
				|| locale.indexOf("cesk") >= 0) {
			return 11;
		}
		if (hasLanguagePrefix(locale, "ru") || locale.indexOf("russian") >= 0) {
			return 12;
		}
		if (hasLanguagePrefix(locale, "hr") || locale.indexOf("croatian") >= 0 || locale.indexOf("hrvat") >= 0) {
			return 13;
		}
		if (hasLanguagePrefix(locale, "sr") || locale.indexOf("serbian") >= 0 || locale.indexOf("srps") >= 0) {
			return 14;
		}
		if (hasLanguagePrefix(locale, "uk") || locale.indexOf("ukrain") >= 0) {
			return 15;
		}
		if (hasLanguagePrefix(locale, "lt") || locale.indexOf("lithuan") >= 0 || locale.indexOf("lietuv") >= 0) {
			return 16;
		}
		if (hasLanguagePrefix(locale, "lv") || locale.indexOf("latv") >= 0) {
			return 17;
		}
		if (hasLanguagePrefix(locale, "hu") || locale.indexOf("hungar") >= 0 || locale.indexOf("magyar") >= 0) {
			return 18;
		}
		if (hasLanguagePrefix(locale, "gsw") || locale.indexOf("swiss") >= 0 || locale.indexOf("schwizer") >= 0) {
			return 19;
		}
		if (hasLanguagePrefix(locale, "da") || locale.indexOf("danish") >= 0) {
			return 20;
		}
		if (hasLanguagePrefix(locale, "tr") || locale.indexOf("turkish") >= 0 || locale.indexOf("turk") >= 0) {
			return 21;
		}
		if (hasLanguagePrefix(locale, "en") || locale.indexOf("english") >= 0) {
			return 0;
		}
		return -1;
	}

	private InputStreamReader openLangReader(String fileName) throws IOException {
		String[] resourcePaths = new String[] {
			"/lang/" + fileName,
			"../../lang/" + fileName,
			"lang/" + fileName
		};
		for (int i = 0; i < resourcePaths.length; i++) {
			InputStream stream = getClass().getResourceAsStream(resourcePaths[i]);
			if (stream != null) {
				return new InputStreamReader(stream, "UTF-8");
			}
		}
		throw new IOException("Language resource not found: " + fileName);
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
			numCarts = 0;
			cartDisplayName = new String[0];
			cartFileName = new String[0];
			cartID = new String[0];
			log("No bundled carts were found in the JAR.");
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
				suspendName10 = StringArrayUtil.removeAt(suspendName10, index);
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
			message = getLiteralOrDefault(47, "Error");
		instance.showMessage(getLiteralOrDefault(46, "Error"), message + formatDetails(details, exception));
		if (instance.gbCanvas != null)
			instance.gbCanvas.releaseReferences();
		instance.gbCanvas = null;
	}

	public static void showLog() {
		instance.showMessage(getLiteralOrDefault(28, "Log"), logString);
	}
	
	public void showMessage(String title, String message) {
		if (title == null || title.length() == 0) {
			title = "MeBoy";
		}
		if (message == null) {
			message = "";
		}
		messageForm = new Form(title);
		messageForm.append(message);
		messageForm.setCommandListener(this);
		messageForm.addCommand(new Command(getLiteralOrDefault(10, "Back"), Command.BACK, 0));
		display.setCurrent(messageForm);
	}

	private static String getLiteralOrDefault(int index, String fallback) {
		if (index >= 0 && index < literal.length && literal[index] != null && literal[index].length() > 0) {
			return literal[index];
		}
		return fallback;
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

	public void showMainMenu() {
		mainMenu = new javax.microedition.lcdui.List(AppInfo.APP_TITLE, javax.microedition.lcdui.List.IMPLICIT);
		if (numCarts > 0) {
			mainMenu.append(literal[0], null);
		}
		if (fileSystemAvailable) {
			mainMenu.append(AppInfo.LOAD_SD_LABEL, null);
		}
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
		if (literal[0].equals(item)) {
			showCartList();
		} else if (literal[1].equals(item)) {
			showResumeGame();
		} else if (literal[2].equals(item)) {
			showSettings();
		} else if (AppInfo.LOAD_SD_LABEL.equals(item)) {
			fileBrowserController.showRoot();
		} else if (literal[4].equals(item)) {
			bluetooth = new Bluetooth(this);
		} else if (literal[5].equals(item)) {
			showMessage(literal[5], AppInfo.ABOUT_MESSAGE);
		} else if (literal[3].equals(item)) {
			log(literal[29] + " " + Runtime.getRuntime().freeMemory() + "/" + Runtime.getRuntime().totalMemory());
			showLog();
		} else if (literal[6].equals(item)) {
			destroyApp(true);
			notifyDestroyed();
		} else {
			showError(null, "Unknown command: " + com.getLabel(), null);
		}
	}

	private void showCartList() {
		if (numCarts == 0) {
			showMessage("MeBoy", AppInfo.NO_BUNDLED_ROMS_MESSAGE + AppInfo.LOAD_SD_LABEL + "\" from the main menu.");
			return;
		}
		cartList = new javax.microedition.lcdui.List(literal[7], javax.microedition.lcdui.List.IMPLICIT);
		
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
		resumeGameController.show();
	}

	private void showSettings() {
		settingsController.show();
	}
	
	public void addSavegamesToList(javax.microedition.lcdui.List list, Vector cartIDs, Vector filenames) {
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
					
					SuspendedGameStore.save(suspendName, gameCartID, savegame);
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

	public void openExternalRom(String fileUrl) {
		try {
			String displayName = fileUrl;
			int lastSlash = fileUrl.lastIndexOf('/');
			if (lastSlash >= 0 && lastSlash + 1 < fileUrl.length()) {
				displayName = fileUrl.substring(lastSlash + 1);
			}

			String externalCartID = FileBrowserUtil.buildExternalCartID(fileUrl);
			registerExternalRomFile(externalCartID, fileUrl);
			showWaitForm(AppInfo.WAIT_LOADING_ROM_MESSAGE);

			gbCanvas = new GBCanvas(externalCartID, this, displayName);
			display.setCurrent(gbCanvas);
		} catch (OutOfMemoryError oom) {
			showError(AppInfo.ROM_LOAD_MEMORY_ERROR, fileUrl, oom);
		} catch (Exception e) {
			showError(AppInfo.ROM_LOAD_ERROR, fileUrl, e);
		}
	}

	public void showWaitForm(String message) {
		Form wait = new Form(AppInfo.WAIT_FORM_TITLE);
		wait.append(message);
		display.setCurrent(wait);
	}

	public void commandAction(Command com, Displayable s) {
		if (s == messageForm)
			messageCommand();
		else if (s == mainMenu)
			mainMenuCommand(com);
		else if (s == cartList)
			cartListCommand(com);
		else if (resumeGameController.handles(s))
			resumeGameController.commandAction(com, s);
		else if (settingsController.handles(s))
			settingsController.commandAction(com, s);
		else if (fileBrowserController.handles(s))
			fileBrowserController.commandAction(com, s);
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

	public Display getDisplay() {
		return display;
	}

	public String getLiteral(int index) {
		return literal[index];
	}

	public String[] getLanguages() {
		return languages;
	}

	public int[] getLanguageLookup() {
		return languageLookup;
	}

	public void reloadLiterals() {
		readLiteralsFile();
		cartList = null;
	}

	public void showErrorMessage(String message, String details, Throwable exception) {
		showError(message, details, exception);
	}

	public String[] getSuspendedNames() {
		return suspendName20;
	}

	public void removeSuspendedGameAt(int index) {
		suspendName20 = StringArrayUtil.removeAt(suspendName20, index);
		GBCanvas.writeSettings();
	}

	public void appendSuspendedGame(String name) {
		addSuspendedGame(name);
	}

	public String resolveCartDisplayName(String suspendCartID) {
		for (int i = 0; i < numCarts; i++) {
			if (suspendCartID.equals(cartID[i])) {
				return cartDisplayName[i];
			}
		}
		return null;
	}

	public void openSuspendedGame(String suspendCartID, String suspendCartDisplayName, String suspendName, byte[] suspendState) {
		gbCanvas = new GBCanvas(suspendCartID, this, suspendCartDisplayName, suspendName, suspendState);
		display.setCurrent(gbCanvas);
	}

	public int nextSuspendCounter() {
		return suspendCounter++;
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
		suspendName20 = StringArrayUtil.append(suspendName20, name);
		GBCanvas.writeSettings();
	}

	public static void registerExternalRom(String cartName, byte[] romData) {
		ExternalRomStore.registerRom(cartName, romData);
	}

	public static byte[] takeExternalRom(String cartName) {
		return ExternalRomStore.takeRom(cartName);
	}

	public static void registerExternalRomFile(String cartName, String fileUrl) {
		ExternalRomStore.registerRomFile(cartName, fileUrl);
	}

	public static String takeExternalRomFile(String cartName) {
		return ExternalRomStore.takeRomFile(cartName);
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
