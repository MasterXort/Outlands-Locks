package ru.outlands.locks.lock;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Who may open a locked chest: a holder of a matching key in either hand, or a player with the bypass permission. Who
 * may make locks and keys at all is a separate question, see {@link #canUse}.
 */
public final class LockAccess {

    public static final String BYPASS_PERMISSION = "outlands.locks.bypass";
    public static final String USE_PERMISSION = "outlands.locks.use";

    private final KeyMatcher matcher;

    public LockAccess(KeyMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * Hanging a lock, cutting the first key, copying a key and tying a lock to a key. Everybody has the permission
     * unless a permission plugin takes it away, so a server that does nothing keeps the behaviour it always had.
     * Opening with a key and taking a lock off do not ask for it.
     */
    public boolean canUse(Player player) {
        return player.hasPermission(USE_PERMISSION);
    }

    public boolean canOpen(Player player, LockData lock) {
        return player.hasPermission(BYPASS_PERMISSION) || matcher.heldBy(player, lock);
    }

    /** Creative players and holders of the bypass permission may break a locked chest (PLAN 4.9, 5a). */
    public boolean canBreak(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.hasPermission(BYPASS_PERMISSION);
    }
}
