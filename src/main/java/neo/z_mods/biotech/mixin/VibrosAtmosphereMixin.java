package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.network.ClientVibrosData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Меняет именно небо и облака, а не закрывает игроку обзор зелёным туманом.
 */
@Mixin(ClientLevel.class)
public abstract class VibrosAtmosphereMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void biotech$tintVibrosSky(
            Vec3 cameraPosition,
            float partialTick,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (!ClientVibrosData.isActive()) {
            return;
        }

        double time = System.nanoTime() * 1.0E-9D;
        double pulse = 0.5D + 0.5D * Math.sin(time * 0.37D);
        Vec3 target = new Vec3(
                0.008D + pulse * 0.006D,
                0.105D + pulse * 0.045D,
                0.075D + pulse * 0.020D
        );

        cir.setReturnValue(blend(cir.getReturnValue(), target, 0.78D));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void biotech$tintVibrosClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!ClientVibrosData.isActive()) {
            return;
        }

        double time = System.nanoTime() * 1.0E-9D;
        double pulse = 0.5D + 0.5D * Math.sin(time * 0.51D);
        Vec3 target = new Vec3(
                0.055D + pulse * 0.025D,
                0.385D + pulse * 0.135D,
                0.215D + pulse * 0.065D
        );

        cir.setReturnValue(blend(cir.getReturnValue(), target, 0.72D));
    }

    private static Vec3 blend(Vec3 original, Vec3 target, double amount) {
        double inverse = 1.0D - amount;
        return new Vec3(
                original.x * inverse + target.x * amount,
                original.y * inverse + target.y * amount,
                original.z * inverse + target.z * amount
        );
    }
}
