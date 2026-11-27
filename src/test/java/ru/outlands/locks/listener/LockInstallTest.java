package ru.outlands.locks.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ItemDisplay;
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
import ru.outlands.locks.lock.LockData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockInstallTest {

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

    private Block chest(int x, int z, Material material, BlockFace facing, Chest.Type type) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(material);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(facing);
        data.setType(type);
        block.setBlockData(data);
        return block;
    }

    private Block chest(Material material) {
        return chest(0, 0, material, BlockFace.NORTH, Chest.Type.SINGLE);
    }

    private ItemStack newLock(int amount) {
        return plugin.items().create(LockItemType.LOCK, amount).orElseThrow();
    }

    private ItemStack usedLock(LockData data) {
        ItemStack lock = newLock(1);
        ItemMeta meta = lock.getItemMeta();
        data.writeTo(meta.getPersistentDataContainer(), plugin.keys());
        lock.setItemMeta(meta);
        return lock;
    }

    private PlayerInteractEvent click(Block block, ItemStack item, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block,
                BlockFace.NORTH, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent click(Block block, ItemStack item) {
        return click(block, item, EquipmentSlot.HAND);
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void newLockOnSingleChestWritesFreshIdAndKeyNotCut() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(1));

        PlayerInteractEvent event = click(block, player.getInventory().getItemInMainHand());

        LockData data = plugin.storage().read(block).orElseThrow();
        assertFalse(data.keyCut());
        assertTrue(event.isCancelled());
    }

    @Test
    void oneLockIsTakenFromTheStack() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(3));

        click(block, player.getInventory().getItemInMainHand());

        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void lastLockLeavesTheHandEmpty() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(1));

        click(block, player.getInventory().getItemInMainHand());

        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    void usedLockKeepsItsIdAndKeyCut() {
        Block block = chest(Material.CHEST);
        LockData old = new LockData(UUID.randomUUID(), true);
        player.getInventory().setItemInMainHand(usedLock(old));

        click(block, player.getInventory().getItemInMainHand());

        assertEquals(Optional.of(old), plugin.storage().read(block));
    }

    @Test
    void installShowsMessage() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(1));
        click(block, player.getInventory().getItemInMainHand());
        assertEquals("You attached a lock.", plain(player.nextActionBar()));
    }

    @Test
    void displaySpawnsOnTheFrontFaceWithLockId() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(1));

        click(block, player.getInventory().getItemInMainHand());

        List<ItemDisplay> displays = displays();
        assertEquals(1, displays.size());
        ItemDisplay d = displays.get(0);
        LockData data = plugin.storage().read(block).orElseThrow();
        assertEquals(data.id().toString(),
                d.getPersistentDataContainer().get(plugin.keys().lockId(), PersistentDataType.STRING));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{0, 64, 0},
                d.getPersistentDataContainer().get(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY));
        assertEquals(0.5, d.getLocation().getX(), 1e-6);
        assertEquals(64.5 + plugin.settings().display().up(), d.getLocation().getY(), 1e-6);
        assertEquals(-plugin.settings().display().out(), d.getLocation().getZ(), 1e-6);
        assertEquals(plugin.settings().display().scale(), d.getTransformation().getScale().x(), 1e-6);
        assertEquals(Material.IRON_INGOT, d.getItemStack().getType());
    }

    @Test
    void doubleChestGetsDataOnBothHalvesAndOneCentredDisplay() {
        Block a = chest(0, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        player.getInventory().setItemInMainHand(newLock(1));

        click(b, player.getInventory().getItemInMainHand());

        assertEquals(plugin.storage().read(a), plugin.storage().read(b));
        assertTrue(plugin.storage().read(a).isPresent());
        List<ItemDisplay> displays = displays();
        assertEquals(1, displays.size());
        assertEquals(1.0, displays.get(0).getLocation().getX(), 1e-6);
    }

    @Test
    void aTrappedChestIsLockable() {
        Block block = chest(0, 5, Material.TRAPPED_CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        player.getInventory().setItemInMainHand(newLock(1));

        click(block, player.getInventory().getItemInMainHand());

        assertTrue(plugin.storage().read(block).isPresent());
        assertEquals(1, displays().size());
    }

    @Test
    void copperChestsAreNeverLocked() {
        int x = 0;
        for (Material m : List.of(Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST,
                Material.WEATHERED_COPPER_CHEST, Material.OXIDIZED_COPPER_CHEST,
                Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST,
                Material.WAXED_WEATHERED_COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST)) {
            Block block = chest(x, 5, m, BlockFace.NORTH, Chest.Type.SINGLE);
            x += 3;
            player.getInventory().setItemInMainHand(newLock(1));

            PlayerInteractEvent event = click(block, player.getInventory().getItemInMainHand());

            assertEquals(1, player.getInventory().getItemInMainHand().getAmount(), m.name());
            assertTrue(plugin.storage().read(block).isEmpty(), m.name());
            assertFalse(event.isCancelled(), m.name());
        }
        assertTrue(displays().isEmpty());
    }

    @Test
    void enderChestIsNotLocked() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.ENDER_CHEST);
        player.getInventory().setItemInMainHand(newLock(1));

        PlayerInteractEvent event = click(block, player.getInventory().getItemInMainHand());

        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(displays().isEmpty());
        assertFalse(event.isCancelled());
    }

    @Test
    void withoutSneakNothingHappens() {
        player.setSneaking(false);
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(1));

        click(block, player.getInventory().getItemInMainHand());

        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void offHandEventIsIgnored() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInOffHand(newLock(1));

        click(block, player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals(1, player.getInventory().getItemInOffHand().getAmount());
    }

    @Test
    void lockInOffHandIsNotUsedWhenMainHandEventFires() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInOffHand(newLock(1));

        click(block, null, EquipmentSlot.HAND);

        assertTrue(plugin.storage().read(block).isEmpty());
    }

    @Test
    void ordinaryItemOfTheSameMaterialIsNotALock() {
        Block block = chest(Material.CHEST);
        ItemStack plain = new ItemStack(Material.IRON_INGOT);
        player.getInventory().setItemInMainHand(plain);

        click(block, plain);

        assertTrue(plugin.storage().read(block).isEmpty());
    }

    @Test
    void alreadyLockedChestRefusesAndKeepsEverything() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(2));
        click(block, player.getInventory().getItemInMainHand());
        LockData first = plugin.storage().read(block).orElseThrow();
        player.nextActionBar();

        PlayerInteractEvent event = click(block, player.getInventory().getItemInMainHand());

        assertEquals("This is already locked.", plain(player.nextActionBar()));
        assertEquals(Optional.of(first), plugin.storage().read(block));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, displays().size());
        assertTrue(event.isCancelled());
    }

    @Test
    void alreadyLockedDoubleChestRefusesFromEitherHalf() {
        Block a = chest(0, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        player.getInventory().setItemInMainHand(newLock(3));
        click(a, player.getInventory().getItemInMainHand());

        click(b, player.getInventory().getItemInMainHand());

        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, displays().size());
    }

    private boolean chestOpen() {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getType() == InventoryType.CHEST;
    }

    private ItemStack keyFor(UUID id) {
        ItemStack key = plugin.items().create(LockItemType.KEY, 1).orElseThrow();
        ItemMeta meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, id.toString());
        key.setItemMeta(meta);
        return key;
    }

    @Test
    void lockInTheMainHandAndTheKeyInTheOffHandOpensTheLockedChest() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(3));
        click(block, player.getInventory().getItemInMainHand());
        LockData data = plugin.storage().read(block).orElseThrow();
        // A lock that is already tied to a key is installed or opens, a loose one is tied (LockBindTest).
        ItemStack tied = plugin.lockItems().boundLock(newLock(1), UUID.randomUUID());
        tied.setAmount(2);
        player.getInventory().setItemInMainHand(tied);
        player.getInventory().setItemInOffHand(keyFor(data.id()));
        player.nextActionBar();

        PlayerInteractEvent event = click(block, player.getInventory().getItemInMainHand());

        assertNull(player.nextActionBar());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, displays().size());
        assertTrue(event.isCancelled());
        assertTrue(chestOpen());
    }

    @Test
    void lockInTheMainHandAndAForeignKeyInTheOffHandStillRefuses() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(3));
        click(block, player.getInventory().getItemInMainHand());
        ItemStack tied = plugin.lockItems().boundLock(newLock(1), UUID.randomUUID());
        tied.setAmount(2);
        player.getInventory().setItemInMainHand(tied);
        player.getInventory().setItemInOffHand(keyFor(UUID.randomUUID()));
        player.nextActionBar();

        click(block, player.getInventory().getItemInMainHand());

        assertEquals("This is already locked.", plain(player.nextActionBar()));
        assertFalse(chestOpen());
    }

    @Test
    void bypassPermissionWithALockInHandOpensTheLockedChest() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(3));
        click(block, player.getInventory().getItemInMainHand());
        player.addAttachment(plugin, "outlands.locks.bypass", true);
        player.nextActionBar();

        click(block, player.getInventory().getItemInMainHand());

        assertNull(player.nextActionBar());
        assertTrue(chestOpen());
    }

    @Test
    void alreadyCancelledEventIsLeftAlone() {
        Block block = chest(Material.CHEST);
        player.getInventory().setItemInMainHand(newLock(1));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.NORTH, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(displays().isEmpty());
    }

    // ---- a chest that is open when the lock goes on ----

    private Inventory inventoryOf(Block block) {
        return ((Container) block.getState()).getInventory();
    }

    private static boolean chestOpenFor(PlayerMock who) {
        Inventory top = who.getOpenInventory().getTopInventory();
        return top != null && top.getType() == InventoryType.CHEST;
    }

    @Test
    void someoneWhoHasTheChestOpenIsSentAwayWhenTheLockGoesOn() {
        Block block = chest(Material.CHEST);
        PlayerMock alex = server.addPlayer("Alex");
        Inventory inventory = inventoryOf(block);
        alex.openInventory(inventory);
        assertTrue(chestOpenFor(alex));
        player.getInventory().setItemInMainHand(newLock(1));

        click(block, player.getInventory().getItemInMainHand());

        assertFalse(chestOpenFor(alex));
        assertTrue(inventory.getViewers().isEmpty());
        assertTrue(plugin.storage().read(block).isPresent());
    }

    @Test
    void severalViewersAreAllSentAway() {
        Block block = chest(Material.CHEST);
        Inventory inventory = inventoryOf(block);
        PlayerMock alex = server.addPlayer("Alex");
        PlayerMock bob = server.addPlayer("Bob");
        alex.openInventory(inventory);
        bob.openInventory(inventory);
        player.getInventory().setItemInMainHand(newLock(1));

        click(block, player.getInventory().getItemInMainHand());

        assertFalse(chestOpenFor(alex));
        assertFalse(chestOpenFor(bob));
        assertTrue(inventory.getViewers().isEmpty());
    }

    @Test
    void aViewerWhoCouldOpenTheChestNowStaysAndTheOthersGo() {
        Block block = chest(Material.CHEST);
        Inventory inventory = inventoryOf(block);
        UUID id = UUID.randomUUID();
        PlayerMock withKey = server.addPlayer("WithKey");
        PlayerMock withBypass = server.addPlayer("WithBypass");
        PlayerMock stranger = server.addPlayer("Stranger");
        withKey.getInventory().setItemInOffHand(keyFor(id));
        withBypass.addAttachment(plugin, "outlands.locks.bypass", true);
        for (PlayerMock viewer : List.of(withKey, withBypass, stranger)) {
            viewer.openInventory(inventory);
        }
        player.getInventory().setItemInMainHand(plugin.lockItems().boundLock(newLock(1), id));

        click(block, player.getInventory().getItemInMainHand());

        assertTrue(chestOpenFor(withKey));
        assertTrue(chestOpenFor(withBypass));
        assertFalse(chestOpenFor(stranger));
        assertEquals(List.of(withKey, withBypass), inventory.getViewers());
    }

    @Test
    void viewersOfEitherHalfOfADoubleChestAreSentAway() {
        Block a = chest(0, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        Inventory left = inventoryOf(a);
        Inventory right = inventoryOf(b);
        PlayerMock alex = server.addPlayer("Alex");
        PlayerMock bob = server.addPlayer("Bob");
        alex.openInventory(left);
        bob.openInventory(right);
        player.getInventory().setItemInMainHand(newLock(1));

        click(b, player.getInventory().getItemInMainHand());

        assertFalse(chestOpenFor(alex));
        assertFalse(chestOpenFor(bob));
        assertTrue(left.getViewers().isEmpty());
        assertTrue(right.getViewers().isEmpty());
    }
}
