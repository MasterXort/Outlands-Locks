package ru.outlands.locks.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.item.LockItemType;
import ru.outlands.locks.listener.LockPlaceholders;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OutlandsLocksCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "outlands.locks.admin";

    /** Subcommands that act on the chest the executor looks at. */
    private static final List<String> TARGET_COMMANDS = List.of("inspect", "remove", "destroy", "copykey", "copylock");
    private static final List<String> SUBCOMMANDS = List.of("inspect", "remove", "destroy", "copykey", "copylock",
            "give", "reload");
    private static final List<LockItemType> GIVABLE = List.of(LockItemType.LOCK, LockItemType.BLANK_KEY);
    private static final int GIVE_LIMIT = 36 * 64;

    private final OutlandsLocks plugin;
    private final TargetFinder targets;

    public OutlandsLocksCommand(OutlandsLocks plugin) {
        this(plugin, TargetFinder.lineOfSight());
    }

    public OutlandsLocksCommand(OutlandsLocks plugin, TargetFinder targets) {
        this.plugin = plugin;
        this.targets = targets;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.messages().send(sender, "admin-no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give")) {
            give(sender, args);
        } else if (sub.equals("reload") && args.length == 1) {
            reload(sender);
        } else if (TARGET_COMMANDS.contains(sub) && args.length == 1) {
            onTarget(sender, sub);
        } else {
            plugin.messages().send(sender, "admin-usage");
        }
        return true;
    }

    private void reload(CommandSender sender) {
        switch (plugin.reload()) {
            case FAILED -> plugin.messages().send(sender, "admin-reload-failed");
            case RELOADED -> plugin.messages().send(sender, "admin-reloaded");
            case PROVIDER_RESTART_NEEDED -> {
                plugin.messages().send(sender, "admin-reloaded");
                plugin.messages().send(sender, "admin-reload-provider-restart");
            }
        }
    }

    // ---- commands on the chest the executor looks at ----

    private void onTarget(CommandSender sender, String sub) {
        Block block = sender instanceof Player player ? targets.lookingAt(player) : null;
        if (block == null || !LockableBlocks.isLockable(block.getType())) {
            plugin.messages().send(sender, "admin-no-target");
            return;
        }
        Optional<LockData> stored = plugin.storage().read(block);
        if (stored.isEmpty()) {
            plugin.messages().send(sender, "admin-not-locked");
            return;
        }
        Player player = (Player) sender;
        LockData data = stored.get();
        Map<String, String> placeholders = LockPlaceholders.of(player, block, data);
        switch (sub) {
            case "inspect" -> plugin.messages().send(player, "admin-inspect", placeholders);
            case "remove" -> removeLock(player, block, data, placeholders);
            case "destroy" -> {
                eraseLock(block, data);
                plugin.messages().send(player, "admin-destroyed", placeholders);
            }
            case "copykey" -> handOver(player, plugin.lockItems().key(data.id()), "admin-copykey", placeholders);
            case "copylock" -> handOver(player, plugin.lockItems().usedLock(data), "admin-copylock", placeholders);
            default -> throw new IllegalStateException(sub);
        }
    }

    private void removeLock(Player player, Block block, LockData data, Map<String, String> placeholders) {
        Optional<ItemStack> lock = plugin.lockItems().usedLock(data);
        if (lock.isEmpty()) {
            plugin.messages().send(player, "admin-item-unavailable");
            return;
        }
        eraseLock(block, data);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(at, lock.get());
        plugin.messages().send(player, "admin-removed", placeholders);
    }

    /** Clears the lock data of every half and removes the display. */
    private void eraseLock(Block block, LockData data) {
        List<Block> halves = ChestHalves.of(block);
        plugin.storage().clear(halves);
        plugin.displays().remove(halves, data.id());
    }

    /** Gives the item to the executor; whatever does not fit falls at their feet. */
    private void handOver(Player player, Optional<ItemStack> item, String message, Map<String, String> placeholders) {
        if (item.isEmpty()) {
            plugin.messages().send(player, "admin-item-unavailable");
            return;
        }
        for (ItemStack left : player.getInventory().addItem(item.get()).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
        plugin.messages().send(player, message, placeholders);
    }

    // ---- give ----

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            plugin.messages().send(sender, "admin-usage");
            return;
        }
        Optional<LockItemType> type = LockItemType.byCommandName(args[2]).filter(GIVABLE::contains);
        int amount = args.length == 4 ? parseAmount(args[3]) : 1;
        if (type.isEmpty() || amount < 1) {
            plugin.messages().send(sender, "admin-usage");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "admin-player-not-found");
            return;
        }
        Optional<ItemStack> probe = stackOf(type.get(), 1);
        if (probe.isEmpty()) {
            plugin.messages().send(sender, "admin-item-unavailable");
            return;
        }

        int maxStack = Math.max(1, probe.get().getMaxStackSize());
        for (int left = amount; left > 0; left -= maxStack) {
            ItemStack stack = stackOf(type.get(), Math.min(left, maxStack)).orElseThrow();
            for (ItemStack overflow : target.getInventory().addItem(stack).values()) {
                target.getWorld().dropItem(target.getLocation(), overflow);
            }
        }

        String itemName = MiniMessage.miniMessage().stripTags(plugin.language().itemName(type.get()));
        plugin.messages().send(sender, "admin-give", Map.of(
                "item", itemName,
                "amount", String.valueOf(amount),
                "player", target.getName()));
    }

    /** A lock comes with the lore of a lock that is not set up (when the lore is on); a blank key is plain. */
    private Optional<ItemStack> stackOf(LockItemType type, int amount) {
        return type == LockItemType.LOCK ? plugin.lockItems().newLock(amount) : plugin.items().create(type, amount);
    }

    private static int parseAmount(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value > GIVE_LIMIT ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- tab complete ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(SUBCOMMANDS, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 3) {
                return matching(GIVABLE.stream().map(LockItemType::commandName).toList(), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> matching(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
