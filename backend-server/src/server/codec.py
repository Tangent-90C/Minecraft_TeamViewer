from __future__ import annotations

from typing import Any, Protocol

import msgpack
from pydantic import BaseModel

from .protocol import PacketDecodeError
from .uuid_codec import normalize_inbound_uuid_fields, normalize_outbound_uuid_fields


class MessageCodec(Protocol):
    def decode(self, payload: bytes | bytearray | memoryview | str) -> dict[str, Any]: ...

    def encode(self, packet: BaseModel | dict[str, Any]) -> bytes: ...


class MsgpackMessageCodec:
    """默认 MessagePack 编解码器。"""

    def decode(self, payload: bytes | bytearray | memoryview | str) -> dict[str, Any]:
        raw: bytes
        if isinstance(payload, str):
            raw = payload.encode("utf-8")
        elif isinstance(payload, memoryview):
            raw = payload.tobytes()
        else:
            raw = bytes(payload)

        try:
            data = msgpack.unpackb(raw, raw=False)
        except Exception as exc:
            raise PacketDecodeError("invalid_msgpack", str(exc)) from exc

        if not isinstance(data, dict):
            raise PacketDecodeError("invalid_payload", "payload must be an object")

        return normalize_inbound_uuid_fields(data)

    def encode(self, packet: BaseModel | dict[str, Any]) -> bytes:
        if isinstance(packet, BaseModel):
            body = packet.model_dump(exclude_none=True)
        else:
            body = packet
        normalized = normalize_outbound_uuid_fields(body)
        return msgpack.packb(normalized, use_bin_type=True)
