package neo.z_mods.biotech;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import neo.z_mods.biotech.network.VibrosStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Серверная логика биовыброса.
 *
 * <p>Фазы:</p>
 * <ol>
 *     <li>IDLE — ожидание следующего случайного события;</li>
 *     <li>WARNING — предупреждение длительностью 2:15;</li>
 *     <li>ACTIVE — сам биовыброс длительностью от 5 до 15 минут.</li>
 * </ol>
 */
public class VibrosEventHandler {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_WARNING = 1;
    public static final int PHASE_ACTIVE = 2;

    private static final int TICKS_PER_SECOND = 20;
    private static final int WARNING_TICKS = 135 * TICKS_PER_SECOND;
    private static final int MIN_ACTIVE_TICKS = 5 * 60 * TICKS_PER_SECOND;
    private static final int MAX_ACTIVE_TICKS = 15 * 60 * TICKS_PER_SECOND;

    // Автоматический биовыброс происходит через случайный промежуток 20–40 минут.
    private static final int MIN_AUTO_DELAY_TICKS = 20 * 60 * TICKS_PER_SECOND;
    private static final int MAX_AUTO_DELAY_TICKS = 40 * 60 * TICKS_PER_SECOND;

    private static final String MUTATED_TAG = "BioTechVibrosMutated";
    private static final RandomSource RANDOM = RandomSource.create();

    private static int phase = PHASE_IDLE;
    private static int phaseTicksRemaining;
    private static int phaseTotalTicks;
    private static int ticksUntilNext = randomBetween(MIN_AUTO_DELAY_TICKS, MAX_AUTO_DELAY_TICKS);
    private static boolean automaticEvents = true;

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("vibros")
                .requires(source -> source.hasPermission(2))

                // Сохраняет прежнее имя команды, но теперь сначала запускает предупреждение.
                .then(Commands.literal("on")
                        .executes(context -> startWarning(context.getSource())))

                // Мгновенный запуск удобен для проверки визуала и механик.
                .then(Commands.literal("now")
                        .executes(context -> startImmediately(context.getSource())))

                .then(Commands.literal("off")
                        .executes(context -> stopFromCommand(context.getSource())))

                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))

                .then(Commands.literal("auto")
                        .then(Commands.literal("on")
                                .executes(context -> setAutomatic(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setAutomatic(context.getSource(), false))))

                // Позволяет запланировать предупреждение через заданное количество секунд.
                .then(Commands.literal("schedule")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                .executes(context -> scheduleFromCommand(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "seconds")
                                )))));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (phase == PHASE_IDLE) {
            BioTech.VIBROS_ACTIVE = false;

            if (automaticEvents && --ticksUntilNext <= 0) {
                beginWarning(server);
            }
            return;
        }

        phaseTicksRemaining = Math.max(0, phaseTicksRemaining - 1);

        if (phase == PHASE_WARNING) {
            if (phaseTicksRemaining == 0) {
                beginActive(server, randomBetween(MIN_ACTIVE_TICKS, MAX_ACTIVE_TICKS));
                return;
            }

            if (phaseTicksRemaining % TICKS_PER_SECOND == 0) {
                syncAll(server);
            }
            return;
        }

        BioTech.VIBROS_ACTIVE = true;
        int elapsedTicks = phaseTotalTicks - phaseTicksRemaining;
        tickActiveVibros(server, elapsedTicks);

        if (phaseTicksRemaining == 0) {
            finishVibros(server, true);
            return;
        }

        if (phaseTicksRemaining % TICKS_PER_SECOND == 0) {
            syncAll(server);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendState(player);
        }
    }

    /**
     * С мутировавших существ во время активного биовыброса может выпасть редкий образец ДНК.
     * Пока отдельный предмет ДНК в моде не зарегистрирован, используется аметистовый осколок
     * с отдельным названием — это не затрагивает остальные системы проекта.
     */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (phase != PHASE_ACTIVE || event.getEntity().level().isClientSide()) {
            return;
        }
        if (!event.getEntity().getPersistentData().getBoolean(MUTATED_TAG)) {
            return;
        }
        if (RANDOM.nextFloat() >= 0.18F) {
            return;
        }

        ItemStack dna = new ItemStack(Items.AMETHYST_SHARD);
        dna.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Редкий образец ДНК")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        );

        ItemEntity drop = new ItemEntity(
                event.getEntity().level(),
                event.getEntity().getX(),
                event.getEntity().getY() + 0.35D,
                event.getEntity().getZ(),
                dna
        );
        event.getDrops().add(drop);
    }

    private static void tickActiveVibros(MinecraftServer server, int elapsedTicks) {
        for (ServerLevel level : server.getAllLevels()) {
            // Гроза поддерживается только в обычном мире; в Незере и Крае работают остальные эффекты.
            if (level.dimension().equals(Level.OVERWORLD) && elapsedTicks % 200 == 0) {
                level.setWeatherParameters(0, 12000, true, true);
            }

            List<ServerPlayer> players = level.players();
            for (ServerPlayer player : players) {
                if (player.isSpectator()) {
                    continue;
                }

                if (elapsedTicks % 10 == 0) {
                    spawnAtmosphericParticles(level, player);
                }

                if (elapsedTicks % TICKS_PER_SECOND == 0) {
                    applyPlayerEffects(level, player, elapsedTicks);
                }

                if (elapsedTicks % 40 == 0) {
                    mutateNearbyCreatures(level, player);
                }

                if (elapsedTicks % 100 == 0) {
                    spreadBiologicalGrowth(level, player);
                }

                if (elapsedTicks % TICKS_PER_SECOND == 0 && RANDOM.nextFloat() < 0.015F) {
                    createVisualLightning(level, player);
                }
            }
        }
    }

    private static void applyPlayerEffects(ServerLevel level, ServerPlayer player, int elapsedTicks) {
        BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        boolean exposedToSky = level.canSeeSky(eyePos);
        boolean toxicWater = player.isInWater();

        if (exposedToSky) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 70, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 0, false, false, true));

            if (elapsedTicks % 40 == 0 && !player.getAbilities().invulnerable) {
                player.hurt(level.damageSources().magic(), 1.0F);
            }
        }

        if (toxicWater) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));

            if (elapsedTicks % 40 == 0 && !player.getAbilities().invulnerable) {
                player.hurt(level.damageSources().magic(), 1.0F);
            }

            level.sendParticles(
                    ParticleTypes.BUBBLE_POP,
                    player.getX(), player.getY() + 0.7D, player.getZ(),
                    18,
                    0.7D, 0.5D, 0.7D,
                    0.04D
            );
        }
    }

    private static void mutateNearbyCreatures(ServerLevel level, ServerPlayer player) {
        List<Monster> monsters = level.getEntitiesOfClass(
                Monster.class,
                player.getBoundingBox().inflate(28.0D),
                monster -> monster.isAlive() && !monster.isRemoved()
        );

        int processed = 0;
        for (Monster monster : monsters) {
            if (processed++ >= 16) {
                break;
            }

            monster.getPersistentData().putBoolean(MUTATED_TAG, true);
            monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, false, true));
            monster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, false, true));
            monster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, false, true));

            level.sendParticles(
                    ParticleTypes.WARPED_SPORE,
                    monster.getX(), monster.getY() + monster.getBbHeight() * 0.55D, monster.getZ(),
                    8,
                    monster.getBbWidth() * 0.55D,
                    monster.getBbHeight() * 0.35D,
                    monster.getBbWidth() * 0.55D,
                    0.01D
            );
        }
    }

    private static void spreadBiologicalGrowth(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = player.blockPosition().getX() + randomBetween(-16, 16);
            int z = player.blockPosition().getZ() + randomBetween(-16, 16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos placePos = new BlockPos(x, y, z);
            BlockPos groundPos = placePos.below();

            if (!level.getBlockState(placePos).isAir()) {
                continue;
            }
            if (!level.getBlockState(groundPos).isFaceSturdy(level, groundPos, Direction.UP)) {
                continue;
            }
            if (!Blocks.MOSS_CARPET.defaultBlockState().canSurvive(level, placePos)) {
                continue;
            }

            level.setBlock(placePos, Blocks.MOSS_CARPET.defaultBlockState(), 3);
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    placePos.getX() + 0.5D,
                    placePos.getY() + 0.25D,
                    placePos.getZ() + 0.5D,
                    5,
                    0.25D, 0.15D, 0.25D,
                    0.0D
            );
        }
    }

    private static void spawnAtmosphericParticles(ServerLevel level, ServerPlayer player) {
        level.sendParticles(
                ParticleTypes.WARPED_SPORE,
                player.getX(), player.getY() + 4.0D, player.getZ(),
                22,
                9.0D, 4.0D, 9.0D,
                0.015D
        );
        level.sendParticles(
                ParticleTypes.SPORE_BLOSSOM_AIR,
                player.getX(), player.getY() + 2.5D, player.getZ(),
                10,
                7.0D, 3.0D, 7.0D,
                0.01D
        );
    }

    private static void createVisualLightning(ServerLevel level, ServerPlayer player) {
        int x = player.blockPosition().getX() + randomBetween(-28, 28);
        int z = player.blockPosition().getZ() + randomBetween(-28, 28);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }

        bolt.moveTo(x + 0.5D, y, z + 0.5D);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private static int startWarning(CommandSourceStack source) {
        if (phase != PHASE_IDLE) {
            source.sendFailure(Component.literal("Биовыброс уже запущен или находится на стадии предупреждения."));
            return 0;
        }

        beginWarning(source.getServer());
        source.sendSuccess(() -> Component.literal("Предупреждение о биовыбросе запущено."), true);
        return 1;
    }

    private static int startImmediately(CommandSourceStack source) {
        beginActive(source.getServer(), randomBetween(MIN_ACTIVE_TICKS, MAX_ACTIVE_TICKS));
        source.sendSuccess(() -> Component.literal("Биовыброс запущен немедленно."), true);
        return 1;
    }

    private static int stopFromCommand(CommandSourceStack source) {
        if (phase == PHASE_IDLE) {
            source.sendFailure(Component.literal("Биовыброс сейчас не запущен."));
            return 0;
        }

        finishVibros(source.getServer(), false);
        source.sendSuccess(() -> Component.literal("Биовыброс остановлен."), true);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        String phaseName = switch (phase) {
            case PHASE_WARNING -> "ПРЕДУПРЕЖДЕНИЕ";
            case PHASE_ACTIVE -> "АКТИВЕН";
            default -> "ОЖИДАНИЕ";
        };

        int displayTicks = phase == PHASE_IDLE ? ticksUntilNext : phaseTicksRemaining;
        source.sendSuccess(() -> Component.literal(
                "Фаза: " + phaseName
                        + " | время: " + formatTicks(displayTicks)
                        + " | авто: " + (automaticEvents ? "включено" : "выключено")
        ), false);
        return 1;
    }

    private static int setAutomatic(CommandSourceStack source, boolean enabled) {
        automaticEvents = enabled;
        if (enabled && phase == PHASE_IDLE && ticksUntilNext <= 0) {
            ticksUntilNext = randomBetween(MIN_AUTO_DELAY_TICKS, MAX_AUTO_DELAY_TICKS);
        }

        source.sendSuccess(
                () -> Component.literal("Автоматические биовыбросы " + (enabled ? "включены" : "выключены") + "."),
                true
        );
        syncAll(source.getServer());
        return 1;
    }

    private static int scheduleFromCommand(CommandSourceStack source, int seconds) {
        if (phase != PHASE_IDLE) {
            source.sendFailure(Component.literal("Сначала остановите текущий биовыброс."));
            return 0;
        }

        ticksUntilNext = seconds * TICKS_PER_SECOND;
        automaticEvents = true;
        source.sendSuccess(
                () -> Component.literal("Предупреждение о биовыбросе начнётся через " + formatTicks(ticksUntilNext) + "."),
                true
        );
        syncAll(source.getServer());
        return 1;
    }

    private static void beginWarning(MinecraftServer server) {
        phase = PHASE_WARNING;
        phaseTotalTicks = WARNING_TICKS;
        phaseTicksRemaining = WARNING_TICKS;
        BioTech.VIBROS_ACTIVE = false;

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("ВНИМАНИЕ! Биовыброс через 02:15. Найдите надёжное укрытие!")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros warning started");
    }

    private static void beginActive(MinecraftServer server, int durationTicks) {
        phase = PHASE_ACTIVE;
        phaseTotalTicks = durationTicks;
        phaseTicksRemaining = durationTicks;
        BioTech.VIBROS_ACTIVE = true;

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("БИОВЫБРОС НАЧАЛСЯ! Не выходите из укрытия и не заходите в воду!")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros started for {} ticks", durationTicks);
    }

    private static void finishVibros(MinecraftServer server, boolean naturallyEnded) {
        phase = PHASE_IDLE;
        phaseTotalTicks = 0;
        phaseTicksRemaining = 0;
        BioTech.VIBROS_ACTIVE = false;
        ticksUntilNext = randomBetween(MIN_AUTO_DELAY_TICKS, MAX_AUTO_DELAY_TICKS);

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                level.setWeatherParameters(6000, 0, false, false);
            }
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.literal(naturallyEnded
                                ? "Биовыброс завершён. Атмосфера постепенно очищается."
                                : "Биовыброс был принудительно остановлен.")
                        .withStyle(ChatFormatting.GREEN),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros ended");
    }

    private static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendState(player);
        }
    }

    private static void sendState(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                new VibrosStatePacket(
                        phase,
                        phase == PHASE_IDLE ? ticksUntilNext : phaseTicksRemaining,
                        phase == PHASE_IDLE ? 0 : phaseTotalTicks,
                        automaticEvents
                )
        );
    }

    private static int randomBetween(int minInclusive, int maxInclusive) {
        return minInclusive + RANDOM.nextInt(maxInclusive - minInclusive + 1);
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = (Math.max(0, ticks) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%02d:%02d".formatted(minutes, seconds);
    }
}
