package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MsgpackMessageCodec implements MessageCodec {
	private final ObjectMapper objectMapper;
	private static final Set<String> UUID_SCALAR_KEYS = Set.of(
			"submitPlayerId",
			"playerId",
			"playerUUID",
			"ownerId",
			"targetEntityId",
			"uuid",
			"id"
	);
	private static final Set<String> UUID_LIST_KEYS = Set.of(
			"targetEntityIds",
			"players",
			"delete",
			"connections",
			"members",
			"waypointIds"
	);
	private static final Set<String> UUID_KEYED_MAP_KEYS = Set.of(
			"players",
			"entities",
			"waypoints",
			"playerMarks",
			"reports",
			"sourceToGroup",
			"upsert"
	);

	public MsgpackMessageCodec() {
		this.objectMapper = new ObjectMapper(new MessagePackFactory());
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public byte[] encode(Object packet) {
		try {
			Object normalized = normalizeUuidOutbound(packet, null);
			return objectMapper.writeValueAsBytes(normalized);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to encode msgpack payload", e);
		}
	}

	@Override
	public <T> T decode(byte[] payload, Class<T> packetType) {
		try {
			Object decoded = objectMapper.readValue(payload, Object.class);
			Object normalized = normalizeUuidInbound(decoded, null);
			return objectMapper.convertValue(normalized, packetType);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to decode msgpack payload", e);
		}
	}

	private Object normalizeUuidInbound(Object value, String keyName) {
		if (value instanceof Map<?, ?> map) {
			Map<Object, Object> normalized = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				Object rawKey = entry.getKey();
				Object normalizedKey = normalizeInboundMapKey(rawKey, keyName);
				String childKey = normalizedKey instanceof String ? (String) normalizedKey : null;
				Object rawValue = entry.getValue();

				if (childKey != null && UUID_SCALAR_KEYS.contains(childKey)) {
					String canonical = UuidBinaryCodec.toCanonicalString(rawValue);
					normalized.put(normalizedKey, canonical != null ? canonical : rawValue);
					continue;
				}

				if (childKey != null && UUID_LIST_KEYS.contains(childKey) && rawValue instanceof List<?> list) {
					List<Object> converted = new ArrayList<>(list.size());
					for (Object item : list) {
						String canonical = UuidBinaryCodec.toCanonicalString(item);
						converted.add(canonical != null ? canonical : item);
					}
					normalized.put(normalizedKey, converted);
					continue;
				}

				normalized.put(normalizedKey, normalizeUuidInbound(rawValue, childKey));
			}
			return normalized;
		}

		if (value instanceof List<?> list) {
			List<Object> normalized = new ArrayList<>(list.size());
			for (Object item : list) {
				normalized.add(normalizeUuidInbound(item, keyName));
			}
			return normalized;
		}

		return value;
	}

	private Object normalizeUuidOutbound(Object value, String keyName) {
		if (value instanceof Map<?, ?> map) {
			Map<Object, Object> normalized = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				Object rawKey = entry.getKey();
				Object normalizedKey = normalizeOutboundMapKey(rawKey, keyName);
				String childKey = rawKey instanceof String ? (String) rawKey : null;
				Object rawValue = entry.getValue();

				if (childKey != null && UUID_SCALAR_KEYS.contains(childKey)) {
					byte[] raw = UuidBinaryCodec.toBytes(rawValue instanceof String ? (String) rawValue : UuidBinaryCodec.toCanonicalString(rawValue));
					normalized.put(normalizedKey, raw != null ? raw : rawValue);
					continue;
				}

				if (childKey != null && UUID_LIST_KEYS.contains(childKey) && rawValue instanceof List<?> list) {
					List<Object> converted = new ArrayList<>(list.size());
					for (Object item : list) {
						String canonical = UuidBinaryCodec.toCanonicalString(item);
						byte[] raw = UuidBinaryCodec.toBytes(canonical);
						converted.add(raw != null ? raw : item);
					}
					normalized.put(normalizedKey, converted);
					continue;
				}

				normalized.put(normalizedKey, normalizeUuidOutbound(rawValue, childKey));
			}
			return normalized;
		}

		if (value instanceof List<?> list) {
			List<Object> normalized = new ArrayList<>(list.size());
			for (Object item : list) {
				normalized.add(normalizeUuidOutbound(item, keyName));
			}
			return normalized;
		}

		return value;
	}

	private Object normalizeInboundMapKey(Object rawKey, String parentKey) {
		if (rawKey instanceof byte[] bytes) {
			String canonical = UuidBinaryCodec.toCanonicalString(bytes);
			if (canonical != null) {
				return canonical;
			}
		}

		if (UUID_KEYED_MAP_KEYS.contains(parentKey) && rawKey instanceof String text) {
			String canonical = UuidBinaryCodec.toCanonicalString(text);
			if (canonical != null) {
				return canonical;
			}
		}

		return rawKey;
	}

	private Object normalizeOutboundMapKey(Object rawKey, String parentKey) {
		if (UUID_KEYED_MAP_KEYS.contains(parentKey) && rawKey instanceof String text) {
			byte[] raw = UuidBinaryCodec.toBytes(text);
			if (raw != null) {
				return raw;
			}
		}
		return rawKey;
	}
}
