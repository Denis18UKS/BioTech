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

        float redTarget = 0.014F + flash * 0.025F;
        float greenTarget = 0.115F + pulse * 0.025F + flash * 0.19F + dissolveGlow * 0.10F;
        float blueTarget = 0.073F + flash * 0.055F;
        float amount = Mth.clamp(storm * 0.46F, 0.0F, 0.58F);

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
        float storm = VibrosShaderManager.currentStormStrength();
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
        if (ClientVibrosData.isWarning()) {
            renderWarning(graphics, minecraft.font);
        } else if (ClientVibrosData.isActive()) {
            renderActive(graphics, minecraft.font);
        } else if (ClientVibrosData.isDissipating()) {
            renderDissipating(graphics, minecraft.font);
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
                ClientVibrosData.remainingTicks() <= 200 ? 0xFFFFC95C : 0xFF86E36D
        );

        int barLeft = left + 14;
        int barTop = top + 43;
        int barWidth = panelWidth - 28;
        int barHeight = 10;
        graphics.fill(barLeft - 2, barTop - 2, barLeft + barWidth + 2, barTop + barHeight + 2, 0xFF28322E);

        float remainingFraction = ClientVibrosData.remainingFraction();
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
