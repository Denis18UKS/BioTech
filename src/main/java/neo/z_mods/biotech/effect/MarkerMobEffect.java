package neo.z_mods.biotech.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class MarkerMobEffect extends MobEffect {
    public MarkerMobEffect(int color) {
        super(MobEffectCategory.NEUTRAL, color);
    }
}
