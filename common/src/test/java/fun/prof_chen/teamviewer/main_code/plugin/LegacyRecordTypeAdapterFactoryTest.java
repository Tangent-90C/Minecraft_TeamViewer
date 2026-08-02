package fun.prof_chen.teamviewer.main_code.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyRecordTypeAdapterFactoryTest {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new LegacyRecordTypeAdapterFactory())
            .create();

    @Test
    void readsEveryBuiltInPluginManifest() throws Exception {
        for (String name : List.of("nodemc", "simmc", "xaero", "journeymap", "example")) {
            String path = "teamviewer/plugins/" + name + "/plugin.json";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
            assertNotNull(stream, path);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                PluginManifest manifest = GSON.fromJson(reader, PluginManifest.class).normalized();
                assertEquals(1, manifest.schemaVersion());
                assertNotNull(manifest.selectedEntrypoint("fabric", "1.18.2"), name);
            }
        }
    }

    @Test
    void readsNestedGenericComponentsAndSkipsUnknownFields() {
        GenericRecord parsed = GSON.fromJson("""
                {
                  "names": ["alpha", "beta"],
                  "counts": {"alpha": 2},
                  "child": {"value": "nested", "unknown": true},
                  "unknown": {"ignored": true}
                }
                """, GenericRecord.class);

        assertEquals(List.of("alpha", "beta"), parsed.names());
        assertEquals(Map.of("alpha", 2), parsed.counts());
        assertEquals(new PrivateRecord("nested"), parsed.child());
    }

    @Test
    void suppliesPrimitiveDefaultsForMissingAndNullValues() {
        PrimitiveRecord parsed = GSON.fromJson("{\"count\":null}", PrimitiveRecord.class);

        assertEquals(new PrimitiveRecord(0, false, 0D, '\0'), parsed);
    }

    @Test
    void roundTripsPrivateRecords() {
        PrivateRecord original = new PrivateRecord("private");

        assertEquals(original, GSON.fromJson(GSON.toJson(original), PrivateRecord.class));
    }

    private record GenericRecord(
            List<String> names, Map<String, Integer> counts, PrivateRecord child) { }

    private record PrimitiveRecord(int count, boolean enabled, double scale, char marker) { }

    private record PrivateRecord(String value) { }
}
