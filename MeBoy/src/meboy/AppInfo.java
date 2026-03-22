package meboy;

final class AppInfo {
	static final String LOAD_SD_LABEL = "Load ROM from SD";
	static final String BACK_DIR_LABEL = "..";
	static final String APP_TITLE = "MeBoy 2.4";
	static final String ABOUT_MESSAGE = AppInfo.APP_TITLE + " \u00A9 Bj\u00F6rn Carlin, 2005-2009.\n"
			+ "Additional development: Juan Reategui Solis (chijure)\n"
			+ "http://arktos.se/meboy/";
	static final String FILE_BROWSER_TITLE = "Select ROM";
	static final String WAIT_FORM_TITLE = "MeBoy";
	static final String WAIT_STORAGE_MESSAGE = "Opening storage...";
	static final String WAIT_LOADING_ROM_MESSAGE = "Loading ROM...";
	static final String NO_BUNDLED_ROMS_MESSAGE = "No bundled ROMs found. Use \"";
	static final String FILE_BROWSER_OPEN_ERROR = "Could not open file browser.";
	static final String ROM_LOAD_MEMORY_ERROR = "Not enough memory to load ROM.";
	static final String ROM_LOAD_ERROR = "Could not load ROM from storage.";
	static final String FILE_OPERATION_ERROR = "File operation failed.";
	static final int MAX_EXTERNAL_ROM_SIZE = 4 * 1024 * 1024;

	private AppInfo() {
	}
}
