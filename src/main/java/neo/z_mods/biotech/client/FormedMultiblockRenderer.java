package neo.z_mods.biotech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.block.entity.FormedMultiblockBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Рисует собранный мультиблок как одну анимированную промышленную 3D-модель. */
public class FormedMultiblockRenderer implements BlockEntityRenderer<FormedMultiblockBlockEntity> {
    private static final ResourceLocation GLASS = ResourceLocation.fromNamespaceAndPath(
            BioTech.MODID, "textures/entity/multiblock/glass.png"
    );
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            BioTech.MODID, "textures/entity/multiblock/glow.png"
    );

    private final ModelPart compactBody = bakeCompactBody();
    private final ModelPart compactGlass = bakeCompactGlass();
    private final ModelPart reactorBody = bakeReactorBody();
    private final ModelPart reactorGlass = bakeReactorGlass();
    private final ModelPart integratorBody = bakeIntegratorBody();
    private final ModelPart integratorGlass = bakeIntegratorGlass();
    private final ModelPart dnaCore = bakeDnaCore();

    public FormedMultiblockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            FormedMultiblockBlockEntity machine,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        String id = machine.getFormedMachineId();
        float time = machine.getLevel() == null
                ? partialTick
                : machine.getLevel().getGameTime() + partialTick;

        poseStack.pushPose();
        // ModelPart uses positive Y downward after the vanilla Y flip. Move the
        // origin to the real top of each machine so the bottom sits exactly on
        // the assembled structure instead of sinking below ground.
        double modelHeight = id.equals("dna_integrator") ? 3.8125D
                : id.equals("bioreactor") ? 3.6875D
                : 2.9375D;
        poseStack.translate(0.5D, modelHeight, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-machine.getAssemblyDirection().toYRot()));
        poseStack.scale(1.0F, -1.0F, -1.0F);

        ResourceLocation bodyTexture = ResourceLocation.fromNamespaceAndPath(
                BioTech.MODID,
                "textures/entity/multiblock/" + id + ".png"
        );
        VertexConsumer solid = buffer.getBuffer(RenderType.entitySolid(bodyTexture));
        VertexConsumer glass = buffer.getBuffer(RenderType.entityTranslucent(GLASS));
        VertexConsumer glow = buffer.getBuffer(RenderType.entityTranslucent(GLOW));

        if (id.equals("bioreactor")) {
            reactorBody.render(poseStack, solid, packedLight, OverlayTexture.NO_OVERLAY);
            reactorGlass.render(poseStack, glass, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else if (id.equals("dna_integrator")) {
            integratorBody.render(poseStack, solid, packedLight, OverlayTexture.NO_OVERLAY);
            integratorGlass.render(poseStack, glass, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else {
            compactBody.render(poseStack, solid, packedLight, OverlayTexture.NO_OVERLAY);
            compactGlass.render(poseStack, glass, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        poseStack.pushPose();
        float pulse = 0.88F + Mth.sin(time * 0.12F) * 0.08F;
        poseStack.scale(pulse, pulse, pulse);
        dnaCore.yRot = time * 0.045F;
        dnaCore.render(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static ModelPart bakeCompactBody() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-24, 0, -24, 48, 5, 48), PartPose.ZERO);
        root.addOrReplaceChild("deck", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-19, 5, -19, 38, 4, 38), PartPose.ZERO);
        root.addOrReplaceChild("lower_ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-12, 9, -12, 24, 4, 24), PartPose.ZERO);
        root.addOrReplaceChild("upper_ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-13, 42, -13, 26, 5, 26), PartPose.ZERO);
        addPillar(root, "nw", -22, 5, -22, 8, 18);
        addPillar(root, "ne", 14, 5, -22, 8, 18);
        addPillar(root, "sw", -22, 5, 14, 8, 18);
        addPillar(root, "se", 14, 5, 14, 8, 18);
        addArm(root, "arm_n", 0, 29, -17, 0.0F);
        addArm(root, "arm_s", 0, 29, 17, 0.0F);
        addArm(root, "arm_w", -17, 29, 0, 90.0F);
        addArm(root, "arm_e", 17, 29, 0, 90.0F);
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static ModelPart bakeCompactGlass() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("chamber", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-10, 13, -10, 20, 29, 20), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static ModelPart bakeReactorBody() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-25, 0, -25, 50, 6, 50), PartPose.ZERO);
        root.addOrReplaceChild("deck", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-20, 6, -20, 40, 5, 40), PartPose.ZERO);
        root.addOrReplaceChild("lower_ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-13, 11, -13, 26, 5, 26), PartPose.ZERO);
        root.addOrReplaceChild("upper_ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-15, 48, -15, 30, 7, 30), PartPose.ZERO);
        root.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-11, 55, -11, 22, 4, 22), PartPose.ZERO);
        addPillar(root, "nw", -24, 6, -24, 9, 25);
        addPillar(root, "ne", 15, 6, -24, 9, 25);
        addPillar(root, "sw", -24, 6, 15, 9, 25);
        addPillar(root, "se", 15, 6, 15, 9, 25);
        addArm(root, "arm_n", 0, 35, -20, 0.0F);
        addArm(root, "arm_s", 0, 35, 20, 0.0F);
        addArm(root, "arm_w", -20, 35, 0, 90.0F);
        addArm(root, "arm_e", 20, 35, 0, 90.0F);
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static ModelPart bakeReactorGlass() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("chamber", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-11, 16, -11, 22, 32, 22), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static ModelPart bakeIntegratorBody() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-39, 0, -39, 78, 6, 78), PartPose.ZERO);
        root.addOrReplaceChild("deck", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-31, 6, -31, 62, 5, 62), PartPose.ZERO);
        root.addOrReplaceChild("lower_ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-15, 11, -15, 30, 6, 30), PartPose.ZERO);
        root.addOrReplaceChild("upper_ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-17, 54, -17, 34, 7, 34), PartPose.ZERO);
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.PI * 2.0 * i / 8.0);
            int x = Math.round(Mth.cos(angle) * 31.0F);
            int z = Math.round(Mth.sin(angle) * 31.0F);
            root.addOrReplaceChild("tower_" + i, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-4, 0, -4, 8, 25, 8), PartPose.offset(x, 6, z));
            root.addOrReplaceChild("brace_" + i, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-3, -3, -15, 6, 6, 30), PartPose.offsetAndRotation(
                            x * 0.55F, 38, z * 0.55F, 0.0F, -angle, 0.0F
                    ));
        }
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static ModelPart bakeIntegratorGlass() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("chamber", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-13, 17, -13, 26, 37, 26), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static ModelPart bakeDnaCore() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("strand_a", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.5F, 19, -1.5F, 3, 26, 3), PartPose.offset(4, 0, 0));
        root.addOrReplaceChild("strand_b", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.5F, 19, -1.5F, 3, 26, 3), PartPose.offset(-4, 0, 0));
        for (int i = 0; i < 6; i++) {
            root.addOrReplaceChild("bridge_" + i, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-5, -1, -1, 10, 2, 2), PartPose.offset(0, 22 + i * 4, 0));
        }
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static void addPillar(PartDefinition root, String name, int x, int y, int z, int width, int height) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0)
                .addBox(x, y, z, width, height, width), PartPose.ZERO);
    }

    private static void addArm(PartDefinition root, String name, int x, int y, int z, float yawDegrees) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3, -3, -12, 6, 6, 24), PartPose.offsetAndRotation(
                        x, y, z, 0.0F, yawDegrees * ((float) Math.PI / 180.0F), 0.0F
                ));
    }

    @Override
    public boolean shouldRenderOffScreen(FormedMultiblockBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
