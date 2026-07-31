package neo.z_mods.biotech.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import neo.z_mods.biotech.client.VibrosShaderManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Убирает плоские ванильные облака, пока работает встроенное процедурное небо.
 * Сами тучи рисуются шейдером несколькими объёмными слоями.
 */
@Mixin(LevelRenderer.class)
public abstract class VibrosVanillaCloudMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void biotech$hideVanillaClouds(
            PoseStack poseStack,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfo ci
    ) {
        if (VibrosShaderManager.shouldRenderSky()) {
            ci.cancel();
        }
    }
}
