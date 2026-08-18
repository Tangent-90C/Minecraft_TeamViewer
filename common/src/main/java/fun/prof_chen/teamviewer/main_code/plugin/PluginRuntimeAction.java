package fun.prof_chen.teamviewer.main_code.plugin;

/** One bounded action exposed by a running integration plugin on its detail page. */
public record PluginRuntimeAction(
        String id,
        String label,
        String tooltip,
        boolean enabled,
        boolean danger,
        String confirmation) {
    public PluginRuntimeAction {
        id = id == null ? "" : id.trim();
        label = label == null ? "" : label.trim();
        tooltip = tooltip == null ? "" : tooltip.trim();
        confirmation = confirmation == null ? "" : confirmation.trim();
    }

    public boolean requiresConfirmation() {
        return !confirmation.isEmpty();
    }
}
