package neo.z_mods.biotech.block;

import com.mojang.serialization.MapCodec;
import neo.z_mods.biotech.block.entity.BioMachineBlockEntity;
import neo.z_mods.biotech.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

/**
 * Базовый блок BioTech. Роль блока явно определяет, может ли он открывать GUI
 * и участвовать ли в энергосети. Благодаря этому служебные части мультиблоков
 * больше не открывают случайный интерфейс другой машины.
 */
public class BioMachineBlock extends Block implements EntityBlock {
    public enum Role {
        SINGLE_MACHINE(true, false, false),
        MULTIBLOCK_CONTROLLER(false, false, false),
        SERVICE(false, false, false),
        ENERGY_PORT(false, false, true),
        CABLE(false, true, false);

        private final boolean opensGui;
        private final boolean cable;
        private final boolean energyPort;

        Role(boolean opensGui, boolean cable, boolean energyPort) {
            this.opensGui = opensGui;
            this.cable = cable;
            this.energyPort = energyPort;
        }

        public boolean opensGui() {
            return opensGui;
        }

        public boolean isCable() {
            return cable;
        }

        public boolean isEnergyPort() {
            return energyPort;
        }
    }

    public static final MapCodec<BioMachineBlock> CODEC = simpleCodec(
            properties -> new BioMachineBlock(properties, Role.SERVICE)
    );

    private final Role role;

    public BioMachineBlock(Properties properties, Role role) {
        super(properties);
        this.role = role;
    }

    /** Совместимый конструктор для старых участков исходников. */
    public BioMachineBlock(Properties properties, boolean cable) {
        this(properties, cable ? Role.CABLE : Role.SERVICE);
    }

    public Role role() {
        return role;
    }

    public boolean isCable() {
        return role.isCable();
    }

    public boolean isEnergyPort() {
        return role.isEnergyPort();
    }

    public boolean opensGuiBeforeAssembly() {
        return role.opensGui();
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
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!opensGuiBeforeAssembly()) {
            if (!level.isClientSide() && role == Role.MULTIBLOCK_CONTROLLER) {
                player.displayClientMessage(
                        Component.literal("Интерфейс станет доступен после полной сборки машины")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof BioMachineBlockEntity machine) {
            serverPlayer.openMenu(machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide() || type != ModBlockEntities.BIO_MACHINE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> BioMachineBlockEntity.serverTick(
                lvl, pos, st, (BioMachineBlockEntity) be
        );
    }
}
