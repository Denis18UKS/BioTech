package neo.z_mods.biotech.client;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.network.ClientVibrosData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Клиентские частицы, атмосфера, дрожь камеры и компактный HUD биовыброса. */
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
        float immersion = cloudImmersion((float) minecraft.gameRenderer.getMainCamera().getPosition().y, storm);

        // Частицы внутри слоя появляются только высоко в самих тучах. На земле
        // больше нет ощущения, будто облачная масса опустилась прямо к игроку.
        if (immersion > 0.08F && clientTicks % 3L == 0L) {
            int localCount = Math.max(1, Math.round(immersion * (storm > 0.25F ? 3.0F : 2.0F)));
            for (int i = 0; i < localCount; i++) {
                double x = minecraft.player.getX() + (minecraft.level.random.nextDouble() - 0.5D) * 10.0D;
                double y = minecraft.player.getEyeY() + (minecraft.level.random.nextDouble() - 0.5D) * 5.0D;
                double z = minecraft.player.getZ() + (minecraft.level.random.nextDouble() - 0.5D) * 10.0D;

                minecraft.level.addParticle(
                        storm > 0.25F ? ParticleTypes.WARPED_SPORE : ParticleTypes.CLOUD,
                        x, y, z,
                        (minecraft.level.random.nextDouble() - 0.5D) * 0.006D,
                        -0.0008D,
                        (minecraft.level.random.nextDouble() - 0.5D) * 0.006D
                );
            }
        }

        if (storm < 0.20F || clientTicks % 5L != 0L) {
            return;
        }

        int count = ClientVibrosData.isActive() ? 2 : 1;
        for (int i = 0; i < count; i++) {
            double x = minecraft.player.getX() + (minecraft.level.random.nextDouble() - 0.5D) * 25.0D;
            double y = minecraft.player.getY() + 3.0D + minecraft.level.random.nextDouble() * 12.0D;
            double z = minecraft.player.getZ() + (minecraft.level.random.nextDouble() - 0.5D) * 25.0D;

            minecraft.level.addParticle(
                    dissolve > 0.05F ? ParticleTypes.GLOW : ParticleTypes.WARPED_SPORE,
                    x, y, z,
                    0.0D,
                    -0.001D - minecraft.level.random.nextDouble() * 0.004D,
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
            displayedBarFraction = Math.min(displayedBarFraction, ClientVibrosData.remainingFraction());
        } else if (panelProgress <= 0.001F) {
            displayedPhase = Integer.MIN_VALUE;
            displayedBarFraction = 1.0F;
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float storm = VibrosShaderManager.currentStormStrength();
        float immersion = cloudImmersion((float) event.getCamera().getPosition().y, storm);

        if (storm <= 0.001F && immersion <= 0.001F) {
            return;
        }

        float pulse = 0.5F + 0.5F * Mth.sin((clientTicks + (float) event.getPartialTick()) * 0.018F);
        float flash = VibrosShaderManager.currentFlashIntensity();
        float dissolveGlow = Mth.sin(VibrosShaderManager.currentDissolveProgress() * (float) Math.PI);

        float stormRed = 0.010F + flash * 0.018F;
        float stormGreen = 0.096F + pulse * 0.020F + flash * 0.175F + dissolveGlow * 0.090F;
        float stormBlue = 0.056F + flash * 0.040F;
        float stormAmount = Mth.clamp(storm * 0.36F, 0.0F, 0.48F);

        float calmCloudRed = 0.72F;
        float calmCloudGreen = 0.76F;
        float calmCloudBlue = 0.82F;

        float cloudRed = Mth.lerp(storm, calmCloudRed, 0.050F + flash * 0.030F);
        float cloudGreen = Mth.lerp(storm, calmCloudGreen, 0.235F + flash * 0.260F);
        float cloudBlue = Mth.lerp(storm, calmCloudBlue, 0.135F + flash * 0.060F);

        float cloudAmount = Mth.clamp(immersion * 0.82F, 0.0F, 0.86F);

        float red = Mth.lerp(stormAmount, event.getRed(), stormRed);
        float green = Mth.lerp(stormAmount, event.getGreen(), stormGreen);
        float blue = Mth.lerp(stormAmount, event.getBlue(), stormBlue);

        event.setRed(Mth.lerp(cloudAmount, red, cloudRed));
        event.setGreen(Mth.lerp(cloudAmount, green, cloudGreen));
        event.setBlue(Mth.lerp(cloudAmount, blue, cloudBlue));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }

        float storm = VibrosShaderManager.currentStormStrength();
        float immersion = cloudImmersion((float) event.getCamera().getPosition().y, storm);
        if (immersion <= 0.001F) {
            return;
        }

        float targetNear = Mth.lerp(storm, 5.0F, 1.8F);
        float targetFar = Mth.lerp(storm, 68.0F, 32.0F);
        float amount = Mth.clamp(immersion * 0.88F, 0.0F, 0.92F);

        event.setNearPlaneDistance(Mth.lerp(amount, event.getNearPlaneDistance(), targetNear));
        event.setFarPlaneDistance(Mth.lerp(amount, event.getFarPlaneDistance(), targetFar));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float countdown = VibrosShaderManager.currentCountdownStrength();
        float dissolve = VibrosShaderManager.currentDissolveProgress();
        float storm = VibrosShaderManager.currentStormStrength();
        float immersion = cloudImmersion((float) event.getCamera().getPosition().y, storm);

        float activeRumble = ClientVibrosData.isActive() ? 0.20F : 0.0F;
        float dissipatingRumble = ClientVibrosData.isDissipating() ? (1.0F - dissolve) * 0.15F : 0.0F;
        float cloudTurbulence = immersion * storm * 0.16F;
        float strength = Mth.clamp(
                countdown + activeRumble + dissipatingRumble + cloudTurbulence,
                0.0F,
                1.10F
        );

        if (strength <= 0.001F) {
            return;
        }

        float time = clientTicks + (float) event.getPartialTick();
        float low = Mth.sin(time * 0.105F) + Mth.sin(time * 0.041F + 1.7F) * 0.55F;
        float fine = Mth.sin(time * 0.37F + 0.4F) * (countdown + cloudTurbulence);

        event.setRoll(event.getRoll() + (low * 0.23F + fine * 0.09F) * strength);
        event.setPitch(event.getPitch() + Mth.sin(time * 0.071F) * 0.12F * strength);
        event.setYaw(event.getYaw() + Mth.cos(time * 0.057F) * 0.10F * strength);
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
        // Панель закреплена в левом верхнем углу и занимает заметно меньше места,
        // чем прежняя центральная версия.
        int panelWidth = Math.min(184, graphics.guiWidth() - 16);
        int panelHeight = 38;
        int targetLeft = 8;

        float eased = easeOutCubic(panelProgress);
        int left = Math.round(-panelWidth - 6 + (panelWidth + targetLeft + 6) * eased);
        int top = 8;

        int remaining = ClientVibrosData.remainingTicks();
        String text;
        if (ClientVibrosData.isWarning()) {
            text = "Биовыброс через: " + formatTicks(remaining);
        } else if (ClientVibrosData.isDissipating()) {
            text = "Рассеивание БВ: " + formatTicks(remaining);
        } else {
            text = "До конца БВ: " + formatTicks(remaining);
        }

        int accent = ClientVibrosData.isDissipating() ? 0xFF9CFF82 : 0xFF6BCB5E;
        int background = 0xDC272D2A;

        graphics.fill(left + 3, top + 3, left + panelWidth + 3, top + panelHeight + 3, 0x55000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, background);
        drawBorder(graphics, left, top, panelWidth, panelHeight, 0xFF777D7A);
        graphics.fill(left + 5, top + 5, left + panelWidth - 5, top + 6, 0xFF9A9F9C);

        graphics.drawString(font, text, left + 9, top + 10, 0xFFE1E6E3, false);

        int barLeft = left + 9;
        int barTop = top + 25;
        int barWidth = panelWidth - 18;
        int barHeight = 7;
        int segments = 6;
        int gap = 3;
        int segmentWidth = Math.max(2, (barWidth - gap * (segments - 1)) / segments);
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

    /**
     * Облачное ядро теперь находится существенно выше максимальной высоты
     * обычного строительства. С земли в слой нельзя войти случайно.
     */
    private static float cloudImmersion(float worldY, float storm) {
        float calmLayer = band(worldY, 238.0F, 276.0F, 372.0F, 430.0F);
        float stormLayer = band(worldY, 296.0F, 338.0F, 510.0F, 590.0F);
        return Mth.lerp(Mth.clamp(storm, 0.0F, 1.0F), calmLayer, stormLayer);
    }

    private static float band(float value, float bottom0, float bottom1, float top0, float top1) {
        return smoothstep(bottom0, bottom1, value) * (1.0F - smoothstep(top0, top1, value));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
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
