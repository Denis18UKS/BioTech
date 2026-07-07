package neo.z_mods.biotech.mixin;

import neo.z_mods.biotech.network.ClientGhostData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRenderMixin {
    
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void onPlayerRender(AbstractClientPlayer player, 
                                float $$1, 
                                float $$2, 
                                com.mojang.blaze3d.vertex.PoseStack $$3, 
                                net.minecraft.client.renderer.MultiBufferSource $$4, 
                                int $$5, 
                                CallbackInfo ci) {
        if (ClientGhostData.isHidden(player.getUUID())) {
            ci.cancel();
        }
    }
}