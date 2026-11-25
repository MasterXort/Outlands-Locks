package ru.outlands.locks.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockRemoveTest {

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

    /** Locks the chest the way the install listener does: PDC on every half plus one display. */
    private LockData lock(Block block, boolean keyCut) {
        LockData data = new LockData(UUID.randomUUID(), keyCut);
        List<Block> halves = ChestHalves.of(block);
        List<BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        // MockBukkit resets the chest shape on TileState.update; Paper does not.
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        plugin.displays().spawn(halves, data).orElseThrow();
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

    private PlayerInteractEvent leftClick(Block block, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, held, block,
                BlockFace.NORTH, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent leftClick(Block block) {
        return leftClick(block, EquipmentSlot.HAND);
    }

    private BlockBreakEvent breakBlock(Block block) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    private List<ItemStack> locksInInventory() {
        return Arrays.stream(player.getInventory().getContents())
                .filter(s -> s != null && plugin.items().identify(s).orElse(null) == LockItemType.LOCK)
                .toList();
    }

    private LockData dataOf(ItemStack lock) {
        return LockData.read(lock.getItemMeta().getPersistentDataContainer(), plugin.keys()).orElseThrow();
    }

    private void fillInventory() {
        // MockBukkit's addItem also uses the slots past the 36 storage slots (Paper does not), so fill them all.
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
        }
    }

    // ---- removal with a key in the main hand (PLAN 4.5) ----

    @Test
    void keyInTheMainHandRemovesTheLockAndReturnsItWithIdAndKeyCut() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        PlayerInteractEvent event = leftClick(block);

        assertTrue(plugin.storage().read(block).isEmpty());
        List<ItemStack> locks = locksInInventory();
        assertEquals(1, locks.size());
        assertEquals(data, dataOf(locks.get(0)));
        assertTrue(event.isCancelled());
        assertEquals("You removed the lock.", plain(player.nextActionBar()));
    }

    @Test
    void aLockWhoseKeyWasNeverCutComesBackWithKeyCutFalse() {
        Block block = chest();
        LockData data = lock(block, false);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        leftClick(block);

        assertEquals(data, dataOf(locksInInventory().get(0)));
    }

    @Test
    void theKeyStaysInTheHand() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        leftClick(block);

        assertEquals(data.id(), UUID.fromString(player.getInventory().getItemInMainHand().getItemMeta()
                .getPersistentDataContainer().get(plugin.keys().lockId(), PersistentDataType.STRING)));
    }

    @Test
    void theDisplayIsRemoved() {
        Block block = chest();
        LockData data = lock(block, true);
        assertEquals(1, displays().size());
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        leftClick(block);

        assertTrue(displays().isEmpty());
    }

    @Test
    void doubleChestIsClearedOnBothHalvesWhicheverOneIsClicked() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        LockData data = lock(a, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        leftClick(b);

        assertTrue(plugin.storage().read(a).isEmpty());
        assertTrue(plugin.storage().read(b).isEmpty());
        assertTrue(displays().isEmpty());
        assertEquals(1, locksInInventory().size());
    }

    @Test
    void onlyTheDisplayOfThisLockIsRemoved() {
        Block mine = chest(0, 0, Chest.Type.SINGLE);
        Block other = chest(2, 0, Chest.Type.SINGLE);
        LockData data = lock(mine, true);
        LockData otherData = lock(other, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        leftClick(mine);

        List<ItemDisplay> left = displays();
        assertEquals(1, left.size());
        assertEquals(otherData.id().toString(),
                left.get(0).getPersistentDataContainer().get(plugin.keys().lockId(), PersistentDataType.STRING));
        assertEquals(Optional.of(otherData), plugin.storage().read(other));
    }

    @Test
    void aSecondKeyOfTheSameLockAlsoRemoves() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        ItemStack copy = keyFor(data.id());
        player.getInventory().setItemInMainHand(copy);

        leftClick(block);

        assertTrue(plugin.storage().read(block).isEmpty());
    }

    // ---- refusals ----

    @Test
    void keyInTheOffHandDoesNotRemoveAndSaysToUseTheMainHand() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
        assertTrue(locksInInventory().isEmpty());
        assertEquals("Hold the key in your main hand to remove the lock.", plain(player.nextActionBar()));
    }

    @Test
    void foreignKeyInTheMainHandDoesNotRemoveAndSaysItDoesNotFit() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
        assertEquals("This key does not fit the lock.", plain(player.nextActionBar()));
        assertFalse(event.isCancelled());
    }

    @Test
    void foreignKeyInTheMainHandAndTheRightKeyInTheOffHandSaysToUseTheMainHand() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals("Hold the key in your main hand to remove the lock.", plain(player.nextActionBar()));
    }

    @Test
    void noKeyDoesNothingAndStaysSilent() {
        Block block = chest();
        LockData data = lock(block, true);

        PlayerInteractEvent event = leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertNull(player.nextActionBar());
        assertFalse(event.isCancelled());
    }

    @Test
    void ordinaryItemOfTheKeyMaterialDoesNotRemove() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_NUGGET));

        leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertNull(player.nextActionBar());
    }

    @Test
    void withoutSneakingNothingHappens() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setSneaking(false);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        PlayerInteractEvent event = leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void bypassPermissionDoesNotRemoveWithoutAKey() {
        Block block = chest();
        LockData data = lock(block, true);
        player.addAttachment(plugin, "outlands.locks.bypass", true);

        leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    @Test
    void offHandEventIsIgnored() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        PlayerInteractEvent event = leftClick(block, EquipmentSlot.OFF_HAND);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertFalse(event.isCancelled());
    }

    @Test
    void rightClickDoesNotRemove() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.NORTH, EquipmentSlot.HAND);

        server.getPluginManager().callEvent(event);

        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    @Test
    void removalIsSkippedWhenAnotherPluginAlreadyCancelled() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.NORTH, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
        assertNull(player.nextActionBar());
    }

    @Test
    void unlockedChestIsLeftAlone() {
        Block block = chest();
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = leftClick(block);

        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
        assertTrue(locksInInventory().isEmpty());
    }

    @Test
    void enderChestIsLeftAlone() {
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(Material.ENDER_CHEST);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        assertFalse(leftClick(block).isCancelled());
        assertNull(player.nextActionBar());
    }

    // ---- full inventory ----

    @Test
    void lockDropsAtTheFeetWhenTheInventoryIsFull() {
        Block block = chest();
        LockData data = lock(block, true);
        fillInventory();
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        leftClick(block);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(locksInInventory().isEmpty());
        List<Item> dropped = world.getEntitiesByClass(Item.class).stream().toList();
        assertEquals(1, dropped.size());
        assertEquals(data, dataOf(dropped.get(0).getItemStack()));
        assertEquals("You removed the lock. Inventory full — it dropped at your feet.",
                plain(player.nextActionBar()));
    }

    // ---- a lock whose key was never cut comes off without a key ----

    @Test
    void aLockWhoseKeyWasNeverCutComesOffWithAnEmptyHand() {
        Block block = chest();
        LockData data = lock(block, false);

        PlayerInteractEvent event = leftClick(block);

        assertTrue(event.isCancelled());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
        assertEquals(data, dataOf(locksInInventory().get(0)));
        assertEquals("You removed the lock.", plain(player.nextActionBar()));
    }

    @Test
    void anyItemInTheHandWillDoForAnUncutLock() {
        Block block = chest();
        lock(block, false);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));

        leftClick(block);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals(Material.STICK, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void anotherPlayerMayTakeOffAnUncutLockToo() {
        Block block = chest();
        lock(block, false);
        PlayerMock stranger = server.addPlayer("Alex");
        stranger.setSneaking(true);
        PlayerInteractEvent event = new PlayerInteractEvent(stranger, Action.LEFT_CLICK_BLOCK, null, block,
                BlockFace.NORTH, EquipmentSlot.HAND);

        server.getPluginManager().callEvent(event);

        assertTrue(plugin.storage().read(block).isEmpty());
    }

    @Test
    void anUncutLockOfADoubleChestComesOffBothHalves() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        lock(a, false);

        leftClick(b);

        assertTrue(plugin.storage().read(a).isEmpty());
        assertTrue(plugin.storage().read(b).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void anUncutLockStaysWithoutSneaking() {
        Block block = chest();
        LockData data = lock(block, false);
        player.setSneaking(false);

        PlayerInteractEvent event = leftClick(block);

        assertFalse(event.isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    @Test
    void aCutLockStillNeedsTheKeyInTheMainHand() {
        Block block = chest();
        LockData data = lock(block, true);

        leftClick(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    @Test
    void creativeBreakOfAnUncutLockTakesTheLockOffAndKeepsTheBlock() {
        Block block = chest();
        LockData data = lock(block, false);
        player.setGameMode(GameMode.CREATIVE);

        BlockBreakEvent event = breakBlock(block);

        assertTrue(event.isCancelled());
        assertEquals(Material.CHEST, block.getType());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals(data, dataOf(locksInInventory().get(0)));
    }

    // ---- creative: BlockBreakEvent (PLAN 4.5) ----

    @Test
    void creativeBreakWithTheKeyRemovesTheLockAndKeepsTheBlock() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        BlockBreakEvent event = breakBlock(block);

        assertTrue(event.isCancelled());
        assertEquals(Material.CHEST, block.getType());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
        List<ItemStack> locks = locksInInventory();
        assertEquals(1, locks.size());
        assertEquals(data, dataOf(locks.get(0)));
        assertEquals("You removed the lock.", plain(player.nextActionBar()));
    }

    @Test
    void creativeBreakOfADoubleChestClearsBothHalves() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        LockData data = lock(a, true);
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        assertTrue(breakBlock(b).isCancelled());

        assertTrue(plugin.storage().read(a).isEmpty());
        assertTrue(plugin.storage().read(b).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void creativeBreakDropsTheLockWhenTheInventoryIsFull() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.CREATIVE);
        fillInventory();
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        breakBlock(block);

        assertEquals(1, world.getEntitiesByClass(Item.class).size());
        assertEquals("You removed the lock. Inventory full — it dropped at your feet.",
                plain(player.nextActionBar()));
    }

    @Test
    void breakWithoutSneakingIsNotTouched() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setSneaking(false);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        breakBlock(block);

        // Stage 6 refuses the survival break itself; the lock must stay and no lock item may come back.
        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertTrue(locksInInventory().isEmpty());
    }

    @Test
    void breakWithAForeignKeyIsNotTouched() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        breakBlock(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertTrue(locksInInventory().isEmpty());
        assertEquals("A locked object cannot be broken.", plain(player.nextActionBar()));
    }

    @Test
    void breakWithTheKeyOnlyInTheOffHandIsNotTouched() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        breakBlock(block);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertTrue(locksInInventory().isEmpty());
    }

    @Test
    void breakOfAnUnlockedChestIsNotTouched() {
        Block block = chest();
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        assertFalse(breakBlock(block).isCancelled());
    }

    @Test
    void breakIsSkippedWhenAnotherPluginAlreadyCancelled() {
        Block block = chest();
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
    }

    // ---- a removed lock goes back on another chest (PLAN 4.1 + 10) ----

    @Test
    void aRemovedLockOnAnotherChestKeepsTheOldKeysAndAllowsNoNewKey() {
        Block first = chest(0, 0, Chest.Type.SINGLE);
        LockData data = lock(first, true);
        ItemStack key = keyFor(data.id());
        player.getInventory().setItemInMainHand(key);
        leftClick(first);
        player.nextActionBar();

        Block second = chest(4, 0, Chest.Type.SINGLE);
        player.getInventory().setItemInMainHand(locksInInventory().get(0));
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), second, BlockFace.NORTH, EquipmentSlot.HAND));

        assertEquals(Optional.of(data), plugin.storage().read(second));
        assertEquals(1, displays().size());

        player.getInventory().setItemInMainHand(key);
        PlayerInteractEvent open = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, key, second,
                BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(open);
        assertFalse(open.isCancelled());

        player.nextActionBar();
        player.getInventory().setItemInMainHand(item(LockItemType.BLANK_KEY, 1));
        PlayerInteractEvent cut = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), second, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(cut);
        assertEquals("A key for this lock has already been cut.", plain(player.nextActionBar()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }
}
