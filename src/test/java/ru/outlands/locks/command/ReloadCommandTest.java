package ru.outlands.locks.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.entity.EntityExplodeEvent;
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
import ru.outlands.locks.config.Settings;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PLAN 5a: /outlands-locks reload reads and checks the new files first, and keeps the old settings on any error. */
class ReloadCommandTest {

    private ServerMock server;
    private OutlandsLocks plugin;
    private WorldMock world;
    private PlayerMock admin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OutlandsLocks.class);
        world = server.addSimpleWorld("world");
        admin = server.addPlayer("Admin");
        admin.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String reply() {
        Component message = admin.nextComponentMessage();
        return message == null ? null : plain(message);
    }

    private void run(CommandSender sender, String... args) {
        Command command = plugin.getCommand("outlands-locks");
        assertNotNull(command);
        command.execute(sender, "outlands-locks", args);
    }

    private void reload() {
        run(admin, "reload");
    }

    private File configFile() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    private File languageFile(String name) {
        return new File(plugin.getDataFolder(), "languages/" + name + ".yml");
    }

    private void replaceIn(File file, String from, String to) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(text.contains(from), "the file has no '" + from + "'");
        Files.writeString(file.toPath(), text.replace(from, to), StandardCharsets.UTF_8);
    }

    private void write(File file, String text) throws IOException {
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private Block chest() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(BlockFace.NORTH);
        block.setBlockData(data);
        return block;
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

    private ItemStack keyFor(LockData data) {
        ItemStack key = plugin.items().create(LockItemType.KEY, 1).orElseThrow();
        var meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.keys().lockId(), PersistentDataType.STRING, data.id().toString());
        key.setItemMeta(meta);
        return key;
    }

    private List<ItemDisplay> displays() {
        return new ArrayList<>(world.getEntitiesByClass(ItemDisplay.class));
    }

    // ---- success ----

    @Test
    void reloadAppliesANewLanguage() throws IOException {
        replaceIn(configFile(), "language: en", "language: ru");
        reload();
        assertEquals("Конфиг и локализация перезагружены.", reply());
        assertEquals("ru", plugin.settings().language());
        assertEquals("<red>Посмотрите на запертый объект.", plugin.language().message("admin-no-target").text());
    }

    @Test
    void reloadAppliesEditedTexts() throws IOException {
        replaceIn(languageFile("en"), "Config and language files reloaded.", "All fresh.");
        reload();
        assertEquals("All fresh.", reply());
    }

    @Test
    void reloadAppliesEditedItemNamesToNewItems() throws IOException {
        replaceIn(languageFile("en"), "Blank Key", "Raw Key");
        reload();
        ItemStack blank = plugin.items().create(LockItemType.BLANK_KEY, 1).orElseThrow();
        assertEquals("Raw Key", plain(blank.getItemMeta().customName()));
    }

    @Test
    void reloadWithoutChangesJustConfirms() {
        Settings before = plugin.settings();
        reload();
        assertEquals("Config and language files reloaded.", reply());
        assertEquals(before, plugin.settings());
    }

    @Test
    void theConsoleCanReload() throws IOException {
        replaceIn(configFile(), "language: en", "language: ru");
        run(server.getConsoleSender(), "reload");
        assertEquals("ru", plugin.settings().language());
    }

    @Test
    void reloadAppliesNewItemMaterialsAndKeepsOldItemsWorking() throws IOException {
        Block chest = chest();
        LockData data = lock(chest);
        ItemStack oldKey = keyFor(data);
        assertEquals(Material.GOLD_NUGGET, oldKey.getType());

        replaceIn(configFile(), "material: GOLD_NUGGET", "material: COPPER_INGOT");
        replaceIn(configFile(), "material: IRON_INGOT", "material: BRICK");
        reload();

        assertEquals("Config and language files reloaded.", reply());
        assertEquals(Material.COPPER_INGOT, plugin.items().create(LockItemType.KEY, 1).orElseThrow().getType());
        assertEquals(Optional.of(LockItemType.KEY), plugin.items().identify(oldKey),
                "items are recognised by the marker, so keys made before the reload still work");
        assertTrue(plugin.keyMatcher().matches(oldKey, data));
        ItemStack newKey = keyFor(data);
        assertEquals(Material.COPPER_INGOT, newKey.getType());
        assertTrue(plugin.keyMatcher().matches(newKey, data));
        assertEquals(Material.BRICK, plugin.lockItems().usedLock(data).orElseThrow().getType());
        assertEquals(Material.COPPER_INGOT, plugin.lockItems().key(data.id()).orElseThrow().getType());
    }

    @Test
    void reloadLeavesPlacedLocksAndTheirDisplaysAlone() throws IOException {
        Block chest = chest();
        LockData data = lock(chest);
        ItemDisplay display = displays().get(0);
        UUID displayId = display.getUniqueId();
        float scale = display.getTransformation().getScale().x();

        replaceIn(configFile(), "scale: 0.4", "scale: 1.5");
        replaceIn(configFile(), "up: 0.1", "up: 0.3");
        reload();

        assertEquals(Optional.of(data), plugin.storage().read(chest));
        assertEquals(1, displays().size());
        assertEquals(displayId, displays().get(0).getUniqueId(), "the display is not rebuilt");
        assertEquals(scale, displays().get(0).getTransformation().getScale().x(), 1e-6);
    }

    @Test
    void newDisplaySettingsApplyToDisplaysCreatedAfterTheReload() throws IOException {
        replaceIn(configFile(), "scale: 0.4", "scale: 1.5");
        reload();
        lock(chest());
        assertEquals(1.5f, displays().get(0).getTransformation().getScale().x(), 1e-6);
    }

    @Test
    void newExplosionSourcesApplyToTheNextExplosion() throws IOException {
        Block chest = chest();
        lock(chest);
        assertTrue(explode(EntityType.CREEPER, chest).blockList().isEmpty(), "a creeper spares the chest by default");

        replaceIn(configFile(), "allowed-sources: [TNT, TNT_MINECART]", "allowed-sources: [CREEPER]");
        reload();

        assertEquals(List.of(chest), explode(EntityType.CREEPER, chest).blockList());
        lock(chest);
        assertTrue(explode(EntityType.TNT, chest).blockList().isEmpty(), "TNT is no longer on the list");
    }

    // ---- item-provider ----

    @Test
    void aChangedItemProviderIsNotAppliedAndTheAdminIsTold() throws IOException {
        replaceIn(configFile(), "item-provider: vanilla", "item-provider: nexo");
        replaceIn(configFile(), "scale: 0.4", "scale: 1.5");
        reload();

        assertEquals("Config and language files reloaded.", reply());
        assertEquals("item-provider change will apply after a server restart.", reply());
        assertEquals(Settings.ProviderKind.VANILLA, plugin.settings().provider());
        assertTrue(plugin.items().create(LockItemType.LOCK, 1).isPresent());
        assertEquals(Material.IRON_INGOT, plugin.items().create(LockItemType.LOCK, 1).orElseThrow().getType());
        assertEquals(1.5, plugin.settings().display().scale(), 1e-6, "the rest of the file is applied");
    }

    @Test
    void theProviderWarningRepeatsOnEveryReloadWhileTheFileDiffers() throws IOException {
        replaceIn(configFile(), "item-provider: vanilla", "item-provider: nexo");
        reload();
        reply();
        reply();
        reload();
        assertEquals("Config and language files reloaded.", reply());
        assertEquals("item-provider change will apply after a server restart.", reply());
    }

    @Test
    void anUnchangedProviderGivesNoWarning() {
        reload();
        assertEquals("Config and language files reloaded.", reply());
        assertEquals(null, reply());
    }

    @Test
    void aProviderChangeWithoutTheNewSectionFails() throws IOException {
        replaceIn(configFile(), "item-provider: vanilla", "item-provider: nexo");
        replaceIn(configFile(), "lock: lock", "lock: \"\"");
        Settings before = plugin.settings();
        reload();
        assertEquals("Reload failed, previous settings kept. See console for details.", reply());
        assertSame(before, plugin.settings());
    }

    // ---- errors keep the old settings ----

    private void assertKeptOldSettings(Settings before, String language) {
        assertEquals("Reload failed, previous settings kept. See console for details.", reply());
        assertSame(before, plugin.settings());
        assertTrue(plugin.isEnabled());
        assertEquals("Look at a locked object.", plugin.language().message("admin-no-target").text()
                .replaceAll("<[^>]*>", ""));
        assertEquals(language, plugin.settings().language());
    }

    @Test
    void brokenYamlInConfigKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        write(configFile(), "language: [unclosed\nitem-provider: vanilla: : :\n");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void brokenYamlInTheLanguageFileKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "language: en", "language: ru");
        write(languageFile("ru"), "messages: [unclosed\n  key: : :\n");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void aMissingLanguageFileKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "language: en", "language: klingon");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void aBadLanguageNameKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "language: en", "language: ../config");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void aBadMaterialKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "material: GOLD_NUGGET", "material: NOT_A_MATERIAL");
        replaceIn(configFile(), "scale: 0.4", "scale: 1.5");
        reload();
        assertKeptOldSettings(before, "en");
        assertEquals(Material.GOLD_NUGGET, plugin.items().create(LockItemType.KEY, 1).orElseThrow().getType());
        lock(chest());
        assertEquals(0.4f, displays().get(0).getTransformation().getScale().x(), 1e-6,
                "a half-applied reload would show the new scale");
    }

    @Test
    void aBadProviderNameKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "item-provider: vanilla", "item-provider: banana");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void aBadDisplayValueKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "scale: 0.4", "scale: -1");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void aBadExplosionSourceKeepsTheOldSettings() throws IOException {
        Settings before = plugin.settings();
        replaceIn(configFile(), "allowed-sources: [TNT, TNT_MINECART]", "allowed-sources: [NOT_A_MOB]");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void aFailedReloadDoesNotTouchThePlacedLocks() throws IOException {
        Block chest = chest();
        LockData data = lock(chest);
        replaceIn(configFile(), "material: GOLD_NUGGET", "material: COPPER_INGOT");
        replaceIn(configFile(), "scale: 0.4", "scale: -1");
        reload();
        reply();
        assertEquals(Optional.of(data), plugin.storage().read(chest));
        assertEquals(1, displays().size());
    }

    @Test
    void reloadWorksAgainAfterAFailedOne() throws IOException {
        String good = Files.readString(configFile().toPath(), StandardCharsets.UTF_8);
        write(configFile(), "language: [unclosed\n");
        reload();
        assertTrue(reply().startsWith("Reload failed"));

        write(configFile(), good.replace("language: en", "language: ru"));
        reload();
        assertEquals("Конфиг и локализация перезагружены.", reply());
        assertEquals("ru", plugin.settings().language());
    }

    @Test
    void anEmptyConfigFileFailsTheWayItDoesAtStartup() throws IOException {
        Settings before = plugin.settings();
        write(configFile(), "");
        reload();
        assertKeptOldSettings(before, "en");
    }

    @Test
    void missingKeysComeFromTheBundledConfig() throws IOException {
        replaceIn(configFile(), "scale: 0.4", "");
        reload();
        assertEquals("Config and language files reloaded.", reply());
        assertEquals(0.4, plugin.settings().display().scale(), 1e-6);
    }

    private EntityExplodeEvent explode(EntityType source, Block... blocks) {
        Entity entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> source;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Entity[" + source + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        EntityExplodeEvent event = new EntityExplodeEvent(entity, new Location(world, 0, 64, 0),
                new ArrayList<>(List.of(blocks)), 1f, ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(event);
        return event;
    }
}
