package ru.outlands.locks.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaItemProviderTest {

    private static final NamespacedKey MARKER = new NamespacedKey("outlands-locks", "item_type");

    private VanillaItemProvider provider;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Map<LockItemType, VanillaItemSpec> specs = new EnumMap<>(LockItemType.class);
        specs.put(LockItemType.LOCK, new VanillaItemSpec(Material.IRON_INGOT, null, null));
        specs.put(LockItemType.BLANK_KEY, new VanillaItemSpec(Material.IRON_NUGGET, null, null));
        specs.put(LockItemType.KEY, new VanillaItemSpec(Material.GOLD_NUGGET, NamespacedKey.fromString("pack:key"), 7));
        provider = new VanillaItemProvider(specs, MARKER,
                type -> MiniMessage.miniMessage().deserialize("<!italic>name-" + type.commandName()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createdItemUsesConfiguredMaterialAndLocalizedName() {
        ItemStack stack = provider.create(LockItemType.LOCK, 2).orElseThrow();
        assertEquals(Material.IRON_INGOT, stack.getType());
        assertEquals(2, stack.getAmount());
        Component name = stack.getItemMeta().customName();
        assertEquals("name-lock", PlainTextComponentSerializer.plainText().serialize(name));
    }

    @Test
    void createdItemHasNoLore() {
        ItemMeta meta = provider.create(LockItemType.KEY, 1).orElseThrow().getItemMeta();
        assertFalse(meta.hasLore());
    }

    @Test
    void identifiesEachCreatedType() {
        for (LockItemType type : LockItemType.values()) {
            ItemStack stack = provider.create(type, 1).orElseThrow();
            assertEquals(Optional.of(type), provider.identify(stack));
        }
    }

    @Test
    void plainItemOfSameMaterialIsNotRecognised() {
        assertEquals(Optional.empty(), provider.identify(new ItemStack(Material.GOLD_NUGGET)));
    }

    @Test
    void airAndNullAreNotRecognised() {
        assertEquals(Optional.empty(), provider.identify(null));
        assertEquals(Optional.empty(), provider.identify(new ItemStack(Material.AIR)));
    }

    @Test
    void itemModelAndCustomModelDataAreApplied() {
        // MockBukkit drops item model and custom model data on setItemMeta, so the meta is checked before the round trip
        ItemMeta meta = new ItemStack(Material.GOLD_NUGGET).getItemMeta();
        VanillaItemProvider.applyModel(meta, new VanillaItemSpec(Material.GOLD_NUGGET, NamespacedKey.fromString("pack:key"), 7));
        assertEquals(NamespacedKey.fromString("pack:key"), meta.getItemModel());
        assertEquals(java.util.List.of(7.0f), meta.getCustomModelDataComponent().getFloats());
    }

    @Test
    void identicalItemsStack() {
        ItemStack a = provider.create(LockItemType.BLANK_KEY, 1).orElseThrow();
        ItemStack b = provider.create(LockItemType.BLANK_KEY, 1).orElseThrow();
        assertTrue(a.isSimilar(b));
    }

    @Test
    void specParsesMaterialModelAndCmd() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("material", "iron_nugget");
        cfg.set("item-model", "pack:lock");
        cfg.set("custom-model-data", 3);
        VanillaItemSpec spec = VanillaItemSpec.parse(cfg, "items.vanilla.lock");
        assertEquals(Material.IRON_NUGGET, spec.material());
        assertEquals(NamespacedKey.fromString("pack:lock"), spec.itemModel());
        assertEquals(3, spec.customModelData());
    }

    @Test
    void specOmitsOptionalFieldsWhenEmpty() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("material", "IRON_INGOT");
        cfg.set("item-model", "");
        VanillaItemSpec spec = VanillaItemSpec.parse(cfg, "items.vanilla.lock");
        assertEquals(null, spec.itemModel());
        assertEquals(null, spec.customModelData());
    }

    @Test
    void specRejectsUnknownMaterialWithPath() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("material", "NOT_A_MATERIAL");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> VanillaItemSpec.parse(cfg, "items.vanilla.lock"));
        assertTrue(e.getMessage().contains("items.vanilla.lock.material"), e.getMessage());
    }

    @Test
    void specRejectsAirAndNonItemMaterials() {
        for (String bad : new String[]{"AIR", "WATER"}) {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("material", bad);
            assertThrows(IllegalArgumentException.class, () -> VanillaItemSpec.parse(cfg, "p"), bad);
        }
    }
}
