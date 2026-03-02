import {
  DEFAULT_CONFIG,
  MC_COLOR_CODE_MAP,
  TEAM_CONFIG_COLOR_FIELD,
  TEAM_DEFAULT_COLORS,
} from './constants';

export function parseTagList(raw: unknown) {
  return String(raw || '')
    .split(/[，,;；\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 12);
}

export function isLocalWebSocketHost(hostname: unknown) {
  const host = String(hostname || '').trim().toLowerCase();
  return host === 'localhost' || host === '127.0.0.1' || host === '::1';
}

export function normalizeWsUrl(rawUrl: unknown) {
  const text = String(rawUrl || '').trim();
  if (!text) return DEFAULT_CONFIG.ADMIN_WS_URL;

  let next = text;
  if (next.endsWith('/snapshot')) next = next.slice(0, -('/snapshot'.length)) + '/adminws';

  try {
    const parsed = new URL(next);
    const protocol = String(parsed.protocol || '').toLowerCase();
    const isLocalHost = isLocalWebSocketHost(parsed.hostname);

    if (protocol === 'http:') {
      parsed.protocol = isLocalHost ? 'ws:' : 'wss:';
    } else if (protocol === 'https:') {
      parsed.protocol = 'wss:';
    } else if (protocol === 'ws:') {
      parsed.protocol = isLocalHost ? 'ws:' : 'wss:';
    } else if (protocol === 'wss:') {
      parsed.protocol = 'wss:';
    } else {
      return DEFAULT_CONFIG.ADMIN_WS_URL;
    }

    return parsed.toString();
  } catch (_) {
    return DEFAULT_CONFIG.ADMIN_WS_URL;
  }
}

export function normalizeRoomCode(rawRoomCode: unknown) {
  const text = String(rawRoomCode ?? '').trim();
  if (!text) return DEFAULT_CONFIG.ROOM_CODE;
  return text.slice(0, 64);
}

export function normalizeTeam(teamValue: unknown) {
  const text = String(teamValue || '').trim().toLowerCase();
  if (text === 'friendly' || text === 'friend' || text === 'ally' || text === 'blue') return 'friendly';
  if (text === 'enemy' || text === 'hostile' || text === 'red') return 'enemy';
  return 'neutral';
}

export function normalizeMarkSource(sourceValue: unknown) {
  const text = String(sourceValue || '').trim().toLowerCase();
  return text === 'auto' ? 'auto' : 'manual';
}

export function normalizeColor(colorValue: unknown, fallbackColor: string) {
  const fallback = typeof fallbackColor === 'string' && fallbackColor ? fallbackColor : TEAM_DEFAULT_COLORS.neutral;
  if (colorValue === undefined || colorValue === null || colorValue === '') return fallback;
  if (typeof colorValue === 'number' && Number.isFinite(colorValue)) {
    const v = Math.max(0, Math.min(0xffffff, Math.floor(colorValue)));
    const hex = v.toString(16).padStart(6, '0');
    return `#${hex}`;
  }
  const text = String(colorValue || '').trim();
  if (!text) return fallback;
  const raw = text.startsWith('#') ? text.slice(1) : text;
  if (/^[0-9a-fA-F]{6}$/.test(raw)) {
    return `#${raw.toLowerCase()}`;
  }
  if (/^[0-9]+$/.test(text)) {
    const num = Number.parseInt(text, 10);
    if (!Number.isNaN(num)) {
      const v = Math.max(0, Math.min(0xffffff, Math.floor(num)));
      const hex = v.toString(16).padStart(6, '0');
      return `#${hex}`;
    }
  }
  return fallback;
}

export function getConfiguredTeamColor(team: unknown, config: Record<string, unknown>) {
  const normalizedTeam = normalizeTeam(team);
  const configKey = TEAM_CONFIG_COLOR_FIELD[normalizedTeam as keyof typeof TEAM_CONFIG_COLOR_FIELD];
  const fallback = TEAM_DEFAULT_COLORS[normalizedTeam as keyof typeof TEAM_DEFAULT_COLORS] || TEAM_DEFAULT_COLORS.neutral;
  return normalizeColor(configKey ? config[configKey] : '', fallback);
}

export function sanitizeConfig(candidate: Record<string, unknown> | null | undefined) {
  const next = { ...DEFAULT_CONFIG };
  if (!candidate || typeof candidate !== 'object') return next;

  const wsUrlCandidate =
    typeof candidate.ADMIN_WS_URL === 'string' && candidate.ADMIN_WS_URL.trim()
      ? candidate.ADMIN_WS_URL.trim()
      : (typeof candidate.SNAPSHOT_URL === 'string' ? candidate.SNAPSHOT_URL.trim() : '');

  if (wsUrlCandidate) {
    next.ADMIN_WS_URL = normalizeWsUrl(wsUrlCandidate);
  }
  next.ROOM_CODE = normalizeRoomCode((candidate.ROOM_CODE ?? candidate.ROOM_ID ?? candidate.roomCode) as string);

  const reconnect = Number(candidate.RECONNECT_INTERVAL_MS ?? candidate.POLL_INTERVAL_MS);
  if (Number.isFinite(reconnect)) {
    next.RECONNECT_INTERVAL_MS = Math.max(200, Math.min(60000, Math.round(reconnect)));
  }

  if (typeof candidate.TARGET_DIMENSION === 'string' && candidate.TARGET_DIMENSION.trim()) {
    next.TARGET_DIMENSION = candidate.TARGET_DIMENSION.trim();
  }

  next.SHOW_PLAYER_ICON = candidate.SHOW_PLAYER_ICON === undefined
    ? DEFAULT_CONFIG.SHOW_PLAYER_ICON
    : Boolean(candidate.SHOW_PLAYER_ICON);
  next.SHOW_PLAYER_TEXT = candidate.SHOW_PLAYER_TEXT === undefined
    ? DEFAULT_CONFIG.SHOW_PLAYER_TEXT
    : Boolean(candidate.SHOW_PLAYER_TEXT);
  next.SHOW_HORSE_TEXT = candidate.SHOW_HORSE_TEXT === undefined
    ? DEFAULT_CONFIG.SHOW_HORSE_TEXT
    : Boolean(candidate.SHOW_HORSE_TEXT);
  next.SHOW_HORSE_ENTITIES = candidate.SHOW_HORSE_ENTITIES === undefined
    ? DEFAULT_CONFIG.SHOW_HORSE_ENTITIES
    : Boolean(candidate.SHOW_HORSE_ENTITIES);
  next.SHOW_LABEL_TEAM_INFO = candidate.SHOW_LABEL_TEAM_INFO === undefined
    ? DEFAULT_CONFIG.SHOW_LABEL_TEAM_INFO
    : Boolean(candidate.SHOW_LABEL_TEAM_INFO);
  next.SHOW_LABEL_TOWN_INFO = candidate.SHOW_LABEL_TOWN_INFO === undefined
    ? DEFAULT_CONFIG.SHOW_LABEL_TOWN_INFO
    : Boolean(candidate.SHOW_LABEL_TOWN_INFO);
  next.SHOW_WAYPOINT_ICON = candidate.SHOW_WAYPOINT_ICON === undefined
    ? DEFAULT_CONFIG.SHOW_WAYPOINT_ICON
    : Boolean(candidate.SHOW_WAYPOINT_ICON);
  next.SHOW_WAYPOINT_TEXT = candidate.SHOW_WAYPOINT_TEXT === undefined
    ? DEFAULT_CONFIG.SHOW_WAYPOINT_TEXT
    : Boolean(candidate.SHOW_WAYPOINT_TEXT);

  const playerIconSize = Number(candidate.PLAYER_ICON_SIZE);
  if (Number.isFinite(playerIconSize)) {
    next.PLAYER_ICON_SIZE = Math.max(6, Math.min(40, Math.round(playerIconSize)));
  }

  const playerTextSize = Number(candidate.PLAYER_TEXT_SIZE);
  if (Number.isFinite(playerTextSize)) {
    next.PLAYER_TEXT_SIZE = Math.max(8, Math.min(32, Math.round(playerTextSize)));
  }

  const horseIconSize = Number(candidate.HORSE_ICON_SIZE);
  if (Number.isFinite(horseIconSize)) {
    next.HORSE_ICON_SIZE = Math.max(6, Math.min(40, Math.round(horseIconSize)));
  }

  const horseTextSize = Number(candidate.HORSE_TEXT_SIZE);
  if (Number.isFinite(horseTextSize)) {
    next.HORSE_TEXT_SIZE = Math.max(8, Math.min(32, Math.round(horseTextSize)));
  }

  next.SHOW_COORDS = Boolean(candidate.SHOW_COORDS);
  next.AUTO_TEAM_FROM_NAME = candidate.AUTO_TEAM_FROM_NAME === undefined
    ? DEFAULT_CONFIG.AUTO_TEAM_FROM_NAME
    : Boolean(candidate.AUTO_TEAM_FROM_NAME);
  if (typeof candidate.FRIENDLY_TAGS === 'string') {
    next.FRIENDLY_TAGS = candidate.FRIENDLY_TAGS.trim();
  }
  if (typeof candidate.ENEMY_TAGS === 'string') {
    next.ENEMY_TAGS = candidate.ENEMY_TAGS.trim();
  }
  next.TEAM_COLOR_FRIENDLY = normalizeColor(candidate.TEAM_COLOR_FRIENDLY, DEFAULT_CONFIG.TEAM_COLOR_FRIENDLY);
  next.TEAM_COLOR_ENEMY = normalizeColor(candidate.TEAM_COLOR_ENEMY, DEFAULT_CONFIG.TEAM_COLOR_ENEMY);
  next.TEAM_COLOR_NEUTRAL = normalizeColor(candidate.TEAM_COLOR_NEUTRAL, DEFAULT_CONFIG.TEAM_COLOR_NEUTRAL);
  next.DEBUG = Boolean(candidate.DEBUG);
  return next;
}

export function readNumber(value: unknown) {
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

export function normalizeDimension(dim: unknown) {
  const text = String(dim || '').toLowerCase();
  if (!text) return '';
  if (text.includes('overworld')) return 'minecraft:overworld';
  if (text.includes('the_nether') || text.endsWith(':nether')) return 'minecraft:the_nether';
  if (text.includes('the_end') || text.endsWith(':end')) return 'minecraft:the_end';
  return text;
}

export function getPlayerDataNode(node: unknown) {
  if (!node || typeof node !== 'object') return null;
  if ((node as Record<string, unknown>).data && typeof (node as Record<string, unknown>).data === 'object') {
    return (node as Record<string, unknown>).data as Record<string, unknown>;
  }
  return node as Record<string, unknown>;
}

export function parseMcDisplayName(rawText: unknown) {
  const original = String(rawText || '').trim();
  if (!original) {
    return {
      plain: '',
      teamText: '',
      color: null,
    };
  }

  let text = original;
  if (text.startsWith('literal{') && text.endsWith('}')) {
    text = text.slice('literal{'.length, -1);
  }

  let color = null;
  const firstColorMatch = text.match(/§([0-9a-fA-F])/);
  if (firstColorMatch) {
    color = MC_COLOR_CODE_MAP[String(firstColorMatch[1]).toLowerCase() as keyof typeof MC_COLOR_CODE_MAP] || null;
  }

  const plain = text.replace(/§[0-9a-fk-orA-FK-OR]/g, '').trim();
  const teamMatch = plain.match(/\[[^\]]+\]/);
  const teamText = teamMatch ? teamMatch[0] : '';

  return {
    plain,
    teamText,
    color,
  };
}

export function hasAllKeys(obj: unknown, keys: string[]) {
  if (!obj || typeof obj !== 'object') {
    return false;
  }
  return keys.every((key) => Object.prototype.hasOwnProperty.call(obj, key));
}

export function applyScopePatchMap(
  baseMap: Record<string, unknown> | null | undefined,
  scopePatch: Record<string, unknown> | null | undefined,
  requiredKeysForNew: string[] | null = null
) {
  const next = (baseMap && typeof baseMap === 'object') ? { ...baseMap } : {};
  if (!scopePatch || typeof scopePatch !== 'object') {
    return next;
  }

  const patchRecord = scopePatch as Record<string, unknown>;
  const upsert = (patchRecord.upsert && typeof patchRecord.upsert === 'object')
    ? patchRecord.upsert as Record<string, unknown>
    : {};
  const remove = Array.isArray(patchRecord.delete) ? patchRecord.delete as string[] : [];

  for (const [objectId, value] of Object.entries(upsert)) {
    const prev = next[objectId] as Record<string, unknown>;
    const existed = prev && typeof prev === 'object' && !Array.isArray(prev);
    if (!existed && requiredKeysForNew && Array.isArray(requiredKeysForNew)) {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        continue;
      }
      if (!hasAllKeys(value, requiredKeysForNew)) {
        continue;
      }
    }
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      next[objectId] = existed
        ? { ...prev, ...value as Record<string, unknown> }
        : { ...value as Record<string, unknown> };
    } else {
      next[objectId] = value;
    }
  }
  for (const objectId of remove) {
    delete next[String(objectId)];
  }
  return next;
}

export function shouldResyncForScopeMissingBaseline(
  currentMap: Record<string, unknown> | null | undefined,
  scopePatch: Record<string, unknown> | null | undefined,
  requiredKeys: string[]
) {
  if (!scopePatch || typeof scopePatch !== 'object') {
    return false;
  }

  const patchRecord = scopePatch as Record<string, unknown>;
  const upsert = (patchRecord.upsert && typeof patchRecord.upsert === 'object')
    ? patchRecord.upsert as Record<string, unknown>
    : {};

  for (const [objectId, delta] of Object.entries(upsert)) {
    const existed = currentMap && typeof currentMap === 'object'
      ? Object.prototype.hasOwnProperty.call(currentMap, objectId)
      : false;
    if (existed) {
      continue;
    }
    if (!delta || typeof delta !== 'object' || Array.isArray(delta)) {
      return true;
    }
    if (!hasAllKeys(delta, requiredKeys)) {
      return true;
    }
  }
  return false;
}
