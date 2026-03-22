package meboy.io;

import java.util.Hashtable;

public final class ExternalRomStore {
	private static final Hashtable externalRoms = new Hashtable();
	private static final Hashtable externalRomFiles = new Hashtable();

	private ExternalRomStore() {
	}

	public static void registerRom(String cartName, byte[] romData) {
		if (cartName == null || romData == null) {
			return;
		}
		externalRoms.put(cartName, romData);
	}

	public static byte[] takeRom(String cartName) {
		byte[] data = (byte[]) externalRoms.get(cartName);
		if (data != null) {
			externalRoms.remove(cartName);
		}
		return data;
	}

	public static void registerRomFile(String cartName, String fileUrl) {
		if (cartName == null || fileUrl == null) {
			return;
		}
		externalRomFiles.put(cartName, fileUrl);
	}

	public static String takeRomFile(String cartName) {
		String path = (String) externalRomFiles.get(cartName);
		if (path != null) {
			externalRomFiles.remove(cartName);
		}
		return path;
	}
}
