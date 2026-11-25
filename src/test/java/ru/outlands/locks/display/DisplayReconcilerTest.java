package ru.outlands.locks.display;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN section 6: on chunk load displays are checked against the locked chests. Chunk.getTileEntities is not
 * implemented in MockBukkit, so the tests feed the reconciler a tile source that walks the blocks of the chunk.
 */
class DisplayReconcilerTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;
    private final List<String> warnings = new ArrayList<>();
    private DisplayReconciler reconciler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
        reconciler = new DisplayReconciler(plugin.storage(), plugin.displays(), this::tiles, warnings::add);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Collection<BlockState> tiles(Chunk chunk) {
        List<BlockState> out = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 60; y < 68; y++) {
                    Block block = world.getBlockAt(chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                    if (LockableBlocks.isLockable(block.getType())) {
                        out.add(block.getState(false));
                    }
                }
            }
        }
        return out;
    }

    private Block chest(int x, int z, Chest.Type type) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        data.setType(type);
        block.setBlockData(data);
        return block;
    }

    private Block chest(int x, int z) {
        return chest(x, z, Chest.Type.SINGLE);
    }

    /** Writes the lock to the chest the way an install does, without a display. */
    private LockData lockWithoutDisplay(Block block, UUID id) {
        LockData data = new LockData(id, false);
        List<Block> halves = ChestHalves.of(block);
        List<BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        // MockBukkit resets the chest shape on TileState.update; Paper does not.
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        return data;
    }

    private LockData lockWithoutDisplay(Block block) {
        return lockWithoutDisplay(block, UUID.randomUUID());
    }

    private ItemDisplay displayFor(Block block, LockData data) {
        return plugin.displays().spawn(ChestHalves.of(block), data).orElseThrow();
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    private Chunk chunkOf(Block block) {
        return block.getChunk();
    }

    private UUID lockIdOf(ItemDisplay display) {
        return UUID.fromString(display.getPersistentDataContainer()
                .get(plugin.keys().lockId(), PersistentDataType.STRING));
    }

    private int[] blockOf(ItemDisplay display) {
        return display.getPersistentDataContainer().get(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY);
    }

    // --- point 1: restore, remove, replace, deduplicate ---

    @Test
    void aLockedChestWithoutADisplayGetsOne() {
        Block block = chest(5, 5);
        LockData data = lockWithoutDisplay(block);

        reconciler.reconcile(chunkOf(block));

        assertEquals(1, displays().size());
        ItemDisplay display = displays().get(0);
        assertEquals(data.id(), lockIdOf(display));
        assertEquals(List.of(5, 64, 5), List.of(blockOf(display)[0], blockOf(display)[1], blockOf(display)[2]));
    }

    @Test
    void aDisplayWithoutAChestIsRemoved() {
        Block block = chest(5, 5);
        LockData data = lockWithoutDisplay(block);
        displayFor(block, data);
        block.setType(Material.AIR);

        reconciler.reconcile(chunkOf(block));

        assertTrue(displays().isEmpty());
    }

    @Test
    void anOldLockOnACopperChestLosesItsDisplayAndGetsNoNewOne() {
        Block block = chest(5, 5);
        block.setType(Material.COPPER_CHEST);
        LockData data = new LockData(UUID.randomUUID(), false);
        plugin.storage().write(List.of(block), data);
        assertTrue(plugin.storage().read(block).isPresent(), "the old lock data is on the copper chest");
        displayFor(block, data);

        reconciler.reconcile(chunkOf(block));

        assertTrue(displays().isEmpty());
    }

    @Test
    void aDisplayOfAChestThatIsNoLongerLockedIsRemoved() {
        Block block = chest(5, 5);
        LockData data = lockWithoutDisplay(block);
        displayFor(block, data);
        plugin.storage().clear(List.of(block));

        reconciler.reconcile(chunkOf(block));

        assertTrue(displays().isEmpty());
    }

    @Test
    void aDisplayLeftBehindWhenTheChunkWasUnloadedDuringRemovalIsCleanedUp() {
        Block block = chest(5, 5);
        LockData data = lockWithoutDisplay(block);
        displayFor(block, data);
        // the lock was taken off while the display's chunk was not loaded: only the chest was cleared
        plugin.storage().clear(List.of(block));
        block.setType(Material.CHEST);

        reconciler.reconcile(chunkOf(block));

        assertTrue(displays().isEmpty());
    }

    @Test
    void aDisplayWithAnotherLockIdIsReplacedByOneForTheChestsLock() {
        Block block = chest(5, 5);
        LockData old = lockWithoutDisplay(block, UUID.randomUUID());
        displayFor(block, old);
        LockData current = lockWithoutDisplay(block, UUID.randomUUID());

        reconciler.reconcile(chunkOf(block));

        assertEquals(1, displays().size());
        assertEquals(current.id(), lockIdOf(displays().get(0)));
    }

    @Test
    void aMatchingDisplayIsLeftAloneAndNotRecreated() {
        Block block = chest(5, 5);
        LockData data = lockWithoutDisplay(block);
        ItemDisplay display = displayFor(block, data);

        reconciler.reconcile(chunkOf(block));

        assertEquals(1, displays().size());
        assertEquals(display.getUniqueId(), displays().get(0).getUniqueId());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void twoDisplaysOfOneLockLeaveExactlyOne() {
        Block block = chest(5, 5);
        LockData data = lockWithoutDisplay(block);
        displayFor(block, data);
        displayFor(block, data);
        assertEquals(2, displays().size());

        reconciler.reconcile(chunkOf(block));

        assertEquals(1, displays().size());
        assertEquals(data.id(), lockIdOf(displays().get(0)));
    }

    @Test
    void reconcilingTwiceChangesNothing() {
        Block block = chest(5, 5);
        lockWithoutDisplay(block);

        reconciler.reconcile(chunkOf(block));
        UUID first = displays().get(0).getUniqueId();
        reconciler.reconcile(chunkOf(block));

        assertEquals(1, displays().size());
        assertEquals(first, displays().get(0).getUniqueId());
    }

    @Test
    void twoChestsWithTheSameLockIdKeepOneDisplayEach() {
        // a copied lock hangs on two chests: same lock_id, different anchor
        UUID id = UUID.randomUUID();
        Block a = chest(5, 5);
        Block b = chest(9, 5);
        lockWithoutDisplay(a, id);
        lockWithoutDisplay(b, id);

        reconciler.reconcile(chunkOf(a));

        assertEquals(2, displays().size());
    }

    @Test
    void thingsThatAreNotOursAreNeverTouched() {
        Block plain = chest(5, 5);
        Location at = plain.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay foreign = world.spawn(at, ItemDisplay.class);
        ItemDisplay halfTagged = world.spawn(at, ItemDisplay.class);
        halfTagged.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING,
                UUID.randomUUID().toString());

        reconciler.reconcile(chunkOf(plain));

        assertEquals(2, displays().size());
    }

    // --- point 3: double chest ---

    @Test
    void aDoubleChestWithoutADisplayGetsOneInTheMiddle() {
        Block left = chest(5, 5, Chest.Type.LEFT);
        chest(6, 5, Chest.Type.RIGHT);
        LockData data = lockWithoutDisplay(left);
        ItemDisplay reference = displayFor(left, data);
        Location expected = reference.getLocation();
        reference.remove();
        assertTrue(displays().isEmpty());

        reconciler.reconcile(chunkOf(left));

        assertEquals(1, displays().size());
        ItemDisplay display = displays().get(0);
        assertEquals(expected.getX(), display.getLocation().getX(), 1e-6);
        assertEquals(expected.getZ(), display.getLocation().getZ(), 1e-6);
        assertEquals(6.0, display.getLocation().getX(), 1e-6);
        assertEquals(left.getX(), blockOf(display)[0]);
    }

    @Test
    void aDoubleChestWithOneDisplayPerHalfEndsWithOne() {
        Block left = chest(5, 5, Chest.Type.LEFT);
        chest(6, 5, Chest.Type.RIGHT);
        LockData data = lockWithoutDisplay(left);
        displayFor(left, data);
        displayFor(left, data);

        reconciler.reconcile(chunkOf(left));

        assertEquals(1, displays().size());
    }

    @Test
    void aDisplayAnchoredOnTheWrongHalfIsReplacedByOneOnTheAnchor() {
        Block left = chest(5, 5, Chest.Type.LEFT);
        Block right = chest(6, 5, Chest.Type.RIGHT);
        LockData data = lockWithoutDisplay(left);
        ItemDisplay stray = displayFor(left, data);
        stray.getPersistentDataContainer().set(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY,
                new int[]{right.getX(), right.getY(), right.getZ()});

        reconciler.reconcile(chunkOf(left));

        assertEquals(1, displays().size());
        assertEquals(left.getX(), blockOf(displays().get(0))[0]);
    }

    // --- point 6: a lock item that cannot be built ---

    @Test
    void anUnbuildableLockItemWarnsOnceAndCreatesNoDisplay() {
        AtomicBoolean buildable = new AtomicBoolean(false);
        LockDisplays flaky = new LockDisplays(plugin.keys(), () -> plugin.settings().display(), () -> plugin.settings().doorDisplay(), () -> plugin.settings().trapdoorDisplay(),
                () -> buildable.get() ? plugin.items().create(LockItemType.LOCK, 1) : Optional.<ItemStack>empty());
        DisplayReconciler flakyReconciler =
                new DisplayReconciler(plugin.storage(), flaky, this::tiles, warnings::add);
        Block first = chest(5, 5);
        Block second = chest(37, 5);
        lockWithoutDisplay(first);
        lockWithoutDisplay(second);

        flakyReconciler.reconcile(chunkOf(first));
        flakyReconciler.reconcile(chunkOf(second));

        assertTrue(displays().isEmpty());
        assertEquals(1, warnings.size());

        buildable.set(true);
        flakyReconciler.reconcile(chunkOf(first));
        assertEquals(1, displays().size());
    }

    @Test
    void aLockItemThatBreaksAgainWarnsAgain() {
        AtomicBoolean buildable = new AtomicBoolean(true);
        LockDisplays flaky = new LockDisplays(plugin.keys(), () -> plugin.settings().display(), () -> plugin.settings().doorDisplay(), () -> plugin.settings().trapdoorDisplay(),
                () -> buildable.get() ? plugin.items().create(LockItemType.LOCK, 1) : Optional.<ItemStack>empty());
        DisplayReconciler flakyReconciler =
                new DisplayReconciler(plugin.storage(), flaky, this::tiles, warnings::add);
        Block first = chest(5, 5);
        Block second = chest(37, 5);
        lockWithoutDisplay(first);
        lockWithoutDisplay(second);

        flakyReconciler.reconcile(chunkOf(first));
        buildable.set(false);
        flakyReconciler.reconcile(chunkOf(second));
        assertEquals(1, warnings.size());
    }

    @Test
    void severalChestsInOneChunkTryTheBrokenItemOnlyOnce() {
        AtomicBoolean buildable = new AtomicBoolean(false);
        int[] attempts = {0};
        LockDisplays flaky = new LockDisplays(plugin.keys(), () -> plugin.settings().display(), () -> plugin.settings().doorDisplay(), () -> plugin.settings().trapdoorDisplay(), () -> {
            attempts[0]++;
            return buildable.get() ? plugin.items().create(LockItemType.LOCK, 1) : Optional.<ItemStack>empty();
        });
        DisplayReconciler flakyReconciler =
                new DisplayReconciler(plugin.storage(), flaky, this::tiles, warnings::add);
        for (int x = 1; x <= 5; x++) {
            lockWithoutDisplay(chest(3 + x * 2, 5));
        }

        flakyReconciler.reconcile(chunkOf(world.getBlockAt(5, 64, 5)));

        assertEquals(1, attempts[0]);
        assertEquals(1, warnings.size());
    }

    // --- chunk borders: nothing is force-loaded, nothing is guessed ---

    @Test
    void aChestNearAnUnloadedNeighbourDoesNotLoadItAndDoesNotGuessADisplay() {
        Block edge = chest(15, 5);
        lockWithoutDisplay(edge);
        assertFalse(world.isChunkLoaded(1, 0));

        reconciler.reconcile(chunkOf(edge));

        assertFalse(world.isChunkLoaded(1, 0));
        assertTrue(displays().isEmpty());
        assertEquals(java.util.Set.of(ChunkRef.of(chunkOf(edge))),
                reconciler.takeWaiters(new ChunkRef(world.getUID(), 1, 0)));
    }

    @Test
    void whenTheNeighbourLoadsTheWaitingChunkIsChecked() {
        Block edge = chest(15, 5);
        lockWithoutDisplay(edge);
        reconciler.reconcile(chunkOf(edge));
        world.loadChunk(1, 0);

        for (ChunkRef waiting : reconciler.takeWaiters(new ChunkRef(world.getUID(), 1, 0))) {
            reconciler.reconcile(world.getChunkAt(waiting.x(), waiting.z()));
        }

        assertEquals(1, displays().size());
    }

    @Test
    void aWaiterIsHandedOutOnlyOnce() {
        Block edge = chest(15, 5);
        lockWithoutDisplay(edge);
        reconciler.reconcile(chunkOf(edge));
        ChunkRef neighbour = new ChunkRef(world.getUID(), 1, 0);

        assertFalse(reconciler.takeWaiters(neighbour).isEmpty());
        assertTrue(reconciler.takeWaiters(neighbour).isEmpty());
    }

    @Test
    void aDisplayWhoseChestChunkIsNotLoadedIsNotJudged() {
        Block block = chest(15, 5);
        LockData data = lockWithoutDisplay(block);
        ItemDisplay display = displayFor(block, data);
        // the display points at a block in chunk (1, 0), which is not loaded
        display.getPersistentDataContainer().set(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY,
                new int[]{20, 64, 5});
        plugin.storage().clear(List.of(block));
        assertFalse(world.isChunkLoaded(1, 0));

        reconciler.reconcile(chunkOf(block));

        assertEquals(1, displays().size());
        assertFalse(world.isChunkLoaded(1, 0));

        world.loadChunk(1, 0);
        for (ChunkRef waiting : reconciler.takeWaiters(new ChunkRef(world.getUID(), 1, 0))) {
            reconciler.reconcile(world.getChunkAt(waiting.x(), waiting.z()));
        }
        assertTrue(displays().isEmpty());
    }

    @Test
    void aDoubleChestWhoseNeighbourSideIsNotLoadedIsSkippedNotLoaded() {
        Block edge = chest(15, 5, Chest.Type.LEFT);
        BlockData shape = edge.getBlockData();
        plugin.storage().write(List.of(edge), new LockData(UUID.randomUUID(), false));
        edge.setBlockData(shape);
        assertFalse(world.isChunkLoaded(1, 0));

        reconciler.reconcile(chunkOf(edge));

        assertFalse(world.isChunkLoaded(1, 0));
        assertTrue(displays().isEmpty());
    }
}
