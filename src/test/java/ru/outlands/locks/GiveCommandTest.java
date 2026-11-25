package ru.outlands.locks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ru.outlands.locks.item.LockItemType;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveCommandTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private PlayerMock admin;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        steve = server.addPlayer("Steve");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private int total(PlayerMock player, LockItemType type) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && plugin.items().identify(stack).equals(Optional.of(type))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void run(PlayerMock sender, String... args) {
        Command command = plugin.getCommand("outlands-locks");
        assertNotNull(command);
        command.execute(sender, "outlands-locks", args);
    }

    @Test
    void pluginEnablesInVanillaMode() {
        assertTrue(plugin.isEnabled());
    }

    @Test
    void giveLockPutsRecognisedItemsInInventory() {
        run(admin, "give", "Steve", "lock", "3");
        assertEquals(3, total(steve, LockItemType.LOCK));
        assertEquals("Given: Lock x3 → Steve", plain(admin.nextComponentMessage()));
    }

    @Test
    void amountDefaultsToOne() {
        run(admin, "give", "Steve", "blank_key");
        assertEquals(1, total(steve, LockItemType.BLANK_KEY));
    }

    @Test
    void amountAboveStackSizeIsSplit() {
        run(admin, "give", "Steve", "blank_key", "100");
        assertEquals(100, total(steve, LockItemType.BLANK_KEY));
    }

    @Test
    void keyCannotBeGivenDirectly() {
        run(admin, "give", "Steve", "key");
        assertEquals(0, total(steve, LockItemType.KEY));
        assertTrue(plain(admin.nextComponentMessage()).startsWith("/outlands-locks"));
    }

    @Test
    void unknownPlayerGetsMessage() {
        run(admin, "give", "Nobody", "lock");
        assertEquals("Player not found.", plain(admin.nextComponentMessage()));
    }

    @Test
    void badAmountShowsUsage() {
        run(admin, "give", "Steve", "lock", "0");
        run(admin, "give", "Steve", "lock", "abc");
        assertTrue(plain(admin.nextComponentMessage()).startsWith("/outlands-locks"));
        assertTrue(plain(admin.nextComponentMessage()).startsWith("/outlands-locks"));
        assertEquals(0, total(steve, LockItemType.LOCK));
    }

    @Test
    void playerWithoutPermissionIsDenied() {
        run(steve, "give", "Steve", "lock");
        assertEquals("You don't have permission.", plain(steve.nextComponentMessage()));
        assertEquals(0, total(steve, LockItemType.LOCK));
    }

    @Test
    void noArgumentsShowUsage() {
        run(admin);
        assertTrue(plain(admin.nextComponentMessage()).startsWith("/outlands-locks"));
    }

    @Test
    void consoleCanGive() {
        Command command = plugin.getCommand("outlands-locks");
        command.execute(server.getConsoleSender(), "outlands-locks", new String[]{"give", "Steve", "lock", "2"});
        assertEquals(2, total(steve, LockItemType.LOCK));
    }

    @Test
    void tabCompleteOffersPlayersAndItemTypes() {
        Command command = plugin.getCommand("outlands-locks");
        assertEquals(List.of("give"), command.tabComplete(admin, "outlands-locks", new String[]{"g"}));
        List<String> players = command.tabComplete(admin, "outlands-locks", new String[]{"give", "st"});
        assertEquals(List.of("Steve"), players);
        assertEquals(List.of("lock"), command.tabComplete(admin, "outlands-locks", new String[]{"give", "Steve", "lo"}));
        assertEquals(List.of("lock", "blank_key"), command.tabComplete(admin, "outlands-locks", new String[]{"give", "Steve", ""}));
    }

    @Test
    void tabCompleteIsEmptyWithoutPermission() {
        Command command = plugin.getCommand("outlands-locks");
        assertEquals(List.of(), command.tabComplete(steve, "outlands-locks", new String[]{""}));
    }
}
