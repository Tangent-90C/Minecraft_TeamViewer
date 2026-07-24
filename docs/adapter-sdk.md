# TeamViewRelay Common Adapter SDK

`common` is the only client business implementation. `common-sdk` is a separately compiled API
surface selected from the platform-neutral SDK sources under `common/src/main/java`. A Minecraft
adapter is compiled against `common-sdk` without `common-runtime`: it reads native state into
immutable snapshots, forwards Fabric events, executes render/UI commands, and calls optional-map
APIs.

## Composition contract

Every version exposes exactly one `ClientAdapterFactory<W, H>` through Java `ServiceLoader`. The
factory constructs a non-null `ClientAdapterBundle<W, H>`; the shared Fabric bootstrap starts the
common runtime. `W` and `H` are the native world/HUD render contexts, so event/sink mismatches are
compile errors rather than runtime casts.

- `RuntimeGateway` supplies protocol/program metadata, the local identity and dimension, the
  log directory and config directory. Calls may originate on the network thread unless a method
  explicitly documents otherwise; returned paths are absolute or stable process-relative paths.
- `GameClientBridge` is read on the Minecraft client thread. Coordinates are world block
  coordinates in doubles, velocities are blocks/tick, dimensions are canonical IDs such as
  `minecraft:overworld`, and absent worlds/players return the relevant `unavailable()` snapshot.
  It must never return `null` collections. Scoreboard lines are already team-decorated and kept
  in display order; text runs preserve native style colors as a name or `#RRGGBB`.
  Quick-mark targeting must resolve the first visible block/entity at the requested range. Versions
  whose prepared crosshair hit is interaction-limited perform their own native raycast, stop at the
  first block obstruction, and hard-limit traversal with `MARK_TARGET_MAX_DISTANCE`.
- `ClientEventBridge<W,H>` registers tick, toggle/config/mark input, remote join, disconnect, stopping,
  world render and HUD render exactly once. Callbacks run on Fabric's client/render threads and
  must not retain render contexts after the callback returns. `registeredEvents()` must report
  every `ClientEventType` after registration.
- `WorldRenderSink<W>` and `HudRenderSink<H>` execute immutable common frames. They perform native
  coordinate/widget calls only and must not inspect config, repositories or network state.
- `ConfigScreenHost` opens the native widget host. Page definitions, values, validation,
  save/cancel behavior, status actions and layout come from `ConfigUiSession`.
- `BattleMapNativeBridge` converts SimMC native region data into absolute chunk cells. When the
  mod is absent it returns `isAvailable=false`; it is still a required, non-null port. NodeMC
  parsing, history alignment, projection, semantic hashes and upload timing live in common.
- `MapAdapterBundle` contains the integrations supported by that Minecraft target. Each port
  reports `AVAILABLE`, `MOD_NOT_INSTALLED`, `UNSUPPORTED_VERSION`, or `FAILED`. An unsupported
  optional plugin does not block the core Jar, but must be explicit in the capability report.
  When an API Jar is absent at runtime, factories must install `UnavailableRemotePlayerProjection`
  or `UnavailableSharedWaypointMapAdapter` before touching the optional API class. This prevents
  capability inspection and session cleanup from accidentally loading an uninstalled mod.

There is no self-declared user feature list. Common-owned behavior is present once in the runtime;
mandatory native abilities are represented by non-null typed bundle fields, while optional
abilities are derived from integration ports.

## Lifecycle and error rules

Adapters return unavailable/empty snapshots for normal lifecycle gaps (title screen, world
transition, optional mod absent). Invalid adapter construction is a programming error and throws
immediately. Reflection/API failures in optional integrations are contained by that adapter and
reported as `FAILED` with a reason or logged once; they must not crash the client tick or fail the
core Adapter TCK. Snapshot, event, config-page and mandatory native-port violations remain hard
TCK failures.

Common owns connection state, reporting clocks, repository cleanup, input state machines,
waypoint TTL/diff policy, render decisions, HUD text and battle-map upload policy. Version code
must not import `Config`, `NetworkManager`, protocol messages, repositories or coordinators.

## TCK and target builds

The shared bootstrap runs `AdapterTck` after registration. It probes title-screen snapshots,
mandatory event registration, all seven config pages and integration status, then writes
`teamviewer-adapter-capabilities.json` under the target log directory. Set
`-Dteamviewer.adapterTck.strict=true` in development/CI to fail startup on a contract violation.

Minecraft targets are declared only in `gradle/minecraft-versions.properties`. Task and CI read
that manifest through `scripts/minecraft_targets.py`; `task build` iterates every target, while
`task build-target TARGET=<version>` builds one profile in its isolated Gradle build directory.
Task starts each target in a separate no-daemon Gradle process so Loom and mapping services from
one target cannot leak into the next target.

## 1.21.8 reference index

The complete reference adapter is under `fabric/src/version/1.21.8`:

- Bundle assembly: `main_code/core/FabricClientAdapterFactory.java`
- Lifecycle/input/render events: `main_code/client/bridge/FabricClientEventBridge.java`
- Player/entity/camera/target/scoreboard snapshots:
  `main_code/client/bridge/FabricGameClientBridge.java`
- Runtime paths and metadata: `main_code/network/bridge/FabricRuntimeGateway.java`
- Common frame executors: `main_code/render/FabricWorldRenderSink.java` and
  `main_code/render/FabricHudRenderSink.java`
- Seven-page widget host: `main_code/screen/ConfigScreen.java`
- SimMC snapshot port: `main_code/battlemap/FabricBattleMapNativeBridge.java`
- NodeMC packet clock: `mixin/client/ClientPlayNetworkHandlerMixin.java`
- JourneyMap/Xaero ports: `main_code/mapbridge/provider/*`

To add a version, declare its manifest profile, copy the reference directory shape, implement each
port against that version's native API, register its factory in `META-INF/services`, and run
`task build`. The `compileVersionAdapterAgainstSdk` task proves the adapter compiles without
common-runtime implementation classes.
