from __future__ import annotations

import uuid
from typing import Any

UUID_SCALAR_KEYS = {
    "submitPlayerId",
    "playerId",
    "playerUUID",
    "ownerId",
    "targetEntityId",
    "uuid",
    "id",
}

UUID_LIST_KEYS = {
    "targetEntityIds",
    "players",
    "delete",
    "connections",
    "members",
    "waypointIds",
}

UUID_KEYED_MAP_FIELDS = {
    "players",
    "entities",
    "waypoints",
    "playerMarks",
    "reports",
    "sourceToGroup",
    "upsert",
}


def _normalize_inbound_map_key(raw_key: Any, parent_key: str | None) -> Any:
    if isinstance(raw_key, (bytes, bytearray, memoryview)):
        as_uuid = _canonical_uuid_text(raw_key)
        if as_uuid is not None:
            return as_uuid
        try:
            return bytes(raw_key).decode("utf-8", errors="ignore")
        except Exception:
            return raw_key

    if parent_key in UUID_KEYED_MAP_FIELDS and isinstance(raw_key, str):
        as_uuid = _canonical_uuid_text(raw_key)
        if as_uuid is not None:
            return as_uuid

    return raw_key


def _normalize_outbound_map_key(raw_key: Any, parent_key: str | None) -> Any:
    if parent_key in UUID_KEYED_MAP_FIELDS and isinstance(raw_key, str):
        as_uuid = _uuid_bytes(raw_key)
        if as_uuid is not None:
            return as_uuid
    return raw_key


def _canonical_uuid_text(value: Any) -> str | None:
    if isinstance(value, uuid.UUID):
        return str(value)
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return str(uuid.UUID(text))
        except Exception:
            return None
    if isinstance(value, (bytes, bytearray, memoryview)):
        raw = bytes(value)
        if len(raw) != 16:
            return None
        try:
            return str(uuid.UUID(bytes=raw))
        except Exception:
            return None
    return None


def _uuid_bytes(value: Any) -> bytes | None:
    if isinstance(value, (bytes, bytearray, memoryview)):
        raw = bytes(value)
        return raw if len(raw) == 16 else None

    text = _canonical_uuid_text(value)
    if text is None:
        return None
    try:
        return uuid.UUID(text).bytes
    except Exception:
        return None


def normalize_inbound_uuid_fields(payload: Any, key_name: str | None = None) -> Any:
    if isinstance(payload, dict):
        normalized: dict[Any, Any] = {}
        for raw_key, raw_value in payload.items():
            key = _normalize_inbound_map_key(raw_key, key_name)
            key_text = key if isinstance(key, str) else None

            if key_text in UUID_SCALAR_KEYS:
                uuid_text = _canonical_uuid_text(raw_value)
                normalized[key] = uuid_text if uuid_text is not None else raw_value
                continue

            if key_text in UUID_LIST_KEYS and isinstance(raw_value, list):
                converted = []
                for item in raw_value:
                    uuid_text = _canonical_uuid_text(item)
                    converted.append(uuid_text if uuid_text is not None else item)
                normalized[key] = converted
                continue

            normalized[key] = normalize_inbound_uuid_fields(raw_value, key_text)
        return normalized

    if isinstance(payload, list):
        return [normalize_inbound_uuid_fields(item, key_name) for item in payload]

    return payload


def normalize_outbound_uuid_fields(payload: Any, key_name: str | None = None) -> Any:
    if isinstance(payload, dict):
        normalized: dict[Any, Any] = {}
        for key, raw_value in payload.items():
            out_key = _normalize_outbound_map_key(key, key_name)
            key_text = key if isinstance(key, str) else None

            if key_text in UUID_SCALAR_KEYS:
                raw_uuid = _uuid_bytes(raw_value)
                normalized[out_key] = raw_uuid if raw_uuid is not None else raw_value
                continue

            if key_text in UUID_LIST_KEYS and isinstance(raw_value, list):
                converted = []
                for item in raw_value:
                    raw_uuid = _uuid_bytes(item)
                    converted.append(raw_uuid if raw_uuid is not None else item)
                normalized[out_key] = converted
                continue

            normalized[out_key] = normalize_outbound_uuid_fields(raw_value, key_text)
        return normalized

    if isinstance(payload, list):
        return [normalize_outbound_uuid_fields(item, key_name) for item in payload]

    return payload
