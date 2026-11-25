package ru.outlands.locks.lang;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import ru.outlands.locks.item.LockItemType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {

    private static final String BUILTIN = """
            items:
              lock: "<!italic>Lock"
              blank-key: "<!italic>Blank Key"
              key: "<!italic>Key"
            lore:
              lock-id: "<dark_gray>{lock_id}"
              lock-unbound: "<gray>Not set up"
              lock-bound: "<gray>Set up for a key"
              key-id: "<dark_gray>{lock_id}"
            messages:
              greet:
                type: actionbar
                text: "<gray>Hello"
                sound: { key: block.chain.place, volume: 0.5, pitch: 1.2 }
              quiet:
                type: chat
                text: "Shh"
                sound: none
              nothing:
                type: none
                text: ""
                sound: none
            """;

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return cfg;
    }

    private static YamlConfiguration resource(String name) throws Exception {
        try (InputStream in = LanguageTest.class.getResourceAsStream("/languages/" + name)) {
            assertNotNull(in, name);
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return cfg;
        }
    }

    @Test
    void parsesTypeTextAndSound() {
        Language lang = Language.load("en", yaml(BUILTIN), yaml(BUILTIN), w -> { });
        MessageSpec spec = lang.message("greet");
        assertEquals(MessageSpec.Type.ACTIONBAR, spec.type());
        assertEquals("<gray>Hello", spec.text());
        assertEquals("block.chain.place", spec.sound().key());
        assertEquals(0.5f, spec.sound().volume());
        assertEquals(1.2f, spec.sound().pitch());
    }

    @Test
    void soundNoneMeansNoSound() {
        Language lang = Language.load("en", yaml(BUILTIN), yaml(BUILTIN), w -> { });
        assertNull(lang.message("quiet").sound());
        assertEquals(MessageSpec.Type.NONE, lang.message("nothing").type());
    }

    @Test
    void missingKeyFallsBackToBuiltinAndWarns() {
        String partial = """
                items:
                  lock: "Замок"
                messages:
                  greet:
                    type: chat
                    text: "Привет"
                    sound: none
                """;
        List<String> warnings = new ArrayList<>();
        Language lang = Language.load("ru", yaml(partial), yaml(BUILTIN), warnings::add);
        assertEquals("Привет", lang.message("greet").text());
        assertEquals("Shh", lang.message("quiet").text());
        assertEquals("<!italic>Key", lang.itemName(LockItemType.KEY));
        assertEquals("Замок", lang.itemName(LockItemType.LOCK));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("messages.quiet")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("items.key")), warnings.toString());
    }

    @Test
    void malformedSoundKeyIsDroppedWithWarning() {
        String bad = BUILTIN.replace("block.chain.place", "Bad Key!");
        List<String> warnings = new ArrayList<>();
        Language lang = Language.load("en", yaml(bad), yaml(BUILTIN), warnings::add);
        assertNull(lang.message("greet").sound());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void loreLinesComeFromTheSelectedLanguage() {
        String ru = BUILTIN.replace("Not set up", "Не настроен");
        Language lang = Language.load("ru", yaml(ru), yaml(BUILTIN), w -> { });
        assertEquals("<gray>Не настроен", lang.lore("lock-unbound"));
        assertEquals("<dark_gray>{lock_id}", lang.lore("key-id"));
    }

    @Test
    void aMissingLoreLineComesFromTheBuiltInFileWithAWarning() {
        String partial = BUILTIN.replace("  lock-bound: \"<gray>Set up for a key\"\n", "");
        List<String> warnings = new ArrayList<>();
        Language lang = Language.load("de", yaml(partial), yaml(BUILTIN), warnings::add);
        assertEquals("<gray>Set up for a key", lang.lore("lock-bound"));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("lore.lock-bound"));
    }

    @Test
    void unknownButWellFormedSoundKeyIsAccepted() {
        String custom = BUILTIN.replace("block.chain.place", "nexo:lock.click");
        List<String> warnings = new ArrayList<>();
        Language lang = Language.load("en", yaml(custom), yaml(BUILTIN), warnings::add);
        assertEquals("nexo:lock.click", lang.message("greet").sound().key());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void shippedLanguageFilesHaveIdenticalKeyStructure() throws Exception {
        Set<String> en = resource("en.yml").getKeys(true);
        Set<String> ru = resource("ru.yml").getKeys(true);
        assertEquals(en, ru);
    }

    @Test
    void shippedRussianFileLoadsWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        Language.load("ru", resource("ru.yml"), resource("en.yml"), warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
