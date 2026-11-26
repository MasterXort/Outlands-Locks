package ru.outlands.locks.listener;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.display.ChunkRef;
import ru.outlands.locks.display.DisplayReconciler;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring of the chunk events to the reconciler (PLAN section 6). The reconciler itself is covered in
 * DisplayReconcilerTest; real chunk loading, entity loading order and getTileEntities are not reproducible in
 * MockBukkit, so the events are built by hand.
 */
class DisplayChunkListenerTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;
    private int tileScans;
    private int failAtChunkX = Integer.MIN_VALUE;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
        DisplayReconciler reconciler =
                new DisplayReconciler(plugin.storage(), plugin.displays(), this::tiles, message -> { });
        Field field = OutlandsLocks.class.getDeclaredField("displayReconciler");
        field.setAccessible(true);
        field.set(plugin, reconciler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Collection<BlockState> tiles(Chunk chunk) {
        tileScans++;
        if (chunk.getX() == failAtChunkX) {
            throw new IllegalStateException("boom");
        }
        List<BlockState> out = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block block = world.getBlockAt(chunk.getX() * 16 + x, 64, chunk.getZ() * 16 + z);
                if (LockableBlocks.isLockable(block.getType())) {
                    out.add(block.getState(false));
                }
            }
        }
        return out;
    }

    private Block lockedChest(int x, int z) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        block.setBlockData(data);
        plugin.storage().write(List.of(block), new LockData(UUID.randomUUID(), false));
        return block;
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    private void chunkLoaded(Chunk chunk) {
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
    }

    private void entitiesLoaded(Chunk chunk) {
        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of()));
    }

    private void tick() {
        server.getScheduler().performOneTick();
    }

    @Test
    void aChunkLoadRestoresAMissingDisplayOnTheNextTick() {
        Block chest = lockedChest(5, 5);

        chunkLoaded(chest.getChunk());
        assertTrue(displays().isEmpty());
        tick();

        assertEquals(1, displays().size());
    }

    @Test
    void anEntitiesLoadRestoresAMissingDisplayToo() {
        Block chest = lockedChest(5, 5);

        entitiesLoaded(chest.getChunk());
        tick();

        assertEquals(1, displays().size());
    }

    @Test
    void killingTheDisplaysAndReenteringTheChunkBringsThemBack() {
        Block chest = lockedChest(5, 5);
        chunkLoaded(chest.getChunk());
        tick();
        assertEquals(1, displays().size());

        displays().forEach(ItemDisplay::remove);
        assertTrue(displays().isEmpty());
        chunkLoaded(chest.getChunk());
        entitiesLoaded(chest.getChunk());
        tick();

        assertEquals(1, displays().size());
    }

    @Test
    void bothEventsOfOneChunkCauseOneCheck() {
        Block chest = lockedChest(5, 5);

        chunkLoaded(chest.getChunk());
        entitiesLoaded(chest.getChunk());
        tick();

        assertEquals(1, tileScans);
        assertEquals(1, displays().size());
    }

    @Test
    void aTickWithoutEventsChecksNothing() {
        lockedChest(5, 5);

        tick();

        assertEquals(0, tileScans);
        assertTrue(displays().isEmpty());
    }

    @Test
    void manyChunksLoadedInOneTickAreCheckedInOneBatch() {
        Block a = lockedChest(5, 5);
        Block b = lockedChest(37, 5);

        chunkLoaded(a.getChunk());
        chunkLoaded(b.getChunk());
        tick();

        assertEquals(2, displays().size());
        assertEquals(2, tileScans);
    }

    @Test
    void severalEventsScheduleASingleTask() {
        Block a = lockedChest(5, 5);
        Block b = lockedChest(37, 5);
        int before = server.getScheduler().getPendingTasks().size();

        chunkLoaded(a.getChunk());
        entitiesLoaded(a.getChunk());
        chunkLoaded(b.getChunk());

        assertEquals(before + 1, server.getScheduler().getPendingTasks().size());
    }

    @Test
    void aChunkThatIsGoneBeforeTheTickIsSkippedWithoutLoadingIt() {
        Block chest = lockedChest(5, 5);
        chunkLoaded(chest.getChunk());
        world.unloadChunk(0, 0, false);

        tick();

        assertEquals(0, tileScans);
        assertFalse(world.isChunkLoaded(0, 0));
    }

    @Test
    void theLoadOfANeighbourWakesTheChunkThatWasWaitingForIt() {
        Block edge = lockedChest(15, 5);
        chunkLoaded(edge.getChunk());
        tick();
        assertTrue(displays().isEmpty());
        assertFalse(world.isChunkLoaded(1, 0));

        world.loadChunk(1, 0);
        chunkLoaded(world.getChunkAt(1, 0));
        tick();
        assertEquals(1, displays().size());
    }

    @Test
    void aFailureInOneChunkDoesNotStopTheOthers() {
        Block broken = lockedChest(5, 5);
        Block fine = lockedChest(37, 5);
        failAtChunkX = 0;

        chunkLoaded(broken.getChunk());
        chunkLoaded(fine.getChunk());
        tick();

        assertEquals(1, displays().size());
    }

    // ---- the list of chunks that wait for a neighbour does not keep what is gone ----

    @Test
    void aChunkThatUnloadsNoLongerWaitsForItsNeighbour() {
        Block leaving = lockedChest(15, 5);
        Block staying = lockedChest(15, 37);
        chunkLoaded(leaving.getChunk());
        chunkLoaded(staying.getChunk());
        tick();

        server.getPluginManager().callEvent(new ChunkUnloadEvent(leaving.getChunk(), false));

        DisplayReconciler reconciler = plugin.displayReconciler();
        assertTrue(reconciler.takeWaiters(new ChunkRef(world.getUID(), 1, 0)).isEmpty());
        assertEquals(Set.of(ChunkRef.of(staying.getChunk())),
                reconciler.takeWaiters(new ChunkRef(world.getUID(), 1, 2)),
                "the chunk that stays is still waiting");
    }

    @Test
    void aWorldThatUnloadsTakesTheWaitingChunksOfItWithIt() {
        Block edge = lockedChest(15, 5);
        chunkLoaded(edge.getChunk());
        tick();

        server.getPluginManager().callEvent(new WorldUnloadEvent(world));

        assertTrue(plugin.displayReconciler().takeWaiters(new ChunkRef(world.getUID(), 1, 0)).isEmpty());
    }

    @Test
    void aWorldUnloadThatIsCancelledChangesNothing() {
        Block edge = lockedChest(15, 5);
        chunkLoaded(edge.getChunk());
        tick();
        WorldUnloadEvent event = new WorldUnloadEvent(world);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(Set.of(ChunkRef.of(edge.getChunk())),
                plugin.displayReconciler().takeWaiters(new ChunkRef(world.getUID(), 1, 0)));
    }
}
