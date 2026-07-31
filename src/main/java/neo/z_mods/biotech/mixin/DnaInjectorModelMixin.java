package neo.z_mods.biotech.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import neo.z_mods.biotech.client.ClientRegistration;
import neo.z_mods.biotech.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class DnaInjectorModelMixin {
    @ModifyVariable(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 8
    )
    private BakedModel biotech$useHandModel(BakedModel original, ItemStack stack, ItemDisplayContext context,
                                             boolean leftHand, PoseStack poseStack, MultiBufferSource buffer,
                                             int packedLight, int packedOverlay) {
        if (!stack.is(ModContent.DNK_INJECTOR.get())) return original;
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED) return original;
        BakedModel hand = Minecraft.getInstance().getModelManager().getModel(ClientRegistration.DNK_INJECTOR_HAND_MODEL);
        return hand == Minecraft.getInstance().getModelManager().getMissingModel() ? original : hand;
    }
}
