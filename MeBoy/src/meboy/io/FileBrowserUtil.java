package meboy.io;

import java.util.Vector;

public final class FileBrowserUtil {
	private FileBrowserUtil() {
	}

	public static void sortStrings(Vector items) {
		for (int i = 1; i < items.size(); i++) {
			String value = (String) items.elementAt(i);
			int j = i - 1;
			while (j >= 0 && compareIgnoreCase((String) items.elementAt(j), value) > 0) {
				items.setElementAt(items.elementAt(j), j + 1);
				j--;
			}
			items.setElementAt(value, j + 1);
		}
	}

	public static String normalizeDirectoryPath(String path) {
		if (path == null || path.length() == 0) {
			return "file:///";
		}
		if (!path.endsWith("/")) {
			return path + "/";
		}
		return path;
	}

	public static String parentDirectory(String path) {
		path = normalizeDirectoryPath(path);
		if ("file:///".equals(path)) {
			return path;
		}
		String trimmed = path.substring(0, path.length() - 1);
		int cut = trimmed.lastIndexOf('/');
		if (cut <= "file://".length()) {
			return "file:///";
		}
		return trimmed.substring(0, cut + 1);
	}

	public static boolean isRomFile(String name) {
		String lower = name.toLowerCase();
		return lower.endsWith(".gb") || lower.endsWith(".gbc") || lower.endsWith(".cgb");
	}

	public static String buildExternalCartID(String fileUrl) {
		int hash = fileUrl.hashCode();
		if (hash < 0) {
			hash = -hash;
		}
		return "ext_" + hash;
	}

	private static int compareIgnoreCase(String left, String right) {
		if (left == null) {
			return right == null ? 0 : -1;
		}
		if (right == null) {
			return 1;
		}
		return left.toLowerCase().compareTo(right.toLowerCase());
	}
}
