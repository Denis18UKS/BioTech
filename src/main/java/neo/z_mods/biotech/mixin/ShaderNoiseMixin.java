package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.client.VibrosNoiseController;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Перед применением шейдера передаёт выбранную игроком интенсивность зерна. */
@Mixin(ShaderInstance.class)
public abstract class ShaderNoiseMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void biotech$applyNoiseMode(CallbackInfo ci) {
        ShaderInstance shader = (ShaderInstance) (Object) this;
        shader.safeGetUniform("NoiseAmount").set(VibrosNoiseController.noiseAmount());
    }
}
