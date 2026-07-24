#!/usr/bin/env python3
"""Read and validate the Minecraft target manifest used by Gradle, Task and CI."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import shutil


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "gradle" / "minecraft-versions.properties"
GRADLE_PROPERTIES = ROOT / "gradle.properties"
REQUIRED_PROFILE_KEYS = (
    "minecraft_target_version",
    "minecraft_adapter_version",
    "java_version",
    "loom_version",
    "loader_version",
    "fabric_api_version",
    "fabric_api_mod_id",
    "mappings_mode",
    "modmenu_version",
    "journeymap_dependency",
)


def read_properties(path: pathlib.Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            raise SystemExit(f"Invalid property in {path}: {raw_line}")
        result[key.strip()] = value.strip()
    return result


def load_manifest() -> tuple[dict[str, str], list[str]]:
    properties = read_properties(MANIFEST)
    targets = [value.strip() for value in properties.get("supported_targets", "").split(",") if value.strip()]
    if not targets:
        raise SystemExit(f"No supported_targets declared in {MANIFEST}")
    if len(set(targets)) != len(targets):
        raise SystemExit(f"Duplicate supported_targets in {MANIFEST}: {targets}")
    default_target = properties.get("default_target")
    if default_target not in targets:
        raise SystemExit(f"default_target {default_target!r} is not in supported_targets")
    for target in targets:
        for key in REQUIRED_PROFILE_KEYS:
            qualified = f"{target}.{key}"
            if not properties.get(qualified):
                raise SystemExit(f"Missing target property: {qualified}")
        java_version = properties[f"{target}.java_version"]
        if not java_version.isdigit():
            raise SystemExit(f"Invalid Java version for {target}: {java_version}")
        adapter_dir = ROOT / "fabric" / "src" / "version" / properties[f"{target}.minecraft_adapter_version"]
        if not adapter_dir.is_dir():
            raise SystemExit(f"Adapter directory does not exist for {target}: {adapter_dir}")
    return properties, targets


def profile(properties: dict[str, str], target: str) -> dict[str, str]:
    prefix = f"{target}."
    return {key[len(prefix):]: value for key, value in properties.items() if key.startswith(prefix)}


def require_target(targets: list[str], target: str) -> None:
    if target not in targets:
        raise SystemExit(f"Unsupported target {target!r}; expected one of {targets}")


def gradle_property(name: str) -> str:
    text = GRADLE_PROPERTIES.read_text(encoding="utf-8")
    match = re.search(rf"^\s*{re.escape(name)}\s*=\s*(.+?)\s*$", text, re.MULTILINE)
    if not match:
        raise SystemExit(f"Missing {name} in {GRADLE_PROPERTIES}")
    return match.group(1)


def collect_artifact(properties: dict[str, str], target: str) -> list[pathlib.Path]:
    target_profile = profile(properties, target)
    default_target = properties["default_target"]
    base_name = gradle_property("archives_base_name")
    mod_version = gradle_property("mod_version")
    minecraft_version = target_profile["minecraft_target_version"]
    artifact_name = f"{base_name}-{minecraft_version}-{mod_version}.jar"
    build_root = ROOT / "fabric" / "build"
    libs = build_root / "libs" if target == default_target else build_root / target / "libs"
    source = libs / artifact_name
    if not source.is_file():
        available = sorted(path.name for path in libs.glob("*.jar")) if libs.is_dir() else []
        raise SystemExit(f"Expected artifact not found: {source}; available={available}")
    destination_dir = ROOT / "build-artifacts"
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / artifact_name
    shutil.copy2(source, destination)
    return [destination]


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("list")
    subparsers.add_parser("ci-matrix")
    subparsers.add_parser("max-java")
    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("target")
    get_parser = subparsers.add_parser("get")
    get_parser.add_argument("target")
    get_parser.add_argument("key")
    collect_parser = subparsers.add_parser("collect")
    collect_parser.add_argument("target")
    args = parser.parse_args()

    properties, targets = load_manifest()
    if args.command == "list":
        print("\n".join(targets))
    elif args.command == "ci-matrix":
        include = [
            {
                "minecraft": target,
                "java": profile(properties, target)["java_version"],
            }
            for target in targets
        ]
        print(json.dumps({"include": include}, separators=(",", ":")))
    elif args.command == "max-java":
        print(max(int(profile(properties, target)["java_version"]) for target in targets))
    elif args.command == "validate":
        require_target(targets, args.target)
    elif args.command == "get":
        require_target(targets, args.target)
        value = profile(properties, args.target).get(args.key)
        if value is None:
            raise SystemExit(f"Target {args.target} has no property {args.key}")
        print(value)
    elif args.command == "collect":
        require_target(targets, args.target)
        for artifact in collect_artifact(properties, args.target):
            print(f"Created {artifact.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
