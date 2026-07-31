package neo.z_mods.biotech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import neo.z_mods.biotech.block.entity.HoloProjectorBlockEntity;
import neo.z_mods.biotech.multiblock.MultiblockDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class HologramProjectorRenderer implements BlockEntityRenderer<HoloProjectorBlockEntity> {
    public HologramProjectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HoloProjectorBlockEntity projector, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (projector.getLevel() == null || projector.isCompleted()) return;
        MultiblockDefinition definition = projector.getDefinition();
        if (definition == null) return;

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        BlockPos projectorPos = projector.getBlockPos();
        BlockPos origin = projectorPos.above();
        Direction direction = projector.getProjectionDirection();
        int selectedLayer = projector.getSelectedLayer();

        poseStack.pushPose();
        for (MultiblockDefinition.Requirement requirement : definition.requirements()) {
            if (selectedLayer >= 0 && requirement.offset().getY() != selectedLayer) continue;
            BlockPos world = definition.worldPosition(origin, direction, requirement.offset());
            BlockState present = projector.getLevel().getBlockState(world);
            boolean correct = present.is(requirement.block().get());
            double x = world.getX() - projectorPos.getX();
            double y = world.getY() - projectorPos.getY();
            double z = world.getZ() - projectorPos.getZ();
            AABB box = new AABB(x + 0.03, y + 0.03, z + 0.03, x + 0.97, y + 0.97, z + 0.97);
            if (correct) {
                LevelRenderer.renderLineBox(poseStack, lines, box, 0.15F, 1.0F, 0.35F, 0.75F);
            } else {
                LevelRenderer.renderLineBox(poseStack, lines, box, 1.0F, 0.18F, 0.12F, 0.75F);
            }
        }
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
