package fun.prof_chen.teamviewer.main_code.model;

public record Position3D(double x, double y, double z) {
	public Position3D add(double dx, double dy, double dz) {
		return new Position3D(x + dx, y + dy, z + dz);
	}
}