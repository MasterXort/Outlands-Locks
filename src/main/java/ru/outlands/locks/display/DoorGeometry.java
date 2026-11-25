package ru.outlands.locks.display;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Pure geometry of the lock displays of a door. The leaf is a slab 3/16 thick on one edge of its block. Closed, it is
 * on the edge opposite to the door's {@code facing}; the player who placed the door stands on that outer side. Open, it
 * has swung round its hinge onto the hinge-side edge (checked against DoorBlock). One display hangs on each face of the
 * slab, at the handle: the end of the leaf away from the hinge.
 */
public final class DoorGeometry {

    /** Height of the handle above the bottom of the door, in blocks. */
    public static final double HANDLE_HEIGHT = 0.95;

    private static final double SLAB = 3.0 / 16.0;

    private DoorGeometry() {
    }

    /**
     * @param lowerCorner lowest corner of the lower door block
     * @param facing      the door's facing
     * @param hingeRight  true when the hinge is on the right as seen from the outer side
     * @param open        whether the door stands open
     * @return the display on the outer face of the leaf first, then the one on the inner face
     */
    public static List<DisplayPlacement> place(Vector lowerCorner, BlockFace facing, boolean hingeRight, boolean open,
                                               DisplaySettings s) {
        double cx = lowerCorner.getX() + 0.5;
        double cz = lowerCorner.getZ() + 0.5;
        double y = lowerCorner.getY() + HANDLE_HEIGHT + s.up();

        int dx = facing.getModX();
        int dz = facing.getModZ();
        // The outer viewer looks along `facing`; their right is (-dz, dx). The hinge is on that side or the other.
        int hinge = hingeRight ? 1 : -1;
        // n points from the centre to the edge the leaf is on, t along the leaf toward the handle.
        double nx;
        double nz;
        double tx;
        double tz;
        if (open) {
            nx = -hinge * dz;
            nz = hinge * dx;
            tx = dx;
            tz = dz;
        } else {
            nx = -dx;
            nz = -dz;
            tx = hinge * dz;
            tz = -hinge * dx;
        }
        double hx = tx * s.right();
        double hz = tz * s.right();

        double outer = 0.5 + s.out();
        double inner = 0.5 - SLAB - s.out();
        return List.of(
                placement(cx + nx * outer + hx, y, cz + nz * outer + hz, yawOf(nx, nz), s),
                placement(cx + nx * inner + hx, y, cz + nz * inner + hz, yawOf(-nx, -nz), s));
    }

    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static DisplayPlacement placement(double x, double y, double z, float yaw, DisplaySettings s) {
        return new DisplayPlacement(new Vector(x, y, z), yaw, 0f, (float) s.scale(),
                (float) s.yaw(), (float) s.pitch(), (float) s.roll());
    }
}
