package fun.prof_chen.teamviewer.main_code.hud.model;

public record LocalMarkedState(boolean active, int count, String ownerSummary) {
    public static LocalMarkedState inactive() {
        return new LocalMarkedState(false, 0, "");
    }

    public String indicatorText() {
        return count > 1 ? "TV! x" + count : "TV!";
    }
}
