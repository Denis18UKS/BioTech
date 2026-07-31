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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class VibrosEventHandler {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_WARNING = 1;
    public static final int PHASE_ACTIVE = 2;
    public static final int PHASE_DISSIPATING = 3;

    public static final ResourceKey<DamageType> BIOVIBROS_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "biovibros")
    );

    private static final int TICKS_PER_SECOND = 20;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int WARNING_TICKS = 135 * TICKS_PER_SECOND;
    private static final int MIN_ACTIVE_TICKS = 4 * 60 * TICKS_PER_SECOND;
    private static final int MAX_ACTIVE_TICKS = 5 * 60 * TICKS_PER_SECOND;
    private static final int DISSIPATION_TICKS = 25 * TICKS_PER_SECOND;
    private static final int MIN_AUTO_DELAY_TICKS = 20 * 60 * TICKS_PER_SECOND;
    private static final int MAX_AUTO_DELAY_TICKS = 40 * 60 * TICKS_PER_SECOND;

    private static final int SHELTER_CONFIRM_TICKS = 3 * TICKS_PER_SECOND;
    private static final int DAMAGE_GRACE_TICKS = 12 * TICKS_PER_SECOND;
    private static final int MAX_EXPOSURE_TICKS = 45 * TICKS_PER_SECOND;

    private static final String MUTATED_TAG = "BioTechVibrosMutated";
    private static final String MUTATION_STAGE_TAG = "BioTechVibrosMutationStage";
    private static final String RUST_STAGE_TAG = "BioTechVibrosRustStage";
    private static final String SHELTER_LOCKED_TAG = "BioTechVibrosShelterLocked";
    private static final String SHELTER_STABLE_TAG = "BioTechVibrosShelterStable";
    private static final String EXPOSURE_TAG = "BioTechVibrosExposure";
    private static final String ANCHOR_X_TAG = "BioTechVibrosAnchorX";
    private static final String ANCHOR_Y_TAG = "BioTechVibrosAnchorY";
    private static final String ANCHOR_Z_TAG = "BioTechVibrosAnchorZ";

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final RandomSource RANDOM = RandomSource.create();

    private static int phase = PHASE_IDLE;
    private static int phaseTicksRemaining;
    private static int phaseTotalTicks;
    private static long phaseEndNanos;
    private static int lastSyncedSecond = Integer.MIN_VALUE;
    private static int ticksUntilNext = randomBetween(MIN_AUTO_DELAY_TICKS, MAX_AUTO_DELAY_TICKS);
    private static boolean automaticEvents = true;
    private static long serverTicks;

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("vibros")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(context -> startWarning(context.getSource())))
                .then(Commands.literal("now").executes(context -> startImmediately(context.getSource())))
                .then(Commands.literal("off").executes(context -> stopFromCommand(context.getSource())))
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("auto")
                        .then(Commands.literal("on").executes(context -> setAutomatic(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> setAutomatic(context.getSource(), false))))
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
        serverTicks++;

        if (phase == PHASE_IDLE) {
            BioTech.VIBROS_ACTIVE = false;
            if (serverTicks % 200L == 0L) {
                forceClearWeather(server);
            }
            if (automaticEvents && --ticksUntilNext <= 0) {
                beginWarning(server);
            }
            return;
        }

        updateTimedPhaseRemaining();

        if (phase == PHASE_WARNING) {
            BioTech.VIBROS_ACTIVE = false;
            if (phaseTicksRemaining <= 10 * TICKS_PER_SECOND) {
                maintainStormWeather(server);
            } else if (serverTicks % 200L == 0L) {
                forceClearWeather(server);
            }
            if (phaseTicksRemaining == 0) {
                beginActive(server, randomBetween(MIN_ACTIVE_TICKS, MAX_ACTIVE_TICKS));
                return;
            }
            syncIfSecondChanged(server);
            return;
        }

        if (phase == PHASE_ACTIVE) {
            BioTech.VIBROS_ACTIVE = true;
            int elapsedTicks = phaseTotalTicks - phaseTicksRemaining;
            tickActiveVibros(server, elapsedTicks);
            if (phaseTicksRemaining == 0) {
                beginDissipation(server);
                return;
            }
            syncIfSecondChanged(server);
            return;
        }

        BioTech.VIBROS_ACTIVE = false;
        if (serverTicks % 40L == 0L) {
            forceClearWeather(server);
        }
        if (phaseTicksRemaining == 0) {
            finishVibros(server, true);
            return;
        }
        syncIfSecondChanged(server);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearShelterState(player, false);
            sendState(player);
        }
    }

    @SubscribeEvent
    public void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (phase != PHASE_ACTIVE) {
            return;
        }
        event.setProblem(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
        event.getEntity().displayClientMessage(
                Component.literal("Во время БВ спать нельзя")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                true
        );
    }

    @SubscribeEvent
    public void onCanContinueSleeping(CanContinueSleepingEvent event) {
        if (phase == PHASE_ACTIVE && event.getEntity() instanceof Player) {
            event.setContinueSleeping(false);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getSource().is(BIOVIBROS_DAMAGE)) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            player.getInventory().dropAll();
        }
        clearShelterState(player, false);
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (phase != PHASE_ACTIVE || event.getEntity().level().isClientSide()) {
            return;
        }
        if (!event.getEntity().getPersistentData().getBoolean(MUTATED_TAG) || RANDOM.nextFloat() >= 0.18F) {
            return;
        }

        ItemStack dna = new ItemStack(Items.AMETHYST_SHARD);
        dna.set(DataComponents.CUSTOM_NAME,
                Component.literal("Редкий образец ДНК").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        event.getDrops().add(new ItemEntity(
                event.getEntity().level(),
                event.getEntity().getX(),
                event.getEntity().getY() + 0.35D,
                event.getEntity().getZ(),
                dna
        ));
    }

    private static void tickActiveVibros(MinecraftServer server, int elapsedTicks) {
        if (elapsedTicks % 200 == 0) {
            maintainStormWeather(server);
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) {
                    continue;
                }

                processPlayerSurvival(level, player, elapsedTicks);

                if (elapsedTicks % 10 == 0) {
                    spawnAtmosphericParticles(level, player);
                }
                if (elapsedTicks % TICKS_PER_SECOND == 0) {
                    clearLegacyVibrosEffects(player);
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

    private static void processPlayerSurvival(ServerLevel level, ServerPlayer player, int elapsedTicks) {
        if (player.getPersistentData().getBoolean(SHELTER_LOCKED_TAG)) {
            keepPlayerAnchored(player);
            return;
        }

        boolean safe = isSafeShelter(level, player);
        int stableTicks = player.getPersistentData().getInt(SHELTER_STABLE_TAG);
        int exposureTicks = player.getPersistentData().getInt(EXPOSURE_TAG);

        if (safe) {
            stableTicks++;
            exposureTicks = Math.max(0, exposureTicks - 3);
            if (stableTicks >= SHELTER_CONFIRM_TICKS) {
                lockPlayerInShelter(player);
                return;
            }
        } else {
            stableTicks = 0;
            exposureTicks = Math.min(MAX_EXPOSURE_TICKS, exposureTicks + 1);
            applyStormResistance(player, exposureTicks);
        }

        player.getPersistentData().putInt(SHELTER_STABLE_TAG, stableTicks);
        player.getPersistentData().putInt(EXPOSURE_TAG, exposureTicks);

        if (!safe
                && elapsedTicks >= DAMAGE_GRACE_TICKS
                && elapsedTicks % 40 == 0
                && !player.getAbilities().invulnerable) {
            float exposure = exposureTicks / (float) MAX_EXPOSURE_TICKS;
            player.hurt(createBiovibrosDamage(level), 1.0F + exposure * 3.0F);
        }
    }

    private static void applyStormResistance(ServerPlayer player, int exposureTicks) {
        float exposure = Math.min(1.0F, exposureTicks / (float) MAX_EXPOSURE_TICKS);
        double horizontalMultiplier = 0.72D - exposure * 0.47D;
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(
                movement.x * horizontalMultiplier,
                Math.min(movement.y, 0.18D),
                movement.z * horizontalMultiplier
        );
        if (exposure > 0.45F && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    private static boolean isSafeShelter(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());

        if (level.canSeeSky(eye) || level.canSeeSky(feet.above())) {
            return false;
        }

        BlockPos floor = feet.below();
        if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
            return false;
        }
        if (!hasCeiling(level, eye, 8)) {
            return false;
        }

        int closedDirections = 0;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            if (hasBlockingWall(level, eye, direction, 6)) {
                closedDirections++;
            }
        }
        return closedDirections >= 3;
    }

    private static boolean hasCeiling(ServerLevel level, BlockPos eye, int maxDistance) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos pos = eye.above(distance);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlockingWall(ServerLevel level, BlockPos eye, Direction direction, int maxDistance) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos lower = eye.relative(direction, distance);
            BlockPos upper = lower.above();
            boolean lowerBlocked = !level.getBlockState(lower).getCollisionShape(level, lower).isEmpty();
            boolean upperBlocked = !level.getBlockState(upper).getCollisionShape(level, upper).isEmpty();
            if (lowerBlocked || upperBlocked) {
                return true;
            }
        }
        return false;
    }

    private static void lockPlayerInShelter(ServerPlayer player) {
        player.getPersistentData().putBoolean(SHELTER_LOCKED_TAG, true);
        player.getPersistentData().putDouble(ANCHOR_X_TAG, player.getX());
        player.getPersistentData().putDouble(ANCHOR_Y_TAG, player.getY());
        player.getPersistentData().putDouble(ANCHOR_Z_TAG, player.getZ());
        player.getPersistentData().putInt(EXPOSURE_TAG, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.displayClientMessage(
                Component.literal("Укрытие выдерживает БВ. Оставайтесь внутри до окончания выброса.")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
    }

    private static void keepPlayerAnchored(ServerPlayer player) {
        double x = player.getPersistentData().getDouble(ANCHOR_X_TAG);
        double y = player.getPersistentData().getDouble(ANCHOR_Y_TAG);
        double z = player.getPersistentData().getDouble(ANCHOR_Z_TAG);
        if (player.distanceToSqr(x, y, z) > 0.0004D) {
            player.teleportTo(x, y, z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private static void clearShelterState(ServerPlayer player, boolean notify) {
        boolean wasLocked = player.getPersistentData().getBoolean(SHELTER_LOCKED_TAG);
        player.getPersistentData().remove(SHELTER_LOCKED_TAG);
        player.getPersistentData().remove(SHELTER_STABLE_TAG);
        player.getPersistentData().remove(EXPOSURE_TAG);
        player.getPersistentData().remove(ANCHOR_X_TAG);
        player.getPersistentData().remove(ANCHOR_Y_TAG);
        player.getPersistentData().remove(ANCHOR_Z_TAG);
        if (notify && wasLocked) {
            player.displayClientMessage(
                    Component.literal("БВ закончился. Укрытие можно покинуть.")
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        }
    }

    private static void clearAllShelterStates(MinecraftServer server, boolean notify) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearShelterState(player, notify);
        }
    }

    private static void clearLegacyVibrosEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private static DamageSource createBiovibrosDamage(ServerLevel level) {
        return new DamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(BIOVIBROS_DAMAGE)
        );
    }

    private static void mutateNearbyCreatures(ServerLevel level, ServerPlayer player) {
        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(28.0D),
                mob -> mob.isAlive() && !mob.isRemoved() && !(mob instanceof IronGolem)
        );

        int processed = 0;
        for (Mob mob : mobs) {
            if (processed++ >= 20) {
                break;
            }

            int stage = mob.getPersistentData().getInt(MUTATION_STAGE_TAG);
            if (stage < 100) {
                stage = Math.min(100, stage + randomBetween(4, 11));
                mob.getPersistentData().putInt(MUTATION_STAGE_TAG, stage);
                double jitter = 0.015D + stage / 100.0D * 0.035D;
                mob.setDeltaMovement(mob.getDeltaMovement().add(
                        (RANDOM.nextDouble() - 0.5D) * jitter,
                        RANDOM.nextDouble() * jitter * 0.35D,
                        (RANDOM.nextDouble() - 0.5D) * jitter
                ));
                level.sendParticles(
                        stage > 65 ? ParticleTypes.GLOW : ParticleTypes.WARPED_SPORE,
                        mob.getX(),
                        mob.getY() + mob.getBbHeight() * 0.55D,
                        mob.getZ(),
                        3 + stage / 12,
                        mob.getBbWidth() * 0.55D,
                        mob.getBbHeight() * 0.35D,
                        mob.getBbWidth() * 0.55D,
                        0.008D + stage * 0.00008D
                );
            }

            if (stage >= 100) {
                mob.getPersistentData().putBoolean(MUTATED_TAG, true);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, false, true));
            }
        }

        rustNearbyGolems(level, player);
    }

    private static void rustNearbyGolems(ServerLevel level, ServerPlayer player) {
        List<IronGolem> golems = level.getEntitiesOfClass(
                IronGolem.class,
                player.getBoundingBox().inflate(28.0D),
                golem -> golem.isAlive() && !golem.isRemoved()
        );

        int processed = 0;
        for (IronGolem golem : golems) {
            if (processed++ >= 8) {
                break;
            }
            int rustStage = golem.getPersistentData().getInt(RUST_STAGE_TAG);
            rustStage = Math.min(100, rustStage + randomBetween(2, 7));
            golem.getPersistentData().putInt(RUST_STAGE_TAG, rustStage);
            level.sendParticles(
                    rustStage > 55 ? ParticleTypes.WAX_OFF : ParticleTypes.SMOKE,
                    golem.getX(),
                    golem.getY() + golem.getBbHeight() * 0.55D,
                    golem.getZ(),
                    2 + rustStage / 20,
                    golem.getBbWidth() * 0.45D,
                    golem.getBbHeight() * 0.35D,
                    golem.getBbWidth() * 0.45D,
                    0.005D
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
            case PHASE_DISSIPATING -> "РАССЕИВАЕТСЯ";
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
        clearAllShelterStates(server, false);
        beginTimedPhase(PHASE_WARNING, WARNING_TICKS);
        BioTech.VIBROS_ACTIVE = false;
        forceClearWeather(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("ВНИМАНИЕ! Биовыброс через 02:15. Найдите надёжное укрытие!")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros warning started");
    }

    private static void beginActive(MinecraftServer server, int durationTicks) {
        beginTimedPhase(PHASE_ACTIVE, durationTicks);
        BioTech.VIBROS_ACTIVE = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearShelterState(player, false);
            if (player.isSleeping()) {
                player.stopSleeping();
            }
        }
        maintainStormWeather(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("БИОВЫБРОС НАЧАЛСЯ! Найдите закрытое укрытие и оставайтесь внутри!")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros started for {} ticks (real-time clock)", durationTicks);
    }

    private static void beginDissipation(MinecraftServer server) {
        beginTimedPhase(PHASE_DISSIPATING, DISSIPATION_TICKS);
        BioTech.VIBROS_ACTIVE = false;
        forceClearWeather(server);
        clearAllShelterStates(server, true);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Биовыброс ослабевает. Облачное ядро начинает рассеиваться.")
                        .withStyle(ChatFormatting.GREEN),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros dissipation started");
    }

    private static void finishVibros(MinecraftServer server, boolean naturallyEnded) {
        clearAllShelterStates(server, true);
        phase = PHASE_IDLE;
        phaseTotalTicks = 0;
        phaseTicksRemaining = 0;
        phaseEndNanos = 0L;
        lastSyncedSecond = Integer.MIN_VALUE;
        BioTech.VIBROS_ACTIVE = false;
        ticksUntilNext = randomBetween(MIN_AUTO_DELAY_TICKS, MAX_AUTO_DELAY_TICKS);
        forceClearWeather(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(naturallyEnded
                                ? "Биовыброс завершён. Атмосфера стабилизировалась."
                                : "Биовыброс был принудительно остановлен.")
                        .withStyle(ChatFormatting.GREEN),
                false
        );
        syncAll(server);
        BioTech.LOGGER.info("Biovibros ended");
    }

    private static void maintainStormWeather(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                level.setWeatherParameters(0, 12000, true, true);
            }
        }
    }

    private static void forceClearWeather(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                level.setWeatherParameters(12000, 0, false, false);
            }
        }
    }

    private static void beginTimedPhase(int newPhase, int durationTicks) {
        phase = newPhase;
        phaseTotalTicks = Math.max(1, durationTicks);
        phaseTicksRemaining = phaseTotalTicks;
        phaseEndNanos = System.nanoTime() + phaseTotalTicks * NANOS_PER_TICK;
        lastSyncedSecond = Integer.MIN_VALUE;
    }

    private static void updateTimedPhaseRemaining() {
        if (phase == PHASE_IDLE || phaseEndNanos <= 0L) {
            return;
        }
        long remainingNanos = Math.max(0L, phaseEndNanos - System.nanoTime());
        phaseTicksRemaining = (int) Math.min(
                phaseTotalTicks,
                (remainingNanos + NANOS_PER_TICK - 1L) / NANOS_PER_TICK
        );
    }

    private static void syncIfSecondChanged(MinecraftServer server) {
        int second = (phaseTicksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        if (second != lastSyncedSecond) {
            lastSyncedSecond = second;
            syncAll(server);
        }
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
