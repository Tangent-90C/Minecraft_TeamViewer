from __future__ import annotations

import re
from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, TypeAdapter

from .models import EntityData, PlayerData, WaypointData


class PacketModel(BaseModel):
    model_config = ConfigDict(extra="ignore")


class HandshakePacket(PacketModel):
    type: Literal["handshake"]
    networkProtocolVersion: str | None = None
    minimumCompatibleNetworkProtocolVersion: str | None = None
    localProgramVersion: str | None = None
    programVersion: str | None = None
    roomCode: str | None = None
    roomId: str | None = None
    preferredReportIntervalTicks: int | None = None
    minReportIntervalTicks: int | None = None
    maxReportIntervalTicks: int | None = None


class PingPacket(PacketModel):
    type: Literal["ping", "health"]


class ResyncRequestPacket(PacketModel):
    type: Literal["resync_req"]


class CommandPlayerMarkSetPacket(PacketModel):
    type: Literal["command_player_mark_set"]
    playerId: str | None = None
    team: str | None = None
    color: str | int | None = None
    label: str | None = None
    source: str | None = None


class CommandPlayerMarkClearPacket(PacketModel):
    type: Literal["command_player_mark_clear"]
    playerId: str | None = None


class CommandPlayerMarkClearAllPacket(PacketModel):
    type: Literal["command_player_mark_clear_all"]


class CommandSameServerFilterSetPacket(PacketModel):
    type: Literal["command_same_server_filter_set"]
    enabled: bool = False


class CommandTacticalWaypointSetPacket(PacketModel):
    type: Literal["command_tactical_waypoint_set"]
    waypointId: str | None = None
    x: float | int | None = None
    z: float | int | None = None
    label: str | None = None
    dimension: str | None = None
    tacticalType: str | None = None
    permanent: bool = False
    ttlSeconds: int | float | None = None
    color: str | int | None = None
    roomCode: str | None = None
    roomId: str | None = None


class PlayerHandshakePacket(HandshakePacket):
    submitPlayerId: str | None = None


class PlayersUpdatePacket(PacketModel):
    type: Literal["players_update"]
    submitPlayerId: str | None = None
    players: dict[str, PlayerData] = Field(default_factory=dict)


class TabPlayersUpdatePacket(PacketModel):
    type: Literal["tab_players_update"]
    submitPlayerId: str | None = None
    tabPlayers: list[dict[str, Any]] = Field(default_factory=list)


class PlayersPatchPacket(PacketModel):
    type: Literal["players_patch"]
    submitPlayerId: str | None = None
    upsert: dict[str, dict[str, Any]] = Field(default_factory=dict)
    delete: list[str] = Field(default_factory=list)


class EntitiesUpdatePacket(PacketModel):
    type: Literal["entities_update"]
    submitPlayerId: str | None = None
    entities: dict[str, EntityData] = Field(default_factory=dict)


class EntitiesPatchPacket(PacketModel):
    type: Literal["entities_patch"]
    submitPlayerId: str | None = None
    upsert: dict[str, dict[str, Any]] = Field(default_factory=dict)
    delete: list[str] = Field(default_factory=list)


class StateKeepalivePacket(PacketModel):
    type: Literal["state_keepalive"]
    submitPlayerId: str | None = None
    players: list[str] = Field(default_factory=list)
    entities: list[str] = Field(default_factory=list)


class WaypointsUpdatePacket(PacketModel):
    type: Literal["waypoints_update"]
    submitPlayerId: str | None = None
    waypoints: dict[str, WaypointData] = Field(default_factory=dict)


class WaypointsDeletePacket(PacketModel):
    type: Literal["waypoints_delete"]
    submitPlayerId: str | None = None
    waypointIds: list[str] = Field(default_factory=list)


class WaypointsEntityDeathCancelPacket(PacketModel):
    type: Literal["waypoints_entity_death_cancel"]
    submitPlayerId: str | None = None
    targetEntityIds: list[str] = Field(default_factory=list)


AdminInboundPacket = Annotated[
    HandshakePacket
    | PingPacket
    | ResyncRequestPacket
    | CommandPlayerMarkSetPacket
    | CommandPlayerMarkClearPacket
    | CommandPlayerMarkClearAllPacket
    | CommandSameServerFilterSetPacket
    | CommandTacticalWaypointSetPacket,
    Field(discriminator="type"),
]

PlayerInboundPacket = Annotated[
    PlayerHandshakePacket
    | PlayersUpdatePacket
    | TabPlayersUpdatePacket
    | PlayersPatchPacket
    | EntitiesUpdatePacket
    | EntitiesPatchPacket
    | StateKeepalivePacket
    | WaypointsUpdatePacket
    | WaypointsDeletePacket
    | WaypointsEntityDeathCancelPacket
    | ResyncRequestPacket,
    Field(discriminator="type"),
]


class PacketDecodeError(ValueError):
    def __init__(self, code: str, detail: str | None = None) -> None:
        self.code = code
        self.detail = detail or code
        super().__init__(self.detail)


class PacketParsers:
    _admin_adapter = TypeAdapter(AdminInboundPacket)
    _player_adapter = TypeAdapter(PlayerInboundPacket)

    @staticmethod
    def parse_admin(payload: dict[str, Any]) -> AdminInboundPacket:
        try:
            return PacketParsers._admin_adapter.validate_python(payload)
        except Exception as exc:
            raise PacketDecodeError("invalid_admin_packet", str(exc)) from exc

    @staticmethod
    def parse_player(payload: dict[str, Any]) -> PlayerInboundPacket:
        try:
            return PacketParsers._player_adapter.validate_python(payload)
        except Exception as exc:
            raise PacketDecodeError("invalid_player_packet", str(exc)) from exc


class HandshakeHelpers:
    @staticmethod
    def protocol_version(packet: HandshakePacket, fallback: str = "0.0.1") -> str:
        raw = packet.networkProtocolVersion or fallback
        text = str(raw).strip()
        return text or fallback

    @staticmethod
    def minimum_compatible_protocol_version(packet: HandshakePacket, fallback: str | None = None) -> str:
        default_value = fallback or HandshakeHelpers.protocol_version(packet, "0.0.1")
        raw = packet.minimumCompatibleNetworkProtocolVersion or default_value
        text = str(raw).strip()
        return text or default_value

    @staticmethod
    def program_version(packet: HandshakePacket, fallback: str = "unknown") -> str:
        raw = packet.localProgramVersion or packet.programVersion or fallback
        text = str(raw).strip()
        return text or fallback

    @staticmethod
    def room_code(packet: HandshakePacket, fallback: str = "default") -> str:
        raw = packet.roomCode or packet.roomId or fallback
        text = str(raw).strip()
        return text or fallback

    @staticmethod
    def parse_protocol_version(version: str | int | float | None) -> tuple[int, int, int]:
        text = str(version or "").strip()
        if not text:
            return 0, 0, 0

        core = text.split("-", 1)[0]
        tokens = core.split(".") if "." in core else [core]
        parsed: list[int] = []

        for token in tokens[:3]:
            match = re.match(r"^(\d+)", token.strip())
            parsed.append(int(match.group(1)) if match else 0)

        while len(parsed) < 3:
            parsed.append(0)

        return parsed[0], parsed[1], parsed[2]

    @staticmethod
    def protocol_at_least(current: str | int | float | None, minimum: str | int | float | None) -> bool:
        return HandshakeHelpers.parse_protocol_version(current) >= HandshakeHelpers.parse_protocol_version(minimum)


class OutboundPacket(PacketModel):
    type: str


class HandshakeAckPacket(OutboundPacket):
    type: Literal["handshake_ack"] = "handshake_ack"
    ready: bool = True
    networkProtocolVersion: str
    minimumCompatibleNetworkProtocolVersion: str | None = None
    localProgramVersion: str
    roomCode: str
    deltaEnabled: bool
    error: str | None = None
    rejectReason: str | None = None
    digestIntervalSec: int | None = None
    broadcastHz: float | None = None
    reportIntervalTicks: int | None = None
    playerTimeoutSec: int | None = None
    entityTimeoutSec: int | None = None


class AdminAckPacket(OutboundPacket):
    type: Literal["admin_ack"] = "admin_ack"
    ok: bool
    action: str | None = None
    error: str | None = None
    playerId: str | None = None
    mark: dict[str, Any] | None = None
    removedCount: int | None = None
    enabled: bool | None = None
    waypointId: str | None = None
    waypoint: dict[str, Any] | None = None
    command: str | None = None


class PongPacket(OutboundPacket):
    type: Literal["pong"] = "pong"
    serverTime: float


class SnapshotFullPacket(OutboundPacket):
    type: Literal["snapshot_full"] = "snapshot_full"
    channel: str | None = None
    players: dict[str, Any] = Field(default_factory=dict)
    entities: dict[str, Any] = Field(default_factory=dict)
    waypoints: dict[str, Any] = Field(default_factory=dict)
    playerMarks: dict[str, Any] | None = None
    tabState: dict[str, Any] | None = None
    roomCode: str | None = None
    connections: list[str] | None = None
    connections_count: int | None = None
    server_time: float | None = None


class PatchPacket(OutboundPacket):
    type: Literal["patch"] = "patch"
    channel: str | None = None
    players: dict[str, Any] = Field(default_factory=dict)
    entities: dict[str, Any] = Field(default_factory=dict)
    waypoints: dict[str, Any] = Field(default_factory=dict)
    playerMarks: dict[str, Any] | None = None
    meta: dict[str, Any] | None = None


class DigestPacket(OutboundPacket):
    type: Literal["digest"] = "digest"
    hashes: dict[str, str]


class RefreshRequestOutboundPacket(OutboundPacket):
    type: Literal["refresh_req"] = "refresh_req"
    reason: str
    serverTime: float
    players: list[str]
    entities: list[str]


class ReportRateHintPacket(OutboundPacket):
    type: Literal["report_rate_hint"] = "report_rate_hint"
    reportIntervalTicks: int
    broadcastHz: float
    reason: str | None = None

