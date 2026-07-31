package neo.z_mods.biotech;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Сохраняет защиту закрытого укрытия, но не фиксирует игрока в одной точке.
 * Игрок может свободно ходить внутри помещения; при выходе наружу снова начинает
 * накапливаться воздействие БВ.
 */
@EventBusSubscriber(modid = BioTech.MODID)
public final class VibrosShelterMobilityHandler {
    private static final String LOCKED = "BioTechVibrosShelterLocked";
    private static final String STABLE = "BioTechVibrosShelterStable";
    private static final String ANCHOR_X = "BioTechVibrosAnchorX";
    private static final String ANCHOR_Y = "BioTechVibrosAnchorY";
    private static final String ANCHOR_Z = "BioTechVibrosAnchorZ";

    private VibrosShelterMobilityHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeVibrosSurvivalTick(ServerTickEvent.Post event) {
        if (!BioTech.VIBROS_ACTIVE) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.getPersistentData().remove(LOCKED);
            player.getPersistentData().remove(ANCHOR_X);
            player.getPersistentData().remove(ANCHOR_Y);
            player.getPersistentData().remove(ANCHOR_Z);

            // Не даём старой логике дойти до фиксации позиции. Проверка самого
            // укрытия и защита от урона при этом продолжают работать каждый тик.
            player.getPersistentData().putInt(STABLE, 0);
        }
    }
}
