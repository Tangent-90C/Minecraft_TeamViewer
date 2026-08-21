package fun.prof_chen.teamviewer.main_code.renderbridge.core;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small camera-facing 3x5 vector font compiled into the existing line batch. */
final class WorldLabelVectorizer {
	private static final float LABEL_STROKE_WIDTH = 2.0F;
	private static final Map<Character, Integer> GLYPHS = glyphs();

    private WorldLabelVectorizer() {
    }

    static void append(
            List<WorldRenderCommand> commands,
            String text,
            Position3D center,
            Position3D lookDirection,
            Position3D upDirection,
            double distance,
            int color,
            boolean depthTest) {
        if (commands == null || text == null || text.isBlank() || center == null) return;
        Position3D up = normalize(upDirection);
        Position3D right = normalize(cross(lookDirection, up));
        if (lengthSquared(right) < 1.0E-9 || lengthSquared(up) < 1.0E-9) return;
        double scale = Math.max(0.025D, Math.min(0.36D, distance * 0.0007D));
        String[] lines = text.toUpperCase(Locale.ROOT).split("\\n", -1);
        double lineHeight = 7D * scale;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            double width = Math.max(0, line.length() * 4 - 1) * scale;
            Position3D origin = add(center, add(multiply(right, -width * 0.5D),
                    multiply(up, (lines.length - 1 - lineIndex) * lineHeight)));
            appendLine(commands, line, origin, right, up, scale, color, depthTest);
        }
    }

    private static void appendLine(
            List<WorldRenderCommand> commands,
            String text,
            Position3D origin,
            Position3D right,
            Position3D up,
            double scale,
            int color,
            boolean depthTest) {
        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            int glyph = GLYPHS.getOrDefault(text.charAt(characterIndex), GLYPHS.get('?'));
            for (int row = 0; row < 5; row++) {
                int column = 0;
                while (column < 3) {
                    while (column < 3 && !pixel(glyph, row, column)) column++;
                    if (column == 3) break;
                    int start = column;
                    while (column + 1 < 3 && pixel(glyph, row, column + 1)) column++;
                    double characterOffset = characterIndex * 4D;
                    Position3D from = point(origin, right, up,
                            (characterOffset + start) * scale, (4 - row) * scale);
                    Position3D to = point(origin, right, up,
                            (characterOffset + column + 0.8D) * scale, (4 - row) * scale);
					commands.add(new WorldRenderCommand.Line(from, to, color, depthTest, LABEL_STROKE_WIDTH));
                    column++;
                }
            }
        }
    }

    private static boolean pixel(int glyph, int row, int column) {
        return (glyph & (1 << (14 - row * 3 - column))) != 0;
    }

    private static Position3D point(
            Position3D origin, Position3D right, Position3D up, double x, double y) {
        return add(origin, add(multiply(right, x), multiply(up, y)));
    }

    private static Map<Character, Integer> glyphs() {
        Map<Character, Integer> glyphs = new HashMap<>();
        put(glyphs, 'A', ".#./#.#/###/#.#/#.#"); put(glyphs, 'B', "##./#.#/##./#.#/##.");
        put(glyphs, 'C', ".##/#../#../#../.##"); put(glyphs, 'D', "##./#.#/#.#/#.#/##.");
        put(glyphs, 'E', "###/#../##./#../###"); put(glyphs, 'F', "###/#../##./#../#..");
        put(glyphs, 'G', ".##/#../#.#/#.#/.##"); put(glyphs, 'H', "#.#/#.#/###/#.#/#.#");
        put(glyphs, 'I', "###/.#./.#./.#./###"); put(glyphs, 'J', "..#/..#/..#/#.#/.#.");
        put(glyphs, 'K', "#.#/#.#/##./#.#/#.#"); put(glyphs, 'L', "#../#../#../#../###");
        put(glyphs, 'M', "#.#/###/###/#.#/#.#"); put(glyphs, 'N', "#.#/###/###/###/#.#");
        put(glyphs, 'O', ".#./#.#/#.#/#.#/.#."); put(glyphs, 'P', "##./#.#/##./#../#..");
        put(glyphs, 'Q', ".#./#.#/#.#/.##/..#"); put(glyphs, 'R', "##./#.#/##./#.#/#.#");
        put(glyphs, 'S', ".##/#../.#./..#/##."); put(glyphs, 'T', "###/.#./.#./.#./.#.");
        put(glyphs, 'U', "#.#/#.#/#.#/#.#/###"); put(glyphs, 'V', "#.#/#.#/#.#/#.#/.#.");
        put(glyphs, 'W', "#.#/#.#/###/###/#.#"); put(glyphs, 'X', "#.#/#.#/.#./#.#/#.#");
        put(glyphs, 'Y', "#.#/#.#/.#./.#./.#."); put(glyphs, 'Z', "###/..#/.#./#../###");
        put(glyphs, '0', "###/#.#/#.#/#.#/###"); put(glyphs, '1', ".#./##./.#./.#./###");
        put(glyphs, '2', "##./..#/.#./#../###"); put(glyphs, '3', "##./..#/.#./..#/##.");
        put(glyphs, '4', "#.#/#.#/###/..#/..#"); put(glyphs, '5', "###/#../##./..#/##.");
        put(glyphs, '6', ".##/#../###/#.#/###"); put(glyphs, '7', "###/..#/.#./.#./.#.");
        put(glyphs, '8', "###/#.#/###/#.#/###"); put(glyphs, '9', "###/#.#/###/..#/.##");
        put(glyphs, '-', ".../.../###/.../..."); put(glyphs, ':', ".../.#./.../.#./...");
        put(glyphs, '_', ".../.../.../.../###"); put(glyphs, '.', ".../.../.../.../.#.");
        put(glyphs, '[', "##./#../#../#../##."); put(glyphs, ']', ".##/..#/..#/..#/.##");
        put(glyphs, ' ', ".../.../.../.../..."); put(glyphs, '?', "##./..#/.#./.../.#.");
        return Map.copyOf(glyphs);
    }

    private static void put(Map<Character, Integer> glyphs, char character, String rows) {
        int bits = 0;
        for (char value : rows.replace("/", "").toCharArray()) {
            bits = (bits << 1) | (value == '#' ? 1 : 0);
        }
        glyphs.put(character, bits);
    }

    private static Position3D cross(Position3D a, Position3D b) {
        if (a == null || b == null) return new Position3D(0, 0, 0);
        return new Position3D(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }

    private static double lengthSquared(Position3D value) {
        return value.x() * value.x() + value.y() * value.y() + value.z() * value.z();
    }

    private static Position3D normalize(Position3D value) {
        if (value == null) return new Position3D(0, 0, 0);
        double length = Math.sqrt(lengthSquared(value));
        return length < 1.0E-9 ? new Position3D(0, 0, 0) : multiply(value, 1D / length);
    }

    private static Position3D add(Position3D a, Position3D b) {
        return new Position3D(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static Position3D multiply(Position3D value, double scale) {
        return new Position3D(value.x() * scale, value.y() * scale, value.z() * scale);
    }
}
