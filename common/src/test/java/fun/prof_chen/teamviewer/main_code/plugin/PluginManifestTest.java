package fun.prof_chen.teamviewer.main_code.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManifestTest {
    @Test
    void selectsTheOnlyHighestPriorityEntrypointAndKeepsEveryResource() {
        PluginManifest manifest = manifest(List.of(
                new PluginManifest.EntrypointDefinition("fallback.lua", List.of(), List.of(), -10),
                new PluginManifest.EntrypointDefinition(
                        "fabric.lua", List.of("fabric"), List.of("1.21.8"), 10))).normalized();

        assertEquals("fabric.lua", manifest.selectedEntrypoint("fabric", "1.21.8"));
        assertEquals("fallback.lua", manifest.selectedEntrypoint("neoforge", "1.21.8"));
        assertEquals(List.of("legacy.lua", "fallback.lua", "fabric.lua", "README.md"),
                manifest.resourcePaths());
    }

    @Test
    void returnsNoEntrypointWhenNoVariantMatches() {
        PluginManifest manifest = manifest(List.of(new PluginManifest.EntrypointDefinition(
                "neoforge.lua", List.of("neoforge"), List.of("1.21.8"), 0))).normalized();

        assertNull(manifest.selectedEntrypoint("fabric", "1.21.8"));
    }

    @Test
    void rejectsAmbiguousEntrypointsAtTheSamePriority() {
        PluginManifest manifest = manifest(List.of(
                new PluginManifest.EntrypointDefinition(
                        "one.lua", List.of("fabric"), List.of("1.21.8"), 5),
                new PluginManifest.EntrypointDefinition(
                        "two.lua", List.of("fabric"), List.of("1.21.8"), 5))).normalized();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manifest.selectedEntrypoint("fabric", "1.21.8"));
        assertTrue(error.getMessage().contains("one.lua"));
        assertTrue(error.getMessage().contains("two.lua"));
    }

    @Test
    void rejectsUnsafeEntrypointAndDocumentationPaths() {
        assertThrows(IllegalArgumentException.class, () -> manifest(List.of(
                new PluginManifest.EntrypointDefinition("../escape.lua", List.of(), List.of(), 0))).normalized());
        PluginManifest unsafeDocumentation = new PluginManifest(
                1, "1", "custom.manifest", "Manifest", "1.0.0", "legacy.lua", true,
                "managed", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PluginManifest.CapabilityDeclaration(
                        "custom-manifest-map", "battle-map-source", "Manifest map")),
                List.of(), List.of(), "../README.md");
        assertThrows(IllegalArgumentException.class, unsafeDocumentation::normalized);
    }

    private static PluginManifest manifest(List<PluginManifest.EntrypointDefinition> entrypoints) {
        return new PluginManifest(
                1, "1", "custom.manifest", "Manifest", "1.0.0", "legacy.lua", true,
                "managed", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PluginManifest.CapabilityDeclaration(
                        "custom-manifest-map", "battle-map-source", "Manifest map")),
                List.of(), entrypoints, "README.md");
    }
}
