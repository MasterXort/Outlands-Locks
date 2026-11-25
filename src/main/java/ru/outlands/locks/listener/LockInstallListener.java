package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
import java.util.UUID;

/** Sneak + right click with a lock on an unlocked chest installs the lock (PLAN 4.1). */
public final class LockInstallListener implements Listener {

    private final OutlandsLocks plugin;

    public LockInstallListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    /** HIGH runs after Towny and Quarters (LOWEST..NORMAL); a cancelled event is skipped. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (!player.isSneaking() || block == null || !LockableBlocks.isLockable(block.getType())) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.items().identify(hand).orElse(null) != LockItemType.LOCK) {
            return;
        }

        event.setCancelled(true);
        List<Block> halves = ChestHalves.of(block);
        Optional<LockData> existing = halves.stream()
                .map(half -> plugin.storage().read(half))
                .flatMap(Optional::stream)
                .findFirst();
        if (existing.isPresent()) {
            if (plugin.access().canOpen(player, existing.get())) {
                // Sneaking with an item in hand never opens a chest or a door in vanilla, so open it here (PLAN 4.6).
                LockOpening.byHand(plugin, player, block);
            } else {
                plugin.messages().send(player, "lock-already-locked");
            }
            return;
        }

        LockData data = LockData.read(hand.getItemMeta().getPersistentDataContainer(), plugin.keys())
                .orElseGet(() -> new LockData(UUID.randomUUID(), false));
        takeOne(player, hand);
        plugin.storage().write(halves, data);
        boolean shown = LockableBlocks.isHinged(block.getType())
                ? plugin.displays().syncDoor(halves.get(0), data)
                : plugin.displays().spawn(halves, data).isPresent();
        if (!shown) {
            plugin.getLogger().warning("Lock display not created at " + block.getWorld().getName() + " "
                    + block.getX() + " " + block.getY() + " " + block.getZ() + ": the lock item cannot be built.");
        }
        plugin.messages().send(player, "lock-installed", LockPlaceholders.of(player, block, data));
    }

    private static void takeOne(Player player, ItemStack hand) {
        ItemStack rest = hand.clone();
        rest.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(rest.getAmount() > 0 ? rest : null);
    }
}
