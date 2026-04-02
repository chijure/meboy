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
import meboy.app.LanguageResources;
import meboy.io.CartCatalog;
import meboy.io.ExternalRomStore;
import meboy.io.FileBrowserUtil;
import meboy.io.SuspendedGameStore;
import meboy.ui.CartListController;
import meboy.ui.DeviceInfoController;
import meboy.ui.FileBrowserController;
import meboy.ui.MainMenuController;
import meboy.ui.ResumeGameController;
import meboy.ui.SettingsController;
import meboy.util.StringArrayUtil;

/**
 * The main class offers a list of games, read from the file "carts.txt". It
 * also handles logging.
 */
public class MeBoy extends MIDlet implements CommandListener, ResumeGameController.Host, SettingsController.Host, DeviceInfoController.Host, FileBrowserController.Host, MainMenuController.Host, CartListController.Host {
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
	private static String[] languages = new String[] {"English"}; // if index.txt fails
	private static int[] languageLookup = new int[] {0};
	private CartCatalog cartCatalog = CartCatalog.empty();
	private boolean fatalError;
	private boolean fileSystemAvailable = false;
	
	public static String logString = "";
	private static MeBoy instance;
	
	// UI components
	public static Display display;
	private Form messageForm;
	private GBCanvas gbCanvas;
	private MainMenuController mainMenuController;
	private CartListController cartListController;
	private ResumeGameController resumeGameController;
	private SettingsController settingsController;
	private DeviceInfoController deviceInfoController;
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
		mainMenuController = new MainMenuController(this);
		cartListController = new CartListController(this);
		resumeGameController = new ResumeGameController(this);
		settingsController = new SettingsController(this);
		deviceInfoController = new DeviceInfoController(this);
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
		if (!LanguageResources.loadLiterals(getClass(), language, literal)) {
			if (language > 0) {
				log("Language literals: failed for language id " + language + ", falling back to 0");
				language = 0;
				return readLiteralsFile();
			}
			
			showError("Failed to read the language file.", null, null);
			fatalError = true;
			return false;
		}
		return true;
	}

	private void readLangIndexFile() {
		LanguageResources.LanguageIndex languageIndex = LanguageResources.loadIndex(getClass());
		languages = languageIndex.names;
		languageLookup = languageIndex.lookup;
		log("Language index: current language id=" + language);
	}

	static int detectLanguageFromLocale() {
		return LanguageResources.detectLanguageFromLocale();
	}
	
	private boolean readCartNames() {
		cartCatalog = CartCatalog.load(getClass());
		return true;
	}
	
	public boolean upgradeSavegames() {
		// upgrade cart ram:
		for (int i = 0; i < cartCatalog.size(); i++) {
			String oldSaveName = cartCatalog.getFileNameAt(i);
			String newSaveName = cartCatalog.getIdAt(i);
			
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
		for (int k = 0; k < cartCatalog.size(); k++) {
			if (cartFileNameTemp.equals(cartCatalog.getFileNameAt(k))) {
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
					byte[] cartIDBuffer = cartCatalog.getIdAt(k).getBytes();

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
		mainMenuController.show();
	}

	public void showCartList() {
		cartListController.show();
	}

	public void showResumeGame() {
		resumeGameController.show();
	}

	public void showSettings() {
		settingsController.show();
	}

	public void showDeviceInfo() {
		deviceInfoController.show();
	}
	
	public void addSavegamesToList(javax.microedition.lcdui.List list, Vector cartIDs, Vector filenames) {
		cartCatalog.addSavegamesToList(list, cartIDs, filenames);
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
					ix = cartCatalog.findMatchByFileName(gameFileName, true);
				if (ix == -1 && gameDisplayName.length() > 0)
					ix = cartCatalog.findMatchByDisplayName(gameDisplayName, true);
				if (ix > -1)
					gameCartID = cartCatalog.getIdAt(ix);
			}
			
			if (gameCartID.length() > 0 && cartCatalog.indexOfId(gameCartID) > -1) {
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
			
			if (gameCartID.length() > 0 && cartCatalog.indexOfId(gameCartID) > -1) {
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
		else if (mainMenuController.handles(s))
			mainMenuController.commandAction(com, s);
		else if (cartListController.handles(s))
			cartListController.commandAction(com, s);
		else if (resumeGameController.handles(s))
			resumeGameController.commandAction(com, s);
		else if (settingsController.handles(s))
			settingsController.commandAction(com, s);
		else if (deviceInfoController.handles(s))
			deviceInfoController.commandAction(com, s);
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
		return cartCatalog.findDisplayNameById(suspendCartID);
	}

	public void openSuspendedGame(String suspendCartID, String suspendCartDisplayName, String suspendName, byte[] suspendState) {
		gbCanvas = new GBCanvas(suspendCartID, this, suspendCartDisplayName, suspendName, suspendState);
		display.setCurrent(gbCanvas);
	}

	public int nextSuspendCounter() {
		return suspendCounter++;
	}

	public boolean hasBundledCarts() {
		return !cartCatalog.isEmpty();
	}

	public boolean isFileSystemAvailable() {
		return fileSystemAvailable;
	}

	public boolean hasSuspendedGames() {
		return suspendName20.length > 0;
	}

	public boolean isBluetoothAvailable() {
		return bluetoothAvailable;
	}

	public boolean isShowLogItemEnabled() {
		return showLogItem;
	}

	public void showFileBrowserRoot() {
		fileBrowserController.showRoot();
	}

	public void startBluetooth() {
		bluetooth = new Bluetooth(this);
	}

	public void showAbout() {
		showMessage(literal[5], AppInfo.ABOUT_MESSAGE);
	}

	public void showLogMemory() {
		log(literal[29] + " " + Runtime.getRuntime().freeMemory() + "/" + Runtime.getRuntime().totalMemory());
		showLog();
	}

	public void exitApplication() {
		destroyApp(true);
		notifyDestroyed();
	}

	public void showUnknownCommandError(String label) {
		showError(null, "Unknown command: " + label, null);
	}

	public String[] getCartDisplayNames() {
		return cartCatalog.getDisplayNames();
	}

	public String getCartIDAt(int index) {
		return cartCatalog.getIdAt(index);
	}

	public String getCartDisplayNameAt(int index) {
		return cartCatalog.getDisplayNameAt(index);
	}

	public void openCart(String selectedCartID, String selectedCartDisplayName) {
		gbCanvas = new GBCanvas(selectedCartID, this, selectedCartDisplayName);
		display.setCurrent(gbCanvas);
	}

	public void showNoBundledRomsMessage() {
		showMessage("MeBoy", AppInfo.NO_BUNDLED_ROMS_MESSAGE + AppInfo.LOAD_SD_LABEL + "\" from the main menu.");
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

	public static String getExternalRomFile(String cartName) {
		return ExternalRomStore.getRomFile(cartName);
	}
	
}
