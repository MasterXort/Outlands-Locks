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
import ru.outlands.locks.lock.DoorBlocks;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.Optional;

/**
 * Right click on a locked chest: a blank key in the main hand cuts the first key when the player sneaks (PLAN 4.2,
 * owner decision 25.11.2026), otherwise the chest opens only for a matching key in either hand or the bypass
 * permission (4.3). The main-hand action goes first (4.6).
 */
public final class KeyListener implements Listener {

    private final OutlandsLocks plugin;

    public KeyListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    /** HIGH runs after Towny and Quarters (LOWEST..NORMAL); a cancelled event is skipped. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !LockableBlocks.isLockable(block.getType())) {
            return;
        }
        Optional<LockData> stored = plugin.storage().read(block);
        if (stored.isEmpty()) {
            return;
        }
        LockData data = stored.get();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (player.isSneaking() && plugin.items().identify(hand).orElse(null) == LockItemType.BLANK_KEY) {
            event.setCancelled(true);
            if (!data.keyCut()) {
                cutFirstKey(player, block, hand, data);
                return;
            }
            plugin.messages().send(player, "key-already-cut", LockPlaceholders.of(player, block, data));
            if (plugin.access().canOpen(player, data)) {
                // Sneaking with an item in hand never opens a chest in vanilla, so open it here (PLAN 4.6).
                LockOpening.byHand(plugin, player, block);
            }
            return;
        }

        if (!plugin.access().canOpen(player, data)) {
            if (LockableBlocks.isHinged(block.getType()) && DoorBlocks.isOpen(block)) {
                return; // an open locked door may be shut by anyone; the lock holds again
            }
            event.setCancelled(true);
            plugin.messages().send(player, "chest-locked", LockPlaceholders.of(player, block, data));
        }
    }

    private void cutFirstKey(Player player, Block block, ItemStack blank, LockData data) {
        Optional<ItemStack> created = plugin.lockItems().key(data.id());
        if (created.isEmpty()) {
            plugin.getLogger().warning("Key not cut at " + block.getWorld().getName() + " " + block.getX() + " "
                    + block.getY() + " " + block.getZ() + ": the key item cannot be built.");
            return;
        }
        ItemStack key = created.get();

        ItemStack rest = blank.clone();
        rest.setAmount(blank.getAmount() - 1);
        player.getInventory().setItemInMainHand(rest.getAmount() > 0 ? rest : null);
        for (ItemStack left : player.getInventory().addItem(key).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }

        LockData cut = new LockData(data.id(), true);
        plugin.storage().write(ChestHalves.of(block), cut);
        plugin.messages().send(player, "key-cut", LockPlaceholders.of(player, block, cut));
    }
}
