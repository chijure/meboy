package meboy.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import meboy.app.AppInfo;

public final class MainMenuController implements CommandListener {
	public interface Host {
		Display getDisplay();
		String getLiteral(int index);
		boolean hasBundledCarts();
		boolean isFileSystemAvailable();
		boolean hasSuspendedGames();
		boolean isBluetoothAvailable();
		boolean isShowLogItemEnabled();
		void showCartList();
		void showResumeGame();
		void showSettings();
		void showFileBrowserRoot();
		void startBluetooth();
		void showAbout();
		void showLogMemory();
		void exitApplication();
		void showUnknownCommandError(String label);
	}

	private final Host host;
	private List mainMenu;

	public MainMenuController(Host host) {
		this.host = host;
	}

	public void show() {
		mainMenu = new List(AppInfo.APP_TITLE, List.IMPLICIT);
		if (host.hasBundledCarts()) {
			mainMenu.append(host.getLiteral(0), null);
		}
		if (host.isFileSystemAvailable()) {
			mainMenu.append(AppInfo.LOAD_SD_LABEL, null);
		}
		if (host.hasSuspendedGames()) {
			mainMenu.append(host.getLiteral(1), null);
		}
		mainMenu.append(host.getLiteral(2), null);
		if (host.isBluetoothAvailable()) {
			mainMenu.append(host.getLiteral(4), null);
		}
		mainMenu.append(host.getLiteral(5), null);
		if (host.isShowLogItemEnabled()) {
			mainMenu.append(host.getLiteral(3), null);
		}
		mainMenu.append(host.getLiteral(6), null);
		mainMenu.setCommandListener(this);
		host.getDisplay().setCurrent(mainMenu);
	}

	public boolean handles(Displayable displayable) {
		return displayable == mainMenu;
	}

	public void commandAction(Command com, Displayable s) {
		String item = mainMenu.getString(mainMenu.getSelectedIndex());
		if (host.getLiteral(0).equals(item)) {
			host.showCartList();
		} else if (host.getLiteral(1).equals(item)) {
			host.showResumeGame();
		} else if (host.getLiteral(2).equals(item)) {
			host.showSettings();
		} else if (AppInfo.LOAD_SD_LABEL.equals(item)) {
			host.showFileBrowserRoot();
		} else if (host.getLiteral(4).equals(item)) {
			host.startBluetooth();
		} else if (host.getLiteral(5).equals(item)) {
			host.showAbout();
		} else if (host.getLiteral(3).equals(item)) {
			host.showLogMemory();
		} else if (host.getLiteral(6).equals(item)) {
			host.exitApplication();
		} else {
			host.showUnknownCommandError(com.getLabel());
		}
	}
}
