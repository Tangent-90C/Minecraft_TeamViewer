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
    SNAPSHOT_URL: 'http://127.0.0.1:8765/snapshot',
    POLL_INTERVAL_MS: 1000,
    REQUEST_TIMEOUT_MS: 5000,
    TARGET_DIMENSION: 'minecraft:overworld',
    SHOW_COORDS: true,
    DEBUG: true,
  };
  const CONFIG = { ...DEFAULT_CONFIG };

  let leafletRef = null;
  let capturedMap = null;
  let markersById = new Map();
  let pollingStarted = false;
  let inFlight = false;
  let lastRevision = null;
  let lastErrorText = null;
  let latestSnapshot = null;
  let uiMounted = false;
  let panelVisible = false;

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

    if (typeof candidate.SNAPSHOT_URL === 'string' && candidate.SNAPSHOT_URL.trim()) {
      next.SNAPSHOT_URL = candidate.SNAPSHOT_URL.trim();
    }

    const poll = Number(candidate.POLL_INTERVAL_MS);
    if (Number.isFinite(poll)) {
      next.POLL_INTERVAL_MS = Math.max(200, Math.min(60000, Math.round(poll)));
    }

    const timeout = Number(candidate.REQUEST_TIMEOUT_MS);
    if (Number.isFinite(timeout)) {
      next.REQUEST_TIMEOUT_MS = Math.max(500, Math.min(60000, Math.round(timeout)));
    }

    if (typeof candidate.TARGET_DIMENSION === 'string' && candidate.TARGET_DIMENSION.trim()) {
      next.TARGET_DIMENSION = candidate.TARGET_DIMENSION.trim();
    }

    next.SHOW_COORDS = Boolean(candidate.SHOW_COORDS);
    next.DEBUG = Boolean(candidate.DEBUG);
    return next;
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

  function readNumber(value) {
    const num = Number(value);
    return Number.isFinite(num) ? num : null;
  }

  function buildMarkerHtml(name, x, z, health) {
    let text = name;
    if (CONFIG.SHOW_COORDS) {
      text += ` (${Math.round(x)}, ${Math.round(z)})`;
    }
    if (Number.isFinite(health) && health > 0) {
      text += ` ❤${Math.round(health)}`;
    }
    return `<div class="nodemc-player-label">${text}</div>`;
  }

  function upsertMarker(map, playerId, payload) {
    const existing = markersById.get(playerId);
    const latLng = worldToLatLng(map, payload.x, payload.z);
    const html = buildMarkerHtml(payload.name, payload.x, payload.z, payload.health);

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

      nextIds.add(playerId);
      upsertMarker(map, playerId, { x, z, health, name });
    }

    removeMissingMarkers(nextIds);

    PAGE.__NODEMC_PLAYER_OVERLAY__ = {
      revision: snapshot.revision,
      playersOnMap: markersById.size,
      source: CONFIG.SNAPSHOT_URL,
      dimension: CONFIG.TARGET_DIMENSION,
    };
  }

  function updateUiStatus() {
    const status = document.getElementById('nodemc-overlay-status');
    if (!status) return;

    const lastErr = lastErrorText ? `错误: ${lastErrorText}` : '正常';
    const players = markersById.size;
    const revText = lastRevision === null || lastRevision === undefined ? '-' : String(lastRevision);
    status.textContent = `状态: ${lastErr} | 标记: ${players} | Rev: ${revText}`;
  }

  function setPanelVisible(visible) {
    const panel = document.getElementById('nodemc-overlay-panel');
    if (!panel) return;
    panelVisible = Boolean(visible);
    panel.style.display = panelVisible ? 'block' : 'none';
  }

  function fillFormFromConfig() {
    const urlInput = document.getElementById('nodemc-overlay-url');
    const pollInput = document.getElementById('nodemc-overlay-poll');
    const timeoutInput = document.getElementById('nodemc-overlay-timeout');
    const dimInput = document.getElementById('nodemc-overlay-dim');
    const coordsInput = document.getElementById('nodemc-overlay-coords');
    const debugInput = document.getElementById('nodemc-overlay-debug');

    if (urlInput) urlInput.value = CONFIG.SNAPSHOT_URL;
    if (pollInput) pollInput.value = String(CONFIG.POLL_INTERVAL_MS);
    if (timeoutInput) timeoutInput.value = String(CONFIG.REQUEST_TIMEOUT_MS);
    if (dimInput) dimInput.value = CONFIG.TARGET_DIMENSION;
    if (coordsInput) coordsInput.checked = CONFIG.SHOW_COORDS;
    if (debugInput) debugInput.checked = CONFIG.DEBUG;
  }

  function applyFormToConfig() {
    const urlInput = document.getElementById('nodemc-overlay-url');
    const pollInput = document.getElementById('nodemc-overlay-poll');
    const timeoutInput = document.getElementById('nodemc-overlay-timeout');
    const dimInput = document.getElementById('nodemc-overlay-dim');
    const coordsInput = document.getElementById('nodemc-overlay-coords');
    const debugInput = document.getElementById('nodemc-overlay-debug');

    const next = sanitizeConfig({
      SNAPSHOT_URL: urlInput ? urlInput.value : CONFIG.SNAPSHOT_URL,
      POLL_INTERVAL_MS: pollInput ? pollInput.value : CONFIG.POLL_INTERVAL_MS,
      REQUEST_TIMEOUT_MS: timeoutInput ? timeoutInput.value : CONFIG.REQUEST_TIMEOUT_MS,
      TARGET_DIMENSION: dimInput ? dimInput.value : CONFIG.TARGET_DIMENSION,
      SHOW_COORDS: coordsInput ? coordsInput.checked : CONFIG.SHOW_COORDS,
      DEBUG: debugInput ? debugInput.checked : CONFIG.DEBUG,
    });

    Object.assign(CONFIG, next);
    saveConfigToStorage();
    updateUiStatus();
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
        width: 54px;
        height: 54px;
        border-radius: 999px;
        border: 1px solid rgba(255,255,255,.35);
        background: radial-gradient(circle at 30% 30%, #60a5fa, #1d4ed8 70%);
        color: #fff;
        font-size: 23px;
        line-height: 54px;
        text-align: center;
        cursor: pointer;
        z-index: 2147483000;
        box-shadow: 0 8px 18px rgba(0,0,0,.35);
        user-select: none;
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
    `;
    document.head.appendChild(style);

    const fab = document.createElement('div');
    fab.id = 'nodemc-overlay-fab';
    fab.textContent = '⚙';
    fab.title = 'NodeMC Overlay 设置';

    const panel = document.createElement('div');
    panel.id = 'nodemc-overlay-panel';
    panel.innerHTML = `
      <div class="n-title">NodeMC Overlay 设置</div>
      <div class="n-row">
        <label>Snapshot URL</label>
        <input id="nodemc-overlay-url" type="text" />
      </div>
      <div class="n-row">
        <label>轮询间隔(ms)</label>
        <input id="nodemc-overlay-poll" type="number" min="200" max="60000" step="100" />
      </div>
      <div class="n-row">
        <label>请求超时(ms)</label>
        <input id="nodemc-overlay-timeout" type="number" min="500" max="60000" step="100" />
      </div>
      <div class="n-row">
        <label>维度过滤</label>
        <input id="nodemc-overlay-dim" type="text" placeholder="minecraft:overworld" />
      </div>
      <label class="n-check"><input id="nodemc-overlay-coords" type="checkbox" />显示坐标</label>
      <label class="n-check"><input id="nodemc-overlay-debug" type="checkbox" />调试日志</label>
      <div class="n-btns">
        <button id="nodemc-overlay-save" type="button">保存</button>
        <button id="nodemc-overlay-reset" type="button">重置</button>
        <button id="nodemc-overlay-refresh" type="button">立即拉取</button>
      </div>
      <div id="nodemc-overlay-status"></div>
    `;

    document.body.appendChild(fab);
    document.body.appendChild(panel);

    fillFormFromConfig();
    updateUiStatus();

    fab.addEventListener('click', () => setPanelVisible(!panelVisible));

    document.getElementById('nodemc-overlay-save')?.addEventListener('click', async () => {
      applyFormToConfig();
      await pollOnce();
      applyLatestSnapshotIfPossible();
    });

    document.getElementById('nodemc-overlay-reset')?.addEventListener('click', async () => {
      Object.assign(CONFIG, DEFAULT_CONFIG);
      saveConfigToStorage();
      fillFormFromConfig();
      await pollOnce();
      applyLatestSnapshotIfPossible();
      updateUiStatus();
    });

    document.getElementById('nodemc-overlay-refresh')?.addEventListener('click', async () => {
      await pollOnce();
      applyLatestSnapshotIfPossible();
      updateUiStatus();
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

  function requestJson(url) {
    const gmRequest =
      (typeof GM_xmlhttpRequest === 'function' && GM_xmlhttpRequest) ||
      (typeof GM === 'object' && GM && typeof GM.xmlHttpRequest === 'function' && GM.xmlHttpRequest);

    if (typeof gmRequest === 'function') {
      return new Promise((resolve, reject) => {
        gmRequest({
          method: 'GET',
          url,
          headers: { Accept: 'application/json' },
          timeout: CONFIG.REQUEST_TIMEOUT_MS,
          onload: (resp) => {
            if (resp.status < 200 || resp.status >= 300) {
              reject(new Error(`HTTP ${resp.status}`));
              return;
            }
            try {
              resolve(JSON.parse(resp.responseText));
            } catch (error) {
              reject(error);
            }
          },
          onerror: () => reject(new Error('request error')),
          ontimeout: () => reject(new Error('request timeout')),
        });
      });
    }

    if (CONFIG.DEBUG) {
      console.warn('[NodeMC Player Overlay] GM_xmlhttpRequest unavailable, fallback to fetch (may be blocked by mixed-content/CORS).');
    }

    return fetch(url, { cache: 'no-store' }).then((resp) => {
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      return resp.json();
    });
  }

  function applyLatestSnapshotIfPossible() {
    if (!latestSnapshot) return;
    const map = capturedMap || findMapByDom();
    if (!map || !leafletRef || !map._loaded) return;
    applySnapshotPlayers(map, latestSnapshot);
  }

  async function pollOnce() {
    if (inFlight) return;

    inFlight = true;
    try {
      const snapshot = await requestJson(CONFIG.SNAPSHOT_URL);
      latestSnapshot = snapshot;

      const rev = snapshot?.revision;
      if (rev !== null && rev !== undefined && rev === lastRevision) {
        lastErrorText = null;
        applyLatestSnapshotIfPossible();
        return;
      }
      lastRevision = rev;
      applyLatestSnapshotIfPossible();
      lastErrorText = null;

      if (CONFIG.DEBUG) {
        const count = snapshot?.players && typeof snapshot.players === 'object' ? Object.keys(snapshot.players).length : 0;
        console.debug('[NodeMC Player Overlay] snapshot ok', { rev, players: count, url: CONFIG.SNAPSHOT_URL });
      }
      updateUiStatus();
    } catch (error) {
      const text = String(error && error.message ? error.message : error);
      if (text !== lastErrorText) {
        lastErrorText = text;
        console.warn('[NodeMC Player Overlay] snapshot pull failed:', text, CONFIG.SNAPSHOT_URL);
      }
      updateUiStatus();
    } finally {
      inFlight = false;
    }
  }

  function startPolling() {
    if (pollingStarted) return;
    pollingStarted = true;

    const loop = async () => {
      await pollOnce();
      setTimeout(loop, CONFIG.POLL_INTERVAL_MS);
    };

    loop();
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
    `;
    document.head.appendChild(style);
  }

  function initOverlay() {
    ensureOverlayStyles();
    applyLatestSnapshotIfPossible();
  }

  function boot() {
    loadConfigFromStorage();
    installLeafletHook();
    startPolling();
    mountUiWhenReady();

    if (CONFIG.DEBUG) {
      console.log('[NodeMC Player Overlay] boot', {
        url: CONFIG.SNAPSHOT_URL,
        interval: CONFIG.POLL_INTERVAL_MS,
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
