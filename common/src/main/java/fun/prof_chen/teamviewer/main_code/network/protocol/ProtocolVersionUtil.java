package fun.prof_chen.teamviewer.main_code.network.protocol;

public final class ProtocolVersionUtil {
	private ProtocolVersionUtil() {
	}

	public static boolean atLeast(String current, String minimum) {
		return parse(current) >= parse(minimum);
	}

	public static long parse(String version) {
		if (version == null || version.isBlank()) {
			return 0L;
		}

		String normalized = version.trim();
		int suffixIndex = normalized.indexOf('-');
		if (suffixIndex >= 0) {
			normalized = normalized.substring(0, suffixIndex);
		}

		String[] parts = normalized.split("\\.");
		long major = parsePart(parts, 0);
		long minor = parsePart(parts, 1);
		long patch = parsePart(parts, 2);
		return major * 1_000_000L + minor * 1_000L + patch;
	}

	private static long parsePart(String[] parts, int index) {
		if (parts == null || index < 0 || index >= parts.length) {
			return 0L;
		}
		String text = parts[index] == null ? "" : parts[index].trim();
		if (text.isEmpty()) {
			return 0L;
		}

		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (Character.isDigit(ch)) {
				digits.append(ch);
			} else {
				break;
			}
		}
		if (digits.length() == 0) {
			return 0L;
		}
		try {
			return Long.parseLong(digits.toString());
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}
}
