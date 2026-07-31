package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.client.VibrosShaderManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Синхронизирует ванильное освещение мира с плавными стадиями шейдера.
 * Дальность тумана и дальность прорисовки здесь не изменяются.
 */
@Mixin(ClientLevel.class)
public abstract class VibrosAtmosphereMixin {

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void biotech$darkenWorldDuringVibros(float partialTick, CallbackInfoReturnable<Float> cir) {
        float storm = VibrosShaderManager.currentStormStrength();
        if (storm <= 0.001F) {
            return;
        }

        float dissolveGlow = Mth.sin(VibrosShaderManager.currentDissolveProgress() * (float) Math.PI);
        float nightBrightness = 0.17F + dissolveGlow * 0.07F;
        cir.setReturnValue(Mth.lerp(storm, cir.getReturnValue(), nightBrightness));
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void biotech$tintVibrosSky(
            Vec3 cameraPosition,
            float partialTick,
            CallbackInfoReturnable<Vec3> cir
    ) {
        float storm = VibrosShaderManager.currentStormStrength();
        if (storm <= 0.001F) {
            return;
        }

        float flash = VibrosShaderManager.currentFlashIntensity();
        float dissolveGlow = Mth.sin(VibrosShaderManager.currentDissolveProgress() * (float) Math.PI);
        Vec3 target = new Vec3(
                0.008D + flash * 0.025D,
                0.090D + flash * 0.220D + dissolveGlow * 0.100D,
                0.060D + flash * 0.070D
        );

        cir.setReturnValue(blend(cir.getReturnValue(), target, storm * 0.82D));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void biotech$tintVibrosClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float storm = VibrosShaderManager.currentStormStrength();
        if (storm <= 0.001F) {
            return;
        }

        float flash = VibrosShaderManager.currentFlashIntensity();
        Vec3 target = new Vec3(
                0.025D + flash * 0.040D,
                0.235D + flash * 0.360D,
                0.135D + flash * 0.120D
        );

        cir.setReturnValue(blend(cir.getReturnValue(), target, storm * 0.78D));
    }

    private static Vec3 blend(Vec3 original, Vec3 target, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        double inverse = 1.0D - clamped;
        return new Vec3(
                original.x * inverse + target.x * clamped,
                original.y * inverse + target.y * clamped,
                original.z * inverse + target.z * clamped
        );
    }
}
