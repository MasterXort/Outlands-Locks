package ru.outlands.locks.lock;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Who may open a locked chest: a holder of a matching key in either hand, or a player with the bypass permission. */
public final class LockAccess {

    public static final String BYPASS_PERMISSION = "outlands.locks.bypass";

    private final KeyMatcher matcher;

    public LockAccess(KeyMatcher matcher) {
        this.matcher = matcher;
    }

    public boolean canOpen(Player player, LockData lock) {
        return player.hasPermission(BYPASS_PERMISSION) || matcher.heldBy(player, lock);
    }

    /** Creative players and holders of the bypass permission may break a locked chest (PLAN 4.9, 5a). */
    public boolean canBreak(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.hasPermission(BYPASS_PERMISSION);
    }
}
