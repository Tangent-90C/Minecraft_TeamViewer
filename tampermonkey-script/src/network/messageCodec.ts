import type { AdminInboundPacket, AdminOutboundPacket } from './networkSchemas';
import { decode as decodeMsgpack, encode as encodeMsgpack } from '@msgpack/msgpack';
import { parseAdminInboundPacket } from './networkSchemas';

const UUID_SCALAR_KEYS = new Set([
  'submitPlayerId',
  'playerId',
  'playerUUID',
  'ownerId',
  'targetEntityId',
  'uuid',
  'id',
]);

const UUID_LIST_KEYS = new Set([
  'targetEntityIds',
  'players',
  'delete',
  'connections',
  'members',
  'waypointIds',
]);

const UUID_KEYED_MAP_KEYS = new Set([
  'players',
  'entities',
  'waypoints',
  'playerMarks',
  'reports',
  'sourceToGroup',
  'upsert',
]);

function bytesToUuid(value: unknown): string | null {
  if (!(value instanceof Uint8Array) || value.length !== 16) {
    return null;
  }

  const hex = Array.from(value).map((byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function decodeMapKeyToStringOrNumber(key: unknown): string | number {
  if (typeof key === 'string' || typeof key === 'number') {
    return key;
  }

  if (key instanceof Uint8Array) {
    return bytesToUuid(key) ?? Array.from(key).join(',');
  }

  if (key instanceof ArrayBuffer) {
    const asBytes = new Uint8Array(key);
    return bytesToUuid(asBytes) ?? Array.from(asBytes).join(',');
  }

  if (ArrayBuffer.isView(key)) {
    const view = key as ArrayBufferView;
    const asBytes = new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
    return bytesToUuid(asBytes) ?? Array.from(asBytes).join(',');
  }

  return String(key);
}

function uuidToBytes(value: unknown): Uint8Array | null {
  if (value instanceof Uint8Array && value.length === 16) {
    return value;
  }
  if (typeof value !== 'string') {
    return null;
  }
  const text = value.trim().toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(text)) {
    return null;
  }
  const pureHex = text.replace(/-/g, '');
  const bytes = new Uint8Array(16);
  for (let index = 0; index < 16; index += 1) {
    const start = index * 2;
    bytes[index] = parseInt(pureHex.slice(start, start + 2), 16);
  }
  return bytes;
}

function normalizeInboundUuidFields(payload: unknown, keyName?: string): unknown {
  if (payload instanceof Map) {
    const next: Record<string, unknown> = {};
    for (const [rawKey, rawValue] of payload.entries()) {
      let key: string;
      if (rawKey instanceof Uint8Array && UUID_KEYED_MAP_KEYS.has(String(keyName || ''))) {
        key = bytesToUuid(rawKey) ?? String(rawKey);
      } else {
        key = String(rawKey);
      }
      next[key] = normalizeInboundUuidFields(rawValue, key);
    }
    return next;
  }

  if (Array.isArray(payload)) {
    if (keyName && UUID_LIST_KEYS.has(keyName)) {
      return payload.map((item) => bytesToUuid(item) ?? normalizeInboundUuidFields(item));
    }
    return payload.map((item) => normalizeInboundUuidFields(item));
  }

  if (!payload || typeof payload !== 'object') {
    return payload;
  }

  const obj = payload as Record<string, unknown>;
  const next: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(obj)) {
    if (UUID_SCALAR_KEYS.has(key)) {
      next[key] = bytesToUuid(value) ?? value;
      continue;
    }

    if (UUID_LIST_KEYS.has(key) && Array.isArray(value)) {
      next[key] = value.map((item) => bytesToUuid(item) ?? normalizeInboundUuidFields(item));
      continue;
    }

    next[key] = normalizeInboundUuidFields(value, key);
  }
  return next;
}

function normalizeOutboundUuidFields(payload: unknown, keyName?: string): unknown {
  if (payload instanceof Map) {
    const next = new Map<unknown, unknown>();
    for (const [rawKey, rawValue] of payload.entries()) {
      const outKey = UUID_KEYED_MAP_KEYS.has(String(keyName || '')) ? (uuidToBytes(rawKey) ?? rawKey) : rawKey;
      const childKey = typeof rawKey === 'string' ? rawKey : undefined;
      next.set(outKey, normalizeOutboundUuidFields(rawValue, childKey));
    }
    return next;
  }

  if (Array.isArray(payload)) {
    if (keyName && UUID_LIST_KEYS.has(keyName)) {
      return payload.map((item) => uuidToBytes(item) ?? normalizeOutboundUuidFields(item));
    }
    return payload.map((item) => normalizeOutboundUuidFields(item));
  }

  if (!payload || typeof payload !== 'object') {
    return payload;
  }

  const obj = payload as Record<string, unknown>;
  const next: Record<string, unknown> | Map<unknown, unknown> = UUID_KEYED_MAP_KEYS.has(String(keyName || ''))
    ? new Map<unknown, unknown>()
    : {};
  for (const [key, value] of Object.entries(obj)) {
    const outKey = UUID_KEYED_MAP_KEYS.has(String(keyName || '')) ? (uuidToBytes(key) ?? key) : key;
    if (UUID_SCALAR_KEYS.has(key)) {
      if (next instanceof Map) next.set(outKey, uuidToBytes(value) ?? value);
      else next[key] = uuidToBytes(value) ?? value;
      continue;
    }

    if (UUID_LIST_KEYS.has(key) && Array.isArray(value)) {
      const converted = value.map((item) => uuidToBytes(item) ?? normalizeOutboundUuidFields(item));
      if (next instanceof Map) next.set(outKey, converted);
      else next[key] = converted;
      continue;
    }

    const converted = normalizeOutboundUuidFields(value, key);
    if (next instanceof Map) next.set(outKey, converted);
    else next[key] = converted;
  }
  return next;
}

export interface NetworkMessageCodec {
  encode(packet: AdminOutboundPacket): ArrayBuffer;
  decode(payload: ArrayBuffer | Uint8Array | string): AdminInboundPacket | null;
}

export class MsgpackNetworkMessageCodec implements NetworkMessageCodec {
  encode(packet: AdminOutboundPacket): ArrayBuffer {
    const normalized = normalizeOutboundUuidFields(packet);
    const encoded = encodeMsgpack(normalized);
    return Uint8Array.from(encoded).buffer;
  }

  decode(payload: ArrayBuffer | Uint8Array | string): AdminInboundPacket | null {
    let raw: Uint8Array;
    if (typeof payload === 'string') {
      raw = new TextEncoder().encode(payload);
    } else if (payload instanceof Uint8Array) {
      raw = payload;
    } else {
      raw = new Uint8Array(payload);
    }

    let parsed: unknown;
    try {
      parsed = decodeMsgpack(raw, {
        mapKeyConverter: decodeMapKeyToStringOrNumber,
      });
    } catch {
      return null;
    }
    const normalized = normalizeInboundUuidFields(parsed);
    return parseAdminInboundPacket(normalized);
  }
}
