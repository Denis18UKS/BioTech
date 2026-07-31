package neo.z_mods.biotech.client;

import com.mojang.math.Axis;
import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.registry.ModEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
public final class MutationRenderHandler {
    @SubscribeEvent
    public static void beforeRender(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance mutation = entity.getEffect(ModEffects.MUTATION_VISUAL);
        MobEffectInstance rust = entity.getEffect(ModEffects.RUST_VISUAL);
        if (mutation == null && rust == null) return;

        event.getPoseStack().pushPose();
        float time = entity.tickCount + event.getPartialTick();
        if (mutation != null) {
            float stage = Mth.clamp((mutation.getAmplifier() + 1) / 10.0F, 0.1F, 1.0F);
            float pulse = Mth.sin(time * (0.12F + stage * 0.16F)) * 0.025F * stage;
            event.getPoseStack().scale(1.0F + stage * 0.12F + pulse, 1.0F + stage * 0.06F - pulse, 1.0F + stage * 0.12F + pulse);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.42F) * stage * 2.8F));
        } else {
            float stage = Mth.clamp((rust.getAmplifier() + 1) / 10.0F, 0.1F, 1.0F);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.08F) * stage * 0.9F));
            event.getPoseStack().scale(1.0F, 1.0F - stage * 0.025F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void afterRender(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModEffects.MUTATION_VISUAL) || entity.hasEffect(ModEffects.RUST_VISUAL)) {
            event.getPoseStack().popPose();
        }
    }

    private MutationRenderHandler() {
    }
}
