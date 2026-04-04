package meboy.app;

public final class AppInfo {
	public static final String LOAD_SD_LABEL = "Load ROM from SD";
	public static final String BACK_DIR_LABEL = "..";
	public static final String APP_TITLE = "MeBoy 2.4";
	public static final String ABOUT_MESSAGE = AppInfo.APP_TITLE + " \u00A9 Bj\u00F6rn Carlin, 2005-2009.\n"
			+ "Additional development: Juan Reategui Solis (chijure)\n"
			+ "http://arktos.se/meboy/";
	public static final String FILE_BROWSER_TITLE = "Select ROM";
	public static final String DEVICE_INFO_LABEL = "Device Info";
	public static final String DEVICE_INFO_TITLE = "Device Info";
	public static final String BACK_LABEL = "Back";
	public static final String LOG_LABEL = "Log";
	public static final String WAIT_FORM_TITLE = "MeBoy";
	public static final String WAIT_STORAGE_MESSAGE = "Opening storage...";
	public static final String WAIT_LOADING_ROM_MESSAGE = "Loading ROM...";
	public static final String NO_BUNDLED_ROMS_MESSAGE = "No bundled ROMs found. Use \"";
	public static final String FILE_BROWSER_OPEN_ERROR = "Could not open file browser.";
	public static final String ROM_LOAD_MEMORY_ERROR = "Not enough memory to load ROM.";
	public static final String ROM_LOAD_ERROR = "Could not load ROM from storage.";
	public static final String FILE_OPERATION_ERROR = "File operation failed.";
	public static final int MAX_EXTERNAL_ROM_SIZE = 4 * 1024 * 1024;

	private AppInfo() {
	}
}
