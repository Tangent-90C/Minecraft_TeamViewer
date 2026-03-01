// @ts-nocheck
import {
  ADMIN_NETWORK_PROTOCOL_VERSION,
  AUTO_MARK_SYNC_INTERVAL_MS,
  AUTO_MARK_SYNC_MAX_PER_TICK,
  DEFAULT_CONFIG,
  LOCAL_PROGRAM_VERSION,
  MC_COLOR_CODE_MAP,
  STORAGE_KEY,
  TEAM_CONFIG_COLOR_FIELD,
  TEAM_DEFAULT_COLORS,
} from './constants';
import { PANEL_HTML } from './panelTemplate';
import { OVERLAY_STYLE_TEXT, UI_STYLE_TEXT } from './styles';

declare const unsafeWindow: Window | undefined;

(function () {
  'use strict';

  const PAGE = (typeof unsafeWindow !== 'undefined' && unsafeWindow) ? unsafeWindow : window;
  const CONFIG = { ...DEFAULT_CONFIG };

  let leafletRef = null;
  let capturedMap = null;
  let markersById = new Map();
  let waypointsById = new Map();
  let lastRevision = null;
  let lastErrorText = null;
  let latestSnapshot = null;
  let uiMounted = false;
  let panelVisible = false;
  let overlayStarted = false;
  let adminWs = null;
  let wsConnected = false;
  let reconnectTimer = null;
  let manualWsClose = false;
  let latestPlayerMarks = {};
  let sameServerFilterEnabled = false;
  let panelPage = 'main';
  let lastAdminResyncRequestAt = 0;
  let lastAdminMessageType = null;
  let lastAdminMessageAt = 0;
  let lastAdminMessageRevision = null;
  let lastAutoMarkSyncAt = 0;
  let autoMarkSyncCache = new Map();

  function patchLeaflet(leafletObj) {
    if (!leafletObj || !leafletObj.Map || leafletObj.__nodemcProjectionPatched) {
      return;
    }
    leafletObj.__nodemcProjectionPatched = true;
    leafletRef = leafletObj;

    const originalInitialize = leafletObj.Map.prototype.initialize;
    leafletObj.Map.prototype.initialize = function (...args) {
      capturedMap = this;
      return originalInitialize.apply(this, args);
    };
  }

  function sanitizeConfig(candidate) {
    const next = { ...DEFAULT_CONFIG };
    if (!candidate || typeof candidate !== 'object') return next;

    const wsUrlCandidate =
      typeof candidate.ADMIN_WS_URL === 'string' && candidate.ADMIN_WS_URL.trim()
        ? candidate.ADMIN_WS_URL.trim()
        : (typeof candidate.SNAPSHOT_URL === 'string' ? candidate.SNAPSHOT_URL.trim() : '');

    if (wsUrlCandidate) {
      next.ADMIN_WS_URL = normalizeWsUrl(wsUrlCandidate);
    }
    next.ROOM_CODE = normalizeRoomCode(candidate.ROOM_CODE ?? candidate.ROOM_ID ?? candidate.roomCode);

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

  function parseTagList(raw) {
    return String(raw || '')
      .split(/[，,;；\s]+/)
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 12);
  }

  function isLocalWebSocketHost(hostname) {
    const host = String(hostname || '').trim().toLowerCase();
    return host === 'localhost' || host === '127.0.0.1' || host === '::1';
  }

  function normalizeWsUrl(rawUrl) {
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

  function normalizeRoomCode(rawRoomCode) {
    const text = String(rawRoomCode ?? '').trim();
    if (!text) return DEFAULT_CONFIG.ROOM_CODE;
    return text.slice(0, 64);
  }

  function normalizeTeam(teamValue) {
    const text = String(teamValue || '').trim().toLowerCase();
    if (text === 'friendly' || text === 'friend' || text === 'ally' || text === 'blue') return 'friendly';
    if (text === 'enemy' || text === 'hostile' || text === 'red') return 'enemy';
    return 'neutral';
  }

  function normalizeMarkSource(sourceValue) {
    const text = String(sourceValue || '').trim().toLowerCase();
    return text === 'auto' ? 'auto' : 'manual';
  }

  function normalizeColor(colorValue, fallbackColor) {
    const fallback = typeof fallbackColor === 'string' && fallbackColor ? fallbackColor : TEAM_DEFAULT_COLORS.neutral;
    if (colorValue === undefined || colorValue === null || colorValue === '') return fallback;
    // accept numeric color (integer), convert to hex
    if (typeof colorValue === 'number' && Number.isFinite(colorValue)) {
      const v = Math.max(0, Math.min(0xFFFFFF, Math.floor(colorValue)));
      const hex = v.toString(16).padStart(6, '0');
      return `#${hex}`;
    }
    const text = String(colorValue || '').trim();
    if (!text) return fallback;
    const raw = text.startsWith('#') ? text.slice(1) : text;
    if (/^[0-9a-fA-F]{6}$/.test(raw)) {
      return `#${raw.toLowerCase()}`;
    }
    // try parsing decimal integer string
    if (/^[0-9]+$/.test(text)) {
      const num = Number.parseInt(text, 10);
      if (!Number.isNaN(num)) {
        const v = Math.max(0, Math.min(0xFFFFFF, Math.floor(num)));
        const hex = v.toString(16).padStart(6, '0');
        return `#${hex}`;
      }
    }
    return fallback;
  }

  function getConfiguredTeamColor(team) {
    const normalizedTeam = normalizeTeam(team);
    const configKey = TEAM_CONFIG_COLOR_FIELD[normalizedTeam];
    const fallback = TEAM_DEFAULT_COLORS[normalizedTeam] || TEAM_DEFAULT_COLORS.neutral;
    return normalizeColor(configKey ? CONFIG[configKey] : '', fallback);
  }

  function getPlayerMark(playerId) {
    if (!latestPlayerMarks || typeof latestPlayerMarks !== 'object') return null;
    const entry = latestPlayerMarks[playerId];
    if (!entry || typeof entry !== 'object') return null;

    const team = normalizeTeam(entry.team);
    const color = normalizeColor(entry.color, getConfiguredTeamColor(team));
    const label = typeof entry.label === 'string' && entry.label.trim() ? entry.label.trim() : null;
    const sourceRaw = typeof entry.source === 'string' ? entry.source.trim().toLowerCase() : '';
    const source = normalizeMarkSource(sourceRaw);
    const hasExplicitSource = sourceRaw === 'auto' || sourceRaw === 'manual';
    return { team, color, label, source, hasExplicitSource };
  }

  function autoTeamFromName(nameText) {
    if (!CONFIG.AUTO_TEAM_FROM_NAME) return null;
    const name = String(nameText || '');
    if (!name) return null;

    const friendlyTags = parseTagList(CONFIG.FRIENDLY_TAGS);
    const enemyTags = parseTagList(CONFIG.ENEMY_TAGS);
    if (friendlyTags.some((tag) => name.includes(tag))) {
      return {
        team: 'friendly',
        color: getConfiguredTeamColor('friendly'),
        label: '',
      };
    }
    if (enemyTags.some((tag) => name.includes(tag))) {
      return {
        team: 'enemy',
        color: getConfiguredTeamColor('enemy'),
        label: '',
      };
    }
    return null;
  }

  function maybeSyncAutoDetectedMarks(candidates) {
    if (!adminWs || adminWs.readyState !== WebSocket.OPEN) return;
    if (!Array.isArray(candidates) || candidates.length === 0) return;

    const now = Date.now();
    if (now - lastAutoMarkSyncAt < AUTO_MARK_SYNC_INTERVAL_MS) {
      return;
    }

    let sent = 0;
    for (const item of candidates) {
      if (!item || typeof item !== 'object') continue;
      const playerId = String(item.playerId || '').trim();
      if (!playerId) continue;

      const action = String(item.action || '').trim().toLowerCase();
      if (action !== 'set' && action !== 'clear') continue;

      let cacheKey = '__clear__';
      let ok = false;

      if (action === 'set') {
        const team = normalizeTeam(item.team);
        if (team !== 'friendly' && team !== 'enemy') continue;

        const color = normalizeColor(item.color, getConfiguredTeamColor(team));
        cacheKey = `set|${team}|${color}`;

        if (autoMarkSyncCache.get(playerId) === cacheKey) {
          continue;
        }

        ok = sendAdminCommand({
          type: 'command_player_mark_set',
          playerId,
          team,
          color,
          source: 'auto',
        });
      } else {
        if (autoMarkSyncCache.get(playerId) === cacheKey) {
          continue;
        }

        ok = sendAdminCommand({
          type: 'command_player_mark_clear',
          playerId,
        });
      }

      if (!ok) {
        continue;
      }

      autoMarkSyncCache.set(playerId, cacheKey);
      sent += 1;
      if (sent >= AUTO_MARK_SYNC_MAX_PER_TICK) {
        break;
      }
    }

    if (sent > 0) {
      lastAutoMarkSyncAt = now;
    }
  }

  function getTabPlayerName(playerId) {
    const info = getTabPlayerInfo(playerId);
    return info ? info.autoName : null;
  }

  function parseMcDisplayName(rawText) {
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
      color = MC_COLOR_CODE_MAP[String(firstColorMatch[1]).toLowerCase()] || null;
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

  function getTabPlayerInfo(playerId) {
    const tabState = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot.tabState : null;
    const reports = tabState && typeof tabState.reports === 'object' ? tabState.reports : null;
    if (!reports) return null;

    for (const report of Object.values(reports)) {
      if (!report || typeof report !== 'object') continue;
      const players = Array.isArray(report.players) ? report.players : [];
      for (const node of players) {
        if (!node || typeof node !== 'object') continue;
        const nodeId = String(node.uuid || node.id || '').trim();
        if (!nodeId || nodeId !== String(playerId)) continue;

        const prefixedName = String(node.prefixedName || '').trim();
        const displayNameRaw = String(node.displayName || '').trim();
        const name = String(node.name || '').trim();
        const parsedDisplay = parseMcDisplayName(displayNameRaw || prefixedName);

        return {
          name,
          teamText: parsedDisplay.teamText,
          teamColor: parsedDisplay.color,
          autoName: prefixedName || parsedDisplay.plain || name || null,
        };
      }
    }

    return null;
  }

  function loadConfigFromStorage() {
    try {
      const raw = PAGE.localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      const normalized = sanitizeConfig(parsed);
      Object.assign(CONFIG, normalized);
    } catch (error) {
      console.warn('[NodeMC Player Overlay] load settings failed:', error);
    }
  }

  function saveConfigToStorage() {
    try {
      PAGE.localStorage.setItem(STORAGE_KEY, JSON.stringify(CONFIG));
    } catch (error) {
      console.warn('[NodeMC Player Overlay] save settings failed:', error);
    }
  }

  function installLeafletHook() {
    let _L = PAGE.L;

    try {
      Object.defineProperty(PAGE, 'L', {
        configurable: true,
        enumerable: true,
        get() {
          return _L;
        },
        set(value) {
          _L = value;
          patchLeaflet(value);
        },
      });
    } catch (error) {
      if (CONFIG.DEBUG) {
        console.warn('[NodeMC Player Overlay] hook window.L failed, fallback to direct access:', error);
      }
    }

    if (_L) {
      patchLeaflet(_L);
    }
  }

  function findMapByDom() {
    if (!leafletRef || !leafletRef.Map || !document.querySelector) {
      return null;
    }

    const mapContainer = document.querySelector('#map.leaflet-container');
    if (!mapContainer) {
      return null;
    }

    for (const key of Object.keys(mapContainer)) {
      if (!key.startsWith('_leaflet_')) continue;
      const maybeMap = mapContainer[key];
      if (maybeMap && typeof maybeMap.setView === 'function' && typeof maybeMap.getCenter === 'function') {
        return maybeMap;
      }
    }

    return null;
  }

  function worldToLatLng(map, x, z) {
    const scale = Number.isFinite(map?.options?.scale) ? map.options.scale : 1;
    return leafletRef.latLng(-z * scale, x * scale);
  }

  function normalizeDimension(dim) {
    const text = String(dim || '').toLowerCase();
    if (!text) return '';
    if (text.includes('overworld')) return 'minecraft:overworld';
    if (text.includes('the_nether') || text.endsWith(':nether')) return 'minecraft:the_nether';
    if (text.includes('the_end') || text.endsWith(':end')) return 'minecraft:the_end';
    return text;
  }

  function getPlayerDataNode(node) {
    if (!node || typeof node !== 'object') return null;
    if (node.data && typeof node.data === 'object') return node.data;
    return node;
  }

  function getOnlinePlayers() {
    const snapshotPlayers = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot.players : null;
    const tabState = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot.tabState : null;
    const reports = tabState && typeof tabState.reports === 'object' ? tabState.reports : null;

    const mergedById = new Map();

    const composeDisplayLabel = (rawLabel, rawPlayerName) => {
      const label = String(rawLabel || '').trim();
      const playerName = String(rawPlayerName || '').trim();
      if (!label) return playerName;
      if (!playerName) return label;
      if (label === playerName) return label;
      if (label.includes(playerName)) return label;
      return `${label} ${playerName}`;
    };

    const labelContainsName = (labelText, playerName) => {
      const label = String(labelText || '').trim();
      const name = String(playerName || '').trim();
      if (!label || !name) return false;
      return label.includes(name);
    };

    const upsertPlayer = (entry) => {
      if (!entry || !entry.playerId) return;
      const playerId = String(entry.playerId).trim();
      if (!playerId) return;

      const prev = mergedById.get(playerId);
      if (!prev) {
        const playerName = String(entry.playerName || '').trim();
        const displayLabel = composeDisplayLabel(entry.displayLabel, playerName);
        mergedById.set(playerId, {
          playerId,
          playerName,
          displayLabel,
          teamColor: entry.teamColor || null,
        });
        return;
      }

      const nextName = String(entry.playerName || '').trim();
      const nextDisplay = String(entry.displayLabel || '').trim();
      const keepName = prev.playerName || nextName;

      const prevDisplayWithName = composeDisplayLabel(prev.displayLabel, keepName);
      const nextDisplayWithName = composeDisplayLabel(nextDisplay, nextName || keepName);
      const prevHasName = labelContainsName(prevDisplayWithName, keepName);
      const nextHasName = labelContainsName(nextDisplayWithName, keepName);

      let keepDisplay = prevDisplayWithName || nextDisplayWithName || keepName;
      if ((!prevHasName && nextHasName) || (!prevDisplayWithName && nextDisplayWithName)) {
        keepDisplay = nextDisplayWithName;
      }
      const keepColor = prev.teamColor || entry.teamColor || null;

      mergedById.set(playerId, {
        playerId,
        playerName: keepName,
        displayLabel: keepDisplay,
        teamColor: keepColor,
      });
    };

    if (reports) {
      for (const report of Object.values(reports)) {
        if (!report || typeof report !== 'object') continue;
        const tabPlayers = Array.isArray(report.players) ? report.players : [];
        for (const node of tabPlayers) {
          if (!node || typeof node !== 'object') continue;
          const playerId = String(node.uuid || node.id || '').trim();
          if (!playerId) continue;

          const prefixedName = String(node.prefixedName || '').trim();
          const displayNameRaw = String(node.displayName || '').trim();
          const plainName = String(node.name || '').trim();
          const parsedDisplay = parseMcDisplayName(displayNameRaw || prefixedName);
          const playerName = plainName || parsedDisplay.plain || prefixedName || playerId;
          const displayLabel = composeDisplayLabel(prefixedName || parsedDisplay.plain, playerName);

          upsertPlayer({
            playerId,
            playerName,
            displayLabel,
            teamColor: parsedDisplay.color || null,
          });
        }
      }
    }

    if (snapshotPlayers && typeof snapshotPlayers === 'object') {
      for (const [playerId, rawNode] of Object.entries(snapshotPlayers)) {
        const data = getPlayerDataNode(rawNode);
        const fallbackName = String((data && data.playerName) || (data && data.playerUUID) || playerId || '').trim();
        const tabInfo = getTabPlayerInfo(playerId);
        const playerName = (tabInfo && tabInfo.name) ? tabInfo.name : (fallbackName || String(playerId));
        const displayLabel = composeDisplayLabel(tabInfo && tabInfo.teamText ? tabInfo.teamText : '', playerName);

        upsertPlayer({
          playerId: String(playerId),
          playerName,
          displayLabel,
          teamColor: tabInfo && tabInfo.teamColor ? tabInfo.teamColor : null,
        });
      }
    }

    const players = Array.from(mergedById.values()).map((item) => ({
      ...item,
      playerName: item.playerName || item.displayLabel || item.playerId,
      displayLabel: item.displayLabel || item.playerName || item.playerId,
    }));

    players.sort((a, b) => {
      const textA = String(a.displayLabel || a.playerName || a.playerId || '');
      const textB = String(b.displayLabel || b.playerName || b.playerId || '');
      return textA.localeCompare(textB, 'zh-Hans-CN');
    });
    return players;
  }

  function refreshPlayerSelector() {
    const select = document.getElementById('nodemc-mark-player-select');
    if (!select) return;

    const players = getOnlinePlayers();
    const previousValue = select.value;
    while (select.firstChild) {
      select.removeChild(select.firstChild);
    }

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = players.length ? '请选择在线玩家…' : '暂无在线玩家';
    select.appendChild(placeholder);

    for (const item of players) {
      const option = document.createElement('option');
      option.value = item.playerId;
      option.textContent = item.displayLabel || item.playerName;
      if (item.teamColor) {
        option.style.color = item.teamColor;
      }
      select.appendChild(option);
    }

    if (previousValue && players.some((item) => item.playerId === previousValue)) {
      select.value = previousValue;
    } else {
      select.value = '';
    }
  }

  function resolvePlayerIdFromInput() {
    const select = document.getElementById('nodemc-mark-player-select');
    const selectedPlayerId = select ? String(select.value || '').trim() : '';
    if (selectedPlayerId) {
      return { ok: true, playerId: selectedPlayerId };
    }
    return { ok: false, error: '请先从在线玩家列表选择目标玩家' };
  }

  function readNumber(value) {
    const num = Number(value);
    return Number.isFinite(num) ? num : null;
  }

  function createEmptyAdminSnapshot() {
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

  function applyScopePatchMap(baseMap, scopePatch, requiredKeysForNew = null) {
    const next = (baseMap && typeof baseMap === 'object') ? { ...baseMap } : {};
    if (!scopePatch || typeof scopePatch !== 'object') {
      return next;
    }

    const upsert = (scopePatch.upsert && typeof scopePatch.upsert === 'object') ? scopePatch.upsert : {};
    const remove = Array.isArray(scopePatch.delete) ? scopePatch.delete : [];

    for (const [objectId, value] of Object.entries(upsert)) {
      const prev = next[objectId];
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
          ? { ...prev, ...value }
          : { ...value };
      } else {
        next[objectId] = value;
      }
    }
    for (const objectId of remove) {
      delete next[objectId];
    }
    return next;
  }

  function hasAllKeys(obj, keys) {
    if (!obj || typeof obj !== 'object') {
      return false;
    }
    return keys.every((key) => Object.prototype.hasOwnProperty.call(obj, key));
  }

  function shouldResyncForScopeMissingBaseline(currentMap, scopePatch, requiredKeys) {
    if (!scopePatch || typeof scopePatch !== 'object') {
      return false;
    }

    const upsert = (scopePatch.upsert && typeof scopePatch.upsert === 'object') ? scopePatch.upsert : {};
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

  function requestAdminResync(reason = 'baseline_missing') {
    if (!adminWs || adminWs.readyState !== WebSocket.OPEN) {
      return;
    }
    const now = Date.now();
    if (now - lastAdminResyncRequestAt < 1500) {
      return;
    }
    lastAdminResyncRequestAt = now;
    try {
      adminWs.send(JSON.stringify({ type: 'resync_req', reason }));
    } catch (error) {
      if (CONFIG.DEBUG) {
        console.warn('[NodeMC Player Overlay] resync_req send failed:', error);
      }
    }
  }

  function applyAdminDeltaMessage(message) {
    if (!message || typeof message !== 'object') {
      return;
    }

    if (message.type === 'snapshot_full') {
      latestSnapshot = {
        players: (message.players && typeof message.players === 'object') ? message.players : {},
        entities: (message.entities && typeof message.entities === 'object') ? message.entities : {},
        waypoints: (message.waypoints && typeof message.waypoints === 'object') ? message.waypoints : {},
        playerMarks: (message.playerMarks && typeof message.playerMarks === 'object') ? message.playerMarks : {},
        tabState: (message.tabState && typeof message.tabState === 'object') ? message.tabState : { enabled: false, reports: {}, groups: [] },
        connections: Array.isArray(message.connections) ? message.connections : [],
        connections_count: Number.isFinite(message.connections_count) ? message.connections_count : 0,
        revision: message.revision,
        server_time: message.server_time,
      };
      return;
    }

    if (message.type !== 'patch') {
      return;
    }

    if (!latestSnapshot || typeof latestSnapshot !== 'object') {
      requestAdminResync('patch_before_full_snapshot');
      return;
    }

    const needResync =
      shouldResyncForScopeMissingBaseline(latestSnapshot.players, message.players, ['x', 'y', 'z', 'dimension']) ||
      shouldResyncForScopeMissingBaseline(latestSnapshot.entities, message.entities, ['x', 'y', 'z', 'dimension']);
    if (needResync) {
      requestAdminResync('patch_missing_baseline');
    }

    latestSnapshot.players = applyScopePatchMap(latestSnapshot.players, message.players, ['x', 'y', 'z', 'dimension']);
    latestSnapshot.entities = applyScopePatchMap(latestSnapshot.entities, message.entities, ['x', 'y', 'z', 'dimension']);
    latestSnapshot.waypoints = applyScopePatchMap(latestSnapshot.waypoints, message.waypoints);
    latestSnapshot.playerMarks = applyScopePatchMap(latestSnapshot.playerMarks, message.playerMarks);

    const meta = (message.meta && typeof message.meta === 'object') ? message.meta : {};
    if (meta.tabState && typeof meta.tabState === 'object') {
      latestSnapshot.tabState = meta.tabState;
    }
    if (Array.isArray(meta.connections)) {
      latestSnapshot.connections = meta.connections;
    }
    if (Number.isFinite(meta.connections_count)) {
      latestSnapshot.connections_count = meta.connections_count;
    }

    if (message.revision !== undefined) {
      latestSnapshot.revision = message.revision;
    }
    if (message.server_time !== undefined) {
      latestSnapshot.server_time = message.server_time;
    }
  }

  function getMarkerVisualConfig(markerKind) {
    const isHorse = markerKind === 'horse';
    const iconSizeRaw = Number(isHorse ? CONFIG.HORSE_ICON_SIZE : CONFIG.PLAYER_ICON_SIZE);
    const textSizeRaw = Number(isHorse ? CONFIG.HORSE_TEXT_SIZE : CONFIG.PLAYER_TEXT_SIZE);
    const iconSize = Number.isFinite(iconSizeRaw) ? Math.max(6, Math.min(40, Math.round(iconSizeRaw))) : (isHorse ? 14 : 10);
    const textSize = Number.isFinite(textSizeRaw) ? Math.max(8, Math.min(32, Math.round(textSizeRaw))) : 12;
    return {
      iconSize,
      textSize,
      labelOffset: iconSize,
    };
  }

  function buildMarkerHtml(name, x, z, health, mark, townInfo, markerKind = 'player') {
    const team = mark ? normalizeTeam(mark.team) : 'neutral';
    const color = mark ? normalizeColor(mark.color, getConfiguredTeamColor(team)) : getConfiguredTeamColor(team);
    const showIcon = Boolean(CONFIG.SHOW_PLAYER_ICON);
    const showText = Boolean(CONFIG.SHOW_PLAYER_TEXT);
    if (!showIcon && !showText) {
      return '';
    }

    const escapeHtml = (raw) => String(raw).replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch]));

    let text = name;
    if (CONFIG.SHOW_COORDS) {
      text += ` (${Math.round(x)}, ${Math.round(z)})`;
    }
    if (Number.isFinite(health) && health > 0) {
      text += ` ❤${Math.round(health)}`;
    }

    const teamText = team === 'friendly' ? '友军' : team === 'enemy' ? '敌军' : '中立';
    const noteText = mark && mark.label ? String(mark.label) : '';
    const townText = townInfo && typeof townInfo.text === 'string' ? townInfo.text.trim() : '';
    const townColor = normalizeColor(townInfo && townInfo.color, '#93c5fd');
    const safeName = escapeHtml(text);
    const safeTeam = escapeHtml(teamText);
    const safeNote = escapeHtml(noteText);
    const safeTown = escapeHtml(townText);
    const visual = getMarkerVisualConfig(markerKind);

    const iconContent = markerKind === 'horse' ? '🐎' : '';
    const iconHtml = showIcon
      ? `<span class="n-icon ${markerKind === 'horse' ? 'is-horse' : ''}" style="background:${markerKind === 'horse' ? 'rgba(15,23,42,.92)' : color};box-shadow:0 0 0 2px ${color}55,0 0 0 1px rgba(15,23,42,.95) inset;width:${visual.iconSize}px;height:${visual.iconSize}px;line-height:${visual.iconSize}px;font-size:${Math.max(9, Math.round(visual.iconSize * 0.75))}px;">${iconContent}</span>`
      : '';
    const teamHtml = CONFIG.SHOW_LABEL_TEAM_INFO && markerKind !== 'horse'
      ? `<span class="n-team" style="color:${color}">[${safeTeam}]</span>`
      : '';
    const townHtml = CONFIG.SHOW_LABEL_TOWN_INFO && safeTown
      ? ` <span class="n-town" style="color:${townColor}">${safeTown}</span>`
      : '';
    const gapAfterMeta = (teamHtml || safeNote || townHtml) ? ' ' : '';
    const textHtml = showText
      ? `<span class="n-label" data-align="${showIcon ? 'with-icon' : 'left-anchor'}" style="border-color:${color};box-shadow:0 0 0 1px ${color}55 inset;left:${showIcon ? visual.labelOffset : 0}px;font-size:${visual.textSize}px;">${teamHtml}${safeNote ? `<span class="n-note"> · ${safeNote}</span>` : ''}${townHtml}${gapAfterMeta}${safeName}</span>`
      : '';

    return `<div class="nodemc-player-anchor">${iconHtml}${textHtml}</div>`;
  }

  function upsertMarker(map, playerId, payload) {
    const existing = markersById.get(playerId);
    const latLng = worldToLatLng(map, payload.x, payload.z);
    const html = buildMarkerHtml(payload.name, payload.x, payload.z, payload.health, payload.mark, payload.townInfo, payload.kind || 'player');

    if (!html) {
      if (existing) {
        existing.remove();
        markersById.delete(playerId);
      }
      return;
    }

    if (existing) {
      try {
        existing.setLatLng(latLng);
        existing.setIcon(
          leafletRef.divIcon({
            className: '',
            html,
            iconSize: [0, 0],
            iconAnchor: [0, 0],
          })
        );
        return;
      } catch (e) {
        try { existing.remove(); } catch (_) {}
        try { markersById.delete(playerId); } catch (_) {}
        // fallthrough to recreate marker
      }
    }

    const marker = leafletRef.marker(latLng, {
      icon: leafletRef.divIcon({
        className: '',
        html,
        iconSize: [0, 0],
        iconAnchor: [0, 0],
      }),
      interactive: false,
      keyboard: false,
    });

    marker.addTo(map);
    markersById.set(playerId, marker);
  }

  function buildWaypointHtml(name, x, z, waypoint) {

    let safeName = (name && String(name)) ? String(name) : '标点';
    if (CONFIG.SHOW_COORDS) {
      safeName += ` (${Math.round(x)}, ${Math.round(z)})`;
    }
    if (Number.isFinite(waypoint && waypoint.health) && waypoint.health > 0) {
      safeName += ` ❤${Math.round(waypoint.health)}`;
    }

    const color = normalizeColor(waypoint && waypoint.color, '#f97316');
    const owner = (waypoint && (waypoint.ownerName || waypoint.ownerId)) ? (waypoint.ownerName || waypoint.ownerId) : null;
    const escapeHtml = (raw) => String(raw).replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch]));

    const visual = getMarkerVisualConfig('waypoint');
    const showIcon = Boolean(CONFIG.SHOW_WAYPOINT_ICON);
    const showText = Boolean(CONFIG.SHOW_WAYPOINT_TEXT);
    if (!showIcon && !showText) return '';

    // Container: left edge corresponds to map point. Text left edge should align to point.
    // If icon present, icon will be absolutely positioned so its center aligns to the point (left edge of container),
    // and text box will have padding-left so visible text does not overlap the icon. If icon absent, no padding.
    const ownerHtml = owner ? `<span class="n-wp-owner" style="font-weight:600;display:inline-block;margin-right:6px;color:${color};">${escapeHtml(String(owner))}</span>` : '';

    const paddingLeft = showIcon ? Math.max(0, Math.round(visual.iconSize / 2) + 6) : 0;

    const textBg = `background:rgba(255,255,255,0.85);color:#0f172a;padding:4px 6px;border-radius:6px;display:inline-block;`; // 浅色半透明背景

    const textHtml = showText
      ? `<span class="n-waypoint-label" style="direction:ltr;white-space:nowrap;padding-left:${paddingLeft}px;${textBg};font-size:${visual.textSize}px;">${ownerHtml}${escapeHtml(safeName)}</span>`
      : '';

    const iconHtml = showIcon
      ? `<span class="n-waypoint-icon" style="position:absolute;left:0;top:50%;transform:translate(-50%,-50%);background:${color};width:${visual.iconSize}px;height:${visual.iconSize}px;display:inline-block;border-radius:50%;line-height:${visual.iconSize}px;text-align:center;font-size:${Math.max(10, Math.round(visual.iconSize*0.7))}px;z-index:2;">📍</span>`
      : '';

    return `<div class="nodemc-waypoint-anchor" style="position:relative;display:inline-block;white-space:nowrap;">${textHtml}${iconHtml}</div>`;
  }

  function upsertWaypoint(map, waypointId, payload) {
    const existing = waypointsById.get(waypointId);
    if (!payload || typeof payload !== 'object') return;
    const latLng = worldToLatLng(map, payload.x, payload.z);
    const html = buildWaypointHtml(payload.label || payload.name || waypointId, payload.x, payload.z, payload);

    if (!html) {
      if (existing) {
        existing.remove();
        waypointsById.delete(waypointId);
      }
      return;
    }

    if (existing) {
      try {
        existing.setLatLng(latLng);
        existing.setIcon(
          leafletRef.divIcon({ className: '', html, iconSize: [0, 0], iconAnchor: [0, 0] })
        );
        return;
      } catch (e) {
        try { existing.remove(); } catch (_) {}
        try { waypointsById.delete(waypointId); } catch (_) {}
        // recreate below on failure
      }
    }

    const marker = leafletRef.marker(latLng, {
      icon: leafletRef.divIcon({ className: '', html, iconSize: [0, 0], iconAnchor: [0, 0] }),
      interactive: false,
      keyboard: false,
    });

    marker.addTo(map);
    waypointsById.set(waypointId, marker);
  }

  function removeMissingMarkers(nextIds) {
    for (const [playerId, marker] of markersById.entries()) {
      if (nextIds.has(playerId)) continue;
      marker.remove();
      markersById.delete(playerId);
    }
  }

  function removeMissingWaypoints(nextIds) {
    for (const [wpId, marker] of waypointsById.entries()) {
      if (nextIds.has(wpId)) continue;
      try { marker.remove(); } catch (e) {}
      waypointsById.delete(wpId);
    }
  }

  function applySnapshotPlayers(map, snapshot) {
    const players = snapshot && typeof snapshot === 'object' ? snapshot.players : null;
    if (!players || typeof players !== 'object') {
      removeMissingMarkers(new Set());
      return;
    }

    const wantedDim = normalizeDimension(CONFIG.TARGET_DIMENSION);
    const nextIds = new Set();
    const autoMarkSyncCandidates = [];

    for (const [playerId, rawNode] of Object.entries(players)) {
      const data = getPlayerDataNode(rawNode);
      if (!data) continue;

      const dim = normalizeDimension(data.dimension);
      if (wantedDim && dim !== wantedDim) continue;

      const x = readNumber(data.x);
      const z = readNumber(data.z);
      if (x === null || z === null) continue;

      const health = readNumber(data.health);
      const name = String(data.playerName || data.playerUUID || playerId);
      const existingMark = getPlayerMark(playerId);
      const tabInfo = getTabPlayerInfo(playerId);
      const autoName = getTabPlayerName(playerId) || name;
      const autoMark = autoTeamFromName(autoName);
      const existingMarkSource = existingMark ? normalizeMarkSource(existingMark.source) : 'manual';
      const isLegacyUnknownMark = Boolean(existingMark) && !Boolean(existingMark.hasExplicitSource);
      const legacyLikelyAuto = Boolean(isLegacyUnknownMark && autoMark)
        && normalizeTeam(existingMark.team) === normalizeTeam(autoMark.team);
      const existingActsAsAuto = Boolean(existingMark) && (existingMarkSource === 'auto' || legacyLikelyAuto);
      const isManualMark = Boolean(existingMark) && !existingActsAsAuto;

      if (isManualMark) {
        autoMarkSyncCache.delete(playerId);
      }

      if (!isManualMark) {
        if (autoMark && (autoMark.team === 'friendly' || autoMark.team === 'enemy')) {
          const desiredTeam = normalizeTeam(autoMark.team);
          const desiredColor = normalizeColor(autoMark.color, getConfiguredTeamColor(desiredTeam));
          const hasSameAutoMark = Boolean(existingMark)
            && existingActsAsAuto
            && normalizeTeam(existingMark.team) === desiredTeam
            && normalizeColor(existingMark.color, getConfiguredTeamColor(desiredTeam)) === desiredColor;

          if (!hasSameAutoMark) {
            autoMarkSyncCandidates.push({
              action: 'set',
              playerId,
              team: desiredTeam,
              color: desiredColor,
            });
          }
        } else if (existingActsAsAuto) {
          autoMarkSyncCandidates.push({
            action: 'clear',
            playerId,
          });
        }
      }

      const effectiveMark = isManualMark
        ? existingMark
        : (autoMark || (existingActsAsAuto ? null : existingMark));

      const townInfo = tabInfo && tabInfo.teamText
        ? {
            text: tabInfo.teamText,
            color: tabInfo.teamColor || null,
          }
        : null;

      nextIds.add(playerId);
      upsertMarker(map, playerId, { x, z, health, name, mark: effectiveMark, townInfo });
    }

    const entities = snapshot && typeof snapshot === 'object' ? snapshot.entities : null;
    if (CONFIG.SHOW_HORSE_ENTITIES && entities && typeof entities === 'object') {
      for (const [entityId, rawNode] of Object.entries(entities)) {
        const data = getPlayerDataNode(rawNode);
        if (!data) continue;

        const entityType = String(data.entityType || '').toLowerCase();
        if (!entityType.includes('horse')) continue;

        const dim = normalizeDimension(data.dimension);
        if (wantedDim && dim !== wantedDim) continue;

        const x = readNumber(data.x);
        const z = readNumber(data.z);
        if (x === null || z === null) continue;

        const markerId = `entity:${entityId}`;
        const entityName = String(data.entityName || '马').trim() || '马';
        nextIds.add(markerId);
        upsertMarker(map, markerId, {
          x,
          z,
          health: null,
          name: entityName,
          mark: {
            team: 'neutral',
            color: getConfiguredTeamColor('neutral'),
            label: '',
          },
          townInfo: null,
          kind: 'horse',
        });
      }
    }

    // waypoints: display user-submitted marks (非玩家实体)
    const waypoints = snapshot && typeof snapshot === 'object' ? snapshot.waypoints : null;
    const nextWaypointIds = new Set();
    if (waypoints && typeof waypoints === 'object') {
      for (const [wpId, rawNode] of Object.entries(waypoints)) {
        if (!rawNode) continue;
        const data = (rawNode.data && typeof rawNode.data === 'object') ? rawNode.data : rawNode;
        if (!data) continue;

        const dim = normalizeDimension(data.dimension);
        if (wantedDim && dim !== wantedDim) continue;

        const x = readNumber(data.x);
        const z = readNumber(data.z);
        if (x === null || z === null) continue;

        nextWaypointIds.add(wpId);
        upsertWaypoint(map, wpId, {
          x,
          z,
          label: data.label || data.name || data.title || data.name || wpId,
          color: data.color || (data.colorHex ? data.colorHex : null) || null,
          kind: data.waypointKind || null,
          ownerName: data.ownerName || null,
          ownerId: data.ownerId || null,
        });
      }
    }

    removeMissingMarkers(nextIds);
    removeMissingWaypoints(nextWaypointIds);
    maybeSyncAutoDetectedMarks(autoMarkSyncCandidates);

    PAGE.__NODEMC_PLAYER_OVERLAY__ = {
      revision: snapshot.revision,
      playersOnMap: markersById.size,
      waypointsOnMap: waypointsById.size,
      source: CONFIG.ADMIN_WS_URL,
      dimension: CONFIG.TARGET_DIMENSION,
      wsConnected,
      playerMarks: latestPlayerMarks,
    };
  }

  function updateUiStatus() {
    const status = document.getElementById('nodemc-overlay-status');
    if (!status) return;

    const lastErr = lastErrorText ? `错误: ${lastErrorText}` : '正常';
    const wsText = wsConnected ? '已连接' : '未连接';
    const players = markersById.size;
    const revText = lastRevision === null || lastRevision === undefined ? '-' : String(lastRevision);
    const serverFilterText = sameServerFilterEnabled ? '同服过滤:开' : '同服过滤:关';
    status.textContent = `状态: ${lastErr} | WS: ${wsText} | 标记: ${players} | ${serverFilterText} | Rev: ${revText}`;
  }

  function setPanelVisible(visible) {
    const panel = document.getElementById('nodemc-overlay-panel');
    if (!panel) return;
    panelVisible = Boolean(visible);
    panel.style.display = panelVisible ? 'block' : 'none';
  }

  function fillFormFromConfig() {
    const urlInput = document.getElementById('nodemc-overlay-url');
    const roomCodeInput = document.getElementById('nodemc-overlay-room-code');
    const reconnectInput = document.getElementById('nodemc-overlay-reconnect');
    const dimInput = document.getElementById('nodemc-overlay-dim');
    const showIconInput = document.getElementById('nodemc-overlay-show-icon');
    const showTextInput = document.getElementById('nodemc-overlay-show-text');
    const showHorseInput = document.getElementById('nodemc-overlay-show-horse-entities');
    const showTeamInfoInput = document.getElementById('nodemc-overlay-show-team-info');
    const showTownInfoInput = document.getElementById('nodemc-overlay-show-town-info');
    const playerIconSizeInput = document.getElementById('nodemc-overlay-player-icon-size');
    const playerTextSizeInput = document.getElementById('nodemc-overlay-player-text-size');
    const horseIconSizeInput = document.getElementById('nodemc-overlay-horse-icon-size');
    const horseTextSizeInput = document.getElementById('nodemc-overlay-horse-text-size');
    const coordsInput = document.getElementById('nodemc-overlay-coords');
    const autoTeamInput = document.getElementById('nodemc-overlay-auto-team');
    const friendlyTagsInput = document.getElementById('nodemc-overlay-friendly-tags');
    const enemyTagsInput = document.getElementById('nodemc-overlay-enemy-tags');
    const teamFriendlyColorInput = document.getElementById('nodemc-overlay-team-friendly-color');
    const teamNeutralColorInput = document.getElementById('nodemc-overlay-team-neutral-color');
    const teamEnemyColorInput = document.getElementById('nodemc-overlay-team-enemy-color');
    const debugInput = document.getElementById('nodemc-overlay-debug');
    const wpIconInput = document.getElementById('nodemc-overlay-show-waypoint-icon');
    const wpTextInput = document.getElementById('nodemc-overlay-show-waypoint-text');

    if (urlInput) urlInput.value = CONFIG.ADMIN_WS_URL;
    if (roomCodeInput) roomCodeInput.value = CONFIG.ROOM_CODE;
    if (reconnectInput) reconnectInput.value = String(CONFIG.RECONNECT_INTERVAL_MS);
    if (dimInput) dimInput.value = CONFIG.TARGET_DIMENSION;
    if (showIconInput) showIconInput.checked = CONFIG.SHOW_PLAYER_ICON;
    if (showTextInput) showTextInput.checked = CONFIG.SHOW_PLAYER_TEXT;
    if (showHorseInput) showHorseInput.checked = CONFIG.SHOW_HORSE_ENTITIES;
    if (showTeamInfoInput) showTeamInfoInput.checked = CONFIG.SHOW_LABEL_TEAM_INFO;
    if (showTownInfoInput) showTownInfoInput.checked = CONFIG.SHOW_LABEL_TOWN_INFO;
    if (playerIconSizeInput) playerIconSizeInput.value = String(CONFIG.PLAYER_ICON_SIZE);
    if (playerTextSizeInput) playerTextSizeInput.value = String(CONFIG.PLAYER_TEXT_SIZE);
    if (horseIconSizeInput) horseIconSizeInput.value = String(CONFIG.HORSE_ICON_SIZE);
    if (horseTextSizeInput) horseTextSizeInput.value = String(CONFIG.HORSE_TEXT_SIZE);
    if (coordsInput) coordsInput.checked = CONFIG.SHOW_COORDS;
    if (autoTeamInput) autoTeamInput.checked = CONFIG.AUTO_TEAM_FROM_NAME;
    if (wpIconInput) wpIconInput.checked = CONFIG.SHOW_WAYPOINT_ICON;
    if (wpTextInput) wpTextInput.checked = CONFIG.SHOW_WAYPOINT_TEXT;
    if (friendlyTagsInput) friendlyTagsInput.value = CONFIG.FRIENDLY_TAGS;
    if (enemyTagsInput) enemyTagsInput.value = CONFIG.ENEMY_TAGS;
    if (teamFriendlyColorInput) teamFriendlyColorInput.value = getConfiguredTeamColor('friendly');
    if (teamNeutralColorInput) teamNeutralColorInput.value = getConfiguredTeamColor('neutral');
    if (teamEnemyColorInput) teamEnemyColorInput.value = getConfiguredTeamColor('enemy');
    if (debugInput) debugInput.checked = CONFIG.DEBUG;
  }

  function applyFormToConfig() {
    const urlInput = document.getElementById('nodemc-overlay-url');
    const roomCodeInput = document.getElementById('nodemc-overlay-room-code');
    const reconnectInput = document.getElementById('nodemc-overlay-reconnect');
    const dimInput = document.getElementById('nodemc-overlay-dim');
    const showIconInput = document.getElementById('nodemc-overlay-show-icon');
    const showTextInput = document.getElementById('nodemc-overlay-show-text');
    const showHorseInput = document.getElementById('nodemc-overlay-show-horse-entities');
    const showTeamInfoInput = document.getElementById('nodemc-overlay-show-team-info');
    const showTownInfoInput = document.getElementById('nodemc-overlay-show-town-info');
    const playerIconSizeInput = document.getElementById('nodemc-overlay-player-icon-size');
    const playerTextSizeInput = document.getElementById('nodemc-overlay-player-text-size');
    const horseIconSizeInput = document.getElementById('nodemc-overlay-horse-icon-size');
    const horseTextSizeInput = document.getElementById('nodemc-overlay-horse-text-size');
    const coordsInput = document.getElementById('nodemc-overlay-coords');
    const autoTeamInput = document.getElementById('nodemc-overlay-auto-team');
    const friendlyTagsInput = document.getElementById('nodemc-overlay-friendly-tags');
    const enemyTagsInput = document.getElementById('nodemc-overlay-enemy-tags');
    const teamFriendlyColorInput = document.getElementById('nodemc-overlay-team-friendly-color');
    const teamNeutralColorInput = document.getElementById('nodemc-overlay-team-neutral-color');
    const teamEnemyColorInput = document.getElementById('nodemc-overlay-team-enemy-color');
    const debugInput = document.getElementById('nodemc-overlay-debug');
    const wpIconInput = document.getElementById('nodemc-overlay-show-waypoint-icon');
    const wpTextInput = document.getElementById('nodemc-overlay-show-waypoint-text');

    const next = sanitizeConfig({
      ADMIN_WS_URL: urlInput ? urlInput.value : CONFIG.ADMIN_WS_URL,
      ROOM_CODE: roomCodeInput ? roomCodeInput.value : CONFIG.ROOM_CODE,
      RECONNECT_INTERVAL_MS: reconnectInput ? reconnectInput.value : CONFIG.RECONNECT_INTERVAL_MS,
      TARGET_DIMENSION: dimInput ? dimInput.value : CONFIG.TARGET_DIMENSION,
      SHOW_PLAYER_ICON: showIconInput ? showIconInput.checked : CONFIG.SHOW_PLAYER_ICON,
      SHOW_PLAYER_TEXT: showTextInput ? showTextInput.checked : CONFIG.SHOW_PLAYER_TEXT,
      SHOW_HORSE_ENTITIES: showHorseInput ? showHorseInput.checked : CONFIG.SHOW_HORSE_ENTITIES,
      SHOW_LABEL_TEAM_INFO: showTeamInfoInput ? showTeamInfoInput.checked : CONFIG.SHOW_LABEL_TEAM_INFO,
      SHOW_LABEL_TOWN_INFO: showTownInfoInput ? showTownInfoInput.checked : CONFIG.SHOW_LABEL_TOWN_INFO,
      PLAYER_ICON_SIZE: playerIconSizeInput ? playerIconSizeInput.value : CONFIG.PLAYER_ICON_SIZE,
      PLAYER_TEXT_SIZE: playerTextSizeInput ? playerTextSizeInput.value : CONFIG.PLAYER_TEXT_SIZE,
      HORSE_ICON_SIZE: horseIconSizeInput ? horseIconSizeInput.value : CONFIG.HORSE_ICON_SIZE,
      HORSE_TEXT_SIZE: horseTextSizeInput ? horseTextSizeInput.value : CONFIG.HORSE_TEXT_SIZE,
      SHOW_COORDS: coordsInput ? coordsInput.checked : CONFIG.SHOW_COORDS,
      AUTO_TEAM_FROM_NAME: autoTeamInput ? autoTeamInput.checked : CONFIG.AUTO_TEAM_FROM_NAME,
      FRIENDLY_TAGS: friendlyTagsInput ? friendlyTagsInput.value : CONFIG.FRIENDLY_TAGS,
      ENEMY_TAGS: enemyTagsInput ? enemyTagsInput.value : CONFIG.ENEMY_TAGS,
      TEAM_COLOR_FRIENDLY: teamFriendlyColorInput ? teamFriendlyColorInput.value : CONFIG.TEAM_COLOR_FRIENDLY,
      TEAM_COLOR_NEUTRAL: teamNeutralColorInput ? teamNeutralColorInput.value : CONFIG.TEAM_COLOR_NEUTRAL,
      TEAM_COLOR_ENEMY: teamEnemyColorInput ? teamEnemyColorInput.value : CONFIG.TEAM_COLOR_ENEMY,
      SHOW_WAYPOINT_ICON: wpIconInput ? wpIconInput.checked : CONFIG.SHOW_WAYPOINT_ICON,
      SHOW_WAYPOINT_TEXT: wpTextInput ? wpTextInput.checked : CONFIG.SHOW_WAYPOINT_TEXT,
      DEBUG: debugInput ? debugInput.checked : CONFIG.DEBUG,
    });

    Object.assign(CONFIG, next);
    saveConfigToStorage();
    updateUiStatus();
  }

  function sendAdminCommand(message) {
    if (!adminWs || adminWs.readyState !== WebSocket.OPEN) {
      lastErrorText = 'ws not connected';
      updateUiStatus();
      return false;
    }
    try {
      adminWs.send(JSON.stringify(message));
      return true;
    } catch (error) {
      lastErrorText = String(error && error.message ? error.message : error);
      updateUiStatus();
      return false;
    }
  }

  function applyMarkFormToServer() {
    const teamInput = document.getElementById('nodemc-mark-team');
    const colorInput = document.getElementById('nodemc-mark-color');
    const labelInput = document.getElementById('nodemc-mark-label');

    const resolved = resolvePlayerIdFromInput();
    if (!resolved.ok) {
      lastErrorText = resolved.error;
      updateUiStatus();
      return;
    }
    const playerId = resolved.playerId;

    const team = normalizeTeam(teamInput ? teamInput.value : 'neutral');
    const color = normalizeColor(colorInput ? colorInput.value : getConfiguredTeamColor(team), getConfiguredTeamColor(team));
    const label = labelInput ? String(labelInput.value || '').trim() : '';

    const ok = sendAdminCommand({
      type: 'command_player_mark_set',
      playerId,
      team,
      color,
      label,
      source: 'manual',
    });
    if (ok) {
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function clearMarkOnServer() {
    const resolved = resolvePlayerIdFromInput();
    if (!resolved.ok) {
      lastErrorText = resolved.error;
      updateUiStatus();
      return;
    }
    const playerId = resolved.playerId;

    const ok = sendAdminCommand({
      type: 'command_player_mark_clear',
      playerId,
    });
    if (ok) {
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function clearAllMarksOnServer() {
    const ok = sendAdminCommand({ type: 'command_player_mark_clear_all' });
    if (ok) {
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function setSameServerFilter(enabled) {
    const ok = sendAdminCommand({
      type: 'command_same_server_filter_set',
      enabled: Boolean(enabled),
    });
    if (ok) {
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function injectSettingsUi() {
    if (uiMounted || !document.body) return;
    uiMounted = true;

    const style = document.createElement('style');
    style.id = 'nodemc-overlay-ui-style';
    style.textContent = UI_STYLE_TEXT;
    document.head.appendChild(style);

    const fab = document.createElement('div');
    fab.id = 'nodemc-overlay-fab';
    fab.textContent = '⚙';
    fab.title = 'NodeMC Overlay 设置';

    const panel = document.createElement('div');
    panel.id = 'nodemc-overlay-panel';

    const parsedPanel = new DOMParser().parseFromString(PANEL_HTML, 'text/html');
    const panelNodes = Array.from(parsedPanel.body.childNodes);
    for (const node of panelNodes) {
      panel.appendChild(node.cloneNode(true));
    }

    document.body.appendChild(fab);
    document.body.appendChild(panel);

    const updatePanelPositionNearFab = () => {
      const fabRect = fab.getBoundingClientRect();
      const panelWidth = panel.offsetWidth || 320;
      const panelHeight = panel.offsetHeight || 280;
      const margin = 10;

      let left = fabRect.left - panelWidth + fabRect.width;
      let top = fabRect.top - panelHeight - margin;

      if (left < margin) left = margin;
      if (left + panelWidth > window.innerWidth - margin) left = window.innerWidth - panelWidth - margin;
      if (top < margin) top = Math.min(window.innerHeight - panelHeight - margin, fabRect.bottom + margin);
      if (top < margin) top = margin;

      panel.style.left = `${Math.round(left)}px`;
      panel.style.top = `${Math.round(top)}px`;
      panel.style.right = 'auto';
      panel.style.bottom = 'auto';
    };

    const clampFabPosition = (left, top) => {
      const width = fab.offsetWidth || 34;
      const height = fab.offsetHeight || 34;
      const margin = 6;
      const minLeft = margin;
      const minTop = margin;
      const maxLeft = Math.max(minLeft, window.innerWidth - width - margin);
      const maxTop = Math.max(minTop, window.innerHeight - height - margin);
      return {
        left: Math.min(maxLeft, Math.max(minLeft, left)),
        top: Math.min(maxTop, Math.max(minTop, top)),
      };
    };

    const clampPanelPosition = (left, top) => {
      const width = panel.offsetWidth || 320;
      const height = panel.offsetHeight || 280;
      const margin = 6;
      const minLeft = margin;
      const minTop = margin;
      const maxLeft = Math.max(minLeft, window.innerWidth - width - margin);
      const maxTop = Math.max(minTop, window.innerHeight - height - margin);
      return {
        left: Math.min(maxLeft, Math.max(minLeft, left)),
        top: Math.min(maxTop, Math.max(minTop, top)),
      };
    };

    const setPanelPosition = (left, top) => {
      const clamped = clampPanelPosition(left, top);
      panel.style.left = `${Math.round(clamped.left)}px`;
      panel.style.top = `${Math.round(clamped.top)}px`;
      panel.style.right = 'auto';
      panel.style.bottom = 'auto';
      return clamped;
    };

    const setFabPosition = (left, top, syncPanel = true) => {
      const clamped = clampFabPosition(left, top);
      fab.style.left = `${Math.round(clamped.left)}px`;
      fab.style.top = `${Math.round(clamped.top)}px`;
      fab.style.right = 'auto';
      fab.style.bottom = 'auto';
      if (panelVisible && syncPanel) {
        updatePanelPositionNearFab();
      }
      return clamped;
    };

    const initialRect = fab.getBoundingClientRect();
    setFabPosition(initialRect.left, initialRect.top);

    let dragState = null;
    let dragMoved = false;

    const beginDrag = (event, kind) => {
      dragState = {
        pointerId: event.pointerId,
        kind,
        startX: event.clientX,
        startY: event.clientY,
        fabLeft: fab.offsetLeft,
        fabTop: fab.offsetTop,
        panelLeft: panel.offsetLeft,
        panelTop: panel.offsetTop,
      };
      dragMoved = false;
    };

    const moveDrag = (event) => {
      if (!dragState || event.pointerId !== dragState.pointerId) return;
      const dx = event.clientX - dragState.startX;
      const dy = event.clientY - dragState.startY;
      if (!dragMoved && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
        dragMoved = true;
      }

      if (dragState.kind === 'fab') {
        setFabPosition(dragState.fabLeft + dx, dragState.fabTop + dy, true);
        return;
      }

      if (dragState.kind === 'panel') {
        const appliedFab = setFabPosition(dragState.fabLeft + dx, dragState.fabTop + dy, false);
        const appliedDx = appliedFab.left - dragState.fabLeft;
        const appliedDy = appliedFab.top - dragState.fabTop;
        setPanelPosition(dragState.panelLeft + appliedDx, dragState.panelTop + appliedDy);
      }
    };

    const endDrag = (event, sourceElement) => {
      if (!dragState || event.pointerId !== dragState.pointerId) return;
      try {
        sourceElement.releasePointerCapture(event.pointerId);
      } catch (_) {}
      dragState = null;
      setTimeout(() => {
        dragMoved = false;
      }, 0);
    };

    fab.addEventListener('pointerdown', (event) => {
      beginDrag(event, 'fab');
      try {
        fab.setPointerCapture(event.pointerId);
      } catch (_) {}
    });

    fab.addEventListener('pointermove', moveDrag);

    const titleBar = panel.querySelector('#nodemc-overlay-title');
    titleBar?.addEventListener('pointerdown', (event) => {
      beginDrag(event, 'panel');
      try {
        titleBar.setPointerCapture(event.pointerId);
      } catch (_) {}
    });

    titleBar?.addEventListener('pointermove', moveDrag);

    fab.addEventListener('pointerup', (event) => endDrag(event, fab));
    fab.addEventListener('pointercancel', (event) => endDrag(event, fab));
    titleBar?.addEventListener('pointerup', (event) => endDrag(event, titleBar));
    titleBar?.addEventListener('pointercancel', (event) => endDrag(event, titleBar));

    fillFormFromConfig();
    updateUiStatus();

    const setPanelPage = (nextPage) => {
      panelPage = nextPage === 'advanced' || nextPage === 'mark' ? nextPage : 'main';
      const mainPage = document.getElementById('nodemc-overlay-page-main');
      const advancedPage = document.getElementById('nodemc-overlay-page-advanced');
      const markPage = document.getElementById('nodemc-overlay-page-mark');
      if (mainPage) {
        mainPage.classList.toggle('active', panelPage === 'main');
      }
      if (advancedPage) {
        advancedPage.classList.toggle('active', panelPage === 'advanced');
      }
      if (markPage) {
        markPage.classList.toggle('active', panelPage === 'mark');
      }
    };
    setPanelPage('main');

    fab.addEventListener('click', () => {
      if (dragMoved) return;
      setPanelVisible(!panelVisible);
      if (panelVisible) {
        updatePanelPositionNearFab();
      }
    });

    document.getElementById('nodemc-overlay-open-advanced')?.addEventListener('click', () => {
      setPanelPage('advanced');
    });

    document.getElementById('nodemc-overlay-open-mark')?.addEventListener('click', () => {
      setPanelPage('mark');
    });

    document.getElementById('nodemc-overlay-back-main')?.addEventListener('click', () => {
      setPanelPage('main');
    });

    document.getElementById('nodemc-overlay-back-main-from-mark')?.addEventListener('click', () => {
      setPanelPage('main');
    });

    document.getElementById('nodemc-overlay-save')?.addEventListener('click', () => {
      applyFormToConfig();
      reconnectAdminWs();
    });

    document.getElementById('nodemc-overlay-save-advanced')?.addEventListener('click', () => {
      applyFormToConfig();
      reconnectAdminWs();
    });

    document.getElementById('nodemc-overlay-reset')?.addEventListener('click', () => {
      Object.assign(CONFIG, DEFAULT_CONFIG);
      saveConfigToStorage();
      fillFormFromConfig();
      reconnectAdminWs();
      updateUiStatus();
    });

    document.getElementById('nodemc-overlay-refresh')?.addEventListener('click', () => {
      reconnectAdminWs();
    });

    const teamInput = document.getElementById('nodemc-mark-team');
    const colorInput = document.getElementById('nodemc-mark-color');
    const selectInput = document.getElementById('nodemc-mark-player-select');
    if (teamInput && colorInput) {
      teamInput.addEventListener('change', () => {
        const team = normalizeTeam(teamInput.value);
        colorInput.value = getConfiguredTeamColor(team);
      });
      colorInput.value = getConfiguredTeamColor(normalizeTeam(teamInput.value));
    }

    if (selectInput) {
      selectInput.addEventListener('change', () => {
        lastErrorText = null;
        updateUiStatus();
      });
    }

    const serverFilterInput = document.getElementById('nodemc-overlay-server-filter');
    if (serverFilterInput) {
      serverFilterInput.addEventListener('change', () => {
        setSameServerFilter(serverFilterInput.checked);
      });
    }

    refreshPlayerSelector();

    document.getElementById('nodemc-mark-apply')?.addEventListener('click', () => {
      applyMarkFormToServer();
    });

    document.getElementById('nodemc-mark-clear')?.addEventListener('click', () => {
      clearMarkOnServer();
    });

    document.getElementById('nodemc-mark-clear-all')?.addEventListener('click', () => {
      clearAllMarksOnServer();
    });
  }

  function mountUiWhenReady() {
    if (uiMounted) return;
    if (!document.body || !document.head) {
      PAGE.requestAnimationFrame(mountUiWhenReady);
      return;
    }
    injectSettingsUi();
  }

  function applyLatestSnapshotIfPossible() {
    if (!latestSnapshot) return;
    const map = capturedMap || findMapByDom();
    if (!map || !leafletRef || !map._loaded) return;
    applySnapshotPlayers(map, latestSnapshot);
  }

  function scheduleReconnect() {
    if (reconnectTimer !== null) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connectAdminWs();
    }, CONFIG.RECONNECT_INTERVAL_MS);
  }

  function cleanupWs() {
    if (adminWs) {
      adminWs.onopen = null;
      adminWs.onmessage = null;
      adminWs.onerror = null;
      adminWs.onclose = null;
      try {
        adminWs.close();
      } catch (_) {}
      adminWs = null;
    }
    wsConnected = false;
    if (reconnectTimer !== null) {
      try { clearTimeout(reconnectTimer); } catch (_) {}
      reconnectTimer = null;
    }
  }

  function reconnectAdminWs() {
    manualWsClose = true;
    cleanupWs();
    manualWsClose = false;
    lastErrorText = null;
    connectAdminWs();
    updateUiStatus();
  }

  function connectAdminWs() {
    if (adminWs && (adminWs.readyState === WebSocket.OPEN || adminWs.readyState === WebSocket.CONNECTING)) {
      return;
    }

    let ws;
    try {
      ws = new WebSocket(CONFIG.ADMIN_WS_URL);
    } catch (error) {
      const text = String(error && error.message ? error.message : error);
      if (text !== lastErrorText) {
        lastErrorText = text;
        console.warn('[NodeMC Player Overlay] ws connect failed:', text, CONFIG.ADMIN_WS_URL);
      }
      updateUiStatus();
      scheduleReconnect();
      return;
    }

    adminWs = ws;
    ws.onopen = () => {
      wsConnected = true;
      lastErrorText = null;
      try {
        ws.send(JSON.stringify({
          type: 'handshake',
          networkProtocolVersion: ADMIN_NETWORK_PROTOCOL_VERSION,
          protocolVersion: ADMIN_NETWORK_PROTOCOL_VERSION,
          localProgramVersion: LOCAL_PROGRAM_VERSION,
          roomCode: normalizeRoomCode(CONFIG.ROOM_CODE),
          supportsDelta: true,
          channel: 'admin',
        }));
      } catch (error) {
        lastErrorText = String(error && error.message ? error.message : error);
      }
      updateUiStatus();

      if (CONFIG.DEBUG) {
        console.debug('[NodeMC Player Overlay] ws connected', { url: CONFIG.ADMIN_WS_URL });
      }
    };

    ws.onmessage = (event) => {
      if (typeof event?.data !== 'string') return;
      try {
        const snapshot = JSON.parse(event.data);
        lastAdminMessageType = snapshot && snapshot.type ? String(snapshot.type) : 'unknown';
        lastAdminMessageAt = Date.now();
        lastAdminMessageRevision = snapshot && snapshot.revision !== undefined
          ? snapshot.revision
          : (snapshot && snapshot.rev !== undefined ? snapshot.rev : null);
        if (snapshot && snapshot.type === 'admin_ack') {
          if (snapshot.ok) {
            lastErrorText = null;
          } else if (snapshot.error) {
            lastErrorText = `命令失败: ${snapshot.error}`;
          }
          updateUiStatus();
          return;
        }

        if (snapshot && snapshot.type === 'pong') {
          lastErrorText = null;
          updateUiStatus();
          return;
        }

        if (snapshot && snapshot.type === 'handshake_ack') {
          lastErrorText = null;
          updateUiStatus();
          return;
        }

        applyAdminDeltaMessage(snapshot);
        sameServerFilterEnabled = Boolean(latestSnapshot?.tabState?.enabled);
        const serverFilterInput = document.getElementById('nodemc-overlay-server-filter');
        if (serverFilterInput) {
          serverFilterInput.checked = sameServerFilterEnabled;
        }
        latestPlayerMarks = latestSnapshot && typeof latestSnapshot.playerMarks === 'object' && latestSnapshot.playerMarks
          ? latestSnapshot.playerMarks
          : {};
        refreshPlayerSelector();
        lastRevision = latestSnapshot?.revision;
        lastErrorText = null;
        applyLatestSnapshotIfPossible();
        updateUiStatus();
      } catch (error) {
        const text = String(error && error.message ? error.message : error);
        if (text !== lastErrorText) {
          lastErrorText = text;
          console.warn('[NodeMC Player Overlay] ws message parse failed:', text);
        }
        updateUiStatus();
      }
    };

    ws.onerror = () => {
      wsConnected = false;
      if (!lastErrorText) {
        lastErrorText = 'ws error';
      }
      updateUiStatus();
    };

    ws.onclose = () => {
      wsConnected = false;
      adminWs = null;
      if (!manualWsClose) {
        if (CONFIG.DEBUG) {
          console.warn('[NodeMC Player Overlay] ws disconnected, reconnect scheduled', CONFIG.ADMIN_WS_URL);
        }
        scheduleReconnect();
      }
      updateUiStatus();
    };
  }

  function ensureOverlayStyles() {
    if (document.getElementById('nodemc-projection-style')) {
      return;
    }
    const style = document.createElement('style');
    style.id = 'nodemc-projection-style';
    style.textContent = OVERLAY_STYLE_TEXT;
    document.head.appendChild(style);
  }

  function installDebugConsoleApi() {
    const debugApi = {
      help() {
        const commands = {
          help: '显示可用命令',
          summary: '查看连接状态/对象数量/最近消息',
          snapshot: '输出最新内存快照',
          markers: '输出当前地图 marker id 列表',
          ws: '输出 websocket 状态',
          last: '输出最近一条 ws 消息元信息',
          resync: '手动发送 resync_req 请求全量',
          ping: '手动发送 ping',
        };
        console.table(commands);
        return commands;
      },
      summary() {
        const snapshot = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot : {};
        return {
          wsConnected,
          wsReadyState: adminWs ? adminWs.readyState : -1,
          revision: lastRevision,
          lastErrorText,
          sameServerFilterEnabled,
          playersCount: snapshot.players ? Object.keys(snapshot.players).length : 0,
          entitiesCount: snapshot.entities ? Object.keys(snapshot.entities).length : 0,
          waypointsCount: snapshot.waypoints ? Object.keys(snapshot.waypoints).length : 0,
          markersOnMap: markersById.size,
          lastAdminMessageType,
          lastAdminMessageAt,
          lastAdminMessageRevision,
        };
      },
      snapshot() {
        return latestSnapshot;
      },
      markers() {
        return Array.from(markersById.keys());
      },
      ws() {
        return {
          url: CONFIG.ADMIN_WS_URL,
          connected: wsConnected,
          readyState: adminWs ? adminWs.readyState : -1,
          reconnectTimerPending: reconnectTimer !== null,
        };
      },
      last() {
        return {
          type: lastAdminMessageType,
          at: lastAdminMessageAt,
          revision: lastAdminMessageRevision,
        };
      },
      resync(reason = 'manual_console_debug') {
        requestAdminResync(reason);
        return { requested: true, reason };
      },
      ping() {
        if (!adminWs || adminWs.readyState !== WebSocket.OPEN) {
          return { sent: false, reason: 'ws_not_open' };
        }
        adminWs.send(JSON.stringify({ type: 'ping', from: 'console_debug' }));
        return { sent: true };
      },
    };

    PAGE.__NODEMC_OVERLAY_DEBUG__ = debugApi;
    PAGE.nodemcDebug = debugApi;
  }

  function cleanupAll() {
    try {
      // remove markers
      for (const m of markersById.values()) {
        try { m.remove(); } catch (_) {}
      }
      markersById.clear();
      for (const m of waypointsById.values()) {
        try { m.remove(); } catch (_) {}
      }
      waypointsById.clear();

      // remove injected UI and styles
      try { const s = document.getElementById('nodemc-overlay-ui-style'); if (s) s.remove(); } catch (_) {}
      try { const s2 = document.getElementById('nodemc-projection-style'); if (s2) s2.remove(); } catch (_) {}
      try { const fab = document.getElementById('nodemc-overlay-fab'); if (fab) fab.remove(); } catch (_) {}
      try { const panel = document.getElementById('nodemc-overlay-panel'); if (panel) panel.remove(); } catch (_) {}

      // clear globals
      try { delete PAGE.__NODEMC_OVERLAY_DEBUG__; } catch (_) {}
      try { delete PAGE.__NODEMC_PLAYER_OVERLAY__; } catch (_) {}

      // cleanup websocket and timers
      cleanupWs();
      autoMarkSyncCache.clear();
      lastAutoMarkSyncAt = 0;

      uiMounted = false;
      overlayStarted = false;
    } catch (_) {}
  }

  // cleanup on page unload
  try { window.addEventListener('beforeunload', cleanupAll); } catch (_) {}

  function initOverlay() {
    ensureOverlayStyles();
    applyLatestSnapshotIfPossible();
  }

  function boot() {
    installDebugConsoleApi();
    loadConfigFromStorage();
    CONFIG.ADMIN_WS_URL = normalizeWsUrl(CONFIG.ADMIN_WS_URL);
    CONFIG.ROOM_CODE = normalizeRoomCode(CONFIG.ROOM_CODE);
    installLeafletHook();
    connectAdminWs();
    mountUiWhenReady();

    if (CONFIG.DEBUG) {
      console.log('[NodeMC Player Overlay] boot', {
        wsUrl: CONFIG.ADMIN_WS_URL,
        reconnectMs: CONFIG.RECONNECT_INTERVAL_MS,
      });
    }

    const tryStart = () => {
      const map = capturedMap || findMapByDom();
      if (map && leafletRef) {
        initOverlay();
        overlayStarted = true;
        return;
      }
      if (!overlayStarted) PAGE.requestAnimationFrame(tryStart);
    };

    tryStart();
  }

  boot();
})();
