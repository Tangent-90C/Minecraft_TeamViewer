package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScoreboardBattleMapParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreboardBattleMapParser.class);
    private static final Comparator<ScoreboardEntry> SCOREBOARD_ENTRY_COMPARATOR = Comparator
            .comparingInt(ScoreboardEntry::value)
            .reversed()
            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final Pattern RANGE_HINT_PATTERN = Pattern.compile("(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)");

    public Optional<ParsedBattleMapSnapshot> parse(
            MinecraftClient client,
            String roomCode,
            UUID reporterId,
            boolean debugLogging
    ) {
        if (client == null || client.world == null || client.player == null) {
            return Optional.empty();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        if (scoreboard == null) {
            return Optional.empty();
        }

        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            return Optional.empty();
        }

        List<ScoreboardEntry> entries = new ArrayList<>(scoreboard.getScoreboardEntries(objective));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        entries.removeIf(ScoreboardEntry::hidden);
        entries.sort(SCOREBOARD_ENTRY_COMPARATOR);

        List<ParsedScoreboardLine> lines = new ArrayList<>();
        Integer hintedSize = null;
        for (ScoreboardEntry entry : entries) {
            ParsedScoreboardLine line = buildLine(scoreboard, entry);
            lines.add(line);

            if (hintedSize == null) {
                hintedSize = parseRangeHintSize(line.rawText());
            }
        }

        int resolvedSize = resolveMapSize(lines, hintedSize);
        if (resolvedSize <= 0) {
            return Optional.empty();
        }

        List<ParsedScoreboardLine> mapLines = selectMapLines(lines, resolvedSize);
        if (mapLines.size() != resolvedSize) {
            if (debugLogging) {
                LOGGER.info("Battle map parse skipped: expected square size={} but got rows={}", resolvedSize, mapLines.size());
            }
            return Optional.empty();
        }

        AnchorPosition anchor = resolveAnchor(mapLines, resolvedSize);
        ChunkPos playerChunk = new ChunkPos(client.player.getBlockPos());
        String dimension = client.player.getWorld().getRegistryKey().getValue().toString();
        String reporterIdText = reporterId == null ? null : reporterId.toString();
        long observedAt = System.currentTimeMillis();

        if (hintedSize != null && hintedSize != resolvedSize && debugLogging) {
            LOGGER.info("Battle map size hint mismatch: hinted={}, parsed={}", hintedSize, resolvedSize);
        }

        Map<String, BattleChunkObservation> chunks = new HashMap<>();
        for (int rowIndex = 0; rowIndex < mapLines.size(); rowIndex++) {
            ParsedScoreboardLine line = mapLines.get(rowIndex);
            for (int columnIndex = 0; columnIndex < line.cells().size(); columnIndex++) {
                ParsedGlyphCell cell = line.cells().get(columnIndex);
                int chunkX = playerChunk.x + (columnIndex - anchor.columnIndex());
                int chunkZ = playerChunk.z + (rowIndex - anchor.rowIndex());
                String chunkId = buildChunkId(roomCode, dimension, chunkX, chunkZ);

                BattleChunkObservation observation = new BattleChunkObservation(
                        chunkId,
                        chunkX,
                        chunkZ,
                        dimension,
                        cell.symbol(),
                        cell.colorRaw(),
                        null,
                        observedAt,
                        reporterIdText,
                        roomCode
                );
                chunks.put(chunkId, observation);
            }
        }

        return Optional.of(new ParsedBattleMapSnapshot(
                resolvedSize,
                anchor.rowIndex(),
                anchor.columnIndex(),
                hintedSize,
                chunks
        ));
    }

    private ParsedScoreboardLine buildLine(Scoreboard scoreboard, ScoreboardEntry entry) {
        String owner = entry.owner();
        Team team = owner == null ? null : scoreboard.getScoreHolderTeam(owner);
        Text lineText = Team.decorateName(team, Text.literal(owner == null ? "" : owner));
        List<ParsedGlyphCell> cells = new ArrayList<>();

        lineText.visit((style, text) -> {
            String colorRaw = normalizeColor(style);
            text.codePoints().forEach(codePoint -> {
                String symbol = new String(Character.toChars(codePoint));
                if (!isBattleMapGlyph(symbol)) {
                    return;
                }
                cells.add(new ParsedGlyphCell(symbol, colorRaw));
            });
            return Optional.empty();
        }, Style.EMPTY);

        return new ParsedScoreboardLine(lineText.getString(), cells);
    }

    private int resolveMapSize(List<ParsedScoreboardLine> lines, Integer hintedSize) {
        if (hintedSize != null && hintedSize > 0) {
            int hintedRows = 0;
            for (ParsedScoreboardLine line : lines) {
                if (line.cells().size() == hintedSize) {
                    hintedRows++;
                }
            }
            if (hintedRows >= hintedSize) {
                return hintedSize;
            }
        }

        Map<Integer, Integer> counts = new HashMap<>();
        for (ParsedScoreboardLine line : lines) {
            int size = line.cells().size();
            if (size <= 0) {
                continue;
            }
            counts.merge(size, 1, Integer::sum);
        }

        int bestSize = 0;
        int bestFrequency = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int size = entry.getKey();
            int frequency = entry.getValue();
            if (frequency >= size && size > bestSize) {
                bestSize = size;
            }
            if (bestSize > 0) {
                continue;
            }
            if (frequency > bestFrequency || (frequency == bestFrequency && size > bestSize)) {
                bestSize = size;
                bestFrequency = frequency;
            }
        }
        return bestSize;
    }

    private List<ParsedScoreboardLine> selectMapLines(List<ParsedScoreboardLine> lines, int size) {
        for (int start = 0; start < lines.size(); start++) {
            if (lines.get(start).cells().size() != size) {
                continue;
            }
            int end = start;
            while (end < lines.size() && lines.get(end).cells().size() == size && (end - start) < size) {
                end++;
            }
            if ((end - start) == size) {
                return new ArrayList<>(lines.subList(start, end));
            }
        }

        List<ParsedScoreboardLine> fallback = new ArrayList<>();
        for (ParsedScoreboardLine line : lines) {
            if (line.cells().size() == size) {
                fallback.add(line);
            }
            if (fallback.size() >= size) {
                break;
            }
        }
        return fallback;
    }

    private AnchorPosition resolveAnchor(List<ParsedScoreboardLine> mapLines, int size) {
        for (int rowIndex = 0; rowIndex < mapLines.size(); rowIndex++) {
            ParsedScoreboardLine line = mapLines.get(rowIndex);
            for (int columnIndex = 0; columnIndex < line.cells().size(); columnIndex++) {
                if ("┼".equals(line.cells().get(columnIndex).symbol())) {
                    return new AnchorPosition(rowIndex, columnIndex);
                }
            }
        }
        int centerIndex = Math.max(0, size / 2);
        return new AnchorPosition(centerIndex, centerIndex);
    }

    private Integer parseRangeHintSize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        Matcher matcher = RANGE_HINT_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return null;
        }

        try {
            int left = Integer.parseInt(matcher.group(1));
            int center = Integer.parseInt(matcher.group(2));
            int right = Integer.parseInt(matcher.group(3));
            if (center != 0 || left > right) {
                return null;
            }
            return right - left + 1;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeColor(Style style) {
        if (style == null) {
            return "#FFFFFF";
        }
        TextColor textColor = style.getColor();
        if (textColor == null) {
            return "#FFFFFF";
        }
        String colorName = textColor.getName();
        if (colorName != null && !colorName.isBlank()) {
            if (colorName.startsWith("#")) {
                return colorName.toUpperCase();
            }
            return colorName.toLowerCase();
        }
        return String.format("#%06X", textColor.getRgb() & 0xFFFFFF);
    }

    private boolean isBattleMapGlyph(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        int codePoint = symbol.codePointAt(0);
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
            return false;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.BLOCK_ELEMENTS
                || block == Character.UnicodeBlock.BOX_DRAWING
                || block == Character.UnicodeBlock.GEOMETRIC_SHAPES
                || block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS;
    }

    private String buildChunkId(String roomCode, String dimension, int chunkX, int chunkZ) {
        String safeRoomCode = roomCode == null || roomCode.isBlank() ? "default" : roomCode.trim();
        String safeDimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.trim();
        return safeRoomCode + "|" + safeDimension + "|" + chunkX + "|" + chunkZ;
    }

    private record ParsedGlyphCell(String symbol, String colorRaw) {
    }

    private record ParsedScoreboardLine(String rawText, List<ParsedGlyphCell> cells) {
    }

    private record AnchorPosition(int rowIndex, int columnIndex) {
    }

    public record BattleChunkObservation(
            String chunkId,
            int chunkX,
            int chunkZ,
            String dimension,
            String symbol,
            String colorRaw,
            String colorNote,
            long observedAt,
            String reporterId,
            String roomCode) {
        public ReportDataSchemas.BattleChunkDataPayload toPayload() {
            return new ReportDataSchemas.BattleChunkDataPayload(
                    chunkX,
                    chunkZ,
                    dimension,
                    symbol,
                    colorRaw,
                    colorNote,
                    observedAt,
                    reporterId,
                    roomCode
            );
        }
    }

    public record ParsedBattleMapSnapshot(
            int size,
            int anchorRow,
            int anchorColumn,
            Integer hintedSize,
            Map<String, BattleChunkObservation> chunks) {
        public Map<String, Map<String, Object>> toProtocolMap() {
            Map<String, Map<String, Object>> protocolMap = new HashMap<>();
            for (Map.Entry<String, BattleChunkObservation> entry : chunks.entrySet()) {
                protocolMap.put(entry.getKey(), entry.getValue().toPayload().toMap());
            }
            return protocolMap;
        }
    }
}
