package ru.outlands.locks.lang;

import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.outlands.locks.item.LockItemType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Immutable set of messages and item names. Missing keys come from the built-in English file. */
public final class Language {

    private final Map<String, MessageSpec> messages;
    private final Map<LockItemType, String> itemNames;
    private final Map<String, String> lore;

    /** Keys of the lore: the id line and the state line of a lock, the id line of a key. */
    public static final List<String> LORE_KEYS = List.of("lock-id", "lock-unbound", "lock-bound", "key-id");

    private Language(Map<String, MessageSpec> messages, Map<LockItemType, String> itemNames,
                     Map<String, String> lore) {
        this.messages = messages;
        this.itemNames = itemNames;
        this.lore = lore;
    }

    public static Language load(String name, YamlConfiguration selected, YamlConfiguration builtin,
                                Consumer<String> warn) {
        Map<LockItemType, String> names = new EnumMap<>(LockItemType.class);
        for (LockItemType type : LockItemType.values()) {
            String path = "items." + type.configKey();
            String value = selected.getString(path);
            if (value == null) {
                warn.accept("Language '" + name + "': missing key " + path + ", using built-in English");
                value = builtin.getString(path, type.configKey());
            }
            names.put(type, value);
        }

        Map<String, String> loreLines = new HashMap<>();
        for (String key : LORE_KEYS) {
            String path = "lore." + key;
            String value = selected.getString(path);
            if (value == null) {
                warn.accept("Language '" + name + "': missing key " + path + ", using built-in English");
                value = builtin.getString(path, "");
            }
            loreLines.put(key, value);
        }

        Map<String, MessageSpec> parsed = new LinkedHashMap<>();
        ConfigurationSection builtinMessages = builtin.getConfigurationSection("messages");
        if (builtinMessages != null) {
            ConfigurationSection selectedMessages = selected.getConfigurationSection("messages");
            for (String key : builtinMessages.getKeys(false)) {
                String path = "messages." + key;
                ConfigurationSection source = selectedMessages == null ? null : selectedMessages.getConfigurationSection(key);
                if (source == null) {
                    warn.accept("Language '" + name + "': missing key " + path + ", using built-in English");
                    source = builtinMessages.getConfigurationSection(key);
                }
                parsed.put(key, parseMessage(name, path, source, warn));
            }
        }
        return new Language(Map.copyOf(parsed), Map.copyOf(names), Map.copyOf(loreLines));
    }

    private static MessageSpec parseMessage(String lang, String path, ConfigurationSection section,
                                            Consumer<String> warn) {
        MessageSpec.Type type = MessageSpec.Type.CHAT;
        String rawType = section.getString("type", "chat");
        try {
            type = MessageSpec.Type.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn.accept("Language '" + lang + "': " + path + ".type '" + rawType + "' is not chat|actionbar|none, using chat");
        }
        return new MessageSpec(type, section.getString("text", ""), parseSound(lang, path, section, warn));
    }

    private static SoundSpec parseSound(String lang, String path, ConfigurationSection section,
                                        Consumer<String> warn) {
        ConfigurationSection sound = section.getConfigurationSection("sound");
        if (sound == null) {
            return null;
        }
        String key = sound.getString("key", "");
        if (key.isEmpty()) {
            return null;
        }
        if (!Key.parseable(key)) {
            warn.accept("Language '" + lang + "': " + path + ".sound.key '" + key + "' is not a valid sound key, sound disabled");
            return null;
        }
        return new SoundSpec(key, (float) sound.getDouble("volume", 1.0), (float) sound.getDouble("pitch", 1.0));
    }

    public MessageSpec message(String key) {
        MessageSpec spec = messages.get(key);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown message key: " + key);
        }
        return spec;
    }

    public String itemName(LockItemType type) {
        return itemNames.get(type);
    }

    /** A lore line (MiniMessage) by one of {@link #LORE_KEYS}; empty text means the line is left out. */
    public String lore(String key) {
        return lore.get(key);
    }
}
