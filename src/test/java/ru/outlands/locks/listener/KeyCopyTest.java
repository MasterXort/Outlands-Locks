package ru.outlands.locks.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PLAN 4.4: sneak + right click with a blank key in the main hand and a key in the off hand copies the key. */
class KeyCopyTest {

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
        player.teleport(world.getBlockAt(0, 70, 0).getLocation());
        player.setSneaking(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack item(LockItemType type, int amount) {
        return plugin.items().create(type, amount).orElseThrow();
    }

    private ItemStack keyFor(String rawId) {
        ItemStack key = item(LockItemType.KEY, 1);
        ItemMeta meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, rawId);
        key.setItemMeta(meta);
        return key;
    }

    private ItemStack keyFor(UUID id) {
        return keyFor(id.toString());
    }

    private UUID lockIdOf(ItemStack key) {
        return UUID.fromString(key.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().lockId(), PersistentDataType.STRING));
    }

    private PlayerInteractEvent click(PlayerMock who, Action action, Block block, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND
                ? who.getInventory().getItemInMainHand() : who.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(who, action, held, block, BlockFace.NORTH, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** A click in the air starts out "cancelled" in Bukkit (no block to use), so what a plugin changes is the item use. */
    private static boolean denied(PlayerInteractEvent event) {
        return event.useItemInHand() == Event.Result.DENY;
    }

    private PlayerInteractEvent clickAir() {
        return click(player, Action.RIGHT_CLICK_AIR, null, EquipmentSlot.HAND);
    }

    private Block chest() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        block.setBlockData(data);
        return block;
    }

    private LockData lock(Block block, boolean keyCut) {
        LockData data = new LockData(UUID.randomUUID(), keyCut);
        List<Block> halves = ChestHalves.of(block);
        List<org.bukkit.block.data.BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        return data;
    }

    private List<ItemStack> keysInInventory() {
        return Arrays.stream(player.getInventory().getContents())
                .filter(s -> s != null && plugin.items().identify(s).orElse(null) == LockItemType.KEY).toList();
    }

    private void hold(ItemStack blank, ItemStack key) {
        player.getInventory().setItemInMainHand(blank);
        player.getInventory().setItemInOffHand(key);
    }

    // ---- the copy ----

    @Test
    void aSingleBlankBecomesACopyOfTheKeyInTheOffHand() {
        UUID id = UUID.randomUUID();
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(id));

        PlayerInteractEvent event = clickAir();

        ItemStack main = player.getInventory().getItemInMainHand();
        assertEquals(LockItemType.KEY, plugin.items().identify(main).orElse(null));
        assertEquals(id, lockIdOf(main));
        assertEquals(1, main.getAmount());
        assertEquals(id, lockIdOf(player.getInventory().getItemInOffHand()));
        assertTrue(denied(event));
    }

    @Test
    void aBlankFromAStackIsUsedAndTheCopyGoesIntoTheInventory() {
        UUID id = UUID.randomUUID();
        hold(item(LockItemType.BLANK_KEY, 3), keyFor(id));

        clickAir();

        ItemStack main = player.getInventory().getItemInMainHand();
        assertEquals(LockItemType.BLANK_KEY, plugin.items().identify(main).orElse(null));
        assertEquals(2, main.getAmount());
        assertEquals(1, keysInInventory().size() - 1, "one new key besides the one in the off hand");
        assertTrue(keysInInventory().stream().allMatch(k -> lockIdOf(k).equals(id)));
    }

    @Test
    void theCopyDropsAtTheFeetWhenTheInventoryIsFull() {
        UUID id = UUID.randomUUID();
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
        }
        // MockBukkit's addItem also fills the off hand, so a signed key keeps the clean copy from merging into it.
        ItemStack signed = keyFor(id);
        ItemMeta signedMeta = signed.getItemMeta();
        signedMeta.customName(Component.text("Bob's chest"));
        signed.setItemMeta(signedMeta);
        hold(item(LockItemType.BLANK_KEY, 2), signed);

        clickAir();

        List<Item> dropped = world.getEntitiesByClass(Item.class).stream().toList();
        assertEquals(1, dropped.size());
        assertEquals(id, lockIdOf(dropped.get(0).getItemStack()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void theCopyIsCleanWithoutTheNameOrLoreOfTheSourceKey() {
        UUID id = UUID.randomUUID();
        ItemStack signed = keyFor(id);
        ItemMeta meta = signed.getItemMeta();
        meta.customName(Component.text("Bob's chest"));
        meta.lore(List.of(Component.text("do not lose")));
        signed.setItemMeta(meta);
        hold(item(LockItemType.BLANK_KEY, 1), signed);

        clickAir();

        ItemStack copy = player.getInventory().getItemInMainHand();
        assertEquals(keyFor(id), copy);
        assertNull(copy.getItemMeta().lore());
        assertEquals(item(LockItemType.KEY, 1).getItemMeta().customName(), copy.getItemMeta().customName());
    }

    @Test
    void twoCleanCopiesOfTheSameKeyStack() {
        UUID id = UUID.randomUUID();
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(id));
        clickAir();
        ItemStack first = player.getInventory().getItemInMainHand().clone();
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));
        clickAir();

        assertTrue(first.isSimilar(player.getInventory().getItemInMainHand()));
    }

    @Test
    void aCopyCanBeCopiedAgain() {
        UUID id = UUID.randomUUID();
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(id));
        clickAir();
        ItemStack copy = player.getInventory().getItemInMainHand().clone();

        hold(item(LockItemType.BLANK_KEY, 1), copy);
        clickAir();

        assertEquals(id, lockIdOf(player.getInventory().getItemInMainHand()));
    }

    @Test
    void theCopyOpensTheLockedChest() {
        Block block = chest();
        LockData data = lock(block, true);
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(data.id()));
        clickAir();

        assertTrue(plugin.keyMatcher().matches(player.getInventory().getItemInMainHand(), data));
    }

    @Test
    void takingTheCopyPlaysTheKeyCopiedSound() {
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(UUID.randomUUID()));

        clickAir();

        assertEquals(1, player.getHeardSounds().size());
    }

    // ---- where it works ----

    @Test
    void itWorksOnABlockToo() {
        UUID id = UUID.randomUUID();
        Block stone = world.getBlockAt(5, 64, 5);
        stone.setType(Material.STONE);
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(id));

        PlayerInteractEvent event = click(player, Action.RIGHT_CLICK_BLOCK, stone, EquipmentSlot.HAND);

        assertEquals(id, lockIdOf(player.getInventory().getItemInMainHand()));
        assertTrue(denied(event));
    }

    @Test
    void onALockedChestTheCopyComesBeforeCuttingAndOpening() {
        Block block = chest();
        LockData data = lock(block, false);
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(data.id()));

        PlayerInteractEvent event = click(player, Action.RIGHT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        assertTrue(denied(event));
        assertEquals(data.id(), lockIdOf(player.getInventory().getItemInMainHand()));
        assertFalse(plugin.storage().read(block).orElseThrow().keyCut(), "the chest is not touched");
        assertNull(player.nextActionBar());
    }

    // ---- where it does not ----

    @Test
    void withoutSneakingNothingHappens() {
        player.setSneaking(false);
        UUID id = UUID.randomUUID();
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(id));

        PlayerInteractEvent event = clickAir();

        assertFalse(denied(event));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
        assertTrue(player.getHeardSounds().isEmpty());
    }

    @Test
    void theKeyInTheMainHandAndTheBlankInTheOffHandDoNotCopy() {
        hold(keyFor(UUID.randomUUID()), item(LockItemType.BLANK_KEY, 1));

        PlayerInteractEvent event = clickAir();

        assertFalse(denied(event));
        assertEquals(LockItemType.KEY, plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInOffHand()).orElse(null));
    }

    @Test
    void aBlankWithoutAKeyInTheOffHandDoesNothing() {
        hold(item(LockItemType.BLANK_KEY, 1), null);

        PlayerInteractEvent event = clickAir();

        assertFalse(denied(event));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
    }

    @Test
    void aKeyWithoutALockIdIsNotCopied() {
        hold(item(LockItemType.BLANK_KEY, 1), item(LockItemType.KEY, 1));

        assertFalse(denied(clickAir()));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
    }

    @Test
    void aKeyWithABrokenLockIdIsNotCopied() {
        hold(item(LockItemType.BLANK_KEY, 1), keyFor("not-a-uuid"));

        assertFalse(denied(clickAir()));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
    }

    @Test
    void ordinaryItemsOfTheKeyAndBlankMaterialsDoNotCopy() {
        hold(new ItemStack(Material.IRON_NUGGET, 1), new ItemStack(Material.GOLD_NUGGET, 1));

        assertFalse(denied(clickAir()));
        assertEquals(Material.IRON_NUGGET, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void anOrdinaryBlankMaterialItemWithARealKeyDoesNotCopy() {
        hold(new ItemStack(Material.IRON_NUGGET, 1), keyFor(UUID.randomUUID()));

        assertFalse(denied(clickAir()));
        assertEquals(Material.IRON_NUGGET, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void theOffHandEventIsIgnored() {
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = click(player, Action.RIGHT_CLICK_AIR, null, EquipmentSlot.OFF_HAND);

        assertFalse(denied(event));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
    }

    @Test
    void aLeftClickDoesNotCopy() {
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = click(player, Action.LEFT_CLICK_AIR, null, EquipmentSlot.HAND);

        assertFalse(denied(event));
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
    }

    @Test
    void aCancelledEventIsSkipped() {
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(UUID.randomUUID()));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.NORTH, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
    }

    @Test
    void thereIsNoCraftingRecipeForTheCopyAnyMore() {
        assertNull(server.getRecipe(new org.bukkit.NamespacedKey(plugin, "key_copy")));
    }

    // ---- the permission to use locks ----

    @Test
    void withoutThePermissionNoCopyIsMadeAndTheClickIsStillRefused() {
        player.addAttachment(plugin, "outlands.locks.use", false);
        UUID id = UUID.randomUUID();
        hold(item(LockItemType.BLANK_KEY, 2), keyFor(id));

        PlayerInteractEvent event = clickAir();

        assertTrue(denied(event));
        ItemStack main = player.getInventory().getItemInMainHand();
        assertEquals(LockItemType.BLANK_KEY, plugin.items().identify(main).orElse(null));
        assertEquals(2, main.getAmount());
        assertEquals(1, keysInInventory().size(), "only the key that was in the off hand");
        assertEquals("You are not allowed to use locks and keys.",
                PlainTextComponentSerializer.plainText().serialize(player.nextActionBar()));
    }

    @Test
    void withoutThePermissionTheCopyClickOnALockedChestIsRefusedAndNotPassedOn() {
        player.addAttachment(plugin, "outlands.locks.use", false);
        Block block = chest();
        LockData data = lock(block, true);
        hold(item(LockItemType.BLANK_KEY, 1), keyFor(data.id()));

        PlayerInteractEvent event = click(player, Action.RIGHT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertEquals(LockItemType.BLANK_KEY,
                plugin.items().identify(player.getInventory().getItemInMainHand()).orElse(null));
        assertEquals(1, keysInInventory().size());
        assertEquals("You are not allowed to use locks and keys.",
                PlainTextComponentSerializer.plainText().serialize(player.nextActionBar()));
        assertNull(player.nextActionBar(), "the chest listener did not add a second message");
    }
}
