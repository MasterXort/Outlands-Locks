package ru.outlands.locks.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyColorsTest {

    private static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(LegacyColors.toMiniMessage(text));
    }

    @Test
    void textWithoutLegacyCodesIsReturnedUntouched() {
        String text = "Steve <red>joined & left";
        assertSame(text, LegacyColors.toMiniMessage(text));
    }

    @Test
    void sectionColorCodeBecomesColor() {
        Component parsed = parse("§cHello");
        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(parsed));
        assertEquals(NamedTextColor.RED, parsed.color() != null ? parsed.color()
                : parsed.children().get(0).color());
    }

    @Test
    void ampersandColorCodeBecomesColor() {
        Component parsed = parse("&aOK");
        assertEquals("OK", PlainTextComponentSerializer.plainText().serialize(parsed));
    }

    @Test
    void sectionHexFormatBecomesRgbColor() {
        Component parsed = parse("§x§f§f§0§0§8§0Hi");
        Component leaf = parsed.color() != null ? parsed : parsed.children().get(0);
        assertEquals(TextColor.color(0xff0080), leaf.color());
    }

    @Test
    void decorationCodeBecomesDecoration() {
        Component parsed = parse("§lBold");
        Component leaf = parsed.hasDecoration(TextDecoration.BOLD) ? parsed : parsed.children().get(0);
        assertEquals(TextDecoration.State.TRUE, leaf.decoration(TextDecoration.BOLD));
    }

    @Test
    void miniMessageSpecialCharactersInPlainPartAreEscaped() {
        Component parsed = parse("§a<3 you");
        assertEquals("<3 you", PlainTextComponentSerializer.plainText().serialize(parsed));
    }
}
