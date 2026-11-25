package ru.outlands.locks.lock;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

import java.util.List;

/** Resolves the block(s) of one chest: the clicked block alone, or both halves of a double chest. */
public final class ChestHalves {

    private ChestHalves() {
    }

    /** The given block first; the partner second when it is a double chest. A door is its lower block alone. */
    public static List<Block> of(Block block) {
        if (LockableBlocks.isHinged(block.getType())) {
            return List.of(DoorBlocks.lower(block));
        }
        if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
            return List.of(block);
        }
        BlockFace facing = chest.getFacing();
        for (BlockFace side : sides(facing)) {
            Block other = block.getRelative(side);
            if (LockableBlocks.isLockable(other.getType()) && isPartner(chest, other.getBlockData())) {
                return List.of(block, other);
            }
        }
        return List.of(block);
    }

    /** The two faces a chest facing the given way can join a neighbour on. */
    public static BlockFace[] sides(BlockFace facing) {
        return new BlockFace[]{rotateLeft(facing), rotateRight(facing)};
    }

    private static boolean isPartner(Chest chest, BlockData data) {
        return data instanceof Chest other
                && other.getFacing() == chest.getFacing()
                && other.getType() != Chest.Type.SINGLE
                && other.getType() != chest.getType();
    }

    private static BlockFace rotateLeft(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace rotateRight(BlockFace f) {
        return rotateLeft(f).getOppositeFace();
    }
}
