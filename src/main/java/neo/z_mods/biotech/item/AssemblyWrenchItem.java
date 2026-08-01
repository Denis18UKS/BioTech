package neo.z_mods.biotech.item;

import neo.z_mods.biotech.block.entity.FormedMultiblockBlockEntity;
import neo.z_mods.biotech.multiblock.MultiblockAssembler;
import neo.z_mods.biotech.multiblock.MultiblockRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class AssemblyWrenchItem extends Item {
    public AssemblyWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        ItemStack wrench = context.getItemInHand();
        var clickedPos = context.getClickedPos();

        if (player.isShiftKeyDown()) {
            var controllerPos = level.getBlockEntity(clickedPos) instanceof FormedMultiblockBlockEntity
                    ? clickedPos
                    : MultiblockAssembler.findNearbyController(level, clickedPos);
            if (controllerPos != null && MultiblockAssembler.disassemble(level, controllerPos, player, wrench)) {
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(
                    Component.literal("Рядом нет собранной 3D-машины").withStyle(ChatFormatting.GRAY),
                    true
            );
            return InteractionResult.FAIL;
        }

        MultiblockRegistry.Match match = MultiblockRegistry.findCompletedAt(level, clickedPos);
        if (match == null) {
            player.displayClientMessage(
                    Component.literal("Структура неполная или блоки стоят неверно").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        return MultiblockAssembler.assemble(level, match, player, wrench)
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }
}
