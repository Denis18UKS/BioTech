package neo.z_mods.biotech.block;

import com.mojang.serialization.MapCodec;
import neo.z_mods.biotech.block.entity.BioMachineBlockEntity;
import neo.z_mods.biotech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BioMachineBlock extends Block implements EntityBlock {
    public static final MapCodec<BioMachineBlock> CODEC = simpleCodec(properties -> new BioMachineBlock(properties, false));
    private final boolean cable;

    public BioMachineBlock(Properties properties, boolean cable) {
        super(properties);
        this.cable = cable;
    }

    public boolean isCable() {
        return cable;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BioMachineBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BioMachineBlockEntity machine) {
            serverPlayer.openMenu(machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.BIO_MACHINE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> BioMachineBlockEntity.serverTick(lvl, pos, st, (BioMachineBlockEntity) be);
    }
}
