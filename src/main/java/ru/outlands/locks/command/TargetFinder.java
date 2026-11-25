package ru.outlands.locks.command;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Finds the block a player is looking at. A seam: MockBukkit cannot ray-trace, so tests supply their own. */
@FunctionalInterface
public interface TargetFinder {

    /** How far a player may be from the chest they point at, in blocks. */
    int REACH = 5;

    /** The block in the player's line of sight, or null when there is none within reach. */
    Block lookingAt(Player player);

    static TargetFinder lineOfSight() {
        return player -> player.getTargetBlockExact(REACH);
    }
}
