package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.network.ClientVibrosData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Во время биовыброса подменяет только текстуру дождя на зелёную.
 * Снег и обычная погода вне события остаются ванильными.
 */
@Mixin(LevelRenderer.class)
public abstract class VibrosRainTextureMixin {
    private static final ResourceLocation BIOTECH_GREEN_RAIN = ResourceLocation.fromNamespaceAndPath(
            BioTech.MODID,
            "textures/environment/green_rain.png"
    );

    @ModifyArg(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
                    ordinal = 0
            ),
            index = 1,
            require = 0
    )
    private ResourceLocation biotech$useGreenRainTexture(ResourceLocation originalTexture) {
        return ClientVibrosData.isActive() ? BIOTECH_GREEN_RAIN : originalTexture;
    }
}
