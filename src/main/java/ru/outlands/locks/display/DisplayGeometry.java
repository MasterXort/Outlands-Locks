package ru.outlands.locks.display;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.List;

/** Pure geometry of the lock display: no world access, so it can be tested on plain vectors. */
public final class DisplayGeometry {

    private DisplayGeometry() {
    }

    /**
     * @param blockCorners lowest corner of each chest block (one for a single chest, two for a double one)
     * @param facing       the way the chest front looks
     */
    public static DisplayPlacement place(List<Vector> blockCorners, BlockFace facing, DisplaySettings s) {
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (Vector corner : blockCorners) {
            cx += corner.getX() + 0.5;
            cy += corner.getY() + 0.5;
            cz += corner.getZ() + 0.5;
        }
        int n = blockCorners.size();
        cx /= n;
        cy /= n;
        cz /= n;

        int dx = facing.getModX();
        int dz = facing.getModZ();
        double reach = 0.5 + s.out();
        // A viewer looking at the front face looks against `facing`; their right is (dz, -dx).
        double x = cx + dx * reach + dz * s.right();
        double y = cy + s.up();
        double z = cz + dz * reach - dx * s.right();

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return new DisplayPlacement(new Vector(x, y, z), yaw, 0f, (float) s.scale(),
                (float) s.yaw(), (float) s.pitch(), (float) s.roll());
    }
}
