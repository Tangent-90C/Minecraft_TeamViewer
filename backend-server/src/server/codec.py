from __future__ import annotations

import json
from typing import Any, Protocol

from pydantic import BaseModel

from .protocol import PacketDecodeError


class MessageCodec(Protocol):
    def decode(self, payload: str) -> dict[str, Any]: ...

    def encode(self, packet: BaseModel | dict[str, Any]) -> str: ...


class JsonMessageCodec:
    """默认 JSON 编解码器，可替换为 MsgPack/ProtoBuf 等实现。"""

    def decode(self, payload: str) -> dict[str, Any]:
        try:
            data = json.loads(payload)
        except json.JSONDecodeError as exc:
            raise PacketDecodeError("invalid_json", str(exc)) from exc

        if not isinstance(data, dict):
            raise PacketDecodeError("invalid_payload", "payload must be an object")

        return data

    def encode(self, packet: BaseModel | dict[str, Any]) -> str:
        if isinstance(packet, BaseModel):
            body = packet.model_dump(exclude_none=True)
        else:
            body = packet
        return json.dumps(body, separators=(",", ":"))
