// ==UserScript==
// @name         NodeMC Projection Test
// @namespace    https://map.nodemc.cc/
// @version      0.2.0
// @description  将本地 MultiplePlayerESP 后台玩家信息投影到 NodeMC 地图
// @author       You
// @match        https://map.nodemc.cc/*
// @match        http://map.nodemc.cc/*
// @match        file:///*NodeMC*时局图*.html*
// @run-at       document-start
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// @connect      *
// ==/UserScript==

(function () {
  'use strict';

  const PAGE = (typeof unsafeWindow !== 'undefined' && unsafeWindow) ? unsafeWindow : window;
  const STORAGE_KEY = 'nodemc_player_overlay_settings_v1';

  const DEFAULT_CONFIG = {
    ADMIN_WS_URL: 'ws://127.0.0.1:8765/adminws',
    RECONNECT_INTERVAL_MS: 1000,
    TARGET_DIMENSION: 'minecraft:overworld',
    SHOW_COORDS: false,
    AUTO_TEAM_FROM_NAME: true,
    FRIENDLY_TAGS: '[xxx]',
    ENEMY_TAGS: '[yyy]',
    DEBUG: false,
  };
  const CONFIG = { ...DEFAULT_CONFIG };

  let leafletRef = null;
  let capturedMap = null;
  let markersById = new Map();
  let lastRevision = null;
  let lastErrorText = null;
  let latestSnapshot = null;
  let uiMounted = false;
  let panelVisible = false;
  let adminWs = null;
  let wsConnected = false;
  let reconnectTimer = null;
  let manualWsClose = false;
  let latestPlayerMarks = {};
  let sameServerFilterEnabled = false;

  const TEAM_DEFAULT_COLORS = {
    friendly: '#3b82f6',
    enemy: '#ef4444',
    neutral: '#94a3b8',
  };

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

    const reconnect = Number(candidate.RECONNECT_INTERVAL_MS ?? candidate.POLL_INTERVAL_MS);
    if (Number.isFinite(reconnect)) {
      next.RECONNECT_INTERVAL_MS = Math.max(200, Math.min(60000, Math.round(reconnect)));
    }

    if (typeof candidate.TARGET_DIMENSION === 'string' && candidate.TARGET_DIMENSION.trim()) {
      next.TARGET_DIMENSION = candidate.TARGET_DIMENSION.trim();
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

  function normalizeWsUrl(rawUrl) {
    const text = String(rawUrl || '').trim();
    if (!text) return DEFAULT_CONFIG.ADMIN_WS_URL;

    let next = text;
    if (next.startsWith('http://')) next = 'ws://' + next.slice('http://'.length);
    if (next.startsWith('https://')) next = 'wss://' + next.slice('https://'.length);
    if (next.endsWith('/snapshot')) next = next.slice(0, -('/snapshot'.length)) + '/adminws';
    return next;
  }

  function normalizeTeam(teamValue) {
    const text = String(teamValue || '').trim().toLowerCase();
    if (text === 'friendly' || text === 'friend' || text === 'ally' || text === 'blue') return 'friendly';
    if (text === 'enemy' || text === 'hostile' || text === 'red') return 'enemy';
    return 'neutral';
  }

  function normalizeColor(colorValue, fallbackColor) {
    const fallback = typeof fallbackColor === 'string' && fallbackColor ? fallbackColor : TEAM_DEFAULT_COLORS.neutral;
    const text = String(colorValue || '').trim();
    if (!text) return fallback;

    const raw = text.startsWith('#') ? text.slice(1) : text;
    if (!/^[0-9a-fA-F]{6}$/.test(raw)) {
      return fallback;
    }
    return `#${raw.toLowerCase()}`;
  }

  function getPlayerMark(playerId) {
    if (!latestPlayerMarks || typeof latestPlayerMarks !== 'object') return null;
    const entry = latestPlayerMarks[playerId];
    if (!entry || typeof entry !== 'object') return null;

    const team = normalizeTeam(entry.team);
    const color = normalizeColor(entry.color, TEAM_DEFAULT_COLORS[team]);
    const label = typeof entry.label === 'string' && entry.label.trim() ? entry.label.trim() : null;
    return { team, color, label };
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
        color: TEAM_DEFAULT_COLORS.friendly,
        label: '自动识别:友军',
      };
    }
    if (enemyTags.some((tag) => name.includes(tag))) {
      return {
        team: 'enemy',
        color: TEAM_DEFAULT_COLORS.enemy,
        label: '自动识别:敌军',
      };
    }
    return null;
  }

  function getTabPlayerName(playerId) {
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
        const displayName = String(node.displayName || '').trim();
        const name = String(node.name || '').trim();
        if (prefixedName) return prefixedName;
        if (displayName) return displayName;
        if (name) return name;
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
    if (!snapshotPlayers || typeof snapshotPlayers !== 'object') return [];

    const players = [];
    for (const [playerId, rawNode] of Object.entries(snapshotPlayers)) {
      const data = getPlayerDataNode(rawNode);
      const name = String((data && data.playerName) || (data && data.playerUUID) || playerId || '').trim();
      players.push({
        playerId: String(playerId),
        playerName: name || String(playerId),
      });
    }

    players.sort((a, b) => a.playerName.localeCompare(b.playerName, 'zh-Hans-CN'));
    return players;
  }

  function refreshPlayerSelector() {
    const select = document.getElementById('nodemc-mark-player-select');
    if (!select) return;

    const players = getOnlinePlayers();
    const previousValue = select.value;
    select.innerHTML = '';

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = players.length ? '请选择在线玩家…' : '暂无在线玩家';
    select.appendChild(placeholder);

    for (const item of players) {
      const option = document.createElement('option');
      option.value = item.playerId;
      option.textContent = `${item.playerName} ｜ ${item.playerId}`;
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

  function buildMarkerHtml(name, x, z, health, mark) {
    let text = name;
    if (CONFIG.SHOW_COORDS) {
      text += ` (${Math.round(x)}, ${Math.round(z)})`;
    }
    if (Number.isFinite(health) && health > 0) {
      text += ` ❤${Math.round(health)}`;
    }

    const team = mark ? normalizeTeam(mark.team) : 'neutral';
    const color = mark ? normalizeColor(mark.color, TEAM_DEFAULT_COLORS[team]) : TEAM_DEFAULT_COLORS.neutral;
    const teamText = mark && mark.label ? mark.label : (team === 'friendly' ? '友军' : team === 'enemy' ? '敌军' : '中立');
    const safeName = String(text).replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch]));
    const safeTeam = String(teamText).replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch]));

    return `<div class="nodemc-player-label" style="border-color:${color};box-shadow:0 0 0 1px ${color}55 inset;"><span class="n-team" style="color:${color}">[${safeTeam}]</span> ${safeName}</div>`;
  }

  function upsertMarker(map, playerId, payload) {
    const existing = markersById.get(playerId);
    const latLng = worldToLatLng(map, payload.x, payload.z);
    const html = buildMarkerHtml(payload.name, payload.x, payload.z, payload.health, payload.mark);

    if (existing) {
      existing.setLatLng(latLng);
      existing.setIcon(
        leafletRef.divIcon({
          className: '',
          html,
          iconSize: null,
        })
      );
      return;
    }

    const marker = leafletRef.marker(latLng, {
      icon: leafletRef.divIcon({
        className: '',
        html,
        iconSize: null,
      }),
      interactive: false,
      keyboard: false,
    });

    marker.addTo(map);
    markersById.set(playerId, marker);
  }

  function removeMissingMarkers(nextIds) {
    for (const [playerId, marker] of markersById.entries()) {
      if (nextIds.has(playerId)) continue;
      marker.remove();
      markersById.delete(playerId);
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
      const mark = getPlayerMark(playerId);
      const autoName = getTabPlayerName(playerId) || name;
      const autoMark = mark ? null : autoTeamFromName(autoName);

      nextIds.add(playerId);
      upsertMarker(map, playerId, { x, z, health, name, mark: mark || autoMark });
    }

    removeMissingMarkers(nextIds);

    PAGE.__NODEMC_PLAYER_OVERLAY__ = {
      revision: snapshot.revision,
      playersOnMap: markersById.size,
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
    const reconnectInput = document.getElementById('nodemc-overlay-reconnect');
    const dimInput = document.getElementById('nodemc-overlay-dim');
    const coordsInput = document.getElementById('nodemc-overlay-coords');
    const autoTeamInput = document.getElementById('nodemc-overlay-auto-team');
    const friendlyTagsInput = document.getElementById('nodemc-overlay-friendly-tags');
    const enemyTagsInput = document.getElementById('nodemc-overlay-enemy-tags');
    const debugInput = document.getElementById('nodemc-overlay-debug');

    if (urlInput) urlInput.value = CONFIG.ADMIN_WS_URL;
    if (reconnectInput) reconnectInput.value = String(CONFIG.RECONNECT_INTERVAL_MS);
    if (dimInput) dimInput.value = CONFIG.TARGET_DIMENSION;
    if (coordsInput) coordsInput.checked = CONFIG.SHOW_COORDS;
    if (autoTeamInput) autoTeamInput.checked = CONFIG.AUTO_TEAM_FROM_NAME;
    if (friendlyTagsInput) friendlyTagsInput.value = CONFIG.FRIENDLY_TAGS;
    if (enemyTagsInput) enemyTagsInput.value = CONFIG.ENEMY_TAGS;
    if (debugInput) debugInput.checked = CONFIG.DEBUG;
  }

  function applyFormToConfig() {
    const urlInput = document.getElementById('nodemc-overlay-url');
    const reconnectInput = document.getElementById('nodemc-overlay-reconnect');
    const dimInput = document.getElementById('nodemc-overlay-dim');
    const coordsInput = document.getElementById('nodemc-overlay-coords');
    const autoTeamInput = document.getElementById('nodemc-overlay-auto-team');
    const friendlyTagsInput = document.getElementById('nodemc-overlay-friendly-tags');
    const enemyTagsInput = document.getElementById('nodemc-overlay-enemy-tags');
    const debugInput = document.getElementById('nodemc-overlay-debug');

    const next = sanitizeConfig({
      ADMIN_WS_URL: urlInput ? urlInput.value : CONFIG.ADMIN_WS_URL,
      RECONNECT_INTERVAL_MS: reconnectInput ? reconnectInput.value : CONFIG.RECONNECT_INTERVAL_MS,
      TARGET_DIMENSION: dimInput ? dimInput.value : CONFIG.TARGET_DIMENSION,
      SHOW_COORDS: coordsInput ? coordsInput.checked : CONFIG.SHOW_COORDS,
      AUTO_TEAM_FROM_NAME: autoTeamInput ? autoTeamInput.checked : CONFIG.AUTO_TEAM_FROM_NAME,
      FRIENDLY_TAGS: friendlyTagsInput ? friendlyTagsInput.value : CONFIG.FRIENDLY_TAGS,
      ENEMY_TAGS: enemyTagsInput ? enemyTagsInput.value : CONFIG.ENEMY_TAGS,
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
    const color = normalizeColor(colorInput ? colorInput.value : TEAM_DEFAULT_COLORS[team], TEAM_DEFAULT_COLORS[team]);
    const label = labelInput ? String(labelInput.value || '').trim() : '';

    const ok = sendAdminCommand({
      type: 'command_player_mark_set',
      playerId,
      team,
      color,
      label,
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
    style.textContent = `
      #nodemc-overlay-fab {
        position: fixed;
        right: 18px;
        bottom: 96px;
        width: 34px;
        height: 34px;
        border-radius: 999px;
        border: 1px solid rgba(255,255,255,.35);
        background: radial-gradient(circle at 30% 30%, #60a5fa, #1d4ed8 70%);
        color: #fff;
        font-size: 15px;
        line-height: 34px;
        text-align: center;
        cursor: pointer;
        z-index: 2147483000;
        box-shadow: 0 8px 18px rgba(0,0,0,.35);
        user-select: none;
        touch-action: none;
      }
      #nodemc-overlay-panel {
        position: fixed;
        right: 18px;
        bottom: 160px;
        width: 320px;
        background: rgba(15, 23, 42, .97);
        border: 1px solid rgba(148, 163, 184, .4);
        border-radius: 12px;
        color: #e2e8f0;
        z-index: 2147483000;
        box-shadow: 0 12px 28px rgba(0,0,0,.45);
        padding: 12px;
        font-size: 12px;
        display: none;
      }
      #nodemc-overlay-panel .n-title {
        font-weight: 700;
        margin-bottom: 8px;
        cursor: move;
        user-select: none;
      }
      #nodemc-overlay-panel .n-row {
        margin-bottom: 8px;
      }
      #nodemc-overlay-panel label {
        display: block;
        margin-bottom: 4px;
        color: #bfdbfe;
      }
      #nodemc-overlay-panel input[type="text"],
      #nodemc-overlay-panel input[type="number"] {
        width: 100%;
        box-sizing: border-box;
        border-radius: 8px;
        border: 1px solid rgba(148,163,184,.45);
        background: rgba(30,41,59,.9);
        color: #e2e8f0;
        padding: 7px 8px;
      }
      #nodemc-overlay-panel .n-check {
        display: flex;
        gap: 8px;
        align-items: center;
        margin-bottom: 6px;
      }
      #nodemc-overlay-panel .n-btns {
        display: flex;
        gap: 8px;
        margin-top: 10px;
        flex-wrap: wrap;
      }
      #nodemc-overlay-panel button {
        border: 1px solid rgba(147,197,253,.45);
        background: rgba(30,64,175,.9);
        color: #fff;
        border-radius: 8px;
        padding: 6px 10px;
        cursor: pointer;
      }
      #nodemc-overlay-status {
        margin-top: 8px;
        color: #93c5fd;
        word-break: break-word;
      }
      #nodemc-overlay-panel .n-subtitle {
        margin-top: 10px;
        margin-bottom: 6px;
        font-weight: 700;
        color: #bfdbfe;
      }
      #nodemc-overlay-panel select {
        width: 100%;
        box-sizing: border-box;
        border-radius: 8px;
        border: 1px solid rgba(148,163,184,.45);
        background: rgba(30,41,59,.9);
        color: #e2e8f0;
        padding: 7px 8px;
      }
    `;
    document.head.appendChild(style);

    const fab = document.createElement('div');
    fab.id = 'nodemc-overlay-fab';
    fab.textContent = '⚙';
    fab.title = 'NodeMC Overlay 设置';

    const panel = document.createElement('div');
    panel.id = 'nodemc-overlay-panel';
    panel.innerHTML = `
      <div class="n-title" id="nodemc-overlay-title">NodeMC Overlay 设置（可拖动）</div>
      <div class="n-row">
        <label>Admin WS URL</label>
        <input id="nodemc-overlay-url" type="text" />
      </div>
      <div class="n-row">
        <label>重连间隔(ms)</label>
        <input id="nodemc-overlay-reconnect" type="number" min="200" max="60000" step="100" />
      </div>
      <div class="n-row">
        <label>维度过滤</label>
        <input id="nodemc-overlay-dim" type="text" placeholder="minecraft:overworld" />
      </div>
      <label class="n-check"><input id="nodemc-overlay-coords" type="checkbox" />显示坐标</label>
      <label class="n-check"><input id="nodemc-overlay-auto-team" type="checkbox" />按名字标签自动判定友敌</label>
      <div class="n-row">
        <label>友军标签（逗号分隔）</label>
        <input id="nodemc-overlay-friendly-tags" type="text" placeholder="[xxx],[队友]" />
      </div>
      <div class="n-row">
        <label>敌军标签（逗号分隔）</label>
        <input id="nodemc-overlay-enemy-tags" type="text" placeholder="[yyy],[红队]" />
      </div>
      <label class="n-check"><input id="nodemc-overlay-server-filter" type="checkbox" />同服隔离广播（服务端）</label>
      <label class="n-check"><input id="nodemc-overlay-debug" type="checkbox" />调试日志</label>
      <div class="n-btns">
        <button id="nodemc-overlay-save" type="button">保存</button>
        <button id="nodemc-overlay-reset" type="button">重置</button>
        <button id="nodemc-overlay-refresh" type="button">立即重连</button>
      </div>
      <div class="n-subtitle">战略指挥：玩家标记</div>
      <div class="n-row">
        <label>在线玩家列表（推荐）</label>
        <select id="nodemc-mark-player-select">
          <option value="">暂无在线玩家</option>
        </select>
      </div>
      <div class="n-row">
        <label>阵营</label>
        <select id="nodemc-mark-team">
          <option value="friendly">友军</option>
          <option value="enemy">敌军</option>
          <option value="neutral" selected>中立</option>
        </select>
      </div>
      <div class="n-row">
        <label>颜色(#RRGGBB)</label>
        <input id="nodemc-mark-color" type="text" placeholder="#ef4444" />
      </div>
      <div class="n-row">
        <label>标签(可选)</label>
        <input id="nodemc-mark-label" type="text" placeholder="例如：突击组/重点观察" />
      </div>
      <div class="n-btns">
        <button id="nodemc-mark-apply" type="button">应用标记</button>
        <button id="nodemc-mark-clear" type="button">清除该玩家</button>
        <button id="nodemc-mark-clear-all" type="button">清空全部标记</button>
      </div>
      <div id="nodemc-overlay-status"></div>
    `;

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

    fab.addEventListener('click', () => {
      if (dragMoved) return;
      setPanelVisible(!panelVisible);
      if (panelVisible) {
        updatePanelPositionNearFab();
      }
    });

    document.getElementById('nodemc-overlay-save')?.addEventListener('click', () => {
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
        colorInput.value = TEAM_DEFAULT_COLORS[team];
      });
      colorInput.value = TEAM_DEFAULT_COLORS[normalizeTeam(teamInput.value)];
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
      updateUiStatus();

      if (CONFIG.DEBUG) {
        console.debug('[NodeMC Player Overlay] ws connected', { url: CONFIG.ADMIN_WS_URL });
      }
    };

    ws.onmessage = (event) => {
      if (typeof event?.data !== 'string') return;
      try {
        const snapshot = JSON.parse(event.data);
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

        latestSnapshot = snapshot;
        sameServerFilterEnabled = Boolean(snapshot?.tabState?.enabled);
        const serverFilterInput = document.getElementById('nodemc-overlay-server-filter');
        if (serverFilterInput) {
          serverFilterInput.checked = sameServerFilterEnabled;
        }
        latestPlayerMarks = snapshot && typeof snapshot.playerMarks === 'object' && snapshot.playerMarks
          ? snapshot.playerMarks
          : {};
        refreshPlayerSelector();
        lastRevision = snapshot?.revision;
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
    style.textContent = `
      .nodemc-projection-label {
        background: rgba(0, 0, 0, 0.78);
        color: #fff;
        border: 1px solid rgba(255, 255, 255, 0.22);
        border-radius: 6px;
        padding: 3px 7px;
        font-size: 12px;
        line-height: 1.2;
        white-space: nowrap;
      }
      .nodemc-player-label {
        background: rgba(15, 23, 42, 0.88);
        color: #dbeafe;
        border: 1px solid rgba(147, 197, 253, 0.5);
        border-radius: 6px;
        padding: 3px 7px;
        font-size: 12px;
        line-height: 1.2;
        white-space: nowrap;
      }
      .nodemc-player-label .n-team {
        font-weight: 700;
      }
    `;
    document.head.appendChild(style);
  }

  function initOverlay() {
    ensureOverlayStyles();
    applyLatestSnapshotIfPossible();
  }

  function boot() {
    loadConfigFromStorage();
    CONFIG.ADMIN_WS_URL = normalizeWsUrl(CONFIG.ADMIN_WS_URL);
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
      }
      PAGE.requestAnimationFrame(tryStart);
    };

    tryStart();
  }

  boot();
})();
