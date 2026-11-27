package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;

import java.util.List;
import java.util.Optional;

/**
 * Lets a locked block go where the plugin itself allows it (a creative or bypass break of the block, or of the floor
 * under a door): the listener calls this once the break is final and before the block is removed.
 */
final class LockRelease {

    private LockRelease() {
    }

    /**
     * Takes the lock off the block (both halves of a double chest, a door as a whole), removes its display and drops
     * the lock as an item where the block stands. The lock is gone before the block is, so the guard that keeps the
     * server from destroying a locked door finds nothing to hold when the other half of a door follows the first.
     */
    static void release(OutlandsLocks plugin, Block block, LockData data) {
        List<Block> halves = ChestHalves.of(block);
        Optional<ItemStack> lock = plugin.lockItems().usedLock(data);
        plugin.storage().clear(halves);
        plugin.displays().remove(halves, data.id());
        if (lock.isPresent()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), lock.get());
        } else {
            plugin.getLogger().warning("Lock at " + block.getWorld().getName() + " " + block.getX() + " "
                    + block.getY() + " " + block.getZ() + " not dropped: the lock item cannot be built.");
        }
    }
}
