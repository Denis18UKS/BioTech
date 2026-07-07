package neo.z_mods.biotech;

import neo.z_mods.biotech.network.ClientGhostData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
public class GhostRenderHandler {
    
    @SubscribeEvent
    public static void onPlayerRenderPre(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        
        if (ClientGhostData.isHidden(player.getUUID())) {
            event.setCanceled(true);
        }
    }
}