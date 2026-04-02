package meboy.io;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

public final class SaveFileStore {
	private static final String SAVE_EXTENSION = ".sav";
	private static final String RTC_EXTENSION = ".rtc";

	private SaveFileStore() {
	}

	public static String getSaveFileUrl(String romFileUrl) {
		return replaceExtension(romFileUrl, SAVE_EXTENSION);
	}

	public static String getRtcFileUrl(String romFileUrl) {
		return replaceExtension(romFileUrl, RTC_EXTENSION);
	}

	private static String replaceExtension(String romFileUrl, String extension) {
		if (romFileUrl == null || romFileUrl.length() == 0) {
			return null;
		}
		int lastSlash = romFileUrl.lastIndexOf('/');
		int lastDot = romFileUrl.lastIndexOf('.');
		if (lastDot > lastSlash) {
			return romFileUrl.substring(0, lastDot) + extension;
		}
		return romFileUrl + extension;
	}

	public static void write(String romFileUrl, byte[] data) throws Exception {
		writeFile(getSaveFileUrl(romFileUrl), data);
	}

	public static void writeRtc(String romFileUrl, byte[] data) throws Exception {
		writeFile(getRtcFileUrl(romFileUrl), data);
	}

	private static void writeFile(String fileUrl, byte[] data) throws Exception {
		FileConnection fc = null;
		OutputStream os = null;
		try {
			fc = (FileConnection) Connector.open(fileUrl, Connector.READ_WRITE);
			if (!fc.exists()) {
				fc.create();
			} else {
				fc.truncate(0);
			}
			os = fc.openOutputStream();
			os.write(data);
			os.flush();
		} finally {
			try {
				if (os != null) {
					os.close();
				}
			} catch (Exception e) {
			}
			try {
				if (fc != null) {
					fc.close();
				}
			} catch (Exception e) {
			}
		}
	}

	public static byte[] read(String romFileUrl) throws Exception {
		return readFile(getSaveFileUrl(romFileUrl));
	}

	public static byte[] readRtc(String romFileUrl) throws Exception {
		return readFile(getRtcFileUrl(romFileUrl));
	}

	private static byte[] readFile(String fileUrl) throws Exception {
		FileConnection fc = null;
		InputStream is = null;
		ByteArrayOutputStream out = null;
		try {
			fc = (FileConnection) Connector.open(fileUrl, Connector.READ);
			if (!fc.exists() || fc.isDirectory()) {
				return null;
			}
			is = fc.openInputStream();
			out = new ByteArrayOutputStream();
			byte[] buffer = new byte[256];
			int read;
			while ((read = is.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (Exception e) {
			}
			try {
				if (is != null) {
					is.close();
				}
			} catch (Exception e) {
			}
			try {
				if (fc != null) {
					fc.close();
				}
			} catch (Exception e) {
			}
		}
	}

	public static void deleteRtc(String romFileUrl) throws Exception {
		FileConnection fc = null;
		try {
			fc = (FileConnection) Connector.open(getRtcFileUrl(romFileUrl), Connector.READ_WRITE);
			if (fc.exists() && !fc.isDirectory()) {
				fc.delete();
			}
		} finally {
			try {
				if (fc != null) {
					fc.close();
				}
			} catch (Exception e) {
			}
		}
	}
}
