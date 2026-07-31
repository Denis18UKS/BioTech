package neo.z_mods.biotech.client;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.network.ClientVibrosData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Клиентские эффекты биовыброса.
 *
 * <p>Обзор больше не перекрывается коротким зелёным туманом. Цвет атмосферы
 * меняется мягко, а основная зелёная буря формируется цветом неба, облаков,
 * дождя и опциональным шейдерпаком.</p>
 */
@EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
public final class VibrosClientEffects {
    private static long clientTicks;

    private VibrosClientEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        clientTicks++;

        if (minecraft.player == null || minecraft.level == null) {
            ClientVibrosData.clear();
            return;
        }

        ClientVibrosData.clientTick();

        if (!ClientVibrosData.isActive() || clientTicks % 4L != 0L) {
            return;
        }

        // Небольшое количество спор создаёт объём без сплошной стены частиц.
        for (int i = 0; i < 2; i++) {
            double x = minecraft.player.getX() + (minecraft.level.random.nextDouble() - 0.5D) * 24.0D;
            double y = minecraft.player.getY() + 2.0D + minecraft.level.random.nextDouble() * 10.0D;
            double z = minecraft.player.getZ() + (minecraft.level.random.nextDouble() - 0.5D) * 24.0D;

            minecraft.level.addParticle(
                    ParticleTypes.WARPED_SPORE,
                    x, y, z,
                    0.0D,
                    -0.002D - minecraft.level.random.nextDouble() * 0.006D,
                    0.0D
            );
        }
    }

    /**
     * Только лёгкая цветокоррекция дальнего тумана. Дальность прорисовки
     * не уменьшается и событие RenderFog больше не отменяется.
     */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientVibrosData.isActive()) {
            return;
        }

        float pulse = 0.5F + 0.5F * Mth.sin((clientTicks + (float) event.getPartialTick()) * 0.018F);
        float strength = 0.12F + pulse * 0.04F;

        event.setRed(Mth.lerp(strength, event.getRed(), 0.025F));
        event.setGreen(Mth.lerp(strength, event.getGreen(), 0.235F));
        event.setBlue(Mth.lerp(strength, event.getBlue(), 0.125F));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientVibrosData.isActive()) {
            return;
        }

        float time = clientTicks + (float) event.getPartialTick();
        event.setRoll(event.getRoll() + Mth.sin(time * 0.095F) * 0.18F);
        event.setPitch(event.getPitch() + Mth.sin(time * 0.061F) * 0.07F);
        event.setYaw(event.getYaw() + Mth.cos(time * 0.052F) * 0.06F);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        if (ClientVibrosData.isWarning()) {
            renderWarning(graphics, minecraft.font);
        } else if (ClientVibrosData.isActive()) {
            renderActive(graphics, minecraft.font);
        }
    }

    private static void renderWarning(GuiGraphics graphics, Font font) {
        int screenWidth = graphics.guiWidth();
        int panelWidth = Math.min(286, screenWidth - 20);
        int panelHeight = 64;
        int left = (screenWidth - panelWidth) / 2;
        int top = 18;

        fillBorderedPanel(graphics, left, top, panelWidth, panelHeight, 0xD9141C1A, 0xFF5A7567);
        graphics.drawCenteredString(font, "ПРЕДУПРЕЖДЕНИЕ О БИОВЫБРОСЕ", screenWidth / 2, top + 8, 0xFFE1F1E8);
        graphics.drawCenteredString(
                font,
                "Биовыброс через: " + formatTicks(ClientVibrosData.remainingTicks()),
                screenWidth / 2,
                top + 25,
                0xFF86E36D
        );

        int barLeft = left + 14;
        int barTop = top + 43;
        int barWidth = panelWidth - 28;
        int barHeight = 10;
        graphics.fill(barLeft - 2, barTop - 2, barLeft + barWidth + 2, barTop + barHeight + 2, 0xFF28322E);

        float remainingFraction = ClientVibrosData.totalTicks() <= 0
                ? 0.0F
                : Mth.clamp(
                        ClientVibrosData.remainingTicks() / (float) ClientVibrosData.totalTicks(),
                        0.0F,
                        1.0F
                );

        int segments = 5;
        int gap = 4;
        int segmentWidth = (barWidth - gap * (segments - 1)) / segments;
        int litSegments = Mth.ceil(remainingFraction * segments);

        for (int i = 0; i < segments; i++) {
            int x1 = barLeft + i * (segmentWidth + gap);
            int x2 = x1 + segmentWidth;
            graphics.fill(x1, barTop, x2, barTop + barHeight, i < litSegments ? 0xFF69C94D : 0xFF202925);
        }
    }

    private static void renderActive(GuiGraphics graphics, Font font) {
        // Никакой полноэкранной зелёной заливки: только компактный индикатор.
        int screenWidth = graphics.guiWidth();
        int panelWidth = 184;
        int panelHeight = 39;
        int left = (screenWidth - panelWidth) / 2;
        int top = 12;

        fillBorderedPanel(graphics, left, top, panelWidth, panelHeight, 0xD916211D, 0xFF64A46F);
        graphics.drawCenteredString(font, "БИОВЫБРОС", screenWidth / 2, top + 7, 0xFF8EF07B);
        graphics.drawCenteredString(
                font,
                "До окончания: " + formatTicks(ClientVibrosData.remainingTicks()),
                screenWidth / 2,
                top + 22,
                0xFFD8E8DF
        );
    }

    private static void fillBorderedPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            int backgroundColor,
            int borderColor
    ) {
        graphics.fill(left, top, left + width, top + height, backgroundColor);
        graphics.fill(left, top, left + width, top + 1, borderColor);
        graphics.fill(left, top + height - 1, left + width, top + height, borderColor);
        graphics.fill(left, top, left + 1, top + height, borderColor);
        graphics.fill(left + width - 1, top, left + width, top + height, borderColor);
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = (Math.max(0, ticks) + 19) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%02d:%02d".formatted(minutes, seconds);
    }
}
