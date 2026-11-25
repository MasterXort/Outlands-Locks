package ru.outlands.locks.config;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import ru.outlands.locks.display.DisplaySettings;
import ru.outlands.locks.item.LockItemType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return cfg;
    }

    private static final String VANILLA = """
            language: ru
            item-provider: vanilla
            items:
              vanilla:
                lock: { material: IRON_INGOT }
                blank-key: { material: IRON_NUGGET }
                key: { material: GOLD_NUGGET, item-model: "pack:key" }
              nexo:
                lock: my_lock
                blank-key: my_blank
                key: my_key
            """;

    @Test
    void parsesVanillaMode() {
        Settings s = Settings.parse(yaml(VANILLA));
        assertEquals("ru", s.language());
        assertEquals(Settings.ProviderKind.VANILLA, s.provider());
        assertEquals(Material.GOLD_NUGGET, s.vanillaItems().get(LockItemType.KEY).material());
    }

    @Test
    void parsesNexoIds() {
        Settings s = Settings.parse(yaml(VANILLA.replace("item-provider: vanilla", "item-provider: NEXO")));
        assertEquals(Settings.ProviderKind.NEXO, s.provider());
        assertEquals("my_blank", s.nexoIds().get(LockItemType.BLANK_KEY));
    }

    @Test
    void languageDefaultsToEnglish() {
        Settings s = Settings.parse(yaml(VANILLA.replace("language: ru\n", "")));
        assertEquals("en", s.language());
    }

    @Test
    void unknownProviderIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(yaml(VANILLA.replace("item-provider: vanilla", "item-provider: mixed"))));
        assertTrue(e.getMessage().contains("item-provider"), e.getMessage());
    }

    @Test
    void brokenVanillaSectionIsRejectedInVanillaMode() {
        assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(yaml(VANILLA.replace("IRON_NUGGET", "NOPE"))));
    }

    @Test
    void brokenVanillaSectionIsIgnoredInNexoMode() {
        String text = VANILLA.replace("item-provider: vanilla", "item-provider: nexo").replace("IRON_NUGGET", "NOPE");
        assertEquals(Settings.ProviderKind.NEXO, Settings.parse(yaml(text)).provider());
    }

    @Test
    void blankNexoIdIsRejectedInNexoMode() {
        String text = VANILLA.replace("item-provider: vanilla", "item-provider: nexo").replace("my_key", "\"\"");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Settings.parse(yaml(text)));
        assertTrue(e.getMessage().contains("items.nexo.key"), e.getMessage());
    }

    // ---- door-display and trapdoor-display ----

    @Test
    void doorsAndTrapdoorsHaveTheirOwnDisplaySettings() {
        Settings s = Settings.parse(yaml(VANILLA + """
                door-display:
                  scale: 0.5
                trapdoor-display:
                  scale: 0.2
                  offset: { right: 0.1 }
                """));

        assertEquals(0.5, s.doorDisplay().scale());
        assertEquals(0.2, s.trapdoorDisplay().scale());
        assertEquals(0.1, s.trapdoorDisplay().right());
        assertEquals(0.0, s.doorDisplay().right());
    }

    @Test
    void missingDoorAndTrapdoorSectionsUseTheirDefaults() {
        Settings s = Settings.parse(yaml(VANILLA));

        assertEquals(DisplaySettings.DOOR_DEFAULT, s.doorDisplay());
        assertEquals(DisplaySettings.TRAPDOOR_DEFAULT, s.trapdoorDisplay());
    }

    @Test
    void aBadTrapdoorScaleIsRejectedWithItsPath() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(yaml(VANILLA + "trapdoor-display:\n  scale: 0\n")));
        assertTrue(e.getMessage().contains("trapdoor-display.scale"), e.getMessage());
    }

    // ---- explosions.allowed-sources (PLAN 4.9, 7) ----

    private static final String SOURCES = "explosions:\n  allowed-sources: [%s]\n";

    @Test
    void explosionSourcesDefaultToTntAndTntMinecart() {
        assertEquals(java.util.Set.of(EntityType.TNT, EntityType.TNT_MINECART),
                Settings.parse(yaml(VANILLA)).explosionSources());
    }

    @Test
    void explosionSourcesAreReadCaseInsensitively() {
        Settings s = Settings.parse(yaml(VANILLA + SOURCES.formatted("creeper, Wither_Skull")));
        assertEquals(java.util.Set.of(EntityType.CREEPER, EntityType.WITHER_SKULL), s.explosionSources());
    }

    @Test
    void emptyExplosionSourcesMeansNothingBreaksLockedChests() {
        assertTrue(Settings.parse(yaml(VANILLA + SOURCES.formatted(""))).explosionSources().isEmpty());
    }

    @Test
    void unknownExplosionSourceIsRejectedWithItsPath() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(yaml(VANILLA + SOURCES.formatted("TNT, NOT_A_MOB"))));
        assertTrue(e.getMessage().contains("explosions.allowed-sources"), e.getMessage());
        assertTrue(e.getMessage().contains("NOT_A_MOB"), e.getMessage());
    }

    @Test
    void unknownEntityTypeIsRejectedAsExplosionSource() {
        assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(yaml(VANILLA + SOURCES.formatted("UNKNOWN"))));
    }

    @Test
    void explosionSourcesMustBeAList() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(yaml(VANILLA + "explosions:\n  allowed-sources: TNT\n")));
        assertTrue(e.getMessage().contains("explosions.allowed-sources"), e.getMessage());
    }

    @Test
    void explosionSourcesAreValidatedInNexoModeToo() {
        String text = VANILLA.replace("item-provider: vanilla", "item-provider: nexo") + SOURCES.formatted("NOPE");
        assertThrows(IllegalArgumentException.class, () -> Settings.parse(yaml(text)));
    }

    @Test
    void shippedConfigParsesInBothModes() throws Exception {
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(Settings.ProviderKind.VANILLA, Settings.parse(cfg).provider());
            cfg.set("item-provider", "nexo");
            assertEquals(Settings.ProviderKind.NEXO, Settings.parse(cfg).provider());
        }
    }
}
