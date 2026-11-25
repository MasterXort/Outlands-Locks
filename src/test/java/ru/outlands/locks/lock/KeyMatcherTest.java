package ru.outlands.locks.lock;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.LockItemType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyMatcherTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private KeyMatcher matcher;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        matcher = plugin.keyMatcher();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack key(LockItemType type, String lockId) {
        ItemStack stack = plugin.items().create(type, 1).orElseThrow();
        if (lockId != null) {
            ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, lockId);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Test
    void keyWithTheSameLockIdMatches() {
        UUID id = UUID.randomUUID();
        assertTrue(matcher.matches(key(LockItemType.KEY, id.toString()), new LockData(id, true)));
    }

    @Test
    void keyMatchesRegardlessOfKeyCut() {
        UUID id = UUID.randomUUID();
        assertTrue(matcher.matches(key(LockItemType.KEY, id.toString()), new LockData(id, false)));
    }

    @Test
    void keyWithAnotherLockIdDoesNotMatch() {
        assertFalse(matcher.matches(key(LockItemType.KEY, UUID.randomUUID().toString()),
                new LockData(UUID.randomUUID(), true)));
    }

    @Test
    void keyWithoutLockIdDoesNotMatch() {
        assertFalse(matcher.matches(key(LockItemType.KEY, null), new LockData(UUID.randomUUID(), true)));
    }

    @Test
    void blankKeyWithTheLockIdDoesNotMatch() {
        UUID id = UUID.randomUUID();
        assertFalse(matcher.matches(key(LockItemType.BLANK_KEY, id.toString()), new LockData(id, true)));
    }

    @Test
    void usedLockWithTheLockIdDoesNotMatch() {
        UUID id = UUID.randomUUID();
        assertFalse(matcher.matches(key(LockItemType.LOCK, id.toString()), new LockData(id, true)));
    }

    @Test
    void emptyAndNullStacksDoNotMatch() {
        LockData data = new LockData(UUID.randomUUID(), true);
        assertFalse(matcher.matches(null, data));
        assertFalse(matcher.matches(new ItemStack(Material.AIR), data));
    }

    @Test
    void heldByLooksAtBothHands() {
        PlayerMock player = server.addPlayer("Steve");
        UUID id = UUID.randomUUID();
        LockData data = new LockData(id, true);
        assertFalse(matcher.heldBy(player, data));

        player.getInventory().setItemInOffHand(key(LockItemType.KEY, id.toString()));
        assertTrue(matcher.heldBy(player, data));

        player.getInventory().setItemInOffHand(null);
        player.getInventory().setItemInMainHand(key(LockItemType.KEY, id.toString()));
        assertTrue(matcher.heldBy(player, data));
    }

    @Test
    void keyInTheHotbarIsNotEnough() {
        PlayerMock player = server.addPlayer("Steve");
        UUID id = UUID.randomUUID();
        player.getInventory().setItem(3, key(LockItemType.KEY, id.toString()));
        assertFalse(matcher.heldBy(player, new LockData(id, true)));
    }
}
