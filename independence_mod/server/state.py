import hashlib
import json
import math
import time
from typing import Dict, Optional

from fastapi import WebSocket
from pydantic import ValidationError


class ServerState:
    """
    服务端内存态与状态仲裁中心。

    业务职责：
    - 保存三类对象（玩家/实体/路标）的“来源上报池”和“最终视图”；
    - 执行多来源仲裁、超时清理、差量计算；
    - 保存连接能力信息（是否支持 delta）。
    """

    # 默认超时配置（秒）
    PLAYER_TIMEOUT = 5
    ENTITY_TIMEOUT = 5
    WAYPOINT_TIMEOUT = 120
    ONLINE_OWNER_TIMEOUT_MULTIPLIER = 8
    SOURCE_SWITCH_THRESHOLD_SEC = 0.35

    # 协议配置
    PROTOCOL_V2 = 2
    DIGEST_INTERVAL_SEC = 10

    def __init__(self) -> None:
        # 已仲裁后的最终视图，供广播层直接下发。
        self.players: Dict[str, dict] = {}
        self.entities: Dict[str, dict] = {}
        self.waypoints: Dict[str, dict] = {}

        # 原始上报池：object_id -> source_id -> state_node。
        self.player_reports: Dict[str, Dict[str, dict]] = {}
        self.entity_reports: Dict[str, Dict[str, dict]] = {}
        self.waypoint_reports: Dict[str, Dict[str, dict]] = {}

        # 连接与能力信息。
        self.connections: Dict[str, WebSocket] = {}
        self.connection_caps: Dict[str, dict] = {}
        self.admin_connections: Dict[str, WebSocket] = {}

        # 管理端指挥态：用于玩家敌我/颜色标记。
        self.player_marks: Dict[str, dict] = {}

        # 来源粘性：用于减少多来源切换抖动。
        self.player_selected_sources: Dict[str, str] = {}
        self.entity_selected_sources: Dict[str, str] = {}
        self.waypoint_selected_sources: Dict[str, str] = {}

        self.revision = 0

    @staticmethod
    def normalize_mark_color(color_value: Optional[str]) -> Optional[str]:
        if not isinstance(color_value, str):
            return None

        text = color_value.strip()
        if not text:
            return None

        if text.startswith("#"):
            text = text[1:]

        if len(text) != 6:
            return None

        try:
            int(text, 16)
        except ValueError:
            return None

        return "#" + text.lower()

    @staticmethod
    def normalize_mark_team(team_value: Optional[str]) -> str:
        text = str(team_value or "").strip().lower()
        if text in ("friendly", "friend", "ally", "blue"):
            return "friendly"
        if text in ("enemy", "hostile", "red"):
            return "enemy"
        if text in ("neutral", "none", "unknown", "gray", "grey"):
            return "neutral"
        return "neutral"

    def set_player_mark(
        self,
        player_id: str,
        team: Optional[str],
        color: Optional[str],
        label: Optional[str] = None,
    ) -> Optional[dict]:
        if not isinstance(player_id, str) or not player_id.strip():
            return None

        normalized_player_id = player_id.strip()
        normalized_team = self.normalize_mark_team(team)
        normalized_color = self.normalize_mark_color(color)

        if normalized_color is None:
            normalized_color = {
                "friendly": "#3b82f6",
                "enemy": "#ef4444",
                "neutral": "#94a3b8",
            }[normalized_team]

        normalized_label: Optional[str] = None
        if isinstance(label, str):
            stripped = label.strip()
            if stripped:
                normalized_label = stripped[:64]

        mark = {
            "team": normalized_team,
            "color": normalized_color,
            "label": normalized_label,
            "updatedAt": int(time.time() * 1000),
        }
        self.player_marks[normalized_player_id] = mark
        return dict(mark)

    def clear_player_mark(self, player_id: str) -> bool:
        if not isinstance(player_id, str) or not player_id.strip():
            return False
        normalized_player_id = player_id.strip()
        if normalized_player_id not in self.player_marks:
            return False
        del self.player_marks[normalized_player_id]
        return True

    def clear_all_player_marks(self) -> int:
        count = len(self.player_marks)
        self.player_marks.clear()
        return count

    def next_revision(self) -> int:
        """全局版本号递增。用于客户端按 rev 应用状态。"""
        self.revision += 1
        return self.revision

    @staticmethod
    def compact_state_map(state_map: Dict[str, dict]) -> Dict[str, dict]:
        """将最终视图转换为下发格式（只保留 data）。"""
        return {sid: node.get("data", {}) for sid, node in state_map.items()}

    @staticmethod
    def canonical_number(value: float) -> str:
        if not math.isfinite(value):
            return "null"
        rounded = round(float(value), 6)
        text = f"{rounded:.6f}".rstrip("0").rstrip(".")
        if text in ("", "-0"):
            return "0"
        return text

    @classmethod
    def canonical_value(cls, value) -> str:
        if value is None:
            return "null"
        if isinstance(value, bool):
            return "true" if value else "false"
        if isinstance(value, int):
            return str(value)
        if isinstance(value, float):
            return cls.canonical_number(value)
        if isinstance(value, str):
            return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        if isinstance(value, dict):
            items = []
            for key in sorted(value.keys(), key=lambda item: str(item)):
                key_json = json.dumps(str(key), ensure_ascii=False, separators=(",", ":"))
                items.append(f"{key_json}:{cls.canonical_value(value[key])}")
            return "{" + ",".join(items) + "}"
        if isinstance(value, list):
            return "[" + ",".join(cls.canonical_value(item) for item in value) + "]"

        try:
            return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        except TypeError:
            return json.dumps(str(value), ensure_ascii=False, separators=(",", ":"))

    @classmethod
    def state_digest(cls, state_map: Dict[str, dict]) -> str:
        lines = []
        for node_id in sorted(state_map.keys()):
            node = state_map.get(node_id, {})
            data = node.get("data", {}) if isinstance(node, dict) else {}
            node_json = json.dumps(str(node_id), ensure_ascii=False, separators=(",", ":"))
            lines.append(f"{node_json}:{cls.canonical_value(data)}")

        raw = "\n".join(lines)
        return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]

    def build_digests(self) -> dict:
        """构建三类对象摘要，用于客户端一致性校验。"""
        return {
            "players": self.state_digest(self.players),
            "entities": self.state_digest(self.entities),
            "waypoints": self.state_digest(self.waypoints),
        }

    @staticmethod
    def make_empty_patch() -> dict:
        return {
            "players": {"upsert": {}, "delete": []},
            "entities": {"upsert": {}, "delete": []},
            "waypoints": {"upsert": {}, "delete": []},
        }

    @staticmethod
    def has_patch_changes(patch: dict) -> bool:
        for scope in ("players", "entities", "waypoints"):
            if patch[scope]["upsert"] or patch[scope]["delete"]:
                return True
        return False

    @staticmethod
    def merge_patch(base: dict, extra: dict) -> None:
        for scope in ("players", "entities", "waypoints"):
            base[scope]["upsert"].update(extra[scope]["upsert"])
            base[scope]["delete"].extend(extra[scope]["delete"])

    @staticmethod
    def compute_field_delta(old_data: Optional[dict], new_data: dict) -> dict:
        if old_data is None:
            return dict(new_data)

        delta = {}
        for key, value in new_data.items():
            if old_data.get(key) != value:
                delta[key] = value
        return delta

    @staticmethod
    def merge_patch_and_validate(model_cls, existing_node: Optional[dict], patch_data: dict) -> dict:
        merged = {}
        if existing_node and isinstance(existing_node.get("data"), dict):
            merged.update(existing_node["data"])
        if isinstance(patch_data, dict):
            merged.update(patch_data)
        validated = model_cls(**merged)
        return validated.model_dump()

    @staticmethod
    def payload_preview(payload, limit: int = 320) -> str:
        try:
            text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        except Exception:
            text = str(payload)
        if len(text) <= limit:
            return text
        return text[:limit] + "...(truncated)"

    @staticmethod
    def missing_fields_from_validation_error(error: ValidationError) -> list:
        fields = []
        for item in error.errors():
            if item.get("type") != "missing":
                continue
            loc = item.get("loc")
            if isinstance(loc, (list, tuple)) and loc:
                fields.append(".".join(str(part) for part in loc))
            elif loc is not None:
                fields.append(str(loc))
        return fields

    @staticmethod
    def websocket_state_label(ws: WebSocket) -> str:
        try:
            client_state = getattr(getattr(ws, "client_state", None), "name", str(getattr(ws, "client_state", None)))
            app_state = getattr(getattr(ws, "application_state", None), "name", str(getattr(ws, "application_state", None)))
            return f"client={client_state},app={app_state}"
        except Exception:
            return "client=unknown,app=unknown"

    @staticmethod
    def websocket_is_connected(ws: WebSocket) -> bool:
        client_state = getattr(getattr(ws, "client_state", None), "name", "")
        app_state = getattr(getattr(ws, "application_state", None), "name", "")
        return client_state == "CONNECTED" and app_state == "CONNECTED"

    @staticmethod
    def build_state_node(submit_player_id: Optional[str], current_time: float, normalized: dict) -> dict:
        return {
            "timestamp": current_time,
            "submitPlayerId": submit_player_id,
            "data": normalized,
        }

    @staticmethod
    def upsert_report(report_map: Dict[str, Dict[str, dict]], object_id: str, source_id: Optional[str], node: dict) -> None:
        source_key = source_id if isinstance(source_id, str) else ""
        source_bucket = report_map.setdefault(object_id, {})
        source_bucket[source_key] = node

    @staticmethod
    def delete_report(report_map: Dict[str, Dict[str, dict]], object_id: str, source_id: Optional[str]) -> bool:
        if object_id not in report_map:
            return False

        source_bucket = report_map[object_id]
        source_key = source_id if isinstance(source_id, str) else ""
        if source_key not in source_bucket:
            return False

        del source_bucket[source_key]
        if not source_bucket:
            del report_map[object_id]
        return True

    @staticmethod
    def node_timestamp(node: Optional[dict]) -> float:
        if not isinstance(node, dict):
            return 0.0
        value = node.get("timestamp")
        if not isinstance(value, (int, float)):
            return 0.0
        return float(value)

    @classmethod
    def resolve_report_map(
        cls,
        report_map: Dict[str, Dict[str, dict]],
        selected_sources: Dict[str, str],
        switch_threshold_sec: float,
        prefer_object_id_source: bool = False,
    ) -> Dict[str, dict]:
        resolved: Dict[str, dict] = {}
        next_selected_sources: Dict[str, str] = {}

        for object_id, source_bucket in report_map.items():
            if not isinstance(source_bucket, dict) or not source_bucket:
                continue

            valid_bucket: Dict[str, dict] = {
                source_id: node
                for source_id, node in source_bucket.items()
                if isinstance(node, dict)
            }
            if not valid_bucket:
                continue

            best_source_id = None
            best_node = None
            best_timestamp = float("-inf")
            for source_id, node in valid_bucket.items():
                timestamp_value = cls.node_timestamp(node)

                if timestamp_value > best_timestamp:
                    best_source_id = source_id
                    best_node = node
                    best_timestamp = timestamp_value
                    continue

                if timestamp_value == best_timestamp:
                    current_best_key = str(best_source_id) if best_source_id is not None else ""
                    current_key = str(source_id)
                    if current_key < current_best_key:
                        best_source_id = source_id
                        best_node = node

            chosen_source_id = best_source_id
            chosen_node = best_node

            preferred_source = str(object_id) if prefer_object_id_source else None
            if preferred_source and preferred_source in valid_bucket:
                preferred_node = valid_bucket[preferred_source]
                preferred_ts = cls.node_timestamp(preferred_node)
                if best_timestamp - preferred_ts <= switch_threshold_sec:
                    chosen_source_id = preferred_source
                    chosen_node = preferred_node

            previous_source = selected_sources.get(object_id)
            if previous_source in valid_bucket:
                previous_node = valid_bucket[previous_source]
                previous_ts = cls.node_timestamp(previous_node)
                chosen_ts = cls.node_timestamp(chosen_node)
                if chosen_ts - previous_ts <= switch_threshold_sec:
                    chosen_source_id = previous_source
                    chosen_node = previous_node

            if chosen_node is not None and chosen_source_id is not None:
                resolved[object_id] = chosen_node
                next_selected_sources[object_id] = chosen_source_id

        selected_sources.clear()
        selected_sources.update(next_selected_sources)

        return resolved

    @classmethod
    def compute_scope_patch(cls, old_map: Dict[str, dict], new_map: Dict[str, dict]) -> dict:
        scope_patch = {"upsert": {}, "delete": []}

        for object_id in old_map.keys() - new_map.keys():
            scope_patch["delete"].append(object_id)

        for object_id, new_node in new_map.items():
            old_node = old_map.get(object_id)
            old_data = old_node.get("data") if isinstance(old_node, dict) else None
            new_data = new_node.get("data") if isinstance(new_node, dict) else None
            if not isinstance(new_data, dict):
                new_data = {}
            delta = cls.compute_field_delta(old_data if isinstance(old_data, dict) else None, new_data)
            if delta:
                scope_patch["upsert"][object_id] = delta

        scope_patch["delete"].sort()
        return scope_patch

    def refresh_resolved_states(self) -> dict:
        """刷新最终视图并返回相对于上一帧的 patch。"""
        old_players = dict(self.players)
        old_entities = dict(self.entities)
        old_waypoints = dict(self.waypoints)

        self.players = self.resolve_report_map(
            self.player_reports,
            self.player_selected_sources,
            self.SOURCE_SWITCH_THRESHOLD_SEC,
            prefer_object_id_source=True,
        )
        self.entities = self.resolve_report_map(
            self.entity_reports,
            self.entity_selected_sources,
            self.SOURCE_SWITCH_THRESHOLD_SEC,
            prefer_object_id_source=False,
        )
        self.waypoints = self.resolve_report_map(
            self.waypoint_reports,
            self.waypoint_selected_sources,
            self.SOURCE_SWITCH_THRESHOLD_SEC,
            prefer_object_id_source=False,
        )

        return {
            "players": self.compute_scope_patch(old_players, self.players),
            "entities": self.compute_scope_patch(old_entities, self.entities),
            "waypoints": self.compute_scope_patch(old_waypoints, self.waypoints),
        }

    def mark_player_capability(self, player_id: str, protocol_version: int, delta_enabled: bool) -> None:
        """记录客户端能力，决定后续是否发送 patch/digest。"""
        self.connection_caps[player_id] = {
            "protocol": protocol_version,
            "delta": bool(protocol_version >= self.PROTOCOL_V2 and delta_enabled),
            "lastDigestSent": 0.0,
        }

    def is_delta_client(self, player_id: str) -> bool:
        caps = self.connection_caps.get(player_id)
        if not caps:
            return False
        return bool(caps.get("delta", False))

    def cleanup_timeouts(self) -> None:
        """按来源维度清理超时上报，避免脏数据长期占用最终视图。"""
        current_time = time.time()

        def is_owner_online(node: dict) -> bool:
            owner_id = node.get("submitPlayerId") if isinstance(node, dict) else None
            return isinstance(owner_id, str) and owner_id in self.connections

        def effective_timeout(base_timeout: int, node: dict) -> int:
            if is_owner_online(node):
                return base_timeout * self.ONLINE_OWNER_TIMEOUT_MULTIPLIER
            return base_timeout

        def effective_waypoint_timeout(node: dict) -> int:
            if not isinstance(node, dict):
                return self.WAYPOINT_TIMEOUT
            data = node.get("data")
            if not isinstance(data, dict):
                return self.WAYPOINT_TIMEOUT
            ttl = data.get("ttlSeconds")
            if isinstance(ttl, (int, float)):
                ttl_int = int(ttl)
                if ttl_int < 5:
                    return 5
                return min(ttl_int, 86400)
            return self.WAYPOINT_TIMEOUT

        def cleanup_report_map(report_map: Dict[str, Dict[str, dict]], timeout_resolver) -> None:
            for object_id in list(report_map.keys()):
                source_bucket = report_map.get(object_id)
                if not isinstance(source_bucket, dict):
                    del report_map[object_id]
                    continue

                for source_id in list(source_bucket.keys()):
                    node = source_bucket.get(source_id)
                    if not isinstance(node, dict):
                        del source_bucket[source_id]
                        continue
                    timestamp = node.get("timestamp")
                    if not isinstance(timestamp, (int, float)):
                        del source_bucket[source_id]
                        continue

                    timeout_seconds = timeout_resolver(node)
                    if current_time - float(timestamp) > timeout_seconds:
                        del source_bucket[source_id]

                if not source_bucket:
                    del report_map[object_id]

        cleanup_report_map(self.player_reports, lambda node: effective_timeout(self.PLAYER_TIMEOUT, node))
        cleanup_report_map(self.entity_reports, lambda node: effective_timeout(self.ENTITY_TIMEOUT, node))
        cleanup_report_map(self.waypoint_reports, effective_waypoint_timeout)

    def remove_connection(self, player_id: str) -> None:
        """连接断开时，移除该来源在所有上报池中的数据。"""
        if player_id in self.connections:
            del self.connections[player_id]
        if player_id in self.connection_caps:
            del self.connection_caps[player_id]

        def remove_source_reports(report_map: Dict[str, Dict[str, dict]]) -> None:
            for object_id in list(report_map.keys()):
                source_bucket = report_map.get(object_id)
                if not isinstance(source_bucket, dict):
                    del report_map[object_id]
                    continue
                if player_id in source_bucket:
                    del source_bucket[player_id]
                if not source_bucket:
                    del report_map[object_id]

        remove_source_reports(self.player_reports)
        remove_source_reports(self.entity_reports)
        remove_source_reports(self.waypoint_reports)
