package neo.z_mods.biotech;

import neo.z_mods.biotech.multiblock.MultiblockAssembler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Исправляет взаимодействие с цельными мультиблоками без изменения мира:
 * ПКМ по любой невидимой рамке открывает GUI, а разрушение любой части
 * восстанавливает исходную структуру из настоящих блоков.
 */
@EventBusSubscriber(modid = BioTech.MODID)
public final class FormedMultiblockFixHandler {
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Не вызываем PlayerInteractEvent#getLevel(): берём серверный мир
        // непосредственно у ServerPlayer, чтобы не зависеть от ковариантных
        // сигнатур событий в разных патчах NeoForge 21.1.x.
        ServerLevel level = player.serverLevel();

        BlockState state = level.getBlockState(event.getPos());
        if (!isFormedPart(state)) {
            return;
        }

        // Сравнение по registry id не зависит от JVM-дескриптора DeferredItem#get().
        // Это важно для совместимости между патч-релизами NeoForge 21.1.x.
        if (hasRegistryId(event.getItemStack().getItem(), "assembly_wrench")) {
            return;
        }

        BlockPos controllerPos = MultiblockAssembler.findNearbyController(level, event.getPos());
        if (controllerPos == null) {
            return;
        }
        if (level.getBlockEntity(controllerPos) instanceof MenuProvider menuProvider) {
            // В Minecraft/NeoForge 1.21.1 метод возвращает OptionalInt.
            // Возвращаемое значение можно игнорировать, но класс обязательно должен
            // собираться против настоящих зависимостей 1.21.1, чтобы JVM-дескриптор
            // был (...MenuProvider)Ljava/util/OptionalInt;, а не (...MenuProvider)V.
            player.openMenu(menuProvider);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !isFormedPart(event.getState())) {
            return;
        }

        // BlockEvent#getLevel() в NeoForge 1.21.1 возвращает LevelAccessor,
        // а не Level. Берём ServerLevel у игрока: это одновременно устраняет
        // NoSuchMethodError и гарантирует выполнение разборки на сервере.
        ServerLevel level = player.serverLevel();
        BlockPos controllerPos = MultiblockAssembler.findNearbyController(level, event.getPos());
        if (controllerPos != null
                && MultiblockAssembler.disassemble(level, controllerPos, player, ItemStack.EMPTY)) {
            // Блок не исчезает: вместо служебной оболочки уже восстановлена
            // настоящая структура, поэтому обычное разрушение отменяется.
            event.setCanceled(true);
        }
    }

    private static boolean isFormedPart(BlockState state) {
        return hasRegistryId(state.getBlock(), "formed_multiblock")
                || hasRegistryId(state.getBlock(), "formed_frame");
    }

    private static boolean hasRegistryId(net.minecraft.world.level.block.Block block, String path) {
        return BuiltInRegistries.BLOCK.getKey(block)
                .equals(ResourceLocation.fromNamespaceAndPath(BioTech.MODID, path));
    }

    private static boolean hasRegistryId(net.minecraft.world.item.Item item, String path) {
        return BuiltInRegistries.ITEM.getKey(item)
                .equals(ResourceLocation.fromNamespaceAndPath(BioTech.MODID, path));
    }

    private FormedMultiblockFixHandler() {
    }
}
