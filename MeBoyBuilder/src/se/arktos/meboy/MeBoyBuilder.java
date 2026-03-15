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
import javax.swing.filechooser.FileFilter;

/**
 * A class for building the MeBoy.jar and .jad files.
 * It will copy the relevant contents of its own jar
 * and insert a new manifest file.
 * 
 * It will also add roms, and enumerate them in the
 * carts file.
 */
public class MeBoyBuilder implements ActionListener, ListSelectionListener, WindowListener {
	private static final String APP_TITLE = "MeBoyBuilder 2.3";
	private static final int DEFAULT_ICON_INDEX = 2;
	private static final int CUSTOM_ICON_INDEX = 3;
	private static final int ICON_MIN_SIZE = 16;
	private static final int ICON_MAX_SIZE = 80;

	public static void main(String[] args) {
		checkForUpdates();
		new MeBoyBuilder();
	}

	static Bluetooth bluetoothInstance;
	static ImageIcon optionIcon;
	
	JFrame frame = new JFrame(APP_TITLE);
	JButton addGame = new JButton("Add game");
	JButton removeGame = new JButton("Remove game");
	JButton renameGame = new JButton("Rename game");
	JButton createJar = new JButton("Create MeBoy.jar");
	JButton bluetooth = new JButton("Bluetooth");

	byte[][] iconData = new byte[4][];
	ImageIcon[] icon = new ImageIcon[4];
	JRadioButton[] iconButton = {new JRadioButton("small"), new JRadioButton("medium"), new JRadioButton("large"), new JRadioButton("other...")};
	JLabel iconLabel = new JLabel();
	int selectedIcon = DEFAULT_ICON_INDEX; // large
	
	ArrayList<Game> games = new ArrayList<Game>();
	
	JFileChooser chooser = new JFileChooser();
	JFileChooser iconChooser = new JFileChooser();
	
	JPanel content = new JPanel(new BorderLayout(4, 4));
	JList<String> list = new JList<String>();
	JScrollPane jsp = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

	boolean dirty;

	private MeBoyBuilder() {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize(); 
		frame.setBounds(screen.width / 2 - 200, screen.height / 2 - 200, 400, 400);

		jsp.setPreferredSize(new Dimension(200, 200));
		content.add(jsp, BorderLayout.CENTER);

		JPanel eastPanel = new JPanel(new BorderLayout(4, 4));
		JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 4, 4));
		JPanel belowButtonPanel = new JPanel(new BorderLayout(4, 4));

		// buttons
		addGame.addActionListener(this);
		removeGame.addActionListener(this);
		renameGame.addActionListener(this);
		createJar.addActionListener(this);
		bluetooth.addActionListener(this);
		
		try {
			// Test if there's a Bluetooth stack we can use:
			LocalDevice.getLocalDevice();
		} catch (Throwable t) {
			bluetooth.setEnabled(false);
		}

		buttonPanel.add(addGame);
		buttonPanel.add(removeGame);
		buttonPanel.add(renameGame);
		buttonPanel.add(createJar);
		eastPanel.add(buttonPanel, BorderLayout.NORTH);
		eastPanel.add(belowButtonPanel, BorderLayout.CENTER);
		belowButtonPanel.add(bluetooth, BorderLayout.SOUTH);
		
		// icons
		iconChooser.setFileFilter(new ImageFilter());
		JPanel iconPanel = new JPanel(new BorderLayout(4, 4));
		iconPanel.setBorder(new TitledBorder("Icon"));
		JPanel radioButtonPanel = new JPanel(new GridLayout(0, 1, 4, 4));
		
		try {
			for (int i = 0; i < 3; i++) {
				iconData[i] = readAll(ClassLoader.getSystemClassLoader().getResourceAsStream("meboy" + i + ".png"));
				icon[i] = new ImageIcon(iconData[i]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		optionIcon = icon[DEFAULT_ICON_INDEX];
		ButtonGroup g = new ButtonGroup();
		for (JRadioButton b : iconButton) {
			g.add(b);
			radioButtonPanel.add(b);
			b.addActionListener(this);
		}
		iconButton[selectedIcon].setSelected(true);
		iconLabel.setIcon(icon[selectedIcon]);
		iconPanel.add(iconLabel);
		iconPanel.add(radioButtonPanel, BorderLayout.WEST);
		belowButtonPanel.add(iconPanel, BorderLayout.NORTH);

		content.setBorder(new EmptyBorder(4, 4, 4, 4));
		content.add(eastPanel, BorderLayout.EAST);

		JScrollPane jsp2 = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		frame.getContentPane().add(jsp2);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(this);

		chooser.setDialogTitle("Select ROM file");
		iconChooser.setDialogTitle("Select icon");

		updateList();
	}

	private void updateList() {
		String[] names = new String[games.size()];
		
		int i = 0;
		for (Game g : games) {
			names[i++] = g.label(); 
		}
		
		list = new JList<String>(names);
		list.addListSelectionListener(this);
		jsp.setViewportView(list);
		content.revalidate();
		valueChanged(null);
	}

	private int getSelectedGameIndex() {
		int selectedIndex = list.getSelectedIndex();
		if (selectedIndex < 0) {
			return -1;
		}
		return selectedIndex;
	}

	public void valueChanged(ListSelectionEvent e) {
		removeGame.setEnabled(list.getSelectedValue() != null);
		renameGame.setEnabled(list.getSelectedValue() != null);
		// Allow creating a MeBoy.jar with no bundled games.
		createJar.setEnabled(true);
	}

	public void windowClosing(WindowEvent e) {
		if (!dirty)
			System.exit(0);

		String q = "Do you want to discard changes and quit? A MeBoy.jar file " +
			"has not yet been created with the games you added.";

		int response = JOptionPane.showOptionDialog(frame, string2JTA(q), APP_TITLE, 0,
				JOptionPane.PLAIN_MESSAGE, icon[DEFAULT_ICON_INDEX], new Object[] { "Quit", "Cancel"}, "Quit");

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
			int i = getSelectedGameIndex();
			if (i < 0)
				return;
			games.remove(i);
			dirty = true;
			updateList();
		} else if (e.getSource() == renameGame) {
			int i = getSelectedGameIndex();
			if (i < 0)
				return;
			
			Game g = games.get(i);

			String s = (String) JOptionPane.showInputDialog(frame,
					"Please enter a new name (max 24 characters):", "Enter name", JOptionPane.QUESTION_MESSAGE, icon[2], null, g.displayName);
			
			if (s == null || s.trim().length() < 1)
				return;
			s = cleanName(s);
			if (g.displayName.equals(s))
				return;
			
			if (findByDisplayName(s) != null) {
				JOptionPane.showMessageDialog(frame,
					string2JTA("Please select a unique name."), APP_TITLE,
					JOptionPane.PLAIN_MESSAGE, icon[DEFAULT_ICON_INDEX]);
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
		for (int i = 0; i < icon.length - 1; i++) {
			if (e.getSource() == iconButton[i]) {
				selectedIcon = i;
				iconLabel.setIcon(icon[i]);
			}
		}
		if (e.getSource() == iconButton[CUSTOM_ICON_INDEX]) {
			int returnVal = iconChooser.showOpenDialog(frame);
			if (returnVal != JFileChooser.APPROVE_OPTION) {
				iconButton[selectedIcon].setSelected(true);
				return;
			}
			
			try {
				File f = iconChooser.getSelectedFile();
				System.out.println(f);
				selectedIcon = CUSTOM_ICON_INDEX;
				ImageIcon i = new ImageIcon(f.getCanonicalPath());
				if (i.getIconHeight() > ICON_MAX_SIZE || i.getIconHeight() < ICON_MIN_SIZE
						|| i.getIconWidth() > ICON_MAX_SIZE || i.getIconWidth() < ICON_MIN_SIZE)
					throw new RuntimeException(i.getIconHeight() + " " + i.getIconWidth());
				
				iconData[CUSTOM_ICON_INDEX] = readAll(new FileInputStream(f));
				icon[CUSTOM_ICON_INDEX] = i;
				iconLabel.setIcon(icon[CUSTOM_ICON_INDEX]);
			} catch (Exception ex) {
				iconButton[1].setSelected(true);
				iconLabel.setIcon(icon[1]);
				ex.printStackTrace();
				JOptionPane.showMessageDialog(frame, "Please select an image file with a size between 16x16 and 80x80 pixels.",
						"MeBoy Error", JOptionPane.ERROR_MESSAGE, icon[DEFAULT_ICON_INDEX]);
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
	
	private byte[] readAll(InputStream is) throws IOException {
		int bufIndex = 0;
		byte[] buffer = new byte[4 << 20];
		int r;
		do {
			r = is.read(buffer, bufIndex, buffer.length - bufIndex);
			if (r > 0)
				bufIndex += r;
		} while (bufIndex < buffer.length && r >= 0);
		is.close();
		byte[] result = new byte[bufIndex];
		System.arraycopy(buffer, 0, result, 0, bufIndex);
		return result;
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
			// Replace the name with the first file-like entry in the zip.
			ZipEntry entry = zin.getNextEntry();
			while (entry != null && entry.isDirectory()) {
				entry = zin.getNextEntry();
			}
			if (entry == null) {
				throw new IOException("ZIP file does not contain a ROM file.");
			}
			fileAndDisplayName = entry.getName();
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
		byte[] buffer = readAll(fi);

		// do sanity check
		boolean sane = true;
		int headerOffset = 0;

		if (buffer.length < 0x200 || buffer.length < f.length()) {
			error = uncleanName + " does not seem to be a valid ROM file, and it will not be added.";
			System.out.println("length: " + buffer.length);
			sane = false;
		}

		// sanity check the cartridge file
		if (sane) {
			if (!hasValidHeaderAt(buffer, 0) && hasValidHeaderAt(buffer, 512)) {
				// Some old ROM dumps include a 512-byte copier header.
				headerOffset = 512;
			}
		}

		int cartSize = sane ? lookUpCartSize(buffer[headerOffset + 0x0148] & 0xff) : -1;
		if (sane && (cartSize == -1 || !hasValidHeaderAt(buffer, headerOffset))) {
			error = uncleanName + " does not seem to be a valid ROM file, and it will not be added.";
			System.out.println("cartSize " + cartSize);
			System.out.println("104 " + buffer[headerOffset + 0x0104]);
			System.out.println("10d " + buffer[headerOffset + 0x010d]);
			System.out.println("118 " + buffer[headerOffset + 0x0118]);
			System.out.println("148 " + buffer[headerOffset + 0x0148]);
			sane = false;

			if (uncleanName.endsWith(".gba"))
				error += " Please note that MeBoy does not emulate the Gameboy Advance.";
			if (uncleanName.endsWith(".nds"))
				error += " Please note that MeBoy does not emulate the Nintendo DS.";
			if (uncleanName.endsWith(".zip"))
				error += " If this is a ZIP, ensure it contains a single GB/GBC ROM file.";
		}

		String identifier = "";
		if (sane) {
			for (int i = headerOffset + 0x134; i < headerOffset + 0x143; i++)
				if (buffer[i] >= 31 && buffer[i] < 128)
					identifier = identifier + (char) buffer[i];
			if (findByCartID(identifier) != null) {
				sane = false;
				error = "This game has already been added (" + findByCartID(identifier).displayName + ").";
			}
		}
		
		if (sane) {
			Game g = new Game();
			g.data = buffer;
			g.displayName = fileAndDisplayName;
			g.fileName = fileAndDisplayName;
			g.cartID = identifier;
			games.add(g);
			Collections.sort(games);
			dirty = true;
		} else {
			JOptionPane.showMessageDialog(null, string2JTA(error), "MeBoyBuilder warning",
					JOptionPane.WARNING_MESSAGE, icon[DEFAULT_ICON_INDEX]);
		}
	}

	private boolean hasValidHeaderAt(byte[] buffer, int offset) {
		if (buffer.length <= offset + 0x148) {
			return false;
		}
		return (buffer[offset + 0x0104] == (byte) 0xCE)
				&& (buffer[offset + 0x010d] == (byte) 0x73)
				&& (buffer[offset + 0x0118] == (byte) 0x88);
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

			int response = JOptionPane.showOptionDialog(frame, string2JTA(q), APP_TITLE, 0,
					JOptionPane.PLAIN_MESSAGE, icon[DEFAULT_ICON_INDEX], new Object[] { a1, a2}, a1);

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

			int parts = Math.max(1, (g.data.length + maxFileSize - 1) / maxFileSize);
			for (int j = 0; j < parts; j++) {
				zo.putNextEntry(new ZipEntry("meboy/" + g.cartID + j));
				zo.write(g.data, j * maxFileSize, Math.min(maxFileSize, g.data.length - j * maxFileSize));
			}
		}

		// copy MeBoy files
		String[] files = new String[] {
				"meboy/AdvancedGraphicsChip.class",
				"meboy/Bluetooth.class",
				"meboy/Dmgcpu.class",
				"meboy/GBCanvas.class",
				"meboy/GraphicsChip.class",
				"meboy/MeBoy.class",
				"meboy/SimpleGraphicsChip.class",
				"lang/index.txt", "lang/0.txt"};
		for (String copyName : files) {
			InputStream tis = openResourceOrFile(copyName);

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
		// optional anonymous inner classes generated for meboy.MeBoy
		for (int i = 1; i < 20; i++) {
			String copyName = "meboy/MeBoy$" + i + ".class";
			InputStream tis = openResourceOrFile(copyName);
			if (tis == null)
				continue;
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
			InputStream tis = openResourceOrFile(copyName);

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

		// add icon
		zo.putNextEntry(new ZipEntry("meboy.png"));
		zo.write(iconData[selectedIcon], 0, iconData[selectedIcon].length);
		
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
		pw.write("MIDlet-1: " + midletName + ", meboy.png, meboy.MeBoy\n");
		pw.write("MIDlet-Name: " + midletName + "\n");
		pw.write("MIDlet-Vendor: Bjorn Carlin, www.arktos.se\n");
		pw.write("MIDlet-Version: 2.3.0\n");
		pw.write("MIDlet-Description: Gameboy emulator for J2ME\n");
		pw.write("MicroEdition-Configuration: CLDC-1.1\n");
		pw.write("MicroEdition-Profile: MIDP-2.0\n");
		pw.write("MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write\n");
		pw.flush();
		zo.close();

		// create .jad
		pw = new PrintWriter(new FileWriter(new File(f.getParent(), midletName + ".jad")));
		pw.write("MIDlet-1: " + midletName + ", meboy.png, meboy.MeBoy\n");
		pw.write("MIDlet-Name: " + midletName + "\n");
		pw.write("MIDlet-Vendor: Bjorn Carlin, www.arktos.se\n");
		pw.write("MIDlet-Version: 2.3.0\n");
		pw.write("MIDlet-Description: Gameboy emulator for J2ME\n");
		pw.write("MicroEdition-Configuration: CLDC-1.1\n");
		pw.write("MicroEdition-Profile: MIDP-2.0\n");
		pw.write("MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write\n");
		pw.write("MIDlet-Jar-URL: " + midletName + ".jar\n");
		pw.write("MIDlet-Jar-Size: " + f.length() + '\n');
		pw.close();

		dirty = false;

		JOptionPane.showMessageDialog(frame, string2JTA("The files " + midletName + ".jar and "
				+ midletName + ".jad have been created.\n"
				+ "Most phones only require a \".jar\" file, so try to transfer " + midletName
				+ ".jar to your phone.\n" + "- If your phone accepted " + midletName
				+ ".jar, you do not need " + midletName + ".jad and can safely throw it away.\n"
				+ "- If your phone requires a \".jad\" file, transfer " + midletName
				+ ".jad instead."), "MeBoyBuilder Finished", JOptionPane.INFORMATION_MESSAGE, icon[DEFAULT_ICON_INDEX]);
	}

	private InputStream openResourceOrFile(String name) throws IOException {
		InputStream resource = ClassLoader.getSystemClassLoader().getResourceAsStream(name);
		if (resource != null) {
			return resource;
		}
		InputStream rootResource = MeBoyBuilder.class.getResourceAsStream("/" + name);
		if (rootResource != null) {
			return rootResource;
		}

		String[] fileCandidates = new String[] {
				name,
				"build/compiled/" + name,
				"build/preverified/" + name,
				"resources/" + name,
				"MeBoy/build/compiled/" + name,
				"MeBoy/build/preverified/" + name,
				"MeBoy/resources/" + name,
				"../MeBoy/build/compiled/" + name,
				"../MeBoy/build/preverified/" + name,
				"../MeBoy/resources/" + name
		};
		for (String candidate : fileCandidates) {
			File file = new File(candidate);
			if (file.exists() && file.isFile()) {
				return new FileInputStream(file);
			}
		}

		String[] jarCandidates = new String[] {
				"dist/MeBoy.jar",
				"MeBoy/dist/MeBoy.jar",
				"../MeBoy/dist/MeBoy.jar"
		};
		for (String jarPath : jarCandidates) {
			InputStream jarEntry = openJarEntry(jarPath, name);
			if (jarEntry != null) {
				return jarEntry;
			}
		}

		return null;
	}

	private InputStream openJarEntry(String jarPath, String entryName) throws IOException {
		File jarFile = new File(jarPath);
		if (!jarFile.exists() || !jarFile.isFile())
			return null;

		final ZipFile zip = new ZipFile(jarFile);
		ZipEntry entry = zip.getEntry(entryName);
		if (entry == null) {
			zip.close();
			return null;
		}
		final InputStream entryStream = zip.getInputStream(entry);
		return new FilterInputStream(entryStream) {
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					zip.close();
				}
			}
		};
	}
	
	private static void fatalException(Exception ex) {
		ex.printStackTrace();
		String msg = "Unfortunately, an error has ocurred that prevents MeBoyBuilder" +
				" from continuing.\n";
		int choice = JOptionPane.showOptionDialog(null, msg, "MeBoy Error", JOptionPane.YES_NO_OPTION,
					JOptionPane.ERROR_MESSAGE, optionIcon, new String[] {"Quit", "More Info"}, "Quit");
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
					JOptionPane.ERROR_MESSAGE, optionIcon, new String[] {"Close", "More Info"}, "Close");
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
							APP_TITLE, 0, JOptionPane.PLAIN_MESSAGE, optionIcon, new Object[] {
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
				URL inputURL = new URL("http://arktos.se/meboy/update/210.txt");
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
							string2JTA(message.toString()), APP_TITLE, 0,
							JOptionPane.PLAIN_MESSAGE, optionIcon, new Object[] { "Cancel",
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

class Game implements Comparable<Game> {
	String displayName;
	String cartID;
	String fileName;
	byte[] data;
	String label() {
		return displayName + ", " + data.length / 1024 + " kB";
	}
	
	public int compareTo(Game o) {
		return displayName.toLowerCase().compareTo(o.displayName.toLowerCase());
	}
}

class ImageFilter extends FileFilter {
	public boolean accept(File f) {
		return f.isDirectory() || f.getName().toLowerCase().endsWith(".png") || f.getName().toLowerCase().endsWith(".gif") ||
				f.getName().toLowerCase().endsWith(".jpg") || f.getName().toLowerCase().endsWith(".jpeg");
	}

	public String getDescription() {
		return "PNG/GIF/JPG Files";
	}
}
