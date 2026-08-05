package fun.prof_chen.teamviewer.neoforge.aio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeAioSelectorTest {
    @Test
    void selectsExactlyOneAdapterForEveryOfficialSupportedMinecraftRelease() {
        List<String> releases = List.of(
                "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
                "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5",
                "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
                "26.1", "26.1.1", "26.1.2", "26.2");

        assertEquals(19, NeoForgeAioTargets.ALL.size());
        releases.forEach(release -> assertEquals(
                1,
                NeoForgeAioTargets.ALL.stream()
                        .filter(target -> NeoForgeAioSelector.targetFor(release).equals(target))
                        .count(),
                release));
    }

    @Test
    void selectsExactLegacyAndModernTargets() {
        assertEquals("1.20.2", NeoForgeAioSelector.targetFor("1.20.2").minecraft());
        assertEquals("1.20.3", NeoForgeAioSelector.targetFor("1.20.3").minecraft());
        assertEquals("1.20.5", NeoForgeAioSelector.targetFor("1.20.5").minecraft());
        assertEquals("1.21.2", NeoForgeAioSelector.targetFor("1.21.2").minecraft());
        assertEquals("1.21.6", NeoForgeAioSelector.targetFor("1.21.6").minecraft());
        assertEquals("1.21.7", NeoForgeAioSelector.targetFor("1.21.7").minecraft());
        assertEquals("1.21.9", NeoForgeAioSelector.targetFor("1.21.9").minecraft());
        assertEquals("1.21.11", NeoForgeAioSelector.targetFor("1.21.11").minecraft());
        assertEquals("26.2", NeoForgeAioSelector.targetFor("26.2").minecraft());
    }

    @Test
    void mapsTheWholeTwentySixOneRuntimeFamilyToItsOwningAdapter() {
        assertEquals("26.1.2", NeoForgeAioSelector.targetFor("26.1").minecraft());
        assertEquals("26.1.2", NeoForgeAioSelector.targetFor("26.1.1").minecraft());
        assertEquals("26.1.2", NeoForgeAioSelector.targetFor("26.1.2").minecraft());
    }

    @Test
    void checksTheNeoForgeVersionBeforeSelectingAnAdapter() {
        assertEquals("1.21.8", NeoForgeAioSelector.targetFor("1.21.8", "21.8.54").minecraft());
        assertThrows(IllegalStateException.class,
                () -> NeoForgeAioSelector.targetFor("1.21.8", "21.8.53"));
        assertThrows(IllegalStateException.class,
                () -> NeoForgeAioSelector.targetFor("1.21.8", "21.9"));
        assertEquals("1.20.3",
                NeoForgeAioSelector.targetFor("1.20.3", "20.3.9-beta").minecraft());
        assertThrows(IllegalStateException.class,
                () -> NeoForgeAioSelector.targetFor("1.20.3", "20.3.7-beta"));
        assertThrows(IllegalStateException.class,
                () -> NeoForgeAioSelector.targetFor("1.20.3", "20.4.0-beta"));
    }

    @Test
    void rejectsMinecraftBeforeTheNeoForgeSupportFloor() {
        assertThrows(IllegalStateException.class, () -> NeoForgeAioSelector.targetFor("1.20.1"));
    }

    @Test
    void readsLegacyStaticFmlApis() {
        assertTrue(NeoForgeAioSelector.isClient(LegacyLoader.class));
        assertEquals("1.20.2", NeoForgeAioSelector.versionValue(LegacyLoader.class, "mcVersion"));
        assertEquals("20.2.93", NeoForgeAioSelector.versionValue(LegacyLoader.class, "neoForgeVersion"));
    }

    @Test
    void readsModernInstanceFmlApis() {
        assertFalse(NeoForgeAioSelector.isClient(ModernLoader.class));
        assertEquals("26.2", NeoForgeAioSelector.versionValue(ModernLoader.class, "mcVersion"));
        assertEquals("26.2.0.48-beta",
                NeoForgeAioSelector.versionValue(ModernLoader.class, "neoForgeVersion"));
    }

    enum TestDist { CLIENT, DEDICATED_SERVER }

    record TestVersionInfo(String mcVersion, String neoForgeVersion) { }

    public static final class LegacyLoader {
        public static TestDist getDist() {
            return TestDist.CLIENT;
        }

        public static TestVersionInfo versionInfo() {
            return new TestVersionInfo("1.20.2", "20.2.93");
        }
    }

    public static final class ModernLoader {
        private static final ModernLoader CURRENT = new ModernLoader();

        public static ModernLoader getCurrent() {
            return CURRENT;
        }

        public TestDist getDist() {
            return TestDist.DEDICATED_SERVER;
        }

        public TestVersionInfo getVersionInfo() {
            return new TestVersionInfo("26.2", "26.2.0.48-beta");
        }
    }
}
