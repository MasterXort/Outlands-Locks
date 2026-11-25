package ru.outlands.locks.text;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/** Converts legacy colour codes ({@code §} and {@code &}) to a MiniMessage string. */
public final class LegacyColors {

    private static final Pattern LEGACY_CODE = Pattern.compile("[§&][0-9a-fk-orxA-FK-ORX#]");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private LegacyColors() {
    }

    public static String toMiniMessage(String text) {
        if (!LEGACY_CODE.matcher(text).find()) {
            return text;
        }
        return MiniMessage.miniMessage().serialize(LEGACY.deserialize(text.replace('§', '&')));
    }
}
