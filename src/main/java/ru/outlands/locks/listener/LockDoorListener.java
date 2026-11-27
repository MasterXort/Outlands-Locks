package ru.outlands.locks.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.block.Block;
import org.bukkit.GameEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.DoorBlocks;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.List;
import java.util.Optional;

/**
 * What only doors need on top of the shared lock rules: redstone and mobs cannot open a locked door, the lock display follows
 * the door when it opens and closes, the block a locked door stands on cannot be broken by hand, a locked door is not
 * destroyed when that block is lost some other way, and a door placed where an old lock was left does not inherit it.
 */
public final class LockDoorListener implements Listener {

    private final OutlandsLocks plugin;

    public LockDoorListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    /**
     * A signal on a locked, closed door or trapdoor is turned down, so it stays shut. One that stands open (a key
     * opened it) is left alone, and so is a signal going away; when such a block changes, its displays follow on the
     * next tick.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (!LockableBlocks.isHinged(block.getType()) || plugin.storage().read(block).isEmpty()) {
            return;
        }
        if (event.getNewCurrent() > 0 && !DoorBlocks.isOpen(block)) {
            event.setNewCurrent(0);
            return;
        }
        Block lower = DoorBlocks.lower(block);
        boolean powered = event.getNewCurrent() > 0;
        if (powered != event.getOldCurrent() > 0) {
            // The signal changed: the block follows it (opens on a signal, shuts when it goes), draw for that now.
            plugin.storage().read(lower).ifPresent(data -> plugin.displays().syncDoor(lower, data, powered));
        }
        syncNextTick(lower);
    }

    private void syncNextTick(Block lower) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.storage().read(lower).ifPresent(data -> plugin.displays().syncDoor(lower, data)));
    }

    /**
     * Villagers, piglins and other mobs that open doors through their brain ask first with this event. A locked closed
     * door or trapdoor is not theirs to open; shutting an open one is allowed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobUse(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (event.getEntity() instanceof Player || !LockableBlocks.isHinged(block.getType())
                || DoorBlocks.isOpen(block) || plugin.storage().read(block).isEmpty()) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Mobs that open doors through a goal (illagers and others) fire no event of their own, only the game event "block
     * open", after the door has opened. A mob never opens a locked door: the door is shut again at once.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMobOpened(GenericGameEvent event) {
        Entity entity = event.getEntity();
        if (event.getEvent() != GameEvent.BLOCK_OPEN || entity == null || entity instanceof Player) {
            return;
        }
        Block block = event.getLocation().getBlock();
        if (!LockableBlocks.isHinged(block.getType()) || plugin.storage().read(block).isEmpty()) {
            return;
        }
        Block lower = DoorBlocks.lower(block);
        if (lower.getBlockData() instanceof Openable hinged && hinged.isOpen()) {
            hinged.setOpen(false);
            lower.setBlockData(hinged);
        }
    }

    /**
     * A click that went through toggles the door right after this handler. The displays are drawn for the new state at
     * once, so they arrive with the door instead of a tick or two behind it, and checked on the next tick against the
     * real state. Sneaking with something in a hand does not toggle a door in vanilla, so nothing is guessed then.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || !LockableBlocks.isHinged(block.getType()) || plugin.storage().read(block).isEmpty()) {
            return;
        }
        Block lower = DoorBlocks.lower(block);
        Player player = event.getPlayer();
        boolean sneakingWithAnItem = player.isSneaking() && (!player.getInventory().getItemInMainHand().getType().isAir()
                || !player.getInventory().getItemInOffHand().getType().isAir());
        if (!sneakingWithAnItem) {
            boolean willBeOpen = !DoorBlocks.isOpen(lower);
            plugin.storage().read(lower).ifPresent(data -> plugin.displays().syncDoor(lower, data, willBeOpen));
        }
        syncNextTick(lower);
    }

    /**
     * A door placed on a spot that still holds the lock of a door that is gone starts out unlocked and shows no lock:
     * the old entry and the displays of the old lock go. Once the placement is final, so a placement that another
     * plugin cancels leaves things as they were.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!LockableBlocks.isHinged(placed.getType())) {
            return;
        }
        Optional<LockData> old = plugin.storage().read(placed);
        if (old.isPresent()) {
            List<Block> lower = List.of(DoorBlocks.lower(placed));
            plugin.storage().clear(lower);
            plugin.displays().remove(lower, old.get().id());
        }
    }

    /**
     * Breaking the block under a locked door would drop the door and lose the lock, so it is refused by hand
     * (creative and the bypass permission break it as they break the door).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakSupport(BlockBreakEvent event) {
        Block above = event.getBlock().getRelative(BlockFace.UP);
        if (!isDoorFoot(above)) {
            return;
        }
        Optional<LockData> stored = plugin.storage().read(above);
        Player player = event.getPlayer();
        if (stored.isPresent() && !plugin.access().canBreak(player)) {
            event.setCancelled(true);
            plugin.messages().send(player, "break-denied", LockPlaceholders.of(player, above, stored.get()));
        }
    }

    /**
     * A door goes with the block it stands on, so when creative or the bypass permission breaks that block the lock is
     * released the way it is when they break the door itself: it comes off, its display goes and it drops as an item.
     * This is done once the break is final and before the block is removed. Left on, the lock would make
     * {@link #onDestroy} keep the door standing in the air.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSupportBroken(BlockBreakEvent event) {
        Block above = event.getBlock().getRelative(BlockFace.UP);
        if (isDoorFoot(above)) {
            plugin.storage().read(above).ifPresent(data -> LockRelease.release(plugin, above, data));
        }
    }

    /**
     * A door that loses the block it stands on is destroyed by the server, and its lock with it, whatever took the
     * block: a creeper or a bed, a fire, a falling block. Hand breaks and pistons are stopped elsewhere, this stops
     * the rest. A locked door or trapdoor (either half of a door) stays where it is, lock and display with it.
     *
     * <p>Everything the plugin lets go of takes the lock off first, before the block is removed: an explosion of an
     * allowed source, a creative or bypass break of the door or of its floor, the admin commands. So this never keeps
     * a door whose lock was taken off, and the second half of a door that follows the first is not held either.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        Block block = event.getBlock();
        if (LockableBlocks.isHinged(block.getType()) && plugin.storage().read(block).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** The lower half of a wooden door: the one that rests on the block under it. */
    private static boolean isDoorFoot(Block block) {
        return LockableBlocks.isDoor(block.getType()) && DoorBlocks.lower(block).getY() == block.getY();
    }
}
