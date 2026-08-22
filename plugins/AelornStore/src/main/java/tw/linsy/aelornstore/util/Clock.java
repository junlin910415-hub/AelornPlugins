package tw.linsy.aelornstore.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Time in the server operator's timezone, not the JVM's.
 *
 * Daily caps and per-day purchase limits are what make this matter: a player in
 * Taiwan expects "today" to end at midnight Taipei time, and a VPS running UTC
 * would otherwise reset their allowance at 8am local.
 */
public final class Clock {

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ZoneId zone;
    private final DateTimeFormatter display;

    public Clock(ZoneId zone, DateTimeFormatter display) {
        this.zone = zone;
        this.display = display;
    }

    public long now() {
        return System.currentTimeMillis();
    }

    /** The {@code day_key} used by per-day purchase limits. */
    public String dayKey(long epochMillis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(DAY_KEY);
    }

    /** Epoch millis of the most recent local midnight; the daily top-up window start. */
    public long startOfDay(long epochMillis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
            .atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Formats for display, or {@code "—"} when the timestamp was never set. */
    public String format(long epochMillis) {
        if (epochMillis <= 0) {
            return "—";
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(display);
    }

    public static long daysToMillis(long days) {
        return days * 86_400_000L;
    }
}
