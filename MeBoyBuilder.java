package se.arktos.meboy;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import java.util.zip.*;
import javax.swing.*;

/**
 * A (decreasingly) quick-and-dirty class for building the MeBoy.jar and .jad files.
 * It will copy the contents of its own jar (except MeBoyBuilder.class)
 * and replace the manifest with the file MEMANIFEST.
 * 
 * It will also add the selected roms, and enumerate them in the
 * carts.txt file.
 */
public class MeBoyBuilder {
	public static void main(String[] arg) {
		checkForUpdates();
		
		try {
			ZipOutputStream zo = new ZipOutputStream(new FileOutputStream("MeBoy.jar"));

			int banksPerFile = 8;
			int maxFileSize = banksPerFile << 14;
			byte[] buf = new byte[maxFileSize];
			int r;

			boolean version12compat = false;
			
			// add games
			ArrayList<String> gameFiles = new ArrayList<String>();
			int gamesSize = 0;
			StringBuffer games = new StringBuffer();
			StringBuffer gamesCommaSeparated = new StringBuffer();
			int gameCount = 0;
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select ROM file");
			while (true) {
				StringBuffer message = new StringBuffer();
				message.append("MeBoy.jar created at " + new File("MeBoy.jar").getCanonicalFile() + '\n');
				message.append("Currently ").append(gameCount).append(" ROM file").append(
						(gameCount == 1 ? "" : "s"));
				if (gameCount > 0)
					message
							.append(" (")
							.append(gamesCommaSeparated)
							.append(gamesSize)
							.append(
									" kB uncompressed).\nAdd another ROM file, or finish building MeBoy.jar?");
				else
					message.append(". Add a ROM file, or finish building MeBoy.jar?");

				int response = JOptionPane.showOptionDialog(null, string2JTA(message.toString()),
						"MeBoyBuilder 1.4", 0, JOptionPane.PLAIN_MESSAGE, null, new Object[] {
								"Add ROM", "Finish", "Update options"}, "Add ROM");

				if (response == 1 || response == -1) {
					break;
				} else if (response == 2) {
					String m = "For users updating from MeBoy 1.1 or 1.2:\n\n" +
							"Due to a design mistake in the development of MeBoy 1.3, the " +
							"newer versions do not by default share suspended game info and battery-backed " +
							"game memory with MeBoy 1.1 and 1.2. If you are updating from 1.1 or 1.2 and want to keep " +
							"your saved games, please select the 1.2-compatible " +
							"option below to use the \"old\" method of saving. (The new features " +
							"in 1.4 will work even if you select the 1.2-compatble option.)\n\n" +
							"Your suspended game will show up as \"1: (From 1.1/1.2)\" in the \"Resume " +
							"Game\" list. Please note that suspended games from combined GB/GBC games (e.g. " +
							"Zelda DX) can not be transferred from 1.2 to 1.4!\n\n" +
							"The battery-backed game memory is always transferable, so when possible, " +
							"please save your progress by using the game's own save feature (not by suspending) before " +
							"upgrading from 1.1 or 1.2 to 1.4.\n\n" +
							"If you have not previously installed 1.1 or 1.2, please select the default option.";
					
					response = JOptionPane.showOptionDialog(null, string2JTA(m),
						"MeBoyBuilder 1.4", 0, JOptionPane.PLAIN_MESSAGE, null, new Object[] {
								"Default", "1.2-compatible"}, "Default");
					if (response == 1)
						version12compat = true;
					else if (response == 0)
						version12compat = false;
					
					continue;
				}

				int returnVal = chooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					InputStream fi;

					String fileName = f.getName();

					if (fileName.endsWith(".zip")) {
						ZipInputStream zin = new ZipInputStream(new FileInputStream(f));
						fi = zin;
						// replace the name with the name of the first zipped file
						fileName = zin.getNextEntry().getName();
					} else {
						// normal, non-zipped file
						fi = new FileInputStream(f);
					}
					if (fileName.length() > 24)
						fileName = fileName.substring(0, 24);

					// replace all non-ascii characters with underscore
					StringBuilder sb = new StringBuilder(fileName);
					for (int i = 0; i < sb.length(); i++)
						if (sb.charAt(i) < 31 || sb.charAt(i) > 127)
							sb.setCharAt(i, '_');
					fileName = sb.toString();

					// make sure name is unique
					if (gameFiles.contains(fileName)) {
						int ix = 2;
						while (gameFiles.contains(fileName + "-" + ix))
							ix++;
						fileName = fileName + "-" + ix;
					}
					gameFiles.add(fileName);

					games.append(fileName).append('\n');
					gamesCommaSeparated.append(fileName).append(", ");

					String error = null;

					// read first part
					int bufIndex = 0;
					do {
						r = fi.read(buf, bufIndex, maxFileSize-bufIndex);
						if (r > 0)
							bufIndex += r;
					} while (bufIndex < maxFileSize && r >= 0);
					
					// do sanity check
					boolean sane = true;

					if (bufIndex < 0x200) {
						error = "There was an error reading the file " + fileName + ", and it will" +
								" not be included in the MeBoy.jar file.";
						System.out.println("r: " + r + "; bufIndex: " + bufIndex + "; maxFileSize: " + maxFileSize);
						sane = false;
					}

					// sanity check the cartridge file
					int cartSize = lookUpCartSize(buf[0x0148] & 0xff);
					if (sane && cartSize == -1 || (buf[0x0104] != (byte) 0xCE)
							|| (buf[0x010d] != (byte) 0x73) || (buf[0x0118] != (byte) 0x88)) {
						error = fileName + " does not seem to be a valid ROM file, and it"
								+ " will not been included in the MeBoy.jar file.";
						System.out.println("cartSize " + cartSize);
						System.out.println("104 " + buf[0x0104]);
						System.out.println("10d " + buf[0x010d]);
						System.out.println("118 " + buf[0x0118]);
						System.out.println("148 " + buf[0x0148]);
						sane = false;
					}

					if (sane) {
						// copy file
						int size = bufIndex;
						int fileIndex = 0;
						
						zo.putNextEntry(new ZipEntry(fileName + fileIndex++));
						zo.write(buf, 0, bufIndex);
						
						while (r != -1) {
							// split into several files
							bufIndex = 0;
							do {
								r = fi.read(buf, bufIndex, maxFileSize-bufIndex);
								if (r > 0)
									bufIndex += r;
							} while (bufIndex < maxFileSize && r >= 0);
							
							size += bufIndex;
							if (bufIndex > 0) {
								zo.putNextEntry(new ZipEntry(fileName + fileIndex++));
								zo.write(buf, 0, bufIndex);
							}
						}

						if (cartSize * 0x4000 != size)
							error = fileName
									+ " does not have the expected size, and will probably not"
									+ "work correctly (expected " + cartSize * 0x4000
									+ " bytes, found " + size + " bytes).";

						// check size
						gamesSize += size / 1024;

						gameCount++;
					}

					if (error != null)
						JOptionPane.showMessageDialog(null, string2JTA(error),
								"MeBoyBuilder warning", JOptionPane.WARNING_MESSAGE);
					fi.close();
				}
			}
			
			String manifestName = version12compat ? "MEMANIFEST12" : "MEMANIFEST";

			// copy MeBoy and manifest files
			String[][] files = new String[][] { { "DmgcpuBW.class"}, { "DmgcpuColor.class"},
					{ "GBCanvas.class"},
					{ "GraphicsChipBW.class"}, { "GraphicsChipColor.class"}, { "ICpu.class"}, { "MeBoy.class"},
					{ manifestName, "META-INF/MANIFEST.MF"}};
			for (String[] copyName : files) {
				InputStream tis = ClassLoader.getSystemClassLoader().getResourceAsStream(
						copyName[0]);

				zo.putNextEntry(new ZipEntry(copyName[copyName.length-1]));
				r = tis.read(buf, 0, maxFileSize);
				while (r >= 0) {
					if (r > 0)
						zo.write(buf, 0, r);
					r = tis.read(buf, 0, maxFileSize);
				}
				tis.close();
			}
			
			// add carts.txt
			zo.putNextEntry(new ZipEntry("carts.txt"));
			PrintWriter pw = new PrintWriter(zo);
			pw.println(games.toString());
			pw.close();
			zo.close();

			// create MeBoy.jad
			BufferedReader ir = new BufferedReader(new InputStreamReader(ClassLoader.getSystemClassLoader().getResourceAsStream(
						manifestName)));
			FileWriter fw = new FileWriter("MeBoy.jad");
			String s;
			while ((s = ir.readLine()) != null) {
				fw.write(s + '\n');
			}
			fw.write("MIDlet-Jar-URL: MeBoy.jar\n");
			fw.write("MIDlet-Jar-Size: " + new File("MeBoy.jar").length() + '\n');
			fw.close();

			if (gameCount == 0)
				JOptionPane.showMessageDialog(null,
						string2JTA("Please note that MeBoy requires at least one ROM file to "
								+ "do anything remotely exciting."), "No carts",
						JOptionPane.INFORMATION_MESSAGE);
			else
				JOptionPane
						.showMessageDialog(
								null,
								string2JTA("The files MeBoy.jar and MeBoy.jad have been completed (" + new File("MeBoy.jar").getCanonicalFile() + "/jad).\n"
										+ "Most phones only require a \".jar\" file, so try to transfer MeBoy.jar to your phone.\n"
										+ "- If your phone accepted MeBoy.jar, you do not need MeBoy.jad and can safely throw it away.\n"
										+ "- If your phone requires a \".jad\" file, transfer MeBoy.jad instead."),
								"MeBoyBuilder Finished", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error has occurred (" + e + ").", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		System.exit(0);
	}

	static void checkForUpdates() {
		Preferences prefs = Preferences.userNodeForPackage(MeBoyBuilder.class).node("meboybuilder");
		String checkPrefs = prefs.get("CheckUpdates", null);

		boolean firstLaunch = checkPrefs == null;

		if (firstLaunch) {
			int response = JOptionPane
					.showOptionDialog(
							null,
							string2JTA("Do you want MeBoyBuilder to automatically check for program updates when launched?"),
							"MeBoyBuilder 1.4", 0, JOptionPane.PLAIN_MESSAGE, null, new Object[] {
									"Don't check", "Check"}, "Check");

			if (response == 1) {
				prefs.put("CheckUpdates", "true");
				checkPrefs = "true";
			} else if (response == 0) {
				prefs.put("CheckUpdates", "false");
			}
		}

		if ("true".equals(checkPrefs)) {
			long now = System.currentTimeMillis();
			long lastTime = prefs.getLong("LastTime", now - 1000*60*60*24);
			if (lastTime + 1000*60*60*24 > now) {
				return; // less than a day since last check
			}
			prefs.putLong("LastTime", now);
			
			try {
				// read info from home page
				URL inputURL = new URL("http://arktos.se/meboy/update/140.txt");
				URLConnection urlConnection = inputURL.openConnection();
				urlConnection.connect();

				BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());
				byte[] buf = new byte[65536];
				int done = 0;

				int r = in.read(buf, 0, 65536);
				while (r >= 0) {
					done += r;
					r = in.read(buf, done, 65536 - done);
				}

				if (done > 1) {
					String message = new String(buf, 0, done);

					int response = JOptionPane.showOptionDialog(null,
							string2JTA(message.toString()), "MeBoyBuilder 1.4", 0,
							JOptionPane.PLAIN_MESSAGE, null, new Object[] { "Cancel",
									"Visit homepage"}, "Visit homepage");

					if (response == 1) {
						openURL("http://arktos.se/meboy/");
					}
				} else if (firstLaunch) {
					JOptionPane.showMessageDialog(null,
							string2JTA("1.4 is the most recent version."), "MeBoyBuilder 1.4",
							JOptionPane.WARNING_MESSAGE);
				}
			} catch (Exception e) {
				// not really important
				e.printStackTrace();
			}
		}
	}

	private static JTextArea string2JTA(String message) {
		JTextArea jta = new JTextArea(message, 1 + message.length() / 40, 40);

		jta.setLineWrap(true);
		jta.setWrapStyleWord(true);
		jta.setEditable(false);
		jta.setBackground(new java.awt.Color(0, true));
		return jta;
	}

	private static int lookUpCartSize(int sizeByte) {
		/** Translation between ROM size byte contained in the ROM header, and the number
		 *  of 16Kb ROM banks the cartridge will contain
		 */
		if (sizeByte < 8)
			return 2 << sizeByte;
		else if (sizeByte == 0x52)
			return 72;
		else if (sizeByte == 0x53)
			return 80;
		else if (sizeByte == 0x54)
			return 96;
		return -1;
	}


	/////////////////////////////////////////////////////////
	//  Bare Bones Browser Launch                          //
	//  Version 1.5                                        //
	//  December 10, 2005                                  //
	//  Supports: Mac OS X, GNU/Linux, Unix, Windows XP    //
	//  Example Usage:                                     //
	//     String url = "http://www.centerkey.com/";       //
	//     BareBonesBrowserLaunch.openURL(url);            //
	//  Public Domain Software -- Free to Use as You Like  //
	/////////////////////////////////////////////////////////
	public static void openURL(String url) throws Exception {
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Mac OS")) {
			Class fileMgr = Class.forName("com.apple.eio.FileManager");
			Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[] { String.class});
			openURL.invoke(null, new Object[] { url});
		} else if (osName.startsWith("Windows"))
			Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
		else { //assume Unix or Linux
			String[] browsers = { "firefox", "opera", "konqueror", "epiphany", "mozilla",
					"netscape"};
			String browser = null;
			for (int count = 0; count < browsers.length && browser == null; count++)
				if (Runtime.getRuntime().exec(new String[] { "which", browsers[count]}).waitFor() == 0)
					browser = browsers[count];
			if (browser == null)
				throw new Exception("Could not find web browser");
			else
				Runtime.getRuntime().exec(new String[] { browser, url});
		}
	}
}
