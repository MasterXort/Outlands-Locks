package ru.outlands.locks.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextRendererTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void ownPlaceholdersAreReplaced() {
        Component out = TextRenderer.render("<gray>Given {item} x{amount}", Map.of("item", "Lock", "amount", "3"), null);
        assertEquals("Given Lock x3", plain(out));
    }

    @Test
    void unknownBracePlaceholderStaysAsText() {
        Component out = TextRenderer.render("a {nope} b", Map.of(), null);
        assertEquals("a {nope} b", plain(out));
    }

    @Test
    void papiRunsAfterOwnPlaceholders() {
        UnaryOperator<String> papi = token -> token.equals("%player_name%") ? "Steve" : token;
        Component out = TextRenderer.render("{world}: %player_name%", Map.of("world", "world_nether"), papi);
        assertEquals("world_nether: Steve", plain(out));
    }

    @Test
    void withoutPapiPercentTokensStayAsText() {
        Component out = TextRenderer.render("Hi %player_name%", Map.of(), null);
        assertEquals("Hi %player_name%", plain(out));
    }

    @Test
    void papiLegacyColoursAreConvertedNotShownRaw() {
        UnaryOperator<String> papi = token -> "§aGreen";
        Component out = TextRenderer.render("<gray>x %rank%", Map.of(), papi);
        assertEquals("x Green", plain(out));
        boolean green = out.children().stream().anyMatch(c -> hasColourDeep(c, NamedTextColor.GREEN));
        assertEquals(true, green);
    }

    @Test
    void miniMessageIsParsedLast() {
        Component out = TextRenderer.render("<red>Locked.", Map.of(), null);
        assertEquals(NamedTextColor.RED, out.color() != null ? out.color() : out.children().get(0).color());
    }

    private static boolean hasColourDeep(Component c, NamedTextColor colour) {
        if (colour.equals(c.color())) {
            return true;
        }
        return c.children().stream().anyMatch(child -> hasColourDeep(child, colour));
    }
}
