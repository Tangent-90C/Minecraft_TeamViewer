from contextlib import asynccontextmanager
from pathlib import Path
import os

import httpx
from fastapi import APIRouter, FastAPI, Request
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, Response, StreamingResponse
from starlette.background import BackgroundTask

nodemc_router = APIRouter()

NODEMC_TARGET_ORIGIN = os.getenv("NODEMC_TARGET_ORIGIN", "https://map.nodemc.cc")
PROJECT_ROOT = Path(__file__).resolve().parent.parent
OVERLAY_SCRIPT_PATH = PROJECT_ROOT / "static" / "nodemc_map_projection.js"


@asynccontextmanager
async def nodemc_lifespan(app: FastAPI):
    timeout = httpx.Timeout(connect=10.0, read=30.0, write=30.0, pool=10.0)
    limits = httpx.Limits(max_connections=200, max_keepalive_connections=40, keepalive_expiry=45.0)
    app.state.nodemc_http_client = httpx.AsyncClient(
        timeout=timeout,
        limits=limits,
        follow_redirects=True,
        http2=True,
    )
    try:
        yield
    finally:
        client = getattr(app.state, "nodemc_http_client", None)
        if client is not None:
            await client.aclose()


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


@nodemc_router.get("/nodemc", response_class=HTMLResponse)
@nodemc_router.get("/nodemc/", response_class=HTMLResponse)
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


@nodemc_router.get("/overlay/nodemc_map_projection.js")
async def overlay_script() -> FileResponse:
    return FileResponse(OVERLAY_SCRIPT_PATH, media_type="application/javascript; charset=utf-8")


@nodemc_router.api_route("/nodemc-proxy/{proxy_path:path}", methods=["GET", "HEAD"])
async def nodemc_proxy(request: Request, proxy_path: str = "") -> Response:
    upstream_url = _build_nodemc_proxy_url(proxy_path, request.url.query)
    client: httpx.AsyncClient | None = getattr(request.app.state, "nodemc_http_client", None)
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
