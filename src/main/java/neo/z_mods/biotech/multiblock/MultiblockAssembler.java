package neo.z_mods.biotech.multiblock;

import neo.z_mods.biotech.block.FormedMultiblockBlock;
import neo.z_mods.biotech.block.FormedFrameBlock;
import neo.z_mods.biotech.block.entity.BioMachineBlockEntity;
import neo.z_mods.biotech.block.entity.FormedMultiblockBlockEntity;
import neo.z_mods.biotech.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MultiblockAssembler {
    public static boolean assemble(ServerLevel level, MultiblockRegistry.Match match, ServerPlayer player, ItemStack wrench) {
        MultiblockDefinition definition = match.definition();
        BlockPos controllerPos = match.controllerPos();
        BlockEntity oldEntity = level.getBlockEntity(controllerPos);
        if (!(oldEntity instanceof BioMachineBlockEntity oldMachine)) {
            player.displayClientMessage(
                    Component.literal("У структуры не найден контроллер BioTech").withStyle(ChatFormatting.RED),
                    true
            );
            return false;
        }

        CompoundTag machineData = oldMachine.saveMachineData(level.registryAccess());

        for (MultiblockDefinition.Requirement requirement : definition.requirements()) {
            if (requirement.offset().equals(BlockPos.ZERO)) {
                continue;
            }
            BlockPos worldPos = definition.worldPosition(match.origin(), match.facing(), requirement.offset());
            boolean energyPort = requirement.block().get() == ModContent.ENERGY_PORT.get();
            level.setBlock(
                    worldPos,
                    ModContent.FORMED_FRAME.get().defaultBlockState()
                            .setValue(FormedFrameBlock.PORT, energyPort),
                    Block.UPDATE_ALL
            );
        }

        BlockState formedState = ModContent.FORMED_MULTIBLOCK.get().defaultBlockState()
                .setValue(FormedMultiblockBlock.FACING, match.facing());
        level.setBlock(controllerPos, formedState, Block.UPDATE_ALL);

        if (!(level.getBlockEntity(controllerPos) instanceof FormedMultiblockBlockEntity formedMachine)) {
            player.displayClientMessage(Component.literal("Не удалось создать цельную модель").withStyle(ChatFormatting.RED), true);
            return false;
        }

        formedMachine.initialize(definition.id(), match.facing(), machineData, level.registryAccess());
        damageWrench(wrench);
        level.playSound(null, controllerPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.85F, 1.25F);
        player.displayClientMessage(
                Component.literal("Мультиблок собран в цельную 3D-машину").withStyle(ChatFormatting.GREEN),
                true
        );
        return true;
    }

    public static boolean disassemble(ServerLevel level, BlockPos controllerPos, ServerPlayer player, ItemStack wrench) {
        if (!(level.getBlockEntity(controllerPos) instanceof FormedMultiblockBlockEntity formedMachine)) {
            return false;
        }
        MultiblockDefinition definition = MultiblockRegistry.get(formedMachine.getFormedMachineId());
        if (definition == null) {
            return false;
        }

        CompoundTag machineData = formedMachine.saveMachineData(level.registryAccess());
        var direction = formedMachine.getAssemblyDirection();

        for (MultiblockDefinition.Requirement requirement : definition.requirements()) {
            BlockPos worldPos = definition.worldPosition(controllerPos, direction, requirement.offset());
            level.setBlock(worldPos, requirement.block().get().defaultBlockState(), Block.UPDATE_ALL);
        }

        if (level.getBlockEntity(controllerPos) instanceof BioMachineBlockEntity restoredController) {
            restoredController.loadMachineData(machineData, level.registryAccess());
        }

        damageWrench(wrench);
        level.playSound(null, controllerPos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.75F, 0.85F);
        player.displayClientMessage(
                Component.literal("3D-машина разобрана обратно на блоки").withStyle(ChatFormatting.YELLOW),
                true
        );
        return true;
    }

    public static BlockPos findNearbyController(ServerLevel level, BlockPos around) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-4, -1, -4), around.offset(4, 5, 4))) {
            if (level.getBlockEntity(pos) instanceof FormedMultiblockBlockEntity) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static void damageWrench(ItemStack wrench) {
        if (!wrench.isDamageableItem()) {
            return;
        }
        wrench.setDamageValue(wrench.getDamageValue() + 1);
        if (wrench.getDamageValue() >= wrench.getMaxDamage()) {
            wrench.shrink(1);
        }
    }

    private MultiblockAssembler() {
    }
}
