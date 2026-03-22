package meboy.ui;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import meboy.app.AppInfo;
import meboy.io.FileBrowserUtil;

public final class FileBrowserController implements CommandListener {
	public interface Host {
		Display getDisplay();
		String getLiteral(int index);
		void showMainMenu();
		void showErrorMessage(String message, String details, Throwable exception);
		void showWaitForm(String message);
		void openExternalRom(String fileUrl);
	}

	private final Host host;
	private boolean fileTaskRunning;
	private List fileBrowserList;
	private String currentBrowserPath = "file:///";
	private final Vector browserEntries = new Vector();

	public FileBrowserController(Host host) {
		this.host = host;
	}

	public void showRoot() {
		runFileTask(new Runnable() {
			public void run() {
				showFileBrowser("file:///");
			}
		});
	}

	public boolean handles(Displayable displayable) {
		return displayable == fileBrowserList;
	}

	public void commandAction(Command com, Displayable s) {
		if (com.getCommandType() == Command.BACK) {
			fileBrowserList = null;
			host.showMainMenu();
			return;
		}

		int index = fileBrowserList.getSelectedIndex();
		if (index < 0 || index >= browserEntries.size()) {
			return;
		}
		final String selectedEntry = (String) browserEntries.elementAt(index);
		runFileTask(new Runnable() {
			public void run() {
				handleFileBrowserSelection(selectedEntry);
			}
		});
	}

	private void showFileBrowser(String path) {
		currentBrowserPath = FileBrowserUtil.normalizeDirectoryPath(path);
		fileBrowserList = new List(AppInfo.FILE_BROWSER_TITLE, List.IMPLICIT);
		fileBrowserList.addCommand(new Command(host.getLiteral(10), Command.BACK, 1));
		fileBrowserList.setCommandListener(this);
		browserEntries.removeAllElements();

		try {
			if (!"file:///".equals(currentBrowserPath)) {
				fileBrowserList.append(AppInfo.BACK_DIR_LABEL, null);
				browserEntries.addElement(AppInfo.BACK_DIR_LABEL);
			}

			if ("file:///".equals(currentBrowserPath)) {
				Enumeration roots = FileSystemRegistry.listRoots();
				while (roots.hasMoreElements()) {
					String root = (String) roots.nextElement();
					String url = FileBrowserUtil.normalizeDirectoryPath("file:///" + root);
					fileBrowserList.append(root, null);
					browserEntries.addElement(url);
				}
			} else {
				FileConnection dir = (FileConnection) Connector.open(currentBrowserPath, Connector.READ);
				try {
					if (!dir.exists() || !dir.isDirectory()) {
						throw new IOException("Invalid directory: " + currentBrowserPath);
					}

					Vector dirs = new Vector();
					Vector files = new Vector();
					Enumeration entries = dir.list();
					while (entries.hasMoreElements()) {
						String entry = (String) entries.nextElement();
						String full = currentBrowserPath + entry;
						if (entry.endsWith("/")) {
							dirs.addElement(full);
						} else if (FileBrowserUtil.isRomFile(entry)) {
							files.addElement(full);
						}
					}
					FileBrowserUtil.sortStrings(dirs);
					FileBrowserUtil.sortStrings(files);

					for (int i = 0; i < dirs.size(); i++) {
						String full = (String) dirs.elementAt(i);
						fileBrowserList.append(full.substring(currentBrowserPath.length()), null);
						browserEntries.addElement(full);
					}
					for (int i = 0; i < files.size(); i++) {
						String full = (String) files.elementAt(i);
						fileBrowserList.append(full.substring(currentBrowserPath.length()), null);
						browserEntries.addElement(full);
					}
				} finally {
					dir.close();
				}
			}

			host.getDisplay().setCurrent(fileBrowserList);
		} catch (Exception e) {
			host.showErrorMessage(AppInfo.FILE_BROWSER_OPEN_ERROR, currentBrowserPath, e);
			host.showMainMenu();
		}
	}

	private void handleFileBrowserSelection(String selected) {
		if (AppInfo.BACK_DIR_LABEL.equals(selected)) {
			showFileBrowser(FileBrowserUtil.parentDirectory(currentBrowserPath));
			return;
		}
		if (selected.endsWith("/")) {
			showFileBrowser(selected);
			return;
		}
		host.openExternalRom(selected);
	}

	private void runFileTask(final Runnable action) {
		if (fileTaskRunning) {
			return;
		}
		fileTaskRunning = true;
		host.showWaitForm(AppInfo.WAIT_STORAGE_MESSAGE);

		new Thread(new Runnable() {
			public void run() {
				try {
					action.run();
				} catch (Throwable t) {
					host.showErrorMessage(AppInfo.FILE_OPERATION_ERROR, null, t);
				} finally {
					fileTaskRunning = false;
				}
			}
		}).start();
	}
}
