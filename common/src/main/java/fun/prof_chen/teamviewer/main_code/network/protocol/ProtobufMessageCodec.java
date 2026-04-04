package fun.prof_chen.teamviewer.main_code.network.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import fun.prof_chen.teamviewer.main_code.network.proto.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtobufMessageCodec implements MessageCodec {
	private final ObjectMapper objectMapper;
	private final JsonFormat.Printer printer;
	private final JsonFormat.Parser parser;

	public ProtobufMessageCodec() {
		this.objectMapper = new ObjectMapper();
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		this.printer = JsonFormat.printer().omittingInsignificantWhitespace();
		this.parser = JsonFormat.parser().ignoringUnknownFields();
	}

	@Override
	public byte[] encode(Object packet) {
		try {
			Map<String, Object> body = objectMapper.convertValue(packet, new TypeReference<Map<String, Object>>() {
			});
			String type = readPacketType(body);
			WireEnvelope.Builder envelope = WireEnvelope.newBuilder().setChannel(WireChannel.WIRE_CHANNEL_PLAYER);

			switch (type) {
				case "handshake" -> envelope.setPlayerHandshakeRequest(buildMessage(
						PlayerHandshakeRequest.newBuilder(),
						normalizeOutboundBody(body)
				).build());
				case "players_update" -> envelope.setPlayerReportBundle(buildBundle(body, "playersReplace", normalizeReplaceBody(body, "players")));
				case "players_patch" -> envelope.setPlayerReportBundle(buildBundle(body, "playersPatch", normalizePatchScopeBody(body)));
				case "entities_update" -> envelope.setPlayerReportBundle(buildBundle(body, "entitiesReplace", normalizeReplaceBody(body, "entities")));
				case "entities_patch" -> envelope.setPlayerReportBundle(buildBundle(body, "entitiesPatch", normalizePatchScopeBody(body)));
				case "waypoints_update" -> envelope.setPlayerReportBundle(buildBundle(body, "waypointsReplace", normalizeReplaceBody(body, "waypoints")));
				case "waypoints_patch" -> envelope.setPlayerReportBundle(buildBundle(body, "waypointsPatch", normalizePatchScopeBody(body)));
				case "tab_players_update" -> envelope.setPlayerReportBundle(buildBundle(body, "tabPlayersReplace", normalizeReplaceBody(body, "tabPlayers")));
				case "tab_players_patch" -> envelope.setPlayerReportBundle(buildBundle(body, "tabPlayersPatch", normalizePatchScopeBody(body)));
				case "state_keepalive" -> envelope.setPlayerReportBundle(buildBundle(body, "stateKeepalive", normalizeNestedBundleBody(body)));
				case "source_state_clear" -> envelope.setPlayerReportBundle(buildBundle(body, "sourceStateClear", normalizeNestedBundleBody(body)));
				case "waypoints_delete" -> envelope.setPlayerReportBundle(buildBundle(body, "waypointsDelete", normalizeNestedBundleBody(body)));
				case "waypoints_entity_death_cancel" -> envelope.setPlayerReportBundle(buildBundle(body, "waypointsEntityDeathCancel", normalizeNestedBundleBody(body)));
				case "battle_map_observation" -> envelope.setPlayerReportBundle(buildBundle(body, "battleMapObservation", normalizeNestedBundleBody(body)));
				case "resync_req" -> envelope.setResyncRequest(buildMessage(
						ResyncRequest.newBuilder(),
						normalizeOutboundBody(body)
				).build());
				default -> throw new IllegalArgumentException("Unsupported protobuf packet type: " + type);
			}

			return envelope.build().toByteArray();
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to encode protobuf payload", e);
		}
	}

	@Override
	public <T> T decode(byte[] payload, Class<T> packetType) {
		try {
			WireEnvelope envelope = WireEnvelope.parseFrom(payload);
			String packetTypeName = mapInboundType(envelope.getPayloadCase());
			if (packetType == ProtocolPackets.BaseInboundPacket.class) {
				Map<String, Object> base = new LinkedHashMap<>();
				base.put("type", packetTypeName);
				return objectMapper.convertValue(base, packetType);
			}

			Map<String, Object> normalized = switch (envelope.getPayloadCase()) {
				case HANDSHAKE_ACK -> withType(packetTypeName, messageToMap(envelope.getHandshakeAck()));
				case SNAPSHOT_FULL -> normalizeSnapshotFullInbound(envelope.getSnapshotFull(), packetTypeName);
				case PATCH -> normalizePatchInbound(envelope.getPatch(), packetTypeName);
				case DIGEST -> normalizeDigestInbound(envelope.getDigest(), packetTypeName);
				case REFRESH_REQUEST -> normalizeRefreshRequestInbound(envelope.getRefreshRequest(), packetTypeName);
				case REPORT_RATE_HINT -> withType(packetTypeName, messageToMap(envelope.getReportRateHint()));
				default -> throw new IllegalArgumentException("Unsupported protobuf payload case: " + envelope.getPayloadCase());
			};

			return objectMapper.convertValue(normalized, packetType);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to decode protobuf payload", e);
		}
	}

	private <B extends Message.Builder> B buildMessage(B builder, Map<String, Object> payload) throws Exception {
		mergeJson(builder, payload);
		return builder;
	}

	private PlayerReportBundle buildBundle(
			Map<String, Object> body,
			String bundleField,
			Map<String, Object> nestedPayload
	) throws Exception {
		PlayerReportBundle.Builder builder = PlayerReportBundle.newBuilder();
		mergeJson(builder, wrapBundleBody(body, bundleField, nestedPayload));
		return builder.build();
	}

	private void mergeJson(Message.Builder builder, Map<String, Object> payload) throws Exception {
		String json = objectMapper.writeValueAsString(payload);
		parser.merge(json, builder);
	}

	private Map<String, Object> messageToMap(Message message) throws Exception {
		String json = printer.print(message);
		return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
		});
	}

	private Map<String, Object> withType(String type, Map<String, Object> payload) {
		Map<String, Object> next = new LinkedHashMap<>(payload);
		next.put("type", type);
		return next;
	}

	private String mapInboundType(WireEnvelope.PayloadCase payloadCase) {
		return switch (payloadCase) {
			case HANDSHAKE_ACK -> "handshake_ack";
			case SNAPSHOT_FULL -> "snapshot_full";
			case PATCH -> "patch";
			case DIGEST -> "digest";
			case REFRESH_REQUEST -> "refresh_req";
			case REPORT_RATE_HINT -> "report_rate_hint";
			default -> payloadCase.name().toLowerCase();
		};
	}

	private Map<String, Object> normalizeDigestInbound(Digest digest, String packetType) throws Exception {
		Map<String, Object> digests = messageToMap(digest);
		Map<String, Object> normalized = new LinkedHashMap<>();
		normalized.put("type", packetType);
		normalized.put("hashes", digests);
		return normalized;
	}

	private Map<String, Object> normalizeSnapshotFullInbound(SnapshotFull snapshotFull, String packetType) throws Exception {
		Map<String, Object> payload = messageToMap(snapshotFull);
		payload.put("battleChunks", normalizeBattleChunkEntries(payload.get("battleChunks")));
		payload.put("type", packetType);
		return payload;
	}

	private Map<String, Object> normalizePatchInbound(Patch patch, String packetType) throws Exception {
		Map<String, Object> payload = messageToMap(patch);
		normalizeInboundPatchScope(payload, "players");
		normalizeInboundPatchScope(payload, "entities");
		normalizeInboundPatchScope(payload, "waypoints");
		payload.put("battleChunks", normalizeBattleChunkPatchScope(payload.get("battleChunks")));
		normalizeInboundPatchScope(payload, "playerMarks");
		payload.put("type", packetType);
		return payload;
	}

	private Map<String, Object> normalizeRefreshRequestInbound(RefreshRequest refreshRequest, String packetType) throws Exception {
		Map<String, Object> payload = messageToMap(refreshRequest);
		payload.put("battleChunks", normalizeBattleChunkRefs(payload.get("battleChunks")));
		payload.put("type", packetType);
		return payload;
	}

	@SuppressWarnings("unchecked")
	private void normalizeInboundPatchScope(Map<String, Object> payload, String scopeKey) {
		Object rawScope = payload.get(scopeKey);
		if (!(rawScope instanceof Map<?, ?> scopeMap)) {
			return;
		}

		Map<String, Object> nextScope = new LinkedHashMap<>();
		Object rawDelete = scopeMap.get("delete");
		if (rawDelete instanceof List<?> deleteList) {
			List<String> normalizedDelete = new ArrayList<>();
			for (Object item : deleteList) {
				if (item != null) {
					normalizedDelete.add(String.valueOf(item));
				}
			}
			nextScope.put("delete", normalizedDelete);
		}

		Object rawUpsert = scopeMap.get("upsert");
		Map<String, Object> normalizedUpsert = new LinkedHashMap<>();
		if (rawUpsert instanceof List<?> upsertList) {
			for (Object item : upsertList) {
				if (!(item instanceof Map<?, ?> upsertEntry)) {
					continue;
				}
				Object rawId = upsertEntry.get("id");
				if (!(rawId instanceof String id) || id.isBlank()) {
					continue;
				}
				Object data = upsertEntry.get("data");
				normalizedUpsert.put(id, data instanceof Map<?, ?> ? data : Map.of());
			}
		}
		nextScope.put("upsert", normalizedUpsert);
		payload.put(scopeKey, nextScope);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> normalizeBattleChunkEntries(Object rawEntries) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		if (!(rawEntries instanceof List<?> entries)) {
			return normalized;
		}

		for (Object item : entries) {
			if (!(item instanceof Map<?, ?> entry)) {
				continue;
			}
			Object rawRef = entry.get("ref");
			Object rawData = entry.get("data");
			Map<String, Object> local = toBattleChunkLocalData(rawRef, rawData);
			if (local.isEmpty()) {
				continue;
			}
			Object chunkId = local.get("id");
			Object data = local.get("data");
			if (chunkId instanceof String id && data instanceof Map<?, ?> mappedData) {
				normalized.put(id, mappedData);
			}
		}

		return normalized;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> normalizeBattleChunkPatchScope(Object rawScope) {
		Map<String, Object> nextScope = new LinkedHashMap<>();
		if (!(rawScope instanceof Map<?, ?> scopeMap)) {
			return nextScope;
		}

		Object rawDelete = scopeMap.get("delete");
		if (rawDelete instanceof List<?> deleteList) {
			nextScope.put("delete", normalizeBattleChunkRefs(deleteList));
		}

		Object rawUpsert = scopeMap.get("upsert");
		if (rawUpsert instanceof List<?> upsertList) {
			Map<String, Object> normalizedUpsert = new LinkedHashMap<>();
			for (Object item : upsertList) {
				if (!(item instanceof Map<?, ?> entry)) {
					continue;
				}
				Map<String, Object> local = toBattleChunkLocalData(entry.get("ref"), entry.get("data"));
				if (local.isEmpty()) {
					continue;
				}
				Object chunkId = local.get("id");
				Object data = local.get("data");
				if (chunkId instanceof String id && data instanceof Map<?, ?> mappedData) {
					normalizedUpsert.put(id, mappedData);
				}
			}
			nextScope.put("upsert", normalizedUpsert);
		}

		return nextScope;
	}

	@SuppressWarnings("unchecked")
	private List<String> normalizeBattleChunkRefs(Object rawRefs) {
		List<String> normalized = new ArrayList<>();
		if (!(rawRefs instanceof List<?> refs)) {
			return normalized;
		}
		for (Object item : refs) {
			Map<String, Object> local = toBattleChunkLocalData(item, Map.of());
			Object chunkId = local.get("id");
			if (chunkId instanceof String id && !id.isBlank()) {
				normalized.add(id);
			}
		}
		return normalized;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toBattleChunkLocalData(Object rawRef, Object rawValue) {
		if (!(rawRef instanceof Map<?, ?> refMap)) {
			return Map.of();
		}
		Object rawCoord = refMap.get("coord");
		if (!(rawCoord instanceof Map<?, ?> coordMap)) {
			return Map.of();
		}

		String dimension = String.valueOf(refMap.get("dimension") == null ? "" : refMap.get("dimension")).trim();
		Integer chunkX = coerceInteger(coordMap.get("chunkX"));
		Integer chunkZ = coerceInteger(coordMap.get("chunkZ"));
		String syntheticId = buildBattleChunkSyntheticId(dimension, chunkX, chunkZ);
		if (syntheticId == null) {
			return Map.of();
		}

		Map<String, Object> data = new LinkedHashMap<>();
		if (rawValue instanceof Map<?, ?> valueMap) {
			for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
				data.put(String.valueOf(entry.getKey()), entry.getValue());
			}
		}
		data.put("dimension", dimension);
		data.put("chunkX", chunkX);
		data.put("chunkZ", chunkZ);

		Map<String, Object> local = new LinkedHashMap<>();
		local.put("id", syntheticId);
		local.put("data", data);
		return local;
	}

	private String buildBattleChunkSyntheticId(String dimension, Integer chunkX, Integer chunkZ) {
		if (dimension == null || dimension.isBlank() || chunkX == null || chunkZ == null) {
			return null;
		}
		return dimension + "|" + chunkX + "|" + chunkZ;
	}

	private Integer coerceInteger(Object value) {
		if (value instanceof Integer integer) {
			return integer;
		}
		if (value instanceof Long longValue) {
			return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE ? longValue.intValue() : null;
		}
		if (value instanceof Double doubleValue) {
			if (!Double.isFinite(doubleValue) || doubleValue % 1.0 != 0.0) {
				return null;
			}
			long longValue = doubleValue.longValue();
			return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE ? (int) longValue : null;
		}
		if (value instanceof Float floatValue) {
			if (!Float.isFinite(floatValue) || floatValue % 1.0f != 0.0f) {
				return null;
			}
			int intValue = floatValue.intValue();
			return intValue;
		}
		if (value instanceof String text) {
			String normalized = text.trim();
			if (normalized.isBlank()) {
				return null;
			}
			try {
				return Integer.parseInt(normalized);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private String readPacketType(Map<String, Object> body) {
		Object rawType = body.get("type");
		String type = rawType == null ? "" : String.valueOf(rawType).trim();
		if (type.isEmpty()) {
			throw new IllegalArgumentException("packet type is required");
		}
		return type;
	}

	private Map<String, Object> normalizeOutboundBody(Map<String, Object> body) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : body.entrySet()) {
			String key = entry.getKey();
			if ("type".equals(key) || "channel".equals(key)) {
				continue;
			}
			normalized.put(key, normalizeOutboundValue(entry.getValue()));
		}
		return normalized;
	}

	private Map<String, Object> normalizeNestedBundleBody(Map<String, Object> body) {
		Map<String, Object> normalized = normalizeOutboundBody(body);
		normalized.remove("submitPlayerId");
		return normalized;
	}

	private Map<String, Object> normalizeReplaceBody(Map<String, Object> body, String scopeKey) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		Map<String, Object> outbound = normalizeNestedBundleBody(body);
		Object scopeValue = outbound.get(scopeKey);
		if (scopeValue != null) {
			normalized.put(scopeKey, scopeValue);
		}
		return normalized;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> normalizePatchScopeBody(Map<String, Object> body) {
		Map<String, Object> normalized = normalizeNestedBundleBody(body);
		Object rawUpsert = normalized.get("upsert");
		if (rawUpsert instanceof Map<?, ?> upsertMap) {
			List<Map<String, Object>> upsertList = new ArrayList<>();
			for (Map.Entry<?, ?> entry : upsertMap.entrySet()) {
				String id = String.valueOf(entry.getKey());
				Object data = entry.getValue();
				Map<String, Object> next = new LinkedHashMap<>();
				next.put("id", id);
				next.put("data", data instanceof Map<?, ?> ? data : Map.of());
				upsertList.add(next);
			}
			normalized.put("upsert", upsertList);
		}
		return normalized;
	}

	private Map<String, Object> wrapBundleBody(
			Map<String, Object> body,
			String bundleField,
			Map<String, Object> nestedPayload
	) {
		Map<String, Object> wrapped = new LinkedHashMap<>();
		Object submitPlayerId = normalizeOutboundValue(body.get("submitPlayerId"));
		if (submitPlayerId != null) {
			wrapped.put("submitPlayerId", submitPlayerId);
		}
		wrapped.put(bundleField, nestedPayload);
		return wrapped;
	}

	@SuppressWarnings("unchecked")
	private Object normalizeOutboundValue(Object value) {
		if (value instanceof byte[] bytes) {
			String canonical = UuidBinaryCodec.toCanonicalString(bytes);
			return canonical != null ? canonical : bytes;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> normalized = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				normalized.put(String.valueOf(entry.getKey()), normalizeOutboundValue(entry.getValue()));
			}
			return normalized;
		}
		if (value instanceof List<?> list) {
			List<Object> normalized = new ArrayList<>(list.size());
			for (Object item : list) {
				normalized.add(normalizeOutboundValue(item));
			}
			return normalized;
		}
		return value;
	}
}
