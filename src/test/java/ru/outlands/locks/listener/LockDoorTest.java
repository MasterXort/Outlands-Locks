package ru.outlands.locks.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ExplosionResult;
import org.bukkit.GameEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.persistence.PersistentDataType;
import ru.outlands.locks.config.Settings;
import ru.outlands.locks.display.DisplaySettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
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
import ru.outlands.locks.command.OutlandsLocksCommand;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wooden doors as lockable blocks: the lock lives in the data of the chunk, the displays follow the door. */
class LockDoorTest {

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
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- fixtures ----

    private Block door(int x, int z, Material type, Door.Hinge hinge, boolean open) {
        Block lower = world.getBlockAt(x, 64, z);
        Block upper = lower.getRelative(BlockFace.UP);
        for (Block half : List.of(lower, upper)) {
            half.setType(type);
            Door data = (Door) half.getBlockData();
            data.setHalf(half == lower ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            data.setFacing(BlockFace.NORTH);
            data.setHinge(hinge);
            data.setOpen(open);
            half.setBlockData(data, false);
        }
        return lower;
    }

    private Block door(int x, int z) {
        return door(x, z, Material.OAK_DOOR, Door.Hinge.LEFT, false);
    }

    private LockData lock(Block lower, boolean keyCut) {
        LockData data = new LockData(UUID.randomUUID(), keyCut);
        plugin.storage().write(List.of(lower), data);
        assertTrue(plugin.displays().syncDoor(lower, data), "the lock item builds");
        return data;
    }

    private void setOpen(Block lower, boolean open) {
        for (Block half : List.of(lower, lower.getRelative(BlockFace.UP))) {
            Door data = (Door) half.getBlockData();
            data.setOpen(open);
            half.setBlockData(data, false);
        }
    }

    private ItemStack keyFor(UUID id) {
        return plugin.lockItems().key(id).orElseThrow();
    }

    private ItemStack newLock() {
        return plugin.items().create(LockItemType.LOCK, 2).orElseThrow();
    }

    private PlayerInteractEvent click(Action action, Block block, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, held, block, BlockFace.NORTH, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent rightClick(Block block) {
        return click(Action.RIGHT_CLICK_BLOCK, block, EquipmentSlot.HAND);
    }

    private List<ItemDisplay> displays() {
        return world.getEntitiesByClass(ItemDisplay.class).stream().toList();
    }

    /** The state (1 open, 0 closed) a display was drawn for. */
    private int drawnFor(ItemDisplay display) {
        return display.getPersistentDataContainer().get(plugin.keys().open(), PersistentDataType.BYTE);
    }

    private Block trapdoor(int x, int z, Bisected.Half half, boolean open) {
        Block block = world.getBlockAt(x, 64, z);
        block.setType(Material.OAK_TRAPDOOR);
        TrapDoor data = (TrapDoor) block.getBlockData();
        data.setHalf(half);
        data.setFacing(BlockFace.NORTH);
        data.setOpen(open);
        block.setBlockData(data, false);
        return block;
    }

    private void setTrapdoorOpen(Block block, boolean open) {
        TrapDoor data = (TrapDoor) block.getBlockData();
        data.setOpen(open);
        block.setBlockData(data, false);
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

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

    private Block upperOf(Block lower) {
        return lower.getRelative(BlockFace.UP);
    }

    // ---- what is lockable ----

    @Test
    void everyWoodenDoorIsLockableAndIronAndCopperDoorsAreNot() {
        for (Material m : List.of(Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
                Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR,
                Material.PALE_OAK_DOOR, Material.BAMBOO_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR)) {
            assertTrue(LockableBlocks.isLockable(m), m.name());
        }
        for (Material m : List.of(Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
                Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
                Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.PALE_OAK_TRAPDOOR,
                Material.BAMBOO_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR)) {
            assertTrue(LockableBlocks.isLockable(m), m.name());
        }
        for (Material m : List.of(Material.IRON_DOOR, Material.COPPER_DOOR, Material.EXPOSED_COPPER_DOOR,
                Material.WEATHERED_COPPER_DOOR, Material.OXIDIZED_COPPER_DOOR, Material.WAXED_COPPER_DOOR,
                Material.WAXED_EXPOSED_COPPER_DOOR, Material.IRON_TRAPDOOR, Material.COPPER_TRAPDOOR,
                Material.WAXED_COPPER_TRAPDOOR, Material.OAK_FENCE_GATE)) {
            assertFalse(LockableBlocks.isLockable(m), m.name());
        }
    }

    /**
     * The doors and trapdoors come from the block tags now. They used to be picked by name: every material that ends in
     * _DOOR or _TRAPDOOR and is neither iron nor copper. For every material of the server's enum the two must agree.
     */
    @Test
    void theTagsPickExactlyWhatTheOldNameRulesPicked() {
        for (Material m : Material.values()) {
            if (m.isLegacy()) {
                continue;
            }
            String name = m.name();
            boolean wooden = !name.contains("IRON") && !name.contains("COPPER");
            assertEquals(name.endsWith("_DOOR") && wooden, LockableBlocks.isDoor(m), name);
            assertEquals(name.endsWith("_TRAPDOOR") && wooden, LockableBlocks.isTrapdoor(m), name);
        }
    }

    // ---- storage ----

    @Test
    void theLockIsKeptForBothHalvesUnderTheLowerBlock() {
        Block lower = door(5, 5);
        LockData data = new LockData(UUID.randomUUID(), true);

        plugin.storage().write(List.of(upperOf(lower)), data);

        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(Optional.of(data), plugin.storage().read(upperOf(lower)));
    }

    @Test
    void eachDoorHasItsOwnLock() {
        Block a = door(5, 5);
        Block b = door(6, 5);
        LockData data = new LockData(UUID.randomUUID(), false);

        plugin.storage().write(List.of(a), data);

        assertTrue(plugin.storage().read(b).isEmpty());
    }

    @Test
    void clearingTakesTheEntryAway() {
        Block lower = door(5, 5);
        plugin.storage().write(List.of(lower), new LockData(UUID.randomUUID(), false));

        plugin.storage().clear(List.of(upperOf(lower)));

        assertTrue(plugin.storage().read(lower).isEmpty());
    }

    @Test
    void anIronDoorKeepsNothing() {
        Block lower = door(5, 5, Material.IRON_DOOR, Door.Hinge.LEFT, false);

        assertTrue(plugin.storage().read(lower).isEmpty());
    }

    // ---- hanging a lock ----

    @Test
    void aLockHungOnTheUpperHalfLocksTheWholeDoorAndShowsTwoDisplays() {
        Block lower = door(5, 5);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(newLock());

        PlayerInteractEvent event = rightClick(upperOf(lower));

        assertTrue(event.isCancelled());
        LockData data = plugin.storage().read(lower).orElseThrow();
        assertFalse(data.keyCut());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(2, displays().size());
        assertEquals("You attached a lock.", plain(player.nextActionBar()));
    }

    @Test
    void aLockHungOnAnOpenDoorShowsNothingUntilItShuts() {
        Block lower = door(5, 5, Material.OAK_DOOR, Door.Hinge.LEFT, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(newLock());

        rightClick(lower);

        assertTrue(plugin.storage().read(lower).isPresent());
        assertTrue(displays().isEmpty());
        setOpen(lower, false);
        plugin.displays().syncDoor(lower, plugin.storage().read(lower).orElseThrow());
        assertEquals(2, displays().size());
    }

    @Test
    void aLockedDoorRefusesASecondLock() {
        Block lower = door(5, 5);
        lock(lower, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(newLock());

        rightClick(lower);

        assertEquals("This is already locked.", plain(player.nextActionBar()));
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void anIronDoorTakesNoLock() {
        Block lower = door(5, 5, Material.IRON_DOOR, Door.Hinge.LEFT, false);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(newLock());

        PlayerInteractEvent event = rightClick(lower);

        assertFalse(event.isCancelled());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    // ---- opening ----

    @Test
    void aLockedClosedDoorRefusesWithoutAKey() {
        Block lower = door(5, 5);
        lock(lower, true);

        PlayerInteractEvent event = rightClick(upperOf(lower));

        assertTrue(event.isCancelled());
        assertEquals("Locked.", plain(player.nextActionBar()));
    }

    @Test
    void theKeyOpensTheDoor() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        assertFalse(rightClick(lower).isCancelled());
    }

    @Test
    void aKeyInTheOffHandOpensTheDoorToo() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        assertFalse(rightClick(lower).isCancelled());
    }

    @Test
    void aForeignKeyIsRefused() {
        Block lower = door(5, 5);
        lock(lower, true);
        player.getInventory().setItemInMainHand(keyFor(UUID.randomUUID()));

        assertTrue(rightClick(lower).isCancelled());
    }

    @Test
    void theBypassPermissionOpensTheDoor() {
        Block lower = door(5, 5);
        lock(lower, true);
        player.addAttachment(plugin, "outlands.locks.bypass", true);

        assertFalse(rightClick(lower).isCancelled());
    }

    @Test
    void anOpenLockedDoorCanBeShutByAnyone() {
        Block lower = door(5, 5);
        lock(lower, true);
        setOpen(lower, true);

        PlayerInteractEvent event = rightClick(lower);

        assertFalse(event.isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void anUnlockedDoorIsLeftAlone() {
        Block lower = door(5, 5);

        assertFalse(rightClick(lower).isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void sneakingWithALockAndAKeyInTheOffHandOpensABoundLockedDoorByHand() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(plugin.lockItems().boundLock(newLock(), UUID.randomUUID()));
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        PlayerInteractEvent event = rightClick(lower);

        assertTrue(event.isCancelled());
        assertTrue(((Door) lower.getBlockData()).isOpen());
        assertTrue(displays().isEmpty(), "an open door shows no lock");
    }

    // ---- displays follow the door ----

    @Test
    void theDisplaysGoWhenTheDoorOpensAndComeBackWhenItShuts() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));

        rightClick(lower);
        setOpen(lower, true);
        server.getScheduler().performOneTick();
        assertTrue(displays().isEmpty());

        player.getInventory().setItemInMainHand(null);
        rightClick(lower);
        assertEquals(2, displays().size(), "back in the same tick as the click");
        setOpen(lower, false);
        server.getScheduler().performOneTick();
        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    @Test
    void syncDoesNotDuplicateDisplaysOfAClosedDoor() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);

        plugin.displays().syncDoor(lower, data);
        plugin.displays().syncDoor(lower, data);

        assertEquals(2, displays().size());
    }

    // ---- redstone ----

    @Test
    void aSignalCannotOpenALockedClosedDoor() {
        Block lower = door(5, 5);
        lock(lower, true);
        BlockRedstoneEvent event = new BlockRedstoneEvent(lower, 0, 15);

        server.getPluginManager().callEvent(event);

        assertEquals(0, event.getNewCurrent());
    }

    @Test
    void theSameGoesForTheUpperHalf() {
        Block lower = door(5, 5);
        lock(lower, true);
        BlockRedstoneEvent event = new BlockRedstoneEvent(upperOf(lower), 0, 15);

        server.getPluginManager().callEvent(event);

        assertEquals(0, event.getNewCurrent());
    }

    @Test
    void aSignalStillWorksOnAnUnlockedDoor() {
        Block lower = door(5, 5);
        BlockRedstoneEvent event = new BlockRedstoneEvent(lower, 0, 15);

        server.getPluginManager().callEvent(event);

        assertEquals(15, event.getNewCurrent());
    }

    @Test
    void aDoorThatAKeyOpenedIsNotHeldShutBySignals() {
        Block lower = door(5, 5);
        lock(lower, true);
        setOpen(lower, true);
        BlockRedstoneEvent event = new BlockRedstoneEvent(lower, 0, 15);

        server.getPluginManager().callEvent(event);

        assertEquals(15, event.getNewCurrent());
    }

    @Test
    void aSignalGoingAwayIsLeftAlone() {
        Block lower = door(5, 5);
        lock(lower, true);
        BlockRedstoneEvent event = new BlockRedstoneEvent(lower, 15, 0);

        server.getPluginManager().callEvent(event);

        assertEquals(0, event.getNewCurrent());
    }

    @Test
    void whenASignalGoingAwayShutsAnOpenDoorItsDisplaysComeBack() {
        Block lower = door(5, 5, Material.OAK_DOOR, Door.Hinge.LEFT, true);
        lock(lower, true);
        assertTrue(displays().isEmpty());

        server.getPluginManager().callEvent(new BlockRedstoneEvent(lower, 15, 0));
        setOpen(lower, false);
        server.getScheduler().performOneTick();

        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    // ---- taking the lock off ----

    @Test
    void aKeyInTheMainHandTakesTheLockOffTheDoor() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        PlayerInteractEvent event = click(Action.LEFT_CLICK_BLOCK, upperOf(lower), EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
        ItemStack back = player.getInventory().all(Material.IRON_INGOT).values().iterator().next();
        assertEquals(data, LockData.read(back.getItemMeta().getPersistentDataContainer(), plugin.keys()).orElseThrow());
        assertEquals(Material.OAK_DOOR, lower.getType());
    }

    @Test
    void aLockWithoutACutKeyComesOffWithoutAKey() {
        Block lower = door(5, 5);
        lock(lower, false);
        player.setSneaking(true);

        click(Action.LEFT_CLICK_BLOCK, lower, EquipmentSlot.HAND);

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
    }

    // ---- breaking ----

    @Test
    void aLockedDoorCannotBeBrokenInSurvival() {
        Block lower = door(5, 5);
        lock(lower, true);
        BlockBreakEvent event = new BlockBreakEvent(upperOf(lower), player);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertEquals("A locked object cannot be broken.", plain(player.nextActionBar()));
        assertTrue(plugin.storage().read(lower).isPresent());
    }

    @Test
    void inCreativeTheDoorBreaksAndTheLockDrops() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(lower, player);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.Item.class).size());
        assertEquals(data, LockData.read(world.getEntitiesByClass(org.bukkit.entity.Item.class).iterator().next()
                .getItemStack().getItemMeta().getPersistentDataContainer(), plugin.keys()).orElseThrow());
    }

    @Test
    void theBlockUnderALockedDoorCannotBeBrokenByHand() {
        Block lower = door(5, 5);
        lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(floor, player);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void theBlockUnderAnUnlockedDoorBreaksAsUsual() {
        Block lower = door(5, 5);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(floor, player);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void theBlockUnderALockedDoorBreaksWithTheBypassPermission() {
        Block lower = door(5, 5);
        lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        player.addAttachment(plugin, "outlands.locks.bypass", true);
        BlockBreakEvent event = new BlockBreakEvent(floor, player);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void theBlockAboveTheUpperHalfIsNotAFloor() {
        Block lower = door(5, 5);
        lock(lower, true);
        Block ceiling = upperOf(upperOf(lower));
        ceiling.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(ceiling, player);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    // ---- explosions ----

    private EntityExplodeEvent explode(EntityType source, Block... blocks) {
        EntityExplodeEvent event = new EntityExplodeEvent(entityOf(source), new Location(world, 5, 64, 5),
                new ArrayList<>(List.of(blocks)), 1f, ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void tntDestroysTheLockWithTheDoor() {
        Block lower = door(5, 5);
        lock(lower, true);

        explode(EntityType.TNT, lower, upperOf(lower));

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void aCreeperLeavesTheLockedDoorStanding() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);

        EntityExplodeEvent event = explode(EntityType.CREEPER, lower, upperOf(lower));

        assertTrue(event.blockList().isEmpty());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
    }

    // ---- stale locks ----

    @Test
    void aDoorPlacedWhereALockWasLeftStartsUnlocked() {
        Block lower = door(5, 5);
        plugin.storage().write(List.of(lower), new LockData(UUID.randomUUID(), true));
        BlockPlaceEvent event = new BlockPlaceEvent(lower, lower.getState(), lower.getRelative(BlockFace.DOWN),
                new ItemStack(Material.OAK_DOOR), player, true);

        server.getPluginManager().callEvent(event);

        assertTrue(plugin.storage().read(lower).isEmpty());
    }

    @Test
    void entriesOfDoorsThatAreGoneAreDroppedWithTheChunkScan() {
        Block lower = door(5, 5);
        plugin.storage().write(List.of(lower), new LockData(UUID.randomUUID(), true));
        lower.setType(Material.AIR);
        upperOf(lower).setType(Material.AIR);

        assertTrue(plugin.storage().doorsIn(lower.getChunk()).isEmpty());
        lower.setType(Material.OAK_DOOR);
        assertTrue(plugin.storage().read(lower).isEmpty(), "the entry went away with the scan");
    }

    @Test
    void theChunkScanFindsTheLowerBlocksOfLockedDoors() {
        Block a = door(5, 5);
        Block b = door(7, 5);
        door(9, 5);
        plugin.storage().write(List.of(a), new LockData(UUID.randomUUID(), false));
        plugin.storage().write(List.of(upperOf(b)), new LockData(UUID.randomUUID(), true));

        List<Block> found = plugin.storage().doorsIn(a.getChunk());

        assertEquals(2, found.size());
        assertTrue(found.containsAll(List.of(a, b)));
    }

    // ---- trapdoors ----

    @Test
    void aLockHungOnATrapdoorShowsTwoDisplaysOnlyWhileItIsClosed() {
        Block closed = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        Block open = trapdoor(7, 5, Bisected.Half.TOP, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(newLock());

        rightClick(closed);
        rightClick(open);

        assertTrue(plugin.storage().read(closed).isPresent());
        assertTrue(plugin.storage().read(open).isPresent());
        assertEquals(2, displays().size());
    }

    @Test
    void aLockedClosedTrapdoorRefusesWithoutAKeyAndTheKeyOpensIt() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        LockData data = lock(block, true);

        assertTrue(rightClick(block).isCancelled());
        assertEquals("Locked.", plain(player.nextActionBar()));

        player.getInventory().setItemInMainHand(keyFor(data.id()));
        assertFalse(rightClick(block).isCancelled());
    }

    @Test
    void anOpenLockedTrapdoorCanBeShutByAnyone() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, true);
        lock(block, true);

        assertFalse(rightClick(block).isCancelled());
        assertNull(player.nextActionBar());
    }

    @Test
    void theTrapdoorDisplaysGoWhenItOpens() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        LockData data = lock(block, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        assertEquals(2, displays().size());

        rightClick(block);
        setTrapdoorOpen(block, true);
        server.getScheduler().performOneTick();

        assertTrue(displays().isEmpty());
    }

    @Test
    void aSignalCannotOpenALockedClosedTrapdoor() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);
        BlockRedstoneEvent event = new BlockRedstoneEvent(block, 0, 15);

        server.getPluginManager().callEvent(event);

        assertEquals(0, event.getNewCurrent());
    }

    @Test
    void aLockedTrapdoorCannotBeBrokenInSurvivalButCreativeBreaksIt() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);
        BlockBreakEvent denied = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(denied);
        assertTrue(denied.isCancelled());

        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent allowed = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(allowed);

        assertFalse(allowed.isCancelled());
        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void theKeyTakesTheLockOffATrapdoor() {
        Block block = trapdoor(5, 5, Bisected.Half.TOP, false);
        LockData data = lock(block, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        click(Action.LEFT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void tntDestroysTheLockWithTheTrapdoor() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);

        explode(EntityType.TNT, block);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void sneakingWithABoundLockAndTheKeyOpensATrapdoorByHand() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        LockData data = lock(block, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(plugin.lockItems().boundLock(newLock(), UUID.randomUUID()));
        player.getInventory().setItemInOffHand(keyFor(data.id()));

        rightClick(block);

        assertTrue(((TrapDoor) block.getBlockData()).isOpen());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 1));
    }

    @Test
    void aTrapdoorPlacedWhereALockWasLeftStartsUnlocked() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        plugin.storage().write(List.of(block), new LockData(UUID.randomUUID(), true));
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                new ItemStack(Material.OAK_TRAPDOOR), player, true);

        server.getPluginManager().callEvent(event);

        assertTrue(plugin.storage().read(block).isEmpty());
    }

    @Test
    void theBlockUnderATrapdoorIsNotProtected() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);
        Block floor = block.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(floor, player);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    // ---- mobs ----

    private EntityInteractEvent mobUses(Block block) {
        EntityInteractEvent event = new EntityInteractEvent(entityOf(EntityType.VILLAGER), block);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void mobOpened(Block opened, Entity entity) {
        server.getPluginManager().callEvent(new GenericGameEvent(GameEvent.BLOCK_OPEN, opened.getLocation(), entity,
                16, false));
    }

    @Test
    void aVillagerCannotOpenALockedClosedDoor() {
        Block lower = door(5, 5);
        lock(lower, true);

        assertTrue(mobUses(upperOf(lower)).isCancelled());
    }

    @Test
    void aVillagerCannotOpenALockedClosedTrapdoor() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);

        assertTrue(mobUses(block).isCancelled());
    }

    @Test
    void aVillagerOpensAnUnlockedDoorAsUsual() {
        Block lower = door(5, 5);

        assertFalse(mobUses(lower).isCancelled());
    }

    @Test
    void aMobMayShutAnOpenLockedDoor() {
        Block lower = door(5, 5, Material.OAK_DOOR, Door.Hinge.LEFT, true);
        lock(lower, true);

        assertFalse(mobUses(lower).isCancelled());
    }

    @Test
    void aMobThatOpenedALockedDoorThroughAGoalFindsItShutAgain() {
        Block lower = door(5, 5);
        lock(lower, true);
        setOpen(lower, true);

        mobOpened(lower, entityOf(EntityType.PILLAGER));

        assertFalse(((Door) lower.getBlockData()).isOpen());
    }

    @Test
    void aMobThatOpenedALockedTrapdoorThroughAGoalFindsItShutAgain() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);
        setTrapdoorOpen(block, true);

        mobOpened(block, entityOf(EntityType.PILLAGER));

        assertFalse(((TrapDoor) block.getBlockData()).isOpen());
    }

    @Test
    void aPlayerOpeningALockedDoorWithTheKeyIsNotShutAgain() {
        Block lower = door(5, 5);
        lock(lower, true);
        setOpen(lower, true);

        mobOpened(lower, player);

        assertTrue(((Door) lower.getBlockData()).isOpen());
    }

    @Test
    void aSignalOrAnythingWithoutAnEntityIsNotTreatedAsAMob() {
        Block lower = door(5, 5);
        lock(lower, true);
        setOpen(lower, true);

        mobOpened(lower, null);

        assertTrue(((Door) lower.getBlockData()).isOpen());
    }

    @Test
    void aMobOpeningAnUnlockedDoorIsLeftAlone() {
        Block lower = door(5, 5);
        setOpen(lower, true);

        mobOpened(lower, entityOf(EntityType.PILLAGER));

        assertTrue(((Door) lower.getBlockData()).isOpen());
    }

    @Test
    void otherGameEventsAreIgnored() {
        Block lower = door(5, 5);
        lock(lower, true);
        setOpen(lower, true);

        server.getPluginManager().callEvent(new GenericGameEvent(GameEvent.STEP, lower.getLocation(),
                entityOf(EntityType.PILLAGER), 16, false));

        assertTrue(((Door) lower.getBlockData()).isOpen());
    }

    // ---- pistons ----

    private BlockPistonExtendEvent extend(Block... moved) {
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(world.getBlockAt(5, 70, 5),
                new ArrayList<>(List.of(moved)), BlockFace.EAST);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private BlockPistonRetractEvent retract(Block... moved) {
        BlockPistonRetractEvent event = new BlockPistonRetractEvent(world.getBlockAt(5, 70, 5),
                new ArrayList<>(List.of(moved)), BlockFace.EAST);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aPistonDoesNothingToALockedDoor() {
        Block lower = door(5, 5);
        lock(lower, true);

        assertTrue(extend(lower, upperOf(lower)).isCancelled());
        assertTrue(retract(upperOf(lower)).isCancelled());
    }

    @Test
    void aPistonDoesNothingToALockedTrapdoor() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);

        assertTrue(extend(block).isCancelled());
        assertTrue(retract(block).isCancelled());
    }

    @Test
    void aPistonDoesNothingToALockedChest() {
        Block chest = world.getBlockAt(5, 64, 5);
        chest.setType(Material.CHEST);
        plugin.storage().write(List.of(chest), new LockData(UUID.randomUUID(), true));

        assertTrue(extend(chest).isCancelled());
    }

    @Test
    void aPistonCannotMoveTheBlockUnderALockedDoor() {
        Block lower = door(5, 5);
        lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);

        assertTrue(extend(floor).isCancelled());
        assertTrue(retract(floor).isCancelled());
    }

    @Test
    void pistonsStillMoveUnlockedThingsAndOtherBlocks() {
        Block lower = door(5, 5);
        Block trapdoor = trapdoor(7, 5, Bisected.Half.BOTTOM, false);
        Block stone = world.getBlockAt(9, 64, 5);
        stone.setType(Material.STONE);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);

        assertFalse(extend(lower, upperOf(lower), trapdoor, stone, floor).isCancelled());
        assertFalse(retract(lower, trapdoor, stone).isCancelled());
    }

    @Test
    void theBlockAboveALockedDoorIsNotItsSupport() {
        Block lower = door(5, 5);
        lock(lower, true);
        Block ceiling = upperOf(upperOf(lower));
        ceiling.setType(Material.STONE);

        assertFalse(extend(ceiling).isCancelled());
    }

    // ---- the displays arrive with the door (state guessed at the click, checked on the next tick) ----

    private List<UUID> displayIds() {
        return displays().stream().map(Entity::getUniqueId).toList();
    }

    @Test
    void aClickThatWillOpenTheDoorTakesTheDisplaysAwayInTheSameTick() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        assertEquals(2, displays().size());

        rightClick(lower);

        assertTrue(displays().isEmpty(), "gone before the door opens");
    }

    @Test
    void whenTheDoorReallyShutsTheGuessStandsAndNothingIsRecreated() {
        Block lower = door(5, 5, Material.OAK_DOOR, Door.Hinge.LEFT, true);
        lock(lower, true);
        assertTrue(displays().isEmpty());

        rightClick(lower);
        List<UUID> guessed = displayIds();
        setOpen(lower, false);
        server.getScheduler().performOneTick();

        assertEquals(2, guessed.size());
        assertEquals(guessed, displayIds());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    @Test
    void whenTheDoorDidNotToggleTheNextTickPutsTheDisplaysBack() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));

        rightClick(lower);
        server.getScheduler().performOneTick();

        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    @Test
    void sneakingWithAnItemInTheHandGuessesNothing() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(keyFor(data.id()));
        List<UUID> before = displayIds();

        rightClick(lower);

        assertEquals(before, displayIds());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    @Test
    void aRefusedClickLeavesTheDisplaysAlone() {
        Block lower = door(5, 5);
        lock(lower, true);
        List<UUID> before = displayIds();

        assertTrue(rightClick(lower).isCancelled());

        assertEquals(before, displayIds());
    }

    @Test
    void shuttingAnOpenLockedDoorBringsTheDisplaysBackAtTheClick() {
        Block lower = door(5, 5, Material.OAK_DOOR, Door.Hinge.LEFT, true);
        lock(lower, true);

        rightClick(lower);

        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    @Test
    void aSignalThatShutsAnOpenLockedDoorBringsTheDisplaysBackAtOnce() {
        Block lower = door(5, 5, Material.OAK_DOOR, Door.Hinge.LEFT, true);
        lock(lower, true);
        assertTrue(displays().isEmpty());

        server.getPluginManager().callEvent(new BlockRedstoneEvent(lower, 15, 0));

        assertEquals(2, displays().size());
        assertTrue(displays().stream().allMatch(d -> drawnFor(d) == 0));
    }

    @Test
    void aNeighbourUpdateWithoutAChangeOfSignalRecreatesNothing() {
        Block lower = door(5, 5);
        lock(lower, true);
        List<UUID> before = displayIds();
        assertEquals(2, before.size());

        server.getPluginManager().callEvent(new BlockRedstoneEvent(lower, 15, 15));
        server.getScheduler().performOneTick();

        assertEquals(before, displayIds());
    }

    // ---- trapdoors have their own display settings ----

    private void trapdoorScale(double scale) throws ReflectiveOperationException {
        Settings old = plugin.settings();
        DisplaySettings own = new DisplaySettings(0, 0, 0.02, scale, 0, 0, 0);
        Settings changed = new Settings(old.language(), old.provider(), old.vanillaItems(), old.nexoIds(),
                old.display(), old.doorDisplay(), own, old.explosionSources(), old.itemLore());
        java.lang.reflect.Field field = OutlandsLocks.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, changed);
    }

    @Test
    void aTrapdoorTakesTheScaleOfItsOwnSettingsAndADoorTheirs() throws Exception {
        trapdoorScale(0.5);
        Block trapdoor = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        Block lower = door(7, 5);

        lock(trapdoor, true);
        lock(lower, true);

        for (ItemDisplay display : displays()) {
            int[] at = display.getPersistentDataContainer().get(plugin.keys().block(), PersistentDataType.INTEGER_ARRAY);
            float scale = display.getTransformation().getScale().x();
            assertEquals(at[0] == 5 ? 0.5f : 0.3f, scale, 1e-6f);
        }
        assertEquals(4, displays().size());
    }

    @Test
    void theShippedTrapdoorSettingsAreTheDefaults() {
        assertEquals(DisplaySettings.TRAPDOOR_DEFAULT, plugin.settings().trapdoorDisplay());
    }

    // ---- a door whose floor is gone: the server destroys it, unless it is locked ----

    private BlockDestroyEvent destroy(Block block) {
        BlockDestroyEvent event = new BlockDestroyEvent(block, Material.AIR.createBlockData(), block.getBlockData(),
                0, true);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Stands in for another plugin that has the last word, after our HIGH handlers ran. */
    private static final class LateCancel implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
        public void onBreak(BlockBreakEvent event) {
            event.setCancelled(true);
        }

        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
        public void onPlace(BlockPlaceEvent event) {
            event.setCancelled(true);
        }
    }

    private List<org.bukkit.entity.Item> droppedItems() {
        return world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().toList();
    }

    @Test
    void theServerDoesNotDestroyEitherHalfOfALockedDoor() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);

        assertTrue(destroy(lower).isCancelled());
        assertTrue(destroy(upperOf(lower)).isCancelled());

        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
    }

    @Test
    void theServerDoesNotDestroyALockedTrapdoorEither() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        LockData data = lock(block, true);

        assertTrue(destroy(block).isCancelled());

        assertEquals(Optional.of(data), plugin.storage().read(block));
        assertEquals(2, displays().size());
    }

    @Test
    void unlockedDoorsAndTrapdoorsAndOtherBlocksAreDestroyedAsUsual() {
        Block lower = door(5, 5);
        Block trapdoor = trapdoor(7, 5, Bisected.Half.BOTTOM, false);
        Block stone = world.getBlockAt(9, 64, 5);
        stone.setType(Material.STONE);
        Block iron = door(11, 5, Material.IRON_DOOR, Door.Hinge.LEFT, false);

        for (Block block : List.of(lower, upperOf(lower), trapdoor, stone, iron, upperOf(iron))) {
            assertFalse(destroy(block).isCancelled(), block.getType().name());
        }
    }

    @Test
    void aFloorLostToACreeperLeavesTheLockedDoorStanding() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);

        EntityExplodeEvent blast = explode(EntityType.CREEPER, floor);
        floor.setType(Material.AIR);

        assertEquals(List.of(floor), blast.blockList(), "the floor is not a locked block, the blast takes it");
        assertTrue(destroy(lower).isCancelled());
        assertTrue(destroy(upperOf(lower)).isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
    }

    @Test
    void aFloorLostToABedExplosionLeavesTheLockedDoorStanding() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        Block bed = world.getBlockAt(20, 64, 20);
        bed.setType(Material.RED_BED);
        BlockExplodeEvent blast = new BlockExplodeEvent(bed, bed.getState(), new ArrayList<>(List.of(floor)), 1f,
                ExplosionResult.DESTROY);

        server.getPluginManager().callEvent(blast);
        floor.setType(Material.AIR);

        assertEquals(List.of(floor), blast.blockList());
        assertTrue(destroy(lower).isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
    }

    @Test
    void aFloorBurntAwayLeavesTheLockedDoorStanding() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.OAK_PLANKS);

        BlockBurnEvent burn = new BlockBurnEvent(floor, null);
        server.getPluginManager().callEvent(burn);
        floor.setType(Material.AIR);

        assertFalse(burn.isCancelled(), "the plugin does not guard planks");
        assertTrue(destroy(lower).isCancelled());
        assertTrue(destroy(upperOf(lower)).isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
    }

    @Test
    void afterAnExplosionOfAnAllowedSourceTheOtherHalfOfTheDoorIsLetGo() {
        Block lower = door(5, 5);
        lock(lower, true);

        explode(EntityType.TNT, lower);

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertFalse(destroy(upperOf(lower)).isCancelled());
        assertFalse(destroy(lower).isCancelled());
    }

    @Test
    void theSameWhenOnlyTheUpperHalfIsInTheBlast() {
        Block lower = door(5, 5);
        lock(lower, true);

        explode(EntityType.TNT, upperOf(lower));

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertFalse(destroy(lower).isCancelled());
        assertFalse(destroy(upperOf(lower)).isCancelled());
    }

    @Test
    void afterACreativeBreakOfOneHalfTheOtherHalfIsLetGo() {
        player.setGameMode(GameMode.CREATIVE);
        Block first = door(5, 5);
        Block second = door(7, 5);
        lock(first, true);
        lock(second, true);

        server.getPluginManager().callEvent(new BlockBreakEvent(first, player));
        server.getPluginManager().callEvent(new BlockBreakEvent(upperOf(second), player));

        assertFalse(destroy(upperOf(first)).isCancelled());
        assertFalse(destroy(second).isCancelled());
        assertEquals(2, droppedItems().size(), "one lock for each door that was broken");
    }

    @Test
    void breakingTheFloorInCreativeLetsTheDoorGoAndTheLockDrops() {
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(floor, player);

        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
        List<org.bukkit.entity.Item> dropped = droppedItems();
        assertEquals(1, dropped.size());
        assertEquals(data, LockData.read(dropped.get(0).getItemStack().getItemMeta().getPersistentDataContainer(),
                plugin.keys()).orElseThrow());
        assertFalse(destroy(lower).isCancelled());
        assertFalse(destroy(upperOf(lower)).isCancelled());
    }

    @Test
    void breakingTheFloorWithTheBypassPermissionLetsTheDoorGoToo() {
        Block lower = door(5, 5);
        lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        player.addAttachment(plugin, "outlands.locks.bypass", true);

        server.getPluginManager().callEvent(new BlockBreakEvent(floor, player));

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertEquals(1, droppedItems().size());
        assertFalse(destroy(lower).isCancelled());
    }

    @Test
    void aFloorBreakThatAnotherPluginCancelsLaterKeepsTheLockOnTheDoor() {
        server.getPluginManager().registerEvents(new LateCancel(), plugin);
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        Block floor = lower.getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(floor, player);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
        assertTrue(droppedItems().isEmpty());
    }

    @Test
    void aDoorBreakThatAnotherPluginCancelsLaterKeepsItsLockAndDisplays() {
        server.getPluginManager().registerEvents(new LateCancel(), plugin);
        Block lower = door(5, 5);
        LockData data = lock(lower, true);
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(upperOf(lower), player);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
        assertTrue(droppedItems().isEmpty());
    }

    private void adminRuns(PlayerMock admin, Block target, String subcommand) {
        plugin.getCommand("outlands-locks").setExecutor(new OutlandsLocksCommand(plugin, who -> target));
        plugin.getCommand("outlands-locks").execute(admin, "outlands-locks", new String[]{subcommand});
    }

    @Test
    void afterAnAdminTakesTheLockOffTheDoorIsLetGo() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        Block destroyed = door(5, 5);
        Block removed = door(7, 5);
        lock(destroyed, true);
        lock(removed, true);

        adminRuns(admin, upperOf(destroyed), "destroy");
        adminRuns(admin, removed, "remove");

        for (Block lower : List.of(destroyed, removed)) {
            assertTrue(plugin.storage().read(lower).isEmpty());
            assertFalse(destroy(lower).isCancelled());
            assertFalse(destroy(upperOf(lower)).isCancelled());
        }
    }

    // ---- a lock left on a spot is taken off only when the placement is final ----

    private BlockPlaceEvent place(Block placed, Material item) {
        BlockPlaceEvent event = new BlockPlaceEvent(placed, placed.getState(), placed.getRelative(BlockFace.DOWN),
                new ItemStack(item), player, true);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aDoorPlacedWhereALockWasLeftShowsNoLock() {
        Block lower = door(5, 5);
        lock(lower, true);
        assertEquals(2, displays().size(), "the displays of the door that was here");

        place(lower, Material.OAK_DOOR);

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void theSameWhenTheEventNamesTheUpperHalfOfTheDoor() {
        Block lower = door(5, 5);
        lock(lower, true);

        place(upperOf(lower), Material.OAK_DOOR);

        assertTrue(plugin.storage().read(lower).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void aTrapdoorPlacedWhereALockWasLeftShowsNoLock() {
        Block block = trapdoor(5, 5, Bisected.Half.BOTTOM, false);
        lock(block, true);

        place(block, Material.OAK_TRAPDOOR);

        assertTrue(plugin.storage().read(block).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void theLockOfAnotherDoorIsNotTouchedWhenADoorIsPlaced() {
        Block placed = door(5, 5);
        Block other = door(7, 5);
        LockData data = lock(other, true);

        place(placed, Material.OAK_DOOR);

        assertEquals(Optional.of(data), plugin.storage().read(other));
        assertEquals(2, displays().size());
    }

    @Test
    void aPlacementThatAnotherPluginCancelsLaterLeavesTheOldLockAlone() {
        server.getPluginManager().registerEvents(new LateCancel(), plugin);
        Block lower = door(5, 5);
        LockData data = lock(lower, true);

        BlockPlaceEvent event = place(lower, Material.OAK_DOOR);

        assertTrue(event.isCancelled());
        assertEquals(Optional.of(data), plugin.storage().read(lower));
        assertEquals(2, displays().size());
    }
}
