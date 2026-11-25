package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Explosions and mobs against locked chests (PLAN 4.9). Explosions of the configured entity types destroy the chest
 * and the lock with it; every other explosion drops the chest from the block list. Block explosions (bed, respawn
 * anchor) never break a locked chest.
 *
 * <p>Protection runs at HIGH, after Towny and Quarters. The lock is cleared at MONITOR, once the block list is final:
 * a chest another plugin spared keeps its lock.
 */
public final class LockExplosionListener implements Listener {

    private final OutlandsLocks plugin;

    public LockExplosionListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protect(EntityExplodeEvent event) {
        if (!plugin.settings().explosionSources().contains(event.getEntityType())) {
            event.blockList().removeIf(this::isLocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protect(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLocked);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void destroyLocks(EntityExplodeEvent event) {
        destroyLocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void destroyLocks(BlockExplodeEvent event) {
        destroyLocks(event.blockList());
    }

    /** The wither eats blocks through this event rather than through an explosion. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (isLocked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean isLocked(Block block) {
        return LockableBlocks.isLockable(block.getType()) && plugin.storage().read(block).isPresent();
    }

    /** Clears the lock of every locked chest still on the list, including the half of a double chest that survives. */
    private void destroyLocks(List<Block> destroyed) {
        for (Block block : new ArrayList<>(destroyed)) {
            if (!LockableBlocks.isLockable(block.getType())) {
                continue;
            }
            Optional<LockData> stored = plugin.storage().read(block);
            if (stored.isEmpty()) {
                continue;
            }
            List<Block> halves = ChestHalves.of(block);
            plugin.storage().clear(halves);
            plugin.displays().remove(halves, stored.get().id());
        }
    }
}
