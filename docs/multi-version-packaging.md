# TeamViewRelay Multi-version Packaging Contract

This document is normative for AI agents and contributors changing Minecraft targets or release
packaging. Read it together with `adapter-sdk.md` before editing client code.

## Published artifacts

Each manifest target produces a complete standalone Fabric Mod. It contains the Java 17 bootstrap,
Java 17 common SDK/runtime, one target adapter and all required nested libraries. It must work by
itself and must not expose the internal adapter Mod ID.

The All-in-One Jar contains the same bootstrap/common/library payload once and one remapped slim
adapter per target under `META-INF/jars`. Slim adapters are internal build artifacts only. Never
publish them or make standalone depend on a separately installed adapter Jar.

## Ownership

- Common owns every state machine, algorithm, page definition, rendering decision, network packet,
  repository and configuration behavior.
- `fabric-bootstrap` owns only Fabric Loader startup, discovery of exactly one
  `ClientAdapterFactory`, runtime startup and Adapter TCK reporting.
- A version adapter owns only native API conversion, event forwarding, native rendering/widgets,
  Mixin hooks and optional-mod API calls.
- `universal` owns only metadata generation, ZIP assembly and artifact verification.

Do not fix a version by copying business logic into an adapter. If a native capability is missing,
extend the platform-neutral SDK first and implement the new port for every mandatory target.

## Java boundary

Shared code is compiled with `--release 17`. Target profiles separately declare the game Java,
adapter bytecode release and Gradle runtime Java. Adapters may use their target language level but
must not leak those classes into the All-in-One root. A target whose runtime is below Java 17 is a
legacy-runtime project, not a manifest-only adapter addition.

## Packaging flow

Loom compiles/remaps each target exactly once. `remapAdapterJar` extracts version classes,
`ClientAdapterFactory` service metadata and target Mixin resources from that production output.
Standalone keeps the production adapter flattened with shared nested libraries. All-in-One keeps
each slim adapter isolated as a nested Mod candidate with ID `team-view-relay-adapter` and exact
Minecraft/Java/Loader/Fabric API constraints.

Shared common and third-party nested Jars use Loom's `processIncludeJars` output so each carries
Fabric Loader metadata. The `universal` project does not apply Loom and never recompiles/remaps an
adapter; `package-all` consumes the slim adapters already staged by target builds.

The public All-in-One container retains ID `team-view-relay` and depends on one compatible internal
adapter. Unsupported Minecraft/Java combinations must fail during Fabric dependency resolution,
before bootstrap or adapter class loading.

## Required checks

Run `task build`. It must leave exactly one standalone per manifest target and one All-in-One in
`build-artifacts`. Adapter hashes and target data in `META-INF/teamviewer/targets.json` must match
the nested bytes. Shared libraries occur once, root/shared bytecode is Java 17-compatible, each
adapter has exactly one Factory provider and non-empty Mixin configuration, and no slim adapter
contains common/runtime/protocol/third-party classes.
