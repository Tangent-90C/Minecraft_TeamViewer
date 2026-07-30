#!/usr/bin/env python3
"""Validate and query the Minecraft release/build matrix shared by Gradle and CI."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import shutil
from dataclasses import dataclass


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "gradle" / "minecraft-versions.properties"
GRADLE_PROPERTIES = ROOT / "gradle.properties"

FABRIC_PROFILE_KEYS = (
    "minecraft_target_version",
    "covers",
    "minecraft_version_range",
    "adapter_version",
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
)
NEOFORGE_PROFILE_KEYS = (
    "minecraft_target_version",
    "minecraft_version_range",
    "neoforge_version_range",
    "adapter_version",
    "game_java_version",
    "adapter_java_release",
    "gradle_runtime_java",
    "standalone_artifact",
    "neoforge_version",
    "build_kind",
    "stability",
)


@dataclass(frozen=True)
class Matrix:
    properties: dict[str, str]
    official: tuple[str, ...]
    fabric: tuple[str, ...]
    neoforge: tuple[str, ...]
    neoforge_modern: tuple[str, ...]
    neoforge_legacy: tuple[str, ...]


def read_properties(path: pathlib.Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            raise SystemExit(f"Invalid property in {path}: {raw_line}")
        key = key.strip()
        if key in result:
            raise SystemExit(f"Duplicate property in {path}: {key}")
        result[key] = value.strip()
    return result


def csv_property(properties: dict[str, str], key: str) -> tuple[str, ...]:
    values = tuple(value.strip() for value in properties.get(key, "").split(",") if value.strip())
    if not values:
        raise SystemExit(f"No {key} declared in {MANIFEST}")
    if len(set(values)) != len(values):
        raise SystemExit(f"Duplicate entries in {key}: {values}")
    return values


def profile(properties: dict[str, str], loader: str, target: str) -> dict[str, str]:
    prefix = f"{loader}.{target}."
    return {key[len(prefix):]: value for key, value in properties.items() if key.startswith(prefix)}


def fabric_runtime_profile(properties: dict[str, str], release: str) -> dict[str, str]:
    prefix = f"fabric_runtime.{release}."
    return {key[len(prefix):]: value for key, value in properties.items() if key.startswith(prefix)}


def expected_java(minecraft: str) -> int:
    if minecraft.startswith("26."):
        return 25
    parts = minecraft.split(".")
    if parts[0] != "1" or len(parts) < 2:
        raise SystemExit(f"Unrecognised release version: {minecraft}")
    minor = int(parts[1])
    patch = int(parts[2]) if len(parts) > 2 else 0
    if minor < 18:
        raise SystemExit(f"Release predates the Java 17 support floor: {minecraft}")
    return 21 if minor > 20 or (minor == 20 and patch >= 5) else 17


def require_keys(properties: dict[str, str], loader: str, target: str, keys: tuple[str, ...]) -> None:
    for key in keys:
        if not properties.get(f"{loader}.{target}.{key}"):
            raise SystemExit(f"Missing target property: {loader}.{target}.{key}")


def validate_java(
    properties: dict[str, str],
    loader: str,
    target: str,
    shared_java: int,
) -> None:
    target_profile = profile(properties, loader, target)
    for key in ("game_java_version", "adapter_java_release", "gradle_runtime_java"):
        if not target_profile[key].isdigit():
            raise SystemExit(f"Invalid {loader}.{target}.{key}: {target_profile[key]!r}")
    game_java = int(target_profile["game_java_version"])
    adapter_java = int(target_profile["adapter_java_release"])
    if game_java != expected_java(target_profile["minecraft_target_version"]):
        raise SystemExit(
            f"{loader} {target} declares Java {game_java}, expected "
            f"{expected_java(target_profile['minecraft_target_version'])}"
        )
    if adapter_java < shared_java or adapter_java > game_java:
        raise SystemExit(
            f"Invalid adapter Java {adapter_java} for {loader} {target}; "
            f"shared={shared_java}, game={game_java}"
        )


def validate_artifact_name(properties: dict[str, str], loader: str, target: str) -> None:
    target_profile = profile(properties, loader, target)
    base_name = gradle_property("archives_base_name")
    label = "Fabric" if loader == "fabric" else "NeoForge"
    expected = (
        f"{base_name}-{label}-{target_profile['minecraft_target_version']}-"
        "{mod_version}.jar"
    )
    if target_profile["standalone_artifact"] != expected:
        raise SystemExit(
            f"{loader} artifact manifest mismatch for {target}: "
            f"{target_profile['standalone_artifact']} != {expected}"
        )


def load_manifest(*, require_source_dirs: bool = True) -> Matrix:
    properties = read_properties(MANIFEST)
    official = csv_property(properties, "official_releases")
    fabric = csv_property(properties, "fabric_targets")
    neoforge = csv_property(properties, "neoforge_targets")
    neoforge_modern = csv_property(properties, "neoforge_modern_targets")
    neoforge_legacy = csv_property(properties, "neoforge_legacy_targets")

    official_count = properties.get("official_release_count", "")
    if not official_count.isdigit() or int(official_count) != len(official):
        raise SystemExit(
            f"official_release_count={official_count!r} does not match the {len(official)} listed releases"
        )
    if official[0] != "1.18" or official[-1] != "26.2":
        raise SystemExit(f"Official release bounds must be 1.18..26.2; got {official[0]}..{official[-1]}")
    shared_java_text = properties.get("shared_java_release", "")
    if not shared_java_text.isdigit():
        raise SystemExit(f"Invalid shared_java_release: {shared_java_text!r}")
    shared_java = int(shared_java_text)
    if properties.get("default_fabric_target") not in fabric:
        raise SystemExit("default_fabric_target must name a Fabric family")
    if properties.get("default_neoforge_target") not in neoforge:
        raise SystemExit("default_neoforge_target must name a NeoForge target")

    coverage: dict[str, list[str]] = {release: [] for release in official}
    fabric_adapters: list[str] = []
    for target in fabric:
        require_keys(properties, "fabric", target, FABRIC_PROFILE_KEYS)
        target_profile = profile(properties, "fabric", target)
        covered = tuple(value.strip() for value in target_profile["covers"].split(",") if value.strip())
        if not covered:
            raise SystemExit(f"Fabric target {target} covers no official releases")
        for release in covered:
            if release not in coverage:
                raise SystemExit(f"Fabric target {target} covers non-release {release}")
            coverage[release].append(target)
            if expected_java(release) != int(target_profile["game_java_version"]):
                raise SystemExit(
                    f"Fabric family {target} crosses the Java runtime boundary at {release}"
                )
        validate_java(properties, "fabric", target, shared_java)
        validate_artifact_name(properties, "fabric", target)
        adapter = target_profile["adapter_version"]
        fabric_adapters.append(adapter)
        if require_source_dirs:
            adapter_dir = ROOT / "fabric" / "src" / "version" / adapter
            if not adapter_dir.is_dir():
                raise SystemExit(f"Fabric adapter directory does not exist for {target}: {adapter_dir}")
    invalid_coverage = {release: owners for release, owners in coverage.items() if len(owners) != 1}
    if invalid_coverage:
        raise SystemExit(f"Each official release must have exactly one Fabric owner: {invalid_coverage}")
    for release in official:
        runtime = fabric_runtime_profile(properties, release)
        owner = coverage[release][0]
        owner_profile = profile(properties, "fabric", owner)
        if runtime.get("owner") != owner:
            raise SystemExit(
                f"Exact Fabric runtime {release} owner {runtime.get('owner')!r} != {owner}"
            )
        if not runtime.get("fabric_api_version"):
            raise SystemExit(f"Exact Fabric runtime {release} has no pinned Fabric API")
        mappings_mode = runtime.get("mappings_mode", owner_profile["mappings_mode"])
        if mappings_mode == "yarn" and not runtime.get("yarn_mappings"):
            raise SystemExit(f"Exact Fabric runtime {release} has no pinned Yarn mappings")
        if mappings_mode not in ("yarn", "none"):
            raise SystemExit(f"Exact Fabric runtime {release} has invalid mappings mode {mappings_mode}")
        if expected_java(release) != int(owner_profile["game_java_version"]):
            raise SystemExit(f"Exact Fabric runtime {release} crosses its family Java boundary")
    declared_fabric_adapters = csv_property(properties, "fabric_adapter_families")
    if tuple(dict.fromkeys(fabric_adapters)) != declared_fabric_adapters:
        raise SystemExit(
            "fabric_adapter_families must equal the ordered unique Fabric adapter_version values"
        )

    if set(neoforge_modern) & set(neoforge_legacy):
        raise SystemExit("NeoForge modern and legacy targets overlap")
    if tuple(target for target in neoforge if target in set(neoforge_legacy)) != neoforge_legacy:
        raise SystemExit("neoforge_legacy_targets must preserve neoforge_targets order")
    if tuple(target for target in neoforge if target in set(neoforge_modern)) != neoforge_modern:
        raise SystemExit("neoforge_modern_targets must preserve neoforge_targets order")
    if set(neoforge_modern) | set(neoforge_legacy) != set(neoforge):
        raise SystemExit("Every NeoForge target must be exactly one of modern or legacy")

    neoforge_adapters: list[str] = []
    for target in neoforge:
        require_keys(properties, "neoforge", target, NEOFORGE_PROFILE_KEYS)
        target_profile = profile(properties, "neoforge", target)
        if target not in official:
            raise SystemExit(f"NeoForge target is not an official Minecraft release: {target}")
        if target_profile["minecraft_target_version"] != target:
            raise SystemExit(f"NeoForge profile {target} points at {target_profile['minecraft_target_version']}")
        expected_kind = "legacy" if target in neoforge_legacy else "modern"
        if target_profile["build_kind"] != expected_kind:
            raise SystemExit(f"NeoForge {target} must use build_kind={expected_kind}")
        if target_profile["stability"] != "stable" and not (
            target == "26.2" and target_profile["stability"] == "beta-exception"
        ):
            raise SystemExit(f"NeoForge {target} is not stable and is not the 26.2 compatibility exception")
        validate_java(properties, "neoforge", target, shared_java)
        validate_artifact_name(properties, "neoforge", target)
        adapter = target_profile["adapter_version"]
        neoforge_adapters.append(adapter)
        if require_source_dirs:
            module = "neoforge-legacy" if expected_kind == "legacy" else "neoforge"
            adapter_dir = ROOT / module / "src" / "version" / adapter
            if not adapter_dir.is_dir():
                raise SystemExit(f"NeoForge adapter directory does not exist for {target}: {adapter_dir}")
    declared_neoforge_adapters = csv_property(properties, "neoforge_adapter_families")
    if tuple(dict.fromkeys(neoforge_adapters)) != declared_neoforge_adapters:
        raise SystemExit(
            "neoforge_adapter_families must equal the ordered unique NeoForge adapter_version values"
        )

    return Matrix(properties, official, fabric, neoforge, neoforge_modern, neoforge_legacy)


def require_target(matrix: Matrix, loader: str, target: str) -> None:
    targets = matrix.fabric if loader == "fabric" else matrix.neoforge
    if target not in targets:
        raise SystemExit(f"Unsupported {loader} target {target!r}; expected one of {targets}")


def fabric_owner(matrix: Matrix, release: str) -> str:
    if release not in matrix.official:
        raise SystemExit(f"Not an official supported Minecraft release: {release}")
    for target in matrix.fabric:
        if release in profile(matrix.properties, "fabric", target)["covers"].split(","):
            return target
    raise AssertionError(f"Validated release has no Fabric owner: {release}")


def gradle_property(name: str) -> str:
    text = GRADLE_PROPERTIES.read_text(encoding="utf-8")
    match = re.search(rf"^\s*{re.escape(name)}\s*=\s*(.+?)\s*$", text, re.MULTILINE)
    if not match:
        raise SystemExit(f"Missing {name} in {GRADLE_PROPERTIES}")
    return match.group(1)


def artifact_build_dir(matrix: Matrix, loader: str, target: str) -> pathlib.Path:
    default_key = f"default_{loader}_target"
    module = "fabric"
    if loader == "neoforge":
        module = "neoforge-legacy" if target in matrix.neoforge_legacy else "neoforge"
    build_root = ROOT / module / "build"
    return build_root / "libs" if target == matrix.properties[default_key] else build_root / target / "libs"


def collect_artifact(matrix: Matrix, loader: str, target: str) -> pathlib.Path:
    target_profile = profile(matrix.properties, loader, target)
    artifact_name = target_profile["standalone_artifact"].replace(
        "{mod_version}", gradle_property("mod_version")
    )
    source = artifact_build_dir(matrix, loader, target) / artifact_name
    if not source.is_file():
        available = sorted(path.name for path in source.parent.glob("*.jar")) if source.parent.is_dir() else []
        raise SystemExit(f"Expected artifact not found: {source}; available={available}")
    destination_dir = ROOT / "build-artifacts"
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / artifact_name
    shutil.copy2(source, destination)
    return destination


def collect_universal() -> pathlib.Path:
    artifact_name = (
        f"{gradle_property('archives_base_name')}-Fabric-all-"
        f"{gradle_property('mod_version')}.jar"
    )
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


def verify_release_set(matrix: Matrix) -> list[pathlib.Path]:
    mod_version = gradle_property("mod_version")
    expected = {
        profile(matrix.properties, "fabric", target)["standalone_artifact"].replace(
            "{mod_version}", mod_version
        )
        for target in matrix.fabric
    }
    expected.update(
        profile(matrix.properties, "neoforge", target)["standalone_artifact"].replace(
            "{mod_version}", mod_version
        )
        for target in matrix.neoforge
    )
    expected.add(f"{gradle_property('archives_base_name')}-Fabric-all-{mod_version}.jar")
    artifact_dir = ROOT / "build-artifacts"
    actual = {path.name for path in artifact_dir.glob("*.jar")} if artifact_dir.is_dir() else set()
    if actual != expected:
        raise SystemExit(f"Release artifacts mismatch: expected={sorted(expected)}, actual={sorted(actual)}")
    ambiguous = [name for name in actual if not ("-Fabric-" in name or "-NeoForge-" in name)]
    if ambiguous:
        raise SystemExit(f"Loader-ambiguous artifacts are forbidden: {ambiguous}")
    return [artifact_dir / name for name in sorted(expected)]


def java_home(matrix: Matrix, loader: str, target: str) -> pathlib.Path:
    required = profile(matrix.properties, loader, target)["gradle_runtime_java"]
    preferred_name = f"JAVA{required}_HOME"
    current_java = shutil.which("java")
    current_home = str(pathlib.Path(current_java).resolve().parents[1]) if current_java else None
    candidates: list[str | pathlib.Path | None] = [
        os.environ.get(preferred_name),
        os.environ.get("JAVA_HOME"),
        current_home,
        pathlib.Path(f"/usr/lib/jvm/java-{required}-openjdk-amd64"),
        pathlib.Path(f"/usr/lib/jvm/java-{required}-openjdk"),
        pathlib.Path(f"/tmp/teamviewer-jdk{required}"),
        pathlib.Path(f"/tmp/teamviewer-msjdk{required}"),
    ]
    system_jvm_dir = pathlib.Path("/usr/lib/jvm")
    if system_jvm_dir.is_dir():
        candidates.extend(sorted(system_jvm_dir.iterdir()))
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
        f"Minecraft {target} ({loader}) requires a Java {required} Gradle runtime. "
        f"Set {preferred_name} (preferred) or JAVA_HOME to that JDK."
    )


def matrix_entry(matrix: Matrix, loader: str, target: str, *, release: str | None = None) -> dict[str, str]:
    target_profile = profile(matrix.properties, loader, target)
    entry = {
        "loader": loader,
        "minecraft": target,
        "java": target_profile["gradle_runtime_java"],
        "game_java": target_profile["game_java_version"],
        "adapter_java": target_profile["adapter_java_release"],
    }
    if release is not None:
        entry["release"] = release
        entry["family"] = target
    if loader == "neoforge":
        entry["build_kind"] = target_profile["build_kind"]
    return entry


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in (
        "list",
        "list-fabric",
        "list-official",
        "list-neoforge",
        "list-neoforge-modern",
        "list-neoforge-legacy",
        "default-target",
        "default-neoforge-target",
        "ci-matrix",
        "full-fabric-ci-matrix",
        "full-ci-matrix",
        "max-java",
        "shared-java",
        "packaging-java",
        "prepare-build",
        "collect-universal",
        "verify-release-set",
    ):
        subparsers.add_parser(command)
    owner_parser = subparsers.add_parser("fabric-owner")
    owner_parser.add_argument("release")
    runtime_parser = subparsers.add_parser("validate-runtime")
    runtime_parser.add_argument("release")
    for command in ("validate", "get", "collect", "java-home"):
        command_parser = subparsers.add_parser(command)
        command_parser.add_argument("target")
        command_parser.add_argument("--loader", choices=("fabric", "neoforge"), default="fabric")
        if command == "get":
            command_parser.add_argument("key")
    args = parser.parse_args()

    matrix = load_manifest()
    if args.command in ("list", "list-fabric"):
        print("\n".join(matrix.fabric))
    elif args.command == "list-official":
        print("\n".join(matrix.official))
    elif args.command == "list-neoforge":
        print("\n".join(matrix.neoforge))
    elif args.command == "list-neoforge-modern":
        print("\n".join(matrix.neoforge_modern))
    elif args.command == "list-neoforge-legacy":
        print("\n".join(matrix.neoforge_legacy))
    elif args.command == "default-target":
        print(matrix.properties["default_fabric_target"])
    elif args.command == "default-neoforge-target":
        print(matrix.properties["default_neoforge_target"])
    elif args.command == "ci-matrix":
        entries = [matrix_entry(matrix, "fabric", target) for target in matrix.fabric]
        entries.extend(matrix_entry(matrix, "neoforge", target) for target in matrix.neoforge)
        print(json.dumps({"include": entries}, separators=(",", ":")))
    elif args.command == "full-ci-matrix":
        entries = [
            matrix_entry(matrix, "fabric", fabric_owner(matrix, release), release=release)
            for release in matrix.official
        ]
        entries.extend(matrix_entry(matrix, "neoforge", target, release=target) for target in matrix.neoforge)
        print(json.dumps({"include": entries}, separators=(",", ":")))
    elif args.command == "full-fabric-ci-matrix":
        entries = [
            matrix_entry(matrix, "fabric", fabric_owner(matrix, release), release=release)
            for release in matrix.official
        ]
        print(json.dumps({"include": entries}, separators=(",", ":")))
    elif args.command == "fabric-owner":
        print(fabric_owner(matrix, args.release))
    elif args.command == "validate-runtime":
        owner = fabric_owner(matrix, args.release)
        runtime = fabric_runtime_profile(matrix.properties, args.release)
        print(json.dumps({
            "release": args.release,
            "family": owner,
            "fabric_api_version": runtime["fabric_api_version"],
            "mappings": runtime.get("yarn_mappings", runtime.get("mappings_mode", "none")),
        }, separators=(",", ":")))
    elif args.command == "max-java":
        print(
            max(
                int(profile(matrix.properties, loader, target)["gradle_runtime_java"])
                for loader, targets in (("fabric", matrix.fabric), ("neoforge", matrix.neoforge))
                for target in targets
            )
        )
    elif args.command == "shared-java":
        print(matrix.properties["shared_java_release"])
    elif args.command == "packaging-java":
        target = matrix.properties["default_fabric_target"]
        print(profile(matrix.properties, "fabric", target)["gradle_runtime_java"])
    elif args.command == "prepare-build":
        prepare_build()
    elif args.command == "collect-universal":
        artifact = collect_universal()
        print(f"Created {artifact.relative_to(ROOT)}")
    elif args.command == "verify-release-set":
        for artifact in verify_release_set(matrix):
            print(artifact.relative_to(ROOT))
    elif args.command == "validate":
        require_target(matrix, args.loader, args.target)
    elif args.command == "get":
        require_target(matrix, args.loader, args.target)
        value = profile(matrix.properties, args.loader, args.target).get(args.key)
        if value is None:
            raise SystemExit(f"Target {args.loader}:{args.target} has no property {args.key}")
        print(value)
    elif args.command == "collect":
        require_target(matrix, args.loader, args.target)
        artifact = collect_artifact(matrix, args.loader, args.target)
        print(f"Created {artifact.relative_to(ROOT)}")
    elif args.command == "java-home":
        require_target(matrix, args.loader, args.target)
        print(java_home(matrix, args.loader, args.target))


if __name__ == "__main__":
    main()
