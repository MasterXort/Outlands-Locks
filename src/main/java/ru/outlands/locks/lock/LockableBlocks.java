package ru.outlands.locks.lock;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * Blocks that can carry a lock: the chest, the trapped chest, the wooden doors and the wooden trapdoors. Copper
 * chests, iron and copper doors and trapdoors are deliberately not lockable. Which doors and trapdoors are wooden is
 * what the server's block tags {@code #minecraft:wooden_doors} and {@code #minecraft:wooden_trapdoors} say.
 */
public final class LockableBlocks {

    private static final Set<Material> CHESTS = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST);

    private LockableBlocks() {
    }

    public static boolean isLockable(Material material) {
        return CHESTS.contains(material) || isHinged(material);
    }

    /** A wooden door or trapdoor: a block that opens on a hinge and has no tile entity to keep a lock in. */
    public static boolean isHinged(Material material) {
        return isDoor(material) || isTrapdoor(material);
    }

    /** Any wooden trapdoor: not the iron trapdoor, not a copper one. */
    public static boolean isTrapdoor(Material material) {
        return Tag.WOODEN_TRAPDOORS.isTagged(material);
    }

    /** Any wooden door (oak to pale oak, bamboo, cherry, crimson, warped): not the iron door, not a copper one. */
    public static boolean isDoor(Material material) {
        return Tag.WOODEN_DOORS.isTagged(material);
    }
}
