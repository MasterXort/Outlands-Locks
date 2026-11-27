package ru.outlands.locks.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.display.ChunkRef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Restores and cleans up lock displays when chunks load (PLAN section 6).
 *
 * <p>A chunk's block entities and its entities load separately, and the javadoc does not promise an order, nor that
 * {@link EntitiesLoadEvent} fires for a chunk without entities. So both events only queue the chunk, and one task on
 * the next tick checks every queued chunk once; the check itself does nothing unless the chunk and its entities are
 * loaded, and it is safe to run twice. A chunk skipped for lack of a neighbour is queued again when that neighbour
 * loads. A chunk that unloads, or a world that does, stops waiting for anything: it is checked from the start when it
 * comes back.
 */
public final class DisplayChunkListener implements Listener {

    private final OutlandsLocks plugin;
    private final Set<ChunkRef> queued = new LinkedHashSet<>();
    private boolean drainScheduled;

    public DisplayChunkListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        queue(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        queue(event.getChunk());
    }

    /** The event cannot be cancelled: the chunk is going. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.displayReconciler().forgetWaiter(ChunkRef.of(event.getChunk()));
    }

    /** Only an unload that goes through counts: a cancelled one leaves the world, and what waits in it, as it was. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.displayReconciler().forgetWorld(event.getWorld().getUID());
    }

    private void queue(Chunk chunk) {
        ChunkRef ref = ChunkRef.of(chunk);
        queued.add(ref);
        queued.addAll(plugin.displayReconciler().takeWaiters(ref));
        if (!drainScheduled) {
            drainScheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::drain);
        }
    }

    private void drain() {
        drainScheduled = false;
        List<ChunkRef> batch = new ArrayList<>(queued);
        queued.clear();
        for (ChunkRef ref : batch) {
            World world = Bukkit.getWorld(ref.world());
            if (world == null || !world.isChunkLoaded(ref.x(), ref.z())) {
                continue;
            }
            try {
                plugin.displayReconciler().reconcile(world.getChunkAt(ref.x(), ref.z()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Lock display check failed in chunk " + ref.x() + ", " + ref.z()
                        + " of " + world.getName() + ": " + e);
            }
        }
    }
}
