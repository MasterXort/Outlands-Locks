package ru.outlands.locks.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.function.UnaryOperator;

/** The only class that touches PlaceholderAPI. Loaded only when PlaceholderAPI is enabled. */
public final class PapiSupport {

    private PapiSupport() {
    }

    public static UnaryOperator<String> resolverFor(Player player) {
        return token -> PlaceholderAPI.setPlaceholders(player, token);
    }
}
