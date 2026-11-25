package ru.outlands.locks.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.List;
import java.util.Optional;

/**
 * Breaking a locked chest by hand (PLAN 4.9): survival and adventure are refused; creative and the bypass permission
 * break it and the lock drops as an item.
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

        Optional<ItemStack> lock = plugin.lockItems().usedLock(data);
        if (lock.isEmpty()) {
            plugin.getLogger().warning("Locked chest at " + block.getWorld().getName() + " " + block.getX() + " "
                    + block.getY() + " " + block.getZ() + " not broken: the lock item cannot be built.");
            event.setCancelled(true);
            return;
        }
        List<Block> halves = ChestHalves.of(block);
        plugin.storage().clear(halves);
        plugin.displays().remove(halves, data.id());
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(at, lock.get());
    }
}
