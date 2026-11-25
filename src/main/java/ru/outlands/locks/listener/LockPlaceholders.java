package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ru.outlands.locks.lock.LockData;

import java.util.Map;

/** Placeholder values shared by the messages of the lock listeners. */
public final class LockPlaceholders {

    private LockPlaceholders() {
    }

    public static Map<String, String> of(Player player, Block block, LockData data) {
        return Map.of(
                "player", player.getName(),
                "lock_id", data.id().toString(),
                "key_cut", String.valueOf(data.keyCut()),
                "world", block.getWorld().getName(),
                "x", String.valueOf(block.getX()),
                "y", String.valueOf(block.getY()),
                "z", String.valueOf(block.getZ()));
    }
}
