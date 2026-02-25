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

  const CONFIG = {
    SNAPSHOT_URL: 'http://127.0.0.1:8765/snapshot',
    POLL_INTERVAL_MS: 1000,
    REQUEST_TIMEOUT_MS: 5000,
    TARGET_DIMENSION: 'minecraft:overworld',
    SHOW_COORDS: true,
    DEBUG: true,
  };

  let leafletRef = null;
  let capturedMap = null;
  let markersById = new Map();
  let pollingStarted = false;
  let inFlight = false;
  let lastRevision = null;
  let lastErrorText = null;
  let latestSnapshot = null;

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
    } catch (error) {
      const text = String(error && error.message ? error.message : error);
      if (text !== lastErrorText) {
        lastErrorText = text;
        console.warn('[NodeMC Player Overlay] snapshot pull failed:', text, CONFIG.SNAPSHOT_URL);
      }
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
    installLeafletHook();
    startPolling();

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
