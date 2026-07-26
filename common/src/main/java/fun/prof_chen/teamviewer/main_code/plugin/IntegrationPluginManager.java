package fun.prof_chen.teamviewer.main_code.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Discovers, validates and executes trusted local Lua integration plugins. */
public final class IntegrationPluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationPluginManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> BUILTIN_NAMES = List.of("nodemc", "simmc", "xaero", "journeymap", "example");
    private static final String BUILTIN_ROOT = "teamviewer/plugins/";
    private static final String DISABLED_METADATA = "disabled-plugin.json";
    private static final DateTimeFormatter DISABLED_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final RuntimeGateway platform;
    private final IntegrationRegistry integrations;
    private final Config config;
    private final PluginHostAccess hostAccess;
    private final Path pluginDirectory;
    private final Path disabledPluginDirectory;
    private final PluginStateStore stateStore;
    private final Map<String, Descriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, LuaPluginRuntime> runtimes = new HashMap<>();

    public IntegrationPluginManager(
            RuntimeGateway platform, IntegrationRegistry integrations, Config config) {
        this(platform, integrations, config, PluginHostAccess.empty());
    }

    public IntegrationPluginManager(
            RuntimeGateway platform, IntegrationRegistry integrations, Config config,
            PluginHostAccess hostAccess) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
        this.config = Objects.requireNonNull(config, "config");
        this.hostAccess = Objects.requireNonNull(hostAccess, "hostAccess");
        Path root = platform.getConfigDirectory().resolve("team-view-relay");
        this.pluginDirectory = root.resolve("plugins");
        this.disabledPluginDirectory = root.resolve("plugins-disabled");
        this.stateStore = new PluginStateStore(root.resolve("plugin-state.json"));
        discoverAndLoad();
    }

    public synchronized List<PluginSnapshot> snapshots() {
        return descriptors.values().stream()
                .sorted(Comparator.comparing(descriptor -> descriptor.manifest.name().toLowerCase(Locale.ROOT)))
                .map(this::snapshot).toList();
    }

    public synchronized PluginSnapshot snapshot(String pluginId) {
        Descriptor descriptor = descriptors.get(pluginId);
        return descriptor == null ? null : snapshot(descriptor);
    }

    public synchronized boolean setEnabled(String pluginId, boolean enabled) {
        Descriptor descriptor = descriptors.get(pluginId);
        if (descriptor == null) return false;
        stateStore.setEnabled(pluginId, enabled);
        descriptor.enabled = enabled;
        if (!descriptor.manifest.managedHotToggle()) {
            descriptor.runtimeStatus = PluginRuntimeStatus.PENDING_RESTART;
            descriptor.detail = "Plugin activation change will apply after restart";
            integrations.setPluginRuntime(pluginId, PluginRuntimeStatus.PENDING_RESTART, descriptor.detail);
            return true;
        }
        if (enabled) {
            return loadDescriptor(descriptor, new HashSet<>());
        }
        disableDescriptor(descriptor, PluginRuntimeStatus.DISABLED, "");
        return true;
    }

    public synchronized boolean setSetting(String pluginId, String key, Object rawValue) {
        Descriptor descriptor = descriptors.get(pluginId);
        if (descriptor == null) return false;
        PluginManifest.SettingDefinition definition = descriptor.manifest.settings().stream()
                .filter(value -> value.key().equals(key)).findFirst().orElse(null);
        if (definition == null) return false;
        Object value = normalizeSetting(definition, rawValue);
        descriptor.settings.put(key, value);
        stateStore.setSetting(pluginId, key, value);
        if (definition.restartRequired()) {
            descriptor.runtimeStatus = PluginRuntimeStatus.PENDING_RESTART;
            descriptor.detail = "Setting " + key + " will apply after restart";
            integrations.setPluginRuntime(pluginId, PluginRuntimeStatus.PENDING_RESTART, descriptor.detail);
        } else {
            LuaPluginRuntime runtime = runtimes.get(pluginId);
            if (runtime != null) {
                runtime.globals().set("settings", LuaValueConverters.settings(descriptor.settings));
                runtime.settingsChanged(key, value);
            }
        }
        return true;
    }

    public synchronized boolean rescan() {
        Map<String, Candidate> scanned = discoverCandidates();
        boolean changed = false;
        for (Descriptor descriptor : descriptors.values()) {
            Candidate candidate = scanned.get(descriptor.manifest.id());
            if (candidate == null || !candidate.fingerprint.equals(descriptor.candidate.fingerprint)) {
                descriptor.runtimeStatus = PluginRuntimeStatus.PENDING_RESTART;
                descriptor.detail = "Plugin files changed; restart required";
                integrations.setPluginRuntime(descriptor.manifest.id(), PluginRuntimeStatus.PENDING_RESTART, descriptor.detail);
                changed = true;
            }
        }
        for (String id : scanned.keySet()) {
            if (!descriptors.containsKey(id)) changed = true;
        }
        return changed;
    }

    public synchronized Path copyBuiltin(String pluginId) {
        PluginFileOperationResult result = copyBuiltinResult(pluginId);
        return result.succeeded() ? result.path() : null;
    }

    public synchronized PluginFileOperationResult copyBuiltinResult(String pluginId) {
        Descriptor source = descriptors.get(pluginId);
        if (source == null) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.NOT_FOUND, null, "Unknown plugin " + pluginId);
        if (!source.candidate.builtIn) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.INVALID_SOURCE, source.candidate.source,
                "Only built-in plugins can be copied as templates");
        String newId = nextCustomId(pluginId);
        Path destination = pluginDirectory.resolve(newId);
        try {
            Files.createDirectories(pluginDirectory);
            Files.createDirectories(destination);
            JsonObject manifest = JsonParser.parseString(source.candidate.manifestJson).getAsJsonObject();
            manifest.addProperty("id", newId);
            manifest.addProperty("name", source.manifest.name() + " (Custom)");
            manifest.addProperty("defaultEnabled", false);
            JsonArray provides = manifest.getAsJsonArray("provides");
            Map<String, String> replacements = new LinkedHashMap<>();
            for (int index = 0; index < source.manifest.provides().size(); index++) {
                PluginManifest.CapabilityDeclaration capability = source.manifest.provides().get(index);
                String customCapability = capability.id() + newId.substring(pluginId.length());
                provides.get(index).getAsJsonObject().addProperty("id", customCapability);
                replacements.put(capability.id(), customCapability);
            }
            Files.writeString(destination.resolve("plugin.json"), GSON.toJson(manifest), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> resource : source.candidate.resources.entrySet()) {
                Path target = destination.resolve(resource.getKey()).normalize();
                if (!target.startsWith(destination.normalize())) {
                    throw new IllegalArgumentException("Unsafe built-in resource " + resource.getKey());
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, rewriteCopiedResource(resource.getValue(), replacements),
                        StandardCharsets.UTF_8);
            }
            if (source.manifest.documentation() == null) {
                Files.writeString(destination.resolve("README.md"), customPluginReadme(newId), StandardCharsets.UTF_8);
            }
            return PluginFileOperationResult.success(destination);
        } catch (Exception error) {
            LOGGER.error("Failed to copy built-in plugin {}", pluginId, error);
            return PluginFileOperationResult.failure(PluginFileOperationResult.Code.IO_ERROR, destination, error);
        }
    }

    public synchronized List<DisabledPluginSnapshot> disabledSnapshots() {
        if (!Files.isDirectory(disabledPluginDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(disabledPluginDirectory)) return List.of();
        try (Stream<Path> entries = Files.list(disabledPluginDirectory)) {
            return entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(this::readDisabledSnapshot)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(DisabledPluginSnapshot::disabledAt).reversed())
                    .toList();
        } catch (Exception error) {
            LOGGER.warn("Unable to scan disabled integration plugins {}: {}",
                    disabledPluginDirectory, error.getMessage());
            return List.of();
        }
    }

    public synchronized DisabledPluginSnapshot disabledSnapshot(String storageId) {
        Path storage = resolveDisabledStorage(storageId);
        return storage == null ? null : readDisabledSnapshot(storage);
    }

    public synchronized PluginFileOperationResult uninstall(String pluginId) {
        Descriptor descriptor = descriptors.get(pluginId);
        if (descriptor == null) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.NOT_FOUND, null, "Unknown plugin " + pluginId);
        if (descriptor.candidate.builtIn) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.BUILTIN_READ_ONLY, null,
                "Built-in plugins can only be disabled");
        Path source = validatedPluginSource(descriptor.candidate.source);
        if (source == null || descriptor.disabledStorageId != null) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.INVALID_SOURCE, descriptor.candidate.source,
                "Plugin source is not an installed package");
        String storageId = nextDisabledStorageId(pluginId);
        Path storage = disabledPluginDirectory.resolve(storageId);
        boolean archive = !Files.isDirectory(source);
        Path payload = storage.resolve(archive ? "payload.tvr-plugin" : "payload");
        DisabledMetadata metadata = new DisabledMetadata(
                pluginId, descriptor.manifest.name(), descriptor.manifest.version(),
                source.getFileName().toString(), archive, System.currentTimeMillis());
        boolean storageCreated = false;
        try {
            Files.createDirectories(disabledPluginDirectory);
            if (Files.isSymbolicLink(disabledPluginDirectory)) {
                throw new IllegalArgumentException("Disabled plugin root cannot be a symbolic link");
            }
            Files.createDirectory(storage);
            storageCreated = true;
            Files.writeString(storage.resolve(DISABLED_METADATA), GSON.toJson(metadata), StandardCharsets.UTF_8);
            moveWithoutOverwrite(source, payload);
        } catch (Exception error) {
            if (storageCreated && !Files.isSymbolicLink(disabledPluginDirectory)) {
                cleanupFailedStorage(storage);
            }
            LOGGER.error("Failed to move integration plugin {} to disabled storage", pluginId, error);
            return PluginFileOperationResult.failure(PluginFileOperationResult.Code.IO_ERROR, storage, error);
        }
        descriptor.enabled = false;
        descriptor.disabledStorageId = storageId;
        stateStore.setEnabled(pluginId, false);
        String detail = "Plugin moved to disabled storage; restart required";
        if (descriptor.manifest.managedHotToggle()) {
            disableDescriptor(descriptor, PluginRuntimeStatus.PENDING_RESTART, detail);
        } else {
            descriptor.runtimeStatus = PluginRuntimeStatus.PENDING_RESTART;
            descriptor.detail = detail;
            integrations.setPluginRuntime(pluginId, PluginRuntimeStatus.PENDING_RESTART, detail);
        }
        return PluginFileOperationResult.success(storage);
    }

    public synchronized PluginFileOperationResult restore(String storageId) {
        DisabledPluginSnapshot disabled = disabledSnapshot(storageId);
        if (disabled == null) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.INVALID_DISABLED_ENTRY, null, "Unknown disabled plugin " + storageId);
        Path target = pluginDirectory.resolve(disabled.originalFileName()).normalize();
        if (!target.getParent().equals(pluginDirectory.normalize())) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.INVALID_SOURCE, target, "Unsafe original plugin file name");
        if (Files.exists(target)) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.TARGET_EXISTS, target, "Plugin target already exists");
        Path payload = disabled.storagePath().resolve(disabled.archive() ? "payload.tvr-plugin" : "payload");
        if (!Files.exists(payload)) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.INVALID_DISABLED_ENTRY, payload, "Disabled plugin payload is missing");
        try {
            Files.createDirectories(pluginDirectory);
            moveWithoutOverwrite(payload, target);
            stateStore.setEnabled(disabled.pluginId(), false);
            cleanupRestoredStorage(disabled.storagePath());
            return PluginFileOperationResult.success(target);
        } catch (Exception error) {
            LOGGER.error("Failed to restore disabled integration plugin {}", storageId, error);
            return PluginFileOperationResult.failure(PluginFileOperationResult.Code.IO_ERROR, target, error);
        }
    }

    public synchronized PluginFileOperationResult deleteDisabled(String storageId) {
        DisabledPluginSnapshot disabled = disabledSnapshot(storageId);
        if (disabled == null) return new PluginFileOperationResult(
                PluginFileOperationResult.Code.INVALID_DISABLED_ENTRY, null, "Unknown disabled plugin " + storageId);
        try {
            deleteTree(disabled.storagePath());
            return PluginFileOperationResult.success(disabled.storagePath());
        } catch (Exception error) {
            LOGGER.error("Failed to permanently delete disabled integration plugin {}", storageId, error);
            return PluginFileOperationResult.failure(
                    PluginFileOperationResult.Code.IO_ERROR, disabled.storagePath(), error);
        }
    }

    public Path pluginDirectory() {
        return pluginDirectory;
    }

    public Path disabledPluginDirectory() {
        return disabledPluginDirectory;
    }

    public boolean openDirectory(Path path) {
        if (path == null) return false;
        Path resolved = path.toAbsolutePath().normalize();
        Path plugins = pluginDirectory.toAbsolutePath().normalize();
        Path disabled = disabledPluginDirectory.toAbsolutePath().normalize();
        if ((!resolved.startsWith(plugins) && !resolved.startsWith(disabled)) || !Files.isDirectory(resolved)) {
            return false;
        }
        return platform.openDirectory(resolved);
    }

    public synchronized void shutdown() {
        for (Descriptor descriptor : descriptors.values()) {
            if (runtimes.containsKey(descriptor.manifest.id())) {
                disableDescriptor(descriptor, PluginRuntimeStatus.DISABLED, "");
            }
        }
        runtimes.clear();
    }

    private void discoverAndLoad() {
        try {
            Files.createDirectories(pluginDirectory);
        } catch (Exception error) {
            LOGGER.warn("Unable to create plugin directory {}: {}", pluginDirectory, error.getMessage());
        }
        Map<String, Candidate> candidates = discoverCandidates();
        for (Candidate candidate : candidates.values()) {
            try {
                PluginManifest manifest = GSON.fromJson(candidate.manifestJson, PluginManifest.class).normalized();
                Descriptor descriptor = new Descriptor(manifest, candidate);
                descriptor.enabled = stateStore.enabled(manifest);
                descriptor.settings.putAll(stateStore.settings(manifest));
                descriptors.put(manifest.id(), descriptor);
                for (PluginManifest.CapabilityDeclaration capability : manifest.provides()) {
                    integrations.declare(capability.id(), capability.role(), manifest.id(), capability.name(),
                            integrations.hasImplementation(capability.id())
                                    ? IntegrationSupportStatus.AVAILABLE
                                    : IntegrationSupportStatus.ENTRYPOINT_NOT_READY,
                            integrations.hasImplementation(capability.id()) ? "" : "Plugin implementation is not loaded",
                            manifest.requiredMods(), manifest.loaders(), manifest.minecraftVersions());
                }
            } catch (Exception error) {
                LOGGER.error("Invalid integration plugin manifest from {}: {}", candidate.label, error.getMessage());
            }
        }
        for (Descriptor descriptor : descriptors.values()) {
            if (descriptor.enabled) loadDescriptor(descriptor, new HashSet<>());
            else {
                descriptor.runtimeStatus = PluginRuntimeStatus.DISABLED;
                integrations.setPluginRuntime(descriptor.manifest.id(), PluginRuntimeStatus.DISABLED, "");
            }
        }
    }

    private Map<String, Candidate> discoverCandidates() {
        Map<String, Candidate> result = new LinkedHashMap<>();
        for (String name : BUILTIN_NAMES) {
            Candidate candidate = readBuiltin(name);
            if (candidate != null) putCandidate(result, candidate);
        }
        if (!Files.isDirectory(pluginDirectory)) return result;
        try (var stream = Files.list(pluginDirectory)) {
            stream.sorted().forEach(path -> {
                Candidate candidate = Files.isDirectory(path) ? readDirectory(path)
                        : path.getFileName().toString().endsWith(".tvr-plugin") ? readArchive(path) : null;
                if (candidate != null) putCandidate(result, candidate);
            });
        } catch (Exception error) {
            LOGGER.warn("Unable to scan plugin directory {}: {}", pluginDirectory, error.getMessage());
        }
        return result;
    }

    private void putCandidate(Map<String, Candidate> result, Candidate candidate) {
        try {
            PluginManifest manifest = GSON.fromJson(candidate.manifestJson, PluginManifest.class).normalized();
            Candidate previous = result.get(manifest.id());
            if (previous == null || previous.builtIn && !candidate.builtIn) {
                result.put(manifest.id(), candidate);
            } else {
                LOGGER.error("Duplicate integration plugin id {} from {} and {}", manifest.id(), previous.label, candidate.label);
            }
        } catch (Exception error) {
            LOGGER.error("Unable to identify integration plugin {}: {}", candidate.label, error.getMessage());
        }
    }

    private Candidate readBuiltin(String name) {
        String root = BUILTIN_ROOT + name + "/";
        try {
            String manifest = readResource(root + "plugin.json");
            PluginManifest parsed = GSON.fromJson(manifest, PluginManifest.class).normalized();
            Map<String, String> resources = new LinkedHashMap<>();
            for (String resource : parsed.resourcePaths()) resources.put(resource, readResource(root + resource));
            return candidate(manifest, resources, selectEntrypoint(parsed),
                    null, true, "builtin:" + name);
        } catch (Exception error) {
            LOGGER.error("Unable to load built-in integration plugin {}: {}", name, error.getMessage());
            return null;
        }
    }

    private Candidate readDirectory(Path path) {
        try {
            Path manifestPath = path.resolve("plugin.json");
            if (!Files.isRegularFile(manifestPath)) return null;
            String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
            PluginManifest parsed = GSON.fromJson(manifest, PluginManifest.class).normalized();
            Map<String, String> resources = new LinkedHashMap<>();
            try {
                for (String resource : parsed.resourcePaths()) {
                    Path resourcePath = path.resolve(resource).normalize();
                    if (!resourcePath.startsWith(path.normalize()) || !Files.isRegularFile(resourcePath)) {
                        throw new IllegalArgumentException("Missing or unsafe plugin resource " + resource);
                    }
                    resources.put(resource, Files.readString(resourcePath, StandardCharsets.UTF_8));
                }
            } catch (Exception resourceError) {
                return candidate(manifest, resources, "!resource-error:" + resourceError.getMessage(),
                        path, false, path.toString());
            }
            return candidate(manifest, resources, selectEntrypoint(parsed), path, false, path.toString());
        } catch (Exception error) {
            LOGGER.error("Unable to read integration plugin directory {}: {}", path, error.getMessage());
            return null;
        }
    }

    private Candidate readArchive(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) validateZipEntry(entries.nextElement());
            ZipEntry manifestEntry = zip.getEntry("plugin.json");
            if (manifestEntry == null) throw new IllegalArgumentException("Archive is missing plugin.json");
            String manifest = read(zip, manifestEntry);
            PluginManifest parsed = GSON.fromJson(manifest, PluginManifest.class).normalized();
            Map<String, String> resources = new LinkedHashMap<>();
            try {
                for (String resource : parsed.resourcePaths()) {
                    ZipEntry resourceEntry = zip.getEntry(resource);
                    if (resourceEntry == null || resourceEntry.isDirectory()) {
                        throw new IllegalArgumentException("Archive is missing " + resource);
                    }
                    resources.put(resource, read(zip, resourceEntry));
                }
            } catch (Exception resourceError) {
                return candidate(manifest, resources, "!resource-error:" + resourceError.getMessage(),
                        path, false, path.toString());
            }
            return candidate(manifest, resources, selectEntrypoint(parsed), path, false, path.toString());
        } catch (Exception error) {
            LOGGER.error("Unable to read integration plugin archive {}: {}", path, error.getMessage());
            return null;
        }
    }

    private boolean loadDescriptor(Descriptor descriptor, Set<String> stack) {
        if (descriptor.runtimeStatus == PluginRuntimeStatus.ACTIVE) return true;
        if (!descriptor.enabled) return false;
        if (!stack.add(descriptor.manifest.id())) {
            fail(descriptor, PluginRuntimeStatus.LOAD_FAILED, "Plugin dependency cycle: " + stack);
            return false;
        }
        for (String dependencyId : descriptor.manifest.dependencies()) {
            Descriptor dependency = descriptors.get(dependencyId);
            if (dependency == null || !dependency.enabled || !loadDescriptor(dependency, stack)) {
                fail(descriptor, PluginRuntimeStatus.LOAD_FAILED, "Unavailable plugin dependency: " + dependencyId);
                stack.remove(descriptor.manifest.id());
                return false;
            }
        }
        stack.remove(descriptor.manifest.id());
        if (descriptor.candidate.entryPath != null && descriptor.candidate.entryPath.startsWith("!")) {
            int separator = descriptor.candidate.entryPath.indexOf(':');
            String detail = separator < 0 ? descriptor.candidate.entryPath
                    : descriptor.candidate.entryPath.substring(separator + 1);
            descriptor.manifest.provides().forEach(capability -> integrations.setCapabilitySupport(
                    capability.id(), IntegrationSupportStatus.FAILED, detail));
            fail(descriptor, PluginRuntimeStatus.LOAD_FAILED, detail);
            return false;
        }
        if (!descriptor.manifest.supports(platform.getLoaderId(), platform.getMinecraftVersion())) {
            descriptor.manifest.provides().forEach(capability -> integrations.setCapabilitySupport(
                    capability.id(), IntegrationSupportStatus.UNSUPPORTED_VERSION,
                    "Plugin does not support " + platform.getLoaderId() + " " + platform.getMinecraftVersion()));
            fail(descriptor, PluginRuntimeStatus.INCOMPATIBLE, "Unsupported loader or Minecraft version");
            return false;
        }
        List<String> missingMods = descriptor.manifest.requiredMods().stream()
                .filter(modId -> !platform.isModLoaded(modId)).toList();
        if (!missingMods.isEmpty()) {
            descriptor.manifest.provides().forEach(capability -> integrations.setCapabilitySupport(
                    capability.id(), IntegrationSupportStatus.MOD_NOT_INSTALLED,
                    "Required mods are not installed: " + missingMods));
            fail(descriptor, PluginRuntimeStatus.INCOMPATIBLE, "Required mods are not installed: " + missingMods);
            return false;
        }
        try {
            LuaPluginRuntime runtime = createRuntime(descriptor);
            descriptor.registered.clear();
            runtime.globals().load(descriptor.candidate.script(), "@" + descriptor.manifest.id()
                    + "/" + descriptor.candidate.entryPath).call();
            Set<String> expected = descriptor.manifest.provides().stream()
                    .map(PluginManifest.CapabilityDeclaration::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!descriptor.registered.equals(expected)) {
                throw new IllegalStateException("Declared capabilities " + expected + " but registered " + descriptor.registered);
            }
            runtimes.put(descriptor.manifest.id(), runtime);
            descriptor.runtimeStatus = PluginRuntimeStatus.ACTIVE;
            descriptor.detail = "";
            integrations.setPluginRuntime(descriptor.manifest.id(), PluginRuntimeStatus.ACTIVE, "");
            runtime.enable();
            LOGGER.info("Loaded integration plugin {} {}", descriptor.manifest.id(), descriptor.manifest.version());
            return true;
        } catch (Throwable error) {
            integrations.detachPluginImplementations(descriptor.manifest.id());
            fail(descriptor, PluginRuntimeStatus.LOAD_FAILED,
                    error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            return false;
        }
    }

    private LuaPluginRuntime createRuntime(Descriptor descriptor) {
        Globals globals = JsePlatform.standardGlobals();
        LuaPluginRuntime runtime = new LuaPluginRuntime(descriptor.manifest.id(), globals, LOGGER, this::suspend);
        LuaTable tv = new LuaTable();
        tv.set("use_native_capability", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String providedId = IntegrationIds.canonicalize(args.checkjstring(1));
                String nativeId = IntegrationIds.canonicalize(args.optjstring(2, providedId));
                PluginManifest.CapabilityDeclaration declaration = declaration(descriptor, providedId);
                if (!integrations.hasImplementation(nativeId)) {
                    throw new IllegalArgumentException("Native capability has no registered implementation: " + nativeId);
                }
                if (providedId.equals(nativeId)) {
                    Object implementation = integrations.implementation(nativeId);
                    if (implementation == null) throw new IllegalArgumentException("Native capability unavailable: " + nativeId);
                } else {
                    integrations.bindNativeImplementation(descriptor.manifest.id(), providedId, declaration.role(), nativeId);
                }
                descriptor.registered.add(providedId);
                return LuaValue.TRUE;
            }
        });
        tv.set("register_unavailable_capability", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                LuaTable table = value.checktable();
                String id = IntegrationIds.canonicalize(table.get("id").checkjstring());
                PluginManifest.CapabilityDeclaration declaration = declaration(descriptor, id);
                IntegrationSupportStatus status;
                try {
                    status = IntegrationSupportStatus.valueOf(
                            table.get("status").checkjstring().toUpperCase(Locale.ROOT));
                } catch (Exception error) {
                    throw new IllegalArgumentException("Invalid unavailable capability status for " + id, error);
                }
                integrations.registerPluginUnavailable(descriptor.manifest.id(), id, declaration.role(),
                        status, table.get("detail").optjstring(""));
                descriptor.registered.add(id);
                return LuaValue.TRUE;
            }
        });
        tv.set("register_remote_player_projection", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                LuaTable table = value.checktable();
                String id = IntegrationIds.canonicalize(table.get("id").checkjstring());
                PluginManifest.CapabilityDeclaration declaration = declaration(descriptor, id, IntegrationRole.REMOTE_PLAYER);
                // Legacy "kind" is accepted as an ignored table member for API v1 compatibility.
                LuaRemotePlayerProjection projection = new LuaRemotePlayerProjection(id, runtime,
                        requireFunction(table, "sync"), table.get("clear"), table.get("probe"));
                integrations.registerPluginImplementation(descriptor.manifest.id(), id, declaration.role(), projection,
                        IntegrationImplementationSource.LUA);
                descriptor.registered.add(id);
                return LuaValue.TRUE;
            }
        });
        tv.set("register_shared_waypoint_adapter", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                LuaTable table = value.checktable();
                String id = IntegrationIds.canonicalize(table.get("id").checkjstring());
                PluginManifest.CapabilityDeclaration declaration = declaration(descriptor, id, IntegrationRole.SHARED_WAYPOINT);
                LuaSharedWaypointAdapter adapter = new LuaSharedWaypointAdapter(id, runtime,
                        requireFunction(table, "list_local"), requireFunction(table, "upsert_remote"),
                        requireFunction(table, "delete_remote"), requireFunction(table, "clear_remote"),
                        table.get("probe"));
                integrations.registerPluginImplementation(descriptor.manifest.id(), id, declaration.role(), adapter,
                        IntegrationImplementationSource.LUA);
                descriptor.registered.add(id);
                return LuaValue.TRUE;
            }
        });
        tv.set("register_battle_map_source", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                LuaTable table = value.checktable();
                String id = IntegrationIds.canonicalize(table.get("id").checkjstring());
                PluginManifest.CapabilityDeclaration declaration = declaration(descriptor, id, IntegrationRole.BATTLE_MAP_SOURCE);
                BattleMapSource source = new LuaBattleMapSource(
                        id, runtime, requireFunction(table, "capture"), table.get("probe"));
                integrations.registerPluginImplementation(descriptor.manifest.id(), id, declaration.role(), source,
                        IntegrationImplementationSource.LUA);
                descriptor.registered.add(id);
                return LuaValue.TRUE;
            }
        });
        tv.set("on_enable", callbackSetter(runtime::setOnEnable));
        tv.set("on_disable", callbackSetter(runtime::setOnDisable));
        tv.set("on_settings_changed", callbackSetter(runtime::setOnSettingsChanged));
        tv.set("log", logTable(descriptor.manifest.id()));
        globals.set("tv", tv);
        globals.set("settings", LuaValueConverters.settings(descriptor.settings));
        LuaTable mods = new LuaTable();
        mods.set("is_loaded", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) { return LuaValue.valueOf(platform.isModLoaded(value.checkjstring())); }
        });
        globals.set("mods", mods);
        LuaTable environment = new LuaTable();
        environment.set("loader_id", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(platform.getLoaderId()); }
        });
        environment.set("minecraft_version", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(platform.getMinecraftVersion()); }
        });
        environment.set("mod_version", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                return LuaValue.valueOf(platform.getModVersion(value.checkjstring()));
            }
        });
        globals.set("environment", environment);
        LuaTable services = new LuaTable();
        services.set("get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                String id = value.checkjstring();
                Object service = hostAccess.service(id);
                if (service == null) service = platform.getPluginService(id);
                return LuaValueConverters.toLua(service);
            }
        });
        globals.set("services", services);
        LuaTable snapshots = new LuaTable();
        snapshots.set("world", snapshotFunction("world"));
        snapshots.set("players", snapshotFunction("players"));
        snapshots.set("waypoints", snapshotFunction("waypoints"));
        snapshots.set("scoreboard", snapshotFunction("scoreboard"));
        globals.set("snapshots", snapshots);
        LuaValue luaJava = globals.get("luajava");
        if (luaJava.istable()) {
            LuaTable java = luaJava.checktable();
            LuaValue newInstance = java.get("new").isnil() ? java.get("newInstance") : java.get("new");
            Map<String, LuaValue> typeCache = new HashMap<>();
            Map<String, LuaValue> methodCache = new HashMap<>();
            Map<String, LuaValue> fieldCache = new HashMap<>();
            java.set("type", new OneArgFunction() {
                @Override public LuaValue call(LuaValue value) {
                    String name = value.checkjstring();
                    return typeCache.computeIfAbsent(name, ignored ->
                            CoerceJavaToLua.coerce(resolvePluginClass(name)));
                }
            });
            java.set("method", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    String owner = args.checkjstring(1);
                    String methodName = args.checkjstring(2);
                    List<String> parameters = new ArrayList<>();
                    for (int index = 3; index <= args.narg(); index++) parameters.add(args.checkjstring(index));
                    String key = owner + "#" + methodName + parameters;
                    return methodCache.computeIfAbsent(key, ignored -> CoerceJavaToLua.coerce(
                            findMethod(owner, methodName, parameters)));
                }
            });
            java.set("field", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    String owner = args.checkjstring(1);
                    String fieldName = args.checkjstring(2);
                    String key = owner + "#" + fieldName;
                    return fieldCache.computeIfAbsent(key, ignored -> CoerceJavaToLua.coerce(
                            findField(owner, fieldName)));
                }
            });
            java.set("new", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (!args.arg1().isstring()) return newInstance.invoke(args);
                    LuaValue type = CoerceJavaToLua.coerce(resolvePluginClass(args.checkjstring(1)));
                    return newInstance.invoke(LuaValue.varargsOf(new LuaValue[]{type}, args.subargs(2)));
                }
            });
            java.set("proxy", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    return createJavaProxy(args);
                }
            });
            globals.set("java", java);
        }
        return runtime;
    }

    private ZeroArgFunction snapshotFunction(String snapshotName) {
        return new ZeroArgFunction() {
            @Override public LuaValue call() {
                try {
                    return LuaValueConverters.toLua(hostAccess.snapshot(snapshotName));
                } catch (Throwable error) {
                    throw new IllegalStateException("Unable to capture " + snapshotName + " snapshot", error);
                }
            }
        };
    }

    private Method findMethod(String owner, String name, List<String> parameterNames) {
        try {
            Class<?> type = platform.resolvePluginClass(owner);
            Class<?>[] parameters = new Class<?>[parameterNames.size()];
            for (int index = 0; index < parameters.length; index++) {
                parameters[index] = resolveClass(parameterNames.get(index));
            }
            Method method;
            try {
                method = type.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                method = type.getDeclaredMethod(name, parameters);
                method.trySetAccessible();
            }
            return method;
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("Unable to resolve Java method " + owner + "#" + name, error);
        }
    }

    private Field findField(String owner, String name) {
        try {
            Class<?> type = platform.resolvePluginClass(owner);
            Field field;
            try {
                field = type.getField(name);
            } catch (NoSuchFieldException ignored) {
                field = type.getDeclaredField(name);
                field.trySetAccessible();
            }
            return field;
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("Unable to resolve Java field " + owner + "#" + name, error);
        }
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> platform.resolvePluginClass(name);
        };
    }

    private Class<?> resolvePluginClass(String name) {
        try {
            return platform.resolvePluginClass(name);
        } catch (ClassNotFoundException error) {
            throw new IllegalArgumentException("Unable to resolve Java class " + name
                    + " through the " + platform.getLoaderId() + " mod classloader", error);
        }
    }

    private LuaValue createJavaProxy(Varargs args) {
        int interfaceCount = args.narg() - 1;
        if (interfaceCount < 1) throw new IllegalArgumentException("java.proxy requires an interface and callback table");
        LuaTable callbacks = args.checktable(args.narg());
        Class<?>[] interfaces = new Class<?>[interfaceCount];
        ClassLoader proxyLoader = null;
        for (int index = 0; index < interfaceCount; index++) {
            Class<?> type = resolvePluginClass(args.checkjstring(index + 1));
            if (!type.isInterface()) throw new IllegalArgumentException(type.getName() + " is not a Java interface");
            interfaces[index] = type;
            if (proxyLoader == null && type.getClassLoader() != null) proxyLoader = type.getClassLoader();
        }
        if (proxyLoader == null) proxyLoader = platform.getClass().getClassLoader();
        Object proxy = Proxy.newProxyInstance(proxyLoader, interfaces, (instance, method, javaArgs) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "LuaProxy" + java.util.Arrays.toString(interfaces);
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == (javaArgs == null ? null : javaArgs[0]);
                    default -> null;
                };
            }
            LuaValue callback = callbacks.get(method.getName());
            if (!callback.isfunction()) {
                throw new IllegalStateException("Lua proxy does not implement " + method.getName());
            }
            Object[] values = javaArgs == null ? new Object[0] : javaArgs;
            LuaValue[] luaArgs = new LuaValue[values.length];
            for (int index = 0; index < values.length; index++) {
                luaArgs[index] = CoerceJavaToLua.coerce(values[index]);
            }
            LuaValue result = callback.invoke(LuaValue.varargsOf(luaArgs)).arg1();
            return method.getReturnType() == void.class ? null : CoerceLuaToJava.coerce(result, method.getReturnType());
        });
        return CoerceJavaToLua.coerce(proxy);
    }

    private void disableDescriptor(Descriptor descriptor, PluginRuntimeStatus status, String detail) {
        cleanupImplementations(descriptor.manifest.id());
        LuaPluginRuntime runtime = runtimes.remove(descriptor.manifest.id());
        if (runtime != null) runtime.disable();
        // Keep the dormant callable in the inventory so support can remain AVAILABLE while the
        // independent runtime state is DISABLED/SUSPENDED. Active registry lookups always filter
        // on runtimeStatus, and a later enable atomically replaces the wrapper.
        descriptor.runtimeStatus = status;
        descriptor.detail = detail == null ? "" : detail;
        if (status == PluginRuntimeStatus.PENDING_RESTART) {
            // PENDING_RESTART normally preserves an attached restart-only implementation. A
            // descriptor reaching this method has already been safely stopped, so detach it first.
            integrations.setPluginRuntime(descriptor.manifest.id(), PluginRuntimeStatus.DISABLED, descriptor.detail);
        }
        integrations.setPluginRuntime(descriptor.manifest.id(), status, descriptor.detail);
    }

    private void cleanupImplementations(String pluginId) {
        for (IntegrationCapability capability : integrations.capabilitiesForPlugin(pluginId)) {
            Object implementation = integrations.implementation(capability.id());
            try {
                if (implementation instanceof RemotePlayerProjection projection) projection.clear();
                else if (implementation instanceof SharedWaypointMapAdapter adapter) adapter.clearRemoteWaypoints();
            } catch (Throwable error) {
                LOGGER.warn("Failed to clear integration capability {}: {}", capability.id(), error.getMessage());
            }
        }
    }

    private synchronized void suspend(String pluginId, String detail) {
        Descriptor descriptor = descriptors.get(pluginId);
        if (descriptor == null || descriptor.runtimeStatus == PluginRuntimeStatus.SUSPENDED) return;
        disableDescriptor(descriptor, PluginRuntimeStatus.SUSPENDED, detail);
    }

    private void fail(Descriptor descriptor, PluginRuntimeStatus status, String detail) {
        descriptor.runtimeStatus = status;
        descriptor.detail = detail;
        integrations.setPluginRuntime(descriptor.manifest.id(), status, detail);
        LOGGER.warn("Integration plugin {} is {}: {}", descriptor.manifest.id(), status, detail);
    }

    private PluginSnapshot snapshot(Descriptor descriptor) {
        return new PluginSnapshot(descriptor.manifest.id(), descriptor.manifest.name(), descriptor.manifest.version(),
                descriptor.candidate.builtIn, descriptor.enabled, descriptor.manifest.managedHotToggle(),
                descriptor.runtimeStatus, descriptor.detail, descriptor.candidate.source,
                descriptor.settings, descriptor.manifest.settings(),
                integrations.capabilitiesForPlugin(descriptor.manifest.id()), descriptor.disabledStorageId != null);
    }

    private static Object normalizeSetting(PluginManifest.SettingDefinition definition, Object rawValue) {
        Object value = definition.normalize(rawValue);
        if (value instanceof Number number) {
            double bounded = number.doubleValue();
            if (definition.min() != null) bounded = Math.max(definition.min(), bounded);
            if (definition.max() != null) bounded = Math.min(definition.max(), bounded);
            return "integer".equals(definition.type()) ? (int) bounded : bounded;
        }
        return value;
    }

    private PluginManifest.CapabilityDeclaration declaration(Descriptor descriptor, String id) {
        return descriptor.manifest.provides().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plugin registered undeclared capability: " + id));
    }

    private PluginManifest.CapabilityDeclaration declaration(
            Descriptor descriptor, String id, IntegrationRole expectedRole) {
        PluginManifest.CapabilityDeclaration declaration = declaration(descriptor, id);
        if (!declaration.role().equals(expectedRole.id())) {
            throw new IllegalArgumentException("Capability role mismatch for " + id);
        }
        return declaration;
    }

    private static LuaValue requireFunction(LuaTable table, String key) {
        LuaValue value = table.get(key);
        if (!value.isfunction()) throw new IllegalArgumentException("Lua adapter requires function " + key);
        return value;
    }

    private static OneArgFunction callbackSetter(Consumer<LuaValue> setter) {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                setter.accept(value.checkfunction());
                return LuaValue.TRUE;
            }
        };
    }

    private static LuaTable logTable(String pluginId) {
        LuaTable table = new LuaTable();
        table.set("info", logFunction(message -> LOGGER.info("[{}] {}", pluginId, message)));
        table.set("warn", logFunction(message -> LOGGER.warn("[{}] {}", pluginId, message)));
        table.set("error", logFunction(message -> LOGGER.error("[{}] {}", pluginId, message)));
        return table;
    }

    private static OneArgFunction logFunction(Consumer<String> sink) {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                sink.accept(value.tojstring());
                return LuaValue.NIL;
            }
        };
    }

    private String nextCustomId(String pluginId) {
        String base = pluginId + ".custom";
        String candidate = base;
        for (int index = 2; descriptors.containsKey(candidate) || Files.exists(pluginDirectory.resolve(candidate)); index++) {
            candidate = base + index;
        }
        return candidate;
    }

    private String nextDisabledStorageId(String pluginId) {
        String base = pluginId + "-" + DISABLED_TIMESTAMP.format(LocalDateTime.now());
        String candidate = base;
        for (int index = 2; Files.exists(disabledPluginDirectory.resolve(candidate)); index++) {
            candidate = base + "-" + index;
        }
        return candidate;
    }

    private Path validatedPluginSource(Path source) {
        if (source == null) return null;
        Path normalized = source.toAbsolutePath().normalize();
        Path root = pluginDirectory.toAbsolutePath().normalize();
        return normalized.getParent() != null && normalized.getParent().equals(root)
                && Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(normalized) ? normalized : null;
    }

    private Path resolveDisabledStorage(String storageId) {
        if (storageId == null || !storageId.matches("[a-zA-Z0-9_.-]+")) return null;
        if (Files.isSymbolicLink(disabledPluginDirectory)) return null;
        Path root = disabledPluginDirectory.toAbsolutePath().normalize();
        Path resolved = root.resolve(storageId).normalize();
        return resolved.getParent() != null && resolved.getParent().equals(root)
                && Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(resolved) ? resolved : null;
    }

    private DisabledPluginSnapshot readDisabledSnapshot(Path storage) {
        try {
            Path normalized = resolveDisabledStorage(storage.getFileName().toString());
            if (normalized == null) return null;
            DisabledMetadata metadata = GSON.fromJson(
                    Files.readString(normalized.resolve(DISABLED_METADATA), StandardCharsets.UTF_8),
                    DisabledMetadata.class);
            if (metadata == null || metadata.pluginId == null || metadata.originalFileName == null
                    || !metadata.originalFileName.equals(Path.of(metadata.originalFileName).getFileName().toString())) {
                throw new IllegalArgumentException("Invalid disabled plugin metadata");
            }
            Path payload = normalized.resolve(metadata.archive ? "payload.tvr-plugin" : "payload");
            if (!Files.exists(payload, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(payload)) {
                throw new IllegalArgumentException("Disabled plugin payload is missing or unsafe");
            }
            return new DisabledPluginSnapshot(normalized.getFileName().toString(), metadata.pluginId,
                    Objects.requireNonNullElse(metadata.name, metadata.pluginId),
                    Objects.requireNonNullElse(metadata.version, "0.0.0"), metadata.originalFileName,
                    metadata.archive, metadata.disabledAt, normalized);
        } catch (Exception error) {
            LOGGER.warn("Ignoring invalid disabled plugin entry {}: {}", storage, error.getMessage());
            return null;
        }
    }

    private static void cleanupFailedStorage(Path storage) {
        try {
            if (Files.isDirectory(storage)) deleteTree(storage);
        } catch (Exception ignored) { }
    }

    private static void cleanupRestoredStorage(Path storage) {
        try {
            Files.deleteIfExists(storage.resolve(DISABLED_METADATA));
            Files.deleteIfExists(storage);
        } catch (Exception error) {
            LOGGER.warn("Restored plugin but could not remove disabled-storage metadata {}: {}",
                    storage, error.getMessage());
        }
    }

    private static void moveWithoutOverwrite(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String customPluginReadme(String pluginId) {
        return """
                # TeamViewRelay custom integration plugin / 自定义集成插件

                Plugin ID / 插件 ID: `%s`

                ## 中文指引

                1. 在 `plugin.json` 中修改插件名称、版本、依赖、设置和 `provides` 能力声明。
                2. 在 `main.lua` 中绑定原生能力，或注册玩家投影、共享路标适配器、战局地图源。
                3. Lua 实际注册的能力 ID 和角色必须与 `provides` 完全一致。
                4. 修改完成后重启客户端；复制出的插件默认关闭，请在插件管理页手动启用。

                常用 API：`tv.use_native_capability`、`tv.register_remote_player_projection`、
                `tv.register_shared_waypoint_adapter`、`tv.register_battle_map_source`、
                `tv.on_enable`、`tv.on_disable`、`tv.on_settings_changed`、`mods.is_loaded`、
                `snapshots.world/players/waypoints/scoreboard`、`java.type/method/field/new/proxy`。

                生命周期、设置与快照示例：

                ```lua
                tv.on_enable(function() tv.log.info("enabled") end)
                tv.on_disable(function() tv.log.info("disabled") end)
                tv.on_settings_changed(function(key, value)
                  tv.log.info("setting " .. key .. " changed")
                end)
                local enabled = settings.enabled_marker
                local world = snapshots.world()
                local players = snapshots.players()
                local scoreboard = snapshots.scoreboard()
                -- 将自定义 provides ID 绑定到内置高频 Java 桥：
                -- tv.use_native_capability("custom-capability-id", "nodemc-scoreboard-battle-map")
                ```

                ## English guide

                1. Edit `plugin.json` to configure metadata, dependencies, settings and `provides` declarations.
                2. Edit `main.lua` to bind a native capability or register a Lua adapter.
                3. Every Lua capability ID and role must exactly match `provides`.
                4. Restart the client after editing. Copied plugins are disabled by default.

                Lifecycle, settings, native bridge and snapshot APIs:

                ```lua
                tv.on_enable(function() tv.log.info("enabled") end)
                tv.on_disable(function() tv.log.info("disabled") end)
                tv.on_settings_changed(function(key, value) end)
                local enabled = settings.enabled_marker
                local world = snapshots.world()
                local players = snapshots.players()
                local waypoints = snapshots.waypoints()
                -- tv.use_native_capability("custom-capability-id", "nodemc-scoreboard-battle-map")
                ```

                Minimal battle-map source:

                ```lua
                tv.register_battle_map_source({
                  id = "replace-with-a-provides-id",
                  capture = function()
                    return nil
                  end
                })
                ```
                """.formatted(pluginId);
    }

    private static Candidate candidate(
            String manifest, Map<String, String> resources, String entryPath,
            Path source, boolean builtIn, String label) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(manifest.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, String> resource : resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            digest.update(resource.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update(resource.getValue().getBytes(StandardCharsets.UTF_8));
        }
        String fingerprint = java.util.HexFormat.of().formatHex(digest.digest());
        return new Candidate(manifest, Map.copyOf(resources), entryPath, source, builtIn, label, fingerprint);
    }

    private static String rewriteCopiedResource(String content, Map<String, String> replacements) {
        String result = content;
        int token = 0;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            String oldId = replacement.getKey();
            String newId = replacement.getValue();
            String nativeDouble = "__TVR_NATIVE_DOUBLE_" + token + "__";
            String nativeSingle = "__TVR_NATIVE_SINGLE_" + token + "__";
            result = result.replace("tv.use_native_capability(\"" + oldId + "\")",
                    "tv.use_native_capability(\"" + newId + "\", \"" + nativeDouble + "\")");
            result = result.replace("tv.use_native_capability('" + oldId + "')",
                    "tv.use_native_capability('" + newId + "', '" + nativeSingle + "')");
            result = result.replace("\"" + oldId + "\"", "\"" + newId + "\"")
                    .replace("'" + oldId + "'", "'" + newId + "'")
                    .replace(nativeDouble, oldId).replace(nativeSingle, oldId);
            token++;
        }
        return result;
    }

    private String selectEntrypoint(PluginManifest manifest) {
        try {
            return manifest.selectedEntrypoint(platform.getLoaderId(), platform.getMinecraftVersion());
        } catch (Exception error) {
            return "!selection-error:" + error.getMessage();
        }
    }

    private static String readResource(String path) throws Exception {
        try (InputStream stream = IntegrationPluginManager.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String read(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream stream = zip.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void validateZipEntry(ZipEntry entry) {
        String name = entry.getName().replace('\\', '/');
        if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
            throw new IllegalArgumentException("Unsafe archive entry: " + entry.getName());
        }
    }

    private static final class Descriptor {
        private final PluginManifest manifest;
        private final Candidate candidate;
        private final Map<String, Object> settings = new LinkedHashMap<>();
        private final Set<String> registered = new LinkedHashSet<>();
        private boolean enabled;
        private PluginRuntimeStatus runtimeStatus = PluginRuntimeStatus.DISABLED;
        private String detail = "";
        private String disabledStorageId;

        private Descriptor(PluginManifest manifest, Candidate candidate) {
            this.manifest = manifest;
            this.candidate = candidate;
        }
    }

    private record Candidate(
            String manifestJson, Map<String, String> resources, String entryPath,
            Path source, boolean builtIn, String label, String fingerprint) {
        private String script() {
            String value = resources.get(entryPath);
            if (value == null) throw new IllegalStateException("No selected plugin entrypoint: " + entryPath);
            return value;
        }
    }

    private record DisabledMetadata(
            String pluginId, String name, String version, String originalFileName,
            boolean archive, long disabledAt) { }
}
