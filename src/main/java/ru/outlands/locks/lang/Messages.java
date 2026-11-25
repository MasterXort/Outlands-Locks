package ru.outlands.locks.lang;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.outlands.locks.text.TextRenderer;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Sends localised messages and sounds. The language is read on every call, so a reload takes effect at once. */
public final class Messages {

    private final Supplier<Language> language;
    private final Function<Player, UnaryOperator<String>> papi;

    /** @param papi per-player PlaceholderAPI resolver; null when PlaceholderAPI is not installed */
    public Messages(Supplier<Language> language, Function<Player, UnaryOperator<String>> papi) {
        this.language = language;
        this.papi = papi;
    }

    public void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        MessageSpec spec = language.get().message(key);
        Player player = to instanceof Player p ? p : null;

        if (spec.type() != MessageSpec.Type.NONE && !spec.text().isEmpty()) {
            UnaryOperator<String> resolver = player != null && papi != null ? papi.apply(player) : null;
            Component text = TextRenderer.render(spec.text(), placeholders, resolver);
            if (spec.type() == MessageSpec.Type.ACTIONBAR) {
                to.sendActionBar(text);
            } else {
                to.sendMessage(text);
            }
        }
        if (player != null && spec.sound() != null) {
            SoundSpec s = spec.sound();
            player.playSound(Sound.sound(Key.key(s.key()), Sound.Source.PLAYER, s.volume(), s.pitch()));
        }
    }
}
