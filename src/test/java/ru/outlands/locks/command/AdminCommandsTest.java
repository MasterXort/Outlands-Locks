package ru.outlands.locks.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.ItemProvider;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockItems;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PLAN 5a: inspect, remove, destroy, copykey, copylock. The block the player looks at is supplied by the test. */
class AdminCommandsTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;
    private PlayerMock admin;
    private PlayerMock steve;
    private Block target;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        steve = server.addPlayer("Steve");
        OutlandsLocksCommand command = new OutlandsLocksCommand(plugin, player -> target);
        plugin.getCommand("outlands-locks").setExecutor(command);
        plugin.getCommand("outlands-locks").setTabCompleter(command);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String reply(CommandSender sender) {
        Component message = sender instanceof PlayerMock p ? p.nextComponentMessage() : null;
        return message == null ? null : plain(message);
    }

    private void run(CommandSender sender, String... args) {
        Command command = plugin.getCommand("outlands-locks");
        assertNotNull(command);
        command.execute(sender, "outlands-locks", args);
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

    /** A double chest whose halves are at x and x + 1; the first of them is returned. */
    private Block doubleChest(int x) {
        Block first = chest(x, 0, Chest.Type.RIGHT);
        chest(x + 1, 0, Chest.Type.LEFT);
        return first;
    }

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

    private List<ItemDisplay> displays() {
        return new ArrayList<>(world.getEntitiesByClass(ItemDisplay.class));
    }

    private List<ItemStack> droppedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item item) {
                items.add(item.getItemStack());
            }
        }
        return items;
    }

    private List<ItemStack> inventoryOf(PlayerMock player) {
        return Arrays.stream(player.getInventory().getContents()).filter(s -> s != null && !s.getType().isAir()).toList();
    }

    private Optional<LockData> stored(Block block) {
        return plugin.storage().read(block);
    }

    private void breakLockItems() throws ReflectiveOperationException {
        ItemProvider broken = new ItemProvider() {
            @Override
            public Optional<LockItemType> identify(ItemStack stack) {
                return Optional.empty();
            }

            @Override
            public Optional<ItemStack> create(LockItemType type, int amount) {
                return Optional.empty();
            }
        };
        set("items", broken);
        set("lockItems", new LockItems(broken, plugin.keys(), new ru.outlands.locks.lock.ItemLore(() -> false, plugin::language)));
    }

    private void set(String field, Object value) throws ReflectiveOperationException {
        Field f = OutlandsLocks.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(plugin, value);
    }

    // ---- inspect ----

    @Test
    void inspectShowsIdKeyCutAndCoordinates() {
        target = chest();
        LockData data = lock(target, true);
        run(admin, "inspect");
        String text = reply(admin);
        assertTrue(text.contains("world 0 64 0"), text);
        assertTrue(text.contains("ID: " + data.id()), text);
        assertTrue(text.contains("Key cut: true"), text);
    }

    @Test
    void inspectReadsTheHalfThePlayerLooksAt() {
        Block first = doubleChest(0);
        LockData data = lock(first, false);
        target = first.getRelative(BlockFace.EAST);
        run(admin, "inspect");
        String text = reply(admin);
        assertTrue(text.contains("world 1 64 0"), text);
        assertTrue(text.contains(data.id().toString()), text);
        assertTrue(text.contains("Key cut: false"), text);
    }

    @Test
    void inspectChangesNothing() {
        target = chest();
        LockData data = lock(target, true);
        run(admin, "inspect");
        assertEquals(Optional.of(data), stored(target));
        assertEquals(1, displays().size());
        assertTrue(droppedItems().isEmpty());
    }

    @Test
    void anUnlockedChestIsAnError() {
        target = chest();
        run(admin, "inspect");
        assertEquals("There is no lock on this object.", reply(admin));
    }

    @Test
    void lookingAtNothingIsAnError() {
        target = null;
        for (String sub : List.of("inspect", "remove", "destroy", "copykey", "copylock")) {
            run(admin, sub);
            assertEquals("Look at a locked object.", reply(admin), sub);
        }
    }

    @Test
    void lookingAtAnotherBlockIsAnError() {
        target = world.getBlockAt(3, 64, 3);
        target.setType(Material.STONE);
        run(admin, "inspect");
        assertEquals("Look at a locked object.", reply(admin));
    }

    @Test
    void aCopperChestIsNotATarget() {
        target = world.getBlockAt(3, 64, 3);
        target.setType(Material.COPPER_CHEST);
        run(admin, "inspect");
        assertEquals("Look at a locked object.", reply(admin));
    }

    @Test
    void aTrappedChestWorksLikeAChest() {
        target = world.getBlockAt(2, 64, 2);
        target.setType(Material.TRAPPED_CHEST);
        LockData data = lock(target, false);
        run(admin, "inspect");
        assertTrue(reply(admin).contains(data.id().toString()));
    }

    @Test
    void theConsoleHasNoTarget() {
        target = chest();
        lock(target, true);
        for (String sub : List.of("inspect", "remove", "destroy", "copykey", "copylock")) {
            run(server.getConsoleSender(), sub);
        }
        assertEquals(1, displays().size());
        assertTrue(stored(target).isPresent());
        assertTrue(droppedItems().isEmpty());
    }

    // ---- remove ----

    @Test
    void removeDropsTheUsedLockAndClearsTheChest() {
        target = chest();
        LockData data = lock(target, true);
        run(admin, "remove");

        assertEquals("Lock removed and dropped nearby: world 0 64 0", reply(admin));
        assertTrue(stored(target).isEmpty());
        assertTrue(displays().isEmpty());
        List<ItemStack> dropped = droppedItems();
        assertEquals(1, dropped.size());
        assertEquals(Optional.of(LockItemType.LOCK), plugin.items().identify(dropped.get(0)));
        assertEquals(Optional.of(data), LockData.read(
                dropped.get(0).getItemMeta().getPersistentDataContainer(), plugin.keys()));
        assertTrue(inventoryOf(admin).isEmpty(), "the lock drops next to the chest, not into the inventory");
    }

    @Test
    void removeNeedsNoKey() {
        target = chest();
        lock(target, false);
        assertTrue(inventoryOf(admin).isEmpty());
        run(admin, "remove");
        assertTrue(stored(target).isEmpty());
    }

    @Test
    void removeClearsBothHalvesAndTheSingleDisplay() {
        Block first = doubleChest(0);
        LockData data = lock(first, false);
        assertEquals(1, displays().size());
        target = first.getRelative(BlockFace.EAST);
        run(admin, "remove");

        assertTrue(stored(first).isEmpty());
        assertTrue(stored(first.getRelative(BlockFace.EAST)).isEmpty());
        assertTrue(displays().isEmpty());
        assertEquals(1, droppedItems().size());
        assertEquals(Optional.of(data), LockData.read(
                droppedItems().get(0).getItemMeta().getPersistentDataContainer(), plugin.keys()));
    }

    @Test
    void removeLeavesOtherLocksAlone() {
        Block other = chest(5, 5, Chest.Type.SINGLE);
        LockData otherData = lock(other, true);
        target = chest();
        lock(target, false);
        run(admin, "remove");
        assertEquals(Optional.of(otherData), stored(other));
        assertEquals(1, displays().size());
    }

    @Test
    void removeWithAnUnbuildableLockItemChangesNothing() throws ReflectiveOperationException {
        target = chest();
        LockData data = lock(target, true);
        breakLockItems();
        run(admin, "remove");

        assertEquals("Item could not be created. Check the item settings in config.yml.", reply(admin));
        assertEquals(Optional.of(data), stored(target));
        assertEquals(1, displays().size());
        assertTrue(droppedItems().isEmpty());
    }

    // ---- destroy ----

    @Test
    void destroyErasesTheLockWithoutADrop() {
        target = doubleChest(0);
        lock(target, true);
        run(admin, "destroy");

        assertEquals("Lock destroyed: world 0 64 0", reply(admin));
        assertTrue(stored(target).isEmpty());
        assertTrue(stored(target.getRelative(BlockFace.EAST)).isEmpty());
        assertTrue(displays().isEmpty());
        assertTrue(droppedItems().isEmpty());
        assertTrue(inventoryOf(admin).isEmpty());
    }

    @Test
    void destroyWorksWhenTheItemProviderIsBroken() throws ReflectiveOperationException {
        target = chest();
        lock(target, true);
        breakLockItems();
        run(admin, "destroy");
        assertTrue(stored(target).isEmpty());
        assertTrue(displays().isEmpty());
    }

    @Test
    void destroyOnAnUnlockedChestIsAnError() {
        target = chest();
        run(admin, "destroy");
        assertEquals("There is no lock on this object.", reply(admin));
    }

    // ---- copykey ----

    @Test
    void copykeyGivesAKeyThatOpensTheLock() {
        target = chest();
        LockData data = lock(target, false);
        run(admin, "copykey");

        assertEquals("Key copy given for lock " + data.id(), reply(admin));
        List<ItemStack> held = inventoryOf(admin);
        assertEquals(1, held.size());
        assertEquals(Optional.of(LockItemType.KEY), plugin.items().identify(held.get(0)));
        assertTrue(plugin.keyMatcher().matches(held.get(0), data));
        assertEquals(Optional.of(data), stored(target), "the lock is not changed, key_cut stays as it was");
    }

    @Test
    void copykeyKeyEqualsTheOneCutOnTheChest() {
        target = chest();
        LockData data = lock(target, true);
        run(admin, "copykey");
        run(admin, "copykey");
        List<ItemStack> held = inventoryOf(admin);
        assertEquals(1, held.size(), "two copies stack");
        assertEquals(2, held.get(0).getAmount());
        assertEquals(data.id().toString(), held.get(0).getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().lockId(), PersistentDataType.STRING));
    }

    @Test
    void copykeyWithAFullInventoryDropsTheKeyAtTheFeet() {
        target = chest();
        lock(target, false);
        for (int i = 0; i < admin.getInventory().getContents().length; i++) {
            admin.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        run(admin, "copykey");
        List<ItemStack> dropped = droppedItems();
        assertEquals(1, dropped.size());
        assertEquals(Optional.of(LockItemType.KEY), plugin.items().identify(dropped.get(0)));
    }

    @Test
    void copykeyWithAnUnbuildableKeyChangesNothing() throws ReflectiveOperationException {
        target = chest();
        LockData data = lock(target, false);
        breakLockItems();
        run(admin, "copykey");
        assertEquals("Item could not be created. Check the item settings in config.yml.", reply(admin));
        assertTrue(inventoryOf(admin).isEmpty());
        assertEquals(Optional.of(data), stored(target));
    }

    // ---- copylock ----

    @Test
    void copylockGivesALockWithTheSameIdAndKeyCut() {
        target = chest();
        LockData data = lock(target, true);
        run(admin, "copylock");

        assertEquals("Lock copy given: " + data.id(), reply(admin));
        List<ItemStack> held = inventoryOf(admin);
        assertEquals(1, held.size());
        assertEquals(Optional.of(LockItemType.LOCK), plugin.items().identify(held.get(0)));
        assertEquals(Optional.of(data), LockData.read(held.get(0).getItemMeta().getPersistentDataContainer(),
                plugin.keys()));
        assertEquals(Optional.of(data), stored(target), "the original lock stays on the chest");
        assertEquals(1, displays().size());
    }

    @Test
    void copylockKeepsAKeyCutOfFalse() {
        target = chest();
        LockData data = lock(target, false);
        run(admin, "copylock");
        assertEquals(Optional.of(data), LockData.read(inventoryOf(admin).get(0).getItemMeta()
                .getPersistentDataContainer(), plugin.keys()));
    }

    @Test
    void copylockWithAFullInventoryDropsTheLockAtTheFeet() {
        target = chest();
        lock(target, false);
        for (int i = 0; i < admin.getInventory().getContents().length; i++) {
            admin.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        run(admin, "copylock");
        assertEquals(1, droppedItems().size());
    }

    @Test
    void copylockWithAnUnbuildableLockChangesNothing() throws ReflectiveOperationException {
        target = chest();
        LockData data = lock(target, false);
        breakLockItems();
        run(admin, "copylock");
        assertEquals("Item could not be created. Check the item settings in config.yml.", reply(admin));
        assertTrue(inventoryOf(admin).isEmpty());
        assertEquals(Optional.of(data), stored(target));
    }

    // ---- permissions ----

    @Test
    void everySubcommandNeedsThePermission() {
        target = chest();
        LockData data = lock(target, true);
        for (String sub : List.of("inspect", "remove", "destroy", "copykey", "copylock", "reload")) {
            run(steve, sub);
            assertEquals("You don't have permission.", reply(steve), sub);
        }
        assertEquals(Optional.of(data), stored(target));
        assertEquals(1, displays().size());
        assertTrue(inventoryOf(steve).isEmpty());
        assertTrue(droppedItems().isEmpty());
    }

    @Test
    void thePermissionAloneIsEnough() {
        steve.addAttachment(plugin, "outlands.locks.admin", true);
        target = chest();
        lock(target, true);
        run(steve, "inspect");
        assertTrue(reply(steve).contains("Key cut: true"));
    }

    // ---- usage ----

    @Test
    void unknownSubcommandShowsUsage() {
        run(admin, "frobnicate");
        assertEquals("/outlands-locks <inspect|remove|destroy|copykey|copylock|give|reload>", reply(admin));
    }

    @Test
    void extraArgumentsShowUsageAndChangeNothing() {
        target = chest();
        LockData data = lock(target, true);
        for (String sub : List.of("inspect", "remove", "destroy", "copykey", "copylock", "reload")) {
            run(admin, sub, "now");
            assertTrue(reply(admin).startsWith("/outlands-locks"), sub);
        }
        assertEquals(Optional.of(data), stored(target));
        assertTrue(inventoryOf(admin).isEmpty());
    }

    @Test
    void subcommandsAreCaseInsensitive() {
        target = chest();
        lock(target, true);
        run(admin, "INSPECT");
        assertTrue(reply(admin).contains("Key cut: true"));
    }

    // ---- tab complete ----

    private List<String> tab(CommandSender sender, String... args) {
        return plugin.getCommand("outlands-locks").tabComplete(sender, "outlands-locks", args);
    }

    @Test
    void tabCompleteOffersEverySubcommand() {
        assertEquals(List.of("inspect", "remove", "destroy", "copykey", "copylock", "give", "reload"), tab(admin, ""));
    }

    @Test
    void tabCompleteFiltersByPrefix() {
        assertEquals(List.of("copykey", "copylock"), tab(admin, "co"));
        assertEquals(List.of("copylock"), tab(admin, "copyl"));
        assertEquals(List.of("remove", "reload"), tab(admin, "RE"));
        assertEquals(List.of("reload"), tab(admin, "rel"));
        assertEquals(List.of(), tab(admin, "x"));
    }

    @Test
    void tabCompleteHasNoArgumentsForTheTargetSubcommands() {
        for (String sub : List.of("inspect", "remove", "destroy", "copykey", "copylock", "reload")) {
            assertEquals(List.of(), tab(admin, sub, ""), sub);
        }
    }

    @Test
    void tabCompleteOfGiveIsUnchanged() {
        assertEquals(List.of("Admin", "Steve"), tab(admin, "give", ""));
        assertEquals(List.of("lock", "blank_key"), tab(admin, "give", "Steve", ""));
        assertEquals(List.of(), tab(admin, "give", "Steve", "lock", ""), "the amount is not suggested");
    }

    @Test
    void tabCompleteOffersNothingWithoutThePermission() {
        assertEquals(List.of(), tab(steve, ""));
        assertEquals(List.of(), tab(steve, "co"));
        assertEquals(List.of(), tab(steve, "give", ""));
    }

    @Test
    void theConsoleGetsTabCompletion() {
        assertEquals(List.of("inspect", "remove", "destroy", "copykey", "copylock", "give", "reload"),
                tab(server.getConsoleSender(), ""));
    }
}
