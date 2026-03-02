// @ts-nocheck
import {
  DEFAULT_CONFIG,
  STORAGE_KEY,
} from './constants';
import { PANEL_HTML } from './panelTemplate';
import { OVERLAY_STYLE_TEXT, UI_STYLE_TEXT } from './styles';
import {
  getConfiguredTeamColor,
  normalizeColor,
  normalizeMarkSource,
  normalizeRoomCode,
  normalizeTeam,
  normalizeWsUrl,
  parseMcDisplayName,
  parseTagList,
  sanitizeConfig,
  getPlayerDataNode,
} from './overlayUtils';
import { createAutoMarkSyncManager } from './autoMarkSync';
import { createAdminWsClient } from './wsClient';
import { createMapProjection } from './mapProjection';
import { createSettingsUi } from './settingsUi';

declare const unsafeWindow: Window | undefined;

(function () {
  'use strict';

  const PAGE = (typeof unsafeWindow !== 'undefined' && unsafeWindow) ? unsafeWindow : window;
  const CONFIG = { ...DEFAULT_CONFIG };

  let latestSnapshot: Record<string, any> | null = null;
  let latestPlayerMarks: Record<string, any> = {};
  let lastRevision: number | null = null;
  let lastErrorText: string | null = null;
  let wsConnected = false;
  let sameServerFilterEnabled = false;
  let overlayStarted = false;
  let lastAdminMessageType: string | null = null;
  let lastAdminMessageAt = 0;
  let lastAdminMessageRevision: number | null = null;

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
    const hasExplicitSource = sourceRaw === 'auto' || sourceRaw === 'manual';
    return { team, color, label, source, hasExplicitSource };
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
    onRevisionChanged: (revision) => {
      lastRevision = revision;
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

  function updateUiStatus() {
    const mapCounts = mapProjection.getCounts();
    const lastErr = lastErrorText ? `错误: ${lastErrorText}` : '正常';
    const wsText = wsConnected ? '已连接' : '未连接';
    const players = mapCounts.markers;
    const revText = lastRevision === null || lastRevision === undefined ? '-' : String(lastRevision);
    const serverFilterText = sameServerFilterEnabled ? '同服过滤:开' : '同服过滤:关';
    settingsUi.updateStatus(`状态: ${lastErr} | WS: ${wsText} | 标记: ${players} | ${serverFilterText} | Rev: ${revText}`);
  }

  function resolvePlayerIdFromInput() {
    const selectedPlayerId = settingsUi.getSelectedPlayerId();
    if (selectedPlayerId) {
      return { ok: true, playerId: selectedPlayerId };
    }
    return { ok: false, error: '请先从在线玩家列表选择目标玩家' };
  }

  function refreshPlayerSelector() {
    settingsUi.refreshPlayerSelector(getOnlinePlayers());
  }

  function applyFormToConfig() {
    const next = sanitizeConfig(settingsUi.readFormCandidate(CONFIG));
    Object.assign(CONFIG, next);
    saveConfigToStorage();
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

    const ok = sendAdminCommand({
      type: 'command_player_mark_set',
      playerId: resolved.playerId,
      team,
      color,
      label,
      source: 'manual',
    });
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

    const ok = sendAdminCommand({
      type: 'command_player_mark_clear',
      playerId: resolved.playerId,
    });
    if (ok) {
      autoMarkSync.clearPlayerCache(resolved.playerId);
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function clearAllMarksOnServer() {
    const ok = sendAdminCommand({ type: 'command_player_mark_clear_all' });
    if (ok) {
      autoMarkSync.reset();
      lastErrorText = null;
      updateUiStatus();
    }
  }

  function setSameServerFilter(enabled: boolean) {
    const ok = sendAdminCommand({
      type: 'command_same_server_filter_set',
      enabled: Boolean(enabled),
    });
    if (ok) {
      lastErrorText = null;
      updateUiStatus();
    }
  }

  const settingsUi = createSettingsUi({
    page: PAGE,
    panelHtml: PANEL_HTML,
    uiStyleText: UI_STYLE_TEXT,
    onSave: () => {
      applyFormToConfig();
      wsClient?.reconnect();
    },
    onSaveAdvanced: () => {
      applyFormToConfig();
      wsClient?.reconnect();
    },
    onSaveDisplay: () => {
      applyFormToConfig();
      wsClient?.reconnect();
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
          revision: lastRevision,
          lastErrorText,
          sameServerFilterEnabled,
          playersCount: snapshot.players ? Object.keys(snapshot.players).length : 0,
          entitiesCount: snapshot.entities ? Object.keys(snapshot.entities).length : 0,
          waypointsCount: snapshot.waypoints ? Object.keys(snapshot.waypoints).length : 0,
          markersOnMap: mapCounts.markers,
          waypointsOnMap: mapCounts.waypoints,
          lastAdminMessageType,
          lastAdminMessageAt,
          lastAdminMessageRevision,
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
          revision: lastAdminMessageRevision,
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
        refreshPlayerSelector();
        lastRevision = snapshot?.revision ?? lastRevision;
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
        lastAdminMessageRevision = status.lastAdminMessageRevision;
        lastRevision = status.lastRevision;
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
      refreshPlayerSelector();
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
      const map = mapProjection.findMapByDom();
      if (map) {
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
