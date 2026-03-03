import {
  getPlayerDataNode,
  normalizeColor,
  normalizeDimension,
  normalizeMarkSource,
  normalizeTeam,
  readNumber,
} from '../utils/overlayUtils';

type MapProjectionDeps = {
  page: Window;
  config: Record<string, any>;
  overlayStyleText: string;
  getPlayerMark: (playerId: string) => any;
  getTabPlayerInfo: (playerId: string) => any;
  getTabPlayerName: (playerId: string) => string | null;
  autoTeamFromName: (nameText: string) => any;
  getConfiguredTeamColor: (team: string) => string;
  maybeSyncAutoDetectedMarks: (candidates: any[]) => void;
  getLatestPlayerMarks: () => Record<string, any>;
  getWsConnected: () => boolean;
  onCreateTacticalWaypoint?: (payload: {
    x: number;
    z: number;
    label: string;
    tacticalType: string;
    color: string;
    ttlSeconds: number | null;
    permanent: boolean;
  }) => boolean;
  onRevisionChanged?: (revision: number | null) => void;
};

export function createMapProjection(deps: MapProjectionDeps) {
  const PAGE = deps.page;
  const CONFIG = deps.config;

  let leafletRef: any = null;
  let capturedMap: any = null;
  let lastGlobalMapScanAt = 0;
  let guardedMapContainer: HTMLElement | null = null;
  let hoverPopupBlockedContainer: HTMLElement | null = null;
  let tacticalMenuEl: HTMLElement | null = null;
  let tacticalMenuOutsideClickHandler: ((event: MouseEvent) => void) | null = null;
  let tacticalMenuEscHandler: ((event: KeyboardEvent) => void) | null = null;
  const markersById = new Map<string, any>();
  const waypointsById = new Map<string, any>();
  const reporterEffectsById = new Map<string, { vision: any | null; chunkArea: any | null }>();

  const MAP_HOVER_BLOCK_CLASS = 'nodemc-map-hover-popup-blocked';
  const MAP_HOVER_BLOCK_STYLE = `
.${MAP_HOVER_BLOCK_CLASS} .leaflet-tooltip-pane,
.${MAP_HOVER_BLOCK_CLASS} .leaflet-tooltip,
.${MAP_HOVER_BLOCK_CLASS} .leaflet-popup-pane,
.${MAP_HOVER_BLOCK_CLASS} .leaflet-popup {
  display: none !important;
}
`;

  const guardedMouseEvents: Array<keyof HTMLElementEventMap> = ['click', 'dblclick', 'auxclick', 'contextmenu'];

  function escapeHtml(raw: unknown) {
    return String(raw || '').replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch] as string));
  }

  function latLngToWorld(map: any, latLng: any) {
    const scale = Number.isFinite(map?.options?.scale) ? map.options.scale : 1;
    const safeScale = scale || 1;
    const x = Number(latLng?.lng) / safeScale;
    const z = -Number(latLng?.lat) / safeScale;
    if (!Number.isFinite(x) || !Number.isFinite(z)) {
      return null;
    }
    return { x, z };
  }

  function shouldEnableTacticalMapMarking() {
    return Boolean(CONFIG.ENABLE_TACTICAL_MAP_MARKING);
  }

  function getDefaultTacticalTtlSeconds() {
    const raw = Number(CONFIG.TACTICAL_MARK_DEFAULT_TTL_SECONDS);
    if (!Number.isFinite(raw)) return 180;
    return Math.max(10, Math.min(86400, Math.round(raw)));
  }

  function getTacticalTypeOptions() {
    return [
      { value: 'attack', name: '进攻', label: '⚔ 进攻此处', color: '#ef4444' },
      { value: 'defend', name: '防守', label: '🛡 防守此处', color: '#3b82f6' },
      { value: 'gather', name: '集结', label: '📣 集结此处', color: '#22c55e' },
      { value: 'scout', name: '侦查', label: '👁 侦查此处', color: '#f59e0b' },
      { value: 'danger', name: '危险', label: '⚠ 危险区域', color: '#f97316' },
    ];
  }

  function closeTacticalMenu() {
    if (tacticalMenuOutsideClickHandler) {
      document.removeEventListener('mousedown', tacticalMenuOutsideClickHandler, true);
      tacticalMenuOutsideClickHandler = null;
    }
    if (tacticalMenuEscHandler) {
      document.removeEventListener('keydown', tacticalMenuEscHandler, true);
      tacticalMenuEscHandler = null;
    }
    if (tacticalMenuEl) {
      try { tacticalMenuEl.remove(); } catch (_) {}
      tacticalMenuEl = null;
    }
  }

  function resolveTtlFromMenuValue(rawValue: string, customRawValue: string) {
    const value = String(rawValue || '').trim().toLowerCase();
    if (value === 'long') {
      return { ttlSeconds: null, permanent: true };
    }
    if (value === 'default') {
      return { ttlSeconds: getDefaultTacticalTtlSeconds(), permanent: false };
    }
    if (value === 'custom') {
      const customNum = Number(customRawValue);
      if (!Number.isFinite(customNum)) return null;
      return {
        ttlSeconds: Math.max(10, Math.min(86400, Math.round(customNum))),
        permanent: false,
      };
    }
    const ttlNum = Number(value);
    if (!Number.isFinite(ttlNum)) return null;
    return {
      ttlSeconds: Math.max(10, Math.min(86400, Math.round(ttlNum))),
      permanent: false,
    };
  }

  function openTacticalMenuAtPointer(
    map: any,
    event: MouseEvent,
    worldPos: { x: number; z: number }
  ) {
    closeTacticalMenu();

    const typeOptions = getTacticalTypeOptions();
    const defaultTtl = getDefaultTacticalTtlSeconds();

    const menu = document.createElement('div');
    menu.className = 'nodemc-tactical-menu';
    menu.innerHTML = `
      <div class="nmc-tactical-title">战术标记</div>
      <label class="nmc-tactical-row">
        <span>标注类型</span>
        <select class="nmc-tactical-type"></select>
      </label>
      <label class="nmc-tactical-row">
        <span>过期时间</span>
        <select class="nmc-tactical-ttl">
          <option value="default">默认（${defaultTtl}s）</option>
          <option value="60">1 分钟</option>
          <option value="180">3 分钟</option>
          <option value="600">10 分钟</option>
          <option value="1800">30 分钟</option>
          <option value="3600">1 小时</option>
          <option value="long">长期有效</option>
          <option value="custom">自定义秒数</option>
        </select>
      </label>
      <label class="nmc-tactical-row nmc-tactical-custom-row" style="display:none;">
        <span>自定义秒数</span>
        <input class="nmc-tactical-custom-ttl" type="number" min="10" max="86400" step="10" value="${defaultTtl}" />
      </label>
      <div class="nmc-tactical-actions">
        <button type="button" class="nmc-tactical-confirm">确认</button>
        <button type="button" class="nmc-tactical-cancel">取消</button>
      </div>
    `;

    const typeSelect = menu.querySelector('.nmc-tactical-type') as HTMLSelectElement | null;
    const ttlSelect = menu.querySelector('.nmc-tactical-ttl') as HTMLSelectElement | null;
    const customRow = menu.querySelector('.nmc-tactical-custom-row') as HTMLElement | null;
    const customInput = menu.querySelector('.nmc-tactical-custom-ttl') as HTMLInputElement | null;
    const confirmBtn = menu.querySelector('.nmc-tactical-confirm') as HTMLButtonElement | null;
    const cancelBtn = menu.querySelector('.nmc-tactical-cancel') as HTMLButtonElement | null;
    if (!typeSelect || !ttlSelect || !customRow || !customInput || !confirmBtn || !cancelBtn) {
      return false;
    }

    for (const item of typeOptions) {
      const option = document.createElement('option');
      option.value = item.value;
      option.textContent = `${item.name}（${item.label}）`;
      typeSelect.appendChild(option);
    }

    menu.addEventListener('mousedown', (e) => {
      e.stopPropagation();
    });
    menu.addEventListener('click', (e) => {
      e.stopPropagation();
    });
    menu.addEventListener('wheel', (e) => {
      e.stopPropagation();
    });
    menu.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      e.stopPropagation();
    }, true);

    ttlSelect.addEventListener('change', () => {
      customRow.style.display = ttlSelect.value === 'custom' ? 'flex' : 'none';
      if (ttlSelect.value === 'custom') {
        customInput.focus();
      }
    });

    cancelBtn.addEventListener('click', () => {
      closeTacticalMenu();
    });

    confirmBtn.addEventListener('click', () => {
      const selectedType = typeOptions.find((item) => item.value === typeSelect.value) || typeOptions[0];
      const ttl = resolveTtlFromMenuValue(ttlSelect.value, customInput.value);
      if (!ttl) {
        customInput.focus();
        return;
      }

      if (typeof deps.onCreateTacticalWaypoint === 'function') {
        deps.onCreateTacticalWaypoint({
          x: worldPos.x,
          z: worldPos.z,
          label: selectedType.label,
          tacticalType: selectedType.value,
          color: selectedType.color,
          ttlSeconds: ttl.ttlSeconds,
          permanent: ttl.permanent,
        });
      }
      closeTacticalMenu();
    });

    document.body.appendChild(menu);
    tacticalMenuEl = menu;

    const margin = 12;
    const menuRect = menu.getBoundingClientRect();
    let left = event.clientX + 14;
    let top = event.clientY + 12;

    const maxLeft = Math.max(margin, window.innerWidth - menuRect.width - margin);
    const maxTop = Math.max(margin, window.innerHeight - menuRect.height - margin);
    left = Math.max(margin, Math.min(maxLeft, left));
    top = Math.max(margin, Math.min(maxTop, top));

    menu.style.left = `${Math.round(left)}px`;
    menu.style.top = `${Math.round(top)}px`;

    tacticalMenuOutsideClickHandler = (e: MouseEvent) => {
      const target = e.target;
      if (tacticalMenuEl && target instanceof Node && tacticalMenuEl.contains(target)) {
        return;
      }
      closeTacticalMenu();
    };
    tacticalMenuEscHandler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        closeTacticalMenu();
      }
    };

    setTimeout(() => {
      if (tacticalMenuOutsideClickHandler) {
        document.addEventListener('mousedown', tacticalMenuOutsideClickHandler, true);
      }
      if (tacticalMenuEscHandler) {
        document.addEventListener('keydown', tacticalMenuEscHandler, true);
      }
    }, 0);

    return true;
  }

  function maybeHandleTacticalMarkPlacement(event: MouseEvent) {
    if (!shouldBlockMapLeftRightClick()) return false;
    if (!shouldEnableTacticalMapMarking()) return false;
    if (event.type !== 'contextmenu' || event.button !== 2) return false;
    if (typeof deps.onCreateTacticalWaypoint !== 'function') return false;

    const map = capturedMap || findMapByDom();
    if (!map || !leafletRef || !map._loaded) return false;
    const latLng = typeof map.mouseEventToLatLng === 'function' ? map.mouseEventToLatLng(event) : null;
    if (!latLng) return false;
    const pos = latLngToWorld(map, latLng);
    if (!pos) return false;

    return openTacticalMenuAtPointer(map, event, pos);
  }

  function shouldBlockMapLeftRightClick() {
    return Boolean(CONFIG.BLOCK_MAP_LEFT_RIGHT_CLICK);
  }

  function shouldBlockMapHoverPopup() {
    return Boolean(CONFIG.BLOCK_MAP_HOVER_POPUP);
  }

  function shouldInterceptMouseEvent(event: MouseEvent) {
    if (!shouldBlockMapLeftRightClick()) return false;
    if (event.type === 'contextmenu') return true;
    if (event.type === 'click' || event.type === 'dblclick') {
      return event.button === 0 || event.button === 2;
    }
    if (event.type === 'auxclick') {
      return event.button === 2;
    }
    return false;
  }

  function onGuardedMouseEvent(event: Event) {
    const mouseEvent = event as MouseEvent;
    if (!shouldInterceptMouseEvent(mouseEvent)) return;
    maybeHandleTacticalMarkPlacement(mouseEvent);
    mouseEvent.preventDefault();
    mouseEvent.stopPropagation();
    if (typeof mouseEvent.stopImmediatePropagation === 'function') {
      mouseEvent.stopImmediatePropagation();
    }
  }

  function detachMapInteractionGuard() {
    closeTacticalMenu();
    if (!guardedMapContainer) return;
    for (const eventName of guardedMouseEvents) {
      guardedMapContainer.removeEventListener(eventName, onGuardedMouseEvent, true);
    }
    guardedMapContainer = null;
  }

  function ensureMapInteractionGuard() {
    const map = capturedMap || findMapByDom();
    const container = map && map._container instanceof HTMLElement ? map._container : null;
    if (!container || !container.isConnected) {
      detachMapInteractionGuard();
      detachMapHoverPopupBlock();
      return;
    }

    if (!shouldBlockMapLeftRightClick()) {
      detachMapInteractionGuard();
    } else if (guardedMapContainer !== container) {
      detachMapInteractionGuard();
      guardedMapContainer = container;
      for (const eventName of guardedMouseEvents) {
        guardedMapContainer.addEventListener(eventName, onGuardedMouseEvent, true);
      }
    }

    if (!shouldBlockMapHoverPopup()) {
      detachMapHoverPopupBlock();
      return;
    }

    if (hoverPopupBlockedContainer === container) {
      return;
    }

    detachMapHoverPopupBlock();
    hoverPopupBlockedContainer = container;
    hoverPopupBlockedContainer.classList.add(MAP_HOVER_BLOCK_CLASS);
  }

  function detachMapHoverPopupBlock() {
    if (!hoverPopupBlockedContainer) return;
    hoverPopupBlockedContainer.classList.remove(MAP_HOVER_BLOCK_CLASS);
    hoverPopupBlockedContainer = null;
  }

  function ensureMapHoverPopupStyles() {
    const style = document.getElementById('nodemc-map-hover-popup-style') as HTMLStyleElement | null;
    if (style) {
      if (style.textContent !== MAP_HOVER_BLOCK_STYLE) {
        style.textContent = MAP_HOVER_BLOCK_STYLE;
      }
      return;
    }
    const blockStyle = document.createElement('style');
    blockStyle.id = 'nodemc-map-hover-popup-style';
    blockStyle.textContent = MAP_HOVER_BLOCK_STYLE;
    document.head.appendChild(blockStyle);
  }

  function isLeafletMapCandidate(value: any) {
    return Boolean(
      value
      && typeof value === 'object'
      && typeof value.setView === 'function'
      && typeof value.getCenter === 'function'
      && typeof value.getZoom === 'function'
      && value._container
    );
  }

  function captureMap(value: any) {
    if (!isLeafletMapCandidate(value)) return null;
    capturedMap = value;
    return value;
  }

  function patchLeaflet(leafletObj: any) {
    if (!leafletObj || !leafletObj.Map || leafletObj.__nodemcProjectionPatched) {
      return;
    }
    leafletObj.__nodemcProjectionPatched = true;
    leafletRef = leafletObj;

    const originalInitialize = leafletObj.Map.prototype.initialize;
    leafletObj.Map.prototype.initialize = function (...args: any[]) {
      captureMap(this);
      return originalInitialize.apply(this, args);
    };

    const originalSetView = leafletObj.Map.prototype.setView;
    if (typeof originalSetView === 'function') {
      leafletObj.Map.prototype.setView = function (...args: any[]) {
        captureMap(this);
        return originalSetView.apply(this, args);
      };
    }

    const originalPanTo = leafletObj.Map.prototype.panTo;
    if (typeof originalPanTo === 'function') {
      leafletObj.Map.prototype.panTo = function (...args: any[]) {
        captureMap(this);
        return originalPanTo.apply(this, args);
      };
    }

    const originalFitBounds = leafletObj.Map.prototype.fitBounds;
    if (typeof originalFitBounds === 'function') {
      leafletObj.Map.prototype.fitBounds = function (...args: any[]) {
        captureMap(this);
        return originalFitBounds.apply(this, args);
      };
    }
  }

  function installLeafletHook() {
    let _L = (PAGE as any).L;

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

    const mapContainer = document.querySelector('#map.leaflet-container, #map .leaflet-container, .leaflet-container') as Record<string, any> | null;
    if (!mapContainer) {
      return null;
    }

    if (capturedMap && capturedMap._container === mapContainer) {
      return capturedMap;
    }

    for (const key of Object.keys(mapContainer)) {
      if (!key.startsWith('_leaflet_')) continue;
      const maybeMap = mapContainer[key];
      if (isLeafletMapCandidate(maybeMap)) {
        return captureMap(maybeMap);
      }
    }

    const now = Date.now();
    if (now - lastGlobalMapScanAt < 300) {
      return null;
    }
    lastGlobalMapScanAt = now;

    const globalMap = findMapFromWindowGlobals(mapContainer);
    if (globalMap) {
      return captureMap(globalMap);
    }

    return null;
  }

  function findMapFromWindowGlobals(mapContainer: any) {
    const pageObj = PAGE as Record<string, any>;
    const keys = Object.keys(pageObj);
    for (const key of keys) {
      let value: any;
      try {
        value = pageObj[key];
      } catch (_) {
        continue;
      }

      if (isLeafletMapCandidate(value) && value._container === mapContainer) {
        return value;
      }

      if (!value || typeof value !== 'object') continue;

      const nestedKeys = Object.keys(value);
      for (const nestedKey of nestedKeys) {
        let nested: any;
        try {
          nested = value[nestedKey];
        } catch (_) {
          continue;
        }
        if (isLeafletMapCandidate(nested) && nested._container === mapContainer) {
          return nested;
        }
      }
    }
    return null;
  }

  function ensureOverlayStyles() {
    if (document.getElementById('nodemc-projection-style')) {
      ensureMapHoverPopupStyles();
      return;
    }
    const style = document.createElement('style');
    style.id = 'nodemc-projection-style';
    style.textContent = deps.overlayStyleText;
    document.head.appendChild(style);
    ensureMapHoverPopupStyles();
  }

  function worldToLatLng(map: any, x: number, z: number) {
    const scale = Number.isFinite(map?.options?.scale) ? map.options.scale : 1;
    return leafletRef.latLng(-z * scale, x * scale);
  }

  function getMarkerVisualConfig(markerKind: string) {
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

  function getReporterVisionRadiusBlocks() {
    const radius = readNumber(CONFIG.REPORTER_VISION_RADIUS);
    if (radius === null) return 64;
    return Math.max(8, Math.min(4096, Math.round(radius)));
  }

  function getReporterChunkRadius() {
    const radius = readNumber(CONFIG.REPORTER_CHUNK_RADIUS);
    if (radius === null) return 2;
    return Math.max(0, Math.min(64, Math.round(radius)));
  }

  function getReporterVisionOpacity() {
    const opacity = readNumber(CONFIG.REPORTER_VISION_OPACITY);
    if (opacity === null) return 0.1;
    return Math.max(0.02, Math.min(0.9, opacity));
  }

  function getReporterChunkOpacity() {
    const opacity = readNumber(CONFIG.REPORTER_CHUNK_OPACITY);
    if (opacity === null) return 0.11;
    return Math.max(0.02, Math.min(0.9, opacity));
  }

  function getReporterEffectColor(configColorKey: 'REPORTER_VISION_COLOR' | 'REPORTER_CHUNK_COLOR', fallbackColor: string) {
    const text = String(CONFIG[configColorKey] || '').trim();
    if (!text) return fallbackColor;
    return normalizeColor(text, fallbackColor);
  }

  function getReportingPlayerIds(snapshot: any) {
    const ids = new Set<string>();
    const tabState = snapshot && typeof snapshot === 'object' ? snapshot.tabState : null;
    const reports = tabState && typeof tabState.reports === 'object' ? tabState.reports : null;
    if (!reports) return ids;

    for (const reportKey of Object.keys(reports)) {
      const playerId = String(reportKey || '').trim();
      if (!playerId) continue;
      ids.add(playerId);
    }
    return ids;
  }

  function isReportingPlayer(playerId: string, rawNode: any, playerData: any, reportingPlayerIds: Set<string>) {
    const idCandidates = new Set<string>();
    const pushId = (value: unknown) => {
      const text = String(value || '').trim();
      if (text) idCandidates.add(text);
    };

    pushId(playerId);
    pushId(playerData && (playerData.playerUUID || playerData.uuid || playerData.id));
    pushId(rawNode && (rawNode.playerUUID || rawNode.uuid || rawNode.id));

    for (const maybeId of idCandidates) {
      if (reportingPlayerIds.has(maybeId)) return true;
    }

    if (!rawNode || typeof rawNode !== 'object') return false;
    const submitPlayerId = String((rawNode as any).submitPlayerId || '').trim();
    if (!submitPlayerId) return false;

    if (reportingPlayerIds.has(submitPlayerId)) return true;
    for (const maybeId of idCandidates) {
      if (submitPlayerId === maybeId) return true;
    }
    return false;
  }

  function buildWorldCircleLatLngs(map: any, centerX: number, centerZ: number, radiusBlocks: number, segments = 48) {
    const points: any[] = [];
    const safeSegments = Math.max(16, Math.min(96, Math.round(segments)));
    for (let i = 0; i < safeSegments; i += 1) {
      const rad = (Math.PI * 2 * i) / safeSegments;
      const px = centerX + Math.cos(rad) * radiusBlocks;
      const pz = centerZ + Math.sin(rad) * radiusBlocks;
      points.push(worldToLatLng(map, px, pz));
    }
    return points;
  }

  function buildChunkCircleCellsLatLngs(map: any, centerX: number, centerZ: number, chunkRadius: number) {
    const cx = Math.floor(centerX / 16);
    const cz = Math.floor(centerZ / 16);
    const radiusSq = chunkRadius * chunkRadius;
    const cells: any[] = [];

    for (let dx = -chunkRadius; dx <= chunkRadius; dx += 1) {
      for (let dz = -chunkRadius; dz <= chunkRadius; dz += 1) {
        if ((dx * dx) + (dz * dz) > radiusSq) continue;

        const chunkX = cx + dx;
        const chunkZ = cz + dz;
        const minX = chunkX * 16;
        const maxX = (chunkX + 1) * 16;
        const minZ = chunkZ * 16;
        const maxZ = (chunkZ + 1) * 16;

        cells.push([
          worldToLatLng(map, minX, minZ),
          worldToLatLng(map, maxX, minZ),
          worldToLatLng(map, maxX, maxZ),
          worldToLatLng(map, minX, maxZ),
        ]);
      }
    }

    return cells;
  }

  function buildMarkerHtml(name: string, x: number, z: number, health: number | null, mark: any, townInfo: any, markerKind = 'player', isReporter = false) {
    const team = mark ? normalizeTeam(mark.team) : 'neutral';
    const color = mark ? normalizeColor(mark.color, deps.getConfiguredTeamColor(team)) : deps.getConfiguredTeamColor(team);
    const showIcon = Boolean(CONFIG.SHOW_PLAYER_ICON);
    const showText = markerKind === 'horse' ? Boolean(CONFIG.SHOW_HORSE_TEXT) : Boolean(CONFIG.SHOW_PLAYER_TEXT);
    if (!showIcon && !showText) {
      return '';
    }

    let text = name;
    if (CONFIG.SHOW_COORDS) {
      text += ` (${Math.round(x)}, ${Math.round(z)})`;
    }
    if (Number.isFinite(health) && (health as number) > 0) {
      text += ` ❤${Math.round(health as number)}`;
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
    const useReporterStar = markerKind === 'player' && isReporter && Boolean(CONFIG.REPORTER_STAR_ICON);
    const iconSize = useReporterStar ? Math.max(18, visual.iconSize + 10) : visual.iconSize;

    const iconContent = markerKind === 'horse' ? '🐎' : (useReporterStar ? '★' : '');
    const iconExtraClass = useReporterStar ? ' is-reporter-star' : '';
    const iconVisualStyle = useReporterStar
      ? `background:transparent;border:none;color:${color};box-shadow:none;text-shadow:0 0 8px ${color}99,0 0 2px rgba(15,23,42,.8);`
      : `background:${markerKind === 'horse' ? 'rgba(15,23,42,.92)' : color};box-shadow:0 0 0 2px ${color}55,0 0 0 1px rgba(15,23,42,.95) inset;`;
    const iconHtml = showIcon
      ? `<span class="n-icon ${markerKind === 'horse' ? 'is-horse' : ''}${iconExtraClass}" style="${iconVisualStyle}width:${iconSize}px;height:${iconSize}px;line-height:${iconSize}px;font-size:${Math.max(9, Math.round(iconSize * 0.75))}px;">${iconContent}</span>`
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

  function getMarkerZIndexOffset(markerKind: string) {
    if (markerKind === 'horse') return -1000;
    return 1000;
  }

  function upsertMarker(map: any, playerId: string, payload: any) {
    const existing = markersById.get(playerId);
    const latLng = worldToLatLng(map, payload.x, payload.z);
    const markerKind = payload.kind || 'player';
    const zIndexOffset = getMarkerZIndexOffset(markerKind);
    const html = buildMarkerHtml(payload.name, payload.x, payload.z, payload.health, payload.mark, payload.townInfo, markerKind, Boolean(payload.isReporter));

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
        if (typeof existing.setZIndexOffset === 'function') {
          existing.setZIndexOffset(zIndexOffset);
        }
        existing.setIcon(
          leafletRef.divIcon({
            className: '',
            html,
            iconSize: [0, 0],
            iconAnchor: [0, 0],
          })
        );
        return;
      } catch (_) {
        try { existing.remove(); } catch (_) {}
        try { markersById.delete(playerId); } catch (_) {}
      }
    }

    const marker = leafletRef.marker(latLng, {
      icon: leafletRef.divIcon({
        className: '',
        html,
        iconSize: [0, 0],
        iconAnchor: [0, 0],
      }),
      zIndexOffset,
      interactive: false,
      keyboard: false,
    });

    marker.addTo(map);
    markersById.set(playerId, marker);
  }

  function upsertReporterEffects(map: any, playerId: string, payload: any, isReporter: boolean) {
    const existing = reporterEffectsById.get(playerId) || { vision: null, chunkArea: null };

    if (!isReporter || !payload || typeof payload !== 'object') {
      if (existing.vision) {
        try { existing.vision.remove(); } catch (_) {}
      }
      if (existing.chunkArea) {
        try { existing.chunkArea.remove(); } catch (_) {}
      }
      reporterEffectsById.delete(playerId);
      return;
    }

    const team = payload.mark ? normalizeTeam(payload.mark.team) : 'neutral';
    const color = payload.mark
      ? normalizeColor(payload.mark.color, deps.getConfiguredTeamColor(team))
      : deps.getConfiguredTeamColor(team);

    if (Boolean(CONFIG.REPORTER_VISION_CIRCLE_ENABLED)) {
      const radiusBlocks = getReporterVisionRadiusBlocks();
      const circlePath = buildWorldCircleLatLngs(map, payload.x, payload.z, radiusBlocks);
      const visionColor = getReporterEffectColor('REPORTER_VISION_COLOR', color);
      const visionFillOpacity = getReporterVisionOpacity();
      const visionLineOpacity = Math.max(0.25, Math.min(1, visionFillOpacity + 0.45));
      if (existing.vision) {
        existing.vision.setLatLngs(circlePath);
        existing.vision.setStyle({
          color: visionColor,
          weight: 1.5,
          opacity: visionLineOpacity,
          fillColor: visionColor,
          fillOpacity: visionFillOpacity,
        });
      } else {
        existing.vision = leafletRef.polygon(circlePath, {
          color: visionColor,
          weight: 1.5,
          opacity: visionLineOpacity,
          fillColor: visionColor,
          fillOpacity: visionFillOpacity,
          interactive: false,
          smoothFactor: 0.5,
        }).addTo(map);
      }
    } else if (existing.vision) {
      try { existing.vision.remove(); } catch (_) {}
      existing.vision = null;
    }

    if (Boolean(CONFIG.REPORTER_CHUNK_AREA_ENABLED)) {
      const chunkRadius = getReporterChunkRadius();
      const areaPath = buildChunkCircleCellsLatLngs(map, payload.x, payload.z, chunkRadius);
      const chunkColor = getReporterEffectColor('REPORTER_CHUNK_COLOR', color);
      const chunkFillOpacity = getReporterChunkOpacity();
      const chunkLineOpacity = Math.max(0.2, Math.min(1, chunkFillOpacity + 0.35));
      if (existing.chunkArea) {
        existing.chunkArea.setLatLngs(areaPath);
        existing.chunkArea.setStyle({
          color: chunkColor,
          weight: 0.8,
          opacity: chunkLineOpacity,
          fillColor: chunkColor,
          fillOpacity: chunkFillOpacity,
        });
      } else {
        existing.chunkArea = leafletRef.polygon(areaPath, {
          color: chunkColor,
          weight: 0.8,
          opacity: chunkLineOpacity,
          fillColor: chunkColor,
          fillOpacity: chunkFillOpacity,
          interactive: false,
        }).addTo(map);
      }
    } else if (existing.chunkArea) {
      try { existing.chunkArea.remove(); } catch (_) {}
      existing.chunkArea = null;
    }

    if (existing.vision || existing.chunkArea) {
      reporterEffectsById.set(playerId, existing);
      return;
    }
    reporterEffectsById.delete(playerId);
  }

  function buildWaypointHtml(name: string, x: number, z: number, waypoint: any) {
    let safeName = (name && String(name)) ? String(name) : '标点';
    if (CONFIG.SHOW_COORDS) {
      safeName += ` (${Math.round(x)}, ${Math.round(z)})`;
    }
    if (Number.isFinite(waypoint && waypoint.health) && waypoint.health > 0) {
      safeName += ` ❤${Math.round(waypoint.health)}`;
    }

    const color = normalizeColor(waypoint && waypoint.color, '#f97316');
    const owner = (waypoint && (waypoint.ownerName || waypoint.ownerId)) ? (waypoint.ownerName || waypoint.ownerId) : null;
    const visual = getMarkerVisualConfig('waypoint');
    const showIcon = Boolean(CONFIG.SHOW_WAYPOINT_ICON);
    const showText = Boolean(CONFIG.SHOW_WAYPOINT_TEXT);
    if (!showIcon && !showText) return '';

    const ownerHtml = owner ? `<span class="n-wp-owner" style="font-weight:600;display:inline-block;margin-right:6px;color:${color};">${escapeHtml(String(owner))}</span>` : '';
    const paddingLeft = showIcon ? Math.max(0, Math.round(visual.iconSize / 2) + 6) : 0;
    const textBg = 'background:rgba(255,255,255,0.85);color:#0f172a;padding:4px 6px;border-radius:6px;display:inline-block;';

    const textHtml = showText
      ? `<span class="n-waypoint-label" style="direction:ltr;white-space:nowrap;padding-left:${paddingLeft}px;${textBg};font-size:${visual.textSize}px;">${ownerHtml}${escapeHtml(safeName)}</span>`
      : '';

    const iconHtml = showIcon
      ? `<span class="n-waypoint-icon" style="position:absolute;left:0;top:50%;transform:translate(-50%,-50%);background:${color};width:${visual.iconSize}px;height:${visual.iconSize}px;display:inline-block;border-radius:50%;line-height:${visual.iconSize}px;text-align:center;font-size:${Math.max(10, Math.round(visual.iconSize * 0.7))}px;z-index:2;">📍</span>`
      : '';

    return `<div class="nodemc-waypoint-anchor" style="position:relative;display:inline-block;white-space:nowrap;">${textHtml}${iconHtml}</div>`;
  }

  function upsertWaypoint(map: any, waypointId: string, payload: any) {
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
      } catch (_) {
        try { existing.remove(); } catch (_) {}
        try { waypointsById.delete(waypointId); } catch (_) {}
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

  function removeMissingMarkers(nextIds: Set<string>) {
    for (const [playerId, marker] of markersById.entries()) {
      if (nextIds.has(playerId)) continue;
      marker.remove();
      markersById.delete(playerId);
    }
  }

  function removeMissingWaypoints(nextIds: Set<string>) {
    for (const [wpId, marker] of waypointsById.entries()) {
      if (nextIds.has(wpId)) continue;
      try { marker.remove(); } catch (_) {}
      waypointsById.delete(wpId);
    }
  }

  function removeMissingReporterEffects(nextIds: Set<string>) {
    for (const [playerId, layers] of reporterEffectsById.entries()) {
      if (nextIds.has(playerId)) continue;
      if (layers.vision) {
        try { layers.vision.remove(); } catch (_) {}
      }
      if (layers.chunkArea) {
        try { layers.chunkArea.remove(); } catch (_) {}
      }
      reporterEffectsById.delete(playerId);
    }
  }

  function applySnapshotPlayers(map: any, snapshot: any) {
    const players = snapshot && typeof snapshot === 'object' ? snapshot.players : null;
    if (!players || typeof players !== 'object') {
      removeMissingMarkers(new Set());
      removeMissingReporterEffects(new Set());
      return;
    }

    const wantedDim = normalizeDimension(CONFIG.TARGET_DIMENSION);
    const nextIds = new Set<string>();
    const nextPlayerIds = new Set<string>();
    const autoMarkSyncCandidates: any[] = [];
    const reportingPlayerIds = getReportingPlayerIds(snapshot);

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
      const existingMark = deps.getPlayerMark(String(playerId));
      const tabInfo = deps.getTabPlayerInfo(String(playerId));
      const autoName = deps.getTabPlayerName(String(playerId)) || name;
      const autoMark = deps.autoTeamFromName(autoName);
      const existingMarkSource = existingMark ? normalizeMarkSource(existingMark.source) : 'manual';
      const isLegacyUnknownMark = Boolean(existingMark) && !Boolean(existingMark.hasExplicitSource);
      const legacyLikelyAuto = Boolean(isLegacyUnknownMark && autoMark)
        && normalizeTeam(existingMark.team) === normalizeTeam(autoMark.team);
      const existingActsAsAuto = Boolean(existingMark) && (existingMarkSource === 'auto' || legacyLikelyAuto);
      const isManualMark = Boolean(existingMark) && !existingActsAsAuto;

      if (isManualMark) {
        // best-effort: keep auto cache clean via clear candidate logic
      }

      if (!isManualMark) {
        if (autoMark && (autoMark.team === 'friendly' || autoMark.team === 'enemy')) {
          const desiredTeam = normalizeTeam(autoMark.team);
          const desiredColor = normalizeColor(autoMark.color, deps.getConfiguredTeamColor(desiredTeam));
          const hasSameAutoMark = Boolean(existingMark)
            && existingActsAsAuto
            && normalizeTeam(existingMark.team) === desiredTeam
            && normalizeColor(existingMark.color, deps.getConfiguredTeamColor(desiredTeam)) === desiredColor;

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
      const isReporter = isReportingPlayer(String(playerId), rawNode, data, reportingPlayerIds);

      nextIds.add(String(playerId));
      nextPlayerIds.add(String(playerId));
      upsertMarker(map, String(playerId), { x, z, health, name, mark: effectiveMark, townInfo, isReporter });
      try {
        upsertReporterEffects(map, String(playerId), { x, z, mark: effectiveMark }, isReporter);
      } catch (_) {
      }
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
            color: deps.getConfiguredTeamColor('neutral'),
            label: '',
          },
          townInfo: null,
          kind: 'horse',
        });
      }
    }

    const waypoints = snapshot && typeof snapshot === 'object' ? snapshot.waypoints : null;
    const nextWaypointIds = new Set<string>();
    if (waypoints && typeof waypoints === 'object') {
      for (const [wpId, rawNode] of Object.entries(waypoints)) {
        if (!rawNode) continue;
        const data = (rawNode as any).data && typeof (rawNode as any).data === 'object' ? (rawNode as any).data : rawNode;
        if (!data) continue;

        const dim = normalizeDimension((data as any).dimension);
        if (wantedDim && dim !== wantedDim) continue;

        const x = readNumber((data as any).x);
        const z = readNumber((data as any).z);
        if (x === null || z === null) continue;

        nextWaypointIds.add(String(wpId));
        upsertWaypoint(map, String(wpId), {
          x,
          z,
          label: (data as any).label || (data as any).name || (data as any).title || String(wpId),
          color: (data as any).color || ((data as any).colorHex ? (data as any).colorHex : null) || null,
          kind: (data as any).waypointKind || null,
          ownerName: (data as any).ownerName || null,
          ownerId: (data as any).ownerId || null,
        });
      }
    }

    removeMissingMarkers(nextIds);
    removeMissingWaypoints(nextWaypointIds);
    removeMissingReporterEffects(nextPlayerIds);
    deps.maybeSyncAutoDetectedMarks(autoMarkSyncCandidates);

    (PAGE as any).__NODEMC_PLAYER_OVERLAY__ = {
      revision: snapshot.revision,
      playersOnMap: markersById.size,
      waypointsOnMap: waypointsById.size,
      source: CONFIG.ADMIN_WS_URL,
      dimension: CONFIG.TARGET_DIMENSION,
      wsConnected: deps.getWsConnected(),
      playerMarks: deps.getLatestPlayerMarks(),
    };

    deps.onRevisionChanged?.(snapshot.revision ?? null);
  }

  function isMapReady() {
    const map = capturedMap || findMapByDom();
    ensureMapInteractionGuard();
    if (!map || !leafletRef || !map._loaded) {
      return false;
    }
    if (map._container && map._container.isConnected === false) {
      if (capturedMap === map) {
        capturedMap = null;
      }
      return false;
    }
    return true;
  }

  function applyLatestSnapshotIfPossible(snapshot: any) {
    if (!snapshot) return false;
    ensureOverlayStyles();
    const map = capturedMap || findMapByDom();
    ensureMapInteractionGuard();
    if (!map || !leafletRef || !map._loaded) return false;
    applySnapshotPlayers(map, snapshot);
    return true;
  }

  function focusOnWorldPosition(x: number, z: number) {
    const map = capturedMap || findMapByDom();
    if (!map || !leafletRef || !map._loaded) return false;
    if (!Number.isFinite(x) || !Number.isFinite(z)) return false;

    const target = worldToLatLng(map, x, z);
    try {
      if (typeof map.panTo === 'function') {
        map.panTo(target, { animate: true, duration: 0.35 });
      } else if (typeof map.setView === 'function') {
        const zoom = typeof map.getZoom === 'function' ? map.getZoom() : undefined;
        map.setView(target, zoom, { animate: true, duration: 0.35 });
      } else {
        return false;
      }
      return true;
    } catch (_) {
      return false;
    }
  }

  function getCounts() {
    return {
      markers: markersById.size,
      waypoints: waypointsById.size,
    };
  }

  function cleanup() {
    closeTacticalMenu();
    detachMapInteractionGuard();
    detachMapHoverPopupBlock();

    for (const m of markersById.values()) {
      try { m.remove(); } catch (_) {}
    }
    markersById.clear();

    for (const m of waypointsById.values()) {
      try { m.remove(); } catch (_) {}
    }
    waypointsById.clear();

    for (const layers of reporterEffectsById.values()) {
      try { layers.vision?.remove(); } catch (_) {}
      try { layers.chunkArea?.remove(); } catch (_) {}
    }
    reporterEffectsById.clear();

    try {
      const blockStyle = document.getElementById('nodemc-map-hover-popup-style');
      if (blockStyle) blockStyle.remove();
    } catch (_) {}
  }

  return {
    ensureOverlayStyles,
    installLeafletHook,
    findMapByDom,
    ensureMapInteractionGuard,
    isMapReady,
    applyLatestSnapshotIfPossible,
    focusOnWorldPosition,
    getCounts,
    cleanup,
  };
}
