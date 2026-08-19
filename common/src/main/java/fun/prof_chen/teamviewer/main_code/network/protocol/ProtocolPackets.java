package fun.prof_chen.teamviewer.main_code.network.protocol;

import java.util.List;
import java.util.Map;

public final class ProtocolPackets {
	private ProtocolPackets() {
	}

	public static class DecodedInboundMessage {
		public final String type;
		public final BaseInboundPacket packet;

		public DecodedInboundMessage(String type, BaseInboundPacket packet) {
			this.type = type;
			this.packet = packet;
		}
	}

	public static class BaseInboundPacket {
		public String type;
	}

	public static class HandshakeAckInboundPacket extends BaseInboundPacket {
		public Boolean ready;
		public String networkProtocolVersion;
		public String minimumCompatibleNetworkProtocolVersion;
		public String localProgramVersion;
		public String programVersion;
		public String error;
		public String rejectReason;
		public Boolean deltaEnabled;
		public Integer digestIntervalSec;
		public Double broadcastHz;
		public Integer reportIntervalTicks;
		public Integer playerTimeoutSec;
		public Integer entityTimeoutSec;
		public Integer battleChunkTimeoutSec;
	}

	public static class SnapshotFullInboundPacket extends BaseInboundPacket {
		public Map<String, Object> players;
		public Map<String, Object> entities;
		public Map<String, Object> waypoints;
		public Map<String, Object> battleChunks;
		public Map<String, Object> playerMarks;
		public Map<String, Object> lastSeenPlayers;
	}

	public static class PatchInboundPacket extends BaseInboundPacket {
		public Map<String, Object> players;
		public Map<String, Object> entities;
		public Map<String, Object> waypoints;
		public Map<String, Object> battleChunks;
		public Map<String, Object> playerMarks;
		public Map<String, Object> lastSeenPlayers;
		public Map<String, Object> meta;
	}

	public static class DigestInboundPacket extends BaseInboundPacket {
		public Map<String, String> hashes;
	}

	public static class RefreshReqInboundPacket extends BaseInboundPacket {
		public List<String> players;
		public List<String> entities;
		public List<String> battleChunks;
		public String reason;
	}

	public static class ReportRateHintInboundPacket extends BaseInboundPacket {
		public Integer reportIntervalTicks;
		public Double broadcastHz;
		public String reason;
	}

	public static class WaypointsUpdateInboundPacket extends BaseInboundPacket {
		public Map<String, Object> waypoints;
	}

	public static class WaypointsDeleteInboundPacket extends BaseInboundPacket {
		public List<String> waypointIds;
	}

	public static class HandshakePacket {
		public final String type = "handshake";
		public String networkProtocolVersion;
		public String minimumCompatibleNetworkProtocolVersion;
		public String localProgramVersion;
		public String roomCode;
		public byte[] submitPlayerId;
		public Integer preferredReportIntervalTicks;
		public Integer minReportIntervalTicks;
		public Integer maxReportIntervalTicks;
	}

	public static class PlayersPatchPacket {
		public final String type = "players_patch";
		public byte[] submitPlayerId;
		public Map<String, Map<String, Object>> upsert;
		public List<String> delete;
	}

	public static class EntitiesPatchPacket {
		public final String type = "entities_patch";
		public byte[] submitPlayerId;
		public Map<String, Map<String, Object>> upsert;
		public List<String> delete;
	}

	public static class StateKeepalivePacket {
		public final String type = "state_keepalive";
		public byte[] submitPlayerId;
		public List<String> players;
		public List<String> entities;
		public List<String> battleChunks;
	}

	public static class SourceStateClearPacket {
		public final String type = "source_state_clear";
		public byte[] submitPlayerId;
		public List<String> scopes;
	}

	public static class WaypointsUpdatePacket {
		public final String type = "waypoints_update";
		public byte[] submitPlayerId;
		public Map<String, Map<String, Object>> waypoints;
	}

	public static class TabPlayersUpdatePacket {
		public final String type = "tab_players_update";
		public byte[] submitPlayerId;
		public List<Map<String, Object>> tabPlayers;
	}

	public static class TabPlayersPatchPacket {
		public final String type = "tab_players_patch";
		public byte[] submitPlayerId;
		public Map<String, Map<String, Object>> upsert;
		public List<String> delete;
	}

	public static class WaypointsDeletePacket {
		public final String type = "waypoints_delete";
		public byte[] submitPlayerId;
		public List<String> waypointIds;
	}

	public static class WaypointsEntityDeathCancelPacket {
		public final String type = "waypoints_entity_death_cancel";
		public byte[] submitPlayerId;
		public List<String> targetEntityIds;
	}

	public static class BattleMapObservationPacket {
		public final String type = "battle_map_observation";
		public byte[] submitPlayerId;
		public String mode;
		public String dimension;
		public Integer mapSize;
		public Integer anchorRow;
		public Integer anchorCol;
		public Long snapshotObservedAt;
		public Long parsedAt;
		public List<Map<String, Object>> candidates;
		public List<Map<String, Object>> cells;
	}

	public static class ResyncReqPacket {
		public final String type = "resync_req";
		public String reason;
		public byte[] submitPlayerId;
	}
}
