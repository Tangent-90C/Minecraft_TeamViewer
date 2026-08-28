package fun.prof_chen.teamviewer.main_code.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamviewerModMetadataTest {
    @Test
    void formatsBackendProgramVersionWithModAndMinecraftVersions() {
        assertEquals("team-view-relay", TeamviewerModMetadata.MOD_ID);
        assertEquals(
                "team-view-relay-mod-v0.8.7-proto0.7.1-mc26.1.2",
                TeamviewerModMetadata.clientProgramVersion(
                        "v0.8.7-proto0.7.1", "26.1.2"));
    }

    @Test
    void keepsDevelopmentFallbackCompactAndNormalizesMissingMinecraftVersion() {
        assertEquals(
                "team-view-relay-mod-dev-mcunknown",
                TeamviewerModMetadata.clientProgramVersion(
                        TeamviewerModMetadata.MOD_VERSION_FALLBACK, " "));
    }
}
