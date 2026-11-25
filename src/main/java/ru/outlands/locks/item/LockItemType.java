package ru.outlands.locks.item;

import java.util.Locale;
import java.util.Optional;

public enum LockItemType {
    LOCK("lock", "lock"),
    BLANK_KEY("blank_key", "blank-key"),
    KEY("key", "key");

    private final String commandName;
    private final String configKey;

    LockItemType(String commandName, String configKey) {
        this.commandName = commandName;
        this.configKey = configKey;
    }

    /** Name used in commands (e.g. {@code blank_key}). */
    public String commandName() {
        return commandName;
    }

    /** Key used in {@code config.yml} and language files (e.g. {@code blank-key}). */
    public String configKey() {
        return configKey;
    }

    public static Optional<LockItemType> byCommandName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (LockItemType type : values()) {
            if (type.commandName.equals(lower)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
