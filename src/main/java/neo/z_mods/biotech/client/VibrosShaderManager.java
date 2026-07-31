package neo.z_mods.biotech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.network.ClientVibrosData;
import neo.z_mods.biotech.VibrosEventHandler;
import neo.z_mods.biotech.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Единый встроенный шейдер неба BioTech.
 *
 * <p>Он постоянно заменяет ванильное небо спокойными объёмными тучами и
 * плавно проходит стадии: спокойствие → предупреждение → последние 10 секунд
 * → активный биовыброс → рассеивание → спокойствие.</p>
 */
public final class VibrosShaderManager {
    private static final ResourceLocation SKY_SHADER_LOCATION = ResourceLocation.fromNamespaceAndPath(
            BioTech.MODID,
            "vibros_sky"
    );
    private static final ResourceLocation SKY_SHADER_JSON = ResourceLocation.fromNamespaceAndPath(
            BioTech.MODID,
            "shaders/core/vibros_sky.json"
    );

    private static final KeyMapping TOGGLE_SHADER = new KeyMapping(
            "key.biotech.toggle_vibros_shader",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.biotech"
    );

    private static final RandomSource RANDOM = RandomSource.create();
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static ShaderInstance skyShader;
    private static boolean shaderLoadFailed;
    private static String shaderLoadError = "";
    private static boolean manualPreview;

    private static float stormStrength;
    private static float warningProgress;
    private static float countdownStrength;
    private static float dissolveProgress;
    private static int previousPhase = Integer.MIN_VALUE;

    private static long animationEpochNanos = System.nanoTime();
    private static long nextFlashNanos = Long.MAX_VALUE;
    private static long flashStartNanos = Long.MIN_VALUE;
    private static long flashDurationNanos;
    private static long rumbleNanos;
    private static boolean flashRunning;
    private static boolean rumblePlayed;
    private static float flashWorldX;
    private static float flashWorldY;
    private static float flashWorldZ;
    private static float flashPitch = 1.0F;
    private static float flashVolume = 1.0F;

    private VibrosShaderManager() {
    }

    /** После успешной загрузки шейдер рисуется постоянно и заменяет ванильное небо. */
    public static boolean shouldRenderSky() {
        return skyShader != null;
    }

    public static boolean isManualPreview() {
        return manualPreview;
    }

    public static boolean isShaderReady() {
        return skyShader != null;
    }

    public static float currentStormStrength() {
        return stormStrength;
    }

    public static float currentWarningProgress() {
        return warningProgress;
    }

    public static float currentCountdownStrength() {
        return countdownStrength;
    }

    public static float currentDissolveProgress() {
        return dissolveProgress;
    }

    public static float currentFlashIntensity() {
        return computeFlashIntensity(System.nanoTime());
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerShader(RegisterShadersEvent event) {
            skyShader = null;
            shaderLoadFailed = false;
            shaderLoadError = "";

            if (event.getResourceProvider().getResource(SKY_SHADER_JSON).isEmpty()) {
                markShaderLoadFailed(
                        "Не найден ресурс " + SKY_SHADER_JSON
                                + ". Проверьте src/main/resources/assets/biotech/shaders/core/vibros_sky.json"
                );
                return;
            }

            try {
                ShaderInstance shader = new ShaderInstance(
                        event.getResourceProvider(),
                        SKY_SHADER_LOCATION,
                        DefaultVertexFormat.POSITION
                );

                event.registerShader(shader, loadedShader -> {
                    skyShader = loadedShader;
                    shaderLoadFailed = false;
                    shaderLoadError = "";
                    animationEpochNanos = System.nanoTime();
                    BioTech.LOGGER.info("BioTech seamless volumetric sky shader loaded successfully");
                });
            } catch (Exception exception) {
                markShaderLoadFailed(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                BioTech.LOGGER.error(
                        "BioTech sky shader could not be loaded. Minecraft will continue with vanilla sky.",
                        exception
                );
            }
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_SHADER);
        }

        private static void markShaderLoadFailed(String error) {
            skyShader = null;
            shaderLoadFailed = true;
            shaderLoadError = error == null ? "неизвестная ошибка" : error;
            BioTech.LOGGER.error("BioTech sky shader disabled: {}", shaderLoadError);
        }
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();

            while (TOGGLE_SHADER.consumeClick()) {
                handlePreviewKey(minecraft);
            }

            if (minecraft.level == null || minecraft.player == null) {
                manualPreview = false;
                stormStrength = 0.0F;
                warningProgress = 0.0F;
                countdownStrength = 0.0F;
                dissolveProgress = 0.0F;
                previousPhase = Integer.MIN_VALUE;
                resetFlashState();
                return;
            }

            int phase = ClientVibrosData.phase();
            if (phase != previousPhase) {
                onPhaseChanged(minecraft, previousPhase, phase);
                previousPhase = phase;
            }

            float targetStorm = 0.0F;
            float targetWarning = 0.0F;
            float targetCountdown = 0.0F;
            float targetDissolve = 0.0F;

            if (manualPreview) {
                targetStorm = 1.0F;
                targetWarning = 1.0F;
                targetCountdown = 0.22F;
            } else if (ClientVibrosData.isWarning()) {
                float progress = ClientVibrosData.phaseProgress();
                targetWarning = smoothstep(0.0F, 1.0F, progress);
                targetStorm = smoothstep(0.06F, 1.0F, progress);
                targetCountdown = smoothstep(200.0F, 0.0F, ClientVibrosData.remainingTicks());
            } else if (ClientVibrosData.isActive()) {
                targetStorm = 1.0F;
                targetWarning = 1.0F;
                targetCountdown = 0.18F;
            } else if (ClientVibrosData.isDissipating()) {
                float progress = ClientVibrosData.phaseProgress();
                targetDissolve = progress;
                targetStorm = 1.0F - smoothstep(0.08F, 1.0F, progress);
                targetWarning = targetStorm;
                targetCountdown = Math.max(0.0F, 1.0F - progress * 3.2F);
            }

            stormStrength = approach(stormStrength, targetStorm, 0.045F);
            warningProgress = approach(warningProgress, targetWarning, 0.040F);
            countdownStrength = approach(countdownStrength, targetCountdown, 0.105F);
            dissolveProgress = approach(dissolveProgress, targetDissolve, 0.070F);

            float flashActivity = computeFlashActivity();
            long now = System.nanoTime();
            if (flashActivity > 0.08F) {
                updateFlashAndSound(minecraft, now, flashActivity);
            } else {
                resetFlashState();
            }
        }

        @SubscribeEvent
        public static void renderVibrosSky(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !shouldRenderSky()) {
                return;
            }

            ShaderInstance shader = skyShader;
            Minecraft minecraft = Minecraft.getInstance();
            if (shader == null || minecraft.level == null) {
                return;
            }

            Matrix4f inverseView = new Matrix4f(event.getModelViewMatrix()).invert();
            Matrix4f inverseProjection = new Matrix4f(event.getProjectionMatrix()).invert();
            float timeSeconds = (System.nanoTime() - animationEpochNanos) / (float) NANOS_PER_SECOND;
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float sunAngle = minecraft.level.getSunAngle(partialTick);
            float dayFactor = Mth.clamp(Mth.cos(sunAngle) * 0.72F + 0.30F, 0.0F, 1.0F);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> shader);

            shader.safeGetUniform("InvViewMat").set(inverseView);
            shader.safeGetUniform("InvProjMat").set(inverseProjection);
            shader.safeGetUniform("CameraPosition").set(
                    (float) event.getCamera().getPosition().x,
                    (float) event.getCamera().getPosition().y,
                    (float) event.getCamera().getPosition().z
            );
            shader.safeGetUniform("FlashPosition").set(flashWorldX, flashWorldY, flashWorldZ);
            shader.safeGetUniform("Time").set(timeSeconds);
            shader.safeGetUniform("StormStrength").set(stormStrength);
            shader.safeGetUniform("WarningProgress").set(warningProgress);
            shader.safeGetUniform("CountdownStrength").set(countdownStrength);
            shader.safeGetUniform("DissolveProgress").set(dissolveProgress);
            shader.safeGetUniform("FlashIntensity").set(computeFlashIntensity(System.nanoTime()));
            shader.safeGetUniform("DayFactor").set(dayFactor);

            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION
            );
            buffer.addVertex(-1.0F, -1.0F, 0.0F);
            buffer.addVertex(1.0F, -1.0F, 0.0F);
            buffer.addVertex(1.0F, 1.0F, 0.0F);
            buffer.addVertex(-1.0F, 1.0F, 0.0F);
            BufferUploader.drawWithShader(buffer.buildOrThrow());

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void handlePreviewKey(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }

        if (skyShader == null) {
            String details = shaderLoadFailed && !shaderLoadError.isBlank()
                    ? shaderLoadError
                    : "шейдер ещё не загружен";
            minecraft.player.displayClientMessage(
                    Component.literal("Шейдер биовыброса недоступен: " + details)
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        if (ClientVibrosData.isStormSequence()) {
            minecraft.player.displayClientMessage(
                    Component.literal("Атмосфера сейчас управляется настоящим биовыбросом.")
                            .withStyle(ChatFormatting.DARK_GREEN),
                    true
            );
            return;
        }

        manualPreview = !manualPreview;
        minecraft.player.displayClientMessage(
                Component.literal(
                                "Тестовый режим биовыброса: "
                                        + (manualPreview ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН")
                        )
                        .withStyle(manualPreview ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                true
        );

        if (manualPreview) {
            animationEpochNanos = System.nanoTime();
            scheduleNextFlash(System.nanoTime(), true, 1.0F);
        }
    }

    private static void onPhaseChanged(Minecraft minecraft, int oldPhase, int newPhase) {
        manualPreview = false;
        long now = System.nanoTime();

        if (newPhase == VibrosEventHandler.PHASE_WARNING) {
            animationEpochNanos = now;
            scheduleNextFlash(now, false, 0.25F);
        } else if (newPhase == VibrosEventHandler.PHASE_ACTIVE) {
            scheduleNextFlash(now, true, 1.0F);
        } else if (newPhase == VibrosEventHandler.PHASE_DISSIPATING) {
            startFlash(minecraft, now, 0.92F, true);
        } else if (newPhase == VibrosEventHandler.PHASE_IDLE && oldPhase != Integer.MIN_VALUE) {
            if (oldPhase == VibrosEventHandler.PHASE_DISSIPATING) {
                // К моменту завершения зелёное сияние уже погасло, поэтому
                // значение можно безопасно сбросить без повторного импульса.
                dissolveProgress = 0.0F;
            }
            resetFlashState();
        }
    }

    private static float computeFlashActivity() {
        if (manualPreview || ClientVibrosData.isActive()) {
            return 1.0F;
        }
        if (ClientVibrosData.isWarning()) {
            return Mth.clamp((warningProgress - 0.34F) / 0.66F + countdownStrength * 0.42F, 0.0F, 1.0F);
        }
        if (ClientVibrosData.isDissipating()) {
            return Mth.clamp((1.0F - dissolveProgress) * 0.76F + Mth.sin(dissolveProgress * (float) Math.PI) * 0.30F, 0.0F, 1.0F);
        }
        return 0.0F;
    }

    private static void updateFlashAndSound(Minecraft minecraft, long now, float activity) {
        if (!flashRunning && now >= nextFlashNanos) {
            startFlash(minecraft, now, activity, false);
        }

        if (!flashRunning) {
            return;
        }

        if (!rumblePlayed && now >= rumbleNanos) {
            rumblePlayed = true;
            minecraft.level.playLocalSound(
                    minecraft.player.getX(),
                    minecraft.player.getY() + 1.0D,
                    minecraft.player.getZ(),
                    ModSounds.VIBROS_RUMBLE.get(),
                    SoundSource.WEATHER,
                    0.48F + flashVolume * 0.42F,
                    Math.max(0.68F, flashPitch - 0.18F),
                    false
            );
        }

        if (now >= flashStartNanos + flashDurationNanos) {
            flashRunning = false;
            rumblePlayed = false;
            scheduleNextFlash(now, false, activity);
        }
    }

    private static void startFlash(Minecraft minecraft, long now, float activity, boolean dissipationBurst) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        double distance = 220.0D + RANDOM.nextDouble() * 440.0D;

        flashWorldX = (float) (minecraft.player.getX() + Math.cos(angle) * distance);
        flashWorldY = (float) (minecraft.player.getY() + 145.0D + RANDOM.nextDouble() * 190.0D);
        flashWorldZ = (float) (minecraft.player.getZ() + Math.sin(angle) * distance);

        flashDurationNanos = (long) ((dissipationBurst ? 2.25D : 0.95D + RANDOM.nextDouble() * 0.75D) * NANOS_PER_SECOND);
        flashStartNanos = now;
        rumbleNanos = now + (long) ((0.14D + RANDOM.nextDouble() * 0.22D) * NANOS_PER_SECOND);
        flashPitch = (dissipationBurst ? 0.76F : 0.88F) + RANDOM.nextFloat() * 0.18F;
        flashVolume = Mth.clamp(activity, 0.20F, 1.0F);
        flashRunning = true;
        rumblePlayed = false;

        double soundDistance = 10.0D + RANDOM.nextDouble() * 6.0D;
        minecraft.level.playLocalSound(
                minecraft.player.getX() + Math.cos(angle) * soundDistance,
                minecraft.player.getY() + 5.0D + RANDOM.nextDouble() * 7.0D,
                minecraft.player.getZ() + Math.sin(angle) * soundDistance,
                ModSounds.VIBROS_FLASH.get(),
                SoundSource.WEATHER,
                0.35F + flashVolume * 0.65F,
                flashPitch,
                false
        );
    }

    private static float computeFlashIntensity(long now) {
        if (!flashRunning || flashDurationNanos <= 0L) {
            return 0.0F;
        }

        float phase = (now - flashStartNanos) / (float) flashDurationNanos;
        if (phase < 0.0F || phase >= 1.0F) {
            return 0.0F;
        }

        float first = gaussian(phase, 0.070F, 0.030F);
        float second = gaussian(phase, 0.205F, 0.052F) * 0.72F;
        float third = gaussian(phase, 0.405F, 0.110F) * 0.35F;
        float afterglow = (1.0F - smoothstep(0.30F, 1.0F, phase)) * 0.11F;
        return Math.min(1.45F, (first + second + third + afterglow) * flashVolume);
    }

    private static float gaussian(float value, float center, float width) {
        float normalized = (value - center) / Math.max(width, 0.0001F);
        return (float) Math.exp(-normalized * normalized);
    }

    private static float approach(float current, float target, float speed) {
        float next = current + (target - current) * speed;
        return Math.abs(target - next) < 0.0005F ? target : next;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 > edge1) {
            return 1.0F - smoothstep(edge1, edge0, value);
        }
        float t = Mth.clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void scheduleNextFlash(long now, boolean firstFlash, float activity) {
        float clamped = Mth.clamp(activity, 0.0F, 1.0F);
        double minDelay = Mth.lerp(clamped, 12.0F, 3.6F);
        double randomRange = Mth.lerp(clamped, 10.0F, 5.5F);
        double delaySeconds = firstFlash
                ? 0.55D + RANDOM.nextDouble() * 1.35D
                : minDelay + RANDOM.nextDouble() * randomRange;
        nextFlashNanos = now + (long) (delaySeconds * NANOS_PER_SECOND);
    }

    private static void resetFlashState() {
        nextFlashNanos = Long.MAX_VALUE;
        flashStartNanos = Long.MIN_VALUE;
        flashDurationNanos = 0L;
        rumbleNanos = 0L;
        flashRunning = false;
        rumblePlayed = false;
        flashVolume = 1.0F;
    }
}
