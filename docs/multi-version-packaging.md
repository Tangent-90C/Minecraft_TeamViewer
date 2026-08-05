# TeamViewRelay Multi-version Packaging Contract

This document is normative for AI agents and contributors changing Minecraft targets or release
packaging. Read it together with `adapter-sdk.md` before editing client code.

## Published artifacts

Each manifest target produces a complete standalone Fabric Mod and a complete standalone NeoForge
Mod. Each contains the Java 17 Loader-neutral bootstrap,
Java 17 common SDK/runtime, one target adapter and all required nested libraries. It must work by
itself and must not expose the internal adapter Mod ID.

The Fabric All-in-One Jar contains the same bootstrap/common/library payload once and one remapped slim
adapter per target under `META-INF/jars`. A slim adapter may declare a cross-compiled and verified
Minecraft compatibility range; its target ID remains the version it was built against. Slim
adapters are internal build artifacts only. Never publish them or make standalone depend on a
separately installed adapter Jar.

The experimental NeoForge All-in-One contains one Java 17 outer Mod and one isolated `GAMELIBRARY`
per NeoForge target. The outer Mod is the only `team_view_relay` Mod candidate and the only
`ClientAdapterFactory` provider. It selects one relocated entrypoint, Factory and Mixin from the
runtime Minecraft version; all other adapter classes remain unloaded.

## Ownership

- Common owns every state machine, algorithm, page definition, rendering decision, network packet,
  repository and configuration behavior.
- `client-bootstrap` owns unique Factory discovery, runtime startup and Adapter TCK reporting.
- `fabric-bootstrap` and the NeoForge `@Mod` class are thin Loader entrypoints only.
- A version adapter owns only native API conversion, event forwarding, native rendering/widgets,
  Mixin hooks and optional-mod API calls.
- `neoforge-adapter` owns the only NeoForge shared/compat/version source hierarchy. ModDev and
  legacy UserDev compile the same selected source plan with their target-specific toolchains.
- `universal` owns Fabric metadata generation, ZIP assembly and artifact verification.
- `neoforge-aio` owns the NeoForge Java 17 dispatcher, target table, Mixin plugin, JarJar assembly and
  AIO verification. `adapter-relocator` may rewrite only class names produced by the selected adapter;
  it must leave Common SDK references unchanged.

Do not fix a version by copying business logic into an adapter. If a native capability is missing,
extend the platform-neutral SDK first and implement the new port for every mandatory target.

## Java boundary

Shared code is compiled with `--release 17`. Target profiles separately declare the game Java,
adapter bytecode release and Gradle runtime Java. Adapters may use their target language level but
must not leak those classes into the All-in-One root. A target whose runtime is below Java 17 is a
legacy-runtime project, not a manifest-only adapter addition.

## Shared dependency policy

Shared library versions are declared once in `gradle.properties`; Fabric, NeoForge, common and the
universal packager must reference those properties instead of copying version literals. Compatible
newer security/patch releases take precedence over old strict dependency constraints only when the
library is actually owned by TeamViewRelay. Host-provided JPMS libraries such as Jackson must not be
embedded: NeoForge would load both copies as named modules and fail before mod construction. Major
runtime migrations such as OkHttp 4 to 5 or Kotlin 1 to 2 require network/gameplay regression testing
and must not be introduced only to satisfy a "latest" version check.

## Packaging flow

Loom compiles/remaps each target exactly once. `remapAdapterJar` extracts version classes,
`ClientAdapterFactory` service metadata and target Mixin resources from that production output.
Standalone keeps the production adapter flattened with shared nested libraries. All-in-One keeps
each slim adapter isolated as a nested Mod candidate with ID `team-view-relay-adapter` and verified
Minecraft plus Java/Loader/Fabric API constraints.

Shared common and third-party nested Jars use Loom's `processIncludeJars` output so each carries
Fabric Loader metadata. The `universal` project does not apply Loom and never recompiles/remaps an
adapter; `package-all` consumes the slim adapters already staged by target builds.

The public All-in-One container retains ID `team-view-relay` and depends on one compatible internal
adapter. Unsupported Minecraft/Java combinations must fail during Fabric dependency resolution,
before bootstrap or adapter class loading.

NeoForge standalone packaging uses ModDevGradle or legacy UserDev JarJar. It embeds common-sdk, common and required
third-party dependencies once, flattens the selected NeoForge adapter and the Java 17
`client-bootstrap`, and never contains Fabric metadata or classes. Each target build also extracts
only its production-mapped adapter classes into a raw slim Jar. The AIO packager relocates every raw
class into a target-private package, strips standalone metadata and embeds the result with
`FMLModType: GAMELIBRARY`, a unique module name and a unique JarJar coordinate.
Both build frontends use the same raw-adapter metadata, extraction and bytecode verification
support. Source reuse never implies binary reuse: every manifest target is compiled separately
and remains an isolated `GAMELIBRARY` entry in the AIO. Beta targets pin a reproducible compile
version while their metadata permits updates only inside the same Minecraft/NeoForge release line.

The NeoForge AIO root remains Java 17. Its Mixin plugin returns only the selected relocated Mixin and
raises Mixin's compatibility level to the selected adapter's Java release before that class is read.
The root carries equivalent loader metadata in the two schemas required by the supported FML span:
FML 1 reads `META-INF/mods.toml` with `mandatory`, while current FML reads
`META-INF/neoforge.mods.toml` with `type`. Both descriptors must declare the same single Mod, version
range and dynamic Mixin config; nested adapters must contain neither descriptor.
Shared runtime and third-party JarJar dependencies come from one canonical NeoForge standalone and
occur once. There is no cross-Loader Jar. Fabric keeps the public Mod ID
`team-view-relay`; NeoForge uses `team_view_relay` because FML rejects hyphens in Mod IDs. This
Loader-specific metadata difference must not leak into configuration names, protocol identity or
common business behavior.

## Required checks

Run `task build`. It must leave one Fabric and one NeoForge standalone per manifest target, plus
one Fabric All-in-One and one experimental NeoForge All-in-One Jar, in `build-artifacts`. The expected set is derived from the normalized
manifest rather than documented as a fixed count. Legacy filenames without `Fabric` or
`NeoForge` are forbidden. Fabric adapter hashes and target data in `META-INF/teamviewer/targets.json` must match
the nested bytes. Shared libraries occur once, root/shared bytecode is Java 17-compatible, each
adapter has exactly one Factory provider and non-empty Mixin configuration, and no slim adapter
contains common/runtime/protocol/third-party classes.

NeoForge AIO checks additionally require exactly the manifest-declared private `GAMELIBRARY` adapters, one root Mod,
one root Factory provider, one dynamic Mixin configuration, unique modules/packages/coordinates and
matching hashes in `META-INF/teamviewer/neoforge-targets.json`. AIO and standalone remain separate
public choices during the experimental rollout and must never be installed together.

Only files accepted by `scripts/minecraft_targets.py verify-release-set` are public release Jars.
Generate `build-artifacts/SHA256SUMS` from that verified set and upload it with every Jar. The release
tag must exactly match `mod_version`; protocol, backend and web versions move independently unless
their compatibility contract changed. The complete maintainer checklist is in `docs/releasing.md`.
