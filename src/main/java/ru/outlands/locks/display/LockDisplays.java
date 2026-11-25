package ru.outlands.locks.display;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.outlands.locks.lock.DoorBlocks;
import ru.outlands.locks.lock.LockData;
import ru.outlands.locks.lock.LockKeys;
import ru.outlands.locks.lock.LockableBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Creates the ItemDisplays that show a lock: one on the front face of a chest, two on a closed door or trapdoor (one
 * on each face of the slab, none while it stands open).
 */
public final class LockDisplays {

    /** How far from the chest's centre a display is looked for, in blocks. */
    public static final int REACH = 2;

    /** The displays a door or trapdoor carries while it shows its lock. */
    public static final int DOOR_DISPLAYS = 2;

    /**
     * Whether locks are drawn on open doors and trapdoors too. Off for now (owner's decision 26.11.2026): the lock
     * shows on closed ones only and is taken away when the block opens. The open-state geometry and the state tag on
     * the displays are kept, so switching this on brings the drawing back.
     */
    private static final boolean DRAW_WHEN_OPEN = false;

    private final LockKeys keys;

    private final Supplier<DisplaySettings> settings;
    private final Supplier<DisplaySettings> doorSettings;
    private final Supplier<DisplaySettings> trapdoorSettings;
    private final Supplier<Optional<ItemStack>> lockItem;

    /**
     * @param settings         current chest display settings, read on every spawn so a reload applies to new displays
     * @param doorSettings     the same for doors
     * @param trapdoorSettings the same for trapdoors
     * @param lockItem         a fresh lock item to show; empty when the item provider cannot build one
     */
    public LockDisplays(LockKeys keys, Supplier<DisplaySettings> settings, Supplier<DisplaySettings> doorSettings,
                        Supplier<DisplaySettings> trapdoorSettings, Supplier<Optional<ItemStack>> lockItem) {
        this.keys = keys;
        this.settings = settings;
        this.doorSettings = doorSettings;
        this.trapdoorSettings = trapdoorSettings;
        this.lockItem = lockItem;
    }

    /**
     * Spawns one display for a chest: centred between the halves of a double chest.
     *
     * @param halves the chest block, plus its partner for a double chest; the door block for a door or trapdoor
     *               (both of its displays are spawned, drawn for its present state: see {@link #syncDoor})
     * @return the display (the first one for a door); empty when the display item cannot be built, and for an open
     *         door or trapdoor, which shows no lock
     */
    public Optional<ItemDisplay> spawn(List<Block> halves, LockData data) {
        Optional<ItemStack> item = lockItem.get();
        if (item.isEmpty()) {
            return Optional.empty();
        }
        Block first = halves.get(0);
        if (LockableBlocks.isHinged(first.getType())) {
            Block lower = DoorBlocks.lower(first);
            boolean open = DoorBlocks.isOpen(lower);
            return expected(open) == 0 ? Optional.empty() : spawnHinged(lower, data, item.get(), open);
        }
        BlockFace facing = first.getBlockData() instanceof Directional d ? d.getFacing() : BlockFace.NORTH;

        List<Vector> corners = new ArrayList<>();
        for (Block block : halves) {
            corners.add(new Vector(block.getX(), block.getY(), block.getZ()));
        }
        DisplayPlacement placement = DisplayGeometry.place(corners, facing, settings.get());
        return Optional.of(create(anchor(halves), item.get(), placement, data));
    }

    /** @param open the state to draw for: the block's own, or the one it is about to get */
    private Optional<ItemDisplay> spawnHinged(Block lower, LockData data, ItemStack item, boolean open) {
        Vector corner = new Vector(lower.getX(), lower.getY(), lower.getZ());
        List<DisplayPlacement> placements;
        if (lower.getBlockData() instanceof Door door) {
            placements = DoorGeometry.place(corner, door.getFacing(), door.getHinge() == Door.Hinge.RIGHT, open,
                    doorSettings.get());
        } else if (lower.getBlockData() instanceof TrapDoor trapdoor) {
            placements = TrapdoorGeometry.place(corner, trapdoor.getFacing(),
                    trapdoor.getHalf() == Bisected.Half.TOP, open, trapdoorSettings.get());
        } else {
            return Optional.empty();
        }
        ItemDisplay first = null;
        for (DisplayPlacement placement : placements) {
            ItemDisplay display = create(lower, item.clone(), placement, data);
            display.getPersistentDataContainer().set(keys.open(), PersistentDataType.BYTE, (byte) (open ? 1 : 0));
            if (first == null) {
                first = display;
            }
        }
        return Optional.ofNullable(first);
    }

    /**
     * Brings the displays of a locked door or trapdoor in line with its state: the pair while it is closed, none while
     * it stands open. Used after the block is toggled and when a lock is hung on it.
     *
     * @return false only when displays were needed and the lock item could not be built
     */
    public boolean syncDoor(Block lower, LockData data) {
        return syncDoor(lower, data, DoorBlocks.isOpen(lower));
    }

    /** How many displays a door or trapdoor carries in this state. */
    private static int expected(boolean open) {
        return open && !DRAW_WHEN_OPEN ? 0 : DOOR_DISPLAYS;
    }

    /**
     * The same for a state the block is about to get: a click or a signal is seen before the block changes, and the
     * displays are drawn for the new state in the same tick, so they do not lag behind the door. The caller checks the
     * guess on the next tick with {@link #syncDoor(Block, LockData)}.
     */
    public boolean syncDoor(Block lower, LockData data, boolean open) {
        List<ItemDisplay> present = find(List.of(lower), data.id());
        if (isInSync(present, open)) {
            return true;
        }
        present.forEach(ItemDisplay::remove);
        if (expected(open) == 0) {
            return true;
        }
        Optional<ItemStack> item = lockItem.get();
        return item.isPresent() && spawnHinged(lower, data, item.get(), open).isPresent();
    }

    /** True when the given displays are exactly what a door or trapdoor needs for its present state. */
    public boolean isInSync(List<ItemDisplay> present, Block lower) {
        return isInSync(present, DoorBlocks.isOpen(lower));
    }

    private boolean isInSync(List<ItemDisplay> present, boolean open) {
        if (present.size() != expected(open)) {
            return false;
        }
        byte state = (byte) (open ? 1 : 0);
        for (ItemDisplay display : present) {
            Byte drawn = display.getPersistentDataContainer().get(keys.open(), PersistentDataType.BYTE);
            if (drawn == null || drawn != state) {
                return false;
            }
        }
        return true;
    }

    private ItemDisplay create(Block anchor, ItemStack item, DisplayPlacement placement, LockData data) {
        World world = anchor.getWorld();
        Location at = new Location(world, placement.position().getX(), placement.position().getY(),
                placement.position().getZ(), placement.yaw(), placement.pitch());
        return world.spawn(at, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf().rotationYXZ(
                            (float) Math.toRadians(placement.extraYaw()),
                            (float) Math.toRadians(placement.extraPitch()),
                            (float) Math.toRadians(placement.extraRoll())),
                    new Vector3f(placement.scale()),
                    new Quaternionf()));
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(true);
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(keys.lockId(), PersistentDataType.STRING, data.id().toString());
            pdc.set(keys.block(), PersistentDataType.INTEGER_ARRAY,
                    new int[]{anchor.getX(), anchor.getY(), anchor.getZ()});
        });
    }

    /**
     * Removes the displays of one lock: those carrying this lock_id and the anchor block of the given chest.
     * Only loaded chunks are searched; a display in an unloaded chunk is left to the chunk-load cleanup.
     */
    public void remove(List<Block> halves, UUID lockId) {
        for (ItemDisplay display : find(halves, lockId)) {
            display.remove();
        }
    }

    /** What a display of this plugin says about itself: the lock it shows and the anchor block it hangs on. */
    public record Tag(String lockId, int x, int y, int z) {
    }

    /** Empty unless the entity is an ItemDisplay carrying both a lock_id and a three-number block. */
    public Optional<Tag> tagOf(Entity entity) {
        if (!(entity instanceof ItemDisplay)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String id = pdc.get(keys.lockId(), PersistentDataType.STRING);
        int[] at = pdc.get(keys.block(), PersistentDataType.INTEGER_ARRAY);
        if (id == null || at == null || at.length != 3) {
            return Optional.empty();
        }
        return Optional.of(new Tag(id, at[0], at[1], at[2]));
    }

    /**
     * The displays of one lock on one chest: this lock_id and the anchor block of the given halves, searched within
     * {@value #REACH} blocks of the chest's centre. Only entities of loaded chunks can be found.
     */
    public List<ItemDisplay> find(List<Block> halves, UUID lockId) {
        Block anchor = anchor(halves);
        Location center = centre(halves);
        String id = lockId.toString();
        List<ItemDisplay> found = new ArrayList<>();
        for (Entity entity : anchor.getWorld().getNearbyEntities(center, REACH, REACH, REACH,
                e -> e instanceof ItemDisplay && e.isValid())) {
            Optional<Tag> tag = tagOf(entity);
            if (tag.isPresent() && tag.get().lockId().equals(id)
                    && tag.get().x() == anchor.getX() && tag.get().y() == anchor.getY()
                    && tag.get().z() == anchor.getZ()) {
                found.add((ItemDisplay) entity);
            }
        }
        return found;
    }

    /** The middle of the chest: the centre of its block, or the point between the two halves of a double chest. */
    static Location centre(List<Block> halves) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Block block : halves) {
            x += block.getX() + 0.5;
            y += block.getY() + 0.5;
            z += block.getZ() + 0.5;
        }
        return new Location(halves.get(0).getWorld(), x / halves.size(), y / halves.size(), z / halves.size());
    }

    /** The same block for both halves, whichever one was clicked: the lowest x, then the lowest z. */
    static Block anchor(List<Block> halves) {
        Block best = halves.get(0);
        for (Block block : halves) {
            if (block.getX() < best.getX() || (block.getX() == best.getX() && block.getZ() < best.getZ())) {
                best = block;
            }
        }
        return best;
    }
}
