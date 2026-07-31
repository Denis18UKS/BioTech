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

/** Клиентские частицы, цвет атмосферы, дрожь камеры и HUD биовыброса. */
@EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
public final class VibrosClientEffects {
    private static long clientTicks;

    private static float warningPanelProgress;
    private static float warningBarFraction = 1.0F;
    private static boolean warningWasActive;

    private VibrosClientEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        clientTicks++;

        if (minecraft.player == null || minecraft.level == null) {
            ClientVibrosData.clear();
            warningPanelProgress = 0.0F;
            warningBarFraction = 1.0F;
            warningWasActive = false;
            return;
        }

        ClientVibrosData.clientTick();
        updateWarningAnimation();

        float storm = VibrosShaderManager.currentStormStrength();
        float dissolve = VibrosShaderManager.currentDissolveProgress();
        if (storm < 0.20F || clientTicks % 4L != 0L) {
            return;
        }

        int count = ClientVibrosData.isActive() ? 2 : 1;
        for (int i = 0; i < count; i++) {
            double x = minecraft.player.getX() + (minecraft.level.random.nextDouble() - 0.5D) * 25.0D;
            double y = minecraft.player.getY() + 2.0D + minecraft.level.random.nextDouble() * 11.0D;
            double z = minecraft.player.getZ() + (minecraft.level.random.nextDouble() - 0.5D) * 25.0D;

            minecraft.level.addParticle(
                    dissolve > 0.05F ? ParticleTypes.GLOW : ParticleTypes.WARPED_SPORE,
                    x, y, z,
                    0.0D,
                    -0.001D - minecraft.level.random.nextDouble() * 0.005D,
                    0.0D
            );
        }
    }

    private static void updateWarningAnimation() {
        boolean warning = ClientVibrosData.isWarning();
        float target = warning ? 1.0F : 0.0F;
        float speed = warning ? 0.145F : 0.205F;
        warningPanelProgress = approach(warningPanelProgress, target, speed);

        if (warning && !warningWasActive) {
            warningWasActive = true;
            warningBarFraction = ClientVibrosData.remainingFraction();
        }

        if (warning) {
            // Только уменьшаем отображаемую долю. Даже старый сетевой пакет не
            // сможет визуально восстановить уже опустевшую часть шкалы.
            warningBarFraction = Math.min(warningBarFraction, ClientVibrosData.remainingFraction());
        } else if (warningPanelProgress <= 0.001F) {
            warningWasActive = false;
            warningBarFraction = 1.0F;
        }
    }

    /**
     * Мягко окрашивает только дальнюю атмосферу. Дальность обзора не меняется,
     * поэтому зелёная стена тумана не появляется.
     */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float storm = VibrosShaderManager.currentStormStrength();
        if (storm <= 0.001F) {
            return;
        }

        float pulse = 0.5F + 0.5F * Mth.sin((clientTicks + (float) event.getPartialTick()) * 0.018F);
        float flash = VibrosShaderManager.currentFlashIntensity();
        float dissolveGlow = Mth.sin(VibrosShaderManager.currentDissolveProgress() * (float) Math.PI);

        float redTarget = 0.012F + flash * 0.020F;
        float greenTarget = 0.105F + pulse * 0.022F + flash * 0.185F + dissolveGlow * 0.10F;
        float blueTarget = 0.064F + flash * 0.046F;
        float amount = Mth.clamp(storm * 0.43F, 0.0F, 0.55F);

        event.setRed(Mth.lerp(amount, event.getRed(), redTarget));
        event.setGreen(Mth.lerp(amount, event.getGreen(), greenTarget));
        event.setBlue(Mth.lerp(amount, event.getBlue(), blueTarget));
    }

    /**
     * В последние десять секунд предупреждения дрожь плавно нарастает. Во время
     * самого выброса остаётся слабая низкочастотная вибрация, а при рассеивании
     * она постепенно исчезает.
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float countdown = VibrosShaderManager.currentCountdownStrength();
        float dissolve = VibrosShaderManager.currentDissolveProgress();

        float activeRumble = ClientVibrosData.isActive() ? 0.24F : 0.0F;
        float dissipatingRumble = ClientVibrosData.isDissipating() ? (1.0F - dissolve) * 0.18F : 0.0F;
        float strength = Mth.clamp(countdown + activeRumble + dissipatingRumble, 0.0F, 1.15F);
        if (strength <= 0.001F) {
            return;
        }

        float time = clientTicks + (float) event.getPartialTick();
        float low = Mth.sin(time * 0.105F) + Mth.sin(time * 0.041F + 1.7F) * 0.55F;
        float fine = Mth.sin(time * 0.37F + 0.4F) * countdown;

        event.setRoll(event.getRoll() + (low * 0.28F + fine * 0.11F) * strength);
        event.setPitch(event.getPitch() + Mth.sin(time * 0.071F) * 0.15F * strength);
        event.setYaw(event.getYaw() + Mth.cos(time * 0.057F) * 0.13F * strength);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();

        // Панель продолжает уезжать влево после завершения предупреждения,
        // вместо мгновенного исчезновения в один кадр.
        if (warningPanelProgress > 0.003F) {
            renderWarning(graphics, minecraft.font);
        }

        if (ClientVibrosData.isActive()) {
            renderActive(graphics, minecraft.font);
        } else if (ClientVibrosData.isDissipating()) {
            renderDissipating(graphics, minecraft.font);
        }
    }

    private static void renderWarning(GuiGraphics graphics, Font font) {
        int panelWidth = Math.min(282, graphics.guiWidth() - 16);
        int panelHeight = 86;

        float eased = easeOutCubic(warningPanelProgress);
        int left = Math.round(-panelWidth - 7 + (panelWidth + 7) * eased);
        int top = (graphics.guiHeight() - panelHeight) / 2;

        int pulse = ClientVibrosData.remainingTicks() <= 200
                ? (int) (28.0F + 22.0F * (0.5F + 0.5F * Mth.sin(clientTicks * 0.30F)))
                : 18;
        int accentGreen = Math.min(255, 0x9B + pulse);
        int accent = 0xFF000000 | (0x5F << 16) | (accentGreen << 8) | 0x58;

        // Мягкая тень и двухслойная рамка дают панели глубину без текстуры.
        graphics.fill(left + 5, top + 5, left + panelWidth + 5, top + panelHeight + 5, 0x70000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEA111918);
        drawBorder(graphics, left, top, panelWidth, panelHeight, 0xFF344742);
        drawBorder(graphics, left + 3, top + 3, panelWidth - 6, panelHeight - 6, 0xFF1E2A27);

        graphics.drawString(font, "ПРЕДУПРЕЖДЕНИЕ О ВЫБРОСЕ", left + 17, top + 12, 0xFFE4ECE8, false);

        int meterLeft = left + 15;
        int meterTop = top + 34;
        int meterWidth = panelWidth - 30;
        int meterHeight = 38;
        graphics.fill(meterLeft, meterTop, meterLeft + meterWidth, meterTop + meterHeight, 0xE72A302E);
        drawBorder(graphics, meterLeft, meterTop, meterWidth, meterHeight, 0xFF6B716E);
        graphics.fill(meterLeft + 3, meterTop + 3, meterLeft + meterWidth - 3, meterTop + 4, 0xFF969B98);

        graphics.drawString(font, "Биовыброс через:", meterLeft + 11, meterTop + 9, 0xFFD7DDDA, false);
        String timer = formatTicks(ClientVibrosData.remainingTicks());
        int timerColor = ClientVibrosData.remainingTicks() <= 200 ? 0xFFFFC95C : 0xFF86D06C;
        graphics.drawString(font, timer, meterLeft + 117, meterTop + 9, timerColor, false);

        int barLeft = meterLeft + 11;
        int barTop = meterTop + 24;
        int barWidth = meterWidth - 22;
        int barHeight = 8;
        int segments = 6;
        int gap = 4;
        int segmentWidth = (barWidth - gap * (segments - 1)) / segments;
        float scaled = Mth.clamp(warningBarFraction, 0.0F, 1.0F) * segments;

        for (int i = 0; i < segments; i++) {
            int x1 = barLeft + i * (segmentWidth + gap);
            int x2 = x1 + segmentWidth;
            graphics.fill(x1, barTop, x2, barTop + barHeight, 0xFF151B19);

            float localFill = Mth.clamp(scaled - i, 0.0F, 1.0F);
            int fillWidth = Math.round(segmentWidth * localFill);
            if (fillWidth > 0) {
                graphics.fill(x1, barTop, x1 + fillWidth, barTop + barHeight, accent);
                graphics.fill(x1 + 1, barTop + 1, x1 + Math.max(1, fillWidth - 1), barTop + 3, 0x606FEA62);
            }
        }
    }

    private static void renderActive(GuiGraphics graphics, Font font) {
        renderCompactPanel(
                graphics,
                font,
                "БИОВЫБРОС",
                "До окончания: " + formatTicks(ClientVibrosData.remainingTicks()),
                0xFF8EF07B,
                0xFF64A46F
        );
    }

    private static void renderDissipating(GuiGraphics graphics, Font font) {
        renderCompactPanel(
                graphics,
                font,
                "ЯДРО РАССЕИВАЕТСЯ",
                "Стабилизация: " + formatTicks(ClientVibrosData.remainingTicks()),
                0xFFB1FF8A,
                0xFF83D69A
        );
    }

    private static void renderCompactPanel(
            GuiGraphics graphics,
            Font font,
            String title,
            String subtitle,
            int titleColor,
            int borderColor
    ) {
        int screenWidth = graphics.guiWidth();
        int panelWidth = 205;
        int panelHeight = 39;
        int left = (screenWidth - panelWidth) / 2;
        int top = 12;

        fillBorderedPanel(graphics, left, top, panelWidth, panelHeight, 0xD916211D, borderColor);
        graphics.drawCenteredString(font, title, screenWidth / 2, top + 7, titleColor);
        graphics.drawCenteredString(font, subtitle, screenWidth / 2, top + 22, 0xFFD8E8DF);
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
        drawBorder(graphics, left, top, width, height, borderColor);
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            int color
    ) {
        graphics.fill(left, top, left + width, top + 1, color);
        graphics.fill(left, top + height - 1, left + width, top + height, color);
        graphics.fill(left, top, left + 1, top + height, color);
        graphics.fill(left + width - 1, top, left + width, top + height, color);
    }

    private static float approach(float current, float target, float speed) {
        float next = current + (target - current) * speed;
        return Math.abs(target - next) < 0.0005F ? target : next;
    }

    private static float easeOutCubic(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = (Math.max(0, ticks) + 19) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%02d:%02d".formatted(minutes, seconds);
    }
}
