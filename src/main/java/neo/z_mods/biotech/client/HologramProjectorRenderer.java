package neo.z_mods.biotech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import neo.z_mods.biotech.block.entity.HoloProjectorBlockEntity;
import neo.z_mods.biotech.multiblock.MultiblockDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Рисует не только рамки, но и уменьшенную модель требуемого блока в каждой
 * незаполненной позиции. По голограмме сразу видно, какой блок куда ставить.
 */
public class HologramProjectorRenderer implements BlockEntityRenderer<HoloProjectorBlockEntity> {
    public HologramProjectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            HoloProjectorBlockEntity projector,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        if (projector.getLevel() == null || projector.isCompleted()) {
            return;
        }

        MultiblockDefinition definition = projector.getDefinition();
        if (definition == null) {
            return;
        }

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        BlockPos projectorPos = projector.getBlockPos();
        BlockPos origin = projectorPos.above();
        Direction direction = projector.getProjectionDirection();
        int selectedLayer = projector.getSelectedLayer();

        poseStack.pushPose();
        for (MultiblockDefinition.Requirement requirement : definition.requirements()) {
            if (selectedLayer >= 0 && requirement.offset().getY() != selectedLayer) {
                continue;
            }

            BlockPos world = definition.worldPosition(origin, direction, requirement.offset());
            BlockState present = projector.getLevel().getBlockState(world);
            Block requiredBlock = requirement.block().get();
            boolean correct = present.is(requiredBlock);
            boolean empty = present.isAir() || present.canBeReplaced();

            double x = world.getX() - projectorPos.getX();
            double y = world.getY() - projectorPos.getY();
            double z = world.getZ() - projectorPos.getZ();

            if (!correct) {
                renderRequiredBlock(poseStack, buffer, requiredBlock.defaultBlockState(), x, y, z);
            }

            AABB box = new AABB(
                    x + 0.03,
                    y + 0.03,
                    z + 0.03,
                    x + 0.97,
                    y + 0.97,
                    z + 0.97
            );

            if (correct) {
                LevelRenderer.renderLineBox(poseStack, lines, box, 0.15F, 1.0F, 0.35F, 0.72F);
            } else if (empty) {
                // Голубая рамка: свободная позиция, сюда нужно поставить показанный блок.
                LevelRenderer.renderLineBox(poseStack, lines, box, 0.20F, 0.90F, 1.0F, 0.92F);
            } else {
                // Оранжевая рамка: место занято неправильным блоком.
                LevelRenderer.renderLineBox(poseStack, lines, box, 1.0F, 0.40F, 0.08F, 0.95F);
            }
        }
        poseStack.popPose();
    }

    private static void renderRequiredBlock(
            PoseStack poseStack,
            MultiBufferSource buffer,
            BlockState requiredState,
            double x,
            double y,
            double z
    ) {
        poseStack.pushPose();
        poseStack.translate(x + 0.5D, y + 0.5D, z + 0.5D);

        // Модель меньше полного блока: она читается как голограмма и не скрывает
        // уже поставленные рядом блоки и цветную рамку позиции.
        float scale = 0.68F;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                requiredState,
                poseStack,
                buffer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(HoloProjectorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
