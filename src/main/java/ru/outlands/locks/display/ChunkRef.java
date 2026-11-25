package ru.outlands.locks.display;

import org.bukkit.Chunk;

import java.util.UUID;

/** A chunk by coordinates, safe to keep while the chunk itself is unloaded. */
public record ChunkRef(UUID world, int x, int z) {

    public static ChunkRef of(Chunk chunk) {
        return new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
