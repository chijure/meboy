/*
 * Created on Jul 22, 2005
 */
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

/**
 * A quick-and-dirty class for building the MeBoy.jar and .jad files.
 * It will copy the contents of its own jar (except MeBoyBuilder.class)
 * and replace the manifest with the file MEMANIFEST.
 * 
 * It will also add the selected roms, and enumerate them in the
 * carts.txt file.
 */
public class MeBoyBuilder {
	public static void main(String[] arg) {
		try {
			ZipOutputStream zo = new ZipOutputStream(new FileOutputStream("MeBoy.jar"));

			// add MeBoy and manifest info
			ZipInputStream zi = new ZipInputStream(new FileInputStream("MeBoyBuilder.jar"));

			ZipEntry e;

			byte[] buf = new byte[65536];
			int r;
			e = zi.getNextEntry();
			while (e != null) {
				if (e.getName().equals("META-INF/MANIFEST.MF") || e.getName().equals("MeBoyBuilder.class")) {
					e = zi.getNextEntry();
					continue;
				} else if (e.getName().equals("MEMANIFEST")) {
					e = new ZipEntry("META-INF/MANIFEST.MF");
				}
				zo.putNextEntry(e);
				
				r = zi.read(buf, 0, 65536);
				while (r >= 0) {
					if (r > 0)
						zo.write(buf, 0, r);
					r = zi.read(buf, 0, 65536);
				}
				e = zi.getNextEntry();
			}
			zi.close();

			// add games
			ArrayList gameFiles = new ArrayList();
			int gamesSize = 0;
			StringBuffer games = new StringBuffer();
			StringBuffer gamesCommaSeparated = new StringBuffer();
			int gameCount = 0;
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select ROM file");
			while (true) {
				StringBuffer message = new StringBuffer();
				message.append("Currently ").append(gameCount).append(" ROM file").append((gameCount == 1 ? "" : "s"));
				if (gameCount > 0)
					message.append(" (").append(gamesCommaSeparated).append(gamesSize).append(" kB uncompressed).\nAdd another ROM file, or finish building MeBoy.jar?");
				else
					message.append(". Add a ROM file, or finish building MeBoy.jar?");
				
				int response = JOptionPane.showOptionDialog(null, string2JTA(message.toString()),
						"MeBoyBuilder 1.2", 0, JOptionPane.PLAIN_MESSAGE, null, new Object[] {"Add ROM", "Finish"}, "Add ROM");
				
				if (response == 1 || response == -1) {
					break;
				}
				
				int returnVal = chooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					
					String fileName = f.getName();
					if (fileName.length() > 31)
						fileName = fileName.substring(0, 31);
					
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
					
					// copy file
					FileInputStream fi = new FileInputStream(f);
					zo.putNextEntry(new ZipEntry(fileName));
					r = fi.read(buf, 0, 65536);
					
					boolean color = (buf[0x0143] & 0x80) != 0;
					
					while (r >= 0) {
						if (r > 0)
							zo.write(buf, 0, r);
						r = fi.read(buf, 0, 65536);
					}
					fi.close();
					
					// check size
					int size = (int) (f.length() / 1024);
					gamesSize += size;
					
					if (color)
						JOptionPane.showMessageDialog(null, string2JTA("Please note that " + fileName + " appears to require a " +
								"Gameboy Color. MeBoy does not emulate a Gameboy Color, and the game will probably not function correctly."),
								"MeBoyBuilder warning", JOptionPane.WARNING_MESSAGE);
					else if (size > 512)
						JOptionPane.showMessageDialog(null, string2JTA("Please note that " + fileName + " is a very large ROM file, " +
								"and will not run unless your phone has plenty of RAM."), "MeBoyBuilder warning", JOptionPane.WARNING_MESSAGE);
					
					gameCount++;
				}
			}

			// add carts.txt
			zo.putNextEntry(new ZipEntry("carts.txt"));
			PrintWriter pw = new PrintWriter(zo);
			pw.println(games.toString());
			pw.close();
			zo.close();
			
			// create MeBoy.jad
			FileWriter fw = new FileWriter("MeBoy.jad");
			fw.write("MIDlet-Name: MeBoy\n");
			fw.write("MIDlet-Version: 1.2.0\n");
			fw.write("MIDlet-Vendor: Bjorn Carlin\n");
			fw.write("MicroEdition-Profile: MIDP-2.0\n");
			fw.write("MicroEdition-Configuration: CLDC-1.0\n");
			fw.write("MIDlet-Jar-URL: MeBoy.jar\n");
			fw.write("MIDlet-Jar-Size: " + new File("MeBoy.jar").length() + '\n');
			fw.write("MIDlet-Description: Gameboy emulator for J2ME\n");
			fw.write("MIDlet-1: MeBoy, , MeBoy\n");
			fw.close();
			
			if (gameCount == 0)
				JOptionPane.showMessageDialog(null, string2JTA("Please note that MeBoy requires at least one ROM file to " +
						"do anything remotely exciting."), "No carts", JOptionPane.INFORMATION_MESSAGE);
			else
				JOptionPane.showMessageDialog(null, string2JTA("The files MeBoy.jar and MeBoy.jad have been created (in the same directory as MeBoyBuilder.jar).\n" +
						"Most phones only require a \".jar\" file, so try to transfer MeBoy.jar to your phone.\n" +
						"- If your phone accepted MeBoy.jar, you do not need MeBoy.jad and can safely throw it away.\n" +
						"- If your phone requires a \".jad\" file, transfer MeBoy.jad instead."),
						"MeBoyBuilder Finished", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error has occurred (" + e + ").", "Error", JOptionPane.ERROR_MESSAGE);
		}
		System.exit(0);
	}

	private static JTextArea string2JTA(String message) {
		JTextArea jta = new JTextArea(message, 1+message.length()/40, 40);
		
		jta.setLineWrap(true);
		jta.setWrapStyleWord(true);
		jta.setEditable(false);
		jta.setBackground(new java.awt.Color(0, true));
		return jta;
	}
}
