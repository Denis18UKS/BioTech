package neo.z_mods.biotech.registry;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.effect.MarkerMobEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BioTech.MODID);
    public static final DeferredHolder<MobEffect, MobEffect> MUTATION_VISUAL = EFFECTS.register("mutation_visual", () -> new MarkerMobEffect(0x21FF72));
    public static final DeferredHolder<MobEffect, MobEffect> RUST_VISUAL = EFFECTS.register("rust_visual", () -> new MarkerMobEffect(0xA85F2A));

    private ModEffects() {
    }
}
