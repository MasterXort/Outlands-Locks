package ru.outlands.locks.lock;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;
import ru.outlands.locks.lang.Language;
import ru.outlands.locks.text.TextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The optional lore of locks and keys (config {@code item-lore}, off by default). When it is off the plugin writes
 * nothing on the items. When it is on, the lore of a lock or key the plugin builds or rewrites is replaced by these
 * lines, taken from the language file.
 */
public final class ItemLore {

    private final BooleanSupplier enabled;
    private final Supplier<Language> language;

    public ItemLore(BooleanSupplier enabled, Supplier<Language> language) {
        this.enabled = enabled;
        this.language = language;
    }

    /** Lock lore: the id line when the lock has an id, then "set up for a key" or "not set up". */
    public void lock(ItemMeta meta, LockData data) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (data != null) {
            add(lines, "lock-id", data.id());
        }
        add(lines, data != null && data.keyCut() ? "lock-bound" : "lock-unbound", data == null ? null : data.id());
        meta.lore(lines);
    }

    /** Key lore: the id line. */
    public void key(ItemMeta meta, UUID lockId) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        add(lines, "key-id", lockId);
        meta.lore(lines);
    }

    private void add(List<Component> lines, String key, UUID lockId) {
        String text = language.get().lore(key);
        if (text == null || text.isBlank()) {
            return;
        }
        Map<String, String> placeholders = lockId == null ? Map.of() : Map.of("lock_id", lockId.toString());
        lines.add(TextRenderer.render(text, placeholders, null));
    }
}
