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
import org.bukkit.event.block.BlockBreakEvent;
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

/** PLAN 4.9: breaking a locked chest by hand. */
class LockBreakTest {

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

    private Block chest(int x, int z, Material material, Chest.Type type) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(material);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        data.setType(type);
        block.setBlockData(data);
        return block;
    }

    private Block chest(int x, int z, Chest.Type type) {
        return chest(x, z, Material.CHEST, type);
    }

    private Block chest() {
        return chest(0, 0, Chest.Type.SINGLE);
    }

    /** Locks the chest the way the install listener does. MockBukkit resets the chest shape on update, Paper does not. */
    private LockData lock(Block block, boolean keyCut) {
        LockData data = new LockData(UUID.randomUUID(), keyCut);
        List<Block> halves = ChestHalves.of(block);
        List<BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        plugin.displays().spawn(halves, data).orElseThrow();
        return data;
    }

    private ItemStack keyFor(UUID id) {
        ItemStack key = plugin.items().create(LockItemType.KEY, 1).orElseThrow();
        ItemMeta meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, id.toString());
        key.setItemMeta(meta);
        return key;
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

    private List<Item> droppedItems() {
        return world.getEntitiesByClass(Item.class).stream().toList();
    }

    private LockData dataOf(ItemStack lock) {
        return LockData.read(lock.getItemMeta().getPersistentDataContainer(), plugin.keys()).orElseThrow();
    }

    // ---- survival and adventure: cancelled ----

    @Test
    void survivalCannotBreakALockedChest() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.SURVIVAL);

        BlockBreakEvent event = breakBlock(block);

        assertTrue(event.isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
        assertTrue(droppedItems().isEmpty());
        assertEquals("A locked object cannot be broken.", plain(player.nextActionBar()));
    }

    @Test
    void adventureCannotBreakALockedChest() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.ADVENTURE);

        assertTrue(breakBlock(block).isCancelled());

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals("A locked object cannot be broken.", plain(player.nextActionBar()));
    }

    @Test
    void survivalCannotBreakEitherHalfOfALockedDoubleChest() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        lock(a, true);

        assertTrue(breakBlock(a).isCancelled());
        assertTrue(breakBlock(b).isCancelled());
    }

    @Test
    void aTrappedChestIsProtectedToo() {
        Block block = chest(10, 10, Material.TRAPPED_CHEST, Chest.Type.SINGLE);
        lock(block, false);

        assertTrue(breakBlock(block).isCancelled());
    }

    @Test
    void aCopperChestStillCarryingAnOldLockIsNotProtected() {
        Block block = chest(10, 10, Material.COPPER_CHEST, Chest.Type.SINGLE);
        lock(block, false);

        assertFalse(breakBlock(block).isCancelled());
    }

    // ---- creative: breaks, lock drops ----

    @Test
    void creativeBreaksTheChestAndTheLockDropsWithIdAndKeyCut() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.CREATIVE);

        BlockBreakEvent event = breakBlock(block);

        assertFalse(event.isCancelled());
        assertTrue(displays().isEmpty());
        assertTrue(plugin.storage().read(block).isEmpty());
        List<Item> dropped = droppedItems();
        assertEquals(1, dropped.size());
        assertEquals(LockItemType.LOCK, plugin.items().identify(dropped.get(0).getItemStack()).orElseThrow());
        assertEquals(data, dataOf(dropped.get(0).getItemStack()));
        assertNull(player.nextActionBar());
    }

    @Test
    void aLockWhoseKeyWasNeverCutDropsWithKeyCutFalse() {
        Block block = chest();
        LockData data = lock(block, false);
        player.setGameMode(GameMode.CREATIVE);

        breakBlock(block);

        assertEquals(data, dataOf(droppedItems().get(0).getItemStack()));
    }

    @Test
    void creativeBreakOfOneHalfClearsTheOtherHalfAndTheDisplay() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        LockData data = lock(a, true);
        player.setGameMode(GameMode.CREATIVE);

        assertFalse(breakBlock(b).isCancelled());

        assertTrue(plugin.storage().read(a).isEmpty());
        assertTrue(plugin.storage().read(b).isEmpty());
        assertTrue(displays().isEmpty());
        assertEquals(1, droppedItems().size());
        assertEquals(data, dataOf(droppedItems().get(0).getItemStack()));
    }

    @Test
    void creativeBreakOfAnotherChestKeepsItsDisplay() {
        Block mine = chest(0, 0, Chest.Type.SINGLE);
        Block other = chest(4, 0, Chest.Type.SINGLE);
        lock(mine, true);
        LockData otherData = lock(other, true);
        player.setGameMode(GameMode.CREATIVE);

        breakBlock(mine);

        assertEquals(1, displays().size());
        assertEquals(Optional.of(otherData), plugin.storage().read(other));
    }

    // ---- bypass ----

    @Test
    void bypassBreaksALockedChestInSurvivalAndTheLockDrops() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.SURVIVAL);
        player.addAttachment(plugin, "outlands.locks.bypass", true);

        BlockBreakEvent event = breakBlock(block);

        assertFalse(event.isCancelled());
        assertTrue(displays().isEmpty());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals(data, dataOf(droppedItems().get(0).getItemStack()));
        assertNull(player.nextActionBar());
    }

    @Test
    void withoutThePermissionTheOrdinaryRulesApply() {
        Block block = chest();
        lock(block, true);
        player.addAttachment(plugin, "outlands.locks.bypass", false);

        assertTrue(breakBlock(block).isCancelled());
    }

    // ---- stage 4 stays in charge of sneak + key ----

    @Test
    void sneakWithTheMatchingKeyInSurvivalRemovesTheLockInsteadOfDenying() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        BlockBreakEvent event = breakBlock(block);

        assertTrue(event.isCancelled());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals("You removed the lock.", plain(player.nextActionBar()));
        assertNull(player.nextActionBar());
    }

    @Test
    void creativeSneakWithTheMatchingKeyStillRemovesTheLockAndKeepsTheBlock() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.CREATIVE);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        assertTrue(breakBlock(block).isCancelled());

        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(droppedItems().isEmpty());
    }

    @Test
    void sneakWithAForeignKeyInSurvivalIsDenied() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        assertTrue(breakBlock(block).isCancelled());

        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    // ---- not ours ----

    @Test
    void unlockedChestBreaksNormally() {
        Block block = chest();

        assertFalse(breakBlock(block).isCancelled());
        assertNull(player.nextActionBar());
        assertTrue(droppedItems().isEmpty());
    }

    @Test
    void otherBlocksBreakNormally() {
        Block block = world.getBlockAt(3, 64, 3);
        block.setType(Material.STONE);

        assertFalse(breakBlock(block).isCancelled());
    }

    @Test
    void enderChestIsNotTouched() {
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(Material.ENDER_CHEST);

        assertFalse(breakBlock(block).isCancelled());
    }

    @Test
    void anotherPluginsCancellationIsRespected() {
        Block block = chest();
        LockData data = lock(block, true);
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
        assertTrue(droppedItems().isEmpty());
    }
}
