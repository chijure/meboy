package meboy.ui;

import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import meboy.GBCanvas;
import meboy.MeBoy;

public final class SettingsController implements CommandListener {
	public interface Host {
		Display getDisplay();
		String getLiteral(int index);
		String[] getLanguages();
		int[] getLanguageLookup();
		void reloadLiterals();
		void showMainMenu();
	}

	private final Host host;
	private Form settingsForm;
	private TextField frameSkipField;
	private TextField rotationField;
	private TextField loadThresholdField;
	private TextField scalingModeField;
	private ChoiceGroup graphicsGroup;
	private ChoiceGroup miscSettingsGroup;
	private ChoiceGroup soundGroup;
	private ChoiceGroup languageGroup;

	public SettingsController(Host host) {
		this.host = host;
	}

	public void show() {
		settingsForm = new Form(host.getLiteral(2));

		frameSkipField = new TextField(host.getLiteral(12), "" + MeBoy.maxFrameSkip, 3, TextField.NUMERIC);
		settingsForm.append(frameSkipField);
		rotationField = new TextField(host.getLiteral(13), "" + MeBoy.rotations, 2, TextField.NUMERIC);
		settingsForm.append(rotationField);

		graphicsGroup = new ChoiceGroup(host.getLiteral(14), ChoiceGroup.MULTIPLE,
				new String[] {host.getLiteral(15), host.getLiteral(16), host.getLiteral(17)}, null);
		graphicsGroup.setSelectedIndex(0, MeBoy.enableScaling);
		graphicsGroup.setSelectedIndex(1, MeBoy.keepProportions);
		graphicsGroup.setSelectedIndex(2, MeBoy.advancedGraphics);
		settingsForm.append(graphicsGroup);

		scalingModeField = new TextField(host.getLiteral(18), Integer.toString(MeBoy.scalingMode), 2, TextField.NUMERIC);
		settingsForm.append(scalingModeField);

		soundGroup = new ChoiceGroup(host.getLiteral(19), ChoiceGroup.MULTIPLE,
				new String[] {host.getLiteral(20), host.getLiteral(21)}, null);
		soundGroup.setSelectedIndex(0, MeBoy.enableSound);
		soundGroup.setSelectedIndex(1, MeBoy.advancedSound);
		settingsForm.append(soundGroup);

		String[] languages = host.getLanguages();
		int[] languageLookup = host.getLanguageLookup();
		languageGroup = new ChoiceGroup(host.getLiteral(22), ChoiceGroup.EXCLUSIVE, languages, null);
		for (int i = 0; i < languages.length; i++) {
			languageGroup.setSelectedIndex(i, MeBoy.language == languageLookup[i]);
		}
		settingsForm.append(languageGroup);

		miscSettingsGroup = new ChoiceGroup(host.getLiteral(23), ChoiceGroup.MULTIPLE,
				new String[] {host.getLiteral(24), host.getLiteral(25)}, null);
		miscSettingsGroup.setSelectedIndex(0, MeBoy.disableColor);
		miscSettingsGroup.setSelectedIndex(1, MeBoy.showLogItem);
		settingsForm.append(miscSettingsGroup);

		loadThresholdField = new TextField(host.getLiteral(26), "" + MeBoy.lazyLoadingThreshold * 16, 5, TextField.NUMERIC);
		settingsForm.append(loadThresholdField);

		settingsForm.addCommand(new Command(host.getLiteral(10), Command.BACK, 0));
		settingsForm.addCommand(new Command(host.getLiteral(27), Command.OK, 1));
		settingsForm.setCommandListener(this);
		host.getDisplay().setCurrent(settingsForm);
	}

	public boolean handles(Displayable displayable) {
		return displayable == settingsForm;
	}

	public void commandAction(Command com, Displayable s) {
		if (com.getCommandType() == Command.BACK) {
			settingsForm = null;
			host.showMainMenu();
			return;
		}

		int f = Integer.parseInt(frameSkipField.getString());
		MeBoy.maxFrameSkip = Math.max(Math.min(f, 59), 0);
		MeBoy.rotations = Integer.parseInt(rotationField.getString()) & 3;
		MeBoy.lazyLoadingThreshold = Math.max(Integer.parseInt(loadThresholdField.getString()) / 16, 20);
		MeBoy.enableScaling = graphicsGroup.isSelected(0);
		MeBoy.keepProportions = graphicsGroup.isSelected(1);
		MeBoy.advancedGraphics = graphicsGroup.isSelected(2);
		f = Integer.parseInt(scalingModeField.getString());
		MeBoy.scalingMode = Math.max(Math.min(f, 3), 0);
		MeBoy.disableColor = miscSettingsGroup.isSelected(0);
		MeBoy.showLogItem = miscSettingsGroup.isSelected(1);
		MeBoy.enableSound = soundGroup.isSelected(0);
		MeBoy.advancedSound = soundGroup.isSelected(1);

		int oldLanguage = MeBoy.language;
		MeBoy.language = host.getLanguageLookup()[languageGroup.getSelectedIndex()];

		GBCanvas.writeSettings();
		if (oldLanguage != MeBoy.language) {
			host.reloadLiterals();
		}
		settingsForm = null;
		host.showMainMenu();
	}
}
