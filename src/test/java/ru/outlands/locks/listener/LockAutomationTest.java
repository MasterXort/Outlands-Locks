package ru.outlands.locks.listener;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PLAN 4.7 (attached chest) and 4.8 (hoppers, droppers, copper golem). */
class LockAutomationTest {

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

    private LockData lock(Block block) {
        LockData data = new LockData(UUID.randomUUID(), true);
        List<Block> halves = ChestHalves.of(block);
        List<BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        return data;
    }

    private Chest typeOf(Block block) {
        return (Chest) block.getBlockData();
    }

    // ---- 4.7 attached chest ----

    private BlockPlaceEvent place(Block placed) {
        BlockPlaceEvent event = new BlockPlaceEvent(placed, placed.getState(), placed, new ItemStack(Material.CHEST),
                player, true);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aChestPlacedNextToALockedOneBecomesSingleAndIsNotLocked() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        LockData data = lock(locked);
        Block placed = chest(1, 0, Chest.Type.RIGHT);

        place(placed);

        assertEquals(Chest.Type.SINGLE, typeOf(placed).getType());
        assertEquals(BlockFace.NORTH, typeOf(placed).getFacing());
        assertTrue(plugin.storage().read(placed).isEmpty());
        assertEquals(Chest.Type.SINGLE, typeOf(locked).getType());
        assertEquals(Optional.of(data), plugin.storage().read(locked));
    }

    @Test
    void worksOnTheOtherSideToo() {
        Block locked = chest(1, 0, Chest.Type.SINGLE);
        lock(locked);
        Block placed = chest(0, 0, Chest.Type.LEFT);

        place(placed);

        assertEquals(Chest.Type.SINGLE, typeOf(placed).getType());
    }

    @Test
    void aLockedNeighbourThatWasAlreadyPairedGoesBackToSingle() {
        Block locked = chest(0, 0, Chest.Type.LEFT);
        lock(locked);
        Block placed = chest(1, 0, Chest.Type.RIGHT);
        // the neighbour got paired before the event reached us
        locked.setBlockData(typeOf(locked));

        place(placed);

        assertEquals(Chest.Type.SINGLE, typeOf(placed).getType());
        assertEquals(Chest.Type.SINGLE, typeOf(locked).getType());
        assertTrue(plugin.storage().read(locked).isPresent());
    }

    @Test
    void aTrappedChestIsHandledToo() {
        Block locked = chest(10, 10, Material.TRAPPED_CHEST, Chest.Type.SINGLE);
        lock(locked);
        Block placed = chest(11, 10, Material.TRAPPED_CHEST, Chest.Type.RIGHT);

        place(placed);

        assertEquals(Chest.Type.SINGLE, typeOf(placed).getType());
    }

    @Test
    void aChestPlacedNextToAnUnlockedOneStillPairs() {
        chest(0, 0, Chest.Type.LEFT);
        Block placed = chest(1, 0, Chest.Type.RIGHT);

        place(placed);

        assertEquals(Chest.Type.RIGHT, typeOf(placed).getType());
    }

    @Test
    void aSingleChestPlacedNextToALockedOneIsLeftAlone() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        lock(locked);
        Block placed = chest(1, 0, Chest.Type.SINGLE);

        place(placed);

        assertEquals(Chest.Type.SINGLE, typeOf(placed).getType());
    }

    @Test
    void aLockedChestOnTheFarSideOfAnUnrelatedPairIsNotAffected() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        lock(locked);
        Block placed = chest(5, 0, Chest.Type.RIGHT);
        chest(4, 0, Chest.Type.LEFT);

        place(placed);

        assertEquals(Chest.Type.RIGHT, typeOf(placed).getType());
    }

    @Test
    void otherBlocksPlacedNextToALockedChestAreIgnored() {
        lock(chest(0, 0, Chest.Type.SINGLE));
        Block stone = world.getBlockAt(1, 64, 0);
        stone.setType(Material.STONE);

        assertFalse(place(stone).isCancelled());
        assertEquals(Material.STONE, stone.getType());
    }

    @Test
    void aCancelledPlacementIsLeftAlone() {
        lock(chest(0, 0, Chest.Type.SINGLE));
        Block placed = chest(1, 0, Chest.Type.RIGHT);
        BlockPlaceEvent event = new BlockPlaceEvent(placed, placed.getState(), placed, new ItemStack(Material.CHEST),
                player, true);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(Chest.Type.RIGHT, typeOf(placed).getType());
    }

    // ---- 4.8 hoppers, hopper minecarts, droppers ----

    /** An inventory whose getHolder(boolean) answers the given holder: what Paper hands the event. */
    private static Inventory inventoryHeldBy(InventoryHolder holder) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHolder" -> holder;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Inventory inventoryOf(Block block) {
        return inventoryHeldBy((InventoryHolder) block.getState(false));
    }

    private Inventory plainInventory() {
        return inventoryHeldBy(null);
    }

    private InventoryMoveItemEvent move(Inventory source, Inventory destination) {
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, new ItemStack(Material.COBBLESTONE),
                destination, true);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aHopperCannotPullFromALockedChest() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        lock(locked);

        assertTrue(move(inventoryOf(locked), plainInventory()).isCancelled());
    }

    @Test
    void aHopperOrDropperCannotPushIntoALockedChest() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        lock(locked);

        assertTrue(move(plainInventory(), inventoryOf(locked)).isCancelled());
    }

    @Test
    void unlockedChestsMoveItemsNormally() {
        Block open = chest(0, 0, Chest.Type.SINGLE);

        assertFalse(move(inventoryOf(open), plainInventory()).isCancelled());
        assertFalse(move(plainInventory(), inventoryOf(open)).isCancelled());
        assertFalse(move(plainInventory(), plainInventory()).isCancelled());
    }

    @Test
    void movesBetweenALockedAndAnUnlockedChestAreStopped() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        lock(locked);
        Block open = chest(4, 0, Chest.Type.SINGLE);

        assertTrue(move(inventoryOf(locked), inventoryOf(open)).isCancelled());
        assertTrue(move(inventoryOf(open), inventoryOf(locked)).isCancelled());
    }

    private Inventory doubleChestInventory(Block left, Block right) {
        DoubleChestInventory both = (DoubleChestInventory) Proxy.newProxyInstance(
                DoubleChestInventory.class.getClassLoader(), new Class<?>[]{DoubleChestInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLeftSide" -> inventoryOf(left);
                    case "getRightSide" -> inventoryOf(right);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return inventoryHeldBy(new DoubleChest(both));
    }

    @Test
    void aLockedDoubleChestIsClosedToAutomationFromEitherSide() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        lock(a);

        assertTrue(move(doubleChestInventory(a, b), plainInventory()).isCancelled());
        assertTrue(move(plainInventory(), doubleChestInventory(a, b)).isCancelled());
    }

    @Test
    void anUnlockedDoubleChestMovesItemsNormally() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);

        assertFalse(move(doubleChestInventory(a, b), plainInventory()).isCancelled());
    }

    @Test
    void aMinecartHolderIsNotAChestAndIsIgnored() {
        InventoryHolder cart = (InventoryHolder) Proxy.newProxyInstance(InventoryHolder.class.getClassLoader(),
                new Class<?>[]{InventoryHolder.class}, (p, m, a) -> {
                    throw new UnsupportedOperationException(m.getName());
                });

        assertFalse(move(inventoryHeldBy(cart), plainInventory()).isCancelled());
    }

    @Test
    void aMoveAnotherPluginAlreadyCancelledStaysCancelled() {
        Block locked = chest(0, 0, Chest.Type.SINGLE);
        lock(locked);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(inventoryOf(locked),
                new ItemStack(Material.COBBLESTONE), plainInventory(), true);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aMoveWithANullHolderDoesNotFail() {
        assertFalse(move(plainInventory(), plainInventory()).isCancelled());
    }

    // ---- 4.8 copper golem ----

    private ItemTransportingEntityValidateTargetEvent golemChecks(Block target) {
        ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(player, target);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void theGolemUsesACopperChestEvenWithAnOldLockOnIt() {
        Block old = chest(0, 0, Material.COPPER_CHEST, Chest.Type.SINGLE);
        lock(old);

        assertTrue(golemChecks(old).isAllowed());
    }

    @Test
    void theGolemMayNotUseALockedOrdinaryOrTrappedChest() {
        for (Material material : List.of(Material.CHEST, Material.TRAPPED_CHEST)) {
            Block locked = chest(10, 10, material, Chest.Type.SINGLE);
            lock(locked);

            assertFalse(golemChecks(locked).isAllowed(), material.name());
        }
    }

    @Test
    void theGolemStillUsesUnlockedChests() {
        Block open = chest(0, 0, Material.COPPER_CHEST, Chest.Type.SINGLE);

        assertTrue(golemChecks(open).isAllowed());
    }

    @Test
    void theGolemKeepsUsingOtherTargets() {
        Block stone = world.getBlockAt(3, 64, 3);
        stone.setType(Material.STONE);

        assertTrue(golemChecks(stone).isAllowed());
    }

    @Test
    void aTargetAnotherPluginAlreadyDisallowedStaysDisallowed() {
        Block open = chest(0, 0, Material.CHEST, Chest.Type.SINGLE);
        ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(player, open);
        event.setAllowed(false);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isAllowed());
    }
}
