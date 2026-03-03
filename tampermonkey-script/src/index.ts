// @ts-nocheck
import {
  DEFAULT_CONFIG,
  STORAGE_KEY,
} from './constants';
import {
  buildExportFileName,
  createConfigExportPayload,
  getConfigCompatVersion,
  parseImportedConfigText,
} from './config/configTransfer';
import { OVERLAY_STYLE_TEXT, UI_STYLE_TEXT } from './ui/styles';
import {
  getConfiguredTeamColor,
  normalizeColor,
  normalizeDimension,
  normalizeMarkSource,
  normalizeRoomCode,
  normalizeTeam,
  normalizeWsUrl,
  parseMcDisplayName,
  parseTagList,
  readNumber,
  sanitizeConfig,
  getPlayerDataNode,
} from './utils/overlayUtils';
import { createAutoMarkSyncManager } from './core/autoMarkSync';
import {
  buildCommandPlayerMarkClear,
  buildCommandPlayerMarkClearAll,
  buildCommandPlayerMarkSet,
  buildCommandSameServerFilterSet,
  buildCommandTacticalWaypointSet,
} from './network/networkSchemas';
import { createAdminWsClient } from './network/wsClient';
import { createMapProjection } from './core/mapProjection';
import { createSettingsUi } from './ui/settingsUi';

declare const unsafeWindow: Window | undefined;

(function () {
  'use strict';

  const PAGE = (typeof unsafeWindow !== 'undefined' && unsafeWindow) ? unsafeWindow : window;
  const CONFIG = { ...DEFAULT_CONFIG };

  let latestSnapshot: Record<string, any> | null = null;
  let latestPlayerMarks: Record<string, any> = {};
  let lastErrorText: string | null = null;
  let wsConnected = false;
  let sameServerFilterEnabled = false;
  let overlayStarted = false;
  let lastAdminMessageType: string | null = null;
  let lastAdminMessageAt = 0;

  let wsClient: ReturnType<typeof createAdminWsClient> | null = null;

  const autoMarkSync = createAutoMarkSyncManager({
    isWsOpen: () => Boolean(wsClient?.isWsOpen()),
    sendAdminCommand: (message) => wsClient ? wsClient.sendCommand(message) : false,
    getConfiguredTeamColor: (team) => getConfiguredTeamColor(team, CONFIG),
  });

  function getPlayerMark(playerId: string) {
    if (!latestPlayerMarks || typeof latestPlayerMarks !== 'object') return null;
    const entry = latestPlayerMarks[playerId];
    if (!entry || typeof entry !== 'object') return null;

    const team = normalizeTeam(entry.team);
    const color = normalizeColor(entry.color, getConfiguredTeamColor(team, CONFIG));
    const label = typeof entry.label === 'string' && entry.label.trim() ? entry.label.trim() : null;
    const sourceRaw = typeof entry.source === 'string' ? entry.source.trim().toLowerCase() : '';
    const source = normalizeMarkSource(sourceRaw);
    return { team, color, label, source };
  }

  function autoTeamFromName(nameText: string) {
    if (!CONFIG.AUTO_TEAM_FROM_NAME) return null;
    const name = String(nameText || '');
    if (!name) return null;

    const friendlyTags = parseTagList(CONFIG.FRIENDLY_TAGS);
    const enemyTags = parseTagList(CONFIG.ENEMY_TAGS);
    if (friendlyTags.some((tag) => name.includes(tag))) {
      return {
        team: 'friendly',
        color: getConfiguredTeamColor('friendly', CONFIG),
        label: '',
      };
    }
    if (enemyTags.some((tag) => name.includes(tag))) {
      return {
        team: 'enemy',
        color: getConfiguredTeamColor('enemy', CONFIG),
        label: '',
      };
    }
    return null;
  }

  function getTabPlayerInfo(playerId: string) {
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

  function getTabPlayerName(playerId: string) {
    const info = getTabPlayerInfo(playerId);
    return info ? info.autoName : null;
  }

  function getOnlinePlayers() {
    const snapshotPlayers = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot.players : null;
    const tabState = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot.tabState : null;
    const reports = tabState && typeof tabState.reports === 'object' ? tabState.reports : null;

    const mergedById = new Map<string, any>();

    const composeDisplayLabel = (rawLabel: string, rawPlayerName: string) => {
      const label = String(rawLabel || '').trim();
      const playerName = String(rawPlayerName || '').trim();
      if (!label) return playerName;
      if (!playerName) return label;
      if (label === playerName) return label;
      if (label.includes(playerName)) return label;
      return `${label} ${playerName}`;
    };

    const labelContainsName = (labelText: string, playerName: string) => {
      const label = String(labelText || '').trim();
      const name = String(playerName || '').trim();
      if (!label || !name) return false;
      return label.includes(name);
    };

    const upsertPlayer = (entry: any) => {
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
        const tabInfo = getTabPlayerInfo(String(playerId));
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

    const players = Array.from(mergedById.values()).map((item: any) => ({
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

  function getMapVisiblePlayersForList() {
    const snapshotPlayers = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot.players : null;
    if (!snapshotPlayers || typeof snapshotPlayers !== 'object') {
      return [];
    }

    const wantedDim = normalizeDimension(CONFIG.TARGET_DIMENSION);
    const teamLabelMap: Record<string, string> = {
      friendly: '友军',
      enemy: '敌军',
      neutral: '中立',
    };

    const rows: Array<{
      playerId: string;
      playerName: string;
      team: string;
      teamColor: string;
      town: string;
      townColor: string;
      health: string;
      armor: string;
      x: number;
      z: number;
    }> = [];

    for (const [playerId, rawNode] of Object.entries(snapshotPlayers)) {
      const data = getPlayerDataNode(rawNode);
      if (!data) continue;

      const dim = normalizeDimension(data.dimension);
      if (wantedDim && dim !== wantedDim) continue;

      const x = readNumber(data.x);
      const z = readNumber(data.z);
      if (x === null || z === null) continue;

      const fallbackName = String(data.playerName || data.playerUUID || playerId || '').trim();
      const autoName = getTabPlayerName(String(playerId)) || fallbackName || String(playerId);
      const tabInfo = getTabPlayerInfo(String(playerId));
      const existingMark = getPlayerMark(String(playerId));
      const autoMark = autoTeamFromName(autoName);
      const existingMarkSource = existingMark ? normalizeMarkSource(existingMark.source) : 'manual';
      const existingActsAsAuto = Boolean(existingMark) && existingMarkSource === 'auto';
      const isManualMark = Boolean(existingMark) && !existingActsAsAuto;
      const effectiveMark = isManualMark
        ? existingMark
        : (autoMark || (existingActsAsAuto ? null : existingMark));

      const team = normalizeTeam(effectiveMark && effectiveMark.team ? effectiveMark.team : 'neutral');
      const teamColor = getConfiguredTeamColor(team, CONFIG);
      const townColor = normalizeColor(tabInfo && tabInfo.teamColor, '#93c5fd');
      const health = readNumber(data.health);
      const armor = readNumber(data.armor);

      rows.push({
        playerId: String(playerId),
        playerName: autoName,
        team: teamLabelMap[team] || teamLabelMap.neutral,
        teamColor,
        town: (tabInfo && String(tabInfo.teamText || '').trim()) || '-',
        townColor,
        health: health === null ? '-' : String(Math.round(health)),
        armor: armor === null ? '-' : String(Math.round(armor)),
        x,
        z,
      });
    }

    rows.sort((a, b) => a.playerName.localeCompare(b.playerName, 'zh-Hans-CN'));
    return rows;
  }

  const mapProjection = createMapProjection({
    page: PAGE,
    config: CONFIG,
    overlayStyleText: OVERLAY_STYLE_TEXT,
    getPlayerMark,
    getTabPlayerInfo,
    getTabPlayerName,
    autoTeamFromName,
    getConfiguredTeamColor: (team) => getConfiguredTeamColor(team, CONFIG),
    maybeSyncAutoDetectedMarks: autoMarkSync.maybeSyncAutoDetectedMarks,
    getLatestPlayerMarks: () => latestPlayerMarks,
    getWsConnected: () => wsConnected,
    onCreateTacticalWaypoint: (payload) => {
      const ok = sendAdminCommand(buildCommandTacticalWaypointSet({
        x: payload.x,
        z: payload.z,
        label: payload.label,
        tacticalType: payload.tacticalType,
        color: payload.color,
        ttlSeconds: payload.ttlSeconds,
        permanent: payload.permanent,
        roomCode: CONFIG.ROOM_CODE,
        dimension: CONFIG.TARGET_DIMENSION,
      }));
      if (ok) {
        lastErrorText = null;
        updateUiStatus();
      }
      return ok;
    },
  });

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

  function downloadTextFile(fileName: string, text: string) {
    try {
      const blob = new Blob([text], { type: 'application/json;charset=utf-8' });
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(objectUrl);
      return true;
    } catch (error) {
      console.warn('[NodeMC Player Overlay] export config failed:', error);
      return false;
    }
  }

  function exportConfig() {
    const payload = createConfigExportPayload(CONFIG);
    const fileName = buildExportFileName();
    const ok = downloadTextFile(fileName, JSON.stringify(payload, null, 2));
    if (!ok) {
      lastErrorText = '配置导出失败，请查看控制台日志';
      updateUiStatus();
      return;
    }
    lastErrorText = null;
    const compat = getConfigCompatVersion();
    settingsUi.updateStatus(`状态: 配置已导出（兼容版本 ${compat}）`);
  }

  function importConfigFromFile() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,application/json';
    input.style.display = 'none';

    const cleanupInput = () => {
      try {
        input.remove();
      } catch (_) {}
    };

    input.addEventListener('change', async () => {
      const file = input.files && input.files[0] ? input.files[0] : null;
      if (!file) {
        cleanupInput();
        return;
      }

      try {
        const text = await file.text();
        const parsed = parseImportedConfigText(text);
        if (!parsed.ok || !parsed.config) {
          lastErrorText = parsed.error || '配置导入失败';
          updateUiStatus();
          return;
        }

        Object.assign(CONFIG, parsed.config);
        saveConfigToStorage();
        settingsUi.fillFormFromConfig(CONFIG, (team) => getConfiguredTeamColor(team, CONFIG));
        wsClient?.reconnect();
        refreshPlayerLists();
        lastErrorText = null;
        updateUiStatus();
      } catch (error) {
        console.warn('[NodeMC Player Overlay] import config failed:', error);
        lastErrorText = '配置导入失败：读取文件异常';
        updateUiStatus();
      } finally {
        cleanupInput();
      }
    });

    document.body.appendChild(input);
    input.click();
  }

  function updateUiStatus() {
    const mapCounts = mapProjection.getCounts();
    const lastErr = lastErrorText ? `错误: ${lastErrorText}` : '正常';
    const wsText = wsConnected ? '已连接' : '未连接';
    const players = mapCounts.markers;
    const serverFilterText = sameServerFilterEnabled ? '同服过滤:开' : '同服过滤:关';
    settingsUi.updateStatus(`状态: ${lastErr} | WS: ${wsText} | 标记: ${players} | ${serverFilterText}`);
  }

  function resolvePlayerIdFromInput() {
    const selectedPlayerId = settingsUi.getSelectedPlayerId();
    if (selectedPlayerId) {
      return { ok: true, playerId: selectedPlayerId };
    }
    return { ok: false, error: '请先从在线玩家列表选择目标玩家' };
  }

  function refreshPlayerLists() {
    settingsUi.refreshPlayerSelector(getOnlinePlayers());
    settingsUi.refreshMapPlayerList(getMapVisiblePlayersForList());
  }

  function focusMapPlayerById(playerId: string) {
    const targetId = String(playerId || '').trim();
    if (!targetId) {
      lastErrorText = '玩家列表目标为空';
      updateUiStatus();
      return;
    }

    const mapPlayers = getMapVisiblePlayersForList();
    const target = mapPlayers.find((item) => item.playerId === targetId);
    if (!target) {
      lastErrorText = '目标玩家当前不在地图显示列表中';
      updateUiStatus();
      return;
    }

    const ok = mapProjection.focusOnWorldPosition(target.x, target.z);
    if (!ok) {
      lastErrorText = '地图尚未就绪，无法定位到该玩家';
      updateUiStatus();
      return;
    }

    lastErrorText = null;
    updateUiStatus();
  }

  function applyFormToConfig() {
    const next = sanitizeConfig(settingsUi.readFormCandidate(CONFIG));
    Object.assign(CONFIG, next);
    saveConfigToStorage();
    mapProjection.ensureMapInteractionGuard();
    updateUiStatus();
  }

  function sendAdminCommand(message: Record<string, unknown>) {
    if (!wsClient) return false;
    const ok = wsClient.sendCommand(message);
    if (!ok) {
      const status = wsClient.getStatus();
      lastErrorText = status.lastErrorText;
    }
    updateUiStatus();
    return ok;
  }

  function applyMarkFormToServer() {
    const markForm = settingsUi.getMarkForm();
    const resolved = resolvePlayerIdFromInput();
    if (!resolved.ok) {
      lastErrorText = resolved.error;
      updateUiStatus();
      return;
    }

    const team = normalizeTeam(markForm.team);
    const color = normalizeColor(markForm.color || getConfiguredTeamColor(team, CONFIG), getConfiguredTeamColor(team, CONFIG));
    const label = markForm.label;

    const ok = sendAdminCommand(buildCommandPlayerMarkSet({
      playerId: resolved.playerId,
      team,
      color,
      label,
      source: 'manual',
    }));
    if (ok) {
      autoMarkSync.clearPlayerCache(resolved.playerId);
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

    const ok = sendAdminCommand(buildCommandPlayerMarkClear(resolved.playerId));
    if (ok) {
      autoMarkSync.clearPlayerCache(resolved.playerId);
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function clearAllMarksOnServer() {
    const ok = sendAdminCommand(buildCommandPlayerMarkClearAll());
    if (ok) {
      autoMarkSync.reset();
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function setSameServerFilter(enabled: boolean) {
    const ok = sendAdminCommand(buildCommandSameServerFilterSet(enabled));
    if (ok) {
      lastErrorText = null;
      updateUiStatus();
    }
  }

  const settingsUi = createSettingsUi({
    page: PAGE,
    uiStyleText: UI_STYLE_TEXT,
    onAutoApply: () => {
      applyFormToConfig();
      refreshPlayerLists();
    },
    onSave: () => {
      applyFormToConfig();
    },
    onSaveAdvanced: () => {
      applyFormToConfig();
      wsClient?.reconnect();
    },
    onSaveDisplay: () => {
      applyFormToConfig();
      refreshPlayerLists();
    },
    onExportConfig: () => {
      exportConfig();
    },
    onImportConfig: () => {
      importConfigFromFile();
    },
    onReset: () => {
      Object.assign(CONFIG, DEFAULT_CONFIG);
      saveConfigToStorage();
      settingsUi.fillFormFromConfig(CONFIG, (team) => getConfiguredTeamColor(team, CONFIG));
      wsClient?.reconnect();
      updateUiStatus();
    },
    onRefresh: () => {
      wsClient?.reconnect();
    },
    onMarkApply: applyMarkFormToServer,
    onMarkClear: clearMarkOnServer,
    onMarkClearAll: clearAllMarksOnServer,
    onServerFilterToggle: setSameServerFilter,
    onTeamChanged: (team) => {
      settingsUi.setMarkColor(getConfiguredTeamColor(normalizeTeam(team), CONFIG));
    },
    onPlayerSelectionChanged: () => {
      lastErrorText = null;
      updateUiStatus();
    },
    onTogglePlayerList: (visible) => {
      settingsUi.setPlayerListVisible(Boolean(visible));
      refreshPlayerLists();
      lastErrorText = null;
      updateUiStatus();
    },
    onFocusMapPlayer: (playerId) => {
      focusMapPlayerById(playerId);
    },
  });

  function installDebugConsoleApi() {
    const debugApi = {
      help() {
        const commands = {
          help: '显示可用命令',
          summary: '查看连接状态/对象数量/最近消息',
          snapshot: '输出最新内存快照',
          markers: '输出当前地图 marker 统计',
          ws: '输出 websocket 状态',
          last: '输出最近一条 ws 消息元信息',
          resync: '手动发送 resync_req 请求全量',
          ping: '手动发送 ping',
        };
        console.table(commands);
        return commands;
      },
      summary() {
        const status = wsClient?.getStatus() || {};
        const mapCounts = mapProjection.getCounts();
        const snapshot = latestSnapshot && typeof latestSnapshot === 'object' ? latestSnapshot : {};
        return {
          wsConnected,
          wsReadyState: status.wsReadyState ?? -1,
          lastErrorText,
          sameServerFilterEnabled,
          playersCount: snapshot.players ? Object.keys(snapshot.players).length : 0,
          entitiesCount: snapshot.entities ? Object.keys(snapshot.entities).length : 0,
          waypointsCount: snapshot.waypoints ? Object.keys(snapshot.waypoints).length : 0,
          markersOnMap: mapCounts.markers,
          waypointsOnMap: mapCounts.waypoints,
          lastAdminMessageType,
          lastAdminMessageAt,
        };
      },
      snapshot() {
        return latestSnapshot;
      },
      markers() {
        return mapProjection.getCounts();
      },
      ws() {
        const status = wsClient?.getStatus() || {};
        return {
          url: CONFIG.ADMIN_WS_URL,
          connected: wsConnected,
          readyState: status.wsReadyState ?? -1,
        };
      },
      last() {
        return {
          type: lastAdminMessageType,
          at: lastAdminMessageAt,
        };
      },
      resync(reason = 'manual_console_debug') {
        wsClient?.requestResync(reason);
        return { requested: true, reason };
      },
      ping() {
        if (!wsClient?.isWsOpen()) {
          return { sent: false, reason: 'ws_not_open' };
        }
        wsClient.sendCommand({ type: 'ping', from: 'console_debug' });
        return { sent: true };
      },
    };

    PAGE.__NODEMC_OVERLAY_DEBUG__ = debugApi;
    PAGE.nodemcDebug = debugApi;
  }

  function cleanupAll() {
    try {
      mapProjection.cleanup();
      settingsUi.cleanup();
      wsClient?.cleanup();
      autoMarkSync.reset();

      try { const s2 = document.getElementById('nodemc-projection-style'); if (s2) s2.remove(); } catch (_) {}

      try { delete PAGE.__NODEMC_OVERLAY_DEBUG__; } catch (_) {}
      try { delete PAGE.__NODEMC_PLAYER_OVERLAY__; } catch (_) {}

      overlayStarted = false;
    } catch (_) {}
  }

  try { window.addEventListener('beforeunload', cleanupAll); } catch (_) {}

  function initOverlay() {
    mapProjection.ensureOverlayStyles();
    mapProjection.ensureMapInteractionGuard();
    mapProjection.applyLatestSnapshotIfPossible(latestSnapshot);
  }

  function boot() {
    installDebugConsoleApi();
    loadConfigFromStorage();
    CONFIG.ADMIN_WS_URL = normalizeWsUrl(CONFIG.ADMIN_WS_URL);
    CONFIG.ROOM_CODE = normalizeRoomCode(CONFIG.ROOM_CODE);

    mapProjection.installLeafletHook();

    wsClient = createAdminWsClient({
      getConfig: () => CONFIG,
      onSnapshotChanged: (snapshot) => {
        latestSnapshot = snapshot;
        sameServerFilterEnabled = Boolean(snapshot?.tabState?.enabled);
        settingsUi.setServerFilterEnabled(sameServerFilterEnabled);
        latestPlayerMarks = snapshot && typeof snapshot.playerMarks === 'object' && snapshot.playerMarks
          ? snapshot.playerMarks
          : {};
        refreshPlayerLists();
        lastErrorText = null;
        mapProjection.applyLatestSnapshotIfPossible(snapshot);
        updateUiStatus();
      },
      onAckMessage: () => {},
      onWsStatusChanged: (status) => {
        wsConnected = status.wsConnected;
        lastErrorText = status.lastErrorText;
        lastAdminMessageType = status.lastAdminMessageType;
        lastAdminMessageAt = status.lastAdminMessageAt;
        updateUiStatus();
      },
    });

    wsClient.connect();

    settingsUi.mountWhenReady();
    const syncUiOnReady = () => {
      if (!settingsUi.isMounted()) {
        PAGE.requestAnimationFrame(syncUiOnReady);
        return;
      }
      settingsUi.fillFormFromConfig(CONFIG, (team) => getConfiguredTeamColor(team, CONFIG));
      refreshPlayerLists();
      updateUiStatus();
    };
    syncUiOnReady();

    if (CONFIG.DEBUG) {
      console.log('[NodeMC Player Overlay] boot', {
        wsUrl: CONFIG.ADMIN_WS_URL,
        reconnectMs: CONFIG.RECONNECT_INTERVAL_MS,
      });
    }

    const tryStart = () => {
      if (mapProjection.isMapReady()) {
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
