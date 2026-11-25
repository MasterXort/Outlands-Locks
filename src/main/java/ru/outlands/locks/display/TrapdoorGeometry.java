package ru.outlands.locks.display;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Pure geometry of the lock displays of a trapdoor, a slab 3/16 thick. Closed it lies flat at the bottom or the top
 * of its block, open it stands on the edge opposite to its facing (checked against TrapDoorBlock). One display hangs
 * on each face.
 */
public final class TrapdoorGeometry {

    private static final double SLAB = 3.0 / 16.0;

    private TrapdoorGeometry() {
    }

    /**
     * @param corner lowest corner of the block
     * @param top    true for a trapdoor in the top half of its block
     * @return the display on the upper or outer face first, then the one on the lower or inner face. Settings:
     *         {@code right} moves along the trapdoor, {@code up} moves up while it stands open, {@code out} is the
     *         gap to the surface.
     */
    public static List<DisplayPlacement> place(Vector corner, BlockFace facing, boolean top, boolean open,
                                               DisplaySettings s) {
        double cx = corner.getX() + 0.5;
        double cz = corner.getZ() + 0.5;
        int dx = facing.getModX();
        int dz = facing.getModZ();
        // Along the slab: the right hand of someone who looks along `facing`.
        double lx = -dz * s.right();
        double lz = dx * s.right();
        float facingYaw = yawOf(dx, dz);

        if (!open) {
            double upper = corner.getY() + (top ? 1.0 : SLAB) + s.out();
            double lower = corner.getY() + (top ? 1.0 - SLAB : 0.0) - s.out();
            return List.of(
                    placement(cx + lx, upper, cz + lz, facingYaw, -90f, s),
                    placement(cx + lx, lower, cz + lz, facingYaw, 90f, s));
        }
        // Open: the slab is on the edge opposite to `facing`, so n = -facing points from the centre to it.
        double y = corner.getY() + 0.5 + s.up();
        double outer = 0.5 + s.out();
        double inner = 0.5 - SLAB - s.out();
        return List.of(
                placement(cx - dx * outer + lx, y, cz - dz * outer + lz, yawOf(-dx, -dz), 0f, s),
                placement(cx - dx * inner + lx, y, cz - dz * inner + lz, facingYaw, 0f, s));
    }

    private static float yawOf(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static DisplayPlacement placement(double x, double y, double z, float yaw, float pitch,
                                              DisplaySettings s) {
        return new DisplayPlacement(new Vector(x, y, z), yaw, pitch, (float) s.scale(),
                (float) s.yaw(), (float) s.pitch(), (float) s.roll());
    }
}
