package meboy.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import meboy.app.AppInfo;

public final class DeviceInfoController implements CommandListener {
	public interface Host {
		Display getDisplay();
		void showMainMenu();
	}

	private static final String[] PROPERTY_KEYS = new String[] {
			"microedition.platform",
			"microedition.configuration",
			"microedition.profiles",
			"microedition.locale",
			"microedition.encoding",
			"microedition.commports",
			"bluetooth.api.version",
			"bluetooth.l2cap.receiveMTU.max",
			"fileconn.dir.memorycard",
			"fileconn.dir.private",
			"fileconn.dir.photos",
			"fileconn.dir.videos",
			"device.model",
			"device.software.version",
			"com.sonyericsson.java.platform",
			"com.sonyericsson.imei",
			"com.nokia.mid.imei",
			"com.nokia.mid.networkID",
			"com.nokia.memoryramfree",
			"com.nokia.memoryramtotal",
			"wireless.messaging.sms.smsc"
	};

	private final Host host;
	private Form deviceInfoForm;

	public DeviceInfoController(Host host) {
		this.host = host;
	}

	public void show() {
		deviceInfoForm = new Form(AppInfo.DEVICE_INFO_TITLE);
		appendOverview();
		appendRuntimeInfo();
		appendProperties();
		deviceInfoForm.addCommand(new Command(AppInfo.BACK_LABEL, Command.BACK, 0));
		deviceInfoForm.setCommandListener(this);
		host.getDisplay().setCurrent(deviceInfoForm);
	}

	public boolean handles(Displayable displayable) {
		return displayable == deviceInfoForm;
	}

	public void commandAction(Command com, Displayable s) {
		deviceInfoForm = null;
		host.showMainMenu();
	}

	private void appendOverview() {
		deviceInfoForm.append("General\n");
		appendLine("App", AppInfo.APP_TITLE);
		appendLine("Platform", readProperty("microedition.platform"));
		appendLine("Configuration", readProperty("microedition.configuration"));
		appendLine("Profiles", readProperty("microedition.profiles"));
		appendLine("Locale", readProperty("microedition.locale"));
		appendLine("Encoding", readProperty("microedition.encoding"));
		appendLine("CPU", guessCpuInfo());
		appendLine("GPU", "Not exposed by Java ME");
		deviceInfoForm.append("\n");
	}

	private void appendRuntimeInfo() {
		Runtime runtime = Runtime.getRuntime();
		long freeMemory = runtime.freeMemory();
		long totalMemory = runtime.totalMemory();

		deviceInfoForm.append("Runtime\n");
		appendLine("Display color", host.getDisplay().isColor() ? "Yes" : "No");
		appendLine("Colors", Integer.toString(host.getDisplay().numColors()));
		appendLine("Free heap", formatBytes(freeMemory));
		appendLine("Total heap", formatBytes(totalMemory));
		appendLine("Used heap", formatBytes(totalMemory - freeMemory));
		deviceInfoForm.append("\n");
	}

	private void appendProperties() {
		deviceInfoForm.append("System Properties\n");
		for (int i = 0; i < PROPERTY_KEYS.length; i++) {
			String key = PROPERTY_KEYS[i];
			String value = readProperty(key);
			if (value != null) {
				appendLine(key, value);
			}
		}
	}

	private void appendLine(String label, String value) {
		deviceInfoForm.append(label + ": " + normalizeValue(value) + "\n");
	}

	private String readProperty(String key) {
		try {
			return System.getProperty(key);
		} catch (SecurityException e) {
			return "Restricted";
		} catch (Throwable t) {
			return null;
		}
	}

	private String guessCpuInfo() {
		String cpu = firstAvailable(new String[] {
				"device.cpu",
				"com.sonyericsson.java.cpu",
				"com.nokia.cpu.version"
		});
		if (cpu != null) {
			return cpu;
		}
		return "Not exposed by Java ME";
	}

	private String firstAvailable(String[] keys) {
		for (int i = 0; i < keys.length; i++) {
			String value = readProperty(keys[i]);
			if (value != null && value.length() > 0) {
				return value;
			}
		}
		return null;
	}

	private String normalizeValue(String value) {
		if (value == null || value.length() == 0) {
			return "N/A";
		}
		return value;
	}

	private String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return (bytes / 1024) + " KB";
		}
		return (bytes / (1024 * 1024)) + " MB";
	}
}
