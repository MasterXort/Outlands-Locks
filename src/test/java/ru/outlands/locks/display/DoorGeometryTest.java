package ru.outlands.locks.display;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A closed door is a 3/16 slab on the edge of its block opposite to its facing (checked against DoorBlock). */
class DoorGeometryTest {

    private static final double EPS = 1e-6;
    private static final DisplaySettings SETTINGS = new DisplaySettings(0.0, 0.0, 0.02, 0.3, 0, 0, 0);

    private static List<DisplayPlacement> place(BlockFace facing, boolean hingeRight, DisplaySettings s) {
        return DoorGeometry.place(new Vector(10, 64, 20), facing, hingeRight, false, s);
    }

    private static List<DisplayPlacement> open(BlockFace facing, boolean hingeRight, DisplaySettings s) {
        return DoorGeometry.place(new Vector(10, 64, 20), facing, hingeRight, true, s);
    }

    @Test
    void thereAreTwoDisplaysOneOnEachFaceOfTheSlab() {
        assertEquals(2, place(BlockFace.NORTH, false, SETTINGS).size());
    }

    @Test
    void aNorthFacingDoorHasItsSlabOnTheSouthEdge() {
        DisplayPlacement outer = place(BlockFace.NORTH, false, SETTINGS).get(0);
        DisplayPlacement inner = place(BlockFace.NORTH, false, SETTINGS).get(1);

        // the slab fills z 13/16..16/16 of the block (z = 20 .. 21)
        assertEquals(21.0 + 0.02, outer.position().getZ(), EPS);
        assertEquals(20.0 + 13.0 / 16.0 - 0.02, inner.position().getZ(), EPS);
        assertEquals(10.5, outer.position().getX(), EPS);
    }

    @Test
    void theOuterDisplayLooksAwayFromTheFacingAndTheInnerOneAlongIt() {
        // north = -z; a display looking north has yaw 180, looking south yaw 0
        assertEquals(0.0, normalise(place(BlockFace.NORTH, false, SETTINGS).get(0).yaw()), 1e-3);
        assertEquals(180.0, Math.abs(normalise(place(BlockFace.NORTH, false, SETTINGS).get(1).yaw())), 1e-3);
    }

    @Test
    void everyFacingPutsTheSlabOnTheOppositeEdge() {
        assertEquals(20.0 - 0.02, place(BlockFace.SOUTH, false, SETTINGS).get(0).position().getZ(), EPS);
        assertEquals(10.0 - 0.02, place(BlockFace.EAST, false, SETTINGS).get(0).position().getX(), EPS);
        assertEquals(11.0 + 0.02, place(BlockFace.WEST, false, SETTINGS).get(0).position().getX(), EPS);
    }

    @Test
    void theDisplaysHangAtHandleHeight() {
        DisplayPlacement p = place(BlockFace.NORTH, false, new DisplaySettings(0, 0.1, 0.02, 0.3, 0, 0, 0)).get(0);

        assertEquals(64 + DoorGeometry.HANDLE_HEIGHT + 0.1, p.position().getY(), EPS);
    }

    @Test
    void theHandleSideDependsOnTheHinge() {
        DisplaySettings s = new DisplaySettings(0.3, 0.0, 0.02, 0.3, 0, 0, 0);
        // seen by the placer of a north-facing door, the right hand is east; the handle is opposite to the hinge
        double leftHinge = place(BlockFace.NORTH, false, s).get(0).position().getX();
        double rightHinge = place(BlockFace.NORTH, true, s).get(0).position().getX();

        assertEquals(10.5 + 0.3, leftHinge, EPS);
        assertEquals(10.5 - 0.3, rightHinge, EPS);
    }

    @Test
    void bothDisplaysSitAtTheSameHandleSide() {
        DisplaySettings s = new DisplaySettings(0.3, 0.0, 0.02, 0.3, 0, 0, 0);
        List<DisplayPlacement> both = place(BlockFace.EAST, true, s);

        assertEquals(both.get(0).position().getZ(), both.get(1).position().getZ(), EPS);
    }

    @Test
    void scaleAndExtraRotationAreCarried() {
        DisplayPlacement p = place(BlockFace.NORTH, false, new DisplaySettings(0, 0, 0.02, 0.5, 10, 20, 30)).get(0);

        assertEquals(0.5f, p.scale());
        assertEquals(10f, p.extraYaw());
        assertEquals(20f, p.extraPitch());
        assertEquals(30f, p.extraRoll());
        assertTrue(p.position().getY() > 64);
    }

    // ---- open: the leaf has swung round its hinge onto the hinge-side edge (DoorBlock: hinge RIGHT = facing.CCW) ----

    @Test
    void anOpenNorthFacingDoorWithTheHingeOnTheRightStandsOnTheEastEdge() {
        // facing north, hinge right: the leaf is the east slab x 13/16..1 of the block (x = 10 .. 11)
        DisplayPlacement outer = open(BlockFace.NORTH, true, SETTINGS).get(0);
        DisplayPlacement inner = open(BlockFace.NORTH, true, SETTINGS).get(1);

        assertEquals(11.0 + 0.02, outer.position().getX(), EPS);
        assertEquals(10.0 + 13.0 / 16.0 - 0.02, inner.position().getX(), EPS);
        assertEquals(20.5, outer.position().getZ(), EPS);
    }

    @Test
    void withTheHingeOnTheLeftTheOpenLeafIsOnTheWestEdge() {
        assertEquals(10.0 - 0.02, open(BlockFace.NORTH, false, SETTINGS).get(0).position().getX(), EPS);
    }

    @Test
    void anOpenDoorLooksSidewaysAlongTheEdge() {
        // outer face of the east slab looks east: yaw -90
        assertEquals(-90.0, normalise(open(BlockFace.NORTH, true, SETTINGS).get(0).yaw()), 1e-3);
        assertEquals(90.0, normalise(open(BlockFace.NORTH, true, SETTINGS).get(1).yaw()), 1e-3);
    }

    @Test
    void theHandleOfAnOpenLeafIsTheEndAwayFromTheHinge() {
        DisplaySettings s = new DisplaySettings(0.3, 0.0, 0.02, 0.3, 0, 0, 0);
        // north facing, hinge right: the hinge corner is at the south end (z = 21); the handle end is to the north
        assertEquals(20.5 - 0.3, open(BlockFace.NORTH, true, s).get(0).position().getZ(), EPS);
        assertEquals(20.5 - 0.3, open(BlockFace.NORTH, true, s).get(1).position().getZ(), EPS);
        // hinge left: the hinge corner is at the south-west corner as well, so the leaf also runs north
        assertEquals(20.5 - 0.3, open(BlockFace.NORTH, false, s).get(0).position().getZ(), EPS);
    }

    @Test
    void anOpenLeafKeepsTheHandleHeight() {
        assertEquals(64 + DoorGeometry.HANDLE_HEIGHT, open(BlockFace.EAST, false, SETTINGS).get(0).position().getY(), EPS);
    }

    private static double normalise(float yaw) {
        double y = yaw % 360.0;
        return y > 180 ? y - 360 : y <= -180 ? y + 360 : y;
    }
}
