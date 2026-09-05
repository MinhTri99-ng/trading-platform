package trading_api.vision;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TimeframeResolver {
    private static final Set<String> VALID_TIMEFRAMES = Set.of(
            "1m", "5m", "15m", "30m", "1h", "2h", "3h", "4h", "12h", "1d", "1w"
    );
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("d", "1d"),
            Map.entry("day", "1d"),
            Map.entry("daily", "1d"),
            Map.entry("w", "1w"),
            Map.entry("week", "1w"),
            Map.entry("weekly", "1w")
    );

    public Resolution resolveTimeframe(String requestedTimeframe, String visionTimeframe, String filenameTimeframe) {
        String requested = normalize(requestedTimeframe);
        if (!requested.isBlank()) {
            return new Resolution(requested, Source.REQUESTED);
        }

        String vision = normalize(visionTimeframe);
        if (!vision.isBlank()) {
            return new Resolution(vision, Source.VISION);
        }

        String filename = normalize(filenameTimeframe);
        if (!filename.isBlank()) {
            return new Resolution(filename, Source.FILENAME);
        }

        throw new IllegalArgumentException("INVALID INPUT: TIMEFRAME_UNDETECTED");
    }

    private String normalize(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return "";
        }
        String normalized = timeframe.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (VALID_TIMEFRAMES.contains(normalized)) {
            return normalized;
        }
        return ALIASES.getOrDefault(normalized, "");
    }

    public enum Source {
        REQUESTED,
        VISION,
        FILENAME
    }

    public record Resolution(String timeframe, Source source) {
    }
}