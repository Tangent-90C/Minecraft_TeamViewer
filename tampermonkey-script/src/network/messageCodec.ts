import type { AdminInboundPacket, AdminOutboundPacket } from './networkSchemas';
import { decode as decodeMsgpack, encode as encodeMsgpack } from '@msgpack/msgpack';
import { parseAdminInboundPacket } from './networkSchemas';

export interface NetworkMessageCodec {
  encode(packet: AdminOutboundPacket): ArrayBuffer;
  decode(payload: ArrayBuffer | Uint8Array | string): AdminInboundPacket | null;
}

export class MsgpackNetworkMessageCodec implements NetworkMessageCodec {
  encode(packet: AdminOutboundPacket): ArrayBuffer {
    const encoded = encodeMsgpack(packet);
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
      parsed = decodeMsgpack(raw);
    } catch {
      return null;
    }
    return parseAdminInboundPacket(parsed);
  }
}
