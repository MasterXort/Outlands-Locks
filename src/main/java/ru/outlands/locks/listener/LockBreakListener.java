package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.Optional;

/**
 * Breaking a locked chest by hand (PLAN 4.9): survival and adventure are refused; creative and the bypass permission
 * break it and the lock drops as an item.
 *
 * <p>The refusals are decided at HIGH, after Towny and Quarters. The lock itself is only taken off at MONITOR, once the
 * break is final: a break that another plugin cancels later leaves the chest with its lock and its display, and
 * nothing drops.
 *
 * <p>Must be registered after {@link LockRemoveListener}: sneak + matching key in the main hand is handled there
 * (it cancels the event first), and this listener skips cancelled events.
 */
public final class LockBreakListener implements Listener {

    private final OutlandsLocks plugin;

    public LockBreakListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!LockableBlocks.isLockable(block.getType())) {
            return;
        }
        Optional<LockData> stored = plugin.storage().read(block);
        if (stored.isEmpty()) {
            return;
        }
        LockData data = stored.get();
        Player player = event.getPlayer();

        if (!plugin.access().canBreak(player)) {
            event.setCancelled(true);
            plugin.messages().send(player, "break-denied", LockPlaceholders.of(player, block, data));
            return;
        }

        if (plugin.lockItems().usedLock(data).isEmpty()) {
            plugin.getLogger().warning("Locked chest at " + block.getWorld().getName() + " " + block.getX() + " "
                    + block.getY() + " " + block.getZ() + " not broken: the lock item cannot be built.");
            event.setCancelled(true);
        }
    }

    /**
     * The break went through: the lock comes off, its display goes and the lock drops as an item. The block itself is
     * removed only after this, so nothing that follows from its removal (the other half of a door) finds the lock
     * still on.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (LockableBlocks.isLockable(block.getType())) {
            plugin.storage().read(block).ifPresent(data -> LockRelease.release(plugin, block, data));
        }
    }
}
