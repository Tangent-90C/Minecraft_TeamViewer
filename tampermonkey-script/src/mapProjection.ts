import {
  getPlayerDataNode,
  normalizeColor,
  normalizeDimension,
  normalizeMarkSource,
  normalizeTeam,
  readNumber,
} from './overlayUtils';

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
  onRevisionChanged?: (revision: number | null) => void;
};

export function createMapProjection(deps: MapProjectionDeps) {
  const PAGE = deps.page;
  const CONFIG = deps.config;

  let leafletRef: any = null;
  let capturedMap: any = null;
  const markersById = new Map<string, any>();
  const waypointsById = new Map<string, any>();

  function patchLeaflet(leafletObj: any) {
    if (!leafletObj || !leafletObj.Map || leafletObj.__nodemcProjectionPatched) {
      return;
    }
    leafletObj.__nodemcProjectionPatched = true;
    leafletRef = leafletObj;

    const originalInitialize = leafletObj.Map.prototype.initialize;
    leafletObj.Map.prototype.initialize = function (...args: any[]) {
      capturedMap = this;
      return originalInitialize.apply(this, args);
    };
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

    const mapContainer = document.querySelector('#map.leaflet-container') as Record<string, any> | null;
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

  function ensureOverlayStyles() {
    if (document.getElementById('nodemc-projection-style')) {
      return;
    }
    const style = document.createElement('style');
    style.id = 'nodemc-projection-style';
    style.textContent = deps.overlayStyleText;
    document.head.appendChild(style);
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

  function buildMarkerHtml(name: string, x: number, z: number, health: number | null, mark: any, townInfo: any, markerKind = 'player') {
    const team = mark ? normalizeTeam(mark.team) : 'neutral';
    const color = mark ? normalizeColor(mark.color, deps.getConfiguredTeamColor(team)) : deps.getConfiguredTeamColor(team);
    const showIcon = Boolean(CONFIG.SHOW_PLAYER_ICON);
    const showText = markerKind === 'horse' ? Boolean(CONFIG.SHOW_HORSE_TEXT) : Boolean(CONFIG.SHOW_PLAYER_TEXT);
    if (!showIcon && !showText) {
      return '';
    }

    const escapeHtml = (raw: string) => String(raw).replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch] as string));

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

  function upsertMarker(map: any, playerId: string, payload: any) {
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
      interactive: false,
      keyboard: false,
    });

    marker.addTo(map);
    markersById.set(playerId, marker);
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
    const escapeHtml = (raw: string) => String(raw).replace(/[&<>"']/g, (ch) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[ch] as string));

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

  function applySnapshotPlayers(map: any, snapshot: any) {
    const players = snapshot && typeof snapshot === 'object' ? snapshot.players : null;
    if (!players || typeof players !== 'object') {
      removeMissingMarkers(new Set());
      return;
    }

    const wantedDim = normalizeDimension(CONFIG.TARGET_DIMENSION);
    const nextIds = new Set<string>();
    const autoMarkSyncCandidates: any[] = [];

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

      nextIds.add(String(playerId));
      upsertMarker(map, String(playerId), { x, z, health, name, mark: effectiveMark, townInfo });
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

  function applyLatestSnapshotIfPossible(snapshot: any) {
    if (!snapshot) return;
    ensureOverlayStyles();
    const map = capturedMap || findMapByDom();
    if (!map || !leafletRef || !map._loaded) return;
    applySnapshotPlayers(map, snapshot);
  }

  function getCounts() {
    return {
      markers: markersById.size,
      waypoints: waypointsById.size,
    };
  }

  function cleanup() {
    for (const m of markersById.values()) {
      try { m.remove(); } catch (_) {}
    }
    markersById.clear();

    for (const m of waypointsById.values()) {
      try { m.remove(); } catch (_) {}
    }
    waypointsById.clear();
  }

  return {
    ensureOverlayStyles,
    installLeafletHook,
    findMapByDom,
    applyLatestSnapshotIfPossible,
    getCounts,
    cleanup,
  };
}
