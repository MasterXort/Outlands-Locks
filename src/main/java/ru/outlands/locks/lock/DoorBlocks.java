package ru.outlands.locks.lock;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;

/** A door is two blocks; its lock belongs to the lower one, whichever half was clicked. */
public final class DoorBlocks {

    private DoorBlocks() {
    }

    /** The lower half of the door the given block belongs to; the block itself when it is not a door. */
    public static Block lower(Block block) {
        if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    public static boolean isOpen(Block block) {
        return lower(block).getBlockData() instanceof Openable openable && openable.isOpen();
    }
}
