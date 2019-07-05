/*

MeBoy

Copyright 2005-2008 Bjorn Carlin
http://www.arktos.se/

Based on JavaBoy, COPYRIGHT (C) 2001 Neil Millstone and The Victoria
University of Manchester. Bluetooth support based on code contributed by
Martin Neumann.

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/

package se.arktos.meboy;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import java.util.zip.*;

import javax.bluetooth.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

/**
 * A class for building the MeBoy.jar and .jad files.
 * It will copy the relevant contents of its own jar
 * and insert a new manifest file.
 * 
 * It will also add roms, and enumerate them in the
 * carts file.
 */
public class MeBoyBuilder implements ActionListener, ListSelectionListener, WindowListener {
	public static void main(String[] args) {
		checkForUpdates();
		new MeBoyBuilder();
	}

	static Bluetooth bluetoothInstance;
	
	JFrame frame = new JFrame("MeBoyBuilder 2.0");
	JButton addGame = new JButton("Add game");
	JButton removeGame = new JButton("Remove game");
	JButton renameGame = new JButton("Rename game");
	JButton createJar = new JButton("Create MeBoy.jar");
	JButton bluetooth = new JButton("Bluetooth");

	ArrayList<Game> games = new ArrayList<Game>();
	
	JFileChooser chooser = new JFileChooser();
	JPanel content = new JPanel(new BorderLayout(4, 4));
	JList list = new JList();
	JScrollPane jsp = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

	boolean dirty;

	private MeBoyBuilder() {
		frame.setBounds(100, 100, 400, 300);
		frame.setMinimumSize(new Dimension(200, 200));

		content.add(jsp, BorderLayout.CENTER);

		JPanel pouter = new JPanel(new BorderLayout(4, 4));
		JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));

		addGame.addActionListener(this);
		removeGame.addActionListener(this);
		renameGame.addActionListener(this);
		createJar.addActionListener(this);
		bluetooth.addActionListener(this);
		
		try {
			// Test if there's a Bluetooth stack we can use:
			LocalDevice.getLocalDevice();
		} catch (Exception e) {
			bluetooth.setEnabled(false);
		}

		p.add(addGame);
		p.add(removeGame);
		p.add(renameGame);
		p.add(createJar);
		pouter.add(p, BorderLayout.NORTH);
		pouter.add(bluetooth, BorderLayout.SOUTH);

		content.setBorder(new EmptyBorder(4, 4, 4, 4));
		content.add(pouter, BorderLayout.EAST);

		frame.getContentPane().add(content);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(this);

		chooser.setDialogTitle("Select ROM file");

		updateList();
	}

	private void updateList() {
		String[] names = new String[games.size()];
		
		int i = 0;
		for (Game g : games) {
			names[i++] = g.label(); 
		}
		
		list = new JList(names);
		list.addListSelectionListener(this);
		jsp.setViewportView(list);
		content.revalidate();
		valueChanged(null);
	}

	public void valueChanged(ListSelectionEvent e) {
		removeGame.setEnabled(list.getSelectedValue() != null);
		renameGame.setEnabled(list.getSelectedValue() != null);
		createJar.setEnabled(list.getModel().getSize() > 0);
	}

	public void windowClosing(WindowEvent e) {
		if (!dirty)
			System.exit(0);

		String q = "Do you want to discard changes and quit? A MeBoy.jar file " +
			"has not yet been created with the games you added.";

		int response = JOptionPane.showOptionDialog(null, string2JTA(q), "MeBoyBuilder 2.0", 0,
				JOptionPane.PLAIN_MESSAGE, null, new Object[] { "Quit", "Cancel"}, "Quit");

		if (response == 0)
			System.exit(0);
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == addGame) {
			try {
				addGame();
				updateList();
			} catch (Exception ex) {
				fatalException(ex);
			}
		} else if (e.getSource() == removeGame) {
			int i = list.getSelectedIndex();
			if (i < 0)
				return;
			games.remove(i);
			dirty = true;
			updateList();
		} else if (e.getSource() == renameGame) {
			int i = list.getSelectedIndex();
			if (i < 0)
				return;
			
			Game g = games.get(i);

			String s = JOptionPane.showInputDialog(
					"Please enter a new name (max 24 characters):", g.displayName);
			
			if (s == null || s.trim().length() < 1)
				return;
			s = cleanName(s);
			if (g.displayName.equals(s))
				return;
			
			if (findByDisplayName(s) != null) {
				JOptionPane.showMessageDialog(null,
					string2JTA("Please select a unique name."), "MeBoyBuilder 2.0",
					JOptionPane.PLAIN_MESSAGE);
				return;
			}
			g.displayName = s;
			Collections.sort(games);
			updateList();
		} else if (e.getSource() == createJar) {
			try {
				createJar();
			} catch (Exception ex) {
				fatalException(ex);
			}
		} else if (e.getSource() == bluetooth) {
			if (bluetoothInstance == null)
				bluetoothInstance = new Bluetooth();
			else {
				bluetoothInstance.frame.requestFocus();
			}
		}
	}

	private String cleanName(String name) {
		name = name.trim();
		if (name.length() > 24)
			name = name.substring(0, 24);

		// replace all control characters with underscore
		StringBuilder sb = new StringBuilder(name);
		for (int i = 0; i < sb.length(); i++)
			if (sb.charAt(i) < 31 || sb.charAt(i) == '/')
				sb.setCharAt(i, '_');
		name = sb.toString();
		return name;
	}
	
	private Game findByDisplayName(String displayName) {
		for (Game g : games)
			if (g.displayName.equals(displayName))
				return g;
		return null;
	}

	private Game findByCartID(String cartID) {
		for (Game g : games)
			if (g.cartID.equals(cartID))
				return g;
		return null;
	}

	private void addGame() throws Exception {
		int returnVal = chooser.showOpenDialog(frame);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;

		File f = chooser.getSelectedFile();
		InputStream fi;

		String fileAndDisplayName = f.getName();

		if (fileAndDisplayName.endsWith(".zip")) {
			ZipInputStream zin = new ZipInputStream(new FileInputStream(f));
			fi = zin;
			// replace the name with the name of the first zipped file
			fileAndDisplayName = zin.getNextEntry().getName();
		} else {
			// normal, non-zipped file
			fi = new FileInputStream(f);
		}

		String uncleanName = fileAndDisplayName;
		fileAndDisplayName = cleanName(fileAndDisplayName);

		// make sure name is unique
		if (findByDisplayName(fileAndDisplayName) != null) {
			int ix = 2;
			while (findByDisplayName(fileAndDisplayName + "-" + ix) != null)
				ix++;
			fileAndDisplayName = fileAndDisplayName + "-" + ix;
		}

		String error = null;

		// read to buffer
		int bufIndex = 0;
		byte[] buffer = new byte[4 << 20];
		int r;
		do {
			r = fi.read(buffer, bufIndex, buffer.length - bufIndex);
			if (r > 0)
				bufIndex += r;
		} while (bufIndex < buffer.length && r >= 0);

		// do sanity check
		boolean sane = true;

		if (bufIndex < 0x200 || bufIndex < f.length()) {
			error = "There was an error reading the file " + uncleanName
					+ ", and it will not be added.";
			System.out.println("r: " + r + "; length: " + bufIndex);
			sane = false;
		}

		// sanity check the cartridge file
		int cartSize = lookUpCartSize(buffer[0x0148] & 0xff);
		if (sane && cartSize == -1 || (buffer[0x0104] != (byte) 0xCE)
				|| (buffer[0x010d] != (byte) 0x73) || (buffer[0x0118] != (byte) 0x88)) {
			error = uncleanName + " does not seem to be a valid ROM file, and it" + " will not added.";
			System.out.println("cartSize " + cartSize);
			System.out.println("104 " + buffer[0x0104]);
			System.out.println("10d " + buffer[0x010d]);
			System.out.println("118 " + buffer[0x0118]);
			System.out.println("148 " + buffer[0x0148]);
			sane = false;

			if (uncleanName.endsWith(".gba"))
				error += " Please note that MeBoy does not emulate the Gameboy Advance.";
			if (uncleanName.endsWith(".nds"))
				error += " Please note that MeBoy does not emulate the Nintendo DS.";
		}

		String identifier = "";
		if (sane) {
			for (int i = 0x134; i < 0x143; i++)
				if (buffer[i] >= 31 && buffer[i] < 128)
					identifier = identifier + (char) buffer[i];
			if (findByCartID(identifier) != null) {
				sane = false;
				error = "This game has already been added (" + findByCartID(identifier).displayName + ").";
			}
		}
		
		if (sane) {
			Game g = new Game();
			g.data = new byte[bufIndex];
			System.arraycopy(buffer, 0, g.data, 0, bufIndex);
			g.displayName = fileAndDisplayName;
			g.fileName = fileAndDisplayName;
			g.cartID = identifier;
			games.add(g);
			Collections.sort(games);
			dirty = true;
		} else {
			JOptionPane.showMessageDialog(null, string2JTA(error), "MeBoyBuilder warning",
					JOptionPane.WARNING_MESSAGE);
		}
		fi.close();
	}

	private void createJar() throws IOException {
		JFileChooser saveChooser = new JFileChooser();
		saveChooser.setSelectedFile(new File("MeBoy.jar"));
		int returnVal = saveChooser.showSaveDialog(frame);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;

		File f = saveChooser.getSelectedFile();

		String midletName = f.getName();
		if (midletName.length() > 4 && midletName.toLowerCase().endsWith(".jar"))
			midletName = midletName.substring(0, midletName.length() - 4);

		if (!midletName.equals("MeBoy")) {
			String q = "You have selected a different name than MeBoy. Mobile phones "
					+ "use the name to identify applications, and settings and saved games "
					+ "can not be shared between applications with different names. Do "
					+ "you want to use the name \"" + midletName + "\"?";

			String a1 = "Use MeBoy";
			String a2 = "Use " + midletName;

			int response = JOptionPane.showOptionDialog(null, string2JTA(q), "MeBoyBuilder 2.0", 0,
					JOptionPane.PLAIN_MESSAGE, null, new Object[] { a1, a2}, a1);

			if (response == -1)
				return;

			if (response == 0)
				midletName = "MeBoy";
		}

		f = new File(f.getParent(), midletName + ".jar");

		ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(f));

		ArrayList<String> cartsTxt = new ArrayList<String>();

		for (Game g : games) {
			cartsTxt.add(g.displayName);
			cartsTxt.add(g.fileName);
			cartsTxt.add(g.cartID);

			int maxFileSize = 1 << 17; // for each split file

			for (int j = 0; j < Math.max(1, g.data.length / maxFileSize); j++) {
				zo.putNextEntry(new ZipEntry(g.cartID + j));
				zo.write(g.data, j * maxFileSize, Math.min(maxFileSize, g.data.length - j * maxFileSize));
			}
		}

		// copy MeBoy files
		String[] files = new String[] {
				"AdvancedGraphicsChip.class",
				"Bluetooth.class",
				"Dmgcpu.class",
				"GBCanvas.class",
				"GraphicsChip.class",
				"MeBoy.class",
				"meboy.png",
				"SimpleGraphicsChip.class",
				"lang/index.txt", "lang/0.txt"};
		for (String copyName : files) {
			InputStream tis = ClassLoader.getSystemClassLoader().getResourceAsStream(copyName);

			if (tis == null) {
				fatalException(new IOException("The file " + copyName + " could not be copied to "
						+ "the output file, and MeBoy.jar will not function correctly."));
			}

			zo.putNextEntry(new ZipEntry(copyName));
			byte[] buf = new byte[100000];
			int r = tis.read(buf, 0, 100000);
			while (r >= 0) {
				if (r > 0)
					zo.write(buf, 0, r);
				r = tis.read(buf, 0, 100000);
			}
			tis.close();
		}
		// optional files (lang):
		for (int i = 1; i < 25; i++) {
			String copyName = "lang/" + i + ".txt";
			InputStream tis = ClassLoader.getSystemClassLoader().getResourceAsStream(copyName);

			if (tis == null) {
				continue;
			}

			zo.putNextEntry(new ZipEntry(copyName));
			byte[] buf = new byte[100000];
			int r = tis.read(buf, 0, 100000);
			while (r >= 0) {
				if (r > 0)
					zo.write(buf, 0, r);
				r = tis.read(buf, 0, 100000);
			}
			tis.close();
		}

		// add carts
		zo.putNextEntry(new ZipEntry("carts"));

		DataOutputStream dos = new DataOutputStream(zo);
		dos.writeInt(cartsTxt.size() / 3);
		for (String s : cartsTxt)
			dos.writeUTF(s);
		dos.flush();

		// add manifest
		zo.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
		PrintWriter pw = new PrintWriter(zo);
		pw.write("MIDlet-1: " + midletName + ", meboy.png, MeBoy\n");
		pw.write("MIDlet-Name: " + midletName + "\n");
		pw.write("MIDlet-Vendor: Bjorn Carlin, www.arktos.se\n");
		pw.write("MIDlet-Version: 2.0.0\n");
		pw.write("MIDlet-Description: Gameboy emulator for J2ME\n");
		pw.write("MicroEdition-Configuration: CLDC-1.0\n");
		pw.write("MicroEdition-Profile: MIDP-2.0\n");
		pw.flush();
		zo.close();

		// create .jad
		pw = new PrintWriter(new FileWriter(new File(f.getParent(), midletName + ".jad")));
		pw.write("MIDlet-1: " + midletName + ", meboy.png, MeBoy\n");
		pw.write("MIDlet-Name: " + midletName + "\n");
		pw.write("MIDlet-Vendor: Bjorn Carlin, www.arktos.se\n");
		pw.write("MIDlet-Version: 2.0.0\n");
		pw.write("MIDlet-Description: Gameboy emulator for J2ME\n");
		pw.write("MicroEdition-Configuration: CLDC-1.0\n");
		pw.write("MicroEdition-Profile: MIDP-2.0\n");
		pw.write("MIDlet-Jar-URL: " + midletName + ".jar\n");
		pw.write("MIDlet-Jar-Size: " + f.length() + '\n');
		pw.close();

		dirty = false;

		JOptionPane.showMessageDialog(null, string2JTA("The files " + midletName + ".jar and "
				+ midletName + ".jad have been created.\n"
				+ "Most phones only require a \".jar\" file, so try to transfer " + midletName
				+ ".jar to your phone.\n" + "- If your phone accepted " + midletName
				+ ".jar, you do not need " + midletName + ".jad and can safely throw it away.\n"
				+ "- If your phone requires a \".jad\" file, transfer " + midletName
				+ ".jad instead."), "MeBoyBuilder Finished", JOptionPane.INFORMATION_MESSAGE);
	}
	
	private static void fatalException(Exception ex) {
		ex.printStackTrace();
		String msg = "Unfortunately, an error has ocurred that prevents MeBoyBuilder" +
				" from continuing.\n";
		int choice = JOptionPane.showOptionDialog(null, msg, "MeBoy Error", JOptionPane.YES_NO_OPTION,
					JOptionPane.ERROR_MESSAGE, null, new String[] {"Quit", "More Info"}, "Quit");
		if (choice == 1) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			JScrollPane p = new JScrollPane(string2JTA(msg + sw.toString()));
			p.setPreferredSize(new Dimension(800, 600));
			JOptionPane.showMessageDialog(null, p);
		}
		System.exit(0);
	}

	static void nonFatalException(Exception ex, String message) {
		ex.printStackTrace();
		int choice = JOptionPane.showOptionDialog(null, message, "MeBoy Error", JOptionPane.YES_NO_OPTION,
					JOptionPane.ERROR_MESSAGE, null, new String[] {"Close", "More Info"}, "Close");
		if (choice == 1) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			JScrollPane p = new JScrollPane(string2JTA(message + sw.toString()));
			p.setPreferredSize(new Dimension(800, 600));
			JOptionPane.showMessageDialog(null, p);
		}
	}
	
	private static void checkForUpdates() {
		Preferences prefs = Preferences.userNodeForPackage(MeBoyBuilder.class).node("meboybuilder");
		String checkPrefs = prefs.get("CheckUpdates", null);

		boolean firstLaunch = checkPrefs == null;

		if (firstLaunch) {
			int response = JOptionPane
					.showOptionDialog(
							null,
							string2JTA("Do you want MeBoyBuilder to automatically check for program updates when launched?"),
							"MeBoyBuilder 2.0", 0, JOptionPane.PLAIN_MESSAGE, null, new Object[] {
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
			long lastTime = prefs.getLong("LastTime", now - 1000 * 60 * 60 * 24);
			if (lastTime + 1000 * 60 * 60 * 24 > now) {
				return; // less than a day since last check
			}
			prefs.putLong("LastTime", now);

			try {
				// read info from home page
				URL inputURL = new URL("http://arktos.se/meboy/update/200.txt");
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
							string2JTA(message.toString()), "MeBoyBuilder 2.0", 0,
							JOptionPane.PLAIN_MESSAGE, null, new Object[] { "Cancel",
									"Visit homepage"}, "Visit homepage");

					if (response == 1) {
						openURL("http://arktos.se/meboy/");
					}
				}
			} catch (Exception e) {
				// not really important, just can't check for updates...
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
	private static void openURL(String url) throws Exception {
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

	public void windowActivated(WindowEvent e) {}

	public void windowClosed(WindowEvent e) {}

	public void windowDeactivated(WindowEvent e) {}

	public void windowDeiconified(WindowEvent e) {}

	public void windowIconified(WindowEvent e) {}

	public void windowOpened(WindowEvent e) {}
}

class Game implements Comparable {
	String displayName;
	String cartID;
	String fileName;
	byte[] data;
	String label() {
		return displayName + ", " + data.length / 1024 + " kB";
	}
	
	public int compareTo(Object o) {
		return displayName.toLowerCase().compareTo(((Game) o).displayName.toLowerCase());
	}
}
