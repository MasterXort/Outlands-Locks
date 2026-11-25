package ru.outlands.locks.display;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A trapdoor is a 3/16 slab: flat at the bottom or top of the block when closed, on the edge opposite to its facing when open. */
class TrapdoorGeometryTest {

    private static final double EPS = 1e-6;
    private static final DisplaySettings SETTINGS = new DisplaySettings(0.0, 0.0, 0.02, 0.3, 0, 0, 0);

    private static List<DisplayPlacement> place(boolean top, boolean open, DisplaySettings s) {
        return TrapdoorGeometry.place(new Vector(10, 64, 20), BlockFace.NORTH, top, open, s);
    }

    @Test
    void aClosedBottomTrapdoorShowsALockOnItsUpperAndLowerSurface() {
        List<DisplayPlacement> p = place(false, false, SETTINGS);

        assertEquals(2, p.size());
        assertEquals(64 + 3.0 / 16.0 + 0.02, p.get(0).position().getY(), EPS);
        assertEquals(64 - 0.02, p.get(1).position().getY(), EPS);
        assertEquals(10.5, p.get(0).position().getX(), EPS);
        assertEquals(20.5, p.get(0).position().getZ(), EPS);
    }

    @Test
    void aClosedTopTrapdoorLiesAtTheTopOfTheBlock() {
        List<DisplayPlacement> p = place(true, false, SETTINGS);

        assertEquals(65 + 0.02, p.get(0).position().getY(), EPS);
        assertEquals(64 + 13.0 / 16.0 - 0.02, p.get(1).position().getY(), EPS);
    }

    @Test
    void theClosedDisplaysLieFlatFacingUpAndDown() {
        List<DisplayPlacement> p = place(false, false, SETTINGS);

        assertEquals(-90f, p.get(0).pitch());
        assertEquals(90f, p.get(1).pitch());
    }

    @Test
    void anOpenNorthFacingTrapdoorStandsOnTheSouthEdge() {
        List<DisplayPlacement> p = place(false, true, SETTINGS);

        assertEquals(20.5 + 0.5 + 0.02, p.get(0).position().getZ(), EPS);
        assertEquals(20.5 + 0.5 - 3.0 / 16.0 - 0.02, p.get(1).position().getZ(), EPS);
        assertEquals(64.5, p.get(0).position().getY(), EPS);
        assertEquals(0f, p.get(0).pitch());
    }

    @Test
    void theOpenDisplaysLookAlongTheFacing() {
        List<DisplayPlacement> p = place(false, true, SETTINGS);

        // outer looks south (yaw 0), inner looks north (yaw 180)
        assertEquals(0.0, Math.abs(p.get(0).yaw() % 360.0), 1e-3);
        assertEquals(180.0, Math.abs(p.get(1).yaw() % 360.0), 1e-3);
    }

    @Test
    void rightMovesAlongTheSlabAndUpOnlyMattersWhileOpen() {
        DisplaySettings s = new DisplaySettings(0.25, 0.1, 0.02, 0.3, 0, 0, 0);

        assertEquals(10.5 + 0.25, place(false, false, s).get(0).position().getX(), EPS);
        assertEquals(64 + 3.0 / 16.0 + 0.02, place(false, false, s).get(0).position().getY(), EPS);
        assertEquals(64.6, place(false, true, s).get(0).position().getY(), EPS);
    }
}
