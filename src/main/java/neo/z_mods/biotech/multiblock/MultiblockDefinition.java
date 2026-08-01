package neo.z_mods.biotech.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Supplier;

public record MultiblockDefinition(String id, List<Requirement> requirements) {
    public record Requirement(BlockPos offset, Supplier<? extends Block> block) {
    }

    public BlockPos rotateOffset(Direction facing, BlockPos offset) {
        return switch (facing) {
            case EAST -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case WEST -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset;
        };
    }

    public BlockPos worldPosition(BlockPos origin, Direction facing, BlockPos offset) {
        return origin.offset(rotateOffset(facing, offset));
    }

    public boolean validate(Level level, BlockPos origin, Direction facing) {
        for (Requirement requirement : requirements) {
            BlockPos world = worldPosition(origin, facing, requirement.offset());
            if (!level.getBlockState(world).is(requirement.block().get())) {
                return false;
            }
        }
        return true;
    }

    public Direction findValidRotation(Level level, BlockPos origin) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (validate(level, origin, direction)) {
                return direction;
            }
        }
        return null;
    }

    public int maxLayer() {
        return requirements.stream().mapToInt(requirement -> requirement.offset().getY()).max().orElse(0);
    }

    public int radius() {
        return requirements.stream()
                .mapToInt(requirement -> Math.max(Math.abs(requirement.offset().getX()), Math.abs(requirement.offset().getZ())))
                .max()
                .orElse(1);
    }
}
