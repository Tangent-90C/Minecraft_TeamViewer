import hashlib
import json
import logging
import math
import time
import tomllib
from pathlib import Path
from typing import Dict, Optional

from fastapi import WebSocket
from pydantic import ValidationError


logger = logging.getLogger("teamviewer.state")


class ServerState:
    """
    服务端内存态与状态仲裁中心。

    业务职责：
    - 保存三类对象（玩家/实体/路标）的“来源上报池”和“最终视图”；
    - 执行多来源仲裁、超时清理、差量计算；
    - 保存连接能力信息（是否支持 delta）。
    """

    # 默认超时配置（秒）
    # - PLAYER_TIMEOUT / ENTITY_TIMEOUT / WAYPOINT_TIMEOUT:
    #   对象在“没有新上报”时的基础生存时间。
    #   实际运行值会在 __init__ 中由配置文件覆盖。
    PLAYER_TIMEOUT = 120
    ENTITY_TIMEOUT = 300
    WAYPOINT_TIMEOUT = 60
    # 多来源切换阈值：仅当候选来源显著更新（领先该阈值秒）才切换来源，减少抖动。
    SOURCE_SWITCH_THRESHOLD_SEC = 0.35
    # 超时清理日志输出节流：避免高频刷屏。
    TIMEOUT_LOG_INTERVAL_SEC = 2.0
    TIMEOUT_LOG_SAMPLE_LIMIT = 20
    # refresh_req 下发节流：同一来源两次请求最小间隔（秒）。
    REFRESH_REQUEST_COOLDOWN_SEC = 1.5
    # 提前量窗口：对象距离超时 <= 该值时，会触发 pre-expiry refresh_req（秒）。
    REFRESH_REQUEST_LEAD_SEC = 1.2
    # 单次 refresh_req 每个 scope 最多携带多少对象，避免包过大。
    REFRESH_REQUEST_MAX_ITEMS_PER_SCOPE = 64

    # 协议配置（可由配置文件覆盖）
    DIGEST_INTERVAL_SEC = 10
    DEFAULT_BROADCAST_HZ = 20.0
    MIN_BROADCAST_HZ = 2.0
    CONGESTION_LEVELS = (
        (40, 2.0),
        (20, 5.0),
        (8, 10.0),
    )
    TAB_REPORT_TIMEOUT_SEC = 45
    DEFAULT_ROOM_CODE = "default"
    ADMIN_TACTICAL_SOURCE_PREFIX = "__admin_tactical__:"

    # 服务端配置文件（TOML）路径。
    CONFIG_FILE_NAME = "server_state_config.toml"

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
        self.connection_rooms: Dict[str, str] = {}
        self.admin_connections: Dict[str, WebSocket] = {}
        self.admin_connection_rooms: Dict[str, str] = {}

        # 管理端指挥态：用于玩家敌我/颜色标记。
        self.player_marks: Dict[str, dict] = {}

        # Tab 玩家列表来源报告：submit_player_id -> report。
        self.tab_player_reports: Dict[str, dict] = {}

        # 来源粘性：用于减少多来源切换抖动。
        self.player_selected_sources: Dict[str, str] = {}
        self.entity_selected_sources: Dict[str, str] = {}
        self.waypoint_selected_sources: Dict[str, str] = {}

        self.broadcast_hz = float(self.DEFAULT_BROADCAST_HZ)
        self._last_timeout_log_ts = 0.0
        self._last_refresh_request_ts: Dict[str, float] = {}

        # 配置加载说明：
        # 1) 不再读取环境变量；
        # 2) 全部从 server_state_config.toml 读取；
        # 3) 未配置时回退到类默认值。
        config = self._load_file_config()

        timeout_cfg = config.get("timeouts", {}) if isinstance(config.get("timeouts"), dict) else {}
        self.PLAYER_TIMEOUT = self._coerce_int(timeout_cfg.get("playerTimeoutSec"), self.PLAYER_TIMEOUT, 1, 3600)
        self.ENTITY_TIMEOUT = self._coerce_int(timeout_cfg.get("entityTimeoutSec"), self.ENTITY_TIMEOUT, 1, 3600)
        self.WAYPOINT_TIMEOUT = self._coerce_int(timeout_cfg.get("waypointTimeoutSec"), self.WAYPOINT_TIMEOUT, 5, 86400)

        timeout_log_cfg = config.get("timeoutLog", {}) if isinstance(config.get("timeoutLog"), dict) else {}
        self.TIMEOUT_LOG_INTERVAL_SEC = self._coerce_float(
            timeout_log_cfg.get("intervalSec"),
            self.TIMEOUT_LOG_INTERVAL_SEC,
            0.1,
            600.0,
        )
        self.TIMEOUT_LOG_SAMPLE_LIMIT = self._coerce_int(
            timeout_log_cfg.get("sampleLimit"),
            self.TIMEOUT_LOG_SAMPLE_LIMIT,
            1,
            500,
        )

        refresh_cfg = config.get("refreshRequest", {}) if isinstance(config.get("refreshRequest"), dict) else {}
        self.REFRESH_REQUEST_COOLDOWN_SEC = self._coerce_float(
            refresh_cfg.get("cooldownSec"),
            self.REFRESH_REQUEST_COOLDOWN_SEC,
            0.1,
            120.0,
        )
        self.REFRESH_REQUEST_LEAD_SEC = self._coerce_float(
            refresh_cfg.get("leadSec"),
            self.REFRESH_REQUEST_LEAD_SEC,
            0.1,
            30.0,
        )
        self.REFRESH_REQUEST_MAX_ITEMS_PER_SCOPE = self._coerce_int(
            refresh_cfg.get("maxItemsPerScope"),
            self.REFRESH_REQUEST_MAX_ITEMS_PER_SCOPE,
            1,
            500,
        )

        protocol_cfg = config.get("protocol", {}) if isinstance(config.get("protocol"), dict) else {}
        self.DIGEST_INTERVAL_SEC = self._coerce_int(protocol_cfg.get("digestIntervalSec"), self.DIGEST_INTERVAL_SEC, 1, 120)
        self.DEFAULT_BROADCAST_HZ = self._coerce_float(protocol_cfg.get("defaultBroadcastHz"), self.DEFAULT_BROADCAST_HZ, 1.0, 120.0)
        self.MIN_BROADCAST_HZ = self._coerce_float(protocol_cfg.get("minBroadcastHz"), self.MIN_BROADCAST_HZ, 0.5, 60.0)
        self.TAB_REPORT_TIMEOUT_SEC = self._coerce_int(protocol_cfg.get("tabReportTimeoutSec"), self.TAB_REPORT_TIMEOUT_SEC, 5, 600)

        raw_congestion_levels = protocol_cfg.get("congestionLevels")
        parsed_congestion_levels = self._parse_congestion_levels(raw_congestion_levels)
        if parsed_congestion_levels:
            self.CONGESTION_LEVELS = tuple(parsed_congestion_levels)

        feature_cfg = config.get("features", {}) if isinstance(config.get("features"), dict) else {}
        self.same_server_filter_enabled = self._coerce_bool(
            feature_cfg.get("sameServerFilterEnabled"),
            False,
        )

        logger.info(
            "ServerState timeout config "
            f"player={self.PLAYER_TIMEOUT}s entity={self.ENTITY_TIMEOUT}s waypoint={self.WAYPOINT_TIMEOUT}s"
        )
        logger.info(
            "ServerState same-server filter config "
            f"enabled={self.same_server_filter_enabled} tabReportTimeout={self.TAB_REPORT_TIMEOUT_SEC}s"
        )

    @classmethod
    def _config_file_path(cls) -> Path:
        return Path(__file__).resolve().parent / cls.CONFIG_FILE_NAME

    @classmethod
    def _load_file_config(cls) -> dict:
        path = cls._config_file_path()
        if not path.exists():
            logger.warning(
                "Config file not found, fallback to defaults: %s",
                path,
            )
            return {}

        try:
            with path.open("rb") as fp:
                data = tomllib.load(fp)
            return data if isinstance(data, dict) else {}
        except Exception as exc:
            logger.warning(
                "Failed to load config file %s, fallback to defaults: %s",
                path,
                exc,
            )
            return {}

    @staticmethod
    def _coerce_int(value, default: int, min_value: int, max_value: int) -> int:
        if not isinstance(value, (int, float)):
            return default
        coerced = int(value)
        if coerced < min_value:
            return min_value
        if coerced > max_value:
            return max_value
        return coerced

    @staticmethod
    def _coerce_float(value, default: float, min_value: float, max_value: float) -> float:
        if not isinstance(value, (int, float)):
            return default
        coerced = float(value)
        if coerced < min_value:
            return min_value
        if coerced > max_value:
            return max_value
        return coerced

    @staticmethod
    def _coerce_bool(value, default: bool) -> bool:
        if isinstance(value, bool):
            return value
        return default

    @staticmethod
    def _parse_congestion_levels(raw_levels) -> list[tuple[int, float]]:
        if not isinstance(raw_levels, list):
            return []

        levels: list[tuple[int, float]] = []
        for item in raw_levels:
            if not isinstance(item, (list, tuple)) or len(item) != 2:
                continue
            threshold, hz = item[0], item[1]
            if not isinstance(threshold, (int, float)) or not isinstance(hz, (int, float)):
                continue
            threshold_int = max(1, int(threshold))
            hz_float = max(0.5, float(hz))
            levels.append((threshold_int, hz_float))

        levels.sort(key=lambda pair: pair[0], reverse=True)
        return levels

    @staticmethod
    def _normalize_tab_uuid(value) -> Optional[str]:
        text = str(value or "").strip().lower()
        if len(text) != 36:
            return None
        return text

    @staticmethod
    def _normalize_tab_name(value) -> Optional[str]:
        if not isinstance(value, str):
            return None
        text = value.strip()
        if not text:
            return None
        return text[:64]

    @classmethod
    def normalize_room_code(cls, value) -> str:
        if isinstance(value, str):
            text = value.strip()
            if text:
                return text[:64]
        return cls.DEFAULT_ROOM_CODE

    def set_player_room(self, player_id: str, room_code) -> str:
        normalized = self.normalize_room_code(room_code)
        if isinstance(player_id, str) and player_id:
            self.connection_rooms[player_id] = normalized
        return normalized

    def get_player_room(self, player_id: str) -> str:
        room_code = self.connection_rooms.get(player_id)
        if isinstance(room_code, str) and room_code.strip():
            return room_code
        return self.DEFAULT_ROOM_CODE

    def set_admin_room(self, admin_id: str, room_code) -> str:
        normalized = self.normalize_room_code(room_code)
        if isinstance(admin_id, str) and admin_id:
            self.admin_connection_rooms[admin_id] = normalized
        return normalized

    def get_admin_room(self, admin_id: str) -> str:
        room_code = self.admin_connection_rooms.get(admin_id)
        if isinstance(room_code, str) and room_code.strip():
            return room_code
        return self.DEFAULT_ROOM_CODE

    def get_active_sources_in_room(self, room_code: str) -> set[str]:
        normalized_room = self.normalize_room_code(room_code)
        return {
            source_id for source_id in self.connections.keys()
            if isinstance(source_id, str)
            and source_id
            and self.get_player_room(source_id) == normalized_room
        }

    def upsert_tab_player_report(self, submit_player_id: str, tab_players: list, current_time: float) -> dict:
        normalized_entries: list[dict] = []
        identity_keys: set[str] = set()
        if isinstance(submit_player_id, str) and submit_player_id.strip():
            identity_keys.add(f"uuid:{submit_player_id.strip().lower()}")

        if isinstance(tab_players, list):
            for item in tab_players:
                if not isinstance(item, dict):
                    continue

                entry_uuid = self._normalize_tab_uuid(item.get("uuid") or item.get("playerUUID") or item.get("id"))
                entry_name = self._normalize_tab_name(item.get("name") or item.get("playerName"))
                entry_display_name = self._normalize_tab_name(item.get("displayName"))
                entry_prefixed_name = self._normalize_tab_name(item.get("prefixedName") or item.get("teamDisplayName"))

                if entry_uuid is None and entry_name is None and entry_display_name is None and entry_prefixed_name is None:
                    continue

                entry = {
                    "uuid": entry_uuid,
                    "name": entry_name,
                    "displayName": entry_display_name,
                    "prefixedName": entry_prefixed_name,
                }
                normalized_entries.append(entry)

                if entry_uuid:
                    identity_keys.add(f"uuid:{entry_uuid}")
                if entry_name:
                    identity_keys.add(f"name:{entry_name.lower()}")

        report = {
            "timestamp": float(current_time),
            "submitPlayerId": submit_player_id,
            "players": normalized_entries,
            "identityKeys": sorted(identity_keys),
        }
        self.tab_player_reports[submit_player_id] = report
        return report

    def cleanup_tab_reports(self, current_time: Optional[float] = None) -> None:
        now = time.time() if current_time is None else float(current_time)
        for source_id in list(self.tab_player_reports.keys()):
            report = self.tab_player_reports.get(source_id)
            if not isinstance(report, dict):
                del self.tab_player_reports[source_id]
                continue

            if source_id not in self.connections:
                del self.tab_player_reports[source_id]
                continue

            ts = report.get("timestamp")
            if not isinstance(ts, (int, float)):
                del self.tab_player_reports[source_id]
                continue

            if now - float(ts) > self.TAB_REPORT_TIMEOUT_SEC:
                del self.tab_player_reports[source_id]

    def _build_same_server_groups(
        self,
        current_time: Optional[float] = None,
        allowed_sources: Optional[set[str]] = None,
    ) -> dict:
        self.cleanup_tab_reports(current_time)

        active_sources = [
            source_id for source_id in self.connections.keys()
            if isinstance(source_id, str) and source_id
        ]
        if isinstance(allowed_sources, set):
            active_sources = [source_id for source_id in active_sources if source_id in allowed_sources]
        if not active_sources:
            return {
                "sourceToGroup": {},
                "groups": [],
            }

        parent: Dict[str, str] = {source_id: source_id for source_id in active_sources}

        def find(source_id: str) -> str:
            root = parent.get(source_id, source_id)
            while parent.get(root, root) != root:
                root = parent[root]
            current = source_id
            while parent.get(current, current) != current:
                next_node = parent[current]
                parent[current] = root
                current = next_node
            return root

        def union(a: str, b: str) -> None:
            ra = find(a)
            rb = find(b)
            if ra == rb:
                return
            if ra <= rb:
                parent[rb] = ra
            else:
                parent[ra] = rb

        identity_sets: Dict[str, set[str]] = {}
        for source_id in active_sources:
            report = self.tab_player_reports.get(source_id)
            if not isinstance(report, dict):
                continue
            keys = report.get("identityKeys")
            if not isinstance(keys, list):
                continue
            normalized = {
                str(item) for item in keys
                if isinstance(item, str) and item
            }
            if normalized:
                identity_sets[source_id] = normalized

        for i in range(len(active_sources)):
            source_a = active_sources[i]
            keys_a = identity_sets.get(source_a)
            if not keys_a:
                continue
            for j in range(i + 1, len(active_sources)):
                source_b = active_sources[j]
                keys_b = identity_sets.get(source_b)
                if not keys_b:
                    continue
                if keys_a.intersection(keys_b):
                    union(source_a, source_b)

        grouped: Dict[str, list[str]] = {}
        for source_id in active_sources:
            root = find(source_id)
            grouped.setdefault(root, []).append(source_id)

        groups = []
        source_to_group: Dict[str, str] = {}
        for index, members in enumerate(sorted(grouped.values(), key=lambda item: item[0])):
            sorted_members = sorted(members)
            group_id = f"g{index + 1}"
            groups.append({
                "groupId": group_id,
                "members": sorted_members,
            })
            for source_id in sorted_members:
                source_to_group[source_id] = group_id

        return {
            "sourceToGroup": source_to_group,
            "groups": groups,
        }

    def get_allowed_sources_for_player(self, player_id: str) -> set[str]:
        if not isinstance(player_id, str) or not player_id:
            return set()

        player_room = self.get_player_room(player_id)
        room_sources = self.get_active_sources_in_room(player_room)
        if player_id not in room_sources:
            return room_sources

        if not self.same_server_filter_enabled:
            return room_sources

        grouping = self._build_same_server_groups(allowed_sources=room_sources)
        source_to_group = grouping.get("sourceToGroup", {})
        group_id = source_to_group.get(player_id)
        if not isinstance(group_id, str) or not group_id:
            return room_sources

        allowed = {
            source_id for source_id, source_group in source_to_group.items()
            if source_group == group_id
        }
        return allowed if allowed else room_sources

    def requires_scoped_delivery(self, player_id: str) -> bool:
        allowed_sources = self.get_allowed_sources_for_player(player_id)
        all_sources = {
            source_id for source_id in self.connections.keys()
            if isinstance(source_id, str) and source_id
        }
        return allowed_sources != all_sources

    @staticmethod
    def filter_state_map_by_sources(state_map: Dict[str, dict], allowed_sources: set[str]) -> Dict[str, dict]:
        if not allowed_sources:
            return {}

        filtered: Dict[str, dict] = {}
        for object_id, node in state_map.items():
            if not isinstance(node, dict):
                continue
            source_id = node.get("submitPlayerId")
            if isinstance(source_id, str) and source_id and source_id not in allowed_sources:
                continue
            filtered[object_id] = node
        return filtered

    @classmethod
    def build_admin_tactical_source_id(cls, room_code: str) -> str:
        normalized_room = cls.normalize_room_code(room_code)
        return f"{cls.ADMIN_TACTICAL_SOURCE_PREFIX}{normalized_room}"

    @classmethod
    def is_admin_tactical_source_id(cls, source_id: Optional[str]) -> bool:
        return isinstance(source_id, str) and source_id.startswith(cls.ADMIN_TACTICAL_SOURCE_PREFIX)

    @classmethod
    def parse_admin_tactical_room_code(cls, source_id: Optional[str]) -> Optional[str]:
        if not cls.is_admin_tactical_source_id(source_id):
            return None
        if not isinstance(source_id, str):
            return None
        room = source_id[len(cls.ADMIN_TACTICAL_SOURCE_PREFIX):]
        return cls.normalize_room_code(room)

    @classmethod
    def filter_waypoint_state_by_sources_and_room(
        cls,
        state_map: Dict[str, dict],
        allowed_sources: set[str],
        room_code: str,
    ) -> Dict[str, dict]:
        normalized_room = cls.normalize_room_code(room_code)
        filtered: Dict[str, dict] = {}

        for object_id, node in state_map.items():
            if not isinstance(node, dict):
                continue
            source_id = node.get("submitPlayerId")
            if isinstance(source_id, str) and source_id and source_id in allowed_sources:
                filtered[object_id] = node
                continue

            if not cls.is_admin_tactical_source_id(source_id):
                continue

            data = node.get("data") if isinstance(node.get("data"), dict) else {}
            data_room_raw = data.get("roomCode") if isinstance(data, dict) else None
            data_room = cls.normalize_room_code(data_room_raw) if isinstance(data_room_raw, str) and data_room_raw.strip() else None
            source_room = cls.parse_admin_tactical_room_code(source_id)
            final_room = data_room or source_room or normalized_room
            if final_room == normalized_room:
                filtered[object_id] = node

        return filtered

    def build_admin_tab_snapshot(self, room_code: Optional[str] = None) -> dict:
        self.cleanup_tab_reports()
        normalized_room = self.normalize_room_code(room_code)
        room_sources = self.get_active_sources_in_room(normalized_room)
        grouping = self._build_same_server_groups(allowed_sources=room_sources)
        reports = {}
        for source_id, report in self.tab_player_reports.items():
            if not isinstance(report, dict):
                continue
            if source_id not in room_sources:
                continue
            reports[source_id] = {
                "timestamp": report.get("timestamp"),
                "players": report.get("players", []),
            }
        return {
            "enabled": self.same_server_filter_enabled,
            "roomCode": normalized_room,
            "reports": reports,
            "groups": grouping.get("groups", []),
        }

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
        source: Optional[str] = None,
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

        normalized_source = "manual"
        if isinstance(source, str):
            source_text = source.strip().lower()
            if source_text in ("auto", "manual"):
                normalized_source = source_text

        mark = {
            "team": normalized_team,
            "color": normalized_color,
            "label": normalized_label,
            "source": normalized_source,
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

    @staticmethod
    def _clamp_report_interval_ticks(value, fallback: int) -> int:
        if isinstance(value, (int, float)):
            return max(1, min(int(value), 200))
        return max(1, min(int(fallback), 200))

    def compute_recommended_report_interval_ticks(self, broadcast_hz: float | None = None) -> int:
        hz = float(broadcast_hz if isinstance(broadcast_hz, (int, float)) else self.broadcast_hz)
        if hz >= 20.0:
            return 1
        if hz >= 10.0:
            return 2
        if hz >= 5.0:
            return 4
        return 10

    def negotiate_report_interval_ticks(self, player_id: str, preferred=None, minimum=None, maximum=None) -> int:
        suggested = self.compute_recommended_report_interval_ticks()
        caps = self.connection_caps.get(player_id, {}) if isinstance(player_id, str) else {}
        preferred_val = self._clamp_report_interval_ticks(preferred, caps.get("preferredReportIntervalTicks", suggested))
        minimum_val = self._clamp_report_interval_ticks(minimum, caps.get("minReportIntervalTicks", 1))
        maximum_val = self._clamp_report_interval_ticks(maximum, caps.get("maxReportIntervalTicks", 200))

        if minimum_val > maximum_val:
            minimum_val, maximum_val = maximum_val, minimum_val

        chosen = max(minimum_val, min(maximum_val, suggested))
        if preferred_val >= chosen and preferred_val <= maximum_val:
            chosen = preferred_val

        return max(minimum_val, min(maximum_val, chosen))

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
        # return text[:limit] + "...(truncated)"
        return text[:] # 先不截断，方便调试，后续再根据实际情况调整

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
    def touch_reports(
        report_map: Dict[str, Dict[str, dict]],
        object_ids: list[str],
        source_id: Optional[str],
        current_time: float,
    ) -> int:
        if not isinstance(object_ids, list) or not object_ids:
            return 0

        source_key = source_id if isinstance(source_id, str) else ""
        touched = 0
        for object_id in object_ids:
            if not isinstance(object_id, str) or not object_id:
                continue
            source_bucket = report_map.get(object_id)
            if not isinstance(source_bucket, dict):
                continue
            node = source_bucket.get(source_key)
            if not isinstance(node, dict):
                continue
            node["timestamp"] = float(current_time)
            touched += 1

        return touched

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

    @staticmethod
    def _normalize_protocol_version(version) -> str:
        text = str(version or "").strip()
        return text or "0.0.0"

    @classmethod
    def _parse_protocol_version(cls, version) -> tuple[int, int, int]:
        text = cls._normalize_protocol_version(version)
        if "." not in text:
            try:
                parsed_num = int(text)
                return 0, 0, max(parsed_num, 0)
            except ValueError:
                return 0, 0, 0

        parts = text.split(".")
        normalized_parts = []
        for part in parts[:3]:
            try:
                normalized_parts.append(max(int(part), 0))
            except ValueError:
                normalized_parts.append(0)

        while len(normalized_parts) < 3:
            normalized_parts.append(0)
        return tuple(normalized_parts)

    @classmethod
    def _protocol_at_least(cls, current, minimum) -> bool:
        return cls._parse_protocol_version(current) >= cls._parse_protocol_version(minimum)

    def mark_player_capability(
        self,
        player_id: str,
        protocol_version,
        preferred_report_interval_ticks=None,
        min_report_interval_ticks=None,
        max_report_interval_ticks=None,
    ) -> None:
        """记录客户端协议与广播节流状态。"""
        normalized_protocol = self._normalize_protocol_version(protocol_version)
        preferred_ticks = self._clamp_report_interval_ticks(
            preferred_report_interval_ticks,
            self.compute_recommended_report_interval_ticks(),
        )
        min_ticks = self._clamp_report_interval_ticks(min_report_interval_ticks, 1)
        max_ticks = self._clamp_report_interval_ticks(max_report_interval_ticks, 200)
        if min_ticks > max_ticks:
            min_ticks, max_ticks = max_ticks, min_ticks

        self.connection_caps[player_id] = {
            "protocol": normalized_protocol,
            "lastDigestSent": 0.0,
            "preferredReportIntervalTicks": preferred_ticks,
            "minReportIntervalTicks": min_ticks,
            "maxReportIntervalTicks": max_ticks,
            "negotiatedReportIntervalTicks": max(min_ticks, min(max_ticks, preferred_ticks)),
        }

    def update_broadcast_hz_for_congestion(self) -> float:
        load = len(self.connections)
        hz = float(self.DEFAULT_BROADCAST_HZ)
        for threshold, lowered_hz in self.CONGESTION_LEVELS:
            if load >= threshold:
                hz = float(lowered_hz)
                break
        self.broadcast_hz = max(self.MIN_BROADCAST_HZ, hz)
        return self.broadcast_hz

    def cleanup_timeouts(self) -> None:
        """按来源维度清理超时上报，避免脏数据长期占用最终视图。"""
        current_time = time.time()
        self.cleanup_tab_reports(current_time)
        removed_summary = {
            "players": 0,
            "entities": 0,
            "waypoints": 0,
        }
        removed_samples = []

        def effective_waypoint_timeout(node: dict) -> int:
            if not isinstance(node, dict):
                return self.WAYPOINT_TIMEOUT
            data = node.get("data")
            if not isinstance(data, dict):
                return self.WAYPOINT_TIMEOUT
            if bool(data.get("permanent")):
                return 315360000
            ttl = data.get("ttlSeconds")
            if isinstance(ttl, (int, float)):
                ttl_int = int(ttl)
                if ttl_int < 5:
                    return 5
                return min(ttl_int, 86400)
            return self.WAYPOINT_TIMEOUT

        def cleanup_report_map(report_name: str, report_map: Dict[str, Dict[str, dict]], timeout_resolver) -> None:
            for object_id in list(report_map.keys()):
                source_bucket = report_map.get(object_id)
                if not isinstance(source_bucket, dict):
                    del report_map[object_id]
                    continue

                for source_id in list(source_bucket.keys()):
                    node = source_bucket.get(source_id)
                    if not isinstance(node, dict):
                        del source_bucket[source_id]
                        removed_summary[report_name] += 1
                        if len(removed_samples) < self.TIMEOUT_LOG_SAMPLE_LIMIT:
                            removed_samples.append(
                                f"scope={report_name} objectId={object_id} sourceId={source_id!r} reason=invalid_node"
                            )
                        continue
                    timestamp = node.get("timestamp")
                    if not isinstance(timestamp, (int, float)):
                        del source_bucket[source_id]
                        removed_summary[report_name] += 1
                        if len(removed_samples) < self.TIMEOUT_LOG_SAMPLE_LIMIT:
                            removed_samples.append(
                                f"scope={report_name} objectId={object_id} sourceId={source_id!r} reason=invalid_timestamp"
                            )
                        continue

                    timeout_seconds = timeout_resolver(node)
                    age_seconds = current_time - float(timestamp)
                    if age_seconds > timeout_seconds:
                        owner_id = node.get("submitPlayerId") if isinstance(node, dict) else None
                        owner_online = isinstance(owner_id, str) and owner_id in self.connections
                        payload = node.get("data") if isinstance(node, dict) else None
                        payload_keys = sorted(payload.keys()) if isinstance(payload, dict) else []
                        del source_bucket[source_id]
                        removed_summary[report_name] += 1
                        if len(removed_samples) < self.TIMEOUT_LOG_SAMPLE_LIMIT:
                            removed_samples.append(
                                f"scope={report_name} objectId={object_id} sourceId={source_id!r} "
                                f"reason=timeout age={age_seconds:.2f}s timeout={timeout_seconds}s "
                                f"owner={owner_id} ownerOnline={owner_online} dataKeys={payload_keys}"
                            )

                if not source_bucket:
                    del report_map[object_id]

        cleanup_report_map("players", self.player_reports, lambda node: self.PLAYER_TIMEOUT)
        cleanup_report_map("entities", self.entity_reports, lambda node: self.ENTITY_TIMEOUT)
        cleanup_report_map("waypoints", self.waypoint_reports, effective_waypoint_timeout)

        total_removed = removed_summary["players"] + removed_summary["entities"] + removed_summary["waypoints"]
        if total_removed > 0 and (current_time - self._last_timeout_log_ts) >= self.TIMEOUT_LOG_INTERVAL_SEC:
            logger.debug(
                "Timeout cleanup removed sources "
                f"players={removed_summary['players']} entities={removed_summary['entities']} "
                f"waypoints={removed_summary['waypoints']} total={total_removed}"
            )
            for sample in removed_samples:
                logger.debug("  - %s", sample)
            self._last_timeout_log_ts = current_time

    def collect_preexpiry_refresh_requests(self, current_time: float) -> Dict[str, dict]:
        """
        收集“即将过期”的来源对象，供广播层向对应客户端发起 refresh_req。

        返回结构：
        {
          submit_player_id: {
            "players": [player_id, ...],
            "entities": [entity_id, ...],
          }
        }
        """
        requests: Dict[str, dict] = {}

        def maybe_add(scope: str, source_id: str, object_id: str) -> None:
            payload = requests.setdefault(source_id, {"players": [], "entities": []})
            items = payload[scope]
            if len(items) >= self.REFRESH_REQUEST_MAX_ITEMS_PER_SCOPE:
                return
            if object_id not in items:
                items.append(object_id)

        def scan_scope(scope: str, report_map: Dict[str, Dict[str, dict]], timeout_resolver) -> None:
            for object_id, source_bucket in report_map.items():
                if not isinstance(source_bucket, dict):
                    continue

                for source_id, node in source_bucket.items():
                    if not isinstance(source_id, str) or not source_id:
                        continue
                    if source_id not in self.connections:
                        continue
                    if not isinstance(node, dict):
                        continue

                    timestamp = node.get("timestamp")
                    if not isinstance(timestamp, (int, float)):
                        continue

                    timeout_seconds = timeout_resolver(node)
                    age_seconds = current_time - float(timestamp)
                    if age_seconds < 0:
                        continue

                    remaining = timeout_seconds - age_seconds
                    if remaining <= 0:
                        continue
                    if remaining > self.REFRESH_REQUEST_LEAD_SEC:
                        continue

                    last_sent = float(self._last_refresh_request_ts.get(source_id, 0.0))
                    if current_time - last_sent < self.REFRESH_REQUEST_COOLDOWN_SEC:
                        continue

                    maybe_add(scope, source_id, object_id)

        scan_scope("players", self.player_reports, lambda node: self.PLAYER_TIMEOUT)
        scan_scope("entities", self.entity_reports, lambda node: self.ENTITY_TIMEOUT)

        # 过滤空 payload
        filtered: Dict[str, dict] = {}
        for source_id, payload in requests.items():
            if payload["players"] or payload["entities"]:
                filtered[source_id] = payload

        return filtered

    def mark_refresh_request_sent(self, source_id: str, current_time: float) -> None:
        if isinstance(source_id, str) and source_id:
            self._last_refresh_request_ts[source_id] = current_time

    def can_send_refresh_request(self, source_id: str, current_time: float) -> bool:
        if not isinstance(source_id, str) or not source_id:
            return False
        last_sent = float(self._last_refresh_request_ts.get(source_id, 0.0))
        return (current_time - last_sent) >= self.REFRESH_REQUEST_COOLDOWN_SEC

    def remove_connection(self, player_id: str) -> None:
        """连接断开时，移除该来源在所有上报池中的数据。"""
        if player_id in self.connections:
            del self.connections[player_id]
        if player_id in self.connection_caps:
            del self.connection_caps[player_id]
        if player_id in self.connection_rooms:
            del self.connection_rooms[player_id]
        if player_id in self.tab_player_reports:
            del self.tab_player_reports[player_id]

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
