package ru.outlands.locks.display;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.LockData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chunk-load check of the displays of locked doors: both on a closed door, none on an open one. */
class DoorDisplayReconcileTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;
    private DisplayReconciler reconciler;
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
        reconciler = new DisplayReconciler(plugin.storage(), plugin.displays(), chunk -> List.of(), warnings::add);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block door(int x, int z, boolean open) {
        Block lower = world.getBlockAt(x, 64, z);
        for (Block half : List.of(lower, lower.getRelative(BlockFace.UP))) {
            half.setType(Material.OAK_DOOR);
            Door data = (Door) half.getBlockData();
            data.setHalf(half == lower ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            data.setFacing(BlockFace.NORTH);
            data.setOpen(open);
            half.setBlockData(data, false);
        }
        return lower;
    }

    private LockData lockWithoutDisplay(Block lower) {
        LockData data = new LockData(UUID.randomUUID(), true);
        plugin.storage().write(List.of(lower), data);
        return data;
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    @Test
    void aClosedLockedDoorWithoutDisplaysGetsBoth() {
        Block lower = door(5, 5, false);
        LockData data = lockWithoutDisplay(lower);

        reconciler.reconcile(lower.getChunk());

        assertEquals(2, displays().size());
        for (ItemDisplay display : displays()) {
            assertEquals(data.id().toString(),
                    display.getPersistentDataContainer().get(plugin.keys().lockId(), PersistentDataType.STRING));
            assertArrayEquals(new int[]{5, 64, 5},
                    display.getPersistentDataContainer().get(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY));
        }
    }

    @Test
    void aSecondCheckChangesNothing() {
        Block lower = door(5, 5, false);
        lockWithoutDisplay(lower);
        reconciler.reconcile(lower.getChunk());
        List<ItemDisplay> first = displays();

        reconciler.reconcile(lower.getChunk());

        assertEquals(first, displays());
    }

    private int drawnFor(ItemDisplay display) {
        return display.getPersistentDataContainer().get(plugin.keys().open(), PersistentDataType.BYTE);
    }

    @Test
    void anOpenLockedDoorGetsNoDisplays() {
        Block lower = door(5, 5, true);
        lockWithoutDisplay(lower);

        reconciler.reconcile(lower.getChunk());

        assertTrue(displays().isEmpty());
    }

    @Test
    void theDisplaysOfADoorThatHasOpenedAreRemoved() {
        Block lower = door(5, 5, false);
        LockData data = lockWithoutDisplay(lower);
        plugin.displays().spawn(List.of(lower), data);
        Door door = (Door) lower.getBlockData();
        door.setOpen(true);
        lower.setBlockData(door, false);
        assertEquals(2, displays().size());

        reconciler.reconcile(lower.getChunk());

        assertTrue(displays().isEmpty());
    }

    @Test
    void anOpenLockedTrapdoorGetsNoDisplaysAndAClosedOneGetsItsPair() {
        Block open = world.getBlockAt(5, 64, 5);
        open.setType(Material.OAK_TRAPDOOR);
        org.bukkit.block.data.type.TrapDoor data = (org.bukkit.block.data.type.TrapDoor) open.getBlockData();
        data.setOpen(true);
        open.setBlockData(data, false);
        Block closed = world.getBlockAt(7, 64, 5);
        closed.setType(Material.OAK_TRAPDOOR);
        lockWithoutDisplay(open);
        lockWithoutDisplay(closed);

        reconciler.reconcile(open.getChunk());

        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> d.getPersistentDataContainer().get(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY)[0] == 7));
    }

    @Test
    void aLockedTrapdoorGetsItsPairToo() {
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(Material.OAK_TRAPDOOR);
        LockData data = lockWithoutDisplay(block);

        reconciler.reconcile(block.getChunk());

        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
        reconciler.reconcile(block.getChunk());
        assertEquals(2, displays().size());
        assertEquals(data.id().toString(), displays().get(0).getPersistentDataContainer()
                .get(plugin.keys().lockId(), PersistentDataType.STRING));
    }

    @Test
    void aMissingDisplayOfThePairIsMadeGoodAndExtrasCollapse() {
        Block lower = door(5, 5, false);
        LockData data = lockWithoutDisplay(lower);
        plugin.displays().spawn(List.of(lower), data);
        plugin.displays().spawn(List.of(lower), data);
        assertEquals(4, displays().size());

        reconciler.reconcile(lower.getChunk());

        assertEquals(2, displays().size());
    }

    @Test
    void displaysOfADoorThatIsNoLongerLockedAreRemoved() {
        Block lower = door(5, 5, false);
        LockData data = lockWithoutDisplay(lower);
        plugin.displays().spawn(List.of(lower), data);
        plugin.storage().clear(List.of(lower));

        reconciler.reconcile(lower.getChunk());

        assertTrue(displays().isEmpty());
    }

    @Test
    void displaysOfADoorThatIsGoneAreRemoved() {
        Block lower = door(5, 5, false);
        LockData data = lockWithoutDisplay(lower);
        plugin.displays().spawn(List.of(lower), data);
        lower.setType(Material.AIR);
        lower.getRelative(BlockFace.UP).setType(Material.AIR);

        reconciler.reconcile(lower.getChunk());

        assertTrue(displays().isEmpty());
        assertTrue(plugin.storage().doorsIn(lower.getChunk()).isEmpty());
    }

    @Test
    void anUnlockedDoorGetsNothing() {
        Block lower = door(5, 5, false);

        reconciler.reconcile(lower.getChunk());

        assertTrue(displays().isEmpty());
    }
}
