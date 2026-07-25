#!/usr/bin/env python3
"""Read and validate the Minecraft target manifest used by Gradle, Task and CI."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import shutil


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "gradle" / "minecraft-versions.properties"
GRADLE_PROPERTIES = ROOT / "gradle.properties"
REQUIRED_PROFILE_KEYS = (
    "minecraft_target_version",
    "minecraft_adapter_version",
    "game_java_version",
    "adapter_java_release",
    "gradle_runtime_java",
    "standalone_artifact",
    "slim_adapter_artifact",
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
    shared_java_release = properties.get("shared_java_release", "")
    if not shared_java_release.isdigit():
        raise SystemExit(f"Invalid shared_java_release: {shared_java_release!r}")
    shared_java = int(shared_java_release)
    default_target = properties.get("default_target")
    if default_target not in targets:
        raise SystemExit(f"default_target {default_target!r} is not in supported_targets")
    for target in targets:
        for key in REQUIRED_PROFILE_KEYS:
            qualified = f"{target}.{key}"
            if not properties.get(qualified):
                raise SystemExit(f"Missing target property: {qualified}")
        numeric_java_keys = ("game_java_version", "adapter_java_release", "gradle_runtime_java")
        for key in numeric_java_keys:
            value = properties[f"{target}.{key}"]
            if not value.isdigit():
                raise SystemExit(f"Invalid {key} for {target}: {value}")
        game_java = int(properties[f"{target}.game_java_version"])
        adapter_java = int(properties[f"{target}.adapter_java_release"])
        if adapter_java < shared_java:
            raise SystemExit(
                f"Adapter Java release {adapter_java} for {target} is below shared Java {shared_java}; "
                "a legacy runtime is required"
            )
        if game_java < adapter_java:
            raise SystemExit(
                f"Game Java {game_java} for {target} cannot load adapter Java {adapter_java}"
            )
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
    artifact_name = target_profile["standalone_artifact"].replace("{mod_version}", mod_version)
    expected_name = f"{base_name}-{minecraft_version}-{mod_version}.jar"
    if artifact_name != expected_name:
        raise SystemExit(f"Standalone artifact manifest mismatch for {target}: {artifact_name} != {expected_name}")
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


def java_home(properties: dict[str, str], target: str) -> pathlib.Path:
    required = profile(properties, target)["gradle_runtime_java"]
    preferred_name = f"JAVA{required}_HOME"
    current_java = shutil.which("java")
    current_home = str(pathlib.Path(current_java).resolve().parents[1]) if current_java else None
    candidates = [os.environ.get(preferred_name), os.environ.get("JAVA_HOME"), current_home]
    for candidate in candidates:
        if not candidate:
            continue
        home = pathlib.Path(candidate).expanduser().resolve()
        java = home / "bin" / "java"
        release = home / "release"
        if not java.is_file() or not release.is_file():
            continue
        match = re.search(r'^JAVA_VERSION="(\d+)', release.read_text(encoding="utf-8"), re.MULTILINE)
        if match and match.group(1) == required:
            return home
    raise SystemExit(
        f"Minecraft {target} requires a Java {required} Gradle runtime. "
        f"Set {preferred_name} (preferred) or JAVA_HOME to that JDK."
    )


def collect_universal() -> pathlib.Path:
    base_name = gradle_property("archives_base_name")
    mod_version = gradle_property("mod_version")
    artifact_name = f"{base_name}-all-{mod_version}.jar"
    source = ROOT / "universal" / "build" / "libs" / artifact_name
    if not source.is_file():
        raise SystemExit(f"Expected universal artifact not found: {source}")
    destination_dir = ROOT / "build-artifacts"
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / artifact_name
    shutil.copy2(source, destination)
    return destination


def prepare_build() -> None:
    artifact_dir = ROOT / "build-artifacts"
    if artifact_dir.is_dir():
        for artifact in artifact_dir.glob("*.jar"):
            artifact.unlink()
    adapter_dir = ROOT / "build" / "adapter-artifacts"
    if adapter_dir.is_dir():
        shutil.rmtree(adapter_dir)


def verify_release_set(properties: dict[str, str], targets: list[str]) -> list[pathlib.Path]:
    base_name = gradle_property("archives_base_name")
    mod_version = gradle_property("mod_version")
    expected = {
        profile(properties, target)["standalone_artifact"].replace("{mod_version}", mod_version)
        for target in targets
    }
    expected.add(f"{base_name}-all-{mod_version}.jar")
    artifact_dir = ROOT / "build-artifacts"
    actual = {path.name for path in artifact_dir.glob("*.jar")} if artifact_dir.is_dir() else set()
    if actual != expected:
        raise SystemExit(f"Release artifacts mismatch: expected={sorted(expected)}, actual={sorted(actual)}")
    return [artifact_dir / name for name in sorted(expected)]


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("list")
    subparsers.add_parser("default-target")
    subparsers.add_parser("ci-matrix")
    subparsers.add_parser("max-java")
    subparsers.add_parser("shared-java")
    subparsers.add_parser("packaging-java")
    subparsers.add_parser("prepare-build")
    subparsers.add_parser("collect-universal")
    subparsers.add_parser("verify-release-set")
    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("target")
    get_parser = subparsers.add_parser("get")
    get_parser.add_argument("target")
    get_parser.add_argument("key")
    collect_parser = subparsers.add_parser("collect")
    collect_parser.add_argument("target")
    java_home_parser = subparsers.add_parser("java-home")
    java_home_parser.add_argument("target")
    args = parser.parse_args()

    properties, targets = load_manifest()
    if args.command == "list":
        print("\n".join(targets))
    elif args.command == "default-target":
        print(properties["default_target"])
    elif args.command == "ci-matrix":
        include = [
            {
                "minecraft": target,
                "java": profile(properties, target)["gradle_runtime_java"],
                "game_java": profile(properties, target)["game_java_version"],
                "adapter_java": profile(properties, target)["adapter_java_release"],
            }
            for target in targets
        ]
        print(json.dumps({"include": include}, separators=(",", ":")))
    elif args.command == "max-java":
        print(max(int(profile(properties, target)["gradle_runtime_java"]) for target in targets))
    elif args.command == "shared-java":
        print(properties["shared_java_release"])
    elif args.command == "packaging-java":
        print(profile(properties, properties["default_target"])["gradle_runtime_java"])
    elif args.command == "prepare-build":
        prepare_build()
    elif args.command == "collect-universal":
        artifact = collect_universal()
        print(f"Created {artifact.relative_to(ROOT)}")
    elif args.command == "verify-release-set":
        for artifact in verify_release_set(properties, targets):
            print(artifact.relative_to(ROOT))
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
    elif args.command == "java-home":
        require_target(targets, args.target)
        print(java_home(properties, args.target))


if __name__ == "__main__":
    main()
