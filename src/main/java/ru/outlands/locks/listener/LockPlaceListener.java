package ru.outlands.locks.listener;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.ChestHalves;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest placed next to a locked one never joins it (PLAN 4.7): otherwise the new half would open the whole
 * inventory. It is forced to a single chest, and the locked neighbour stays single.
 */
public final class LockPlaceListener implements Listener {

    private final OutlandsLocks plugin;

    public LockPlaceListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!LockableBlocks.isLockable(placed.getType()) || !(placed.getBlockData() instanceof Chest chest)
                || chest.getType() == Chest.Type.SINGLE) {
            return;
        }
        List<Block> locked = lockedNeighbours(placed, chest);
        if (locked.isEmpty()) {
            return;
        }
        // No physics: a neighbour update would pair the two chests again.
        placed.setBlockData(single(chest), false);
        for (Block neighbour : locked) {
            Chest data = (Chest) neighbour.getBlockData();
            if (data.getType() != Chest.Type.SINGLE) {
                neighbour.setBlockData(single(data), false);
            }
        }
    }

    /** Locked chests beside the placed one that it could pair with: same facing, still single or already paired to it. */
    private List<Block> lockedNeighbours(Block placed, Chest chest) {
        List<Block> found = new ArrayList<>();
        for (BlockFace side : ChestHalves.sides(chest.getFacing())) {
            Block neighbour = placed.getRelative(side);
            if (LockableBlocks.isLockable(neighbour.getType())
                    && neighbour.getBlockData() instanceof Chest other
                    && other.getFacing() == chest.getFacing()
                    && (other.getType() == Chest.Type.SINGLE || other.getType() != chest.getType())
                    && plugin.storage().read(neighbour).isPresent()) {
                found.add(neighbour);
            }
        }
        return found;
    }

    private static Chest single(Chest chest) {
        Chest copy = (Chest) chest.clone();
        copy.setType(Chest.Type.SINGLE);
        return copy;
    }
}
