package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minecraft-free NodeMC sidebar parser. */
public final class ScoreboardBattleMapParser {
    private static final Pattern RANGE_HINT = Pattern.compile("(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)");

    public Optional<ParsedSnapshot> parse(ScoreboardSnapshot snapshot) {
        if (snapshot == null || snapshot.lines().isEmpty() || snapshot.dimension() == null) return Optional.empty();
        List<ParsedLine> lines = new ArrayList<>();
        Integer hinted = null;
        for (ScoreboardSnapshot.Line line : snapshot.lines()) {
            List<Glyph> glyphs = new ArrayList<>();
            for (ScoreboardSnapshot.Run run : line.runs()) {
                if (run.text() == null) continue;
                run.text().codePoints().forEach(codePoint -> {
                    String symbol = new String(Character.toChars(codePoint));
                    if (isGlyph(symbol)) glyphs.add(new Glyph(symbol, normalizeColor(run.colorRaw())));
                });
            }
            ParsedLine parsed = new ParsedLine(line.rawText(), glyphs);
            lines.add(parsed);
            if (hinted == null) hinted = parseRangeHint(line.rawText());
        }
        int size = resolveSize(lines, hinted);
        if (size <= 0) return Optional.empty();
        List<ParsedLine> mapLines = selectMapLines(lines, size);
        if (mapLines.size() != size) return Optional.empty();
        int anchorRow = Math.max(0, size / 2);
        int anchorColumn = anchorRow;
        outer: for (int row = 0; row < mapLines.size(); row++) {
            for (int column = 0; column < mapLines.get(row).glyphs().size(); column++) {
                if ("┼".equals(mapLines.get(row).glyphs().get(column).symbol())) {
                    anchorRow = row;
                    anchorColumn = column;
                    break outer;
                }
            }
        }
        List<Cell> cells = new ArrayList<>();
        for (int row = 0; row < mapLines.size(); row++) {
            for (int column = 0; column < mapLines.get(row).glyphs().size(); column++) {
                Glyph glyph = mapLines.get(row).glyphs().get(column);
                if (!"┼".equals(glyph.symbol())) {
                    cells.add(new Cell(column - anchorColumn, row - anchorRow, glyph.symbol(), glyph.colorRaw()));
                }
            }
        }
        return Optional.of(new ParsedSnapshot(size, anchorRow, anchorColumn, snapshot.dimension(), snapshot.observedAt(), cells));
    }

    private static int resolveSize(List<ParsedLine> lines, Integer hinted) {
        if (hinted != null && hinted > 0 && lines.stream().filter(line -> line.glyphs().size() == hinted).count() >= hinted) return hinted;
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (ParsedLine line : lines) if (!line.glyphs().isEmpty()) counts.merge(line.glyphs().size(), 1, Integer::sum);
        int best = 0;
        for (var entry : counts.entrySet()) if (entry.getValue() >= entry.getKey() && entry.getKey() > best) best = entry.getKey();
        return best;
    }

    private static List<ParsedLine> selectMapLines(List<ParsedLine> lines, int size) {
        for (int start = 0; start < lines.size(); start++) {
            int end = start;
            while (end < lines.size() && lines.get(end).glyphs().size() == size && end - start < size) end++;
            if (end - start == size) return new ArrayList<>(lines.subList(start, end));
        }
        return lines.stream().filter(line -> line.glyphs().size() == size).limit(size).toList();
    }

    private static Integer parseRangeHint(String text) {
        if (text == null) return null;
        Matcher matcher = RANGE_HINT.matcher(text);
        if (!matcher.find()) return null;
        try {
            int left = Integer.parseInt(matcher.group(1));
            int center = Integer.parseInt(matcher.group(2));
            int right = Integer.parseInt(matcher.group(3));
            return center == 0 && left <= right ? right - left + 1 : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isGlyph(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        int codePoint = symbol.codePointAt(0);
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return false;
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.BLOCK_ELEMENTS || block == Character.UnicodeBlock.BOX_DRAWING
                || block == Character.UnicodeBlock.GEOMETRIC_SHAPES || block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS;
    }

    private static String normalizeColor(String color) {
        return color == null || color.isBlank() ? "#FFFFFF" : color;
    }

    private record Glyph(String symbol, String colorRaw) { }
    private record ParsedLine(String rawText, List<Glyph> glyphs) { }
    public record Cell(int relChunkX, int relChunkZ, String symbol, String colorRaw) { }
    public record ParsedSnapshot(int size, int anchorRow, int anchorColumn, String dimension,
                                 long observedAt, List<Cell> cells) { }
}
