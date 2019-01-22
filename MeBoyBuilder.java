/*
 * Created on Jul 22, 2005
 */
import java.io.*;
import java.util.zip.*;
import javax.swing.*;

/**
 * A quick-and-dirty class for building the MeBoy.jar file.
 * It will copy the contents of its own jar (except MeBoyBuilder.class)
 * and replace the manifest with the file MEMANIFEST.
 * 
 * It will also add the selected roms, and enumerate them in the
 * carts.txt file.
 * 
 * This is not unicode-savvy, but neither are ROMs, usually.
 */
public class MeBoyBuilder {
	static ZipOutputStream zo;
	

	public static void main(String[] arg) {
		try {
			zo = new ZipOutputStream(new FileOutputStream("MeBoy.jar"));

			// add MeBoy
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
			StringBuffer games = new StringBuffer();
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select ROM file");
			while (true) {
				int returnVal = chooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					
					String fileName = f.getName();
					if (fileName.length() > 31)
						fileName = fileName.substring(0, 31);
					
					games.append(fileName).append('\n');
					FileInputStream fi = new FileInputStream(f);
					
					zo.putNextEntry(new ZipEntry(fileName));
					r = fi.read(buf, 0, 65536);
					while (r >= 0) {
						if (r > 0)
							zo.write(buf, 0, r);
						r = fi.read(buf, 0, 65536);
					}
					fi.close();
				} else
					break;
			}
			if (games.length() == 0)
				JOptionPane.showMessageDialog(null, "Please note that MeBoy can't run without a ROM file.", "No carts", JOptionPane.INFORMATION_MESSAGE);

			// add carts.txt
			zo.putNextEntry(new ZipEntry("carts.txt"));
			PrintWriter pw = new PrintWriter(zo);
			pw.println(games.toString());
			pw.close();
			zo.close();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error has occurred (" + e + ").", "Error", JOptionPane.ERROR_MESSAGE);
		}
		System.exit(0);
	}

}
