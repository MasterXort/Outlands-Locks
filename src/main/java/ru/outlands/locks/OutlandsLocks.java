package ru.outlands.locks;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.outlands.locks.command.OutlandsLocksCommand;
import ru.outlands.locks.config.ConfigUpdater;
import ru.outlands.locks.config.Settings;
import ru.outlands.locks.display.DisplayReconciler;
import ru.outlands.locks.display.LockDisplays;
import ru.outlands.locks.item.ItemProvider;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.item.VanillaItemProvider;
import ru.outlands.locks.lang.Language;
import ru.outlands.locks.lang.Messages;
import ru.outlands.locks.listener.DisplayChunkListener;
import ru.outlands.locks.listener.KeyCopyListener;
import ru.outlands.locks.listener.KeyListener;
import ru.outlands.locks.listener.LockAutomationListener;
import ru.outlands.locks.listener.LockBindListener;
import ru.outlands.locks.listener.LockBreakListener;
import ru.outlands.locks.listener.LockDoorListener;
import ru.outlands.locks.listener.LockExplosionListener;
import ru.outlands.locks.listener.LockInstallListener;
import ru.outlands.locks.listener.LockPlaceListener;
import ru.outlands.locks.listener.LockRemoveListener;
import ru.outlands.locks.lock.ItemLore;
import ru.outlands.locks.lock.KeyMatcher;
import ru.outlands.locks.lock.LockAccess;
import ru.outlands.locks.lock.LockItems;
import ru.outlands.locks.lock.LockKeys;
import ru.outlands.locks.lock.LockStorage;
import ru.outlands.locks.nexo.NexoItemProvider;
import ru.outlands.locks.papi.PapiSupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class OutlandsLocks extends JavaPlugin {

    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "ru");
    private static final Pattern LANGUAGE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private Settings settings;
    private Language language;
    private Messages messages;
    private ItemProvider items;
    private LockKeys keys;
    private LockStorage storage;
    private LockDisplays displays;
    private DisplayReconciler displayReconciler;
    private KeyMatcher keyMatcher;
    private LockAccess access;
    private LockItems lockItems;
    private ItemLore itemLore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String name : BUNDLED_LANGUAGES) {
            if (!new File(getDataFolder(), "languages/" + name + ".yml").exists()) {
                saveResource("languages/" + name + ".yml", false);
            }
        }
        addMissingEntries();

        try {
            settings = Settings.parse(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        language = loadLanguageOrBuiltin(settings.language());
        Function<Player, UnaryOperator<String>> papi =
                getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? PapiSupport::resolverFor : null;
        messages = new Messages(() -> language, papi);

        items = createItemProvider(settings);
        if (items == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        keys = new LockKeys(this);
        storage = new LockStorage(keys);
        displays = new LockDisplays(keys, () -> settings.display(), () -> settings.doorDisplay(), () -> settings.trapdoorDisplay(),
                () -> items.create(LockItemType.LOCK, 1));
        keyMatcher = new KeyMatcher(items, keys);
        access = new LockAccess(keyMatcher);
        displayReconciler = new DisplayReconciler(storage, displays, DisplayReconciler::lockableTiles,
                getLogger()::warning);
        itemLore = new ItemLore(() -> settings.itemLore(), () -> language);
        lockItems = new LockItems(items, keys, itemLore);
        getServer().getPluginManager().registerEvents(new LockBindListener(this), this);
        getServer().getPluginManager().registerEvents(new LockInstallListener(this), this);
        getServer().getPluginManager().registerEvents(new KeyCopyListener(this), this);
        getServer().getPluginManager().registerEvents(new KeyListener(this), this);
        getServer().getPluginManager().registerEvents(new LockRemoveListener(this), this);
        getServer().getPluginManager().registerEvents(new LockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new LockExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new LockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new LockDoorListener(this), this);
        getServer().getPluginManager().registerEvents(new LockAutomationListener(this), this);
        getServer().getPluginManager().registerEvents(new DisplayChunkListener(this), this);

        OutlandsLocksCommand command = new OutlandsLocksCommand(this);
        getCommand("outlands-locks").setExecutor(command);
        getCommand("outlands-locks").setTabCompleter(command);
    }

    /**
     * A new version of the plugin may bring keys the files of the server do not have yet. They are added as text, with
     * their comments, to config.yml and to the bundled language files; the files of other languages stay as they are.
     */
    private void addMissingEntries() {
        addMissingEntries("config.yml");
        for (String name : BUNDLED_LANGUAGES) {
            addMissingEntries("languages/" + name + ".yml");
        }
    }

    private void addMissingEntries(String resource) {
        Path file = new File(getDataFolder(), resource).toPath();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = getResource(resource)) {
            if (in != null) {
                ConfigUpdater.update(file, new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        getLogger()::info, getLogger()::warning);
            }
        } catch (IOException e) {
            getLogger().warning(resource + ": not updated: " + e.getMessage());
        }
    }

    public enum ReloadResult { RELOADED, PROVIDER_RESTART_NEEDED, FAILED }

    /**
     * Re-reads config.yml and the selected language file. Everything is read and checked before anything is applied,
     * so any error leaves the previous settings in place. Placed locks, keys and displays are not touched; a changed
     * item-provider needs a restart and is ignored.
     */
    public ReloadResult reload() {
        Settings parsed;
        Language newLanguage;
        try {
            parsed = Settings.parse(readConfig());
            newLanguage = readLanguage(parsed.language());
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            getLogger().severe("Reload failed, keeping the previous settings: " + e.getMessage());
            return ReloadResult.FAILED;
        }

        boolean providerChanged = parsed.provider() != settings.provider();
        Settings applied = providerChanged
                ? new Settings(parsed.language(), settings.provider(), settings.vanillaItems(), settings.nexoIds(),
                        parsed.display(), parsed.doorDisplay(), parsed.trapdoorDisplay(),
                        parsed.explosionSources(),
                        parsed.itemLore())
                : parsed;
        ItemProvider rebuilt = createItemProvider(applied);
        if (rebuilt == null) {
            getLogger().severe("Reload failed, keeping the previous settings: the item provider cannot be built.");
            return ReloadResult.FAILED;
        }

        settings = applied;
        language = newLanguage;
        items = rebuilt;
        keyMatcher = new KeyMatcher(items, keys);
        access = new LockAccess(keyMatcher);
        lockItems = new LockItems(items, keys, itemLore);
        if (providerChanged) {
            getLogger().warning("item-provider changed in config.yml; it applies after a server restart.");
            return ReloadResult.PROVIDER_RESTART_NEEDED;
        }
        return ReloadResult.RELOADED;
    }

    /** config.yml as it is now on disk, with the bundled one as defaults (as getConfig() does). */
    private YamlConfiguration readConfig() throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        try (InputStream in = getResource("config.yml")) {
            if (in != null) {
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        }
        return config;
    }

    private ItemProvider createItemProvider(Settings from) {
        if (from.provider() == Settings.ProviderKind.NEXO) {
            if (!getServer().getPluginManager().isPluginEnabled("Nexo")) {
                getLogger().severe("item-provider is 'nexo' but Nexo is not installed. Disabling the plugin.");
                return null;
            }
            return new NexoItemProvider(from.nexoIds());
        }
        return new VanillaItemProvider(from.vanillaItems(), new NamespacedKey(this, "item_type"),
                type -> MiniMessage.miniMessage().deserialize(language.itemName(type)));
    }

    private Language loadLanguageOrBuiltin(String name) {
        try {
            return readLanguage(name);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Cannot load language '" + name + "': " + e.getMessage() + ". Using built-in English.");
            try {
                return readLanguage("en");
            } catch (IOException | InvalidConfigurationException inner) {
                throw new IllegalStateException("Built-in language is broken", inner);
            }
        }
    }

    /** Reads languages/{name}.yml; keys missing there are taken from the built-in en.yml with a warning. */
    private Language readLanguage(String name) throws IOException, InvalidConfigurationException {
        if (!LANGUAGE_NAME.matcher(name).matches()) {
            throw new IOException("invalid language name");
        }
        File file = new File(getDataFolder(), "languages/" + name + ".yml");
        if (!file.isFile()) {
            throw new FileNotFoundException(file.getName() + " not found in the languages folder");
        }
        YamlConfiguration selected = new YamlConfiguration();
        selected.load(file);

        YamlConfiguration builtin = new YamlConfiguration();
        try (InputStream in = getResource("languages/en.yml")) {
            if (in == null) {
                throw new FileNotFoundException("built-in en.yml");
            }
            builtin.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return Language.load(name, selected, builtin, getLogger()::warning);
    }

    public Settings settings() {
        return settings;
    }

    public Language language() {
        return language;
    }

    public Messages messages() {
        return messages;
    }

    public ItemProvider items() {
        return items;
    }

    public LockKeys keys() {
        return keys;
    }

    public LockStorage storage() {
        return storage;
    }

    public LockDisplays displays() {
        return displays;
    }

    public DisplayReconciler displayReconciler() {
        return displayReconciler;
    }

    public KeyMatcher keyMatcher() {
        return keyMatcher;
    }

    public LockAccess access() {
        return access;
    }

    public LockItems lockItems() {
        return lockItems;
    }
}
