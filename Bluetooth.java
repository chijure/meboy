/*

MeBoy

Copyright 2005-2009 Bjorn Carlin
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

import java.io.*;
import java.util.*;
import javax.bluetooth.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.*;


public class Bluetooth implements DiscoveryListener, CommandListener, Runnable {
	private static final UUID MeBoyUUID = new UUID("ceb15f4a19eb4db28d1c40eb0f091607", false);
	private MeBoy parent;
	private String connectionURL;
	private String savegameDisplayName;
	private String savegameFilename;
	private String savegameCartID;
	private String sendgameMode = "s";
	private byte[] savegameData;

	private Vector remoteDevices = new Vector();
	private DiscoveryAgent discoveryAgent;
	private boolean discoveryAgentIsInquiring;
	private boolean discoveryAgentIsScanning;
	private int discoveryAgentId;
	// 0x1101 is the UUID for the Serial Port Profile
	private UUID[] uuidSet = {new UUID(0x1101)};	// 0x0100 is the attribute for the service name element
	// in the service record
	private int[] attrSet = {0x0100};

	// UI
	private Form waitForm;
	private List mainList;
	private Form receiveForm;
	private List savegameList;
	private List suspendgameList;
	private List deviceList;
	private Gauge gauge;
	
	private Vector savegameCartIDs;
	private Vector savegameFilenames;
	
	// transmit stuff
	private boolean iAmSender;
	private StreamConnectionNotifier notifier;
	private StreamConnection connection;
	private DataInputStream is;
	private DataOutputStream os;
	
	private boolean poisoned;
	
	
	public Bluetooth(MeBoy parent) {
		this.parent = parent;
		showMain();
	}

	public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass cod) {
		remoteDevices.addElement(remoteDevice);
	}

	public void inquiryCompleted(int discType) {
		if (poisoned) return;
		
		discoveryAgentIsInquiring = false;
		
		if (discType != INQUIRY_COMPLETED) {
			// probably canceled
			MeBoy.log("disctype = " + discType);
			return;
		}
		if (remoteDevices.size() == 0) {
			// the discovery process failed, so inform the user
			if (MeBoy.debug)
				parent.cancelBluetooth(MeBoy.literal[60] + " " + discType + " " + remoteDevices.size());
			else
				parent.cancelBluetooth(MeBoy.literal[60]);
			return;
		}
		
		showDevice();
	}

	public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		if (poisoned) return;
		
		for (int i = 0; i < servRecord.length; i++) {
			DataElement serviceElement = servRecord[i].getAttributeValue(0x0001);
			Enumeration serviceIDs = (Enumeration) (serviceElement == null ? null : serviceElement.getValue());
			if (serviceIDs == null) {
				// MeBoy.log("serviceIDs=null");
				continue;
			}

			// check, if one of the service's class IDs matches the application's UUID
			while (serviceIDs.hasMoreElements()) {
				UUID serviceID = (UUID) (((DataElement) serviceIDs.nextElement()).getValue());
				if (!serviceID.equals(MeBoyUUID))
					continue;
				
				// MeBoy.log("Bluetooth: service found: " + servRecord[i].toString());

				// get URL the "canonical" way
				connectionURL = servRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				if (connectionURL != null && connectionURL.length() > 0)
					return;

				// get URL the "messy but compatible" way, since getConnectionURL can fail
				long channel = getChannel(servRecord[i].getAttributeValue(0x0004));
				if (channel <= 0)
					break;
				
				connectionURL = "btspp://" + servRecord[i].getHostDevice().getBluetoothAddress() + ":" +
					channel + ";authenticate=false;encrypt=false";
				return;
			}
		}
	}

	public void serviceSearchCompleted(int transID, int respCode) {
		if (poisoned) return;
		
		discoveryAgentIsScanning = false;
		
		if (connectionURL != null && respCode == DiscoveryListener.SERVICE_SEARCH_COMPLETED) {
			iAmSender = true;
			new Thread(this).start();
			showWait(MeBoy.literal[61], true);
		} else {
			parent.cancelBluetooth(MeBoy.literal[62]);
		}
	}

	public void showWait(String message) {
		showWait(message, false);
	}
	
	public void showWait(String message, boolean progressBar) {
		waitForm = new Form(MeBoy.literal[4]);
		waitForm.append(message);
		if (progressBar) {
			gauge = new Gauge("", false, 100, 5);
			waitForm.append(gauge);
		}
		waitForm.setCommandListener(this);
		waitForm.addCommand(new Command(MeBoy.literal[57], Command.CANCEL, 0));
		parent.display.setCurrent(waitForm);
	}
	
	private void waitCommand() {
		parent.cancelBluetooth();
	}

	private void showMain() {
		mainList = new List(MeBoy.literal[4], List.IMPLICIT);
		mainList.append(MeBoy.literal[52], null);
		if (parent.suspendName20.length > 0)
			mainList.append(MeBoy.literal[54], null);
		mainList.append(MeBoy.literal[56], null);
		mainList.addCommand(new Command(MeBoy.literal[10], Command.BACK, 1));
		mainList.setCommandListener(this);
		parent.display.setCurrent(mainList);
	}

	private void mainCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			parent.cancelBluetooth();
		} else {
			String item = mainList.getString(mainList.getSelectedIndex());
			if (item == MeBoy.literal[52]) {
				showSavegameSelect();
			} else if (item == MeBoy.literal[54]) {
				showSuspendGameSelect();
			} else if (item.equals(MeBoy.literal[56])) {
				showReceive(false);
			}
		}
		mainList = null;
	}

	private void showSavegameSelect() {
		savegameList = new List(MeBoy.literal[53], List.IMPLICIT);
		savegameCartIDs = new Vector();
		savegameFilenames = new Vector();
		parent.addSavegamesToList(savegameList, savegameCartIDs, savegameFilenames);
		
		if (savegameList.size() == 0) {
			parent.cancelBluetooth(MeBoy.literal[65]);
			return;
		}
		
		savegameList.addCommand(new Command(MeBoy.literal[10], Command.BACK, 1));
		savegameList.setCommandListener(this);
		parent.display.setCurrent(savegameList);
	}
	
	private void startInquiry() throws BluetoothStateException, InterruptedException {
		LocalDevice localDevice = LocalDevice.getLocalDevice();
		discoveryAgent = localDevice.getDiscoveryAgent();
		for (int i = 0; i < 10; i++) {
			// For when there's an old inquiry still running, this will
			// retry 10 times to start an inquiry and ignoring the
			// state exception:
			try {
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
				discoveryAgentIsInquiring = true;
				break;
			} catch (BluetoothStateException bse) {
				showWait(MeBoy.literal[66]);
				Thread.sleep(500);
			}
		}
		if (!discoveryAgentIsInquiring) {
			discoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
			discoveryAgentIsInquiring = true;
		}

		showWait(MeBoy.literal[66]);
	}
	
	private void savegameSelectCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			showMain();
			savegameList = null;
			return;
		}
		
		savegameDisplayName = savegameList.getString(savegameList.getSelectedIndex());
		savegameCartID = (String) savegameCartIDs.elementAt(savegameList.getSelectedIndex());
		savegameFilename = (String) savegameFilenames.elementAt(savegameList.getSelectedIndex());
		sendgameMode = "r";
		savegameList = null;
		
		try {
			RecordStore rs = RecordStore.openRecordStore("20R_" + savegameCartID, false);
			savegameData = rs.getRecord(1);
			rs.closeRecordStore();
			
			startInquiry();
		} catch (Exception e) {
			parent.cancelBluetooth(e);
			return;
		}
	}

	private void showSuspendGameSelect() {
		suspendgameList = new List(MeBoy.literal[55], List.IMPLICIT);
		
		for (int i = 0; i < parent.suspendName20.length; i++) {
			suspendgameList.append(parent.suspendName20[i], null);
		}
		
		suspendgameList.addCommand(new Command(MeBoy.literal[10], Command.BACK, 1));
		suspendgameList.setCommandListener(this);
		parent.display.setCurrent(suspendgameList);
	}
	
	private void suspendGameSelectCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			showMain();
			suspendgameList = null;
			return;
		}
		
		savegameDisplayName = suspendgameList.getString(suspendgameList.getSelectedIndex());
		savegameFilename = "";
		sendgameMode = "s";
		suspendgameList = null;
		
		try {
			RecordStore rs = RecordStore.openRecordStore("20S_" + savegameDisplayName, false);
			savegameCartID = new String(rs.getRecord(1));
			savegameData = rs.getRecord(2);
			rs.closeRecordStore();
			
			int ix = savegameDisplayName.indexOf(':');
			if (ix > 0)
				savegameDisplayName = savegameDisplayName.substring(ix + 2); // : and space
			
			startInquiry();
		} catch (Exception e) {
			parent.cancelBluetooth(e);
			return;
		}
	}

	private void showDevice() {
		try {
			// the discovery process was a success
			// so let's out them in a List and display it to the user
			deviceList = new List(MeBoy.literal[70], List.IMPLICIT);
			for (int i = 0; i < remoteDevices.size(); i++) {
				String device;
				try {
					device = ((RemoteDevice) remoteDevices.elementAt(i)).getFriendlyName(true);
				} catch (Exception ex) {
					device = ((RemoteDevice) remoteDevices.elementAt(i)).getBluetoothAddress();
				}
				deviceList.append(device, null);
			}
			
			deviceList.addCommand(new Command(MeBoy.literal[57], Command.CANCEL, 1));
			deviceList.setCommandListener(this);
			parent.display.setCurrent(deviceList);
		} catch (Exception e) {
			parent.cancelBluetooth(e);
		}
	}

	private void deviceCommand(Command com) {
		if (com.getCommandType() == Command.CANCEL) {
			parent.cancelBluetooth();
			return;
		}
		
		int selectedDevice = deviceList.getSelectedIndex();
		
		try {
			RemoteDevice remoteDevice = (RemoteDevice) remoteDevices.elementAt(selectedDevice);
			discoveryAgentId = discoveryAgent.searchServices(attrSet, uuidSet, remoteDevice, this);
			discoveryAgentIsScanning = true;
		} catch (Exception e) {
			parent.cancelBluetooth(e);
			return;
		}
		
		showWait(MeBoy.literal[67]);
	}

	private void showReceive(boolean gotConnection) {
		receiveForm = new Form(MeBoy.literal[56]);
		receiveForm.addCommand(new Command(MeBoy.literal[57], Command.CANCEL, 1));
		receiveForm.setCommandListener(this);
		if (gotConnection) {
			receiveForm.append(MeBoy.literal[71]);
			gauge = new Gauge("", false, 100, 0);
			receiveForm.append(gauge);
		} else {
			receiveForm.append(MeBoy.literal[68]);
			iAmSender = false;
			new Thread(this).start();
		}
		parent.display.setCurrent(receiveForm);
	}

	private void receiveCommand(Command com) {
		if (com.getCommandType() == Command.CANCEL) {
			parent.cancelBluetooth();
		}
	}
	
	public void tearDown() {
		if (poisoned) return;
		
		poisoned = true;
		
		if (discoveryAgent != null) {
			if (discoveryAgentIsScanning)
				discoveryAgent.cancelServiceSearch(discoveryAgentId);
			else if (discoveryAgentIsInquiring)
				discoveryAgent.cancelInquiry(this);
			discoveryAgent = null;
		}
		
		if (is != null) {
			try {
				is.close();
			} catch (IOException ex) {
			} //ignore
			is = null;
		}
		if (os != null) {
			try {
				os.close();
			} catch (IOException ex) {
			} //ignore
			os = null;
		}
		if (connection != null) {
			try {
				connection.close();
			} catch (IOException ex) {
			} //ignore
			connection = null;
		}
		if (notifier != null) {
			try {
				notifier.close();
			} catch (IOException ex) {
			} //ignore
			notifier = null;
		}
		mainList = null;
		receiveForm = null;
		savegameList = null;
		deviceList = null;
		System.gc();
	}

	public void commandAction(Command com, Displayable s) {
		if (s == waitForm)
			waitCommand();
		else if (s == mainList)
			mainCommand(com);
		else if (s == savegameList)
			savegameSelectCommand(com);
		else if (s == suspendgameList)
			suspendGameSelectCommand(com);
		else if (s == deviceList)
			deviceCommand(com);
		else if (s == receiveForm)
			receiveCommand(com);
	}
	
	private void send() throws Exception {
		connection = (StreamConnection) Connector.open(connectionURL);
		is = connection.openDataInputStream();
		os = connection.openDataOutputStream();
		os.writeUTF("MeBoy20");
		os.flush();
		if (MeBoy.debug) showWait("sent protocol");
		String ack = is.readUTF();
		if (MeBoy.debug) showWait("read " + ack);
		if (ack.equals("version")) {
			throw new IOException(MeBoy.literal[69]);
		} else if (!ack.equals("go")) {
			// weird answer
			throw new IOException("read " + ack);
		}
		os.writeUTF(sendgameMode);
		os.writeUTF(savegameDisplayName);
		os.writeUTF(savegameFilename);
		os.writeUTF(savegameCartID);
		os.writeInt(savegameData.length);
		if (MeBoy.debug) showWait("sent header", true);
		int lastPercent = 0;
		// Send small chunks, since SE K810i crashes hard if I send everything at once.
		for (int i = 0; i < savegameData.length; i += 512) {
			int length = Math.min(savegameData.length - i, 512);
			os.write(savegameData, i, length);
			os.flush();
			if (MeBoy.debug) showWait("sent " + i, true);
			int percent = 100 * i / savegameData.length;
			if (percent > lastPercent + 10) {
				gauge.setValue(percent);
				Thread.sleep(50);
				lastPercent = percent;
			}
		}
		gauge.setValue(100);
		if (MeBoy.debug) showWait("sent all");
		ack = is.readUTF();
		if (ack.equals("ok")) {
			// everything fine
			parent.finishBluetooth();
		} else {
			// something went wrong :(
			throw new IOException("read " + ack);
		}
	}

	private void receive() throws Exception {
		String serviceURL = "btspp://localhost:" + MeBoyUUID +
				";authenticate=false;encrypt=false;name=MeBoy";

		// create a server connection
		notifier = (StreamConnectionNotifier) Connector.open(serviceURL);

		if (MeBoy.debug) showWait("entering blocking");
		// accept client connections
		connection = notifier.acceptAndOpen(); // blocking
		showReceive(true);

		if (MeBoy.debug) showWait("got connection");
		// we have a client when notifier.acceptAndOpen(); returns
		is = connection.openDataInputStream();
		os = connection.openDataOutputStream();
		if (MeBoy.debug) showWait("got streams");
		String protocol = is.readUTF();
		if (!protocol.equals("MeBoy20")) {
			os.writeUTF("version");
			os.flush();
			throw new IOException("read " + protocol);
		}
		if (MeBoy.debug) showWait("read protocol");
		os.writeUTF("go");
		os.flush();
		if (MeBoy.debug) showWait("sent go");
		String mode = is.readUTF();
		if (!mode.equals("r") && !mode.equals("s")) {
			throw new IOException("invalid mode: " + mode);
		}
		
		String gameDisplayName = is.readUTF();
		String gameFileName = is.readUTF();
		String gameCartID = is.readUTF();
		if (MeBoy.debug) showWait("read name");
		int length = is.readInt();
		byte[] savegame = new byte[length];
		gauge.setMaxValue(100);
		gauge.setValue(0);

		int a;
		int offset = 0;
		int lastPercent = 0;
		while (offset < length &&
				(a = is.read(savegame, offset, length - offset)) != -1) {
			offset += a;
			if (MeBoy.debug) showWait("read total " + offset + "/" + length);
			int percent = 100 * offset / length;
			if (percent > lastPercent + 10) {
				gauge.setValue(percent);
				Thread.sleep(50);
				lastPercent = percent;
			}
		}

		if (offset == length) {
			os.writeUTF("ok");
			os.flush();
			parent.finishBluetoothReceive(mode, gameDisplayName, gameFileName, gameCartID, savegame);
		} else {
			throw new IOException("read " + offset + "/" + length);
		}
	}

	public void run() {
		try {
			if (iAmSender) {
				send();
			} else {
				receive();
			}
		} catch (InterruptedIOException e) {
			parent.cancelBluetooth();
		} catch (Exception e) {
			parent.cancelBluetooth(e);
		}
	}
	
	// shamelessly stolen from "traud", with minor changes
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
						if (DataElement.U_INT_8 == channelRFCOMM.getDataType() ||
								DataElement.U_INT_16 == channelRFCOMM.getDataType() ||
								DataElement.INT_16 == channelRFCOMM.getDataType()) {
							byte[] bytes = (byte[]) channelRFCOMM.getValue();
							return bytes[0]; // Range (decimal): 1 to 30
						} else {
							return channelRFCOMM.getLong(); // U_INT_1
						}
					}
				}
			}
		} catch (Exception ex) {}
		
		return -1;
	}
	
	// Will never return false, but invocation will throw an NoClassDefFoundError if not available.
	static boolean available() {
		return true;
	}
}
