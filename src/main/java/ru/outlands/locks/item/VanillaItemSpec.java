package ru.outlands.locks.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

/** Vanilla item definition from config.yml. {@code itemModel} and {@code customModelData} are optional (null). */
public record VanillaItemSpec(Material material, NamespacedKey itemModel, Integer customModelData) {

    /** @param path config path of the section, used in error messages */
    public static VanillaItemSpec parse(ConfigurationSection section, String path) {
        String rawMaterial = section.getString("material", "");
        Material material = Material.matchMaterial(rawMaterial);
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException(path + ".material: '" + rawMaterial + "' is not a valid item material");
        }

        NamespacedKey model = null;
        String rawModel = section.getString("item-model", "");
        if (!rawModel.isBlank()) {
            model = NamespacedKey.fromString(rawModel.trim());
            if (model == null) {
                throw new IllegalArgumentException(path + ".item-model: '" + rawModel + "' is not a valid namespaced key");
            }
        }

        Integer customModelData = section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null;
        return new VanillaItemSpec(material, model, customModelData);
    }
}
