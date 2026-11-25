package ru.outlands.locks.lock;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.outlands.locks.item.ItemProvider;
import ru.outlands.locks.item.LockItemType;

import java.util.Optional;
import java.util.UUID;

/** Decides whether an item opens a lock. Today only the lock_id has to be equal (master keys: PLAN section 8). */
public final class KeyMatcher {

    private final ItemProvider items;
    private final LockKeys keys;

    public KeyMatcher(ItemProvider items, LockKeys keys) {
        this.items = items;
        this.keys = keys;
    }

    public boolean matches(ItemStack stack, LockData lock) {
        return lockIdOf(stack).filter(lock.id()::equals).isPresent();
    }

    /** The lock_id of a key item; empty unless the provider says KEY and the stored id is a valid UUID. */
    public Optional<UUID> lockIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()
                || items.identify(stack).orElse(null) != LockItemType.KEY) {
            return Optional.empty();
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(keys.lockId(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** True when the main hand or the off hand holds a matching key. */
    public boolean heldBy(Player player, LockData lock) {
        return matches(player.getInventory().getItemInMainHand(), lock)
                || matches(player.getInventory().getItemInOffHand(), lock);
    }
}
