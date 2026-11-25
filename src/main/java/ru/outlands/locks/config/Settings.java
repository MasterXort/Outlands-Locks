package ru.outlands.locks.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import ru.outlands.locks.display.DisplaySettings;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.item.VanillaItemSpec;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parsed config.yml. Only the section of the selected item provider is validated. */
public record Settings(String language, ProviderKind provider,
                       Map<LockItemType, VanillaItemSpec> vanillaItems,
                       Map<LockItemType, String> nexoIds,
                       DisplaySettings display,
                       DisplaySettings doorDisplay,
                       DisplaySettings trapdoorDisplay,
                       Set<EntityType> explosionSources,
                       boolean itemLore) {

    public enum ProviderKind { VANILLA, NEXO }

    /** @throws IllegalArgumentException with the config path of the first problem */
    public static Settings parse(FileConfiguration config) {
        String language = config.getString("language", "en").trim();
        if (language.isEmpty()) {
            language = "en";
        }

        String rawProvider = config.getString("item-provider", "vanilla");
        ProviderKind provider;
        try {
            provider = ProviderKind.valueOf(rawProvider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("item-provider: '" + rawProvider + "' is not vanilla|nexo");
        }

        Map<LockItemType, VanillaItemSpec> vanilla = new EnumMap<>(LockItemType.class);
        Map<LockItemType, String> nexo = new EnumMap<>(LockItemType.class);
        for (LockItemType type : LockItemType.values()) {
            if (provider == ProviderKind.VANILLA) {
                String path = "items.vanilla." + type.configKey();
                ConfigurationSection section = config.getConfigurationSection(path);
                if (section == null) {
                    throw new IllegalArgumentException(path + ": section is missing");
                }
                vanilla.put(type, VanillaItemSpec.parse(section, path));
            } else {
                String path = "items.nexo." + type.configKey();
                String id = config.getString(path, "").trim();
                if (id.isEmpty()) {
                    throw new IllegalArgumentException(path + ": Nexo item id is missing");
                }
                nexo.put(type, id);
            }
        }
        DisplaySettings display = DisplaySettings.parse(config.getConfigurationSection("display"), "display");
        DisplaySettings doorDisplay = DisplaySettings.parse(config.getConfigurationSection("door-display"),
                "door-display", DisplaySettings.DOOR_DEFAULT);
        DisplaySettings trapdoorDisplay = DisplaySettings.parse(config.getConfigurationSection("trapdoor-display"),
                "trapdoor-display", DisplaySettings.TRAPDOOR_DEFAULT);
        return new Settings(language, provider, Map.copyOf(vanilla), Map.copyOf(nexo), display, doorDisplay,
                trapdoorDisplay,
                explosionSources(config), config.getBoolean("item-lore", false));
    }

    private static final String SOURCES_PATH = "explosions.allowed-sources";

    /** Entity types whose explosions may break a locked chest. A missing key means TNT and TNT minecart. */
    private static Set<EntityType> explosionSources(FileConfiguration config) {
        if (!config.contains(SOURCES_PATH)) {
            return Set.of(EntityType.TNT, EntityType.TNT_MINECART);
        }
        if (!config.isList(SOURCES_PATH)) {
            throw new IllegalArgumentException(SOURCES_PATH + ": must be a list of entity types");
        }
        Set<EntityType> sources = EnumSet.noneOf(EntityType.class);
        for (Object raw : config.getList(SOURCES_PATH)) {
            String name = String.valueOf(raw).trim();
            EntityType type;
            try {
                type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(SOURCES_PATH + ": '" + name + "' is not an entity type");
            }
            if (type == EntityType.UNKNOWN) {
                throw new IllegalArgumentException(SOURCES_PATH + ": '" + name + "' is not an entity type");
            }
            sources.add(type);
        }
        return Set.copyOf(sources);
    }
}
