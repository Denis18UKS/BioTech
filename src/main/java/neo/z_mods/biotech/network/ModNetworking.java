package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = BioTech.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {
    
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        
        registrar.playToClient(
            GhostDataPacket.TYPE,
            GhostDataPacket.STREAM_CODEC,
            GhostDataPacket::handle
        );
    }
}