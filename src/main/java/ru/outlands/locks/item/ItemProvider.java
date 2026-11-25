package ru.outlands.locks.item;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Source of the three plugin items. The rest of the plugin works only through this interface. */
public interface ItemProvider {

    Optional<LockItemType> identify(ItemStack stack);

    /** Creates a stack of the given type; empty when the item cannot be built. Amount must fit one stack. */
    Optional<ItemStack> create(LockItemType type, int amount);
}
