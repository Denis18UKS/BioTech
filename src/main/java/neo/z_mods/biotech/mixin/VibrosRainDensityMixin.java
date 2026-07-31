package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.client.VibrosShaderManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Плавно разрежает дождь по мере усиления и ослабления биовыброса. */
@Mixin(Level.class)
public abstract class VibrosRainDensityMixin {
    private static final float BIOTECH_RAIN_DENSITY = 0.22F;

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void biotech$reduceVibrosRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) {
            return;
        }

        float storm = VibrosShaderManager.currentStormStrength();
        if (storm <= 0.001F) {
            return;
        }

        float multiplier = Mth.lerp(storm, 1.0F, BIOTECH_RAIN_DENSITY);
        cir.setReturnValue(cir.getReturnValue() * multiplier);
    }
}
