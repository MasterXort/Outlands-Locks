package ru.outlands.locks.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.LockItemType;

import java.util.Optional;
import java.util.UUID;

/**
 * Key copying (PLAN 4.4, owner decision 25.11.2026): sneak + right click with a blank key in the main hand and a key
 * in the off hand turns one blank into a copy of that key. The key stays. It works in the air and on any block, and
 * it comes before cutting and opening, so the event is cancelled here and the other listeners skip it.
 */
public final class KeyCopyListener implements Listener {

    private final OutlandsLocks plugin;

    public KeyCopyListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    /**
     * HIGH runs after Towny and Quarters (LOWEST..NORMAL). A click in the air has no block, so Bukkit reports that event
     * as cancelled from the start and {@code ignoreCancelled} cannot be used: what counts is whether using the item in
     * the hand was denied.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack blank = player.getInventory().getItemInMainHand();
        if (plugin.items().identify(blank).orElse(null) != LockItemType.BLANK_KEY) {
            return;
        }
        Optional<UUID> lockId = plugin.keyMatcher().lockIdOf(player.getInventory().getItemInOffHand());
        if (lockId.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Optional<ItemStack> created = plugin.lockItems().key(lockId.get());
        if (created.isEmpty()) {
            plugin.getLogger().warning("Key not copied: the key item cannot be built.");
            return;
        }
        ItemStack copy = created.get();

        if (blank.getAmount() == 1) {
            player.getInventory().setItemInMainHand(copy);
        } else {
            ItemStack rest = blank.clone();
            rest.setAmount(blank.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest);
            for (ItemStack left : player.getInventory().addItem(copy).values()) {
                player.getWorld().dropItem(player.getLocation(), left);
            }
        }
        plugin.messages().send(player, "key-copied");
    }
}
