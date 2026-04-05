package fun.prof_chen.teamviewer.main_code.network.protocol;

import fun.prof_chen.teamviewer.main_code.network.proto.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtobufMessageCodec implements MessageCodec {
	@Override
	public byte[] encode(Object packet) {
		try {
			WireEnvelope.Builder envelope = WireEnvelope.newBuilder().setChannel(WireChannel.WIRE_CHANNEL_PLAYER);
			if (packet instanceof ProtocolPackets.HandshakePacket handshake) {
				envelope.setPlayerHandshakeRequest(buildPlayerHandshake(handshake));
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.PlayersPatchPacket playersPatch) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(playersPatch.submitPlayerId))
								.setPlayersPatch(buildPlayerPatchScope(playersPatch.upsert, playersPatch.delete))
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.EntitiesPatchPacket entitiesPatch) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(entitiesPatch.submitPlayerId))
								.setEntitiesPatch(buildEntityPatchScope(entitiesPatch.upsert, entitiesPatch.delete))
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.WaypointsUpdatePacket waypointsUpdate) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(waypointsUpdate.submitPlayerId))
								.setWaypointsReplace(buildWaypointsReplace(waypointsUpdate.waypoints))
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.SourceStateClearPacket stateClear) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(stateClear.submitPlayerId))
								.setSourceStateClear(
										SourceStateClear.newBuilder().addAllScopes(cleanStringList(stateClear.scopes)).build()
								)
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.TabPlayersPatchPacket tabPlayersPatch) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(tabPlayersPatch.submitPlayerId))
								.setTabPlayersPatch(buildTabPlayersPatchScope(tabPlayersPatch.upsert, tabPlayersPatch.delete))
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.WaypointsDeletePacket waypointsDelete) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(waypointsDelete.submitPlayerId))
								.setWaypointsDelete(
										WaypointsDelete.newBuilder()
												.addAllWaypointIds(cleanStringList(waypointsDelete.waypointIds))
												.build()
								)
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.BattleMapObservationPacket observation) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(observation.submitPlayerId))
								.setBattleMapObservation(buildBattleMapObservation(observation))
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.WaypointsEntityDeathCancelPacket deathCancel) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(deathCancel.submitPlayerId))
								.setWaypointsEntityDeathCancel(
										WaypointsEntityDeathCancel.newBuilder()
												.addAllTargetEntityIds(cleanStringList(deathCancel.targetEntityIds))
												.build()
								)
								.build()
				);
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.ResyncReqPacket resyncReq) {
				ResyncRequest.Builder builder = ResyncRequest.newBuilder();
				String reason = normalizeText(resyncReq.reason);
				if (reason != null) {
					builder.setReason(reason);
				}
				envelope.setResyncRequest(builder.build());
				return envelope.build().toByteArray();
			}
			if (packet instanceof ProtocolPackets.StateKeepalivePacket keepalive) {
				envelope.setPlayerReportBundle(
						PlayerReportBundle.newBuilder()
								.setSubmitPlayerId(uuidString(keepalive.submitPlayerId))
								.setStateKeepalive(
										StateKeepalive.newBuilder()
												.addAllPlayers(cleanStringList(keepalive.players))
												.addAllEntities(cleanStringList(keepalive.entities))
												.addAllBattleChunks(buildBattleChunkRefs(keepalive.battleChunks))
												.build()
								)
								.build()
				);
				return envelope.build().toByteArray();
			}
			throw new IllegalArgumentException("Unsupported protobuf packet type: " + packet);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to encode protobuf payload", e);
		}
	}

	@Override
	public ProtocolPackets.DecodedInboundMessage decode(byte[] payload) {
		try {
			WireEnvelope envelope = WireEnvelope.parseFrom(payload);
			return switch (envelope.getPayloadCase()) {
				case HANDSHAKE_ACK -> new ProtocolPackets.DecodedInboundMessage(
						"handshake_ack",
						decodeHandshakeAck(envelope.getHandshakeAck())
				);
				case SNAPSHOT_FULL -> new ProtocolPackets.DecodedInboundMessage(
						"snapshot_full",
						decodeSnapshotFull(envelope.getSnapshotFull())
				);
				case PATCH -> new ProtocolPackets.DecodedInboundMessage(
						"patch",
						decodePatch(envelope.getPatch())
				);
				case DIGEST -> new ProtocolPackets.DecodedInboundMessage(
						"digest",
						decodeDigest(envelope.getDigest())
				);
				case REFRESH_REQUEST -> new ProtocolPackets.DecodedInboundMessage(
						"refresh_req",
						decodeRefreshRequest(envelope.getRefreshRequest())
				);
				case REPORT_RATE_HINT -> new ProtocolPackets.DecodedInboundMessage(
						"report_rate_hint",
						decodeReportRateHint(envelope.getReportRateHint())
				);
				default -> throw new IllegalArgumentException("Unsupported protobuf payload case: " + envelope.getPayloadCase());
			};
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to decode protobuf payload", e);
		}
	}

	private PlayerHandshakeRequest buildPlayerHandshake(ProtocolPackets.HandshakePacket packet) {
		PlayerHandshakeRequest.Builder builder = PlayerHandshakeRequest.newBuilder();
		builder.setNetworkProtocolVersion(defaultString(packet.networkProtocolVersion));
		builder.setMinimumCompatibleNetworkProtocolVersion(defaultString(packet.minimumCompatibleNetworkProtocolVersion));
		builder.setLocalProgramVersion(defaultString(packet.localProgramVersion));
		String submitPlayerId = uuidString(packet.submitPlayerId);
		if (submitPlayerId != null) {
			builder.setSubmitPlayerId(submitPlayerId);
		}
		setOptionalString(builder::setRoomCode, normalizeText(packet.roomCode));
		setOptionalInt(builder::setPreferredReportIntervalTicks, packet.preferredReportIntervalTicks);
		setOptionalInt(builder::setMinReportIntervalTicks, packet.minReportIntervalTicks);
		setOptionalInt(builder::setMaxReportIntervalTicks, packet.maxReportIntervalTicks);
		return builder.build();
	}

	private PlayerPatchScope buildPlayerPatchScope(Map<String, Map<String, Object>> upsert, List<String> delete) {
		PlayerPatchScope.Builder builder = PlayerPatchScope.newBuilder();
		for (Map.Entry<String, Map<String, Object>> entry : safeMap(upsert).entrySet()) {
			String id = normalizeText(entry.getKey());
			if (id == null) {
				continue;
			}
			PlayerUpsert.Builder upsertBuilder = PlayerUpsert.newBuilder().setId(id);
			upsertBuilder.setData(buildPlayerDelta(entry.getValue()));
			builder.addUpsert(upsertBuilder.build());
		}
		builder.addAllDelete(cleanStringList(delete));
		return builder.build();
	}

	private EntityPatchScope buildEntityPatchScope(Map<String, Map<String, Object>> upsert, List<String> delete) {
		EntityPatchScope.Builder builder = EntityPatchScope.newBuilder();
		for (Map.Entry<String, Map<String, Object>> entry : safeMap(upsert).entrySet()) {
			String id = normalizeText(entry.getKey());
			if (id == null) {
				continue;
			}
			EntityUpsert.Builder upsertBuilder = EntityUpsert.newBuilder().setId(id);
			upsertBuilder.setData(buildEntityDelta(entry.getValue()));
			builder.addUpsert(upsertBuilder.build());
		}
		builder.addAllDelete(cleanStringList(delete));
		return builder.build();
	}

	private WaypointsReplace buildWaypointsReplace(Map<String, Map<String, Object>> waypoints) {
		WaypointsReplace.Builder builder = WaypointsReplace.newBuilder();
		for (Map.Entry<String, Map<String, Object>> entry : safeMap(waypoints).entrySet()) {
			String id = normalizeText(entry.getKey());
			if (id == null) {
				continue;
			}
			builder.putWaypoints(id, buildWaypointData(entry.getValue()));
		}
		return builder.build();
	}

	private TabPlayersPatchScope buildTabPlayersPatchScope(Map<String, Map<String, Object>> upsert, List<String> delete) {
		TabPlayersPatchScope.Builder builder = TabPlayersPatchScope.newBuilder();
		for (Map.Entry<String, Map<String, Object>> entry : safeMap(upsert).entrySet()) {
			String id = normalizeText(entry.getKey());
			if (id == null) {
				continue;
			}
			builder.addUpsert(
					TabPlayerUpsert.newBuilder()
							.setKey(id)
							.setData(buildTabPlayerEntry(entry.getValue()))
							.build()
			);
		}
		builder.addAllDelete(cleanStringList(delete));
		return builder.build();
	}

	private BattleMapObservation buildBattleMapObservation(ProtocolPackets.BattleMapObservationPacket packet) {
		BattleMapObservation.Builder builder = BattleMapObservation.newBuilder();
		builder.setDimension(defaultString(packet.dimension));
		builder.setMapSize(defaultInt(packet.mapSize));
		builder.setAnchorRow(defaultInt(packet.anchorRow));
		builder.setAnchorCol(defaultInt(packet.anchorCol));
		builder.setSnapshotObservedAt(defaultLong(packet.snapshotObservedAt));
		builder.setParsedAt(defaultLong(packet.parsedAt));
		for (Map<String, Object> item : safeListOfMaps(packet.candidates)) {
			BattleMapObservationCandidate.Builder candidate = BattleMapObservationCandidate.newBuilder();
			setOptionalInt(candidate::setBaseChunkX, toIntegerOrNull(item.get("baseChunkX")));
			setOptionalInt(candidate::setBaseChunkZ, toIntegerOrNull(item.get("baseChunkZ")));
			setOptionalLong(candidate::setPositionSampledAt, toLongOrNull(item.get("positionSampledAt")));
			setOptionalString(candidate::setSource, normalizeText(item.get("source")));
			builder.addCandidates(candidate.build());
		}
		for (Map<String, Object> item : safeListOfMaps(packet.cells)) {
			BattleMapObservationCell.Builder cell = BattleMapObservationCell.newBuilder();
			setOptionalInt(cell::setRelChunkX, toIntegerOrNull(item.get("relChunkX")));
			setOptionalInt(cell::setRelChunkZ, toIntegerOrNull(item.get("relChunkZ")));
			setOptionalString(cell::setSymbol, normalizeText(item.get("symbol")));
			String colorRaw = normalizeText(item.get("colorRaw"));
			cell.setColorRaw(colorRaw == null ? "" : colorRaw);
			builder.addCells(cell.build());
		}
		return builder.build();
	}

	private PlayerDelta buildPlayerDelta(Map<String, Object> raw) {
		Map<String, Object> value = safeObjectMap(raw);
		PlayerDelta.Builder builder = PlayerDelta.newBuilder();
		setOptionalDouble(builder::setX, toDoubleOrNull(value.get("x")));
		setOptionalDouble(builder::setY, toDoubleOrNull(value.get("y")));
		setOptionalDouble(builder::setZ, toDoubleOrNull(value.get("z")));
		setOptionalDouble(builder::setVx, toDoubleOrNull(value.get("vx")));
		setOptionalDouble(builder::setVy, toDoubleOrNull(value.get("vy")));
		setOptionalDouble(builder::setVz, toDoubleOrNull(value.get("vz")));
		setOptionalString(builder::setDimension, normalizeText(value.get("dimension")));
		setOptionalString(builder::setPlayerName, normalizeText(value.get("playerName")));
		setOptionalString(builder::setPlayerUuid, normalizeText(value.get("playerUUID")));
		setOptionalDouble(builder::setHealth, toDoubleOrNull(value.get("health")));
		setOptionalDouble(builder::setMaxHealth, toDoubleOrNull(value.get("maxHealth")));
		setOptionalDouble(builder::setArmor, toDoubleOrNull(value.get("armor")));
		setOptionalBool(builder::setIsRiding, toBooleanOrNull(value.get("isRiding")));
		setOptionalDouble(builder::setWidth, toDoubleOrNull(value.get("width")));
		setOptionalDouble(builder::setHeight, toDoubleOrNull(value.get("height")));
		return builder.build();
	}

	private EntityDelta buildEntityDelta(Map<String, Object> raw) {
		Map<String, Object> value = safeObjectMap(raw);
		EntityDelta.Builder builder = EntityDelta.newBuilder();
		setOptionalDouble(builder::setX, toDoubleOrNull(value.get("x")));
		setOptionalDouble(builder::setY, toDoubleOrNull(value.get("y")));
		setOptionalDouble(builder::setZ, toDoubleOrNull(value.get("z")));
		setOptionalDouble(builder::setVx, toDoubleOrNull(value.get("vx")));
		setOptionalDouble(builder::setVy, toDoubleOrNull(value.get("vy")));
		setOptionalDouble(builder::setVz, toDoubleOrNull(value.get("vz")));
		setOptionalString(builder::setDimension, normalizeText(value.get("dimension")));
		setOptionalString(builder::setEntityType, normalizeText(value.get("entityType")));
		setOptionalString(builder::setEntityName, normalizeText(value.get("entityName")));
		setOptionalDouble(builder::setWidth, toDoubleOrNull(value.get("width")));
		setOptionalDouble(builder::setHeight, toDoubleOrNull(value.get("height")));
		return builder.build();
	}

	private WaypointData buildWaypointData(Map<String, Object> raw) {
		Map<String, Object> value = safeObjectMap(raw);
		WaypointData.Builder builder = WaypointData.newBuilder();
		builder.setX(defaultDouble(toDoubleOrNull(value.get("x"))));
		builder.setY(defaultDouble(toDoubleOrNull(value.get("y"))));
		builder.setZ(defaultDouble(toDoubleOrNull(value.get("z"))));
		builder.setDimension(defaultString(normalizeText(value.get("dimension"))));
		builder.setName(defaultString(normalizeText(value.get("name"))));
		setOptionalString(builder::setSymbol, normalizeText(value.get("symbol")));
		setOptionalInt(builder::setColor, toIntegerOrNull(value.get("color")));
		setOptionalString(builder::setOwnerId, normalizeText(value.get("ownerId")));
		setOptionalString(builder::setOwnerName, normalizeText(value.get("ownerName")));
		setOptionalLong(builder::setCreatedAt, toLongOrNull(value.get("createdAt")));
		setOptionalInt(builder::setTtlSeconds, toIntegerOrNull(value.get("ttlSeconds")));
		setOptionalString(builder::setWaypointKind, normalizeText(value.get("waypointKind")));
		setOptionalBool(builder::setReplaceOldQuick, toBooleanOrNull(value.get("replaceOldQuick")));
		setOptionalInt(builder::setMaxQuickMarks, toIntegerOrNull(value.get("maxQuickMarks")));
		setOptionalString(builder::setTargetType, normalizeText(value.get("targetType")));
		setOptionalString(builder::setTargetEntityId, normalizeText(value.get("targetEntityId")));
		setOptionalString(builder::setTargetEntityType, normalizeText(value.get("targetEntityType")));
		setOptionalString(builder::setTargetEntityName, normalizeText(value.get("targetEntityName")));
		setOptionalString(builder::setRoomCode, normalizeText(value.get("roomCode")));
		setOptionalBool(builder::setPermanent, toBooleanOrNull(value.get("permanent")));
		setOptionalString(builder::setTacticalType, normalizeText(value.get("tacticalType")));
		setOptionalString(builder::setSourceType, normalizeText(value.get("sourceType")));
		setOptionalString(builder::setDeletableBy, normalizeText(value.get("deletableBy")));
		return builder.build();
	}

	private TabPlayerEntry buildTabPlayerEntry(Map<String, Object> raw) {
		Map<String, Object> value = safeObjectMap(raw);
		TabPlayerEntry.Builder builder = TabPlayerEntry.newBuilder();
		setOptionalString(builder::setUuid, normalizeText(value.get("uuid")));
		setOptionalString(builder::setName, normalizeText(value.get("name")));
		setOptionalString(builder::setDisplayName, normalizeText(value.get("displayName")));
		setOptionalString(builder::setPrefixedName, normalizeText(value.get("prefixedName")));
		return builder.build();
	}

	private ProtocolPackets.HandshakeAckInboundPacket decodeHandshakeAck(HandshakeAck message) {
		ProtocolPackets.HandshakeAckInboundPacket packet = new ProtocolPackets.HandshakeAckInboundPacket();
		packet.type = "handshake_ack";
		packet.ready = message.getReady();
		packet.networkProtocolVersion = message.getNetworkProtocolVersion();
		packet.minimumCompatibleNetworkProtocolVersion = message.getMinimumCompatibleNetworkProtocolVersion();
		packet.localProgramVersion = message.getLocalProgramVersion();
		packet.programVersion = packet.localProgramVersion;
		packet.error = message.hasError() ? message.getError() : null;
		packet.rejectReason = message.hasRejectReason() ? message.getRejectReason() : null;
		packet.deltaEnabled = message.getDeltaEnabled();
		packet.digestIntervalSec = message.hasDigestIntervalSec() ? message.getDigestIntervalSec() : null;
		packet.broadcastHz = message.hasBroadcastHz() ? message.getBroadcastHz() : null;
		packet.reportIntervalTicks = message.hasReportIntervalTicks() ? message.getReportIntervalTicks() : null;
		packet.playerTimeoutSec = message.hasPlayerTimeoutSec() ? message.getPlayerTimeoutSec() : null;
		packet.entityTimeoutSec = message.hasEntityTimeoutSec() ? message.getEntityTimeoutSec() : null;
		packet.battleChunkTimeoutSec = message.hasBattleChunkTimeoutSec() ? message.getBattleChunkTimeoutSec() : null;
		return packet;
	}

	private ProtocolPackets.SnapshotFullInboundPacket decodeSnapshotFull(SnapshotFull message) {
		ProtocolPackets.SnapshotFullInboundPacket packet = new ProtocolPackets.SnapshotFullInboundPacket();
		packet.type = "snapshot_full";
		packet.players = decodePlayerDataMap(message.getPlayersMap());
		packet.entities = decodeEntityDataMap(message.getEntitiesMap());
		packet.waypoints = decodeWaypointDataMap(message.getWaypointsMap());
		packet.battleChunks = decodeBattleChunkSnapshot(message.getBattleChunksList());
		packet.playerMarks = decodePlayerMarkFullMap(message.getPlayerMarksMap());
		return packet;
	}

	private ProtocolPackets.PatchInboundPacket decodePatch(Patch message) {
		ProtocolPackets.PatchInboundPacket packet = new ProtocolPackets.PatchInboundPacket();
		packet.type = "patch";
		packet.players = message.hasPlayers() ? decodePlayerPatchScope(message.getPlayers()) : null;
		packet.entities = message.hasEntities() ? decodeEntityPatchScope(message.getEntities()) : null;
		packet.waypoints = message.hasWaypoints() ? decodeWaypointPatchScope(message.getWaypoints()) : null;
		packet.battleChunks = message.hasBattleChunks() ? decodeBattleChunkPatchScope(message.getBattleChunks()) : null;
		packet.playerMarks = message.hasPlayerMarks() ? decodePlayerMarkPatchScope(message.getPlayerMarks()) : null;
		packet.meta = null;
		return packet;
	}

	private ProtocolPackets.DigestInboundPacket decodeDigest(Digest message) {
		ProtocolPackets.DigestInboundPacket packet = new ProtocolPackets.DigestInboundPacket();
		packet.type = "digest";
		packet.hashes = new LinkedHashMap<>();
		packet.hashes.put("players", message.getPlayers());
		packet.hashes.put("entities", message.getEntities());
		packet.hashes.put("waypoints", message.getWaypoints());
		if (message.hasBattleChunks()) {
			packet.hashes.put("battleChunks", message.getBattleChunks());
		}
		return packet;
	}

	private ProtocolPackets.RefreshReqInboundPacket decodeRefreshRequest(RefreshRequest message) {
		ProtocolPackets.RefreshReqInboundPacket packet = new ProtocolPackets.RefreshReqInboundPacket();
		packet.type = "refresh_req";
		packet.players = new ArrayList<>(message.getPlayersList());
		packet.entities = new ArrayList<>(message.getEntitiesList());
		packet.battleChunks = decodeBattleChunkDeleteRefs(message.getBattleChunksList());
		packet.reason = message.getReason();
		return packet;
	}

	private ProtocolPackets.ReportRateHintInboundPacket decodeReportRateHint(ReportRateHint message) {
		ProtocolPackets.ReportRateHintInboundPacket packet = new ProtocolPackets.ReportRateHintInboundPacket();
		packet.type = "report_rate_hint";
		packet.reportIntervalTicks = message.getReportIntervalTicks();
		packet.broadcastHz = message.getBroadcastHz();
		packet.reason = message.hasReason() ? message.getReason() : null;
		return packet;
	}

	private Map<String, Object> decodePlayerDataMap(Map<String, PlayerData> raw) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		for (Map.Entry<String, PlayerData> entry : raw.entrySet()) {
			mapped.put(entry.getKey(), playerDataToMap(entry.getValue()));
		}
		return mapped;
	}

	private Map<String, Object> decodeEntityDataMap(Map<String, EntityData> raw) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		for (Map.Entry<String, EntityData> entry : raw.entrySet()) {
			mapped.put(entry.getKey(), entityDataToMap(entry.getValue()));
		}
		return mapped;
	}

	private Map<String, Object> decodeWaypointDataMap(Map<String, WaypointData> raw) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		for (Map.Entry<String, WaypointData> entry : raw.entrySet()) {
			mapped.put(entry.getKey(), waypointDataToMap(entry.getValue()));
		}
		return mapped;
	}

	private Map<String, Object> decodePlayerMarkFullMap(Map<String, PlayerMark> raw) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		for (Map.Entry<String, PlayerMark> entry : raw.entrySet()) {
			mapped.put(entry.getKey(), playerMarkToMap(entry.getValue()));
		}
		return mapped;
	}

	private Map<String, Object> decodePlayerPatchScope(PlayerPatchScope scope) {
		Map<String, Object> patch = new LinkedHashMap<>();
		Map<String, Object> upsert = new LinkedHashMap<>();
		for (PlayerUpsert item : scope.getUpsertList()) {
			if (item.getId().isBlank()) {
				continue;
			}
			upsert.put(item.getId(), playerDeltaToMap(item.getData()));
		}
		patch.put("upsert", upsert);
		patch.put("delete", new ArrayList<>(scope.getDeleteList()));
		return patch;
	}

	private Map<String, Object> decodeEntityPatchScope(EntityPatchScope scope) {
		Map<String, Object> patch = new LinkedHashMap<>();
		Map<String, Object> upsert = new LinkedHashMap<>();
		for (EntityUpsert item : scope.getUpsertList()) {
			if (item.getId().isBlank()) {
				continue;
			}
			upsert.put(item.getId(), entityDeltaToMap(item.getData()));
		}
		patch.put("upsert", upsert);
		patch.put("delete", new ArrayList<>(scope.getDeleteList()));
		return patch;
	}

	private Map<String, Object> decodeWaypointPatchScope(WaypointPatchScope scope) {
		Map<String, Object> patch = new LinkedHashMap<>();
		Map<String, Object> upsert = new LinkedHashMap<>();
		for (WaypointUpsert item : scope.getUpsertList()) {
			if (item.getId().isBlank()) {
				continue;
			}
			upsert.put(item.getId(), waypointDeltaToMap(item.getData()));
		}
		patch.put("upsert", upsert);
		patch.put("delete", new ArrayList<>(scope.getDeleteList()));
		return patch;
	}

	private Map<String, Object> decodeBattleChunkPatchScope(BattleChunkPatchScope scope) {
		Map<String, Object> patch = new LinkedHashMap<>();
		Map<String, Object> upsert = new LinkedHashMap<>();
		for (BattleChunkUpsert item : scope.getUpsertList()) {
			String chunkId = buildBattleChunkSyntheticId(item.getRef().getDimension(), item.getRef().getCoord().getChunkX(), item.getRef().getCoord().getChunkZ());
			if (chunkId == null) {
				continue;
			}
			upsert.put(chunkId, battleChunkValueToMap(item.getData(), item.getRef()));
		}
		patch.put("upsert", upsert);
		patch.put("delete", decodeBattleChunkDeleteRefs(scope.getDeleteList()));
		return patch;
	}

	private Map<String, Object> decodePlayerMarkPatchScope(PlayerMarkPatchScope scope) {
		Map<String, Object> patch = new LinkedHashMap<>();
		Map<String, Object> upsert = new LinkedHashMap<>();
		for (PlayerMarkUpsert item : scope.getUpsertList()) {
			if (item.getId().isBlank()) {
				continue;
			}
			upsert.put(item.getId(), playerMarkToMap(item.getData()));
		}
		patch.put("upsert", upsert);
		patch.put("delete", new ArrayList<>(scope.getDeleteList()));
		return patch;
	}

	private Map<String, Object> decodeBattleChunkSnapshot(List<BattleChunkEntry> entries) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		for (BattleChunkEntry entry : entries) {
			String chunkId = buildBattleChunkSyntheticId(entry.getRef().getDimension(), entry.getRef().getCoord().getChunkX(), entry.getRef().getCoord().getChunkZ());
			if (chunkId == null) {
				continue;
			}
			mapped.put(chunkId, battleChunkValueToMap(entry.getData(), entry.getRef()));
		}
		return mapped;
	}

	private List<String> decodeBattleChunkDeleteRefs(List<BattleChunkRef> refs) {
		List<String> delete = new ArrayList<>();
		for (BattleChunkRef ref : refs) {
			String chunkId = buildBattleChunkSyntheticId(ref.getDimension(), ref.getCoord().getChunkX(), ref.getCoord().getChunkZ());
			if (chunkId != null) {
				delete.add(chunkId);
			}
		}
		return delete;
	}

	private Map<String, Object> playerDataToMap(PlayerData value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		mapped.put("x", value.getX());
		mapped.put("y", value.getY());
		mapped.put("z", value.getZ());
		if (value.hasVx()) mapped.put("vx", value.getVx());
		if (value.hasVy()) mapped.put("vy", value.getVy());
		if (value.hasVz()) mapped.put("vz", value.getVz());
		mapped.put("dimension", value.getDimension());
		if (value.hasPlayerName()) mapped.put("playerName", value.getPlayerName());
		if (value.hasPlayerUuid()) mapped.put("playerUUID", value.getPlayerUuid());
		if (value.hasHealth()) mapped.put("health", value.getHealth());
		if (value.hasMaxHealth()) mapped.put("maxHealth", value.getMaxHealth());
		if (value.hasArmor()) mapped.put("armor", value.getArmor());
		if (value.hasIsRiding()) mapped.put("isRiding", value.getIsRiding());
		if (value.hasWidth()) mapped.put("width", value.getWidth());
		if (value.hasHeight()) mapped.put("height", value.getHeight());
		return mapped;
	}

	private Map<String, Object> playerDeltaToMap(PlayerDelta value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		if (value.hasX()) mapped.put("x", value.getX());
		if (value.hasY()) mapped.put("y", value.getY());
		if (value.hasZ()) mapped.put("z", value.getZ());
		if (value.hasVx()) mapped.put("vx", value.getVx());
		if (value.hasVy()) mapped.put("vy", value.getVy());
		if (value.hasVz()) mapped.put("vz", value.getVz());
		if (value.hasDimension()) mapped.put("dimension", value.getDimension());
		if (value.hasPlayerName()) mapped.put("playerName", value.getPlayerName());
		if (value.hasPlayerUuid()) mapped.put("playerUUID", value.getPlayerUuid());
		if (value.hasHealth()) mapped.put("health", value.getHealth());
		if (value.hasMaxHealth()) mapped.put("maxHealth", value.getMaxHealth());
		if (value.hasArmor()) mapped.put("armor", value.getArmor());
		if (value.hasIsRiding()) mapped.put("isRiding", value.getIsRiding());
		if (value.hasWidth()) mapped.put("width", value.getWidth());
		if (value.hasHeight()) mapped.put("height", value.getHeight());
		return mapped;
	}

	private Map<String, Object> entityDataToMap(EntityData value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		mapped.put("x", value.getX());
		mapped.put("y", value.getY());
		mapped.put("z", value.getZ());
		if (value.hasVx()) mapped.put("vx", value.getVx());
		if (value.hasVy()) mapped.put("vy", value.getVy());
		if (value.hasVz()) mapped.put("vz", value.getVz());
		mapped.put("dimension", value.getDimension());
		if (value.hasEntityType()) mapped.put("entityType", value.getEntityType());
		if (value.hasEntityName()) mapped.put("entityName", value.getEntityName());
		if (value.hasWidth()) mapped.put("width", value.getWidth());
		if (value.hasHeight()) mapped.put("height", value.getHeight());
		return mapped;
	}

	private Map<String, Object> entityDeltaToMap(EntityDelta value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		if (value.hasX()) mapped.put("x", value.getX());
		if (value.hasY()) mapped.put("y", value.getY());
		if (value.hasZ()) mapped.put("z", value.getZ());
		if (value.hasVx()) mapped.put("vx", value.getVx());
		if (value.hasVy()) mapped.put("vy", value.getVy());
		if (value.hasVz()) mapped.put("vz", value.getVz());
		if (value.hasDimension()) mapped.put("dimension", value.getDimension());
		if (value.hasEntityType()) mapped.put("entityType", value.getEntityType());
		if (value.hasEntityName()) mapped.put("entityName", value.getEntityName());
		if (value.hasWidth()) mapped.put("width", value.getWidth());
		if (value.hasHeight()) mapped.put("height", value.getHeight());
		return mapped;
	}

	private Map<String, Object> waypointDataToMap(WaypointData value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		mapped.put("x", value.getX());
		mapped.put("y", value.getY());
		mapped.put("z", value.getZ());
		mapped.put("dimension", value.getDimension());
		mapped.put("name", value.getName());
		if (value.hasSymbol()) mapped.put("symbol", value.getSymbol());
		if (value.hasColor()) mapped.put("color", value.getColor());
		if (value.hasOwnerId()) mapped.put("ownerId", value.getOwnerId());
		if (value.hasOwnerName()) mapped.put("ownerName", value.getOwnerName());
		if (value.hasCreatedAt()) mapped.put("createdAt", value.getCreatedAt());
		if (value.hasTtlSeconds()) mapped.put("ttlSeconds", value.getTtlSeconds());
		if (value.hasWaypointKind()) mapped.put("waypointKind", value.getWaypointKind());
		if (value.hasReplaceOldQuick()) mapped.put("replaceOldQuick", value.getReplaceOldQuick());
		if (value.hasMaxQuickMarks()) mapped.put("maxQuickMarks", value.getMaxQuickMarks());
		if (value.hasTargetType()) mapped.put("targetType", value.getTargetType());
		if (value.hasTargetEntityId()) mapped.put("targetEntityId", value.getTargetEntityId());
		if (value.hasTargetEntityType()) mapped.put("targetEntityType", value.getTargetEntityType());
		if (value.hasTargetEntityName()) mapped.put("targetEntityName", value.getTargetEntityName());
		if (value.hasRoomCode()) mapped.put("roomCode", value.getRoomCode());
		if (value.hasPermanent()) mapped.put("permanent", value.getPermanent());
		if (value.hasTacticalType()) mapped.put("tacticalType", value.getTacticalType());
		if (value.hasSourceType()) mapped.put("sourceType", value.getSourceType());
		if (value.hasDeletableBy()) mapped.put("deletableBy", value.getDeletableBy());
		return mapped;
	}

	private Map<String, Object> waypointDeltaToMap(WaypointDelta value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		if (value.hasX()) mapped.put("x", value.getX());
		if (value.hasY()) mapped.put("y", value.getY());
		if (value.hasZ()) mapped.put("z", value.getZ());
		if (value.hasDimension()) mapped.put("dimension", value.getDimension());
		if (value.hasName()) mapped.put("name", value.getName());
		if (value.hasSymbol()) mapped.put("symbol", value.getSymbol());
		if (value.hasColor()) mapped.put("color", value.getColor());
		if (value.hasOwnerId()) mapped.put("ownerId", value.getOwnerId());
		if (value.hasOwnerName()) mapped.put("ownerName", value.getOwnerName());
		if (value.hasCreatedAt()) mapped.put("createdAt", value.getCreatedAt());
		if (value.hasTtlSeconds()) mapped.put("ttlSeconds", value.getTtlSeconds());
		if (value.hasWaypointKind()) mapped.put("waypointKind", value.getWaypointKind());
		if (value.hasReplaceOldQuick()) mapped.put("replaceOldQuick", value.getReplaceOldQuick());
		if (value.hasMaxQuickMarks()) mapped.put("maxQuickMarks", value.getMaxQuickMarks());
		if (value.hasTargetType()) mapped.put("targetType", value.getTargetType());
		if (value.hasTargetEntityId()) mapped.put("targetEntityId", value.getTargetEntityId());
		if (value.hasTargetEntityType()) mapped.put("targetEntityType", value.getTargetEntityType());
		if (value.hasTargetEntityName()) mapped.put("targetEntityName", value.getTargetEntityName());
		if (value.hasRoomCode()) mapped.put("roomCode", value.getRoomCode());
		if (value.hasPermanent()) mapped.put("permanent", value.getPermanent());
		if (value.hasTacticalType()) mapped.put("tacticalType", value.getTacticalType());
		if (value.hasSourceType()) mapped.put("sourceType", value.getSourceType());
		if (value.hasDeletableBy()) mapped.put("deletableBy", value.getDeletableBy());
		return mapped;
	}

	private Map<String, Object> playerMarkToMap(PlayerMark value) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		mapped.put("team", value.getTeam());
		if (value.hasColor()) mapped.put("color", value.getColor());
		if (value.hasLabel()) mapped.put("label", value.getLabel());
		if (value.hasSource()) mapped.put("source", value.getSource());
		return mapped;
	}

	private Map<String, Object> battleChunkValueToMap(BattleChunkValue value, BattleChunkRef ref) {
		Map<String, Object> mapped = new LinkedHashMap<>();
		mapped.put("dimension", ref.getDimension());
		mapped.put("chunkX", ref.getCoord().getChunkX());
		mapped.put("chunkZ", ref.getCoord().getChunkZ());
		if (value.hasSymbol()) mapped.put("symbol", value.getSymbol());
		if (value.hasMarkerType()) mapped.put("markerType", value.getMarkerType());
		mapped.put("colorRaw", value.getColorRaw());
		if (value.hasColorNote()) mapped.put("colorNote", value.getColorNote());
		if (value.hasRoomCode()) mapped.put("roomCode", value.getRoomCode());
		if (value.hasColorMode()) mapped.put("colorMode", value.getColorMode());
		if (value.hasColorSemanticKey()) mapped.put("colorSemanticKey", value.getColorSemanticKey());
		if (value.hasObservedAt()) mapped.put("observedAt", value.getObservedAt());
		if (value.hasPositionSampledAt()) mapped.put("positionSampledAt", value.getPositionSampledAt());
		if (value.hasAlignmentSource()) mapped.put("alignmentSource", value.getAlignmentSource());
		if (value.hasReporterId()) mapped.put("reporterId", value.getReporterId());
		return mapped;
	}

	private List<BattleChunkRef> buildBattleChunkRefs(List<String> chunkIds) {
		List<BattleChunkRef> refs = new ArrayList<>();
		for (String chunkId : cleanStringList(chunkIds)) {
			String[] parts = chunkId.split("\\|");
			if (parts.length != 3) {
				continue;
			}
			Integer chunkX = toIntegerOrNull(parts[1]);
			Integer chunkZ = toIntegerOrNull(parts[2]);
			String dimension = normalizeText(parts[0]);
			if (dimension == null || chunkX == null || chunkZ == null) {
				continue;
			}
			refs.add(
					BattleChunkRef.newBuilder()
							.setDimension(dimension)
							.setCoord(
									BattleChunkCoord.newBuilder()
											.setChunkX(chunkX)
											.setChunkZ(chunkZ)
											.build()
							)
							.build()
			);
		}
		return refs;
	}

	private String buildBattleChunkSyntheticId(String dimension, int chunkX, int chunkZ) {
		String normalizedDimension = normalizeText(dimension);
		if (normalizedDimension == null) {
			return null;
		}
		return normalizedDimension + "|" + chunkX + "|" + chunkZ;
	}

	private static Map<String, Map<String, Object>> safeMap(Map<String, Map<String, Object>> value) {
		return value == null ? Map.of() : value;
	}

	private static Map<String, Object> safeObjectMap(Map<String, Object> value) {
		return value == null ? Map.of() : value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> safeListOfMaps(List<Map<String, Object>> value) {
		return value == null ? List.of() : value;
	}

	private static List<String> cleanStringList(List<String> value) {
		if (value == null || value.isEmpty()) {
			return List.of();
		}
		List<String> cleaned = new ArrayList<>();
		for (String item : value) {
			String normalized = normalizeText(item);
			if (normalized != null) {
				cleaned.add(normalized);
			}
		}
		return cleaned;
	}

	private static String uuidString(byte[] raw) {
		return UuidBinaryCodec.toCanonicalString(raw);
	}

	private static String defaultString(String value) {
		return value == null ? "" : value;
	}

	private static int defaultInt(Integer value) {
		return value == null ? 0 : value;
	}

	private static long defaultLong(Long value) {
		return value == null ? 0L : value;
	}

	private static double defaultDouble(Double value) {
		return value == null ? 0.0d : value;
	}

	private static String normalizeText(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() ? null : text;
	}

	private static Double toDoubleOrNull(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String text) {
			try {
				return Double.parseDouble(text.trim());
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static Integer toIntegerOrNull(Object value) {
		if (value instanceof Integer integer) {
			return integer;
		}
		if (value instanceof Number number) {
			double candidate = number.doubleValue();
			if (!Double.isFinite(candidate) || candidate % 1.0d != 0.0d) {
				return null;
			}
			long longValue = (long) candidate;
			return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE ? (int) longValue : null;
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.trim());
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static Long toLongOrNull(Object value) {
		if (value instanceof Long longValue) {
			return longValue;
		}
		if (value instanceof Number number) {
			double candidate = number.doubleValue();
			if (!Double.isFinite(candidate) || candidate % 1.0d != 0.0d) {
				return null;
			}
			return (long) candidate;
		}
		if (value instanceof String text) {
			try {
				return Long.parseLong(text.trim());
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static Boolean toBooleanOrNull(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof String text) {
			String normalized = text.trim().toLowerCase();
			if ("true".equals(normalized)) {
				return Boolean.TRUE;
			}
			if ("false".equals(normalized)) {
				return Boolean.FALSE;
			}
		}
		return null;
	}

	private static void setOptionalString(StringSetter setter, String value) {
		if (value != null) {
			setter.apply(value);
		}
	}

	private static void setOptionalInt(IntSetter setter, Integer value) {
		if (value != null) {
			setter.apply(value);
		}
	}

	private static void setOptionalLong(LongSetter setter, Long value) {
		if (value != null) {
			setter.apply(value);
		}
	}

	private static void setOptionalDouble(DoubleSetter setter, Double value) {
		if (value != null) {
			setter.apply(value);
		}
	}

	private static void setOptionalBool(BoolSetter setter, Boolean value) {
		if (value != null) {
			setter.apply(value);
		}
	}

	@FunctionalInterface
	private interface StringSetter {
		void apply(String value);
	}

	@FunctionalInterface
	private interface IntSetter {
		void apply(int value);
	}

	@FunctionalInterface
	private interface LongSetter {
		void apply(long value);
	}

	@FunctionalInterface
	private interface DoubleSetter {
		void apply(double value);
	}

	@FunctionalInterface
	private interface BoolSetter {
		void apply(boolean value);
	}
}
