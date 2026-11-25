package ru.outlands.locks.nexo;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;
import ru.outlands.locks.item.ItemProvider;
import ru.outlands.locks.item.LockItemType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Nexo items by id from config.yml. Only loaded when item-provider is nexo and Nexo is present. */
public final class NexoItemProvider implements ItemProvider {

    private final Map<LockItemType, String> idByType;
    private final Map<String, LockItemType> typeById = new HashMap<>();

    public NexoItemProvider(Map<LockItemType, String> ids) {
        this.idByType = new EnumMap<>(ids);
        ids.forEach((type, id) -> typeById.put(id, type));
    }

    @Override
    public Optional<LockItemType> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        String id = NexoItems.idFromItem(stack);
        return id == null ? Optional.empty() : Optional.ofNullable(typeById.get(id));
    }

    @Override
    public Optional<ItemStack> create(LockItemType type, int amount) {
        String id = idByType.get(type);
        ItemBuilder builder = id == null ? null : NexoItems.itemFromId(id);
        if (builder == null) {
            return Optional.empty();
        }
        ItemStack stack = builder.build().clone();
        stack.setAmount(amount);
        return Optional.of(stack);
    }
}
