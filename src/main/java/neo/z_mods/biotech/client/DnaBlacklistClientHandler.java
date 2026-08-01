package neo.z_mods.biotech.client;

import com.mojang.blaze3d.platform.InputConstants;
import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.client.screen.DnaBlacklistScreen;
import neo.z_mods.biotech.network.ClientDnaBlacklistData;
import neo.z_mods.biotech.network.DnaBlacklistRequestPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class DnaBlacklistClientHandler {
    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.biotech.open_dna_blacklist",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.biotech"
    );

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_CONFIG);
        }
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_CONFIG.consumeClick()) {
                if (minecraft.player != null && minecraft.getConnection() != null) {
                    PacketDistributor.sendToServer(new DnaBlacklistRequestPacket());
                }
            }
            if (ClientDnaBlacklistData.consumeOpenRequested()) {
                minecraft.setScreen(new DnaBlacklistScreen(
                        minecraft.screen,
                        ClientDnaBlacklistData.excluded(),
                        ClientDnaBlacklistData.canEdit()
                ));
            }
        }
    }

    private DnaBlacklistClientHandler() {
    }
}
