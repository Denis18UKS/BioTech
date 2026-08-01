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

/**
 * Цельные непрозрачные модели собранных мультиблочных машин BioTech.
 *
 * <p>Координата Y в этих моделях направлена вверх от пола структуры, поэтому
 * рендер не использует ванильный переворот по Y. Это убирает старый эффект,
 * при котором основание оказывалось наверху, а машина висела над землёй.</p>
 */
public final class FormedMultiblockRenderer implements BlockEntityRenderer<FormedMultiblockBlockEntity> {
    private static final ResourceLocation WINDOW = texture("window");
    private static final ResourceLocation PORT = texture("port");
    private static final ResourceLocation GLOW = texture("glow");

    private final MachineModel synthesizer = bakeSynthesizer();
    private final MachineModel mixer = bakeMixer();
    private final MachineModel hybridizer = bakeHybridizer();
    private final MachineModel bioreactor = bakeBioreactor();
    private final MachineModel integrator = bakeIntegrator();

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
        MachineModel model = modelFor(id);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-machine.getAssemblyDirection().toYRot()));
        // Только разворачиваем локальную ось Z, не переворачивая модель вверх ногами.
        poseStack.scale(1.0F, 1.0F, -1.0F);

        ResourceLocation bodyTexture = ResourceLocation.fromNamespaceAndPath(
                BioTech.MODID,
                "textures/entity/multiblock/" + id + ".png"
        );
        VertexConsumer body = buffer.getBuffer(RenderType.entitySolid(bodyTexture));
        model.body().render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);

        VertexConsumer window = buffer.getBuffer(RenderType.entitySolid(WINDOW));
        model.window().render(poseStack, window, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        VertexConsumer port = buffer.getBuffer(RenderType.entitySolid(PORT));
        model.port().render(poseStack, port, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        VertexConsumer glow = buffer.getBuffer(RenderType.entitySolid(GLOW));
        model.glow().render(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    private MachineModel modelFor(String id) {
        return switch (id) {
            case "dna_mixer" -> mixer;
            case "dna_hybridizer" -> hybridizer;
            case "bioreactor" -> bioreactor;
            case "dna_integrator" -> integrator;
            default -> synthesizer;
        };
    }

    private static MachineModel bakeSynthesizer() {
        MeshDefinition bodyMesh = new MeshDefinition();
        PartDefinition body = bodyMesh.getRoot();
        box(body, "foundation", -24, 0, -24, 48, 5, 48, 0, 0);
        box(body, "lower_chassis", -20, 5, -20, 40, 7, 40, 32, 0);
        box(body, "central_housing", -14, 12, -14, 28, 27, 28, 64, 0);
        box(body, "upper_cap", -17, 39, -17, 34, 7, 34, 96, 0);
        box(body, "processor", -9, 46, -9, 18, 5, 18, 128, 0);
        box(body, "left_module", -23, 12, -13, 8, 24, 26, 0, 32);
        box(body, "right_module", 15, 12, -13, 8, 24, 26, 32, 32);
        box(body, "front_console", -12, 8, -29, 24, 14, 8, 64, 32);
        box(body, "rear_bus", -14, 11, 20, 28, 11, 7, 96, 32);

        MeshDefinition windowMesh = new MeshDefinition();
        PartDefinition window = windowMesh.getRoot();
        box(window, "front_window", -9, 18, -14.6F, 18, 15, 1.2F, 0, 0);
        box(window, "side_window_left", -14.6F, 18, -8, 1.2F, 15, 16, 16, 0);
        box(window, "side_window_right", 13.4F, 18, -8, 1.2F, 15, 16, 32, 0);

        MeshDefinition portMesh = new MeshDefinition();
        box(portMesh.getRoot(), "energy_port", -6, 10, -33, 12, 12, 5, 0, 0);

        MeshDefinition glowMesh = new MeshDefinition();
        PartDefinition glow = glowMesh.getRoot();
        box(glow, "status_panel", -7, 12, -34.1F, 14, 6, 1, 0, 0);
        box(glow, "top_core", -5, 48, -5, 10, 2, 10, 16, 0);

        return machine(bodyMesh, windowMesh, portMesh, glowMesh);
    }

    private static MachineModel bakeMixer() {
        MeshDefinition bodyMesh = new MeshDefinition();
        PartDefinition body = bodyMesh.getRoot();
        box(body, "foundation", -24, 0, -24, 48, 5, 48, 0, 0);
        box(body, "lower_chassis", -21, 5, -21, 42, 7, 42, 32, 0);
        box(body, "left_tank", -20, 12, -13, 16, 27, 26, 64, 0);
        box(body, "right_tank", 4, 12, -13, 16, 27, 26, 96, 0);
        box(body, "left_cap", -22, 39, -15, 20, 7, 30, 128, 0);
        box(body, "right_cap", 2, 39, -15, 20, 7, 30, 160, 0);
        box(body, "mixing_bridge", -7, 23, -8, 14, 10, 16, 0, 32);
        box(body, "upper_bridge", -12, 44, -8, 24, 5, 16, 32, 32);
        box(body, "front_console", -13, 8, -29, 26, 14, 8, 64, 32);
        box(body, "rear_manifold", -17, 12, 19, 34, 12, 8, 96, 32);

        MeshDefinition windowMesh = new MeshDefinition();
        PartDefinition window = windowMesh.getRoot();
        box(window, "left_window", -16, 18, -13.6F, 8, 15, 1.2F, 0, 0);
        box(window, "right_window", 8, 18, -13.6F, 8, 15, 1.2F, 16, 0);
        box(window, "mix_window", -5, 25, -8.6F, 10, 6, 1.2F, 32, 0);

        MeshDefinition portMesh = new MeshDefinition();
        box(portMesh.getRoot(), "energy_port", -6, 10, -33, 12, 12, 5, 0, 0);

        MeshDefinition glowMesh = new MeshDefinition();
        PartDefinition glow = glowMesh.getRoot();
        box(glow, "left_status", -18, 14, -14.2F, 12, 3, 1, 0, 0);
        box(glow, "right_status", 6, 14, -14.2F, 12, 3, 1, 16, 0);
        box(glow, "bridge_core", -4, 26, -9.2F, 8, 4, 1, 32, 0);

        return machine(bodyMesh, windowMesh, portMesh, glowMesh);
    }

    private static MachineModel bakeHybridizer() {
        MeshDefinition bodyMesh = new MeshDefinition();
        PartDefinition body = bodyMesh.getRoot();
        box(body, "foundation", -24, 0, -24, 48, 5, 48, 0, 0);
        box(body, "lower_chassis", -21, 5, -21, 42, 7, 42, 32, 0);
        box(body, "fusion_housing", -13, 12, -13, 26, 31, 26, 64, 0);
        box(body, "left_stabilizer", -23, 14, -12, 9, 25, 24, 96, 0);
        box(body, "right_stabilizer", 14, 14, -12, 9, 25, 24, 128, 0);
        box(body, "top_arch", -20, 43, -15, 40, 7, 30, 160, 0);
        box(body, "top_reactor", -9, 50, -9, 18, 5, 18, 0, 32);
        box(body, "front_console", -13, 8, -29, 26, 14, 8, 32, 32);
        box(body, "left_feed", -22, 20, -18, 9, 8, 10, 64, 32);
        box(body, "right_feed", 13, 20, -18, 9, 8, 10, 96, 32);

        MeshDefinition windowMesh = new MeshDefinition();
        PartDefinition window = windowMesh.getRoot();
        box(window, "fusion_window", -9, 19, -13.6F, 18, 17, 1.2F, 0, 0);
        box(window, "left_window", -22.6F, 20, -7, 1.2F, 12, 14, 16, 0);
        box(window, "right_window", 21.4F, 20, -7, 1.2F, 12, 14, 32, 0);

        MeshDefinition portMesh = new MeshDefinition();
        box(portMesh.getRoot(), "energy_port", -6, 10, -33, 12, 12, 5, 0, 0);

        MeshDefinition glowMesh = new MeshDefinition();
        PartDefinition glow = glowMesh.getRoot();
        box(glow, "fusion_core", -6, 24, -14.2F, 12, 7, 1, 0, 0);
        box(glow, "top_indicator", -5, 52, -9.2F, 10, 2, 1, 16, 0);

        return machine(bodyMesh, windowMesh, portMesh, glowMesh);
    }

    private static MachineModel bakeBioreactor() {
        MeshDefinition bodyMesh = new MeshDefinition();
        PartDefinition body = bodyMesh.getRoot();
        box(body, "foundation", -25, 0, -25, 50, 6, 50, 0, 0);
        box(body, "lower_chassis", -22, 6, -22, 44, 8, 44, 32, 0);
        box(body, "reactor_housing", -16, 14, -16, 32, 38, 32, 64, 0);
        box(body, "upper_collar", -19, 52, -19, 38, 8, 38, 96, 0);
        box(body, "reactor_cap", -12, 60, -12, 24, 7, 24, 128, 0);
        box(body, "coil_nw", -24, 13, -24, 9, 35, 9, 160, 0);
        box(body, "coil_ne", 15, 13, -24, 9, 35, 9, 0, 32);
        box(body, "coil_sw", -24, 13, 15, 9, 35, 9, 32, 32);
        box(body, "coil_se", 15, 13, 15, 9, 35, 9, 64, 32);
        box(body, "front_console", -14, 8, -31, 28, 16, 9, 96, 32);
        box(body, "left_feed", -27, 20, -8, 11, 12, 16, 128, 32);
        box(body, "right_feed", 16, 20, -8, 11, 12, 16, 160, 32);

        MeshDefinition windowMesh = new MeshDefinition();
        PartDefinition window = windowMesh.getRoot();
        box(window, "reactor_window", -11, 22, -16.6F, 22, 22, 1.2F, 0, 0);
        box(window, "left_window", -16.6F, 22, -10, 1.2F, 22, 20, 16, 0);
        box(window, "right_window", 15.4F, 22, -10, 1.2F, 22, 20, 32, 0);

        MeshDefinition portMesh = new MeshDefinition();
        box(portMesh.getRoot(), "energy_port", -7, 11, -36, 14, 14, 6, 0, 0);

        MeshDefinition glowMesh = new MeshDefinition();
        PartDefinition glow = glowMesh.getRoot();
        box(glow, "reactor_core", -7, 28, -17.2F, 14, 9, 1, 0, 0);
        box(glow, "cap_indicator", -6, 63, -12.2F, 12, 2, 1, 16, 0);

        return machine(bodyMesh, windowMesh, portMesh, glowMesh);
    }

    private static MachineModel bakeIntegrator() {
        MeshDefinition bodyMesh = new MeshDefinition();
        PartDefinition body = bodyMesh.getRoot();
        box(body, "foundation", -40, 0, -40, 80, 6, 80, 0, 0);
        box(body, "lower_chassis", -35, 6, -35, 70, 8, 70, 32, 0);
        box(body, "central_capsule", -18, 14, -18, 36, 45, 36, 64, 0);
        box(body, "upper_collar", -21, 59, -21, 42, 8, 42, 96, 0);
        box(body, "top_cap", -13, 67, -13, 26, 6, 26, 128, 0);
        box(body, "front_console", -16, 8, -46, 32, 18, 11, 160, 0);
        box(body, "rear_service", -19, 12, 35, 38, 16, 10, 0, 32);

        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.PI * 2.0D * i / 8.0D);
            float x = net.minecraft.util.Mth.cos(angle) * 31.0F;
            float z = net.minecraft.util.Mth.sin(angle) * 31.0F;
            body.addOrReplaceChild(
                    "pylon_" + i,
                    CubeListBuilder.create().texOffs((i % 6) * 32, 32)
                            .addBox(-4, 0, -4, 8, 38, 8),
                    PartPose.offset(x, 12, z)
            );
            body.addOrReplaceChild(
                    "upper_brace_" + i,
                    CubeListBuilder.create().texOffs((i % 6) * 32, 64)
                            .addBox(-3, -3, -16, 6, 6, 32),
                    PartPose.offsetAndRotation(x * 0.58F, 52, z * 0.58F, 0.0F, -angle, 0.0F)
            );
        }

        MeshDefinition windowMesh = new MeshDefinition();
        PartDefinition window = windowMesh.getRoot();
        box(window, "capsule_front", -13, 23, -18.6F, 26, 27, 1.2F, 0, 0);
        box(window, "capsule_left", -18.6F, 23, -12, 1.2F, 27, 24, 16, 0);
        box(window, "capsule_right", 17.4F, 23, -12, 1.2F, 27, 24, 32, 0);

        MeshDefinition portMesh = new MeshDefinition();
        box(portMesh.getRoot(), "energy_port", -8, 12, -52, 16, 16, 7, 0, 0);

        MeshDefinition glowMesh = new MeshDefinition();
        PartDefinition glow = glowMesh.getRoot();
        box(glow, "capsule_core", -8, 31, -19.2F, 16, 11, 1, 0, 0);
        box(glow, "console_strip", -11, 14, -47.2F, 22, 4, 1, 16, 0);

        return machine(bodyMesh, windowMesh, portMesh, glowMesh);
    }

    private static MachineModel machine(
            MeshDefinition body,
            MeshDefinition window,
            MeshDefinition port,
            MeshDefinition glow
    ) {
        return new MachineModel(
                LayerDefinition.create(body, 256, 256).bakeRoot(),
                LayerDefinition.create(window, 64, 64).bakeRoot(),
                LayerDefinition.create(port, 64, 64).bakeRoot(),
                LayerDefinition.create(glow, 64, 64).bakeRoot()
        );
    }

    private static void box(
            PartDefinition root,
            String name,
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth,
            int u,
            int v
    ) {
        root.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth),
                PartPose.ZERO
        );
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                BioTech.MODID,
                "textures/entity/multiblock/" + name + ".png"
        );
    }

    @Override
    public boolean shouldRenderOffScreen(FormedMultiblockBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private record MachineModel(ModelPart body, ModelPart window, ModelPart port, ModelPart glow) {
    }
}
