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
        float original = cir.getReturnValue();

        // Даже спокойное небо BioTech всегда облачное. Днём немного приглушаем
        // небесный свет, чтобы земля соответствовала плотным тучам, но ночью
        // почти не вмешиваемся и не превращаем обычную погоду в вечную ночь.
        float daylight = Mth.clamp((original - 0.18F) / 0.82F, 0.0F, 1.0F);
        float calmOvercast = original * (1.0F - daylight * 0.24F);

        float storm = VibrosShaderManager.currentStormStrength();
        if (storm <= 0.001F) {
            cir.setReturnValue(calmOvercast);
            return;
        }

        float flash = VibrosShaderManager.currentFlashIntensity();
        float dissolveGlow = Mth.sin(VibrosShaderManager.currentDissolveProgress() * (float) Math.PI);

        // Активная фаза остаётся тёмной даже посреди дня. Краткие вспышки и
        // финальное сияние слегка поднимают небесную яркость, создавая ощущение
        // зелёного света от облаков, а не обычного солнечного освещения.
        float stormBrightness = 0.105F
                + Math.min(flash, 1.8F) * 0.055F
                + dissolveGlow * 0.075F;
        cir.setReturnValue(Mth.lerp(storm, calmOvercast, stormBrightness));
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
