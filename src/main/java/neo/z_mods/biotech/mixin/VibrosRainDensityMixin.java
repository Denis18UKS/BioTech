package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.network.ClientVibrosData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Уменьшает только клиентскую плотность дождя во время биовыброса.
 * Серверная гроза, молнии и логика события при этом продолжают работать.
 */
@Mixin(Level.class)
public abstract class VibrosRainDensityMixin {
    private static final float BIOTECH_RAIN_DENSITY = 0.22F;

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void biotech$reduceVibrosRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel && ClientVibrosData.isActive()) {
            cir.setReturnValue(cir.getReturnValue() * BIOTECH_RAIN_DENSITY);
        }
    }
}
