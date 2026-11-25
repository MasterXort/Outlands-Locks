package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.List;
import java.util.Optional;

/**
 * Sneak + left click on a locked chest with a matching key in the main hand takes the lock off (PLAN 4.5). A lock whose
 * key has not been cut yet comes off the same way without any key (owner decision 25.11.2026). A creative left click
 * breaks the block at once, so the same removal also runs on {@link BlockBreakEvent}.
 */
public final class LockRemoveListener implements Listener {

    private final OutlandsLocks plugin;

    public LockRemoveListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    /** HIGH runs after Towny and Quarters (LOWEST..NORMAL); a cancelled event is skipped. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (!player.isSneaking() || block == null) {
            return;
        }
        Optional<LockData> stored = lockOf(block);
        if (stored.isEmpty()) {
            return;
        }
        LockData data = stored.get();

        if (!data.keyCut() || plugin.keyMatcher().matches(player.getInventory().getItemInMainHand(), data)) {
            event.setCancelled(true);
            removeLock(player, block, data);
        } else if (plugin.keyMatcher().matches(player.getInventory().getItemInOffHand(), data)) {
            plugin.messages().send(player, "remove-need-main-hand", LockPlaceholders.of(player, block, data));
        } else if (plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null)
                == LockItemType.KEY) {
            plugin.messages().send(player, "remove-wrong-key", LockPlaceholders.of(player, block, data));
        }
    }

    /** Sneak + matching key: the break is cancelled and the lock comes off instead. Other breaks: LockBreakListener. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!player.isSneaking()) {
            return;
        }
        Optional<LockData> stored = lockOf(block);
        if (stored.isPresent() && (!stored.get().keyCut()
                || plugin.keyMatcher().matches(player.getInventory().getItemInMainHand(), stored.get()))) {
            event.setCancelled(true);
            removeLock(player, block, stored.get());
        }
    }

    private Optional<LockData> lockOf(Block block) {
        return LockableBlocks.isLockable(block.getType()) ? plugin.storage().read(block) : Optional.empty();
    }

    private void removeLock(Player player, Block block, LockData data) {
        Optional<ItemStack> created = plugin.lockItems().usedLock(data);
        if (created.isEmpty()) {
            plugin.getLogger().warning("Lock not removed at " + block.getWorld().getName() + " " + block.getX() + " "
                    + block.getY() + " " + block.getZ() + ": the lock item cannot be built.");
            return;
        }
        ItemStack lock = created.get();

        List<Block> halves = ChestHalves.of(block);
        plugin.storage().clear(halves);
        plugin.displays().remove(halves, data.id());

        boolean dropped = false;
        for (ItemStack left : player.getInventory().addItem(lock).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
            dropped = true;
        }
        plugin.messages().send(player, dropped ? "lock-removed-dropped" : "lock-removed",
                LockPlaceholders.of(player, block, data));
    }
}
