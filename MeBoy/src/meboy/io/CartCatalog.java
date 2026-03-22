package meboy.io;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Vector;
import javax.microedition.lcdui.List;
import javax.microedition.rms.RecordStore;
import meboy.MeBoy;

public final class CartCatalog {
	private static final String BUNDLED_CART_RESOURCE = "/carts";

	private final String[] displayNames;
	private final String[] fileNames;
	private final String[] ids;

	private CartCatalog(String[] displayNames, String[] fileNames, String[] ids) {
		this.displayNames = displayNames;
		this.fileNames = fileNames;
		this.ids = ids;
	}

	public static CartCatalog load(Class owner) {
		try {
			InputStream is = owner.getResourceAsStream(BUNDLED_CART_RESOURCE);
			DataInputStream dis = new DataInputStream(is);
			int numCarts = dis.readInt();
			String[] displayNames = new String[numCarts];
			String[] fileNames = new String[numCarts];
			String[] ids = new String[numCarts];

			for (int i = 0; i < numCarts; i++) {
				displayNames[i] = dis.readUTF();
				fileNames[i] = dis.readUTF();
				ids[i] = dis.readUTF();
			}
			is.close();
			return new CartCatalog(displayNames, fileNames, ids);
		} catch (Exception e) {
			MeBoy.log("No bundled carts were found in the JAR.");
			return empty();
		}
	}

	public static CartCatalog empty() {
		return new CartCatalog(new String[0], new String[0], new String[0]);
	}

	public int size() {
		return ids.length;
	}

	public boolean isEmpty() {
		return ids.length == 0;
	}

	public String[] getDisplayNames() {
		return displayNames;
	}

	public String getIdAt(int index) {
		return ids[index];
	}

	public String getFileNameAt(int index) {
		return fileNames[index];
	}

	public String getDisplayNameAt(int index) {
		return displayNames[index];
	}

	public String findDisplayNameById(String cartID) {
		for (int i = 0; i < ids.length; i++) {
			if (cartID.equals(ids[i])) {
				return displayNames[i];
			}
		}
		return null;
	}

	public int indexOfId(String cartID) {
		return findMatch(cartID, ids, false);
	}

	public int findMatchByFileName(String fileName, boolean fuzzy) {
		return findMatch(fileName, fileNames, fuzzy);
	}

	public int findMatchByDisplayName(String displayName, boolean fuzzy) {
		return findMatch(displayName, displayNames, fuzzy);
	}

	public void addSavegamesToList(List list, Vector cartIDs, Vector filenames) {
		for (int i = 0; i < ids.length; i++) {
			try {
				RecordStore rs = RecordStore.openRecordStore("20R_" + ids[i], true);
				if (rs.getNumRecords() > 0) {
					list.append(displayNames[i], null);
					cartIDs.addElement(ids[i]);
					filenames.addElement(fileNames[i]);
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

	private int findMatch(String match, String[] list, boolean fuzzy) {
		for (int i = 0; i < list.length; i++) {
			if (list[i].equals(match)) {
				return i;
			}
		}
		if (!fuzzy) {
			return -1;
		}
		int lastdot = match.lastIndexOf('.');
		if (lastdot != -1 && lastdot > match.length() - 5) {
			match = match.substring(0, lastdot);
		}
		for (int i = 0; i < list.length; i++) {
			if (list[i].startsWith(match)) {
				return i;
			}
		}
		return -1;
	}
}
