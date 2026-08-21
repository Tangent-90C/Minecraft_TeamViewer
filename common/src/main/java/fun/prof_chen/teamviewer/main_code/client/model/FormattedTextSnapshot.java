package fun.prof_chen.teamviewer.main_code.client.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FormattedTextSnapshot(String plainText, List<FormattedTextSpanSnapshot> spans) {
    public static final int MAX_SPANS = 64;
    public static final int MAX_UTF8_BYTES = 4096;

    public FormattedTextSnapshot {
        plainText = truncateUtf8(plainText == null ? "" : plainText, MAX_UTF8_BYTES);
        spans = normalize(plainText, spans);
    }

    public static FormattedTextSnapshot plain(String text) {
        String value = truncateUtf8(text == null ? "" : text, MAX_UTF8_BYTES);
        return new FormattedTextSnapshot(value, value.isEmpty()
                ? List.of()
                : List.of(new FormattedTextSpanSnapshot(value, null, null, false, false, false, false, false, null)));
    }

    public static FormattedTextSnapshot concat(FormattedTextSnapshot... values) {
        StringBuilder plain = new StringBuilder();
        List<FormattedTextSpanSnapshot> spans = new ArrayList<>();
        if (values != null) {
            for (FormattedTextSnapshot value : values) {
                if (value == null) continue;
                plain.append(value.plainText());
                spans.addAll(value.spans());
            }
        }
        return new FormattedTextSnapshot(plain.toString(), spans);
    }

    public Map<String, Object> toProtocolMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("plainText", plainText);
        value.put("spans", spans.stream().map(FormattedTextSpanSnapshot::toProtocolMap).toList());
        return value;
    }

    private static List<FormattedTextSpanSnapshot> normalize(
            String plainText, List<FormattedTextSpanSnapshot> input) {
        List<FormattedTextSpanSnapshot> merged = new ArrayList<>();
        StringBuilder concatenated = new StringBuilder();
        if (input != null) {
            for (FormattedTextSpanSnapshot span : input) {
                if (span == null || span.text() == null || span.text().isEmpty()) continue;
                concatenated.append(span.text());
                if (!merged.isEmpty() && sameStyle(merged.get(merged.size() - 1), span)) {
                    FormattedTextSpanSnapshot previous = merged.remove(merged.size() - 1);
                    merged.add(new FormattedTextSpanSnapshot(
                            previous.text() + span.text(), previous.colorArgb(), previous.shadowColorArgb(),
                            previous.bold(), previous.italic(), previous.underlined(), previous.strikethrough(),
                            previous.obfuscated(), previous.fontId()));
                } else {
                    merged.add(span);
                }
            }
        }
        if (!plainText.contentEquals(concatenated) || merged.size() > MAX_SPANS) {
            return plainText.isEmpty() ? List.of() : List.of(new FormattedTextSpanSnapshot(
                    plainText, null, null, false, false, false, false, false, null));
        }
        return List.copyOf(merged);
    }

    private static boolean sameStyle(FormattedTextSpanSnapshot left, FormattedTextSpanSnapshot right) {
        return java.util.Objects.equals(left.colorArgb(), right.colorArgb())
                && java.util.Objects.equals(left.shadowColorArgb(), right.shadowColorArgb())
                && left.bold() == right.bold()
                && left.italic() == right.italic()
                && left.underlined() == right.underlined()
                && left.strikethrough() == right.strikethrough()
                && left.obfuscated() == right.obfuscated()
                && java.util.Objects.equals(left.fontId(), right.fontId());
    }

    private static String truncateUtf8(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        int end = Math.min(value.length(), maxBytes);
        while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > maxBytes) end--;
        return value.substring(0, end);
    }
}
