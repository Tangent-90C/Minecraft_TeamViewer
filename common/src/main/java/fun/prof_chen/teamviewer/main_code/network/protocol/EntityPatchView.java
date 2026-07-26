package fun.prof_chen.teamviewer.main_code.network.protocol;

import java.util.UUID;

/** Typed entity delta view consumed directly by protobuf encoding. */
public interface EntityPatchView {
    int X = 1;
    int Y = 1 << 1;
    int Z = 1 << 2;
    int VX = 1 << 3;
    int VY = 1 << 4;
    int VZ = 1 << 5;
    int DIMENSION = 1 << 6;
    int TYPE = 1 << 7;
    int NAME = 1 << 8;
    int WIDTH = 1 << 9;
    int HEIGHT = 1 << 10;
    int ALL = (1 << 11) - 1;

    int upsertCount();
    UUID upsertId(int index);
    int fieldMask(int index);
    double x(int index);
    double y(int index);
    double z(int index);
    double vx(int index);
    double vy(int index);
    double vz(int index);
    String dimension(int index);
    String entityType(int index);
    String entityName(int index);
    float width(int index);
    float height(int index);

    int deleteCount();
    UUID deleteId(int index);
}
