package neo.z_mods.biotech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.item.DnaInjectorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Экранный прогресс и зелёный луч, визуально вытягивающий ДНК из существа. */
@EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
public final class DnaExtractionClientHandler {
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        ItemStack held = player.getMainHandItem().getItem() instanceof DnaInjectorItem
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (held.getItem() instanceof DnaInjectorItem) {
            renderModeBadge(event.getGuiGraphics(), minecraft.font, DnaInjectorItem.getMode(held));
        }

        if (!player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof DnaInjectorItem)
                || DnaInjectorItem.getMode(stack) != DnaInjectorItem.Mode.EXTRACT) {
            return;
        }

        int duration = Math.max(1, stack.getUseDuration(player));
        float progress = Mth.clamp(1.0F - player.getUseItemRemainingTicks() / (float) duration, 0.0F, 1.0F);
        renderExtractionPanel(event.getGuiGraphics(), minecraft.font, progress);
    }

    private static void renderModeBadge(GuiGraphics graphics, Font font, DnaInjectorItem.Mode mode) {
        String label = mode == DnaInjectorItem.Mode.EXTRACT ? "ЗАБРАТЬ ДНК [V]" : "ВСТАВИТЬ ДНК [V]";
        int width = font.width(label) + 14;
        int left = graphics.guiWidth() - width - 8;
        int top = 8;
        graphics.fill(left, top, left + width, top + 20, 0xD916201D);
        border(graphics, left, top, width, 20, mode == DnaInjectorItem.Mode.EXTRACT ? 0xFF62E884 : 0xFF55DCEB);
        graphics.drawString(font, label, left + 7, top + 6,
                mode == DnaInjectorItem.Mode.EXTRACT ? 0xFF91FFA9 : 0xFF92F3FF, false);
    }

    private static void renderExtractionPanel(GuiGraphics graphics, Font font, float progress) {
        int width = 220;
        int height = 54;
        int left = (graphics.guiWidth() - width) / 2;
        int top = graphics.guiHeight() - 92;

        graphics.fill(left + 3, top + 3, left + width + 3, top + height + 3, 0x70000000);
        graphics.fill(left, top, left + width, top + height, 0xED17201D);
        border(graphics, left, top, width, height, 0xFF65756F);
        graphics.drawCenteredString(font, "ИЗВЛЕЧЕНИЕ ДНК", left + width / 2, top + 8, 0xFF83F18D);

        int barLeft = left + 13;
        int barTop = top + 27;
        int barWidth = width - 26;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 13, 0xFF080D0B);
        border(graphics, barLeft, barTop, barWidth, 13, 0xFF485850);

        int segments = 12;
        int gap = 2;
        int segmentWidth = (barWidth - 4 - gap * (segments - 1)) / segments;
        float filled = progress * segments;
        for (int i = 0; i < segments; i++) {
            int x = barLeft + 2 + i * (segmentWidth + gap);
            int color = i < filled ? 0xFF52E878 : 0xFF18231F;
            graphics.fill(x, barTop + 2, x + segmentWidth, barTop + 11, color);
            if (i < filled) {
                graphics.fill(x + 1, barTop + 3, x + segmentWidth - 1, barTop + 5, 0xFF8BFF9D);
            }
        }
        graphics.drawCenteredString(font, Math.round(progress * 100.0F) + "%", left + width / 2, top + 43, 0xFFDCE9E3);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof DnaInjectorItem)
                || DnaInjectorItem.getMode(stack) != DnaInjectorItem.Mode.EXTRACT) {
            return;
        }
        LivingEntity target = DnaInjectorItem.findTarget(minecraft.level, stack);
        if (target == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Vec3 start = player.getEyePosition(partialTick)
                .add(player.getLookAngle().scale(0.72D))
                .subtract(camera);
        Vec3 end = target.getPosition(partialTick)
                .add(0.0D, target.getBbHeight() * 0.58D, 0.0D)
                .subtract(camera);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        int segments = 28;
        double time = (minecraft.level.getGameTime() + partialTick) * 0.22D;
        Vec3 axis = end.subtract(start).normalize();
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 0.0001D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = axis.cross(side).normalize();

        for (int strand = -2; strand <= 2; strand++) {
            double radius = strand * 0.012D;
            Vec3 previous = start.add(side.scale(radius));
            for (int i = 1; i <= segments; i++) {
                double t = i / (double) segments;
                Vec3 point = start.lerp(end, t);
                double envelope = Math.sin(Math.PI * t);
                double phase = time + i * 1.18D + strand * 0.8D;
                point = point.add(side.scale((Math.sin(phase) * 0.040D + radius) * envelope))
                        .add(up.scale(Math.cos(phase * 1.17D) * 0.030D * envelope));
                int alpha = strand == 0 ? 255 : 175;
                int red = strand == 0 ? 185 : 68;
                addLine(consumer, poseStack, matrix, previous, point, red, 255, 126, alpha);
                previous = point;
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void addLine(
            VertexConsumer consumer,
            PoseStack poseStack,
            Matrix4f matrix,
            Vec3 start,
            Vec3 end,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        Vec3 normal = end.subtract(start).normalize();
        consumer.addVertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private DnaExtractionClientHandler() {
    }
}
