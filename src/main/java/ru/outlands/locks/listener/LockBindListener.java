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
import ru.outlands.locks.lock.LockData;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ties a lock to an existing key (owner decision 25.11.2026): sneak + right click with a lock whose key has not been
 * cut in the main hand and a key in the off hand. The lock then carries the key's lock id and counts as cut, so the key
 * opens every chest this lock is hung on and no other key can be cut for it. It works in the air and on any block and
 * comes before installing, so the same click never also hangs the lock: the next sneak + right click does. It needs
 * the permission {@code outlands.locks.use}; without it the click is refused, and still not passed on.
 */
public final class LockBindListener implements Listener {

    private final OutlandsLocks plugin;

    public LockBindListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    /** See {@link KeyCopyListener#onInteract} for why {@code ignoreCancelled} is not used. */
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
        ItemStack lock = player.getInventory().getItemInMainHand();
        if (plugin.items().identify(lock).orElse(null) != LockItemType.LOCK) {
            return;
        }
        Optional<LockData> own = LockData.read(lock.getItemMeta().getPersistentDataContainer(), plugin.keys());
        if (own.isPresent() && own.get().keyCut()) {
            return;
        }
        Optional<UUID> keyLockId = plugin.keyMatcher().lockIdOf(player.getInventory().getItemInOffHand());
        if (keyLockId.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        if (!plugin.access().canUse(player)) {
            plugin.messages().send(player, "use-denied");
            return;
        }
        ItemStack bound = plugin.lockItems().boundLock(lock, keyLockId.get());
        if (lock.getAmount() == 1) {
            player.getInventory().setItemInMainHand(bound);
        } else {
            ItemStack rest = lock.clone();
            rest.setAmount(lock.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest);
            for (ItemStack left : player.getInventory().addItem(bound).values()) {
                player.getWorld().dropItem(player.getLocation(), left);
            }
        }
        plugin.messages().send(player, "lock-bound", Map.of(
                "player", player.getName(),
                "lock_id", keyLockId.get().toString()));
    }
}
