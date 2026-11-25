package ru.outlands.locks.listener;

import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.config.Settings;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PLAN 4.9: explosions and other non-player ways of destroying a locked chest. */
class LockExplosionTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
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

    private LockData lock(Block block) {
        LockData data = new LockData(UUID.randomUUID(), true);
        List<Block> halves = ChestHalves.of(block);
        List<BlockData> shapes = halves.stream().map(Block::getBlockData).toList();
        plugin.storage().write(halves, data);
        for (int i = 0; i < halves.size(); i++) {
            halves.get(i).setBlockData(shapes.get(i));
        }
        plugin.displays().spawn(halves, data).orElseThrow();
        return data;
    }

    /** MockBukkit cannot spawn every entity type; the events only ask the entity for its type. */
    private static Entity entityOf(EntityType type) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Entity[" + type + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private EntityExplodeEvent explode(EntityType source, Block... blocks) {
        EntityExplodeEvent event = new EntityExplodeEvent(entityOf(source), new Location(world, 0, 64, 0),
                new ArrayList<>(List.of(blocks)), 1f, ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private BlockExplodeEvent explodeBlock(Block... blocks) {
        Block bed = world.getBlockAt(20, 64, 20);
        bed.setType(Material.RED_BED);
        BlockExplodeEvent event = new BlockExplodeEvent(bed, bed.getState(), new ArrayList<>(List.of(blocks)), 1f,
                ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    private List<Item> droppedItems() {
        return world.getEntitiesByClass(Item.class).stream().toList();
    }

    private void allowSources(Set<EntityType> sources) throws ReflectiveOperationException {
        Settings old = plugin.settings();
        Settings changed = new Settings(old.language(), old.provider(), old.vanillaItems(), old.nexoIds(),
                old.display(), old.doorDisplay(), old.trapdoorDisplay(), sources, old.itemLore());
        Field field = OutlandsLocks.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, changed);
    }

    // ---- allowed sources: TNT and TNT minecart ----

    @Test
    void tntBreaksTheChestAndTheLockVanishesWithoutADrop() {
        Block block = chest();
        lock(block);

        EntityExplodeEvent event = explode(EntityType.TNT, block);

        assertTrue(event.blockList().contains(block));
        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
        assertTrue(droppedItems().isEmpty());
    }

    @Test
    void tntMinecartBreaksTheChestToo() {
        Block block = chest();
        lock(block);

        EntityExplodeEvent event = explode(EntityType.TNT_MINECART, block);

        assertTrue(event.blockList().contains(block));
        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void anOneHalfExplosionUnlocksTheSurvivingHalfAndRemovesTheDisplay() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        lock(a);

        EntityExplodeEvent event = explode(EntityType.TNT, b);

        assertEquals(List.of(b), event.blockList());
        assertTrue(plugin.storage().read(a).isEmpty());
        assertTrue(plugin.storage().read(b).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void whenBothHalvesGoTheLockIsClearedOnce() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        lock(a);

        explode(EntityType.TNT, a, b);

        assertTrue(plugin.storage().read(a).isEmpty());
        assertTrue(plugin.storage().read(b).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void otherBlocksOfTheExplosionAreUntouched() {
        Block block = chest();
        lock(block);
        Block stone = world.getBlockAt(3, 64, 3);
        stone.setType(Material.STONE);

        EntityExplodeEvent event = explode(EntityType.TNT, block, stone);

        assertEquals(List.of(block, stone), event.blockList());
    }

    @Test
    void anUnlockedChestInTheListIsNotTouched() {
        Block block = chest();

        EntityExplodeEvent event = explode(EntityType.CREEPER, block);

        assertEquals(List.of(block), event.blockList());
    }

    // ---- everything else leaves the chest standing ----

    @Test
    void aCreeperLeavesTheLockedChestStanding() {
        Block block = chest();
        LockData data = lock(block);
        Block stone = world.getBlockAt(3, 64, 3);
        stone.setType(Material.STONE);

        EntityExplodeEvent event = explode(EntityType.CREEPER, block, stone);

        assertEquals(List.of(stone), event.blockList());
        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
    }

    @Test
    void crystalWitherSkullFireballAndWindChargeLeaveItStandingToo() {
        for (EntityType source : List.of(EntityType.END_CRYSTAL, EntityType.WITHER_SKULL, EntityType.WITHER,
                EntityType.FIREBALL, EntityType.ENDER_DRAGON, EntityType.WIND_CHARGE)) {
            Block block = chest();
            LockData data = lock(block);

            EntityExplodeEvent event = explode(source, block);

            assertTrue(event.blockList().isEmpty(), source.name());
            assertEquals(Optional.of(data), plugin.storage().read(block), source.name());
            block.setType(Material.AIR);
            displays().forEach(Entity::remove);
        }
    }

    @Test
    void aSingleHalfIsKeptWhenOnlyThePartnerIsInTheListOfAForbiddenSource() {
        Block a = chest(0, 0, Chest.Type.LEFT);
        Block b = chest(1, 0, Chest.Type.RIGHT);
        LockData data = lock(a);

        EntityExplodeEvent event = explode(EntityType.CREEPER, a);

        assertTrue(event.blockList().isEmpty());
        assertEquals(Optional.of(data), plugin.storage().read(a));
        assertEquals(Optional.of(data), plugin.storage().read(b));
        assertEquals(1, displays().size());
    }

    @Test
    void aBlockExplosionLikeABedOrAnAnchorNeverBreaksALockedChest() {
        Block block = chest();
        LockData data = lock(block);

        BlockExplodeEvent event = explodeBlock(block);

        assertTrue(event.blockList().isEmpty());
        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
    }

    @Test
    void aBlockExplosionKeepsUnlockedChestsInTheList() {
        Block block = chest();

        assertEquals(List.of(block), explodeBlock(block).blockList());
    }

    // ---- config ----

    @Test
    void aSourceAddedToTheConfigBreaksTheChest() throws Exception {
        allowSources(Set.of(EntityType.TNT, EntityType.CREEPER));
        Block block = chest();
        lock(block);

        EntityExplodeEvent event = explode(EntityType.CREEPER, block);

        assertEquals(List.of(block), event.blockList());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void aSourceRemovedFromTheConfigNoLongerBreaksTheChest() throws Exception {
        allowSources(Set.of(EntityType.TNT_MINECART));
        Block block = chest();
        LockData data = lock(block);

        EntityExplodeEvent event = explode(EntityType.TNT, block);

        assertTrue(event.blockList().isEmpty());
        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    @Test
    void anEmptyListProtectsAgainstEverything() throws Exception {
        allowSources(Set.of());
        Block block = chest();
        LockData data = lock(block);

        assertTrue(explode(EntityType.TNT, block).blockList().isEmpty());
        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    // ---- other plugins ----

    @Test
    void aCancelledExplosionChangesNothing() {
        Block block = chest();
        LockData data = lock(block);
        EntityExplodeEvent event = new EntityExplodeEvent(entityOf(EntityType.TNT), new Location(world, 0, 64, 0),
                new ArrayList<>(List.of(block)), 1f, ExplosionResult.DESTROY);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
    }

    /** Stands in for Towny: trims the list after our HIGH handler ran. */
    private static final class LateTrim implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
        public void on(EntityExplodeEvent event) {
            event.blockList().clear();
        }
    }

    @Test
    void aBlockAnotherPluginTookOutOfTheListLaterKeepsItsLock() {
        server.getPluginManager().registerEvents(new LateTrim(), plugin);
        Block block = chest();
        LockData data = lock(block);

        EntityExplodeEvent event = explode(EntityType.TNT, block);

        assertTrue(event.blockList().isEmpty());
        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(1, displays().size());
    }

    // ---- entities that change blocks (wither, PLAN 4.9 [проверить]) ----

    @Test
    void aWitherEatingALockedChestIsStopped() {
        Block block = chest();
        LockData data = lock(block);
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(entityOf(EntityType.WITHER), block,
                Material.AIR.createBlockData());

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(block));
    }

    @Test
    void entitiesChangingAnUnlockedChestOrOtherBlocksAreNotStopped() {
        Block unlocked = chest();
        Block stone = world.getBlockAt(3, 64, 3);
        stone.setType(Material.STONE);

        for (Block block : List.of(unlocked, stone)) {
            EntityChangeBlockEvent event = new EntityChangeBlockEvent(entityOf(EntityType.WITHER), block,
                    Material.AIR.createBlockData());
            server.getPluginManager().callEvent(event);
            assertFalse(event.isCancelled());
        }
    }
}
