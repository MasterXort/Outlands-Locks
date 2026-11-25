package ru.outlands.locks.item;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Vanilla items, recognised only by the plugin's PDC marker and never by material. */
public final class VanillaItemProvider implements ItemProvider {

    private final Map<LockItemType, VanillaItemSpec> specs;
    private final NamespacedKey markerKey;
    private final Function<LockItemType, Component> names;

    public VanillaItemProvider(Map<LockItemType, VanillaItemSpec> specs, NamespacedKey markerKey,
                               Function<LockItemType, Component> names) {
        this.specs = Map.copyOf(specs);
        this.markerKey = markerKey;
        this.names = names;
    }

    @Override
    public Optional<LockItemType> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String marker = stack.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        return marker == null ? Optional.empty() : LockItemType.byCommandName(marker);
    }

    @Override
    public Optional<ItemStack> create(LockItemType type, int amount) {
        VanillaItemSpec spec = specs.get(type);
        if (spec == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(spec.material(), amount);
        ItemMeta meta = stack.getItemMeta();
        meta.customName(names.apply(type));
        applyModel(meta, spec);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.STRING, type.commandName());
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    static void applyModel(ItemMeta meta, VanillaItemSpec spec) {
        if (spec.itemModel() != null) {
            meta.setItemModel(spec.itemModel());
        }
        if (spec.customModelData() != null) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of(spec.customModelData().floatValue()));
            meta.setCustomModelDataComponent(cmd);
        }
    }
}
