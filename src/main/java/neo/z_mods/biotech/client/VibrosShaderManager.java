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
import neo.z_mods.biotech.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
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
 * Встроенное объёмное небо биовыброса.
 *
 * <p>Тучи строятся процедурно непосредственно в мировом пространстве методом
 * послойного прохождения луча через облачный объём. Эффект не является
 * натянутой на экран картинкой, не следует за взглядом и не требует Iris,
 * Oculus или OptiFine.</p>
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
    private static boolean wasEventActive;
    private static boolean wasEffectRequested;
    private static float effectBlend;

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

    private VibrosShaderManager() {
    }

    private static boolean isEffectRequested() {
        return ClientVibrosData.isActive() || manualPreview;
    }

    /** Оставляет рендер активным ещё на короткое время для плавного затухания. */
    public static boolean shouldRenderSky() {
        return skyShader != null && (isEffectRequested() || effectBlend > 0.01F);
    }

    public static boolean isManualPreview() {
        return manualPreview;
    }

    public static boolean isShaderReady() {
        return skyShader != null;
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
                    BioTech.LOGGER.info("Biovibros volumetric sky shader loaded successfully");
                });
            } catch (Exception exception) {
                markShaderLoadFailed(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                BioTech.LOGGER.error(
                        "Biovibros sky shader could not be loaded. Minecraft will continue without the procedural sky.",
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
            BioTech.LOGGER.error("Biovibros shader disabled: {}", shaderLoadError);
        }
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean eventActive = ClientVibrosData.isActive();

            if (eventActive && !wasEventActive) {
                manualPreview = false;
            }
            wasEventActive = eventActive;

            while (TOGGLE_SHADER.consumeClick()) {
                if (minecraft.player == null) {
                    continue;
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
                    continue;
                }

                if (eventActive) {
                    minecraft.player.displayClientMessage(
                            Component.literal("Во время биовыброса атмосферное небо включено автоматически.")
                                    .withStyle(ChatFormatting.DARK_GREEN),
                            true
                    );
                    continue;
                }

                manualPreview = !manualPreview;
                minecraft.player.displayClientMessage(
                        Component.literal(
                                        "Тестовый шейдер биовыброса: "
                                                + (manualPreview ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН")
                                )
                                .withStyle(manualPreview ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                        true
                );
            }

            if (minecraft.level == null || minecraft.player == null) {
                manualPreview = false;
                wasEventActive = false;
                wasEffectRequested = false;
                effectBlend = 0.0F;
                resetFlashState();
                return;
            }

            boolean requested = isEffectRequested();
            float targetBlend = requested ? 1.0F : 0.0F;
            if (eventActive) {
                // Настоящий биовыброс сразу полностью скрывает солнце и луну.
                effectBlend = 1.0F;
            } else {
                effectBlend += (targetBlend - effectBlend) * 0.115F;
                if (Math.abs(targetBlend - effectBlend) < 0.001F) {
                    effectBlend = targetBlend;
                }
            }

            long now = System.nanoTime();
            if (requested && !wasEffectRequested) {
                animationEpochNanos = now;
                scheduleNextFlash(now, true);
            } else if (!requested && wasEffectRequested) {
                resetFlashState();
            }
            wasEffectRequested = requested;

            if (requested) {
                updateFlashAndSound(minecraft, now);
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
            shader.safeGetUniform("Intensity").set(effectBlend);
            shader.safeGetUniform("FlashIntensity").set(computeFlashIntensity(System.nanoTime()));

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

    private static void updateFlashAndSound(Minecraft minecraft, long now) {
        if (!flashRunning && now >= nextFlashNanos) {
            startFlash(minecraft, now);
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
                    0.82F,
                    Math.max(0.72F, flashPitch - 0.16F),
                    false
            );
        }

        if (now >= flashStartNanos + flashDurationNanos) {
            flashRunning = false;
            rumblePlayed = false;
            scheduleNextFlash(now, false);
        }
    }

    private static void startFlash(Minecraft minecraft, long now) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        double distance = 260.0D + RANDOM.nextDouble() * 360.0D;

        flashWorldX = (float) (minecraft.player.getX() + Math.cos(angle) * distance);
        flashWorldY = (float) (minecraft.player.getY() + 155.0D + RANDOM.nextDouble() * 150.0D);
        flashWorldZ = (float) (minecraft.player.getZ() + Math.sin(angle) * distance);

        flashDurationNanos = (long) ((0.95D + RANDOM.nextDouble() * 0.70D) * NANOS_PER_SECOND);
        flashStartNanos = now;
        rumbleNanos = now + (long) ((0.13D + RANDOM.nextDouble() * 0.18D) * NANOS_PER_SECOND);
        flashPitch = 0.88F + RANDOM.nextFloat() * 0.20F;
        flashRunning = true;
        rumblePlayed = false;

        double soundDistance = 11.0D + RANDOM.nextDouble() * 4.0D;
        minecraft.level.playLocalSound(
                minecraft.player.getX() + Math.cos(angle) * soundDistance,
                minecraft.player.getY() + 6.0D + RANDOM.nextDouble() * 5.0D,
                minecraft.player.getZ() + Math.sin(angle) * soundDistance,
                ModSounds.VIBROS_FLASH.get(),
                SoundSource.WEATHER,
                1.0F,
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

        float first = gaussian(phase, 0.075F, 0.032F);
        float second = gaussian(phase, 0.215F, 0.054F) * 0.72F;
        float third = gaussian(phase, 0.405F, 0.105F) * 0.34F;
        float afterglow = (1.0F - smoothstep(0.28F, 1.0F, phase)) * 0.10F;
        return Math.min(1.35F, first + second + third + afterglow);
    }

    private static float gaussian(float value, float center, float width) {
        float normalized = (value - center) / Math.max(width, 0.0001F);
        return (float) Math.exp(-normalized * normalized);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static void scheduleNextFlash(long now, boolean firstFlash) {
        double delaySeconds = firstFlash
                ? 1.4D + RANDOM.nextDouble() * 2.8D
                : 4.5D + RANDOM.nextDouble() * 8.5D;
        nextFlashNanos = now + (long) (delaySeconds * NANOS_PER_SECOND);
    }

    private static void resetFlashState() {
        nextFlashNanos = Long.MAX_VALUE;
        flashStartNanos = Long.MIN_VALUE;
        flashDurationNanos = 0L;
        rumbleNanos = 0L;
        flashRunning = false;
        rumblePlayed = false;
    }
}
