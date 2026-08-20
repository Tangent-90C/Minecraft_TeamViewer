package fun.prof_chen.teamviewer.main_code.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LastSeenTimeFormatterTest {
    @Test
    void formatsEpochMillisUsingTheClientDefaultZone() {
        long epochMillis = 1_700_000_004_000L;
        String expected = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));

        assertEquals(expected, LastSeenTimeFormatter.format(epochMillis));
    }
}
