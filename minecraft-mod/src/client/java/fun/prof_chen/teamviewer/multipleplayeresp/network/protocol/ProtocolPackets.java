package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

import java.util.List;
import java.util.Map;

public final class ProtocolPackets {
	private ProtocolPackets() {
	}

	public static class BaseInboundPacket {
		public String type;
		public Long rev;
		public Long revision;
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
	}

	public static class SnapshotFullInboundPacket extends BaseInboundPacket {
		public Map<String, Object> players;
		public Map<String, Object> entities;
		public Map<String, Object> waypoints;
		public Map<String, Object> playerMarks;
	}

	public static class PatchInboundPacket extends BaseInboundPacket {
		public Map<String, Object> players;
		public Map<String, Object> entities;
		public Map<String, Object> waypoints;
		public Map<String, Object> playerMarks;
		public Map<String, Object> meta;
	}

	public static class DigestInboundPacket extends BaseInboundPacket {
		public Map<String, String> hashes;
	}

	public static class RefreshReqInboundPacket extends BaseInboundPacket {
		public List<String> players;
		public List<String> entities;
		public String reason;
	}

	public static class WaypointsUpdateInboundPacket extends BaseInboundPacket {
		public Map<String, Object> waypoints;
	}

	public static class WaypointsDeleteInboundPacket extends BaseInboundPacket {
		public List<String> waypointIds;
	}

	public static class PositionsInboundPacket extends BaseInboundPacket {
		public Map<String, Object> players;
		public Map<String, Object> entities;
		public Map<String, Object> waypoints;
		public Map<String, Object> playerMarks;
	}

	public static class HandshakePacket {
		public final String type = "handshake";
		public String networkProtocolVersion;
		public String minimumCompatibleNetworkProtocolVersion;
		public String localProgramVersion;
		public String roomCode;
		public boolean supportsDelta;
		public String submitPlayerId;
	}

	public static class PlayersPatchPacket {
		public final String type = "players_patch";
		public String submitPlayerId;
		public long ackRev;
		public Map<String, Map<String, Object>> upsert;
		public List<String> delete;
	}

	public static class EntitiesPatchPacket {
		public final String type = "entities_patch";
		public String submitPlayerId;
		public long ackRev;
		public Map<String, Map<String, Object>> upsert;
		public List<String> delete;
	}

	public static class WaypointsUpdatePacket {
		public final String type = "waypoints_update";
		public String submitPlayerId;
		public Map<String, Map<String, Object>> waypoints;
	}

	public static class TabPlayersUpdatePacket {
		public final String type = "tab_players_update";
		public String submitPlayerId;
		public long ackRev;
		public List<Map<String, Object>> tabPlayers;
	}

	public static class WaypointsDeletePacket {
		public final String type = "waypoints_delete";
		public String submitPlayerId;
		public List<String> waypointIds;
	}

	public static class WaypointsEntityDeathCancelPacket {
		public final String type = "waypoints_entity_death_cancel";
		public String submitPlayerId;
		public List<String> targetEntityIds;
	}

	public static class PlayersUpdatePacket {
		public final String type = "players_update";
		public String submitPlayerId;
		public Map<String, Map<String, Object>> players;
	}

	public static class EntitiesUpdatePacket {
		public final String type = "entities_update";
		public String submitPlayerId;
		public Map<String, Map<String, Object>> entities;
	}

	public static class ResyncReqPacket {
		public final String type = "resync_req";
		public String reason;
		public long ackRev;
		public String submitPlayerId;
	}
}
