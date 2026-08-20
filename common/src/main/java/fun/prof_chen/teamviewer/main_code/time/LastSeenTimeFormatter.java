package fun.prof_chen.teamviewer.main_code.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formats protocol UTC timestamps for the local client UI. */
public final class LastSeenTimeFormatter {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private LastSeenTimeFormatter() {
    }

    public static String format(long epochMillis) {
        return FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
