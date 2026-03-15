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
import java.util.*;

import javax.bluetooth.*;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;


public class Bluetooth implements Runnable, DiscoveryListener, WindowListener, ActionListener,
		ListSelectionListener {
	private static final UUID MeBoyUUID = new UUID("ceb15f4a19eb4db28d1c40eb0f091607", false);
	// 0x1101 is the UUID for the Serial Port Profile
	private static final UUID[] uuidSet = { new UUID(0x1101)};
	// 0x0100 is the attribute for the service name element in the service record
	private static final int[] attrSet = { 0x0100};

	private String connectionURL;
	private JFileChooser chooser = new JFileChooser();

	JFrame frame = new JFrame("MeBoyBuilder - Bluetooth");

	private JProgressBar receiveProgress = new JProgressBar();
	private JLabel receiveProgressLabel = new JLabel("");

	private JProgressBar sendProgress = new JProgressBar();
	private JLabel sendProgressLabel = new JLabel("Scanning for devices...");

	private ArrayList<RemoteDevice> remoteDevices = new ArrayList<RemoteDevice>();
	private JList deviceList = new JList();
	private JButton sendButton = new JButton("Send savegame");
	private boolean readyToSend = false;
	private byte[] sendBuffer;
	private String sendFilename;

	private int threadCounter;
	private Thread receiverThread;
	private DiscoveryAgent discoveryAgent;
	private boolean discoveryAgentIsInquiring;
	private boolean discoveryAgentIsScanning;
	private int discoveryAgentId;


	public Bluetooth() {
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		JPanel contents = new JPanel(new BorderLayout(4, 4));
		frame.getContentPane().add(contents);
		contents.setBorder(new EmptyBorder(4, 4, 4, 4));

		JPanel receivePanel = new JPanel(new BorderLayout(4, 4));
		contents.add(receivePanel, BorderLayout.NORTH);

		receivePanel.add(receiveProgress, BorderLayout.WEST);
		receiveProgress.setIndeterminate(true);
		receivePanel.add(receiveProgressLabel);

		JPanel sendPanel = new JPanel(new BorderLayout(4, 4));
		contents.add(sendPanel);

		JPanel sendStatusPanel = new JPanel(new BorderLayout(4, 4));
		sendPanel.add(sendStatusPanel, BorderLayout.NORTH);

		sendStatusPanel.add(sendProgress, BorderLayout.WEST);
		sendProgress.setIndeterminate(true);
		sendStatusPanel.add(sendProgressLabel);

		sendPanel.add(deviceList, BorderLayout.CENTER);
		deviceList.addListSelectionListener(this);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sendPanel.add(buttonPanel, BorderLayout.EAST);
		sendButton.addActionListener(this);
		sendButton.setEnabled(false);
		buttonPanel.add(sendButton);

		frame.pack();
		frame.setBounds(300, 180, 500, 300);
		frame.setVisible(true);
		frame.addWindowListener(this);
		MeBoyBuilder.bluetoothInstance = this;
		
		new Thread(this).start();
	}

	public void run() {
		threadCounter++;
		if (threadCounter == 1) {
			new Thread(this).start();
			
			try {
				LocalDevice localDevice = LocalDevice.getLocalDevice();
				discoveryAgent = localDevice.getDiscoveryAgent();
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
				discoveryAgentIsInquiring = true;
			} catch (Exception e) {
				MeBoyBuilder.nonFatalException(e, "MeBoyBuilder failed scanning for Bluetooth devices.");
				sendProgress.setIndeterminate(false);
				sendProgressLabel.setText("Failed scanning for Bluetooth devices.");
			}
		} else if (threadCounter == 2) {
			receiverThread = Thread.currentThread();
			receiveMainloop();
		} else {
			send();
		}
	}

	private void send() {
		try {
			sendProgress.setValue(0);
			sendProgress.setMaximum(100);
			sendProgress.setIndeterminate(false);
			
			boolean signatureMatch = true;
			String signature = "MeBoy20S";
			for (int i = 0; i < signature.length(); i++)
				if (signature.charAt(i) != sendBuffer[i])
					signatureMatch = false;
			
			if (!signatureMatch && (sendBuffer.length % 0x2000 != 0)) {
				MeBoyBuilder.nonFatalException(new IOException(), "The file you selected is not a valid savegame file.");
				readyToSend = true;
				sendButton.setEnabled(deviceList.getSelectedValue() != null);
				sendProgressLabel.setText("Could not send.");
				return;
			}
			
			StreamConnection connection = (StreamConnection) Connector.open(connectionURL);
			DataInputStream is = connection.openDataInputStream();
			DataOutputStream os = connection.openDataOutputStream();
			os.writeUTF("MeBoy20");
			
			int ix = 0;
			if (signatureMatch) {
				os.writeUTF("s"); // suspended game
				os.flush();
				String r = is.readUTF();
				if (!r.equals("go")) {
					// weird answer
					throw new IOException("Received unexpected data: " + r);
				}
				String displayName = sendFilename;
				if (displayName.endsWith(".meboy"))
					displayName = displayName.substring(0, displayName.length() - 6);
				String cartID = new String(sendBuffer, 9, sendBuffer[8]); // 8 bytes are signature
				
				os.writeUTF(displayName);
				os.writeUTF(""); // filename unknown
				os.writeUTF(cartID);
				os.writeInt(sendBuffer.length - cartID.length() - 9);
				ix = cartID.length() + 9; // skip signature + cartID when sending bulk
			} else {
				os.writeUTF("r"); // cart ram
				os.flush();
				String r = is.readUTF();
				if (!r.equals("go")) {
					// weird answer
					throw new IOException("Received unexpected data: " + r);
				}
				os.writeUTF(""); // display name unknown
				os.writeUTF(sendFilename);
				os.writeUTF(""); // cartid unknown
				os.writeInt(sendBuffer.length);
			}
			while (ix < sendBuffer.length) {
				int length = Math.min(sendBuffer.length - ix, 512);
				os.write(sendBuffer, ix, length);
				ix += length;
				sendProgress.setValue(95 * ix / sendBuffer.length);
			}
			os.flush();
			String r = is.readUTF();
			if (!r.equals("ok")) {
				// something went wrong :(
				throw new IOException("Received unexpected data: " + r);
			}
			is.close();
			os.close();
			connection.close();
			readyToSend = true;
			sendButton.setEnabled(deviceList.getSelectedValue() != null);
			sendProgressLabel.setText("Finished sending.");
			sendProgress.setValue(100);
		} catch (Exception ex) {
			MeBoyBuilder.nonFatalException(ex, "MeBoyBuilder failed to send the selected file.");
			readyToSend = true;
			sendButton.setEnabled(deviceList.getSelectedValue() != null);
			sendProgressLabel.setText("Could not send.");
		}
	}

	public void receiveMainloop() {
		StreamConnectionNotifier notifier;
		DataInputStream is;
		DataOutputStream os;
		StreamConnection connection = null;

		String serviceURL = "btspp://localhost:" + MeBoyUUID + ";authenticate=false;encrypt=false;name=MeBoy";

		try {
			// create a server connection
			notifier = (StreamConnectionNotifier) Connector.open(serviceURL);
			while (true) {
				receiveProgressLabel.setText("Ready to receive savegame...");
				receiveProgress.setIndeterminate(true);

				// accept client connections
				connection = notifier.acceptAndOpen(); // blocking

				// we have a client when notifier.acceptAndOpen(); returns
				receiveProgressLabel.setText("Connecting...");
				is = connection.openDataInputStream();
				os = connection.openDataOutputStream();
				String protocol = is.readUTF();
				if (!protocol.equals("MeBoy20")) {
					throw new IOException("Received unexpected data: " + protocol);
				}
				os.writeUTF("go");
				os.flush();
				String mode = is.readUTF();
				if (!mode.equals("r") && !mode.equals("s")) {
					throw new IOException("Received unexpected data: " + mode);
				}
				String displayName = is.readUTF(); // display name
				System.out.println("Received displayname: " + displayName);
				String gameFileName = is.readUTF();
				System.out.println("Received gameFileName: " + gameFileName);
				String cartID = is.readUTF(); // cart id
				System.out.println("Received cartID: " + cartID);
				int length = is.readInt();
				byte[] savegame = new byte[length];

				receiveProgress.setValue(0);
				receiveProgress.setMaximum(length);
				receiveProgress.setIndeterminate(false);
				int a;
				int offset = 0;
				while (offset < length && (a = is.read(savegame, offset, length - offset)) != -1) {
					offset += a;
					receiveProgress.setValue(offset);
					receiveProgressLabel.setText("Receiving... " + offset / length * 100 + "%");
				}

				if (offset == length) {
					os.writeUTF("ok");
					os.flush();
					
					if (mode.equals("r")) {
						if (gameFileName.toLowerCase().endsWith(".gb"))
							gameFileName = gameFileName.substring(0, gameFileName.length()-2) + "sav";
						else if (gameFileName.toLowerCase().endsWith(".gbc"))
							gameFileName = gameFileName.substring(0, gameFileName.length()-3) + "sav";
						else if (!gameFileName.toLowerCase().endsWith(".sav"))
							gameFileName += ".sav";
						chooser.setSelectedFile(new File(gameFileName));
						int returnVal = chooser.showSaveDialog(frame);
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							File savegameFile = chooser.getSelectedFile();
							FileOutputStream fos = new FileOutputStream(savegameFile);
							fos.write(savegame, 0, savegame.length & 0xFF000); // strip trailing RTCregs
							fos.close();
						}
					} else {
						chooser.setSelectedFile(new File(displayName + ".meboy"));
						int returnVal = chooser.showSaveDialog(frame);
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							File savegameFile = chooser.getSelectedFile();
							FileOutputStream fos = new FileOutputStream(savegameFile);
							fos.write("MeBoy20S".getBytes());
							fos.write(cartID.length());
							fos.write(cartID.getBytes());
							fos.write(savegame);
							fos.close();
						}
					}
				} else {
					throw new IOException("Received unexpected data: " + offset + "/" + length);
				}
				is.close();
				os.close();
				connection.close();
			}
		} catch (InterruptedIOException iioex) {
			// window closing
		} catch (Exception ex) {
			MeBoyBuilder.nonFatalException(ex, "MeBoyBuilder failed to receive a savegame.");
			receiveProgressLabel.setText("Could not receive.");
			receiveProgress.setValue(0);
		}
	}

	public void deviceDiscovered(RemoteDevice device, DeviceClass dummy) {
		remoteDevices.add(device);
		sendProgressLabel.setText("Scanning for devices... (" + remoteDevices.size() + " found)");
	}

	public void inquiryCompleted(int discType) {
		discoveryAgentIsInquiring = false;
		if (discType != INQUIRY_COMPLETED || remoteDevices.size() == 0) {
			sendProgressLabel.setText("Did not find any Bluetooth devices.");
			sendProgress.setIndeterminate(false);
			return;
		}

		// The discovery process was a success
		Object[] data = new Object[remoteDevices.size()];
		for (int i = 0; i < remoteDevices.size(); i++) {
			String device;
			try {
				device = (remoteDevices.get(i)).getFriendlyName(true);
			} catch (Exception ex) {
				device = (remoteDevices.get(i)).getBluetoothAddress();
			}
			data[i] = device;
		}
		deviceList.setListData(data);
		sendProgress.setIndeterminate(false);
		sendProgressLabel.setText("Found " + data.length + " device"
				+ (data.length == 1 ? "" : "s") + ".");
		readyToSend = true;
	}

	public void serviceSearchCompleted(int transID, int respCode) {
		discoveryAgentIsScanning = false;
		if (connectionURL != null && respCode == DiscoveryListener.SERVICE_SEARCH_COMPLETED) {
			sendProgressLabel.setText("MeBoy found, sending...");
			sendProgress.setIndeterminate(true);
			new Thread(this).start();
		} else {
			sendProgressLabel.setText("Could not connect to MeBoy on the selected device.");
			readyToSend = true;
			sendButton.setEnabled(readyToSend && deviceList.getSelectedValue() != null);
		}
	}

	public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		for (int i = 0; i < servRecord.length; i++) {
			DataElement serviceElement = servRecord[i].getAttributeValue(0x0001);
			Enumeration serviceIDs = (Enumeration) (serviceElement == null ? null : serviceElement
					.getValue());
			if (serviceIDs == null) {
				continue;
			}

			// Check if one of the service's class IDs matches the application's UUID
			while (serviceIDs.hasMoreElements()) {
				UUID serviceID = (UUID) (((DataElement) serviceIDs.nextElement()).getValue());
				if (!serviceID.equals(MeBoyUUID))
					continue;

				// get URL the "canonical" way
				connectionURL = servRecord[i].getConnectionURL(
						ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				if (connectionURL != null && connectionURL.length() > 0)
					return;

				// get URL the "messy but compatible" way, since getConnectionURL can fail
				connectionURL = null;
				long channel = getChannel(servRecord[i].getAttributeValue(0x0004));
				if (channel <= 0)
					break;

				connectionURL = "btspp://" + servRecord[i].getHostDevice().getBluetoothAddress()
						+ ":" + channel + ";authenticate=false;encrypt=false";
				return;
			}
		}
	}

	// code based on an example snippet by "traud"
	// http://developer.sonyericsson.com/message/73104#73104
	static long getChannel(DataElement list) {
		try {
			Enumeration e = (Enumeration) list.getValue(); // DATSEQ | DATALT
			while (e.hasMoreElements()) {
				DataElement d = (DataElement) e.nextElement();
				if (DataElement.DATSEQ == d.getDataType() || DataElement.DATALT == d.getDataType()) {
					long r = getChannel(d);
					if (r > 0) {
						return r;
					}
				} else if (DataElement.UUID == d.getDataType()) {
					if (d.getValue().equals(new UUID(0x0003))) {
						DataElement channelRFCOMM = (DataElement) e.nextElement();
						if (DataElement.U_INT_8 == channelRFCOMM.getDataType()
								|| DataElement.U_INT_16 == channelRFCOMM.getDataType()
								|| DataElement.INT_16 == channelRFCOMM.getDataType()) {
							byte[] bytes = (byte[]) channelRFCOMM.getValue();
							return bytes[0]; // Range (decimal): 1 to 30
						} else {
							return channelRFCOMM.getLong(); // U_INT_1
						}
					}
				}
			}
		} catch (Exception ex) {
		}

		return -1;
	}

	public void windowClosing(WindowEvent e) {
		if (discoveryAgentIsScanning)
			discoveryAgent.cancelServiceSearch(discoveryAgentId);
		else if (discoveryAgentIsInquiring)
			discoveryAgent.cancelInquiry(this);
		receiverThread.interrupt();
		frame.setVisible(false);
		MeBoyBuilder.bluetoothInstance = null;
	}

	public void actionPerformed(ActionEvent e) {
		int returnVal = chooser.showOpenDialog(frame);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;

		readyToSend = false;
		sendButton.setEnabled(false);

		try {
			File f = chooser.getSelectedFile();
			InputStream is = new FileInputStream(f);

			sendFilename = f.getName();
			int bufIndex = 0;
			sendBuffer = new byte[(int) f.length()];
			int r;
			do {
				r = is.read(sendBuffer, bufIndex, sendBuffer.length - bufIndex);
				if (r > 0)
					bufIndex += r;
			} while (bufIndex < sendBuffer.length && r >= 0);

			if (r < 0) {
				throw new IOException("Unexpected end of file: " + bufIndex + "/"
						+ sendBuffer.length);
			}

			is.close();
		} catch (Exception ex) {
			MeBoyBuilder.nonFatalException(ex, "MeBoyBuilder failed to open the selected file.");
			readyToSend = true;
			sendButton.setEnabled(deviceList.getSelectedValue() != null);
			sendProgressLabel.setText("Failed to open file.");
			return;
		}

		int ix = deviceList.getSelectedIndex();
		RemoteDevice device = remoteDevices.get(ix);
		sendProgress.setIndeterminate(true);
		sendProgressLabel.setText("Trying to connect to MeBoy on the selected device.");

		try {
			discoveryAgentIsScanning = true;
			discoveryAgentId = discoveryAgent.searchServices(attrSet, uuidSet, device, this);
		} catch (Exception ex) {
			MeBoyBuilder.nonFatalException(ex, "MeBoyBuilder failed to connect to MeBoy on the selected device.");
			readyToSend = true;
			sendButton.setEnabled(deviceList.getSelectedValue() != null);
			sendProgressLabel.setText("Failed to connect.");
			return;
		}
	}

	public void valueChanged(ListSelectionEvent e) {
		sendButton.setEnabled(readyToSend && deviceList.getSelectedValue() != null);
	}

	public void windowActivated(WindowEvent e) {}

	public void windowClosed(WindowEvent e) {}

	public void windowDeactivated(WindowEvent e) {}

	public void windowDeiconified(WindowEvent e) {}

	public void windowIconified(WindowEvent e) {}

	public void windowOpened(WindowEvent e) {}
}
