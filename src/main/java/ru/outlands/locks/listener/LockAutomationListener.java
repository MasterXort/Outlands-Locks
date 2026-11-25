package ru.outlands.locks.listener;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.outlands.locks.OutlandsLocks;
import ru.outlands.locks.lock.DoorBlocks;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.List;

/**
 * Automation never reaches a locked object (PLAN 4.8): hoppers, hopper minecarts and droppers can neither pull from
 * nor push into a chest, a copper golem may not pick it as a target, and pistons leave locked blocks alone. The events
 * are frequent, so the checks stay small.
 */
public final class LockAutomationListener implements Listener {

    private final OutlandsLocks plugin;

    public LockAutomationListener(OutlandsLocks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (isLocked(event.getSource()) || isLocked(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    /** The event is not Cancellable: the target is refused with setAllowed(false). It covers taking and depositing. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGolemTarget(ItemTransportingEntityValidateTargetEvent event) {
        if (event.isAllowed() && LockableBlocks.isLockable(event.getBlock().getType())
                && plugin.storage().read(event.getBlock()).isPresent()) {
            event.setAllowed(false);
        }
    }

    /**
     * A piston that would move or break a locked block does nothing. The event lists the blocks that are pushed and
     * the ones that are broken (doors), so one check covers both. The block under a locked door counts too: moving it
     * would drop the door and lose the lock.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesLock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesLock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean touchesLock(List<Block> blocks) {
        for (Block block : blocks) {
            if (isLockedBlock(block)) {
                return true;
            }
            Block above = block.getRelative(BlockFace.UP);
            if (LockableBlocks.isDoor(above.getType()) && DoorBlocks.lower(above).getY() == above.getY()
                    && isLockedBlock(above)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLockedBlock(Block block) {
        return LockableBlocks.isLockable(block.getType()) && plugin.storage().read(block).isPresent();
    }

    private boolean isLocked(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof DoubleChest chest) {
            return isLockedHolder(chest.getLeftSide(false)) || isLockedHolder(chest.getRightSide(false));
        }
        return isLockedHolder(holder);
    }

    private boolean isLockedHolder(InventoryHolder holder) {
        return holder instanceof TileState tile && plugin.storage().read(tile).isPresent();
    }
}
