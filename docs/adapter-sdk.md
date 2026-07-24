# TeamViewRelay Common Adapter SDK

`common/src/main/java` is the only client business implementation. A Minecraft adapter is a
translation layer: it reads native state into immutable common snapshots, forwards Fabric
events, executes common render/UI commands, and calls optional-map APIs.

## Composition contract

Every version constructs one non-null `ClientAdapterBundle` in its thin `PlayerProcesses`:

- `RuntimeGateway` supplies protocol/program metadata, the local identity and dimension, the
  log directory and config directory. Calls may originate on the network thread unless a method
  explicitly documents otherwise; returned paths are absolute or stable process-relative paths.
- `GameClientBridge` is read on the Minecraft client thread. Coordinates are world block
  coordinates in doubles, velocities are blocks/tick, dimensions are canonical IDs such as
  `minecraft:overworld`, and absent worlds/players return the relevant `unavailable()` snapshot.
  It must never return `null` collections. Scoreboard lines are already team-decorated and kept
  in display order; text runs preserve native style colors as a name or `#RRGGBB`.
- `ClientEventBridge` registers tick, toggle/config/mark input, remote join, disconnect, stopping,
  world render and HUD render exactly once. Callbacks run on Fabric's client/render threads and
  must not retain render contexts after the callback returns.
- `WorldRenderSink` and `HudRenderSink` execute immutable common frames. They perform native
  coordinate/widget calls only and must not inspect config, repositories or network state.
- `ConfigScreenHost` opens the native widget host. Page definitions, values, validation,
  save/cancel behavior, status actions and layout come from `ConfigUiSession`.
- `BattleMapNativeBridge` converts SimMC native region data into absolute chunk cells. When the
  mod is absent it returns `isAvailable=false`; it is still a required, non-null port. NodeMC
  parsing, history alignment, projection, semantic hashes and upload timing live in common.
- `MapAdapterBundle` always includes JourneyMap and Xaero remote-player and shared-waypoint
  ports. Optional-mod absence is represented by each port's `isAvailable=false`, never by an
  empty list or `null`.

`ClientAdapterDescriptor.complete(adapterVersion)` declares the full `ClientFeature` matrix.
Adding a feature to that enum makes every adapter incomplete until its bundle and the build guard
are updated.

## Lifecycle and error rules

Adapters return unavailable/empty snapshots for normal lifecycle gaps (title screen, world
transition, optional mod absent). Invalid adapter construction is a programming error and throws
immediately. Reflection/API failures in optional integrations are contained by that adapter and
reported as unavailable or logged once; they must not crash the client tick.

Common owns connection state, reporting clocks, repository cleanup, input state machines,
waypoint TTL/diff policy, render decisions, HUD text and battle-map upload policy. Version code
must not import `Config`, `NetworkManager`, protocol messages, repositories or coordinators.

## 1.21.8 reference index

The complete reference adapter is under `fabric/src/version/1.21.8`:

- Bundle assembly: `main_code/core/PlayerProcesses.java`
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

To add a version, copy this directory shape, implement each port against that version's native
API, use the same common bundle, and run `verifyAdapterSdkCompleteness` plus both target builds.
