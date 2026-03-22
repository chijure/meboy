package meboy.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import meboy.io.SuspendedGameStore;

public final class ResumeGameController implements CommandListener {
	public interface Host {
		Display getDisplay();
		String[] getSuspendedNames();
		String getLiteral(int index);
		void showMainMenu();
		void showErrorMessage(String message, String details, Throwable exception);
		void removeSuspendedGameAt(int index);
		void appendSuspendedGame(String name);
		String resolveCartDisplayName(String suspendCartID);
		void openSuspendedGame(String suspendCartID, String suspendCartDisplayName, String suspendName, byte[] suspendState);
		int nextSuspendCounter();
	}

	private final Host host;
	private List suspendList;

	public ResumeGameController(Host host) {
		this.host = host;
	}

	public void show() {
		String[] suspendedNames = host.getSuspendedNames();
		if (suspendedNames.length == 0) {
			host.showMainMenu();
			return;
		}
		suspendList = new List(host.getLiteral(7), List.IMPLICIT);
		for (int i = 0; i < suspendedNames.length; i++) {
			suspendList.append(suspendedNames[i], null);
		}
		suspendList.addCommand(new Command(host.getLiteral(8), Command.SCREEN, 2));
		suspendList.addCommand(new Command(host.getLiteral(9), Command.SCREEN, 2));
		suspendList.addCommand(new Command(host.getLiteral(10), Command.BACK, 1));
		suspendList.setCommandListener(this);
		host.getDisplay().setCurrent(suspendList);
	}

	public boolean handles(Displayable displayable) {
		return displayable == suspendList;
	}

	public void commandAction(Command com, Displayable s) {
		if (com.getCommandType() == Command.BACK) {
			suspendList = null;
			host.showMainMenu();
			return;
		}

		String label = com.getLabel();
		int index = suspendList.getSelectedIndex();
		String selectedName = host.getSuspendedNames()[index];

		if (host.getLiteral(8).equals(label)) {
			try {
				host.removeSuspendedGameAt(index);
				SuspendedGameStore.delete(selectedName);
			} catch (Exception e) {
				host.showErrorMessage(null, "error#2", e);
			}
			show();
		} else if (host.getLiteral(9).equals(label)) {
			try {
				String oldName = selectedName;
				String newName = host.nextSuspendCounter() + oldName.substring(oldName.indexOf(':'));
				SuspendedGameStore.copy(oldName, newName);
				host.appendSuspendedGame(newName);
				show();
			} catch (Exception e) {
				host.showErrorMessage(null, "error#3", e);
			}
		} else {
			try {
				SuspendedGameStore.SuspendedGameData suspendedGame = SuspendedGameStore.load(selectedName);
				String suspendCartDisplayName = host.resolveCartDisplayName(suspendedGame.cartID);
				if (suspendCartDisplayName == null) {
					host.showErrorMessage(host.getLiteral(11), suspendedGame.cartID, null);
				} else {
					host.openSuspendedGame(suspendedGame.cartID, suspendCartDisplayName, selectedName, suspendedGame.state);
				}
				suspendList = null;
			} catch (Exception e) {
				host.showErrorMessage(null, "error#4", e);
			}
		}
	}
}
