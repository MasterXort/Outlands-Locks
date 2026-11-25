package ru.outlands.locks.display;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.DoorBlocks;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockStorage;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Checks the displays of one chunk against its locked chests (PLAN section 6): a display without a locked chest under
 * it, or with another lock's id, is removed; a locked chest without a display gets one; two displays of one lock
 * collapse to one.
 *
 * <p>The check never loads a chunk and never judges what it cannot see. A chest or a display whose neighbours (the
 * other half of a double chest, the chunk the display points into, the chunks a display could sit in) are not loaded,
 * or whose entities are not loaded yet, is skipped and remembered: {@link #takeWaiters} hands the chunk back once the
 * missing one arrives. Treating "not loaded yet" as "missing" would spawn duplicates.
 */
public final class DisplayReconciler {

    private static final Locked UNKNOWN = new Locked(null, null, null);

    private final LockStorage storage;
    private final LockDisplays displays;
    private final Function<Chunk, Collection<BlockState>> tiles;
    private final Consumer<String> warn;
    private final Map<ChunkRef, Set<ChunkRef>> waiting = new HashMap<>();
    private boolean itemWarned;

    /**
     * @param tiles the lockable block entities of a chunk; {@link #lockableTiles} on a server
     * @param warn  receives the warning that the lock item cannot be built
     */
    public DisplayReconciler(LockStorage storage, LockDisplays displays,
                             Function<Chunk, Collection<BlockState>> tiles, Consumer<String> warn) {
        this.storage = storage;
        this.displays = displays;
        this.tiles = tiles;
        this.warn = warn;
    }

    /**
     * Walks only the block entities of the chunk and only builds a state for the lockable types, so a chunk with
     * thousands of hoppers and furnaces costs a material check each.
     */
    public static Collection<BlockState> lockableTiles(Chunk chunk) {
        return chunk.getTileEntities(block -> LockableBlocks.isLockable(block.getType()), false);
    }

    /** Chunks that were skipped because of the given one and should be checked again now that it is here. */
    public Set<ChunkRef> takeWaiters(ChunkRef arrived) {
        Set<ChunkRef> waiters = waiting.remove(arrived);
        return waiters == null ? Set.of() : waiters;
    }

    /** Does nothing unless the chunk and its entities are loaded. */
    public void reconcile(Chunk chunk) {
        if (!chunk.isLoaded() || !chunk.isEntitiesLoaded()) {
            return;
        }
        ChunkRef self = ChunkRef.of(chunk);
        removeStale(chunk, self);
        restoreMissing(chunk, self);
        restoreDoors(chunk, self);
    }

    /** Displays of this chunk that no locked chest backs any more. */
    private void removeStale(Chunk chunk, ChunkRef self) {
        World world = chunk.getWorld();
        for (Entity entity : chunk.getEntities()) {
            Optional<LockDisplays.Tag> found = displays.tagOf(entity);
            if (found.isEmpty() || !entity.isValid()) {
                continue;
            }
            LockDisplays.Tag tag = found.get();
            int chunkX = tag.x() >> 4;
            int chunkZ = tag.z() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                waitFor(self, world, chunkX, chunkZ);
                continue;
            }
            Locked locked = probe(world.getBlockAt(tag.x(), tag.y(), tag.z()), self);
            if (locked == UNKNOWN) {
                continue;
            }
            boolean backed = locked != null
                    && locked.data().id().toString().equals(tag.lockId())
                    && locked.anchor().getX() == tag.x()
                    && locked.anchor().getY() == tag.y()
                    && locked.anchor().getZ() == tag.z();
            if (!backed) {
                entity.remove();
            }
        }
    }

    /** Locked chests of this chunk, whose anchor is here, that have no display or too many. */
    private void restoreMissing(Chunk chunk, ChunkRef self) {
        World world = chunk.getWorld();
        Set<List<Integer>> handled = new HashSet<>();
        boolean itemFailed = false;
        for (BlockState state : tiles.apply(chunk)) {
            Block block = state.getBlock();
            if (!LockableBlocks.isLockable(block.getType())) {
                continue;
            }
            Locked locked = probe(block, self);
            if (locked == null || locked == UNKNOWN || !ChunkRef.of(locked.anchor().getChunk()).equals(self)
                    || !handled.add(List.of(locked.anchor().getX(), locked.anchor().getY(),
                    locked.anchor().getZ()))) {
                continue;
            }
            if (!displaysAreVisible(world, locked.halves(), self)) {
                continue;
            }
            List<ItemDisplay> present = displays.find(locked.halves(), locked.data().id());
            if (present.isEmpty()) {
                if (!itemFailed) {
                    itemFailed = !spawn(locked);
                }
            } else {
                for (ItemDisplay extra : present.subList(1, present.size())) {
                    extra.remove();
                }
            }
        }
    }

    /**
     * Locked doors and trapdoors of this chunk: a closed one carries its pair, an open one none. They come from the
     * data of the chunk itself, so no other chunk is involved.
     */
    private void restoreDoors(Chunk chunk, ChunkRef self) {
        World world = chunk.getWorld();
        boolean itemFailed = false;
        for (Block lower : storage.doorsIn(chunk)) {
            Locked locked = probe(lower, self);
            if (locked == null || locked == UNKNOWN || !displaysAreVisible(world, locked.halves(), self)) {
                continue;
            }
            List<ItemDisplay> present = displays.find(locked.halves(), locked.data().id());
            if (!displays.isInSync(present, lower) && !itemFailed) {
                itemFailed = !report(displays.syncDoor(lower, locked.data()));
            }
        }
    }

    /** One warning until the item builds again; the rest of the chunk is not tried after a failure. */
    private boolean spawn(Locked locked) {
        return report(displays.spawn(locked.halves(), locked.data()).isPresent());
    }

    private boolean report(boolean built) {
        if (built) {
            itemWarned = false;
            return true;
        }
        if (!itemWarned) {
            itemWarned = true;
            warn.accept("Cannot build the lock item for a display, so locked chests get no display until it can be "
                    + "built. Check the item settings in config.yml.");
        }
        return false;
    }

    /** True when every chunk a display of this chest could be in is loaded and has its entities. */
    private boolean displaysAreVisible(World world, List<Block> halves, ChunkRef self) {
        Location centre = LockDisplays.centre(halves);
        int fromX = (int) Math.floor(centre.getX() - LockDisplays.REACH) >> 4;
        int toX = (int) Math.floor(centre.getX() + LockDisplays.REACH) >> 4;
        int fromZ = (int) Math.floor(centre.getZ() - LockDisplays.REACH) >> 4;
        int toZ = (int) Math.floor(centre.getZ() + LockDisplays.REACH) >> 4;
        boolean visible = true;
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                if (!world.isChunkLoaded(x, z) || !world.getChunkAt(x, z).isEntitiesLoaded()) {
                    waitFor(self, world, x, z);
                    visible = false;
                }
            }
        }
        return visible;
    }

    /**
     * The lock on this block, null when there is none, or {@link #UNKNOWN} when the other half of a double chest
     * lies in a chunk that is not loaded (reading it would load the chunk).
     */
    private Locked probe(Block block, ChunkRef waiter) {
        if (!LockableBlocks.isLockable(block.getType())) {
            return null;
        }
        if (LockableBlocks.isHinged(block.getType())) {
            Block lower = DoorBlocks.lower(block);
            Optional<LockData> door = storage.read(lower);
            return door.map(data -> new Locked(List.of(lower), lower, data)).orElse(null);
        }
        Optional<LockData> data = block.getState(false) instanceof TileState tile
                ? storage.read(tile) : Optional.empty();
        if (data.isEmpty()) {
            return null;
        }
        World world = block.getWorld();
        if (block.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            for (BlockFace side : ChestHalves.sides(chest.getFacing())) {
                int chunkX = (block.getX() + side.getModX()) >> 4;
                int chunkZ = (block.getZ() + side.getModZ()) >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    waitFor(waiter, world, chunkX, chunkZ);
                    return UNKNOWN;
                }
            }
        }
        List<Block> halves = ChestHalves.of(block);
        return new Locked(halves, LockDisplays.anchor(halves), data.get());
    }

    private void waitFor(ChunkRef waiter, World world, int chunkX, int chunkZ) {
        ChunkRef missing = new ChunkRef(world.getUID(), chunkX, chunkZ);
        if (!missing.equals(waiter)) {
            waiting.computeIfAbsent(missing, key -> new HashSet<>()).add(waiter);
        }
    }

    private record Locked(List<Block> halves, Block anchor, LockData data) {
    }
}
