package meboy.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Vector;
import meboy.MeBoy;

public final class LanguageResources {
	public static final class LanguageIndex {
		public final String[] names;
		public final int[] lookup;

		public LanguageIndex(String[] names, int[] lookup) {
			this.names = names;
			this.lookup = lookup;
		}
	}

	private static final String[] DEFAULT_LANGUAGE_NAMES = new String[] {"English"};
	private static final int[] DEFAULT_LANGUAGE_LOOKUP = new int[] {0};

	private LanguageResources() {
	}

	public static LanguageIndex loadIndex(Class owner) {
		try {
			InputStreamReader isr = openLangReader(owner, "index.txt");
			String code;
			Vector langVector = new Vector();
			Vector lookupVector = new Vector();
			while ((code = next(isr)) != null) {
				String langName = next(isr);
				if (code.length() == 0 || langName == null) {
					continue;
				}
				lookupVector.addElement(new Integer(code.charAt(0) - 'a'));
				langVector.addElement(langName);
			}

			String[] languages = new String[langVector.size()];
			langVector.copyInto(languages);
			int[] languageLookup = new int[lookupVector.size()];
			for (int i = 0; i < lookupVector.size(); i++) {
				languageLookup[i] = ((Integer) lookupVector.elementAt(i)).intValue();
			}
			isr.close();
			MeBoy.log("Language index: loaded " + languages.length + " languages");
			return new LanguageIndex(languages, languageLookup);
		} catch (Exception e) {
			if (MeBoy.debug) {
				e.printStackTrace();
			}
			return new LanguageIndex(DEFAULT_LANGUAGE_NAMES, DEFAULT_LANGUAGE_LOOKUP);
		}
	}

	public static boolean loadLiterals(Class owner, int language, String[] target) {
		try {
			MeBoy.log("Language literals: loading /lang/" + language + ".txt");
			InputStreamReader isr = openLangReader(owner, language + ".txt");
			int counter = 0;
			String s;
			while ((s = next(isr)) != null && counter < target.length) {
				target[counter++] = s;
			}
			isr.close();
			while (counter < target.length) {
				target[counter++] = "?";
			}
			MeBoy.log("Language literals: loaded " + counter + " entries for language id " + language);
			return true;
		} catch (Exception e) {
			if (MeBoy.debug) {
				e.printStackTrace();
			}
			return false;
		}
	}

	public static int detectLanguageFromLocale() {
		String[] localeProps = new String[] {
			"microedition.locale",
			"user.language",
			"user.locale"
		};
		for (int i = 0; i < localeProps.length; i++) {
			String locale = readSystemProperty(localeProps[i]);
			MeBoy.log("Language detect: " + localeProps[i] + "=" + (locale == null ? "<null>" : locale));
			int detected = mapLocaleToLanguage(locale);
			if (detected >= 0) {
				MeBoy.log("Language detect: matched locale from " + localeProps[i] + " -> language id " + detected);
				return detected;
			}
		}
		MeBoy.log("Language detect: no locale match, defaulting to language id 0");
		return 0;
	}

	private static String readSystemProperty(String key) {
		try {
			String value = System.getProperty(key);
			if (value != null && value.length() > 0) {
				return value.toLowerCase();
			}
		} catch (Throwable t) {
		}
		return null;
	}

	private static boolean hasLanguagePrefix(String locale, String prefix) {
		if (locale == null || prefix == null || !locale.startsWith(prefix)) {
			return false;
		}
		if (locale.length() == prefix.length()) {
			return true;
		}
		char separator = locale.charAt(prefix.length());
		return separator == '_' || separator == '-' || separator == '.' || separator == '@';
	}

	private static int mapLocaleToLanguage(String locale) {
		if (locale == null || locale.length() == 0) {
			return -1;
		}
		if (hasLanguagePrefix(locale, "es") || locale.indexOf("spanish") >= 0 || locale.indexOf("espan") >= 0
				|| locale.indexOf("castell") >= 0) {
			return 1;
		}
		if (hasLanguagePrefix(locale, "el") || locale.indexOf("greek") >= 0) {
			return 2;
		}
		if (hasLanguagePrefix(locale, "pt") || locale.indexOf("portugu") >= 0) {
			return 3;
		}
		if (hasLanguagePrefix(locale, "zh") || locale.indexOf("chinese") >= 0) {
			return 4;
		}
		if (hasLanguagePrefix(locale, "pl") || locale.indexOf("polish") >= 0) {
			return 5;
		}
		if (hasLanguagePrefix(locale, "de") || locale.indexOf("german") >= 0 || locale.indexOf("deutsch") >= 0) {
			return 6;
		}
		if (hasLanguagePrefix(locale, "nl") || locale.indexOf("dutch") >= 0 || locale.indexOf("neder") >= 0) {
			return 7;
		}
		if (hasLanguagePrefix(locale, "id") || hasLanguagePrefix(locale, "in") || locale.indexOf("indones") >= 0) {
			return 8;
		}
		if (hasLanguagePrefix(locale, "fr") || locale.indexOf("french") >= 0 || locale.indexOf("franc") >= 0) {
			return 9;
		}
		if (hasLanguagePrefix(locale, "it") || locale.indexOf("ital") >= 0) {
			return 10;
		}
		if (hasLanguagePrefix(locale, "cs") || hasLanguagePrefix(locale, "cz") || locale.indexOf("czech") >= 0
				|| locale.indexOf("cesk") >= 0) {
			return 11;
		}
		if (hasLanguagePrefix(locale, "ru") || locale.indexOf("russian") >= 0) {
			return 12;
		}
		if (hasLanguagePrefix(locale, "hr") || locale.indexOf("croatian") >= 0 || locale.indexOf("hrvat") >= 0) {
			return 13;
		}
		if (hasLanguagePrefix(locale, "sr") || locale.indexOf("serbian") >= 0 || locale.indexOf("srps") >= 0) {
			return 14;
		}
		if (hasLanguagePrefix(locale, "uk") || locale.indexOf("ukrain") >= 0) {
			return 15;
		}
		if (hasLanguagePrefix(locale, "lt") || locale.indexOf("lithuan") >= 0 || locale.indexOf("lietuv") >= 0) {
			return 16;
		}
		if (hasLanguagePrefix(locale, "lv") || locale.indexOf("latv") >= 0) {
			return 17;
		}
		if (hasLanguagePrefix(locale, "hu") || locale.indexOf("hungar") >= 0 || locale.indexOf("magyar") >= 0) {
			return 18;
		}
		if (hasLanguagePrefix(locale, "gsw") || locale.indexOf("swiss") >= 0 || locale.indexOf("schwizer") >= 0) {
			return 19;
		}
		if (hasLanguagePrefix(locale, "da") || locale.indexOf("danish") >= 0) {
			return 20;
		}
		if (hasLanguagePrefix(locale, "tr") || locale.indexOf("turkish") >= 0 || locale.indexOf("turk") >= 0) {
			return 21;
		}
		if (hasLanguagePrefix(locale, "en") || locale.indexOf("english") >= 0) {
			return 0;
		}
		return -1;
	}

	private static InputStreamReader openLangReader(Class owner, String fileName) throws IOException {
		String[] resourcePaths = new String[] {
			"/lang/" + fileName,
			"../../lang/" + fileName,
			"lang/" + fileName
		};
		for (int i = 0; i < resourcePaths.length; i++) {
			InputStream stream = owner.getResourceAsStream(resourcePaths[i]);
			if (stream != null) {
				return new InputStreamReader(stream, "UTF-8");
			}
		}
		throw new IOException("Language resource not found: " + fileName);
	}

	private static String next(InputStreamReader isr) throws IOException {
		StringBuffer sb = new StringBuffer();
		int c;
		while ((c = isr.read()) != -1) {
			if (c >= 32) {
				sb.append((char) c);
			} else if (sb.length() > 0) {
				return sb.toString();
			}
		}
		if (sb.length() > 0) {
			return sb.toString();
		}
		return null;
	}
}
