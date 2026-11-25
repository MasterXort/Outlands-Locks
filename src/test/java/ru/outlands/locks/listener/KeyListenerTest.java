package ru.outlands.locks.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyListenerTest {

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
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block chest(int x, int z, Chest.Type type) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        data.setType(type);
        block.setBlockData(data);
        return block;
    }

    private Block chest() {
        return chest(0, 0, Chest.Type.SINGLE);
    }

    private LockData lock(Block block, boolean keyCut) {
        LockData data = new LockData(UUID.randomUUID(), keyCut);
        List<Block> halves = ChestHalves.of(block);
        List<org.bukkit.block.data.BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        // MockBukkit resets the chest shape on TileState.update; Paper does not.
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        return data;
    }

    private ItemStack item(LockItemType type, int amount) {
        return plugin.items().create(type, amount).orElseThrow();
    }

    private ItemStack keyFor(UUID id) {
        ItemStack key = item(LockItemType.KEY, 1);
        ItemMeta meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, id.toString());
        key.setItemMeta(meta);
        return key;
    }

    private PlayerInteractEvent click(PlayerMock who, Block block, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND
                ? who.getInventory().getItemInMainHand() : who.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(who, Action.RIGHT_CLICK_BLOCK, held, block,
                BlockFace.NORTH, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent click(Block block) {
        return click(player, block, EquipmentSlot.HAND);
    }

    private boolean chestOpen() {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getType() == InventoryType.CHEST;
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private List<ItemStack> keysInInventory(PlayerMock who) {
        return java.util.Arrays.stream(who.getInventory().getContents())
                .filter(s -> s != null && plugin.items().identify(s).orElse(null) == LockItemType.KEY)
                .toList();
    }

    private UUID idOf(ItemStack key) {
        return UUID.fromString(key.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().lockId(), PersistentDataType.STRING));
    }

    // ---- cutting (PLAN 4.2) ----

    @Test
    void firstCutGivesAKeyWithTheLockIdAndTakesOneBlank() {
        player.setSneaking(true);
        Block block = chest();
        LockData data = lock(block, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 3));

        PlayerInteractEvent event = click(block);

        List<ItemStack> keys = keysInInventory(player);
        assertEquals(1, keys.size());
        assertEquals(data.id(), idOf(keys.get(0)));
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(event.isCancelled());
        assertEquals("You cut a key.", plain(player.nextActionBar()));
    }

    @Test
    void cutMarksTheLockAsCut() {
        player.setSneaking(true);
        Block block = chest();
        LockData data = lock(block, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        click(block);

        assertEquals(Optional.of(new LockData(data.id(), true)), plugin.storage().read(block));
    }

    @Test
    void cutMarksBothHalvesOfADoubleChest() {
        player.setSneaking(true);
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        LockData data = lock(a, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        click(b);

        assertEquals(Optional.of(new LockData(data.id(), true)), plugin.storage().read(a));
        assertEquals(Optional.of(new LockData(data.id(), true)), plugin.storage().read(b));
        assertEquals(1, keysInInventory(player).size());
    }

    @Test
    void lastBlankIsConsumed() {
        player.setSneaking(true);
        Block block = chest();
        lock(block, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        click(block);

        assertEquals(0, player.getInventory().all(Material.IRON_NUGGET).size());
        assertEquals(1, keysInInventory(player).size());
    }

    @Test
    void secondCutIsRefusedAndKeepsTheBlank() {
        player.setSneaking(true);
        Block block = chest();
        LockData data = lock(block, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 2));
        click(block);
        player.nextActionBar();

        PlayerInteractEvent event = click(block);

        assertEquals("A key for this lock has already been cut.", plain(player.nextActionBar()));
        assertEquals(1, keysInInventory(player).size());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(event.isCancelled());
        assertEquals(Optional.of(new LockData(data.id(), true)), plugin.storage().read(block));
    }

    @Test
    void anotherPlayerMayCutTheFirstKey() {
        Block block = chest();
        LockData data = lock(block, false);
        PlayerMock stranger = server.addPlayer("Alex");
        stranger.setSneaking(true);
        stranger.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        click(stranger, block, EquipmentSlot.HAND);

        List<ItemStack> keys = keysInInventory(stranger);
        assertEquals(1, keys.size());
        assertEquals(data.id(), idOf(keys.get(0)));
        assertTrue(plugin.storage().read(block).orElseThrow().keyCut());
    }

    @Test
    void keyDropsAtTheFeetWhenTheInventoryIsFull() {
        player.setSneaking(true);
        Block block = chest();
        lock(block, false);
        // MockBukkit's addItem also uses the slots past the 36 storage slots (Paper does not), so fill them all.
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
        }
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 2));

        click(block);

        assertEquals(0, keysInInventory(player).size());
        List<Item> dropped = world.getEntitiesByClass(Item.class).stream().toList();
        assertEquals(1, dropped.size());
        assertEquals(LockItemType.KEY, plugin.items().identify(dropped.get(0).getItemStack()).orElse(null));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void blankKeyOnAnUnlockedChestDoesNothing() {
        Block block = chest();
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        PlayerInteractEvent event = click(block);

        assertFalse(event.isCancelled());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(keysInInventory(player).isEmpty());
    }

    @Test
    void ordinaryItemOfTheBlankMaterialDoesNotCut() {
        player.setSneaking(true);
        Block block = chest();
        lock(block, false);
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_NUGGET, 1));

        click(block);

        assertFalse(plugin.storage().read(block).orElseThrow().keyCut());
        assertTrue(keysInInventory(player).isEmpty());
    }

    @Test
    void blankInTheOffHandDoesNotCut() {
        player.setSneaking(true);
        Block block = chest();
        lock(block, false);
        player.getInventory().setItemInOffHand(item(LockItemType.BLANK_KEY, 1));

        click(player, block, EquipmentSlot.OFF_HAND);

        assertFalse(plugin.storage().read(block).orElseThrow().keyCut());
    }

    @Test
    void cutIsSkippedWhenAnotherPluginAlreadyCancelled() {
        player.setSneaking(true);
        Block block = chest();
        lock(block, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.NORTH, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertFalse(plugin.storage().read(block).orElseThrow().keyCut());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    // ---- opening (PLAN 4.3) ----

    @Test
    void keyInTheMainHandOpens() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        PlayerInteractEvent event = click(block);

        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void keyInTheOffHandOpens() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        PlayerInteractEvent event = click(block);

        assertFalse(event.isCancelled());
    }

    @Test
    void foreignKeyIsRefused() {
        Block block = chest();
        lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = click(block);

        assertTrue(event.isCancelled());
        assertEquals("Locked.", plain(player.nextActionBar()));
    }

    @Test
    void noKeyIsRefused() {
        Block block = chest();
        lock(block, true);

        PlayerInteractEvent event = click(block);

        assertTrue(event.isCancelled());
        assertEquals("Locked.", plain(player.nextActionBar()));
    }

    @Test
    void ordinaryItemOfTheKeyMaterialIsRefused() {
        Block block = chest();
        lock(block, true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_NUGGET));

        assertTrue(click(block).isCancelled());
    }

    @Test
    void keyWithoutLockIdIsRefused() {
        Block block = chest();
        lock(block, true);
        player.getInventory().setItemInMainHand(item(LockItemType.KEY, 1));

        assertTrue(click(block).isCancelled());
    }

    @Test
    void keyWithBrokenLockIdIsRefused() {
        Block block = chest();
        lock(block, true);
        ItemStack key = item(LockItemType.KEY, 1);
        ItemMeta meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, "not-a-uuid");
        key.setItemMeta(meta);
        player.getInventory().setItemInMainHand(key);

        assertTrue(click(block).isCancelled());
    }

    @Test
    void bypassPermissionOpensWithoutAKey() {
        Block block = chest();
        lock(block, true);
        player.addAttachment(plugin, "outlands.locks.bypass", true);

        PlayerInteractEvent event = click(block);

        assertFalse(event.isCancelled());
    }

    @Test
    void withoutBypassPermissionOrdinaryRulesApply() {
        Block block = chest();
        lock(block, true);
        player.addAttachment(plugin, "outlands.locks.bypass", false);

        assertTrue(click(block).isCancelled());
    }

    @Test
    void doubleChestOpensFromEitherHalfWithTheKey() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        LockData data = lock(a, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        assertFalse(click(a).isCancelled());
        assertFalse(click(b).isCancelled());
    }

    @Test
    void doubleChestIsRefusedFromEitherHalfWithoutTheKey() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        lock(a, true);

        assertTrue(click(a).isCancelled());
        assertTrue(click(b).isCancelled());
    }

    @Test
    void dataOnTheClickedHalfIsEnough() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        chest(1, 0, Chest.Type.RIGHT);
        LockData data = new LockData(UUID.randomUUID(), true);
        plugin.storage().write(List.of(a), data);

        assertTrue(click(a).isCancelled());
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        assertFalse(click(a).isCancelled());
    }

    @Test
    void unlockedChestIsLeftAlone() {
        Block block = chest();

        PlayerInteractEvent event = click(block);

        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void enderChestIsLeftAlone() {
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(Material.ENDER_CHEST);

        assertFalse(click(block).isCancelled());
    }

    @Test
    void offHandEventIsIgnoredOnALockedChest() {
        Block block = chest();
        lock(block, true);

        PlayerInteractEvent event = click(player, block, EquipmentSlot.OFF_HAND);

        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void refusalIsSkippedWhenAnotherPluginAlreadyCancelled() {
        Block block = chest();
        lock(block, true);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.NORTH, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertNull(player.nextActionBar());
    }

    // ---- priority (PLAN 4.6) ----

    @Test
    void blankInTheMainHandAndTheKeyInTheOffHandOpensWhenNotSneaking() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        PlayerInteractEvent event = click(block);

        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void aBlankKeyWithoutSneakingDoesNotCut() {
        Block block = chest();
        lock(block, false);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 2));

        PlayerInteractEvent event = click(block);

        assertTrue(event.isCancelled());
        assertEquals("Locked.", plain(player.nextActionBar()));
        assertFalse(plugin.storage().read(block).orElseThrow().keyCut());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(keysInInventory(player).isEmpty());
    }

    @Test
    void aBlankKeyWithoutSneakingOnACutLockIsJustAnItemInTheHand() {
        Block block = chest();
        lock(block, true);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        PlayerInteractEvent event = click(block);

        assertTrue(event.isCancelled());
        assertEquals("Locked.", plain(player.nextActionBar()));
    }

    @Test
    void sneakingWithABlankAndBypassOpensACutLockByHand() {
        player.setSneaking(true);
        player.addAttachment(plugin, "outlands.locks.bypass", true);
        Block block = chest();
        lock(block, true);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        PlayerInteractEvent event = click(block);

        assertTrue(event.isCancelled());
        assertTrue(chestOpen());
    }

    @Test
    void blankInTheMainHandOnACutLockWithoutAKeyIsRefusedWithKeyAlreadyCut() {
        player.setSneaking(true);
        Block block = chest();
        lock(block, true);
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));

        PlayerInteractEvent event = click(block);

        assertTrue(event.isCancelled());
        assertEquals("A key for this lock has already been cut.", plain(player.nextActionBar()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void lockInTheMainHandWithoutSneakStillOpensWithTheOffHandKey() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(item(LockItemType.LOCK, 1));
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        assertFalse(click(block).isCancelled());
    }
}
