package ru.outlands.locks.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import ru.outlands.locks.OutlandsLocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** New keys are added to the files of a server as text; what the owner has is left exactly as it is. */
class ConfigUpdaterTest {

    @TempDir
    Path dir;

    private final List<String> infos = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    // ---- helpers ----

    private static String resource(String name) throws IOException {
        try (InputStream in = ConfigUpdaterTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return yaml;
    }

    private Path write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private List<String> update(Path file, String bundled) {
        return ConfigUpdater.update(file, bundled, infos::add, warnings::add);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /** The text without the entry of this key at this indent: its comments above, its lines below, one blank line. */
    private static String without(String text, String key, int indent) {
        return without(text, key, indent, "");
    }

    /** The same, looking for the key only after the line that starts with {@code after} (the parent's own line). */
    private static String without(String text, String key, int indent, String after) {
        List<String> lines = new ArrayList<>(List.of(text.split("\\n", -1)));
        String head = " ".repeat(indent) + key + ":";
        int at = -1;
        int start = 0;
        if (!after.isEmpty()) {
            while (start < lines.size() && !lines.get(start).trim().startsWith(after)) {
                start++;
            }
        }
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.equals(head) || line.startsWith(head + " ")) {
                at = i;
                break;
            }
        }
        assertTrue(at >= 0, key);
        int from = at;
        while (from > 0 && lines.get(from - 1).trim().startsWith("#")) {
            from--;
        }
        int to = at + 1;
        while (to < lines.size() && (lines.get(to).isBlank() || indentOf(lines.get(to)) > indent)) {
            to++;
        }
        while (to > at + 1 && lines.get(to - 1).isBlank()) {
            to--;
        }
        lines.subList(from, to).clear();
        return String.join("\n", lines);
    }

    private static Set<String> leaves(YamlConfiguration yaml) {
        Set<String> keys = new java.util.TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                keys.add(key + "=" + yaml.get(key));
            }
        }
        return keys;
    }

    // ---- nothing to add ----

    @Test
    void aFileThatHasEverythingIsLeftAlone() throws Exception {
        String bundled = resource("config.yml");
        Path file = write("config.yml", bundled);

        assertTrue(update(file, bundled).isEmpty());

        assertEquals(bundled, read(file));
        assertFalse(Files.exists(dir.resolve("config.yml.bak")));
        assertTrue(infos.isEmpty() && warnings.isEmpty());
    }

    // ---- adding ----

    @Test
    void aMissingSectionIsAddedAtTheEndWithItsComment() throws Exception {
        String bundled = resource("config.yml");
        String old = without(bundled, "trapdoor-display", 0);
        Path file = write("config.yml", old);

        List<String> added = update(file, bundled);

        assertEquals(List.of("trapdoor-display"), added);
        String result = read(file);
        assertTrue(result.contains("# Lock display on trapdoors"));
        assertEquals(leaves(yaml(bundled)), leaves(yaml(result)));
        assertTrue(result.startsWith(old.substring(0, old.indexOf("# Entity types"))), "everything before is as it was");
    }

    @Test
    void aMissingKeyInsideABlockGoesIntoThatBlock() throws Exception {
        String bundled = resource("languages/en.yml");
        Path file = write("en.yml", without(bundled, "lock-bound", 2, "messages:"));

        List<String> added = update(file, bundled);

        assertEquals(List.of("messages.lock-bound"), added);
        assertEquals(leaves(yaml(bundled)), leaves(yaml(read(file))));
    }

    @Test
    void aMessageWithAMultiLineTextIsAddedWhole() throws Exception {
        String bundled = resource("languages/en.yml");
        Path file = write("en.yml", without(bundled, "admin-inspect", 2));

        update(file, bundled);

        YamlConfiguration result = yaml(read(file));
        assertEquals(yaml(bundled).getString("messages.admin-inspect.text"), result.getString("messages.admin-inspect.text"));
        assertTrue(result.getString("messages.admin-inspect.text").contains("\n"));
    }

    @Test
    void aNewSectionAndANewEntryInAnotherBlockAtTheSameLineDoNotMix() throws Exception {
        String bundled = resource("languages/en.yml");
        String old = without(without(bundled, "admin-usage", 2, "messages:"), "lore", 0);
        Path file = write("en.yml", old);

        List<String> added = update(file, bundled);

        assertEquals(2, added.size());
        assertEquals(leaves(yaml(bundled)), leaves(yaml(read(file))));
    }

    @Test
    void severalMissingEntriesAreAllAddedAndLoggedOnce() throws Exception {
        String bundled = resource("config.yml");
        String old = without(without(without(bundled, "item-lore", 0), "door-display", 0), "trapdoor-display", 0);
        Path file = write("config.yml", old);

        List<String> added = update(file, bundled);

        assertEquals(3, added.size());
        assertEquals(1, infos.size());
        assertTrue(infos.get(0).contains("added 3 missing entries"), infos.get(0));
        assertTrue(infos.get(0).contains("config.yml.bak"), infos.get(0));
        assertEquals(leaves(yaml(bundled)), leaves(yaml(read(file))));
    }

    // ---- what the owner has stays ----

    @Test
    void valuesCommentsAndExtraKeysOfTheOwnerStay() throws Exception {
        String bundled = resource("config.yml");
        String old = without(bundled, "item-lore", 0)
                .replace("language: en", "language: ru")
                .replace("scale: 0.4", "scale: 0.9")
                + "\n# my note\ncustom-thing: 7\n";
        Path file = write("config.yml", old);

        update(file, bundled);

        String result = read(file);
        YamlConfiguration yaml = yaml(result);
        assertEquals("ru", yaml.getString("language"));
        assertEquals(0.9, yaml.getDouble("display.scale"));
        assertEquals(7, yaml.getInt("custom-thing"));
        assertTrue(result.contains("# my note"));
        assertTrue(yaml.contains("item-lore"));
        assertTrue(result.indexOf("language: ru") < result.indexOf("display:"), "order unchanged");
    }

    @Test
    void anInlineMapWithFewerKeysIsNotCompleted() throws Exception {
        String bundled = resource("config.yml");
        String old = bundled.replace("offset: { right: 0.0, up: 0.1, out: -0.05 }", "offset: { right: 0.1 }");
        Path file = write("config.yml", old);

        assertTrue(update(file, bundled).isEmpty());

        assertEquals(old, read(file));
    }

    @Test
    void aTrimmedBlockIsCompletedKeyByKey() throws Exception {
        String bundled = resource("languages/en.yml");
        Path file = write("en.yml", bundled.replace("  key: \"<!italic>Key\"\n", ""));

        List<String> added = update(file, bundled);

        assertEquals(List.of("items.key"), added);
        assertEquals("<!italic>Key", yaml(read(file)).getString("items.key"));
    }

    // ---- file shape ----

    @Test
    void windowsLineEndingsStayWindowsLineEndings() throws Exception {
        String bundled = resource("config.yml");
        Path file = write("config.yml", without(bundled, "door-display", 0).replace("\n", "\r\n"));

        update(file, bundled);

        String result = read(file);
        assertFalse(result.replace("\r\n", "").contains("\n"), "no bare line feed");
        assertTrue(result.contains("door-display:"));
    }

    @Test
    void aByteOrderMarkIsKeptAndNothingIsDuplicated() throws Exception {
        String bundled = resource("config.yml");
        Path file = write("config.yml", "﻿" + without(bundled, "door-display", 0));

        update(file, bundled);

        String result = read(file);
        assertTrue(result.startsWith("﻿"));
        assertEquals(1, result.split("language:", -1).length - 1);
        assertEquals(leaves(yaml(bundled)), leaves(yaml(result.substring(1))));
    }

    @Test
    void aFileWithoutAFinalNewlineStaysThatWay() throws Exception {
        String bundled = resource("config.yml");
        Path file = write("config.yml", without(bundled, "door-display", 0).stripTrailing());

        update(file, bundled);

        assertFalse(read(file).endsWith("\n"));
    }

    @Test
    void anOwnerWhoIndentsByFourGetsFourInTheNewEntries() throws Exception {
        String bundled = resource("languages/en.yml");
        String old = "messages:\n    lock-installed:\n        type: chat\n        text: \"x\"\n        sound: none\n";
        Path file = write("en.yml", old);

        update(file, bundled);

        YamlConfiguration result = yaml(read(file));
        assertTrue(result.contains("messages.lock-bound.text"));
        assertTrue(read(file).contains("\n    lock-already-locked:\n"), "same indent as the owner's entries");
        assertEquals("x", result.getString("messages.lock-installed.text"));
    }

    // ---- safety ----

    @Test
    void aBrokenFileIsNeverTouched() throws Exception {
        String broken = "language: en\nitems: [unclosed\n";
        Path file = write("config.yml", broken);

        assertTrue(update(file, resource("config.yml")).isEmpty());

        assertEquals(broken, read(file));
        assertFalse(Files.exists(dir.resolve("config.yml.bak")));
        assertEquals(1, warnings.size());
    }

    @Test
    void aMissingFileIsReportedNotCreated() {
        assertTrue(update(dir.resolve("none.yml"), "a: 1\n").isEmpty());

        assertFalse(Files.exists(dir.resolve("none.yml")));
        assertEquals(1, warnings.size());
    }

    @Test
    void theBackupHoldsTheOldFileAndSecondRunChangesNothing() throws Exception {
        String bundled = resource("config.yml");
        String old = without(bundled, "item-lore", 0);
        Path file = write("config.yml", old);

        update(file, bundled);
        String once = read(file);

        assertEquals(old, read(dir.resolve("config.yml.bak")));
        assertNotEquals(old, once);
        assertTrue(update(file, bundled).isEmpty());
        assertEquals(once, read(file));
        assertEquals(old, read(dir.resolve("config.yml.bak")), "an unchanged file does not touch the backup");
    }

    // ---- the real files: any single entry that is missing comes back ----

    private void everySingleEntryComesBack(String resourceName, int indentOfEntries, String parentKey)
            throws Exception {
        String bundled = resource(resourceName);
        YamlConfiguration reference = yaml(bundled);
        List<String> keys = parentKey == null
                ? new ArrayList<>(reference.getKeys(false))
                : new ArrayList<>(reference.getConfigurationSection(parentKey).getKeys(false));
        for (String key : keys) {
            Path file = write("case.yml", without(bundled, key, indentOfEntries, parentKey == null ? "" : parentKey.substring(parentKey.lastIndexOf('.') + 1) + ":"));
            List<String> added = ConfigUpdater.update(file, bundled, infos::add, warnings::add);
            String path = parentKey == null ? key : parentKey + "." + key;
            assertEquals(List.of(path), added, resourceName + " " + path);
            assertEquals(leaves(reference), leaves(yaml(read(file))), resourceName + " " + path);
        }
    }

    @Test
    void everyTopLevelEntryOfConfigYmlComesBack() throws Exception {
        everySingleEntryComesBack("config.yml", 0, null);
    }

    @Test
    void everyTopLevelEntryOfTheLanguageFilesComesBack() throws Exception {
        everySingleEntryComesBack("languages/en.yml", 0, null);
        everySingleEntryComesBack("languages/ru.yml", 0, null);
    }

    @Test
    void everyMessageOfTheLanguageFilesComesBack() throws Exception {
        everySingleEntryComesBack("languages/en.yml", 2, "messages");
        everySingleEntryComesBack("languages/ru.yml", 2, "messages");
    }

    @Test
    void everyItemBlockOfConfigYmlComesBack() throws Exception {
        everySingleEntryComesBack("config.yml", 4, "items.vanilla");
    }

    @Test
    void theFileThatComesBackIsReadByThePlugin() throws Exception {
        String bundled = resource("config.yml");
        Path file = write("config.yml", without(without(bundled, "trapdoor-display", 0), "item-lore", 0));
        update(file, bundled);

        Settings settings = Settings.parse(yaml(read(file)));

        assertFalse(settings.itemLore());
        assertEquals(bundled.contains("trapdoor-display"), true);
        assertEquals(yaml(bundled).getDouble("trapdoor-display.scale"), settings.trapdoorDisplay().scale());
    }

    // ---- the plugin starts it ----

    @Test
    void thePluginAddsWhatIsMissingWhenItStarts() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            OutlandsLocks plugin = MockBukkit.load(OutlandsLocks.class);
            Path config = plugin.getDataFolder().toPath().resolve("config.yml");
            Path ru = plugin.getDataFolder().toPath().resolve("languages/ru.yml");
            Files.writeString(config, without(resource("config.yml"), "door-display", 0), StandardCharsets.UTF_8);
            Files.writeString(ru, without(resource("languages/ru.yml"), "lore", 0), StandardCharsets.UTF_8);

            server.getPluginManager().disablePlugin(plugin);
            server.getPluginManager().enablePlugin(plugin);

            assertTrue(read(config).contains("door-display:"));
            assertTrue(Files.exists(config.resolveSibling("config.yml.bak")));
            assertTrue(read(ru).contains("lore:"));
        } finally {
            MockBukkit.unmock();
        }
    }
}
