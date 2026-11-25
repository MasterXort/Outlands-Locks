package ru.outlands.locks.listener;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.DoorBlocks;
import ru.outlands.locks.lock.LockableBlocks;

/**
 * Opens a lock by hand where vanilla would not: sneaking with an item in the hand never opens a chest or a door, so
 * the listeners that let such a click through (PLAN 4.6) do the opening themselves.
 */
final class LockOpening {

    private LockOpening() {
    }

    /** Opens the chest, or toggles the door or trapdoor; its displays follow the new state. */
    static void byHand(OutlandsLocks plugin, Player player, Block block) {
        if (LockableBlocks.isHinged(block.getType())) {
            Block lower = DoorBlocks.lower(block);
            if (!(lower.getBlockData() instanceof Openable hinged)) {
                return;
            }
            boolean open = !hinged.isOpen();
            hinged.setOpen(open);
            lower.setBlockData(hinged);
            boolean trapdoor = LockableBlocks.isTrapdoor(lower.getType());
            Sound sound = trapdoor
                    ? (open ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE)
                    : (open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE);
            lower.getWorld().playSound(lower.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 1f, 1f);
            plugin.storage().read(lower).ifPresent(data -> plugin.displays().syncDoor(lower, data));
        } else if (block.getState(false) instanceof Container container) {
            player.openInventory(container.getInventory());
        }
    }
}
