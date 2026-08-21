package fun.prof_chen.teamviewer.main_code.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Version-neutral resolved text run; interactive component events are intentionally excluded. */
public record FormattedTextSpanSnapshot(
        String text,
        Integer colorArgb,
        Integer shadowColorArgb,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough,
        boolean obfuscated,
        String fontId) {
    public Map<String, Object> toProtocolMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", text == null ? "" : text);
        if (colorArgb != null) value.put("colorArgb", colorArgb);
        if (shadowColorArgb != null) value.put("shadowColorArgb", shadowColorArgb);
        if (bold) value.put("bold", true);
        if (italic) value.put("italic", true);
        if (underlined) value.put("underlined", true);
        if (strikethrough) value.put("strikethrough", true);
        if (obfuscated) value.put("obfuscated", true);
        if (fontId != null && !fontId.isBlank()) value.put("fontId", fontId);
        return value;
    }
}
