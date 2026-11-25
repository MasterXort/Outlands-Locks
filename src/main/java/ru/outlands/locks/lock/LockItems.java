package ru.outlands.locks.lock;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.outlands.locks.item.ItemProvider;
import ru.outlands.locks.item.LockItemType;

import java.util.Optional;
import java.util.UUID;

/** Builds the lock and key items the plugin hands out, with the optional lore. */
public final class LockItems {

    private final ItemProvider items;
    private final LockKeys keys;
    private final ItemLore lore;

    public LockItems(ItemProvider items, LockKeys keys, ItemLore lore) {
        this.items = items;
        this.keys = keys;
        this.lore = lore;
    }

    /** A lock item carrying the given lock_id and key_cut; empty when the provider cannot build a lock. */
    public Optional<ItemStack> usedLock(LockData data) {
        Optional<ItemStack> created = items.create(LockItemType.LOCK, 1);
        if (created.isEmpty()) {
            return Optional.empty();
        }
        ItemStack lock = created.get();
        ItemMeta meta = lock.getItemMeta();
        data.writeTo(meta.getPersistentDataContainer(), keys);
        lore.lock(meta, data);
        lock.setItemMeta(meta);
        return Optional.of(lock);
    }

    /** A new lock (no id yet) for the {@code give} command. */
    public Optional<ItemStack> newLock(int amount) {
        Optional<ItemStack> created = items.create(LockItemType.LOCK, amount);
        if (created.isEmpty()) {
            return Optional.empty();
        }
        ItemStack lock = created.get();
        ItemMeta meta = lock.getItemMeta();
        lore.lock(meta, null);
        lock.setItemMeta(meta);
        return Optional.of(lock);
    }

    /** One of the given locks, tied to the lock id of a key: same item (name kept), key_cut set. */
    public ItemStack boundLock(ItemStack lock, UUID keyLockId) {
        ItemStack bound = lock.clone();
        bound.setAmount(1);
        ItemMeta meta = bound.getItemMeta();
        LockData data = new LockData(keyLockId, true);
        data.writeTo(meta.getPersistentDataContainer(), keys);
        lore.lock(meta, data);
        bound.setItemMeta(meta);
        return bound;
    }

    /** A key for the given lock: the provider's key item plus the lock_id; empty when the provider cannot build it. */
    public Optional<ItemStack> key(UUID lockId) {
        Optional<ItemStack> created = items.create(LockItemType.KEY, 1);
        if (created.isEmpty()) {
            return Optional.empty();
        }
        ItemStack key = created.get();
        ItemMeta meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(keys.lockId(), PersistentDataType.STRING, lockId.toString());
        lore.key(meta, lockId);
        key.setItemMeta(meta);
        return Optional.of(key);
    }
}
