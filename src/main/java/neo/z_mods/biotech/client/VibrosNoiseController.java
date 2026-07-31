package neo.z_mods.biotech.client;

import com.mojang.blaze3d.platform.InputConstants;
import neo.z_mods.biotech.BioTech;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Переключает между минимальным статичным зерном и полностью чистым небом. */
public final class VibrosNoiseController {
    private static final KeyMapping TOGGLE_NOISE = new KeyMapping(
            "key.biotech.toggle_vibros_noise",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.biotech"
    );

    private static boolean noiseDisabled;

    private VibrosNoiseController() {
    }

    /** 0.12 — едва заметное статичное сглаживающее зерно, 0 — без зерна. */
    public static float noiseAmount() {
        return noiseDisabled ? 0.0F : 0.12F;
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_NOISE);
        }
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (TOGGLE_NOISE.consumeClick()) {
                noiseDisabled = !noiseDisabled;
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.literal(noiseDisabled
                                            ? "Шейдер БВ: шум полностью отключён"
                                            : "Шейдер БВ: минимальный статичный шум")
                                    .withStyle(noiseDisabled ? ChatFormatting.AQUA : ChatFormatting.GREEN),
                            true
                    );
                }
            }
        }
    }
}
