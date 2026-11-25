package ru.outlands.locks.display;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import ru.outlands.locks.config.Settings;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisplaySettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return cfg;
    }

    private static DisplaySettings parse(String text) {
        YamlConfiguration cfg = yaml(text);
        return DisplaySettings.parse(cfg.getConfigurationSection("display"), "display");
    }

    @Test
    void missingSectionUsesDefaults() {
        assertEquals(DisplaySettings.DEFAULT, parse("other: 1"));
    }

    @Test
    void readsAllValues() {
        DisplaySettings s = parse("""
                display:
                  offset: { right: 0.1, up: 0.2, out: -0.03 }
                  scale: 0.5
                  rotation: { yaw: 5, pitch: 6, roll: 7 }
                """);
        assertEquals(new DisplaySettings(0.1, 0.2, -0.03, 0.5, 5, 6, 7), s);
    }

    @Test
    void missingKeysFallBackIndividually() {
        DisplaySettings s = parse("""
                display:
                  scale: 0.9
                """);
        assertEquals(new DisplaySettings(DisplaySettings.DEFAULT.right(), DisplaySettings.DEFAULT.up(),
                DisplaySettings.DEFAULT.out(), 0.9, 0, 0, 0), s);
    }

    @Test
    void nonNumericValueIsAnError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("""
                display:
                  offset: { up: high }
                """));
        assertEquals("display.offset.up: not a number", e.getMessage());
    }

    @Test
    void nonPositiveScaleIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> parse("display:\n  scale: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("display:\n  scale: -1\n"));
    }

    @Test
    void shippedConfigMatchesTheDefaults() throws Exception {
        YamlConfiguration shipped = new YamlConfiguration();
        try (InputStream in = Settings.class.getResourceAsStream("/config.yml")) {
            shipped.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertEquals(DisplaySettings.DEFAULT,
                DisplaySettings.parse(shipped.getConfigurationSection("display"), "display"));
        assertEquals(true, shipped.isConfigurationSection("display"));
    }
}
