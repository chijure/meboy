package meboy.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AppInfo {
	private static final String VERSION_RESOURCE = "/version.properties";
	private static final String VERSION_PROPERTY = "app.version";
	private static final String DEFAULT_APP_VERSION = "0.0.0";

	public static final String LOAD_SD_LABEL = "Load ROM from SD";
	public static final String BACK_DIR_LABEL = "..";
	public static final String APP_VERSION = loadAppVersion();
	public static final String APP_DISPLAY_VERSION = toDisplayVersion(APP_VERSION);
	public static final String APP_TITLE = "MeBoy " + APP_DISPLAY_VERSION;
	public static final String ABOUT_MESSAGE = AppInfo.APP_TITLE + " \u00A9 Bj\u00F6rn Carlin, 2005-2009.\n"
			+ "Additional development: Juan Reategui Solis (chijure)\n"
			+ "http://arktos.se/meboy/";
	public static final String FILE_BROWSER_TITLE = "Select ROM";
	public static final String DEVICE_INFO_LABEL = "Device Info";
	public static final String DEVICE_INFO_TITLE = "Device Info";
	public static final String BACK_LABEL = "Back";
	public static final String LOG_LABEL = "Log";
	public static final String WAIT_FORM_TITLE = "MeBoy";
	public static final String WAIT_STORAGE_MESSAGE = "Opening storage...";
	public static final String WAIT_LOADING_ROM_MESSAGE = "Loading ROM...";
	public static final String NO_BUNDLED_ROMS_MESSAGE = "No bundled ROMs found. Use \"";
	public static final String FILE_BROWSER_OPEN_ERROR = "Could not open file browser.";
	public static final String ROM_LOAD_MEMORY_ERROR = "Not enough memory to load ROM.";
	public static final String ROM_LOAD_ERROR = "Could not load ROM from storage.";
	public static final String FILE_OPERATION_ERROR = "File operation failed.";
	public static final int MAX_EXTERNAL_ROM_SIZE = 4 * 1024 * 1024;

	private AppInfo() {
	}

	private static String loadAppVersion() {
		InputStream input = null;

		try {
			input = AppInfo.class.getResourceAsStream(VERSION_RESOURCE);
			if (input == null) {
				return DEFAULT_APP_VERSION;
			}

			String properties = readFully(input);
			String version = extractProperty(properties, VERSION_PROPERTY);
			if (version == null || version.length() == 0) {
				return DEFAULT_APP_VERSION;
			}

			return version;
		} catch (IOException e) {
			return DEFAULT_APP_VERSION;
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static String readFully(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[64];
		int bytesRead;

		while ((bytesRead = input.read(buffer)) != -1) {
			output.write(buffer, 0, bytesRead);
		}

		return new String(output.toByteArray());
	}

	private static String extractProperty(String properties, String propertyName) {
		String[] lines = splitLines(properties);
		String prefix = propertyName + "=";

		for (int i = 0; i < lines.length; i++) {
			if (lines[i].startsWith(prefix)) {
				return trim(lines[i].substring(prefix.length()));
			}
		}

		return null;
	}

	private static String[] splitLines(String text) {
		int lineCount = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lineCount++;
			}
		}

		String[] lines = new String[lineCount];
		int lineIndex = 0;
		int lineStart = 0;

		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines[lineIndex++] = text.substring(lineStart, i);
				lineStart = i + 1;
			}
		}

		lines[lineIndex] = text.substring(lineStart);
		return lines;
	}

	private static String trim(String value) {
		int start = 0;
		int end = value.length();

		while (start < end && isTrimCharacter(value.charAt(start))) {
			start++;
		}

		while (end > start && isTrimCharacter(value.charAt(end - 1))) {
			end--;
		}

		return value.substring(start, end);
	}

	private static boolean isTrimCharacter(char value) {
		return value <= ' ';
	}

	private static String toDisplayVersion(String version) {
		if (version.endsWith(".0")) {
			return version.substring(0, version.length() - 2);
		}
		return version;
	}
}
