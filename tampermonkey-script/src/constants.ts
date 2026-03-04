import { APP_META, PROTOCOL_META } from './meta';

export const STORAGE_KEY = APP_META.storageKey;
export const ADMIN_NETWORK_PROTOCOL_VERSION = PROTOCOL_META.adminNetworkProtocolVersion;
export const ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION = PROTOCOL_META.adminMinCompatibleNetworkProtocolVersion;
export const LOCAL_PROGRAM_VERSION = `${APP_META.localProgramPrefix}-${__USERSCRIPT_VERSION__}`;
export const AUTO_MARK_SYNC_INTERVAL_MS = 1200;
export const AUTO_MARK_SYNC_MAX_PER_TICK = 12;

export const DEFAULT_CONFIG = {
  ADMIN_WS_URL: 'ws://127.0.0.1:8765/adminws',
  ROOM_CODE: 'default',
  RECONNECT_INTERVAL_MS: 1000,
  TARGET_DIMENSION: 'minecraft:overworld',
  SHOW_PLAYER_ICON: true,
  SHOW_PLAYER_TEXT: true,
  SHOW_HORSE_TEXT: true,
  SHOW_HORSE_ENTITIES: true,
  SHOW_LABEL_TEAM_INFO: true,
  SHOW_LABEL_TOWN_INFO: true,
  SHOW_WAYPOINT_ICON: true,
  SHOW_WAYPOINT_TEXT: true,
  BLOCK_MAP_LEFT_RIGHT_CLICK: false,
  ENABLE_TACTICAL_MAP_MARKING: true,
  TACTICAL_MARK_DEFAULT_TTL_SECONDS: 180,
  BLOCK_MAP_HOVER_POPUP: false,
  PLAYER_ICON_SIZE: 10,
  PLAYER_TEXT_SIZE: 12,
  HORSE_ICON_SIZE: 14,
  HORSE_TEXT_SIZE: 12,
  SHOW_COORDS: false,
  REPORTER_STAR_ICON: true,
  REPORTER_VISION_CIRCLE_ENABLED: false,
  REPORTER_VISION_RADIUS: 64,
  REPORTER_VISION_COLOR: '',
  REPORTER_VISION_OPACITY: 0.1,
  REPORTER_CHUNK_AREA_ENABLED: false,
  REPORTER_CHUNK_RADIUS: 2,
  REPORTER_CHUNK_COLOR: '',
  REPORTER_CHUNK_OPACITY: 0.11,
  AUTO_TEAM_FROM_NAME: true,
  FRIENDLY_TAGS: '[xxx]',
  ENEMY_TAGS: '[yyy]',
  TEAM_COLOR_FRIENDLY: '#3b82f6',
  TEAM_COLOR_ENEMY: '#ef4444',
  TEAM_COLOR_NEUTRAL: '#94a3b8',
  DEBUG: false,
};

export const TEAM_DEFAULT_COLORS = {
  friendly: '#3b82f6',
  enemy: '#ef4444',
  neutral: '#94a3b8',
};

export const TEAM_CONFIG_COLOR_FIELD = {
  friendly: 'TEAM_COLOR_FRIENDLY',
  enemy: 'TEAM_COLOR_ENEMY',
  neutral: 'TEAM_COLOR_NEUTRAL',
};

export const MC_COLOR_CODE_MAP = {
  '0': '#000000',
  '1': '#0000aa',
  '2': '#00aa00',
  '3': '#00aaaa',
  '4': '#aa0000',
  '5': '#aa00aa',
  '6': '#ffaa00',
  '7': '#aaaaaa',
  '8': '#555555',
  '9': '#5555ff',
  a: '#55ff55',
  b: '#55ffff',
  c: '#ff5555',
  d: '#ff55ff',
  e: '#ffff55',
  f: '#ffffff',
} as const;
