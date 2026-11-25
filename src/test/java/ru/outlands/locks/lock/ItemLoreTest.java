package ru.outlands.locks.lock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.config.Settings;
import ru.outlands.locks.item.LockItemType;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The optional lore of locks and keys (config item-lore). Off by default: the plugin writes nothing on items. */
class ItemLoreTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.setSneaking(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void loreOn() throws ReflectiveOperationException {
        Settings old = plugin.settings();
        Settings changed = new Settings(old.language(), old.provider(), old.vanillaItems(), old.nexoIds(),
                old.display(), old.doorDisplay(), old.trapdoorDisplay(), old.explosionSources(), true);
        Field field = OutlandsLocks.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, changed);
    }

    private static List<String> lore(ItemStack stack) {
        List<Component> lore = stack.getItemMeta().lore();
        return lore == null ? null : lore.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    }

    // ---- off by default ----

    @Test
    void theConfigShipsWithTheLoreOff() {
        assertFalse(plugin.settings().itemLore());
    }

    @Test
    void withTheLoreOffNothingIsWrittenOnAnyItem() {
        UUID id = UUID.randomUUID();

        assertNull(lore(plugin.lockItems().key(id).orElseThrow()));
        assertNull(lore(plugin.lockItems().usedLock(new LockData(id, true)).orElseThrow()));
        assertNull(lore(plugin.lockItems().newLock(1).orElseThrow()));
        assertNull(lore(plugin.lockItems().boundLock(plugin.lockItems().newLock(1).orElseThrow(), id)));
    }

    // ---- on ----

    @Test
    void aKeyShowsTheLockId() throws Exception {
        loreOn();
        UUID id = UUID.randomUUID();

        assertEquals(List.of(id.toString()), lore(plugin.lockItems().key(id).orElseThrow()));
    }

    @Test
    void aLockWithACutKeyShowsTheIdAndThatItIsSetUp() throws Exception {
        loreOn();
        UUID id = UUID.randomUUID();

        assertEquals(List.of(id.toString(), "Set up for a key"),
                lore(plugin.lockItems().usedLock(new LockData(id, true)).orElseThrow()));
    }

    @Test
    void aLockWithoutACutKeyShowsTheIdAndThatItIsNotSetUp() throws Exception {
        loreOn();
        UUID id = UUID.randomUUID();

        assertEquals(List.of(id.toString(), "Not set up"),
                lore(plugin.lockItems().usedLock(new LockData(id, false)).orElseThrow()));
    }

    @Test
    void aNewLockHasNoIdYetAndShowsOnlyThatItIsNotSetUp() throws Exception {
        loreOn();

        assertEquals(List.of("Not set up"), lore(plugin.lockItems().newLock(2).orElseThrow()));
    }

    @Test
    void tyingALockWritesTheLoreOfASetUpLock() throws Exception {
        loreOn();
        UUID id = UUID.randomUUID();
        ItemStack loose = plugin.lockItems().newLock(1).orElseThrow();

        assertEquals(List.of(id.toString(), "Set up for a key"), lore(plugin.lockItems().boundLock(loose, id)));
    }

    @Test
    void aBlankKeyIsNeverLabelled() throws Exception {
        loreOn();

        assertNull(lore(plugin.items().create(LockItemType.BLANK_KEY, 1).orElseThrow()));
    }

    @Test
    void theKeyCutAtAChestCarriesTheLore() throws Exception {
        loreOn();
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        block.setBlockData(data);
        LockData lock = new LockData(UUID.randomUUID(), false);
        plugin.storage().write(List.of(block), lock);
        player.getInventory().setItemInMainHand(plugin.items().create(LockItemType.BLANK_KEY, 1).orElseThrow());

        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.NORTH, EquipmentSlot.HAND));

        ItemStack key = player.getInventory().all(Material.GOLD_NUGGET).values().iterator().next();
        assertEquals(List.of(lock.id().toString()), lore(key));
    }

    @Test
    void aCopyOfAKeyCarriesTheLoreOfAKey() throws Exception {
        loreOn();
        UUID id = UUID.randomUUID();
        player.getInventory().setItemInMainHand(plugin.items().create(LockItemType.BLANK_KEY, 1).orElseThrow());
        player.getInventory().setItemInOffHand(plugin.lockItems().key(id).orElseThrow());

        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.NORTH, EquipmentSlot.HAND));

        assertEquals(List.of(id.toString()), lore(player.getInventory().getItemInMainHand()));
    }
}
