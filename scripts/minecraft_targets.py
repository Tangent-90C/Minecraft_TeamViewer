#!/usr/bin/env python3
"""Validate and query the Minecraft release/build matrix shared by Gradle and CI."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
from dataclasses import dataclass


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "gradle" / "minecraft-versions.properties"
MAP_MANIFEST = ROOT / "gradle" / "map-mod-versions.properties"
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

SERVICE_PROVIDER_PATH = pathlib.Path(
    "resources/META-INF/services/"
    "fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory"
)
MODULE_REQUIRED_PORTS = {
    "fabric": tuple(
        pathlib.Path("java/fun/prof_chen/teamviewer/main_code") / relative
        for relative in (
            "core/FabricClientAdapterFactory.java",
            "client/bridge/FabricClientEventBridge.java",
            "client/bridge/FabricGameClientBridge.java",
            "render/FabricWorldRenderSink.java",
            "render/FabricHudRenderSink.java",
            "screen/ConfigScreen.java",
            "screen/PluginManagerScreen.java",
        )
    ),
    "neoforge": tuple(
        pathlib.Path("java") / relative
        for relative in (
            "fun/prof_chen/teamviewer/main_code/core/NeoForgeClientAdapterFactory.java",
            "fun/prof_chen/teamviewer/neoforge/adapter/client/NeoForgeClientEventBridge.java",
            "fun/prof_chen/teamviewer/neoforge/adapter/client/NeoForgeGameClientBridge.java",
            "fun/prof_chen/teamviewer/main_code/network/bridge/NeoForgeRuntimeGateway.java",
            "fun/prof_chen/teamviewer/main_code/render/NeoForgeWorldRenderSink.java",
            "fun/prof_chen/teamviewer/main_code/render/NeoForgeHudRenderSink.java",
            "fun/prof_chen/teamviewer/main_code/screen/ConfigScreen.java",
            "fun/prof_chen/teamviewer/main_code/screen/PluginManagerScreen.java",
        )
    ),
    "neoforge-legacy": tuple(
        pathlib.Path("java/fun/prof_chen/teamviewer/main_code") / relative
        for relative in (
            "core/NeoForgeClientAdapterFactory.java",
            "client/bridge/NeoForgeClientEventBridge.java",
            "client/bridge/NeoForgeGameClientBridge.java",
            "network/bridge/NeoForgeRuntimeGateway.java",
            "render/NeoForgeWorldRenderSink.java",
            "render/NeoForgeHudRenderSink.java",
            "screen/ConfigScreen.java",
            "screen/PluginManagerScreen.java",
        )
    ),
}
MODULE_MIXIN_PATH = {
    "fabric": pathlib.Path("resources/teamviewer.client.mixins.json"),
    "neoforge": pathlib.Path("resources/teamviewer.neoforge.client.mixins.json"),
    "neoforge-legacy": pathlib.Path("resources/teamviewer.neoforge.client.mixins.json"),
}


@dataclass(frozen=True)
class Matrix:
    properties: dict[str, str]
    map_properties: dict[str, str]
    official: tuple[str, ...]
    fabric: tuple[str, ...]
    neoforge: tuple[str, ...]
    neoforge_modern: tuple[str, ...]
    neoforge_legacy: tuple[str, ...]


@dataclass(frozen=True)
class SourceEntry:
    path: pathlib.Path
    origin: str


@dataclass(frozen=True)
class SourcePlan:
    module: str
    adapter: str
    layers: tuple[str, ...]
    entries: dict[pathlib.Path, SourceEntry]


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
    default_prefix = f"{loader}.default."
    prefix = f"{loader}.{target}."
    result = {
        key[len(default_prefix):]: value
        for key, value in properties.items()
        if key.startswith(default_prefix)
    }
    result.update({key[len(prefix):]: value for key, value in properties.items() if key.startswith(prefix)})
    result.setdefault("minecraft_target_version", target)
    result.setdefault("adapter_version", target)
    try:
        game_java = expected_java(result["minecraft_target_version"])
    except SystemExit:
        game_java = None
    if game_java is not None:
        result.setdefault("game_java_version", str(game_java))
        result.setdefault("adapter_java_release", str(game_java))
        result.setdefault("gradle_runtime_java", str(max(21, game_java) if loader == "fabric" else game_java))
    if loader == "fabric":
        covered = tuple(value.strip() for value in result.get("covers", "").split(",") if value.strip())
        if covered:
            support_range = covered[0] if len(covered) == 1 else f"{covered[0]}-to-{covered[-1]}"
            result.setdefault(
                "standalone_artifact",
                f"{gradle_property('archives_base_name')}-Fabric-MC{support_range}-{{mod_version}}.jar",
            )
        result.setdefault("slim_adapter_artifact", f"teamviewer-adapter-{target}.jar")
    else:
        legacy = set(value.strip() for value in properties.get("neoforge_legacy_targets", "").split(","))
        result.setdefault("build_kind", "legacy" if target in legacy else "modern")
        result.setdefault("stability", "stable")
        result.setdefault(
            "standalone_artifact",
            f"{gradle_property('archives_base_name')}-NeoForge-MC{target}-{{mod_version}}.jar",
        )
    return result


def adapter_module(matrix: Matrix, loader: str, target: str) -> str:
    if loader == "fabric":
        return "fabric"
    return "neoforge-legacy" if target in matrix.neoforge_legacy else "neoforge"


def module_adapters(matrix: Matrix, module: str) -> tuple[str, ...]:
    if module == "fabric":
        targets = matrix.fabric
        loader = "fabric"
    elif module == "neoforge":
        targets = matrix.neoforge_modern
        loader = "neoforge"
    elif module == "neoforge-legacy":
        targets = matrix.neoforge_legacy
        loader = "neoforge"
    else:
        raise ValueError(f"Unknown adapter module: {module}")
    return tuple(dict.fromkeys(profile(matrix.properties, loader, target)["adapter_version"] for target in targets))


def _source_files(root: pathlib.Path) -> dict[pathlib.Path, pathlib.Path]:
    entries: dict[pathlib.Path, pathlib.Path] = {}
    for source_kind in ("java", "resources"):
        source_root = root / source_kind
        if not source_root.is_dir():
            continue
        for source in sorted(path for path in source_root.rglob("*") if path.is_file()):
            entries[pathlib.Path(source_kind) / source.relative_to(source_root)] = source
    return entries


def source_plan(matrix: Matrix, loader: str, target: str) -> SourcePlan:
    require_target(matrix, loader, target)
    target_profile = profile(matrix.properties, loader, target)
    adapter = target_profile["adapter_version"]
    module = adapter_module(matrix, loader, target)
    source_root = ROOT / module / "src"
    entries = {
        relative: SourceEntry(path, "shared")
        for relative, path in _source_files(source_root / "shared").items()
    }
    selected_layers: list[str] = []
    compat_owners: dict[pathlib.Path, str] = {}
    compat_root = source_root / "compat"
    if compat_root.is_dir():
        for layer_root in sorted(path for path in compat_root.iterdir() if path.is_dir()):
            metadata_path = layer_root / "layer.properties"
            if not metadata_path.is_file():
                raise SystemExit(f"Compatibility layer has no layer.properties: {layer_root}")
            metadata = read_properties(metadata_path)
            adapters = tuple(value.strip() for value in metadata.get("adapters", "").split(",") if value.strip())
            if adapter not in adapters:
                continue
            selected_layers.append(layer_root.name)
            layer_files = _source_files(layer_root)
            for relative, path in layer_files.items():
                previous = compat_owners.get(relative)
                if previous is not None:
                    raise SystemExit(
                        f"Adapter {module}:{adapter} selects conflicting compatibility layers "
                        f"{previous} and {layer_root.name} for {relative}"
                    )
                compat_owners[relative] = layer_root.name
                entries[relative] = SourceEntry(path, f"compat:{layer_root.name}")
    version_root = source_root / "version" / adapter
    for relative, path in _source_files(version_root).items():
        previous = entries.get(relative)
        if previous is not None and previous.path.read_bytes() == path.read_bytes():
            raise SystemExit(
                f"Redundant version override for {module}:{adapter} {relative}; "
                f"it is identical to {previous.origin}"
            )
        entries[relative] = SourceEntry(path, f"version:{adapter}")
    return SourcePlan(module, adapter, tuple(selected_layers), entries)


def validate_source_layout(matrix: Matrix) -> None:
    for module in ("fabric", "neoforge", "neoforge-legacy"):
        expected = set(module_adapters(matrix, module))
        version_root = ROOT / module / "src" / "version"
        actual = {path.name for path in version_root.iterdir() if path.is_dir()}
        if actual != expected:
            raise SystemExit(
                f"{module} adapter directories mismatch: "
                f"missing={sorted(expected - actual)}, unexpected={sorted(actual - expected)}"
            )
        for adapter in sorted(expected):
            marker = version_root / adapter / "adapter.properties"
            if not marker.is_file() or read_properties(marker).get("adapter") != adapter:
                raise SystemExit(f"Invalid or missing adapter marker: {marker}")

        compat_root = ROOT / module / "src" / "compat"
        if compat_root.is_dir():
            seen_variants: dict[tuple[pathlib.Path, str], pathlib.Path] = {}
            for layer_root in sorted(path for path in compat_root.iterdir() if path.is_dir()):
                metadata_path = layer_root / "layer.properties"
                if not metadata_path.is_file():
                    raise SystemExit(f"Compatibility layer has no layer.properties: {layer_root}")
                metadata = read_properties(metadata_path)
                adapters = tuple(value.strip() for value in metadata.get("adapters", "").split(",") if value.strip())
                if len(adapters) < 2:
                    raise SystemExit(f"Compatibility layer must cover at least two adapters: {layer_root}")
                if len(set(adapters)) != len(adapters) or not set(adapters) <= expected:
                    raise SystemExit(f"Compatibility layer has invalid adapters {adapters}: {layer_root}")
                files = _source_files(layer_root)
                if not files:
                    raise SystemExit(f"Compatibility layer has no sources or resources: {layer_root}")
                for relative, source in files.items():
                    digest = hashlib.sha256(source.read_bytes()).hexdigest()
                    key = (relative, digest)
                    previous = seen_variants.get(key)
                    if previous is not None:
                        raise SystemExit(
                            f"Duplicate compatibility implementation for {relative}: {previous} and {source}"
                        )
                    seen_variants[key] = source

        implementations: dict[tuple[pathlib.Path, str], pathlib.Path] = {}
        implementation_roots = [ROOT / module / "src" / "shared"]
        if compat_root.is_dir():
            implementation_roots.extend(path for path in compat_root.iterdir() if path.is_dir())
        implementation_roots.extend(version_root / adapter for adapter in sorted(expected))
        for implementation_root in implementation_roots:
            for relative, source in _source_files(implementation_root).items():
                key = (relative, hashlib.sha256(source.read_bytes()).hexdigest())
                previous = implementations.get(key)
                if previous is not None:
                    raise SystemExit(
                        f"Duplicate adapter implementation for {relative}: {previous} and {source}"
                    )
                implementations[key] = source

    for loader, targets in (("fabric", matrix.fabric), ("neoforge", matrix.neoforge)):
        checked: set[tuple[str, str]] = set()
        for target in targets:
            plan = source_plan(matrix, loader, target)
            key = (plan.module, plan.adapter)
            if key in checked:
                continue
            checked.add(key)
            missing_ports = [
                str(relative)
                for relative in MODULE_REQUIRED_PORTS[plan.module]
                if relative not in plan.entries
            ]
            if missing_ports:
                raise SystemExit(
                    f"{plan.module}:{plan.adapter} has no mandatory ports: {missing_ports}"
                )
            provider = plan.entries.get(SERVICE_PROVIDER_PATH)
            if provider is None:
                raise SystemExit(f"{plan.module}:{plan.adapter} has no ClientAdapterFactory provider")
            providers = [
                line.strip()
                for line in provider.path.read_text(encoding="utf-8").splitlines()
                if line.strip() and not line.lstrip().startswith("#")
            ]
            if len(providers) != 1:
                raise SystemExit(
                    f"{plan.module}:{plan.adapter} must resolve exactly one "
                    f"ClientAdapterFactory provider; got {providers}"
                )
            mixin_entry = plan.entries.get(MODULE_MIXIN_PATH[plan.module])
            if mixin_entry is None:
                raise SystemExit(f"{plan.module}:{plan.adapter} has no client Mixin configuration")
            mixin = json.loads(mixin_entry.path.read_text(encoding="utf-8"))
            if not mixin.get("client"):
                raise SystemExit(
                    f"{plan.module}:{plan.adapter} must resolve a non-empty client Mixin list"
                )


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
    target_profile = profile(properties, loader, target)
    for key in keys:
        if not target_profile.get(key):
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
    if loader == "fabric":
        covered_releases = tuple(
            release.strip()
            for release in target_profile["covers"].split(",")
            if release.strip()
        )
        support_range = (
            covered_releases[0]
            if len(covered_releases) == 1
            else f"{covered_releases[0]}-to-{covered_releases[-1]}"
        )
    else:
        support_range = target_profile["minecraft_target_version"]
    expected = (
        f"{base_name}-{label}-MC{support_range}-"
        "{mod_version}.jar"
    )
    if target_profile["standalone_artifact"] != expected:
        raise SystemExit(
            f"{loader} artifact manifest mismatch for {target}: "
            f"{target_profile['standalone_artifact']} != {expected}"
        )


def load_manifest(*, require_source_dirs: bool = True) -> Matrix:
    properties = read_properties(MANIFEST)
    map_properties = read_properties(MAP_MANIFEST)
    official = csv_property(properties, "official_releases")
    fabric = csv_property(properties, "fabric_targets")
    neoforge = csv_property(properties, "neoforge_targets")
    neoforge_modern = csv_property(properties, "neoforge_modern_targets")
    neoforge_legacy = csv_property(properties, "neoforge_legacy_targets")

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

    validate_map_matrix(official, map_properties)

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

    matrix = Matrix(properties, map_properties, official, fabric, neoforge, neoforge_modern, neoforge_legacy)
    if require_source_dirs:
        validate_source_layout(matrix)
    return matrix


def validate_map_matrix(official: tuple[str, ...], properties: dict[str, str]) -> None:
    expected_keys = {f"map.{release}.{plugin}" for release in official
                     for plugin in ("journeymap", "xaero")}
    actual_keys = set(properties)
    if actual_keys != expected_keys:
        raise SystemExit(
            f"Map plugin matrix keys mismatch: missing={sorted(expected_keys - actual_keys)}, "
            f"unexpected={sorted(actual_keys - expected_keys)}"
        )
    journey_entries = {
        "api-v1-merged": "fabric-api-v1.lua",
        "api-v2-merged": "fabric-1.21.8.lua",
        "api-v2-full": "fabric-26.1.2.lua",
        "unsupported": "unsupported.lua",
    }
    xaero_entries = {
        "module-session": "main.lua",
        "legacy-session": "legacy-1.19.3.lua",
        "unsupported": "unsupported.lua",
    }
    manifests = {
        "journeymap": (journey_entries, ROOT / "common/src/main/resources/teamviewer/plugins/journeymap/plugin.json"),
        "xaero": (xaero_entries, ROOT / "common/src/main/resources/teamviewer/plugins/xaero/plugin.json"),
    }
    for plugin, (families, manifest_path) in manifests.items():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        routes: dict[str, str] = {}
        for entrypoint in manifest.get("entrypoints", []):
            if "fabric" not in entrypoint.get("loaders", []):
                continue
            for release in entrypoint.get("minecraftVersions", []):
                if release in routes:
                    raise SystemExit(f"{plugin} has overlapping Fabric routes for {release}")
                routes[release] = entrypoint["entry"]
        for release in official:
            parts = properties[f"map.{release}.{plugin}"].split("|")
            family = parts[0]
            if family not in families:
                raise SystemExit(f"Unknown {plugin} API family for {release}: {family}")
            expected_length = 1 if family == "unsupported" else (2 if plugin == "journeymap" else 3)
            if len(parts) != expected_length:
                raise SystemExit(f"Invalid {plugin} artifact declaration for {release}: {parts}")
            for artifact in parts[1:]:
                if not artifact.startswith("maven.modrinth:"):
                    raise SystemExit(f"{plugin} {release} must pin an official Modrinth Maven artifact")
            actual_entry = routes.get(release, "unsupported.lua")
            if actual_entry != families[family]:
                raise SystemExit(
                    f"{plugin} {release} matrix family {family} expects {families[family]}, "
                    f"manifest selects {actual_entry}"
                )


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


def universal_artifact_name(matrix: Matrix) -> str:
    return (
        f"{gradle_property('archives_base_name')}-Fabric-"
        f"MC{matrix.official[0]}-to-{matrix.official[-1]}-All-in-One-"
        f"{gradle_property('mod_version')}.jar"
    )


def collect_universal(matrix: Matrix) -> pathlib.Path:
    artifact_name = universal_artifact_name(matrix)
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
    expected.add(universal_artifact_name(matrix))
    artifact_dir = ROOT / "build-artifacts"
    actual = {path.name for path in artifact_dir.glob("*.jar")} if artifact_dir.is_dir() else set()
    if actual != expected:
        raise SystemExit(f"Release artifacts mismatch: expected={sorted(expected)}, actual={sorted(actual)}")
    ambiguous = [name for name in actual if not ("-Fabric-" in name or "-NeoForge-" in name)]
    if ambiguous:
        raise SystemExit(f"Loader-ambiguous artifacts are forbidden: {ambiguous}")
    return [artifact_dir / name for name in sorted(expected)]


def java_home_candidates(required: str) -> tuple[str | pathlib.Path | None, ...]:
    preferred_name = f"JAVA{required}_HOME"
    setup_java_prefix = f"JAVA_HOME_{required}_"
    current_java = shutil.which("java")
    current_home = str(pathlib.Path(current_java).resolve().parents[1]) if current_java else None
    candidates: list[str | pathlib.Path | None] = [
        os.environ.get(preferred_name),
        *(
            value
            for name, value in sorted(os.environ.items())
            if name.startswith(setup_java_prefix)
        ),
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
    return tuple(candidates)


def java_home(matrix: Matrix, loader: str, target: str) -> pathlib.Path:
    required = profile(matrix.properties, loader, target)["gradle_runtime_java"]
    preferred_name = f"JAVA{required}_HOME"
    for candidate in java_home_candidates(required):
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
        f"Set {preferred_name} (preferred), JAVA_HOME_{required}_<ARCH>, or JAVA_HOME to that JDK."
    )


def gradle_java_versions(matrix: Matrix) -> tuple[str, ...]:
    versions = {
        int(profile(matrix.properties, loader, target)["gradle_runtime_java"])
        for loader, targets in (("fabric", matrix.fabric), ("neoforge", matrix.neoforge))
        for target in targets
    }
    return tuple(str(version) for version in sorted(versions))


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
        "list-gradle-java",
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
    source_plan_parser = subparsers.add_parser("source-plan")
    source_plan_parser.add_argument("target")
    source_plan_parser.add_argument("--loader", choices=("fabric", "neoforge"), default="fabric")
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
    elif args.command == "list-gradle-java":
        print("\n".join(gradle_java_versions(matrix)))
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
    elif args.command == "source-plan":
        plan = source_plan(matrix, args.loader, args.target)
        print(f"{plan.module}:{plan.adapter}")
        print("generated\tjava/fun/prof_chen/teamviewer/main_code/core/AdapterBuildProfile.java")
        for relative, entry in sorted(plan.entries.items()):
            print(f"{entry.origin}\t{relative}")
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
        artifact = collect_universal(matrix)
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
