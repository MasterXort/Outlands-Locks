package ru.outlands.locks.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockStorageTest {

    private WorldMock world;
    private LockStorage storage;
    private LockKeys keys;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        keys = new LockKeys(plugin);
        storage = new LockStorage(keys);
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block chest(int x, int y, int z, Material material, BlockFace facing, Chest.Type type) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(facing);
        data.setType(type);
        block.setBlockData(data);
        return block;
    }

    @Test
    void lockDataRoundTripsThroughChest() {
        Block block = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        LockData data = new LockData(UUID.randomUUID(), true);
        storage.write(List.of(block), data);
        assertEquals(Optional.of(data), storage.read(world.getBlockAt(0, 64, 0)));
    }

    @Test
    void unlockedChestReadsEmpty() {
        Block block = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertEquals(Optional.empty(), storage.read(block));
    }

    @Test
    void clearRemovesLockData() {
        Block block = chest(0, 64, 0, Material.TRAPPED_CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        storage.write(List.of(block), new LockData(UUID.randomUUID(), false));
        storage.clear(List.of(block));
        assertEquals(Optional.empty(), storage.read(block));
    }

    @Test
    void writeToDoubleChestMarksBothHalves() {
        Block a = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        LockData data = new LockData(UUID.randomUUID(), false);
        storage.write(ChestHalves.of(a), data);
        assertEquals(Optional.of(data), storage.read(a));
        assertEquals(Optional.of(data), storage.read(b));
    }

    /** MockBukkit resets the chest shape on TileState.update; Paper does not. */
    private void write(Block block, LockData data) {
        List<Block> halves = ChestHalves.of(block);
        List<org.bukkit.block.data.BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        storage.write(halves, data);
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
    }

    @Test
    void overwritingADoubleChestUpdatesBothHalvesFromEitherSide() {
        Block a = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        LockData first = new LockData(UUID.randomUUID(), false);
        write(a, first);

        LockData second = new LockData(first.id(), true);
        write(b, second);
        assertEquals(Optional.of(second), storage.read(a));
        assertEquals(Optional.of(second), storage.read(b));

        LockData third = new LockData(UUID.randomUUID(), false);
        write(a, third);
        assertEquals(Optional.of(third), storage.read(a));
        assertEquals(Optional.of(third), storage.read(b));
    }

    @Test
    void clearingADoubleChestClearsBothHalves() {
        Block a = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        write(a, new LockData(UUID.randomUUID(), true));

        storage.clear(ChestHalves.of(b));

        assertEquals(Optional.empty(), storage.read(a));
        assertEquals(Optional.empty(), storage.read(b));
    }

    @Test
    void itemLockDataRoundTrips() {
        ItemStack stack = new ItemStack(Material.IRON_INGOT);
        LockData data = new LockData(UUID.randomUUID(), true);
        ItemMeta meta = stack.getItemMeta();
        data.writeTo(meta.getPersistentDataContainer(), keys);
        stack.setItemMeta(meta);
        assertEquals(Optional.of(data), LockData.read(stack.getItemMeta().getPersistentDataContainer(), keys));
    }

    @Test
    void itemWithoutLockIdHasNoData() {
        ItemStack stack = new ItemStack(Material.IRON_INGOT);
        assertEquals(Optional.empty(), LockData.read(stack.getItemMeta().getPersistentDataContainer(), keys));
    }

    @Test
    void invalidLockIdReadsEmpty() {
        ItemMeta meta = new ItemStack(Material.IRON_INGOT).getItemMeta();
        meta.getPersistentDataContainer().set(keys.lockId(), PersistentDataType.STRING, "nope");
        assertEquals(Optional.empty(), LockData.read(meta.getPersistentDataContainer(), keys));
    }

    @Test
    void missingKeyCutDefaultsToFalse() {
        ItemMeta meta = new ItemStack(Material.IRON_INGOT).getItemMeta();
        UUID id = UUID.randomUUID();
        meta.getPersistentDataContainer().set(keys.lockId(), PersistentDataType.STRING, id.toString());
        assertEquals(Optional.of(new LockData(id, false)), LockData.read(meta.getPersistentDataContainer(), keys));
    }

    @Test
    void lockableMaterialsAreOnlyTheChestAndTheTrappedChest() {
        for (Material m : List.of(Material.CHEST, Material.TRAPPED_CHEST)) {
            assertTrue(LockableBlocks.isLockable(m), m.name());
        }
        for (Material m : List.of(Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST,
                Material.WEATHERED_COPPER_CHEST, Material.OXIDIZED_COPPER_CHEST,
                Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST,
                Material.WAXED_WEATHERED_COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST)) {
            assertFalse(LockableBlocks.isLockable(m), m.name());
        }
        assertFalse(LockableBlocks.isLockable(Material.ENDER_CHEST));
        assertFalse(LockableBlocks.isLockable(Material.BARREL));
    }

    @Test
    void singleChestHasOneHalf() {
        Block block = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertEquals(List.of(block), ChestHalves.of(block));
    }

    @Test
    void doubleChestFindsPartnerFromEitherHalf() {
        Block a = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block b = chest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertEquals(List.of(a, b), ChestHalves.of(a));
        assertEquals(List.of(b, a), ChestHalves.of(b));
    }

    @Test
    void doubleChestOnZAxis() {
        Block a = chest(0, 64, 0, Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.LEFT);
        chest(0, 64, 1, Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.RIGHT);
        assertEquals(2, ChestHalves.of(a).size());
    }

    @Test
    void neighbourOfTheSameHalfTypeIsNotAPartner() {
        Block a = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        chest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        assertEquals(List.of(a), ChestHalves.of(a));
    }

    @Test
    void neighbourOfDifferentFacingIsNotAPartner() {
        Block a = chest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        chest(1, 64, 0, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        chest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertEquals(List.of(a), ChestHalves.of(a));
    }
}
