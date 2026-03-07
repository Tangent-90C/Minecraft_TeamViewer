package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidBinaryCodec {
	private UuidBinaryCodec() {
	}

	public static byte[] toBytes(UUID uuid) {
		if (uuid == null) {
			return null;
		}
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		return buffer.array();
	}

	public static byte[] toBytes(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return toBytes(UUID.fromString(value.trim()));
		} catch (Exception ignored) {
			return null;
		}
	}

	public static String toCanonicalString(Object value) {
		if (value == null) {
			return null;
		}

		if (value instanceof UUID uuid) {
			return uuid.toString();
		}

		if (value instanceof byte[] bytes) {
			if (bytes.length != 16) {
				return null;
			}
			try {
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				long msb = buffer.getLong();
				long lsb = buffer.getLong();
				return new UUID(msb, lsb).toString();
			} catch (Exception ignored) {
				return null;
			}
		}

		if (value instanceof String text) {
			try {
				return UUID.fromString(text.trim()).toString();
			} catch (Exception ignored) {
				return null;
			}
		}

		return null;
	}
}
