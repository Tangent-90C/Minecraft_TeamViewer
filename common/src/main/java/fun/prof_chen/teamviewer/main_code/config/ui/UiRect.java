package fun.prof_chen.teamviewer.main_code.config.ui;

public record UiRect(int x, int y, int width, int height) {
    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }

    public boolean intersects(UiRect other) {
        return other != null
                && x < other.x + other.width
                && x + width > other.x
                && y < other.y + other.height
                && y + height > other.y;
    }
}
