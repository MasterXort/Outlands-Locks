package ru.outlands.locks.lock;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes lock data. A chest keeps it in the PDC of its tile entity. A door or trapdoor has no tile entity,
 * so its lock lives in the PDC of its chunk, one entry per door under the coordinates of the lower block.
 */
public final class LockStorage {

    private final LockKeys keys;

    public LockStorage(LockKeys keys) {
        this.keys = keys;
    }

    public Optional<LockData> read(Block block) {
        if (LockableBlocks.isHinged(block.getType())) {
            Block lower = DoorBlocks.lower(block);
            PersistentDataContainer entry = lower.getChunk().getPersistentDataContainer()
                    .get(entryKey(lower), PersistentDataType.TAG_CONTAINER);
            return entry == null ? Optional.empty() : LockData.read(entry, keys);
        }
        if (block.getState(false) instanceof TileState tile) {
            return read(tile);
        }
        return Optional.empty();
    }

    /** For callers that already hold the tile state (inventory holders), so no second lookup is needed. */
    public Optional<LockData> read(TileState tile) {
        return LockData.read(tile.getPersistentDataContainer(), keys);
    }

    /** Writes the same data to every given block (both halves of a double chest, the lower block of a door). */
    public void write(List<Block> blocks, LockData data) {
        for (Block block : blocks) {
            if (LockableBlocks.isHinged(block.getType())) {
                Block lower = DoorBlocks.lower(block);
                PersistentDataContainer chunkData = lower.getChunk().getPersistentDataContainer();
                PersistentDataContainer entry = chunkData.getAdapterContext().newPersistentDataContainer();
                data.writeTo(entry, keys);
                chunkData.set(entryKey(lower), PersistentDataType.TAG_CONTAINER, entry);
                continue;
            }
            BlockState state = block.getState();
            if (state instanceof TileState tile) {
                data.writeTo(tile.getPersistentDataContainer(), keys);
                tile.update(true, false);
            }
        }
    }

    public void clear(List<Block> blocks) {
        for (Block block : blocks) {
            if (LockableBlocks.isHinged(block.getType())) {
                Block lower = DoorBlocks.lower(block);
                lower.getChunk().getPersistentDataContainer().remove(entryKey(lower));
                continue;
            }
            BlockState state = block.getState();
            if (state instanceof TileState tile) {
                LockData.clear(tile.getPersistentDataContainer(), keys);
                tile.update(true, false);
            }
        }
    }

    /** The lower blocks of the locked doors and trapdoors of a loaded chunk, after vanished ones are dropped. */
    public List<Block> doorsIn(Chunk chunk) {
        PersistentDataContainer chunkData = chunk.getPersistentDataContainer();
        List<Block> doors = new ArrayList<>();
        for (NamespacedKey key : List.copyOf(chunkData.getKeys())) {
            int[] at = keys.doorAt(key);
            if (at == null) {
                continue;
            }
            Block block = chunk.getWorld().getBlockAt(at[0], at[1], at[2]);
            if (LockableBlocks.isHinged(block.getType())
                    && (!(block.getBlockData() instanceof Door door) || door.getHalf() == Bisected.Half.BOTTOM)) {
                doors.add(block);
            } else {
                chunkData.remove(key);
            }
        }
        return doors;
    }

    private NamespacedKey entryKey(Block lower) {
        return keys.door(lower.getX(), lower.getY(), lower.getZ());
    }
}
