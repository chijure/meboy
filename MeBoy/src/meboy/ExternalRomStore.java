package meboy;

import java.util.Hashtable;

final class ExternalRomStore {
	private static final Hashtable externalRoms = new Hashtable();
	private static final Hashtable externalRomFiles = new Hashtable();

	private ExternalRomStore() {
	}

	static void registerRom(String cartName, byte[] romData) {
		if (cartName == null || romData == null) {
			return;
		}
		externalRoms.put(cartName, romData);
	}

	static byte[] takeRom(String cartName) {
		byte[] data = (byte[]) externalRoms.get(cartName);
		if (data != null) {
			externalRoms.remove(cartName);
		}
		return data;
	}

	static void registerRomFile(String cartName, String fileUrl) {
		if (cartName == null || fileUrl == null) {
			return;
		}
		externalRomFiles.put(cartName, fileUrl);
	}

	static String takeRomFile(String cartName) {
		String path = (String) externalRomFiles.get(cartName);
		if (path != null) {
			externalRomFiles.remove(cartName);
		}
		return path;
	}
}
