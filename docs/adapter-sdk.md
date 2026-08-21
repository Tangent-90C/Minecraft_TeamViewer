# TeamViewRelay Common Adapter SDK

`common` is the only client business implementation. `common-sdk` is a separately compiled API
surface selected from the platform-neutral SDK sources under `common/src/main/java`. A Minecraft
adapter is compiled against `common-sdk` without `common-runtime`: it reads native state into
immutable snapshots, forwards Loader events, executes render/UI commands, and calls optional-map
APIs.

## Composition contract

Every version exposes exactly one `ClientAdapterFactory<W, H>` through Java `ServiceLoader`. The
factory constructs a non-null `ClientAdapterBundle<W, H>`; the Loader-neutral `client-bootstrap` starts the
common runtime. `W` and `H` are the native world/HUD render contexts, so event/sink mismatches are
compile errors rather than runtime casts.

- `RuntimeGateway` supplies protocol/program metadata, the local identity and dimension, the
  log directory and config directory. Calls may originate on the network thread unless a method
  explicitly documents otherwise; returned paths are absolute or stable process-relative paths.
  `copyTextToClipboard(String)` is the narrow native clipboard boundary used by trusted local UI
  actions. It runs on the client UI thread, returns `false` when unavailable, and adapters must use
  Minecraft's clipboard wrapper rather than AWT so headless and cross-platform behavior stays owned
  by the game runtime.
- `GameClientBridge` is read on the Minecraft client thread. Coordinates are world block
  coordinates in doubles, velocities are blocks/tick, dimensions are canonical IDs such as
  `minecraft:overworld`, and absent worlds/players return the relevant `unavailable()` snapshot.
  It must never return `null` collections. Report/world snapshots never collect the server Tab
  list: `captureTabPlayerSnapshot()` is the only Tab boundary and common throttles it to once per
  second. `captureWorldSnapshot(false)` must not enumerate world entities.
  Official adapters implement `captureEntityFrame()` as the high-volume entity boundary: walk the
  native loaded-entity collection once, write UUIDs and primitive fields directly into the supplied
  `EntityCaptureTarget`, and apply the precompiled exact-match `EntityUploadFilter` before accepting
  a row. Entity types are stable namespaced registry IDs, not translated display strings.
  `captureReportSnapshot(true)` remains a compatibility path for external adapters and Lua snapshots;
  common player reporting always requests `false`. Scoreboard lines are
  already team-decorated and kept in display order; text runs preserve native style colors as a
  name or `#RRGGBB`.
  Quick-mark targeting must resolve the first visible block/entity at the requested range. Versions
  whose prepared crosshair hit is interaction-limited perform their own native raycast, stop at the
  first block obstruction, and hard-limit traversal with `MARK_TARGET_MAX_DISTANCE`.
- `ClientEventBridge<W,H>` registers tick, toggle/config/mark input, remote join, disconnect, system chat,
  stopping, world render and HUD render exactly once. System chat forwards its plain text and overlay flag as
  `SystemChatMessageSnapshot`; it does not alter native chat delivery. Callbacks run on the Loader's
  client/render threads and must not retain render contexts after the callback returns. `registeredEvents()`
  must report every `ClientEventType` after registration.
- `WorldRenderSink<W>` and `HudRenderSink<H>` execute immutable common frames. They perform native
  coordinate/widget calls only and must not inspect config, repositories or network state.
- `ConfigScreenHost` opens the native widget host. Page definitions, values, validation,
  save/cancel behavior, status actions and layout come from `ConfigUiSession`.
- `IntegrationRegistry` is the shared inventory for optional integrations. The platform factory
  supplies one empty registry; built-in manifests predeclare every stable capability before Lua
  executes, so disabled, missing-mod, unsupported and failed capabilities remain visible.
- `BattleMapSource` is the Java extension point for native providers and Lua-backed adapters. It
  returns `BattleMapSourceSnapshot`; `BattleMapCoordinator` exclusively owns source selection,
  history alignment, projection, semantic hashes, keepalive and conversion to the dedicated
  `battle_map_observation` packet.
- `PlayerRelationClassifier` is the loader-neutral local relationship extension point. Common
  captures and caches the Tab snapshot at most once per second, merges active classifier results,
  and supplies the same effective relation to rendering, the public player API and projections.
  Lua reads that cache through `snapshots.tab_players()`; plugins must not trigger adapter reads.
- Platform factories do not link optional JourneyMap, Xaero or SimMC API classes. Lua adapters
  resolve them after probing the installed Mods. A high-frequency native provider may register a
  Java implementation and expose it through `tv.use_native_capability`.
- Lua plugins may call `tv.copy_json_to_clipboard(table)` for explicit local export actions. The
  host accepts JSON-compatible scalar/table values only and rejects mixed-key or sparse arrays,
  nesting deeper than 8 levels, more than 4096 entries, oversized strings and serialized payloads
  above 256 KiB. This API does not send data to the relay or grant arbitrary clipboard reads.

There is no self-declared user feature list. Common-owned behavior is present once in the runtime;
mandatory native abilities are represented by non-null typed bundle fields, while optional
abilities are derived from manifest declarations in the shared registry.

## Lifecycle and error rules

Adapters return unavailable/empty snapshots for normal lifecycle gaps (title screen, world
transition, optional mod absent). Invalid adapter construction is a programming error and throws
immediately. Reflection/API failures in optional integrations are contained by the plugin and
reported as `FAILED` with a reason; they must not crash the client tick. Explicit unavailable
states do not fail the Adapter TCK, but a missing stable declaration, duplicate ID, role mismatch
or an `AVAILABLE` capability without a callable implementation does.

Common owns connection state, reporting clocks, repository cleanup, input state machines,
waypoint TTL/diff policy, render decisions, HUD text and battle-map upload policy. Version code
must not import `Config`, `NetworkManager`, protocol messages, repositories or coordinators.
Common captures one lightweight world snapshot per enabled client tick and shares it across
coordinators. Render callbacks keep camera data current, but request entity enumeration only when
an entity-bound waypoint actually needs it.
Entity upload is independently scheduled: the client thread fills a pooled structure-of-arrays frame,
then a single worker performs typed state comparison and direct protobuf encoding. Adapters must not
retain the capture target after `captureEntityFrame()` returns.

## TCK and target builds

The shared bootstrap starts the common runtime and built-in plugins before running `AdapterTck`.
It probes title-screen snapshots, mandatory event registration, every config page and the shared
registry snapshot, then writes
`teamviewer-adapter-capabilities.json` under the target log directory. Set
`-Dteamviewer.adapterTck.strict=true` in development/CI to fail startup on a contract violation.

Minecraft targets are declared only in `gradle/minecraft-versions.properties`. Task and CI read
that manifest through `scripts/minecraft_targets.py`; `task build` iterates every target, while
`task build-target TARGET=<version>` builds one profile in its isolated Gradle build directory.
Task starts each target in a separate no-daemon Gradle process so Loom and mapping services from
one target cannot leak into the next target.

Loader sources resolve in three stages: `src/shared` supplies loader-wide defaults, selected
`src/compat/<capability>-<variant>` layers replace individual ports for their declared adapters,
and `src/version/<adapter>` supplies the final exceptional overrides. Compatibility layers are
self-described by `layer.properties`; two selected layers may not own the same relative path.
Fabric resolves these stages under `fabric/src`. Every NeoForge target resolves them under the
single `neoforge-adapter/src` tree; `neoforge` and `neoforge-legacy` are ModDev/UserDev compilation
frontends, not separate source owners. A NeoForge compatibility layer may therefore span the
legacy/modern build boundary when the native API is actually identical.
For example, a layer reused by non-adjacent API-compatible adapters declares them explicitly:

```properties
adapters=1.18.2,1.19.2,1.19.4,1.20.1,1.20.2
```

Gradle filters the original `SourceDirectorySet` roots in place; it does not copy selected sources
to a generated mirror. IDE navigation, Qodana findings and compiler diagnostics therefore keep the
real shared/compat/version path.
Run `python3 scripts/minecraft_targets.py source-plan <target> [--loader neoforge] [--format json]` to inspect the
effective owner of every Java and resource file.

Large native ports follow a core/shim split inside those layers. `GameClientBridge`, runtime
metadata, configuration screens, plugin screens and event registration keep their stable control
flow in a shared core; a version shim contains only the native method names, event signatures or
text/input constructors that actually changed. UI state and layout remain in common and native
screens only paint `ConfigPageView`/`PluginManagerView` and translate input events.

Immediate world renderers compile `WorldRenderFrame` once through `WorldRenderBatchCompiler`.
Lines are grouped by depth mode and width, while filled primitives are grouped by depth mode, so
the number of native submissions depends on render state rather than remote-player count. Minecraft
1.21.11 and 26.x use the vanilla Gizmo pipeline instead; their custom `BatchGizmo` combines all
commands for each occlusion mode and must not be replaced with per-command Gizmo registration.

`common-sdk`, common runtime and `client-bootstrap` are Java 17 ABI artifacts. Each adapter is
compiled with its target's `adapter_java_release`. A target below Java 17 is rejected until a
separate legacy runtime exists; running an old Minecraft release on a newer JRE is not a supported
substitute. Every target produces a complete standalone Jar plus an internal slim adapter. Fabric
uses Loom's remapped slim output; NeoForge extracts production-mapped classes and applies exact-class
relocation when assembling its AIO. The same adapter bytecode is embedded into the Loader-specific
All-in-One container, while business/runtime classes
and third-party libraries remain shared once. See `docs/multi-version-packaging.md`.

## Adding an adapter

Declare the target profile and its `fabric/src/version/<adapter>/adapter.properties` or
`neoforge-adapter/src/version/<adapter>/adapter.properties` marker, then inspect
each mandatory port in `source-plan`. Reuse an existing capability layer when its API compiles for
the new target; otherwise create a new named variant and list every compatible adapter in that
layer's metadata. Do not copy a complete adapter directory. A version directory contains only
genuinely single-adapter exceptions, while Gradle generates the adapter identity used by the shared
factory implementation.

Optional Mod conversion belongs in Lua or a Java implementation of the stable integration
interfaces. `compileVersionAdapterAgainstSdk` and `compileNeoForgeAdapterAgainstSdk` prove the
effective adapter compiles without common-runtime implementation classes. Loader entrypoints may
initialize an event-bus holder and call `ClientBootstrap.start()`; they must not own client state
or business timing.

The `1.21.8` Fabric adapter is cross-compiled for Minecraft 1.21.6–1.21.8. The `26.1` source family
is compiled into the 26.1.2 and 26.2 artifacts; cached compatibility access isolates the client UI,
camera and HUD API changes introduced by 26.2. Do not widen metadata beyond these ranges without
cross-compiling the adapter and checking optional-Mod entrypoints against the real target APIs.

NeoForge compiles every official Minecraft release from 1.20.2 through 26.2 as its own binary target.
The beta-only 1.20.3/1.20.5/1.21.2/1.21.6/1.21.7/1.21.9 targets reuse the proven
1.20.4/1.20.6/1.21.3/1.21.8/1.21.8/1.21.10 source families respectively, but never reuse their
bytecode. Minecraft 1.20.1 is reserved for a future Forge loader implementation and must not add
`net.minecraftforge.*` classes or a second `@Mod` entrypoint to the NeoForge AIO.
