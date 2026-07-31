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

    private static float panelProgress;
    private static float displayedBarFraction = 1.0F;
    private static int displayedPhase = Integer.MIN_VALUE;

    private VibrosClientEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        clientTicks++;

        if (minecraft.player == null || minecraft.level == null) {
            ClientVibrosData.clear();
            panelProgress = 0.0F;
            displayedBarFraction = 1.0F;
            displayedPhase = Integer.MIN_VALUE;
            return;
        }

        ClientVibrosData.clientTick();
        updatePanelAnimation();

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

    private static void updatePanelAnimation() {
        int phase = ClientVibrosData.phase();
        boolean visible = ClientVibrosData.isWarning()
                || ClientVibrosData.isActive()
                || ClientVibrosData.isDissipating();

        panelProgress = approach(panelProgress, visible ? 1.0F : 0.0F, visible ? 0.16F : 0.20F);

        if (phase != displayedPhase) {
            displayedPhase = phase;
            displayedBarFraction = ClientVibrosData.remainingFraction();
        }

        if (visible) {
            // Никогда не восстанавливаем уже опустевшую часть полосы внутри одной фазы.
            displayedBarFraction = Math.min(displayedBarFraction, ClientVibrosData.remainingFraction());
        } else if (panelProgress <= 0.001F) {
            displayedPhase = Integer.MIN_VALUE;
            displayedBarFraction = 1.0F;
        }
    }

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
        if (minecraft.player == null || panelProgress <= 0.003F) {
            return;
        }

        renderUnifiedPanel(event.getGuiGraphics(), minecraft.font);
    }

    private static void renderUnifiedPanel(GuiGraphics graphics, Font font) {
        int panelWidth = Math.min(218, graphics.guiWidth() - 12);
        int panelHeight = 48;

        float eased = easeOutCubic(panelProgress);
        int left = Math.round(-panelWidth - 6 + (panelWidth + 12) * eased);
        int top = (graphics.guiHeight() - panelHeight) / 2;

        int remaining = ClientVibrosData.remainingTicks();
        String text = ClientVibrosData.isWarning()
                ? "Биовыброс через: " + formatTicks(remaining)
                : "Биовыброс закончится через: " + formatTicks(remaining);

        int accent = ClientVibrosData.isDissipating() ? 0xFF9CFF82 : 0xFF6BCB5E;
        int background = 0xE8272D2A;

        graphics.fill(left + 3, top + 3, left + panelWidth + 3, top + panelHeight + 3, 0x65000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, background);
        drawBorder(graphics, left, top, panelWidth, panelHeight, 0xFF777D7A);
        graphics.fill(left + 5, top + 5, left + panelWidth - 5, top + 6, 0xFF9A9F9C);

        graphics.drawString(font, text, left + 10, top + 13, 0xFFE1E6E3, false);

        int barLeft = left + 10;
        int barTop = top + 31;
        int barWidth = panelWidth - 20;
        int barHeight = 8;
        int segments = 6;
        int gap = 4;
        int segmentWidth = (barWidth - gap * (segments - 1)) / segments;
        float scaled = Mth.clamp(displayedBarFraction, 0.0F, 1.0F) * segments;

        for (int i = 0; i < segments; i++) {
            int x1 = barLeft + i * (segmentWidth + gap);
            int x2 = x1 + segmentWidth;
            graphics.fill(x1, barTop, x2, barTop + barHeight, 0xFF111614);

            float localFill = Mth.clamp(scaled - i, 0.0F, 1.0F);
            int fillWidth = Math.round(segmentWidth * localFill);
            if (fillWidth > 0) {
                graphics.fill(x1, barTop, x1 + fillWidth, barTop + barHeight, accent);
                graphics.fill(x1 + 1, barTop + 1, x1 + Math.max(1, fillWidth - 1), barTop + 3, 0x607FFF70);
            }
        }
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int width, int height, int color) {
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
