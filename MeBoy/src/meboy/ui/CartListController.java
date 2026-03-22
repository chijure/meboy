package meboy.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

public final class CartListController implements CommandListener {
	public interface Host {
		Display getDisplay();
		String getLiteral(int index);
		String[] getCartDisplayNames();
		String getCartIDAt(int index);
		String getCartDisplayNameAt(int index);
		void showMainMenu();
		void openCart(String cartID, String displayName);
		void showErrorMessage(String message, String details, Throwable exception);
		void showNoBundledRomsMessage();
	}

	private final Host host;
	private List cartList;

	public CartListController(Host host) {
		this.host = host;
	}

	public void show() {
		String[] cartDisplayNames = host.getCartDisplayNames();
		if (cartDisplayNames.length == 0) {
			host.showNoBundledRomsMessage();
			return;
		}
		cartList = new List(host.getLiteral(7), List.IMPLICIT);
		for (int i = 0; i < cartDisplayNames.length; i++) {
			cartList.append(cartDisplayNames[i], null);
		}
		cartList.addCommand(new Command(host.getLiteral(10), Command.BACK, 1));
		cartList.setCommandListener(this);
		host.getDisplay().setCurrent(cartList);
	}

	public boolean handles(Displayable displayable) {
		return displayable == cartList;
	}

	public void commandAction(Command com, Displayable s) {
		if (com.getCommandType() == Command.BACK) {
			cartList = null;
			host.showMainMenu();
			return;
		}

		int index = cartList.getSelectedIndex();
		try {
			host.openCart(host.getCartIDAt(index), host.getCartDisplayNameAt(index));
			cartList = null;
		} catch (Exception e) {
			host.showErrorMessage(null, "error#1", e);
		}
	}
}
