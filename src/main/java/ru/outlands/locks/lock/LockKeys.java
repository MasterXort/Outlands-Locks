package ru.outlands.locks.lock;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PDC keys of the data model (PLAN section 3). */
public final class LockKeys {

    private final NamespacedKey lockId;
    private final NamespacedKey keyCut;
    private final NamespacedKey block;
    private final NamespacedKey open;
    private final Plugin plugin;

    public LockKeys(Plugin plugin) {
        this.plugin = plugin;
        this.lockId = new NamespacedKey(plugin, "lock_id");
        this.keyCut = new NamespacedKey(plugin, "key_cut");
        this.block = new NamespacedKey(plugin, "block");
        this.open = new NamespacedKey(plugin, "open");
    }

    /** String (UUID): on a chest, a lock item, a key and a display. */
    public NamespacedKey lockId() {
        return lockId;
    }

    /** Boolean: on a chest and a used lock item. */
    public NamespacedKey keyCut() {
        return keyCut;
    }

    /** Int array {x, y, z} of the display's anchor block. */
    public NamespacedKey block() {
        return block;
    }

    /** Byte (0 or 1) on the display of a door or trapdoor: the state it was drawn for. */
    public NamespacedKey open() {
        return open;
    }

    /** Key of a door lock in the data of its chunk: one entry per locked door, named after the lower block. */
    public NamespacedKey door(int x, int y, int z) {
        return new NamespacedKey(plugin, DOOR_PREFIX + x + "_" + y + "_" + z);
    }

    /** The {x, y, z} of a door entry key of this plugin; null for any other key. */
    public int[] doorAt(NamespacedKey key) {
        if (!key.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))
                || !key.getKey().startsWith(DOOR_PREFIX)) {
            return null;
        }
        String[] parts = key.getKey().substring(DOOR_PREFIX.length()).split("_");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final String DOOR_PREFIX = "door_";
}
