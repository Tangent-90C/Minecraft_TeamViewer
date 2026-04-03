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
			Object rawType = body.get("type");
			String type = rawType == null ? "" : String.valueOf(rawType).trim();
			if (type.isEmpty()) {
				throw new IllegalArgumentException("packet type is required");
			}

			WireEnvelope.Builder envelope = WireEnvelope.newBuilder();
			envelope.setChannel(WireChannel.WIRE_CHANNEL_PLAYER);

			switch (type) {
				case "handshake": {
					HandshakeRequest.Builder builder = HandshakeRequest.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setHandshakeRequest(builder.build());
					break;
				}
				case "players_patch": {
					PlayersPatch.Builder builder = PlayersPatch.newBuilder();
					mergeJson(builder, normalizePlayerPatchBody(body));
					envelope.setPlayersPatch(builder.build());
					break;
				}
				case "entities_patch": {
					EntitiesPatch.Builder builder = EntitiesPatch.newBuilder();
					mergeJson(builder, normalizeEntityPatchBody(body));
					envelope.setEntitiesPatch(builder.build());
					break;
				}
				case "state_keepalive": {
					StateKeepalive.Builder builder = StateKeepalive.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setStateKeepalive(builder.build());
					break;
				}
				case "source_state_clear": {
					SourceStateClear.Builder builder = SourceStateClear.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setSourceStateClear(builder.build());
					break;
				}
				case "waypoints_update": {
					WaypointsUpdate.Builder builder = WaypointsUpdate.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setWaypointsUpdate(builder.build());
					break;
				}
				case "tab_players_update": {
					TabPlayersUpdate.Builder builder = TabPlayersUpdate.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setTabPlayersUpdate(builder.build());
					break;
				}
				case "tab_players_patch": {
					TabPlayersPatch.Builder builder = TabPlayersPatch.newBuilder();
					mergeJson(builder, normalizeTabPlayersPatchBody(body));
					envelope.setTabPlayersPatch(builder.build());
					break;
				}
				case "waypoints_delete": {
					WaypointsDelete.Builder builder = WaypointsDelete.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setWaypointsDelete(builder.build());
					break;
				}
				case "waypoints_entity_death_cancel": {
					WaypointsEntityDeathCancel.Builder builder = WaypointsEntityDeathCancel.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setWaypointsEntityDeathCancel(builder.build());
					break;
				}
				case "battle_map_observation": {
					BattleMapObservation.Builder builder = BattleMapObservation.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setBattleMapObservation(builder.build());
					break;
				}
				case "resync_req": {
					ResyncRequest.Builder builder = ResyncRequest.newBuilder();
					mergeJson(builder, normalizeOutboundBody(body));
					envelope.setResyncRequest(builder.build());
					break;
				}
				default:
					throw new IllegalArgumentException("Unsupported protobuf packet type: " + type);
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
				case SNAPSHOT_FULL -> withType(packetTypeName, messageToMap(envelope.getSnapshotFull()));
				case PATCH -> normalizePatchInbound(envelope.getPatch(), packetTypeName);
				case DIGEST -> normalizeDigestInbound(envelope.getDigest(), packetTypeName);
				case REFRESH_REQUEST -> withType(packetTypeName, messageToMap(envelope.getRefreshRequest()));
				case REPORT_RATE_HINT -> withType(packetTypeName, messageToMap(envelope.getReportRateHint()));
				case WAYPOINTS_UPDATE -> withType(packetTypeName, messageToMap(envelope.getWaypointsUpdate()));
				case WAYPOINTS_DELETE -> withType(packetTypeName, messageToMap(envelope.getWaypointsDelete()));
				default -> throw new IllegalArgumentException("Unsupported protobuf payload case: " + envelope.getPayloadCase());
			};

			return objectMapper.convertValue(normalized, packetType);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to decode protobuf payload", e);
		}
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
			case WAYPOINTS_UPDATE -> "waypoints_update";
			case WAYPOINTS_DELETE -> "waypoints_delete";
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

	private Map<String, Object> normalizePatchInbound(Patch patch, String packetType) throws Exception {
		Map<String, Object> payload = messageToMap(patch);
		normalizeInboundPatchScope(payload, "players");
		normalizeInboundPatchScope(payload, "entities");
		normalizeInboundPatchScope(payload, "waypoints");
		normalizeInboundPatchScope(payload, "battleChunks");
		normalizeInboundPatchScope(payload, "playerMarks");
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

	@SuppressWarnings("unchecked")
	private Object normalizeOutboundValue(Object value) {
		if (value instanceof byte[] bytes) {
			String canonical = UuidBinaryCodec.toCanonicalString(bytes);
			return canonical != null ? canonical : bytes;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> normalized = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = String.valueOf(entry.getKey());
				normalized.put(key, normalizeOutboundValue(entry.getValue()));
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

	@SuppressWarnings("unchecked")
	private Map<String, Object> normalizePlayerPatchBody(Map<String, Object> body) {
		Map<String, Object> normalized = normalizeOutboundBody(body);
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

	private Map<String, Object> normalizeEntityPatchBody(Map<String, Object> body) {
		return normalizePlayerPatchBody(body);
	}

	private Map<String, Object> normalizeTabPlayersPatchBody(Map<String, Object> body) {
		return normalizePlayerPatchBody(body);
	}
}
