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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A lock whose key was never cut is tied to a key: sneak + right click, lock in the main hand, key in the off hand. */
class LockBindTest {

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

    private ItemStack newLock(int amount) {
        return plugin.items().create(LockItemType.LOCK, amount).orElseThrow();
    }

    private ItemStack keyFor(UUID id) {
        return plugin.lockItems().key(id).orElseThrow();
    }

    private void hold(ItemStack main, ItemStack off) {
        player.getInventory().setItemInMainHand(main);
        player.getInventory().setItemInOffHand(off);
    }

    private PlayerInteractEvent click(Action action, Block block, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, held, block, BlockFace.NORTH, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent clickAir() {
        return click(Action.RIGHT_CLICK_AIR, null, EquipmentSlot.HAND);
    }

    /** A click in the air starts out "cancelled" in Bukkit; what a plugin changes is the item use. */
    private static boolean denied(PlayerInteractEvent event) {
        return event.useItemInHand() == Event.Result.DENY;
    }

    private Block chest() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        block.setBlockData(data);
        return block;
    }

    private Optional<LockData> dataOf(ItemStack lock) {
        return LockData.read(lock.getItemMeta().getPersistentDataContainer(), plugin.keys());
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private ItemStack bound(UUID id) {
        return plugin.lockItems().boundLock(newLock(1), id);
    }

    // ---- the binding ----

    @Test
    void aLooseLockIsTiedToTheKeyInTheOffHand() {
        UUID id = UUID.randomUUID();
        hold(newLock(1), keyFor(id));

        PlayerInteractEvent event = clickAir();

        ItemStack main = player.getInventory().getItemInMainHand();
        assertEquals(LockItemType.LOCK, plugin.items().identify(main).orElse(null));
        assertEquals(Optional.of(new LockData(id, true)), dataOf(main));
        assertEquals(1, main.getAmount());
        assertTrue(denied(event));
        assertEquals("You tied the lock to the key.", plain(player.nextActionBar()));
        assertEquals(1, player.getHeardSounds().size());
    }

    @Test
    void theKeyIsKept() {
        UUID id = UUID.randomUUID();
        hold(newLock(1), keyFor(id));

        clickAir();

        assertEquals(keyFor(id), player.getInventory().getItemInOffHand());
    }

    @Test
    void oneLockOfAStackIsTiedAndTheRestStaysLoose() {
        UUID id = UUID.randomUUID();
        hold(newLock(3), keyFor(id));

        clickAir();

        ItemStack main = player.getInventory().getItemInMainHand();
        assertEquals(2, main.getAmount());
        assertTrue(dataOf(main).isEmpty());
        List<ItemStack> tied = Arrays.stream(player.getInventory().getContents())
                .filter(s -> s != null && plugin.items().identify(s).orElse(null) == LockItemType.LOCK
                        && dataOf(s).isPresent()).toList();
        assertEquals(1, tied.size());
        assertEquals(new LockData(id, true), dataOf(tied.get(0)).orElseThrow());
    }

    @Test
    void theTiedLockDropsAtTheFeetWhenTheInventoryIsFull() {
        UUID id = UUID.randomUUID();
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
        }
        hold(newLock(2), keyFor(id));

        clickAir();

        List<Item> dropped = world.getEntitiesByClass(Item.class).stream().toList();
        assertEquals(1, dropped.size());
        assertEquals(Optional.of(new LockData(id, true)), dataOf(dropped.get(0).getItemStack()));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void theNameOfTheLockSurvives() {
        hold(newLock(1), keyFor(UUID.randomUUID()));
        ItemStack named = player.getInventory().getItemInMainHand();
        ItemMeta meta = named.getItemMeta();
        meta.customName(Component.text("Storage"));
        named.setItemMeta(meta);
        player.getInventory().setItemInMainHand(named);

        clickAir();

        assertEquals("Storage", plain(player.getInventory().getItemInMainHand().getItemMeta().customName()));
    }

    @Test
    void aUsedLockOfAnUncutChestIsTiedToTheNewKeyToo() {
        UUID id = UUID.randomUUID();
        ItemStack used = plugin.lockItems().usedLock(new LockData(UUID.randomUUID(), false)).orElseThrow();
        hold(used, keyFor(id));

        clickAir();

        assertEquals(Optional.of(new LockData(id, true)), dataOf(player.getInventory().getItemInMainHand()));
    }

    @Test
    void itWorksOnABlockAndLeavesAnUnlockedChestAlone() {
        UUID id = UUID.randomUUID();
        Block block = chest();
        hold(newLock(1), keyFor(id));

        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        assertTrue(denied(event));
        assertEquals(Optional.of(new LockData(id, true)), dataOf(player.getInventory().getItemInMainHand()));
        assertTrue(plugin.storage().read(block).isEmpty());
    }

    @Test
    void theNextSneakClickHangsTheTiedLockSoTheKeyOpensIt() {
        UUID id = UUID.randomUUID();
        Block block = chest();
        hold(newLock(1), keyFor(id));
        click(Action.RIGHT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        click(Action.RIGHT_CLICK_BLOCK, block, EquipmentSlot.HAND);

        assertEquals(Optional.of(new LockData(id, true)), plugin.storage().read(block));
        assertTrue(plugin.keyMatcher().matches(keyFor(id), plugin.storage().read(block).orElseThrow()));
    }

    // ---- when nothing happens ----

    @Test
    void aTiedLockIsNotTiedAgain() {
        ItemStack tied = bound(UUID.randomUUID());
        hold(tied, keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = clickAir();

        assertFalse(denied(event));
        assertEquals(tied, player.getInventory().getItemInMainHand());
    }

    @Test
    void withoutSneakingNothingHappens() {
        player.setSneaking(false);
        hold(newLock(1), keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = clickAir();

        assertFalse(denied(event));
        assertTrue(dataOf(player.getInventory().getItemInMainHand()).isEmpty());
    }

    @Test
    void withoutAKeyInTheOffHandNothingHappens() {
        hold(newLock(1), null);

        assertFalse(denied(clickAir()));
        assertTrue(dataOf(player.getInventory().getItemInMainHand()).isEmpty());
    }

    @Test
    void aKeyWithoutALockIdDoesNotTie() {
        hold(newLock(1), plugin.items().create(LockItemType.KEY, 1).orElseThrow());

        assertFalse(denied(clickAir()));
        assertTrue(dataOf(player.getInventory().getItemInMainHand()).isEmpty());
    }

    @Test
    void anOrdinaryItemInTheOffHandDoesNotTie() {
        hold(newLock(1), new ItemStack(Material.GOLD_NUGGET));

        assertFalse(denied(clickAir()));
        assertTrue(dataOf(player.getInventory().getItemInMainHand()).isEmpty());
    }

    @Test
    void anOrdinaryItemOfTheLockMaterialIsNotALock() {
        hold(new ItemStack(Material.IRON_INGOT), keyFor(UUID.randomUUID()));

        assertFalse(denied(clickAir()));
        assertEquals(Material.IRON_INGOT, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void theKeyInTheMainHandAndTheLockInTheOffHandDoNotTie() {
        hold(keyFor(UUID.randomUUID()), newLock(1));

        assertFalse(denied(clickAir()));
        assertTrue(dataOf(player.getInventory().getItemInOffHand()).isEmpty());
    }

    @Test
    void theOffHandEventIsIgnored() {
        hold(newLock(1), keyFor(UUID.randomUUID()));

        assertFalse(denied(click(Action.RIGHT_CLICK_AIR, null, EquipmentSlot.OFF_HAND)));
        assertTrue(dataOf(player.getInventory().getItemInMainHand()).isEmpty());
    }

    @Test
    void aCancelledEventIsSkipped() {
        hold(newLock(1), keyFor(UUID.randomUUID()));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.NORTH, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertTrue(dataOf(player.getInventory().getItemInMainHand()).isEmpty());
        assertNull(player.nextActionBar());
    }

    // ---- the permission to use locks ----

    @Test
    void withoutThePermissionTheLockIsNotTiedAndTheClickIsStillRefused() {
        player.addAttachment(plugin, "outlands.locks.use", false);
        hold(newLock(2), keyFor(UUID.randomUUID()));

        PlayerInteractEvent event = clickAir();

        assertTrue(denied(event));
        ItemStack main = player.getInventory().getItemInMainHand();
        assertEquals(2, main.getAmount());
        assertTrue(dataOf(main).isEmpty());
        assertEquals("You are not allowed to use locks and keys.", plain(player.nextActionBar()));
        assertEquals(0, player.getHeardSounds().size());
    }
}
