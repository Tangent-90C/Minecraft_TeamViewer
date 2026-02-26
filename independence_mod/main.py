import json
import logging
import os
import time
from pathlib import Path

import httpx
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, Response, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import ValidationError
from starlette.background import BackgroundTask

from server.broadcaster import Broadcaster
from server.models import EntityData, PlayerData, WaypointData
from server.state import ServerState


def configure_logging() -> None:
    if logging.getLogger().handlers:
        return

    level_name = os.getenv("TEAMVIEWER_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


configure_logging()
logger = logging.getLogger("teamviewer.main")

# 进程级单例：承载内存态与广播能力。
state = ServerState()
broadcaster = Broadcaster(state)

# HTTP/WS 入口层：仅做协议收发与调度，不承载核心仲裁逻辑。
app = FastAPI()
app.mount("/admin", StaticFiles(directory="static", html=True), name="admin")


@app.on_event("startup")
async def startup_event() -> None:
    timeout = httpx.Timeout(connect=10.0, read=30.0, write=30.0, pool=10.0)
    limits = httpx.Limits(max_connections=200, max_keepalive_connections=40, keepalive_expiry=45.0)
    app.state.nodemc_http_client = httpx.AsyncClient(
        timeout=timeout,
        limits=limits,
        follow_redirects=True,
        http2=True,
    )


@app.on_event("shutdown")
async def shutdown_event() -> None:
    client = getattr(app.state, "nodemc_http_client", None)
    if client is not None:
        await client.aclose()

PROJECT_ROOT = Path(__file__).resolve().parent
NODEMC_TARGET_ORIGIN = os.getenv("NODEMC_TARGET_ORIGIN", "https://map.nodemc.cc")
NODEMC_PROXY_PREFIX = "/nodemc-proxy"


def _build_nodemc_proxy_url(path: str, query: str = "") -> str:
    clean_path = path or ""
    if clean_path.startswith("/"):
        clean_path = clean_path[1:]

    base = NODEMC_TARGET_ORIGIN.rstrip("/") + "/"
    url = base + clean_path
    if query:
        url = f"{url}?{query}"
    return url


def _inject_overlay_script(html_text: str) -> str:
    injection = "<script src=\"/overlay/nodemc_map_projection.js\"></script>"
    lower = html_text.lower()

    if "</body>" in lower:
        idx = lower.rfind("</body>")
        return html_text[:idx] + injection + html_text[idx:]
    if "</html>" in lower:
        idx = lower.rfind("</html>")
        return html_text[:idx] + injection + html_text[idx:]
    return html_text + injection


@app.get("/nodemc", response_class=HTMLResponse)
@app.get("/nodemc/", response_class=HTMLResponse)
async def nodemc_entry() -> HTMLResponse:
    html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>NodeMC 地图代理入口</title>
  <style>
    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif; background: #020617; color: #e2e8f0; }
    .top { padding: 10px 12px; border-bottom: 1px solid rgba(148,163,184,.25); background: rgba(15,23,42,.92); }
    a { color: #93c5fd; text-decoration: none; }
    iframe { border: 0; width: 100vw; height: calc(100vh - 48px); display: block; background: #0f172a; }
    .small { color: #94a3b8; font-size: 12px; }
  </style>
</head>
<body>
  <div class="top">
        <div>NodeMC 地图代理（已自动注入 overlay 脚本）</div>
        <div class="small">如果地图白屏，点击 <a href="/nodemc-proxy/" target="_blank" rel="noopener">打开代理地图页</a></div>
  </div>
    <iframe src="/nodemc-proxy/"></iframe>
</body>
</html>"""
    return HTMLResponse(content=html)


@app.get("/overlay/nodemc_map_projection.js")
async def overlay_script() -> FileResponse:
    script_path = PROJECT_ROOT / "static" / "nodemc_map_projection.js"
    return FileResponse(script_path, media_type="application/javascript; charset=utf-8")


@app.api_route("/nodemc-proxy/{proxy_path:path}", methods=["GET", "HEAD"])
async def nodemc_proxy(request: Request, proxy_path: str = "") -> Response:
    upstream_url = _build_nodemc_proxy_url(proxy_path, request.url.query)
    client: httpx.AsyncClient | None = getattr(app.state, "nodemc_http_client", None)
    if client is None:
        return JSONResponse(
            {
                "status": "error",
                "message": "nodemc proxy client not initialized",
            },
            status_code=503,
        )

    request_headers = {
        "User-Agent": request.headers.get("user-agent", "Mozilla/5.0"),
        "Accept": request.headers.get("accept", "*/*"),
        "Accept-Language": request.headers.get("accept-language", "zh-CN,zh;q=0.9,en;q=0.8"),
        "Referer": NODEMC_TARGET_ORIGIN.rstrip("/") + "/",
    }

    pass_through_header_names = [
        "cookie",
        "range",
        "if-none-match",
        "if-modified-since",
        "if-match",
        "if-range",
        "accept-encoding",
    ]
    for name in pass_through_header_names:
        value = request.headers.get(name)
        if value:
            request_headers[name] = value

    cookie = request.headers.get("cookie")
    if cookie:
        request_headers["Cookie"] = cookie

    try:
        upstream_request = client.build_request(
            method=request.method,
            url=upstream_url,
            headers=request_headers,
        )
        upstream_response = await client.send(upstream_request, stream=True)

        status = int(upstream_response.status_code)
        content_type = upstream_response.headers.get("content-type", "application/octet-stream")

        response_headers = {}
        passthrough_response_headers = [
            "cache-control",
            "etag",
            "last-modified",
            "content-encoding",
            "content-length",
            "content-range",
            "accept-ranges",
            "expires",
            "vary",
            "location",
        ]
        for name in passthrough_response_headers:
            value = upstream_response.headers.get(name)
            if value:
                response_headers[name.title()] = value

        if request.method == "HEAD":
            await upstream_response.aclose()
            return Response(
                content=b"",
                status_code=status,
                media_type=content_type,
                headers=response_headers,
            )

        if "text/html" in content_type.lower():
            body = await upstream_response.aread()
            await upstream_response.aclose()
            text = body.decode("utf-8", errors="replace")
            text = _inject_overlay_script(text)
            encoded = text.encode("utf-8")
            response_headers.pop("Content-Length", None)
            response_headers.pop("Content-Encoding", None)
            return Response(
                content=encoded,
                status_code=status,
                media_type="text/html; charset=utf-8",
                headers=response_headers,
            )

        return StreamingResponse(
            upstream_response.aiter_raw(),
            status_code=status,
            media_type=content_type,
            headers=response_headers,
            background=BackgroundTask(upstream_response.aclose),
        )
    except httpx.HTTPStatusError as e:
        return JSONResponse(
            {
                "status": "error",
                "message": "nodemc upstream http status error",
                "detail": str(e),
                "upstream": upstream_url,
            },
            status_code=502,
        )
    except httpx.TimeoutException as e:
        return JSONResponse(
            {
                "status": "error",
                "message": "nodemc proxy timeout",
                "detail": str(e),
                "upstream": upstream_url,
            },
            status_code=504,
        )
    except httpx.HTTPError as e:
        return JSONResponse(
            {
                "status": "error",
                "message": "nodemc proxy http error",
                "detail": str(e),
                "upstream": upstream_url,
            },
            status_code=502,
        )
    except Exception as e:
        return JSONResponse(
            {
                "status": "error",
                "message": "nodemc proxy failed",
                "detail": str(e),
                "upstream": upstream_url,
            },
            status_code=502,
        )


@app.websocket("/adminws")
async def admin_ws(websocket: WebSocket):
    """管理端订阅通道：用于查看服务端实时快照。"""
    await websocket.accept()
    admin_id = str(id(websocket))
    state.admin_connections[admin_id] = websocket
    try:
        await broadcaster.broadcast_snapshot()
        while True:
            raw_text = await websocket.receive_text()
            if not raw_text:
                continue

            try:
                message = json.loads(raw_text)
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({
                    "type": "admin_ack",
                    "ok": False,
                    "error": "invalid_json",
                }, separators=(",", ":")))
                continue

            if not isinstance(message, dict):
                await websocket.send_text(json.dumps({
                    "type": "admin_ack",
                    "ok": False,
                    "error": "invalid_payload",
                }, separators=(",", ":")))
                continue

            msg_type = str(message.get("type") or "").strip()

            if msg_type in ("ping", "health"):
                await websocket.send_text(json.dumps({
                    "type": "pong",
                    "serverTime": time.time(),
                    "revision": state.revision,
                }, separators=(",", ":")))
                continue

            if msg_type == "command_player_mark_set":
                target_player_id = message.get("playerId")
                updated_mark = state.set_player_mark(
                    target_player_id,
                    message.get("team"),
                    message.get("color"),
                    message.get("label"),
                )

                if updated_mark is None:
                    await websocket.send_text(json.dumps({
                        "type": "admin_ack",
                        "ok": False,
                        "error": "invalid_player_id",
                    }, separators=(",", ":")))
                    continue

                await websocket.send_text(json.dumps({
                    "type": "admin_ack",
                    "ok": True,
                    "action": "command_player_mark_set",
                    "playerId": str(target_player_id).strip() if isinstance(target_player_id, str) else target_player_id,
                    "mark": updated_mark,
                }, separators=(",", ":")))
                await broadcaster.broadcast_snapshot()
                continue

            if msg_type == "command_player_mark_clear":
                target_player_id = message.get("playerId")
                removed = state.clear_player_mark(target_player_id)
                await websocket.send_text(json.dumps({
                    "type": "admin_ack",
                    "ok": bool(removed),
                    "action": "command_player_mark_clear",
                    "playerId": target_player_id,
                    "error": None if removed else "mark_not_found",
                }, separators=(",", ":")))
                if removed:
                    await broadcaster.broadcast_snapshot()
                continue

            if msg_type == "command_player_mark_clear_all":
                removed_count = state.clear_all_player_marks()
                await websocket.send_text(json.dumps({
                    "type": "admin_ack",
                    "ok": True,
                    "action": "command_player_mark_clear_all",
                    "removedCount": removed_count,
                }, separators=(",", ":")))
                await broadcaster.broadcast_snapshot()
                continue

            if msg_type == "command_same_server_filter_set":
                enabled = bool(message.get("enabled"))
                state.same_server_filter_enabled = enabled
                await websocket.send_text(json.dumps({
                    "type": "admin_ack",
                    "ok": True,
                    "action": "command_same_server_filter_set",
                    "enabled": state.same_server_filter_enabled,
                }, separators=(",", ":")))
                await broadcaster.broadcast_updates(force_full_to_delta=True)
                continue

            await websocket.send_text(json.dumps({
                "type": "admin_ack",
                "ok": False,
                "error": "unsupported_command",
                "command": msg_type,
            }, separators=(",", ":")))
    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.exception("Admin websocket error: %s", e)
    finally:
        if admin_id in state.admin_connections:
            del state.admin_connections[admin_id]


@app.websocket("/playeresp")
async def websocket_endpoint(websocket: WebSocket):
    """
    玩家数据主通道。

    职责：
    1) 接收客户端上报（全量/增量）；
    2) 做入参校验并写入来源报告池；
    3) 触发统一广播（由 Broadcaster 完成聚合后下发）。
    """
    await websocket.accept()
    submit_player_id = None

    try:
        while True:
            message = await websocket.receive_text()
            try:
                data = json.loads(message)
            except json.JSONDecodeError as e:
                logger.debug("Error decoding JSON message: %s", e)
                continue

            submit_player_id = data.get("submitPlayerId")
            message_type = data.get("type")

            # 握手：建立能力协商（协议版本、是否支持 delta）。
            if message_type == "handshake":
                if submit_player_id:
                    state.connections[submit_player_id] = websocket
                    client_protocol = int(data.get("protocolVersion", 1))
                    client_delta = bool(data.get("supportsDelta", False))
                    state.mark_player_capability(submit_player_id, client_protocol, client_delta)

                    logger.info("Client %s connected (protocol %s)", submit_player_id, client_protocol)
                    ack = {
                        "type": "handshake_ack",
                        "ready": True,
                        "protocolVersion": state.PROTOCOL_V2,
                        "deltaEnabled": state.is_delta_client(submit_player_id),
                        "digestIntervalSec": state.DIGEST_INTERVAL_SEC,
                        "rev": state.revision,
                    }
                    await websocket.send_text(json.dumps(ack, separators=(",", ":")))

                    await broadcaster.broadcast_updates(force_full_to_delta=state.is_delta_client(submit_player_id))
                continue

            if submit_player_id and submit_player_id not in state.connections:
                # 兼容旧客户端：未显式握手也可接入，但按 legacy 能力处理。
                state.connections[submit_player_id] = websocket
                state.mark_player_capability(submit_player_id, 1, False)
                logger.info("Client %s connected (legacy)", submit_player_id)

            if message_type == "players_update":
                # 玩家全量：语义为“该来源本轮玩家状态完整快照”。
                current_time = time.time()
                for pid, player_data in data.get("players", {}).items():
                    try:
                        validated_data = PlayerData(**player_data)
                        normalized = validated_data.model_dump()
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.player_reports, pid, submit_player_id, node)
                    except Exception as e:
                        logger.warning("Error validating player data for %s: %s", pid, e)

                await broadcaster.broadcast_updates()
                continue

            if message_type == "tab_players_update":
                if isinstance(submit_player_id, str) and submit_player_id:
                    current_time = time.time()
                    tab_players = data.get("tabPlayers", [])
                    state.upsert_tab_player_report(submit_player_id, tab_players, current_time)
                    await broadcaster.broadcast_snapshot()
                continue

            if message_type == "players_patch":
                # 玩家增量：基于该来源已有快照做 merge 后再校验。
                current_time = time.time()
                upsert = data.get("upsert", {})
                delete = data.get("delete", [])
                missing_baseline_players = []

                for pid, player_data in upsert.items():
                    source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                    existing_node = state.player_reports.get(pid, {}).get(source_key)
                    try:
                        normalized = state.merge_patch_and_validate(PlayerData, existing_node, player_data)
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.player_reports, pid, submit_player_id, node)
                    except ValidationError as e:
                        missing_fields = state.missing_fields_from_validation_error(e)
                        existing_data = existing_node.get("data") if isinstance(existing_node, dict) else None
                        existing_keys = sorted(existing_data.keys()) if isinstance(existing_data, dict) else []
                        if not isinstance(existing_data, dict):
                            missing_baseline_players.append(pid)
                        logger.warning(
                            "Player patch validation failed "
                            f"pid={pid} submitPlayerId={submit_player_id} sourceKey={source_key!r} "
                            f"hasExistingSnapshot={bool(isinstance(existing_data, dict))} "
                            f"missingFields={missing_fields or '[]'} "
                            f"existingKeys={existing_keys} payload={state.payload_preview(player_data)} "
                            f"errors={state.payload_preview(e.errors(), 480)}"
                        )
                    except Exception as e:
                        logger.exception(
                            "Unexpected error validating player patch "
                            f"pid={pid} submitPlayerId={submit_player_id} payload={state.payload_preview(player_data)}: {e}"
                        )

                if isinstance(delete, list):
                    for pid in delete:
                        if not isinstance(pid, str):
                            continue
                        state.delete_report(state.player_reports, pid, submit_player_id)

                if missing_baseline_players and isinstance(submit_player_id, str) and submit_player_id:
                    await broadcaster.send_refresh_request_to_source(
                        submit_player_id,
                        players=missing_baseline_players,
                        entities=[],
                        reason="missing_baseline_patch",
                        bypass_cooldown=False,
                    )

                await broadcaster.broadcast_updates()
                continue

            if message_type == "entities_update":
                # 实体全量：先清理该来源旧实体，再写入本轮实体列表。
                current_time = time.time()
                player_entities = data.get("entities", {})
                source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                for entity_id in list(state.entity_reports.keys()):
                    source_bucket = state.entity_reports.get(entity_id, {})
                    if source_key in source_bucket:
                        state.delete_report(state.entity_reports, entity_id, submit_player_id)

                for entity_id, entity_data in player_entities.items():
                    try:
                        validated_data = EntityData(**entity_data)
                        normalized = validated_data.model_dump()
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.entity_reports, entity_id, submit_player_id, node)
                    except Exception as e:
                        logger.warning("Error validating entity data for %s: %s", entity_id, e)

                await broadcaster.broadcast_updates()
                continue

            if message_type == "entities_patch":
                # 实体增量：仅修改当前来源 bucket，不影响其他来源。
                current_time = time.time()
                upsert = data.get("upsert", {})
                delete = data.get("delete", [])
                missing_baseline_entities = []

                for entity_id, entity_data in upsert.items():
                    source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                    existing_node = state.entity_reports.get(entity_id, {}).get(source_key)
                    try:
                        normalized = state.merge_patch_and_validate(EntityData, existing_node, entity_data)
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.entity_reports, entity_id, submit_player_id, node)
                    except ValidationError as e:
                        missing_fields = state.missing_fields_from_validation_error(e)
                        existing_data = existing_node.get("data") if isinstance(existing_node, dict) else None
                        existing_keys = sorted(existing_data.keys()) if isinstance(existing_data, dict) else []
                        if not isinstance(existing_data, dict):
                            missing_baseline_entities.append(entity_id)
                        logger.warning(
                            "Entity patch validation failed "
                            f"entityId={entity_id} submitPlayerId={submit_player_id} sourceKey={source_key!r} "
                            f"hasExistingSnapshot={bool(isinstance(existing_data, dict))} "
                            f"missingFields={missing_fields or '[]'} "
                            f"existingKeys={existing_keys} payload={state.payload_preview(entity_data)} "
                            f"errors={state.payload_preview(e.errors(), 480)}"
                        )
                    except Exception as e:
                        logger.exception(
                            "Unexpected error validating entity patch "
                            f"entityId={entity_id} submitPlayerId={submit_player_id} "
                            f"payload={state.payload_preview(entity_data)}: {e}"
                        )

                if isinstance(delete, list):
                    for entity_id in delete:
                        if not isinstance(entity_id, str):
                            continue
                        state.delete_report(state.entity_reports, entity_id, submit_player_id)

                if missing_baseline_entities and isinstance(submit_player_id, str) and submit_player_id:
                    await broadcaster.send_refresh_request_to_source(
                        submit_player_id,
                        players=[],
                        entities=missing_baseline_entities,
                        reason="missing_baseline_patch",
                        bypass_cooldown=False,
                    )

                await broadcaster.broadcast_updates()
                continue

            if message_type == "waypoints_update":
                # 路标上报：支持 quick 类型数量约束。
                current_time = time.time()
                player_waypoints = data.get("waypoints", {})
                for waypoint_id, waypoint_data in player_waypoints.items():
                    try:
                        validated_data = WaypointData(**waypoint_data)
                        normalized = validated_data.model_dump()

                        if normalized.get("waypointKind") == "quick":
                            max_quick_marks = normalized.get("maxQuickMarks")
                            if isinstance(max_quick_marks, (int, float)):
                                max_quick_marks = max(1, min(int(max_quick_marks), 100))
                            elif bool(normalized.get("replaceOldQuick")):
                                max_quick_marks = 1
                            else:
                                max_quick_marks = None

                            if max_quick_marks is not None:
                                # 限流策略：超出上限时按最旧 quick 路标淘汰。
                                old_quick_waypoints = [
                                    (wid, source_bucket[submit_player_id])
                                    for wid, source_bucket in list(state.waypoint_reports.items())
                                    if wid != waypoint_id
                                    and isinstance(source_bucket, dict)
                                    and submit_player_id in source_bucket
                                    and isinstance(source_bucket[submit_player_id], dict)
                                    and isinstance(source_bucket[submit_player_id].get("data"), dict)
                                    and source_bucket[submit_player_id]["data"].get("waypointKind") == "quick"
                                ]

                                remove_count = len(old_quick_waypoints) - max_quick_marks + 1
                                if remove_count > 0:
                                    old_quick_waypoints.sort(key=lambda item: state.node_timestamp(item[1]))
                                    for old_id, _ in old_quick_waypoints[:remove_count]:
                                        state.delete_report(state.waypoint_reports, old_id, submit_player_id)

                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.waypoint_reports, waypoint_id, submit_player_id, node)
                    except Exception as e:
                        logger.warning("Error validating waypoint data for %s: %s", waypoint_id, e)

                await broadcaster.broadcast_updates()
                continue

            if message_type == "waypoints_delete":
                # 路标删除：仅删除当前来源提交的目标路标。
                waypoint_ids = data.get("waypointIds", [])
                if not isinstance(waypoint_ids, list):
                    waypoint_ids = []

                for waypoint_id in waypoint_ids:
                    if not isinstance(waypoint_id, str):
                        continue
                    state.delete_report(state.waypoint_reports, waypoint_id, submit_player_id)

                await broadcaster.broadcast_updates()
                continue

            if message_type == "waypoints_entity_death_cancel":
                # 实体死亡撤销：清理 targetEntityId 命中的 entity 类型路标。
                target_entity_ids = data.get("targetEntityIds", [])
                if not isinstance(target_entity_ids, list):
                    target_entity_ids = []

                target_entity_id_set = {
                    entity_id for entity_id in target_entity_ids
                    if isinstance(entity_id, str) and entity_id.strip()
                }

                if target_entity_id_set:
                    for waypoint_id in list(state.waypoint_reports.keys()):
                        source_bucket = state.waypoint_reports.get(waypoint_id)
                        if not isinstance(source_bucket, dict):
                            continue

                        for source_id in list(source_bucket.keys()):
                            node = source_bucket.get(source_id)
                            if not isinstance(node, dict):
                                continue
                            payload = node.get("data")
                            if not isinstance(payload, dict):
                                continue
                            if payload.get("targetType") != "entity":
                                continue
                            if payload.get("targetEntityId") not in target_entity_id_set:
                                continue
                            state.delete_report(state.waypoint_reports, waypoint_id, source_id)

                    await broadcaster.broadcast_updates()
                continue

            if message_type == "resync_req" and submit_player_id:
                # 客户端主动请求全量重同步。
                try:
                    await broadcaster.send_snapshot_full_to_player(submit_player_id)
                except Exception as e:
                    logger.warning("Error sending snapshot_full to %s: %s", submit_player_id, e)
                continue

    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.exception("Error handling player message: %s", e)
    finally:
        if submit_player_id:
            state.remove_connection(submit_player_id)
            logger.info("Client %s disconnected", submit_player_id)
            await broadcaster.broadcast_updates()


@app.get("/health")
async def health_check():
    """健康检查：用于探活。"""
    return JSONResponse({"status": "ok"})


@app.get("/snapshot")
async def snapshot():
    """调试快照：返回当前最终视图与连接状态。"""
    current_time = time.time()
    return JSONResponse({
        "server_time": current_time,
        "players": dict(state.players),
        "entities": dict(state.entities),
        "waypoints": dict(state.waypoints),
        "playerMarks": dict(state.player_marks),
        "tabState": state.build_admin_tab_snapshot(),
        "connections": list(state.connections.keys()),
        "connections_count": len(state.connections),
        "revision": state.revision,
        "digests": state.build_digests(),
    })


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8765, ws_per_message_deflate=True)
