package fun.prof_chen.teamviewer.main_code.client.model;

/** Plain system message delivered by a version adapter without Minecraft text objects. */
public record SystemChatMessageSnapshot(String text, boolean overlay) {
    public SystemChatMessageSnapshot {
        text = text == null ? "" : text;
    }
}
