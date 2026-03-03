import {
  ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION,
  ADMIN_NETWORK_PROTOCOL_VERSION,
  LOCAL_PROGRAM_VERSION,
} from '../constants';
import { normalizeDimension, normalizeRoomCode } from '../utils/overlayUtils';

export type PlayerData = {
  x: number;
  y: number;
  z: number;
  vx?: number;
  vy?: number;
  vz?: number;
  dimension: string;
  playerName?: string | null;
  playerUUID?: string | null;
  health?: number;
  maxHealth?: number;
  armor?: number;
  width?: number;
  height?: number;
};

export type EntityData = {
  x: number;
  y: number;
  z: number;
  vx?: number;
  vy?: number;
  vz?: number;
  dimension: string;
  entityType?: string | null;
  entityName?: string | null;
  width?: number;
  height?: number;
};

export type WaypointData = {
  x: number;
  y: number;
  z: number;
  dimension: string;
  name: string;
  symbol?: string | null;
  color?: number;
  ownerId?: string | null;
  ownerName?: string | null;
  createdAt?: number | null;
  ttlSeconds?: number | null;
  waypointKind?: string | null;
  replaceOldQuick?: boolean | null;
  maxQuickMarks?: number | null;
  targetType?: string | null;
  targetEntityId?: string | null;
  targetEntityType?: string | null;
  targetEntityName?: string | null;
  roomCode?: string | null;
  permanent?: boolean | null;
  tacticalType?: string | null;
  sourceType?: string | null;
};

export const PLAYER_DATA_RELIABILITY: Record<string, boolean> = {
  x: false,
  y: false,
  z: false,
  vx: false,
  vy: false,
  vz: false,
  dimension: false,
  playerName: true,
  playerUUID: true,
  health: true,
  maxHealth: true,
  armor: true,
  width: false,
  height: false,
};

export const ENTITY_DATA_RELIABILITY: Record<string, boolean> = {
  x: false,
  y: false,
  z: false,
  vx: false,
  vy: false,
  vz: false,
  dimension: false,
  entityType: true,
  entityName: true,
  width: false,
  height: false,
};

export const WAYPOINT_DATA_RELIABILITY: Record<string, boolean> = {
  x: false,
  y: false,
  z: false,
  dimension: false,
  name: true,
  symbol: true,
  color: true,
  ownerId: true,
  ownerName: true,
  createdAt: true,
  ttlSeconds: true,
  waypointKind: true,
  replaceOldQuick: true,
  maxQuickMarks: true,
  targetType: true,
  targetEntityId: true,
  targetEntityType: true,
  targetEntityName: true,
  roomCode: true,
  permanent: true,
  tacticalType: true,
  sourceType: true,
};

export type PlayerNode = {
  source?: string;
  timestamp?: number;
  data?: PlayerData;
} | PlayerData;

export type EntityNode = {
  source?: string;
  timestamp?: number;
  data?: EntityData;
} | EntityData;

export type WaypointNode = {
  source?: string;
  timestamp?: number;
  data?: WaypointData;
} | WaypointData;

export type AdminSnapshot = {
  players: Record<string, PlayerNode>;
  entities: Record<string, EntityNode>;
  waypoints: Record<string, WaypointNode>;
  playerMarks: Record<string, any>;
  tabState: { enabled: boolean; reports: Record<string, any>; groups: any[] };
  connections: string[];
  connections_count: number;
  revision: number;
  server_time: number | null;
};

export type AdminHandshakePacket = {
  type: 'handshake';
  networkProtocolVersion: string;
  minimumCompatibleNetworkProtocolVersion: string;
  localProgramVersion: string;
  roomCode: string;
  channel: 'admin';
};

export type AdminResyncRequestPacket = {
  type: 'resync_req';
  reason?: string;
};

export type CommandPlayerMarkSetPacket = {
  type: 'command_player_mark_set';
  playerId: string;
  team: string;
  color: string;
  label?: string;
  source: 'auto' | 'manual';
};

export type CommandPlayerMarkClearPacket = {
  type: 'command_player_mark_clear';
  playerId: string;
};

export type CommandPlayerMarkClearAllPacket = {
  type: 'command_player_mark_clear_all';
};

export type CommandSameServerFilterSetPacket = {
  type: 'command_same_server_filter_set';
  enabled: boolean;
};

export type CommandTacticalWaypointSetPacket = {
  type: 'command_tactical_waypoint_set';
  x: number;
  z: number;
  label: string;
  tacticalType: string;
  color: string;
  ttlSeconds: number;
  permanent: boolean;
  roomCode: string;
  dimension: string;
};

export type AdminOutboundPacket =
  | AdminHandshakePacket
  | AdminResyncRequestPacket
  | CommandPlayerMarkSetPacket
  | CommandPlayerMarkClearPacket
  | CommandPlayerMarkClearAllPacket
  | CommandSameServerFilterSetPacket
  | CommandTacticalWaypointSetPacket;

export type AdminAckInboundPacket = {
  type: 'admin_ack';
  ok: boolean;
  error?: string;
  action?: string;
  [key: string]: unknown;
};

export type HandshakeAckInboundPacket = {
  type: 'handshake_ack';
  ready?: boolean;
  networkProtocolVersion?: string;
  minimumCompatibleNetworkProtocolVersion?: string;
  localProgramVersion?: string;
  error?: string;
  rejectReason?: string;
  revision?: number;
  rev?: number;
  [key: string]: unknown;
};

export type PongInboundPacket = {
  type: 'pong';
  revision?: number;
  [key: string]: unknown;
};

export type SnapshotFullInboundPacket = {
  type: 'snapshot_full';
  players?: Record<string, PlayerNode>;
  entities?: Record<string, EntityNode>;
  waypoints?: Record<string, WaypointNode>;
  playerMarks?: Record<string, unknown>;
  tabState?: { enabled: boolean; reports: Record<string, any>; groups: any[] };
  connections?: string[];
  connections_count?: number;
  revision?: number;
  server_time?: number | null;
  [key: string]: unknown;
};

export type ScopePatch = {
  upsert?: Record<string, unknown>;
  delete?: string[];
};

export type PatchInboundPacket = {
  type: 'patch';
  players?: ScopePatch;
  entities?: ScopePatch;
  waypoints?: ScopePatch;
  playerMarks?: ScopePatch;
  meta?: Record<string, unknown>;
  revision?: number;
  server_time?: number | null;
  [key: string]: unknown;
};

export type AdminInboundPacket =
  | AdminAckInboundPacket
  | HandshakeAckInboundPacket
  | PongInboundPacket
  | SnapshotFullInboundPacket
  | PatchInboundPacket;

export function createEmptyAdminSnapshotModel(): AdminSnapshot {
  return {
    players: {},
    entities: {},
    waypoints: {},
    playerMarks: {},
    tabState: { enabled: false, reports: {}, groups: [] },
    connections: [],
    connections_count: 0,
    revision: 0,
    server_time: null,
  };
}

export function buildAdminHandshake(roomCode: string): AdminHandshakePacket {
  return {
    type: 'handshake',
    networkProtocolVersion: ADMIN_NETWORK_PROTOCOL_VERSION,
    minimumCompatibleNetworkProtocolVersion: ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION,
    localProgramVersion: LOCAL_PROGRAM_VERSION,
    roomCode: normalizeRoomCode(roomCode),
    channel: 'admin',
  };
}

export function buildCommandPlayerMarkSet(payload: {
  playerId: string;
  team: string;
  color: string;
  label?: string;
  source: 'auto' | 'manual';
}) {
  return {
    type: 'command_player_mark_set',
    playerId: payload.playerId,
    team: payload.team,
    color: payload.color,
    label: payload.label,
    source: payload.source,
  };
}

export function buildCommandPlayerMarkClear(playerId: string): CommandPlayerMarkClearPacket {
  return {
    type: 'command_player_mark_clear',
    playerId,
  };
}

export function buildCommandPlayerMarkClearAll(): CommandPlayerMarkClearAllPacket {
  return { type: 'command_player_mark_clear_all' };
}

export function buildCommandSameServerFilterSet(enabled: boolean): CommandSameServerFilterSetPacket {
  return {
    type: 'command_same_server_filter_set',
    enabled: Boolean(enabled),
  };
}

export function buildCommandTacticalWaypointSet(payload: {
  x: number;
  z: number;
  label: string;
  tacticalType: string;
  color: string;
  ttlSeconds: number;
  permanent: boolean;
  roomCode: string;
  dimension: string;
}): CommandTacticalWaypointSetPacket {
  return {
    type: 'command_tactical_waypoint_set',
    x: payload.x,
    z: payload.z,
    label: payload.label,
    tacticalType: payload.tacticalType,
    color: payload.color,
    ttlSeconds: payload.ttlSeconds,
    permanent: payload.permanent,
    roomCode: normalizeRoomCode(payload.roomCode),
    dimension: normalizeDimension(payload.dimension) || 'minecraft:overworld',
  };
}

export function buildAdminResyncRequest(reason = 'baseline_missing'): AdminResyncRequestPacket {
  return {
    type: 'resync_req',
    reason,
  };
}

export function parseAdminInboundPacket(payload: unknown): AdminInboundPacket | null {
  if (!payload || typeof payload !== 'object') {
    return null;
  }

  const message = payload as Record<string, unknown>;
  const type = typeof message.type === 'string' ? message.type : '';
  if (!type) {
    return null;
  }

  switch (type) {
    case 'admin_ack':
    case 'handshake_ack':
    case 'pong':
    case 'snapshot_full':
    case 'patch':
      return message as AdminInboundPacket;
    default:
      return null;
  }
}