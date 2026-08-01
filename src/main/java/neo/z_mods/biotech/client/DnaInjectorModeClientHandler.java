package neo.z_mods.biotech.client;

import com.mojang.blaze3d.platform.InputConstants;
import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.item.DnaInjectorItem;
import neo.z_mods.biotech.network.DnaInjectorModePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Настраиваемый кейбинд переключения режимов инъектора. По умолчанию V. */
public final class DnaInjectorModeClientHandler {
    private static final KeyMapping SWITCH_MODE = new KeyMapping(
            "key.biotech.switch_dna_injector_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.biotech"
    );

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(SWITCH_MODE);
        }
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (SWITCH_MODE.consumeClick()) {
                if (minecraft.player == null || minecraft.getConnection() == null) {
                    continue;
                }
                if (minecraft.player.getMainHandItem().getItem() instanceof DnaInjectorItem) {
                    PacketDistributor.sendToServer(new DnaInjectorModePacket(0));
                } else if (minecraft.player.getOffhandItem().getItem() instanceof DnaInjectorItem) {
                    PacketDistributor.sendToServer(new DnaInjectorModePacket(1));
                }
            }
        }
    }

    private DnaInjectorModeClientHandler() {
    }
}
