package meboy.util;

public final class StringArrayUtil {
	private StringArrayUtil() {
	}

	public static String[] append(String[] values, String value) {
		String[] source = values == null ? new String[0] : values;
		String[] result = new String[source.length + 1];
		System.arraycopy(source, 0, result, 0, source.length);
		result[source.length] = value;
		return result;
	}

	public static String[] removeAt(String[] values, int index) {
		if (values == null || index < 0 || index >= values.length) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		String[] result = new String[values.length - 1];
		System.arraycopy(values, 0, result, 0, index);
		System.arraycopy(values, index + 1, result, index, result.length - index);
		return result;
	}
}
