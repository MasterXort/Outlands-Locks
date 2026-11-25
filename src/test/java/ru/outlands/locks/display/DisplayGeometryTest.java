package ru.outlands.locks.display;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayGeometryTest {

    private static final double EPS = 1e-9;
    private static final DisplaySettings PLAIN = new DisplaySettings(0, 0, 0, 1, 0, 0, 0);

    private static void assertVec(double x, double y, double z, Vector v) {
        assertEquals(x, v.getX(), EPS, "x");
        assertEquals(y, v.getY(), EPS, "y");
        assertEquals(z, v.getZ(), EPS, "z");
    }

    @Test
    void singleChestNorthFaceIsOnTheNorthEdge() {
        DisplayPlacement p = DisplayGeometry.place(List.of(new Vector(10, 64, 20)), BlockFace.NORTH, PLAIN);
        assertVec(10.5, 64.5, 20.0, p.position());
    }

    @Test
    void facingSelectsTheEdge() {
        Vector block = new Vector(0, 0, 0);
        assertVec(0.5, 0.5, 1.0, DisplayGeometry.place(List.of(block), BlockFace.SOUTH, PLAIN).position());
        assertVec(1.0, 0.5, 0.5, DisplayGeometry.place(List.of(block), BlockFace.EAST, PLAIN).position());
        assertVec(0.0, 0.5, 0.5, DisplayGeometry.place(List.of(block), BlockFace.WEST, PLAIN).position());
    }

    @Test
    void doubleChestIsCentredBetweenHalves() {
        DisplayPlacement p = DisplayGeometry.place(
                List.of(new Vector(10, 64, 20), new Vector(11, 64, 20)), BlockFace.NORTH, PLAIN);
        assertVec(11.0, 64.5, 20.0, p.position());
    }

    @Test
    void doubleChestOnTheOtherAxis() {
        DisplayPlacement p = DisplayGeometry.place(
                List.of(new Vector(5, 70, 8), new Vector(5, 70, 9)), BlockFace.EAST, PLAIN);
        assertVec(6.0, 70.5, 9.0, p.position());
    }

    @Test
    void yawFollowsFacing() {
        Vector block = new Vector(0, 0, 0);
        assertEquals(0f, DisplayGeometry.place(List.of(block), BlockFace.SOUTH, PLAIN).yaw(), 1e-4);
        assertEquals(90f, DisplayGeometry.place(List.of(block), BlockFace.WEST, PLAIN).yaw(), 1e-4);
        assertEquals(180f, DisplayGeometry.place(List.of(block), BlockFace.NORTH, PLAIN).yaw(), 1e-4);
        assertEquals(-90f, DisplayGeometry.place(List.of(block), BlockFace.EAST, PLAIN).yaw(), 1e-4);
    }

    @Test
    void offsetIsRelativeToTheFaceNotTheWorld() {
        DisplaySettings s = new DisplaySettings(0.1, 0.2, -0.05, 1, 0, 0, 0);
        // north face: out = -z, right (viewer looking south) = -x
        assertVec(0.5 - 0.1, 0.5 + 0.2, 0.0 + 0.05,
                DisplayGeometry.place(List.of(new Vector(0, 0, 0)), BlockFace.NORTH, s).position());
        // east face: out = +x, right (viewer looking west) = -z
        assertVec(1.0 - 0.05, 0.5 + 0.2, 0.5 - 0.1,
                DisplayGeometry.place(List.of(new Vector(0, 0, 0)), BlockFace.EAST, s).position());
    }

    @Test
    void settingsAreCarriedToPlacement() {
        DisplaySettings s = new DisplaySettings(0, 0, 0, 0.4, 10, 20, 30);
        DisplayPlacement p = DisplayGeometry.place(List.of(new Vector(0, 0, 0)), BlockFace.NORTH, s);
        assertEquals(0.4f, p.scale(), 1e-6);
        assertEquals(10f, p.extraYaw(), 1e-6);
        assertEquals(20f, p.extraPitch(), 1e-6);
        assertEquals(30f, p.extraRoll(), 1e-6);
    }
}
