#!/usr/bin/env python3
"""Run a development client until its strict Adapter TCK report is available."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import signal
import subprocess
import sys
import time


def read_report(path: pathlib.Path) -> dict[str, object] | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return None


def stop_process_group(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True, type=pathlib.Path)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a client command is required after --")

    args.report.unlink(missing_ok=True)
    process = subprocess.Popen(command, start_new_session=True)
    deadline = time.monotonic() + args.timeout
    try:
        while time.monotonic() < deadline:
            report = read_report(args.report)
            if report is not None:
                if report.get("passed") is not True:
                    print(f"Adapter TCK failed: {report}", file=sys.stderr)
                    return 1
                print(
                    "Adapter TCK passed for "
                    f"{report.get('minecraftVersion')} / {report.get('adapterVersion')}"
                )
                return 0
            exit_code = process.poll()
            if exit_code is not None:
                print(
                    f"Client exited with code {exit_code} before writing {args.report}",
                    file=sys.stderr,
                )
                return 1
            time.sleep(0.25)
        print(f"Timed out waiting for Adapter TCK report {args.report}", file=sys.stderr)
        return 1
    finally:
        stop_process_group(process)


if __name__ == "__main__":
    raise SystemExit(main())
