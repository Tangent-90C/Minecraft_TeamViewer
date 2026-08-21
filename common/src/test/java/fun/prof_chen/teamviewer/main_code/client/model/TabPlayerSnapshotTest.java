package fun.prof_chen.teamviewer.main_code.client.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabPlayerSnapshotTest {
    @Test
    void preservesResolvedDisplayNameSeparatelyFromScoreboardPrefix() {
        TabPlayerSnapshot snapshot = new TabPlayerSnapshot(
                "12345678-1234-5678-9234-567812345678",
                "Player",
                "nt00011bf146084c",
                "[利雅得] Player",
                "town-raw",
                null,
                null,
                "[利雅得] Player",
                FormattedTextSnapshot.plain("[利雅得] Player"),
                FormattedTextSnapshot.plain("nt00011bf146084c"),
                null);

        Map<String, Object> packet = snapshot.toProtocolMap();
        assertEquals("[利雅得] Player", packet.get("displayName"));
        assertEquals("nt00011bf146084c", packet.get("prefixedName"));
        assertEquals("nt00011bf146084c", packet.get("scoreboardPrefix"));
    }
}
