import {
  applyScopePatchMap,
  shouldResyncForScopeMissingBaseline,
} from './overlayUtils';
import {
  AdminSnapshot,
  buildAdminHandshake,
  createEmptyAdminSnapshotModel,
} from './networkSchemas';

type Snapshot = AdminSnapshot & Record<string, any>;

type WsClientDeps = {
  getConfig: () => Record<string, any>;
  onSnapshotChanged: (snapshot: Snapshot) => void;
  onAckMessage: (payload: Record<string, any>) => void;
  onWsStatusChanged: (payload: {
    wsConnected: boolean;
    lastErrorText: string | null;
    lastAdminMessageType: string | null;
    lastAdminMessageAt: number;
    lastAdminMessageRevision: number | null;
    lastRevision: number | null;
  }) => void;
};

export function createEmptyAdminSnapshot() {
  return createEmptyAdminSnapshotModel();
}

export function createAdminWsClient(deps: WsClientDeps) {
  let adminWs: WebSocket | null = null;
  let wsConnected = false;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let manualWsClose = false;

  let lastErrorText: string | null = null;
  let lastAdminResyncRequestAt = 0;
  let lastAdminMessageType: string | null = null;
  let lastAdminMessageAt = 0;
  let lastAdminMessageRevision: number | null = null;
  let lastRevision: number | null = null;
  let latestSnapshot: Snapshot = createEmptyAdminSnapshot();

  function emitStatus() {
    deps.onWsStatusChanged({
      wsConnected,
      lastErrorText,
      lastAdminMessageType,
      lastAdminMessageAt,
      lastAdminMessageRevision,
      lastRevision,
    });
  }

  function scheduleReconnect() {
    if (reconnectTimer !== null) return;
    const config = deps.getConfig();
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, config.RECONNECT_INTERVAL_MS);
  }

  function requestResync(reason = 'baseline_missing') {
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
    } catch (_) {}
  }

  function applyAdminDeltaMessage(message: Snapshot) {
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
      lastRevision = latestSnapshot.revision ?? null;
      deps.onSnapshotChanged(latestSnapshot);
      return;
    }

    if (message.type !== 'patch') {
      return;
    }

    if (!latestSnapshot || typeof latestSnapshot !== 'object') {
      requestResync('patch_before_full_snapshot');
      return;
    }

    const needResync =
      shouldResyncForScopeMissingBaseline(latestSnapshot.players, message.players, ['x', 'y', 'z', 'dimension']) ||
      shouldResyncForScopeMissingBaseline(latestSnapshot.entities, message.entities, ['x', 'y', 'z', 'dimension']);
    if (needResync) {
      requestResync('patch_missing_baseline');
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

    lastRevision = latestSnapshot.revision ?? null;
    deps.onSnapshotChanged(latestSnapshot);
  }

  function sendCommand(message: Record<string, unknown>) {
    if (!adminWs || adminWs.readyState !== WebSocket.OPEN) {
      lastErrorText = 'ws not connected';
      emitStatus();
      return false;
    }
    try {
      adminWs.send(JSON.stringify(message));
      return true;
    } catch (error: any) {
      lastErrorText = String(error?.message || error);
      emitStatus();
      return false;
    }
  }

  function cleanup() {
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

  function reconnect() {
    manualWsClose = true;
    cleanup();
    manualWsClose = false;
    lastErrorText = null;
    connect();
    emitStatus();
  }

  function connect() {
    if (adminWs && (adminWs.readyState === WebSocket.OPEN || adminWs.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const config = deps.getConfig();

    let ws: WebSocket;
    try {
      ws = new WebSocket(config.ADMIN_WS_URL);
    } catch (error: any) {
      const text = String(error?.message || error);
      lastErrorText = text;
      emitStatus();
      scheduleReconnect();
      return;
    }

    adminWs = ws;
    ws.onopen = () => {
      wsConnected = true;
      lastErrorText = null;
      try {
        ws.send(JSON.stringify(buildAdminHandshake(config.ROOM_CODE)));
      } catch (error: any) {
        lastErrorText = String(error?.message || error);
      }
      emitStatus();
    };

    ws.onmessage = (event) => {
      if (typeof event?.data !== 'string') return;
      try {
        const payload = JSON.parse(event.data);
        lastAdminMessageType = payload?.type ? String(payload.type) : 'unknown';
        lastAdminMessageAt = Date.now();
        lastAdminMessageRevision = payload?.revision !== undefined
          ? payload.revision
          : (payload?.rev !== undefined ? payload.rev : null);

        if (payload?.type === 'admin_ack') {
          if (payload.ok) {
            lastErrorText = null;
          } else if (payload.error) {
            lastErrorText = `命令失败: ${payload.error}`;
          }
          deps.onAckMessage(payload);
          emitStatus();
          return;
        }

        if (payload?.type === 'pong' || payload?.type === 'handshake_ack') {
          lastErrorText = null;
          emitStatus();
          return;
        }

        applyAdminDeltaMessage(payload);
        lastErrorText = null;
        emitStatus();
      } catch (error: any) {
        lastErrorText = String(error?.message || error);
        emitStatus();
      }
    };

    ws.onerror = () => {
      wsConnected = false;
      if (!lastErrorText) {
        lastErrorText = 'ws error';
      }
      emitStatus();
    };

    ws.onclose = () => {
      wsConnected = false;
      adminWs = null;
      if (!manualWsClose) {
        scheduleReconnect();
      }
      emitStatus();
    };
  }

  function isWsOpen() {
    return !!adminWs && adminWs.readyState === WebSocket.OPEN;
  }

  function getSnapshot() {
    return latestSnapshot;
  }

  function getStatus() {
    return {
      wsConnected,
      lastErrorText,
      lastAdminMessageType,
      lastAdminMessageAt,
      lastAdminMessageRevision,
      lastRevision,
      wsReadyState: adminWs ? adminWs.readyState : -1,
    };
  }

  return {
    connect,
    reconnect,
    cleanup,
    sendCommand,
    requestResync,
    isWsOpen,
    getSnapshot,
    getStatus,
  };
}
