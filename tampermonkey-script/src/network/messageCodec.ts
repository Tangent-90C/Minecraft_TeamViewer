import type { AdminInboundPacket, AdminOutboundPacket } from './networkSchemas';
import { parseAdminInboundPacket } from './networkSchemas';

export interface NetworkMessageCodec {
  encode(packet: AdminOutboundPacket): string;
  decode(payload: string): AdminInboundPacket | null;
}

export class JsonNetworkMessageCodec implements NetworkMessageCodec {
  encode(packet: AdminOutboundPacket): string {
    return JSON.stringify(packet);
  }

  decode(payload: string): AdminInboundPacket | null {
    let parsed: unknown;
    try {
      parsed = JSON.parse(payload);
    } catch {
      return null;
    }
    return parseAdminInboundPacket(parsed);
  }
}
