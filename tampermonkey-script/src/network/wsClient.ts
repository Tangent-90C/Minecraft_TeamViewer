import {
  ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION,
} from '../constants';
import {
  applyScopePatchMap,
  shouldResyncForScopeMissingBaseline,
} from '../utils/overlayUtils';
import {
  AdminInboundPacket,
  AdminOutboundPacket,
  AdminSnapshot,
  buildAdminHandshake,
  buildAdminResyncRequest,
  createEmptyAdminSnapshotModel,
} from './networkSchemas';
import { MsgpackNetworkMessageCodec } from './messageCodec';

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
  }) => void;
  onVersionIncompatible?: (payload: {
    message: string;
    serverProtocolVersion?: string;
    minimumCompatibleVersion?: string;
    rejectReason?: string;
  }) => void;
};

export function createEmptyAdminSnapshot() {
  return createEmptyAdminSnapshotModel();
}

export function createAdminWsClient(deps: WsClientDeps) {
  const messageCodec = new MsgpackNetworkMessageCodec();
  let adminWs: WebSocket | null = null;
  let wsConnected = false;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let manualWsClose = false;
  let reconnectSuppressedByVersionIncompatibility = false;

  let lastErrorText: string | null = null;
  let lastAdminResyncRequestAt = 0;
  let lastAdminMessageType: string | null = null;
  let lastAdminMessageAt = 0;
  let latestSnapshot: Snapshot = createEmptyAdminSnapshot();

  function emitStatus() {
    deps.onWsStatusChanged({
      wsConnected,
      lastErrorText,
      lastAdminMessageType,
      lastAdminMessageAt,
    });
  }

  function scheduleReconnect() {
    if (reconnectSuppressedByVersionIncompatibility) return;
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
      adminWs.send(messageCodec.encode(buildAdminResyncRequest(reason)));
    } catch (_) {}
  }

  function applyAdminDeltaMessage(message: AdminInboundPacket) {
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
        server_time: message.server_time,
      };
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
    const metaTabState = (meta as Record<string, unknown>).tabState;
    if (metaTabState && typeof metaTabState === 'object') {
      latestSnapshot.tabState = metaTabState as { enabled: boolean; reports: Record<string, any>; groups: any[] };
    }
    const metaConnections = (meta as Record<string, unknown>).connections;
    if (Array.isArray(metaConnections)) {
      latestSnapshot.connections = metaConnections as string[];
    }
    const metaConnectionsCount = (meta as Record<string, unknown>).connections_count;
    if (Number.isFinite(metaConnectionsCount)) {
      latestSnapshot.connections_count = Number(metaConnectionsCount);
    }

    if (message.server_time !== undefined) {
      latestSnapshot.server_time = message.server_time;
    }

    deps.onSnapshotChanged(latestSnapshot);
  }

  function sendCommand(message: AdminOutboundPacket) {
    if (!adminWs || adminWs.readyState !== WebSocket.OPEN) {
      lastErrorText = 'ws not connected';
      emitStatus();
      return false;
    }
    try {
      adminWs.send(messageCodec.encode(message));
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
    reconnectSuppressedByVersionIncompatibility = false;
    lastErrorText = null;
    connect();
    emitStatus();
  }

  function parseProtocolVersionNumber(version: unknown): number {
    const raw = String(version ?? '').trim();
    if (!raw) return 0;
    const core = raw.split('-', 1)[0] || '';
    const parts = core.split('.');
    const nums = [0, 0, 0].map((_, index) => {
      const token = String(parts[index] ?? '').trim();
      const match = token.match(/^(\d+)/);
      return match ? Number(match[1]) : 0;
    });
    return nums[0] * 1_000_000 + nums[1] * 1_000 + nums[2];
  }

  function protocolAtLeast(current: unknown, minimum: unknown): boolean {
    return parseProtocolVersionNumber(current) >= parseProtocolVersionNumber(minimum);
  }

  function formatHandshakeRejectReason(payload: Record<string, unknown>): string {
    const text = String(payload.rejectReason ?? payload.error ?? '').trim();
    return text || 'unknown';
  }

  function forceCloseForIncompatibleVersion(
    message: string,
    details?: {
      serverProtocolVersion?: string;
      minimumCompatibleVersion?: string;
      rejectReason?: string;
    },
  ) {
    reconnectSuppressedByVersionIncompatibility = true;
    wsConnected = false;
    lastErrorText = message;
    try {
      deps.onVersionIncompatible?.({
        message,
        serverProtocolVersion: details?.serverProtocolVersion,
        minimumCompatibleVersion: details?.minimumCompatibleVersion,
        rejectReason: details?.rejectReason,
      });
    } catch (_) {}
    if (adminWs) {
      try {
        adminWs.close(1008, message.slice(0, 120));
      } catch (_) {
        try { adminWs.close(); } catch (_) {}
      }
    }
    emitStatus();
  }

  function connect() {
    if (adminWs && (adminWs.readyState === WebSocket.OPEN || adminWs.readyState === WebSocket.CONNECTING)) {
      return;
    }

    reconnectSuppressedByVersionIncompatibility = false;

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
    ws.binaryType = 'arraybuffer';
    ws.onopen = () => {
      wsConnected = true;
      lastErrorText = null;
      try {
        ws.send(messageCodec.encode(buildAdminHandshake(config.ROOM_CODE)));
      } catch (error: any) {
        lastErrorText = String(error?.message || error);
      }
      emitStatus();
    };

    ws.onmessage = async (event) => {
      try {
        const data = event?.data;
        let rawPayload: ArrayBuffer | Uint8Array | string | null = null;
        if (data instanceof ArrayBuffer) {
          rawPayload = data;
        } else if (typeof data === 'string') {
          rawPayload = data;
        } else if (data && typeof (data as Blob).arrayBuffer === 'function') {
          rawPayload = await (data as Blob).arrayBuffer();
        }
        if (rawPayload == null) return;

        const payload = messageCodec.decode(rawPayload);
        if (!payload) {
          return;
        }
        lastAdminMessageType = payload?.type ? String(payload.type) : 'unknown';
        lastAdminMessageAt = Date.now();

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

        if (payload?.type === 'pong') {
          lastErrorText = null;
          emitStatus();
          return;
        }

        if (payload?.type === 'handshake_ack') {
          const handshakePayload = payload as Record<string, unknown>;
          if (handshakePayload.ready === false) {
            const reason = formatHandshakeRejectReason(handshakePayload);
            forceCloseForIncompatibleVersion(`服务端拒绝握手: ${reason}`, {
              rejectReason: reason,
            });
            return;
          }

          const serverProtocolVersion = String(handshakePayload.networkProtocolVersion ?? '0.0.0').trim() || '0.0.0';
          if (!protocolAtLeast(serverProtocolVersion, ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION)) {
            forceCloseForIncompatibleVersion(
              `版本不兼容: 服务端协议 ${serverProtocolVersion} 低于脚本最低要求 ${ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION}`,
              {
                serverProtocolVersion,
                minimumCompatibleVersion: ADMIN_MIN_COMPATIBLE_NETWORK_PROTOCOL_VERSION,
              },
            );
            return;
          }

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

    ws.onclose = (event) => {
      wsConnected = false;
      adminWs = null;
      if (!manualWsClose && !reconnectSuppressedByVersionIncompatibility) {
        scheduleReconnect();
      } else if (reconnectSuppressedByVersionIncompatibility && !lastErrorText) {
        const reason = String(event?.reason || '').trim();
        lastErrorText = reason || '版本不兼容，已停止自动重连';
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
