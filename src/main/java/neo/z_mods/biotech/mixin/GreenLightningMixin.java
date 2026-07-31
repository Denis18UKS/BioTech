package neo.z_mods.biotech.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import neo.z_mods.biotech.network.ClientVibrosData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Полностью заменяет форму ванильного разряда во время активного БВ. */
@Mixin(LightningBoltRenderer.class)
public abstract class GreenLightningMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void biotech$renderBiovibrosLightning(LightningBolt bolt, float entityYaw, float partialTick,
                                                   PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                                   CallbackInfo ci) {
        if (!ClientVibrosData.isActive()) return;
        ci.cancel();

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        RandomSource random = RandomSource.create(bolt.seed ^ 0x42B10B10L);
        float[] x = new float[13];
        float[] z = new float[13];
        for (int i = 1; i < x.length; i++) {
            float widen = 0.55F + i * 0.16F;
            x[i] = x[i - 1] + (random.nextFloat() - 0.5F) * widen;
            z[i] = z[i - 1] + (random.nextFloat() - 0.5F) * widen;
        }

        // Яркое центральное ядро и широкий зелёный ореол.
        renderPath(matrix, vertices, x, z, 0, 12, 0.10F, 0.96F, 0.30F, 0.92F, 0.18F);
        renderPath(matrix, vertices, x, z, 0, 12, 0.02F, 0.42F, 0.12F, 0.22F, 0.62F);

        // Собственные боковые ответвления: форма больше не совпадает с ванильной молнией.
        for (int branch = 0; branch < 7; branch++) {
            int start = 3 + random.nextInt(8);
            int length = 2 + random.nextInt(4);
            float[] bx = new float[length + 1];
            float[] bz = new float[length + 1];
            bx[0] = x[start];
            bz[0] = z[start];
            float directionX = (random.nextBoolean() ? 1.0F : -1.0F) * (0.8F + random.nextFloat() * 1.5F);
            float directionZ = (random.nextBoolean() ? 1.0F : -1.0F) * (0.8F + random.nextFloat() * 1.5F);
            for (int i = 1; i <= length; i++) {
                bx[i] = bx[i - 1] + directionX * (0.55F + random.nextFloat() * 0.45F) + (random.nextFloat() - 0.5F) * 0.5F;
                bz[i] = bz[i - 1] + directionZ * (0.55F + random.nextFloat() * 0.45F) + (random.nextFloat() - 0.5F) * 0.5F;
            }
            renderBranch(matrix, vertices, bx, bz, start, 0.06F, 0.93F, 0.26F, 0.72F, 0.11F);
            renderBranch(matrix, vertices, bx, bz, start, 0.02F, 0.45F, 0.12F, 0.18F, 0.34F);
        }
    }

    private static void renderPath(Matrix4f matrix, VertexConsumer vertices, float[] x, float[] z,
                                   int from, int to, float red, float green, float blue, float alpha, float width) {
        for (int i = from; i < to; i++) {
            float y0 = i * 10.5F;
            float y1 = (i + 1) * 10.5F;
            segment(matrix, vertices, x[i], y0, z[i], x[i + 1], y1, z[i + 1], width, red, green, blue, alpha);
        }
    }

    private static void renderBranch(Matrix4f matrix, VertexConsumer vertices, float[] x, float[] z,
                                     int mainStart, float red, float green, float blue, float alpha, float width) {
        for (int i = 0; i < x.length - 1; i++) {
            float y0 = (mainStart - i * 0.72F) * 10.5F;
            float y1 = (mainStart - (i + 1) * 0.72F) * 10.5F;
            segment(matrix, vertices, x[i], y0, z[i], x[i + 1], y1, z[i + 1], width, red, green, blue, alpha);
        }
    }

    private static void segment(Matrix4f matrix, VertexConsumer vertices,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float width, float red, float green, float blue, float alpha) {
        quad(matrix, vertices, x0, y0, z0, x1, y1, z1, width, red, green, blue, alpha, false);
        quad(matrix, vertices, x0, y0, z0, x1, y1, z1, width, red, green, blue, alpha, true);
    }

    private static void quad(Matrix4f matrix, VertexConsumer vertices,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float width, float red, float green, float blue, float alpha, boolean cross) {
        float ax = cross ? width : 0.0F;
        float az = cross ? 0.0F : width;
        vertices.addVertex(matrix, x0 - ax, y0, z0 - az).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, x1 - ax, y1, z1 - az).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, x1 + ax, y1, z1 + az).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, x0 + ax, y0, z0 + az).setColor(red, green, blue, alpha);
    }
}
