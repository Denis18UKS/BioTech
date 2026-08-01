package neo.z_mods.biotech.item;

import neo.z_mods.biotech.DnaBlacklistConfig;
import neo.z_mods.biotech.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * ДНК-инъектор с двумя режимами. Режим хранится непосредственно в предмете и
 * переключается настраиваемым кейбиндом (V по умолчанию).
 */
public class DnaInjectorItem extends Item {
    public enum Mode {
        EXTRACT("забрать ДНК"),
        INSERT("вставить ДНК");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static final String TARGET_ID = "BioTechExtractionTargetId";
    public static final String TARGET_UUID = "BioTechExtractionTargetUuid";
    public static final String MODE_KEY = "BioTechInjectorMode";

    public static final String INSERTED_DNA = "BioTechInsertedDna";
    public static final String PACIFIED_OWNER = "BioTechPacifiedOwner";
    public static final String PLAYER_BLEND_PRIMARY = "BioTechPlayerBlendPrimary";
    public static final String PLAYER_BLEND_SECONDARY = "BioTechPlayerBlendSecondary";
    public static final String PLAYER_BLEND_PROGRESS = "BioTechPlayerBlendProgress";

    private static final Set<String> NETHER_DNA = Set.of(
            "blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute",
            "strider", "wither", "wither_skeleton", "zoglin", "zombified_piglin"
    );

    public DnaInjectorItem(Properties properties) {
        super(properties);
    }

    public static Mode getMode(ItemStack stack) {
        String value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString(MODE_KEY);
        return "insert".equals(value) ? Mode.INSERT : Mode.EXTRACT;
    }

    public static Mode cycleMode(ItemStack stack) {
        Mode next = getMode(stack) == Mode.EXTRACT ? Mode.INSERT : Mode.EXTRACT;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(MODE_KEY, next == Mode.INSERT ? "insert" : "extract");
        tag.remove(TARGET_ID);
        tag.remove(TARGET_UUID);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return next;
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack injector,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        if (getMode(injector) == Mode.INSERT) {
            return insertDna(injector, player, target);
        }
        return beginExtraction(injector, player, target, hand);
    }

    /** Shift+ПКМ в воздух позволяет забрать собственную ДНК или вставить её себе. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack injector = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(injector);
        }
        if (getMode(injector) == Mode.INSERT) {
            InteractionResult result = insertDna(injector, player, player);
            return new InteractionResultHolder<>(result, injector);
        }
        InteractionResult result = beginExtraction(injector, player, player, hand);
        return new InteractionResultHolder<>(result, injector);
    }

    private InteractionResult beginExtraction(
            ItemStack injector,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        if (!player.level().isClientSide()) {
            if (!(target instanceof Player) && DnaBlacklistConfig.isExcluded(target.getType())) {
                player.displayClientMessage(
                        Component.literal("У этого существа нельзя извлечь ДНК").withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResult.FAIL;
            }
            if (findEmptyCapsule(player) < 0) {
                player.displayClientMessage(
                        Component.literal("Нужна пустая капсула ДНК").withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResult.FAIL;
            }
        }

        CompoundTag data = injector.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(TARGET_ID, target.getId());
        data.putUUID(TARGET_UUID, target.getUUID());
        injector.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.startUsingItem(hand);
        return InteractionResult.sidedSuccess(player.level().isClientSide());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return hasUpgrade(stack, "BioTechAccelerator") ? 36 : 72;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (getMode(stack) != Mode.EXTRACT) {
            user.releaseUsingItem();
            return;
        }
        LivingEntity target = findTarget(level, stack);
        double range = hasUpgrade(stack, "BioTechRange") ? 14.0D : 8.5D;
        if (target == null || !target.isAlive() || distanceToTargetSqr(user, target) > range * range) {
            user.releaseUsingItem();
            if (user instanceof Player player && !level.isClientSide()) {
                player.displayClientMessage(
                        Component.literal("Извлечение прервано: цель слишком далеко").withStyle(ChatFormatting.RED),
                        true
                );
            }
            return;
        }

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && remainingUseDuration % 2 == 0) {
            Vec3 start = user.getEyePosition().add(user.getLookAngle().scale(0.65D));
            Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.58D, 0.0D);
            double t = 0.18D + serverLevel.random.nextDouble() * 0.78D;
            Vec3 point = start.lerp(end, t);
            serverLevel.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    point.x, point.y, point.z,
                    2,
                    0.035D, 0.035D, 0.035D,
                    0.0D
            );
            serverLevel.sendParticles(
                    ParticleTypes.GLOW,
                    point.x, point.y, point.z,
                    1,
                    0.02D, 0.02D, 0.02D,
                    0.0D
            );
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack injector, Level level, LivingEntity livingEntity) {
        if (!(livingEntity instanceof Player player) || level.isClientSide() || getMode(injector) != Mode.EXTRACT) {
            return injector;
        }

        LivingEntity target = findTarget(level, injector);
        if (target == null || !target.isAlive()
                || (!(target instanceof Player) && DnaBlacklistConfig.isExcluded(target.getType()))) {
            clearTarget(injector);
            return injector;
        }

        double range = hasUpgrade(injector, "BioTechRange") ? 14.0D : 8.5D;
        if (distanceToTargetSqr(player, target) > range * range) {
            player.displayClientMessage(Component.literal("Цель вышла из зоны извлечения").withStyle(ChatFormatting.RED), true);
            clearTarget(injector);
            return injector;
        }

        int capsuleSlot = findEmptyCapsule(player);
        if (capsuleSlot < 0) {
            player.displayClientMessage(Component.literal("Пустая капсула закончилась").withStyle(ChatFormatting.RED), true);
            clearTarget(injector);
            return injector;
        }

        CompoundTag injectorData = injector.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean stabilized = injectorData.getBoolean("BioTechStabilizer");
        boolean accelerated = injectorData.getBoolean("BioTechAccelerator");
        float successChance = stabilized ? 0.94F : 0.74F;
        boolean success = player.getRandom().nextFloat() <= successChance;

        player.getInventory().getItem(capsuleSlot).shrink(1);
        damageInjector(injector, 1);
        player.getCooldowns().addCooldown(this, accelerated ? 8 : 18);

        ResourceLocation entityId = EntityType.getKey(target.getType());
        int quality = success
                ? (stabilized ? 82 + player.getRandom().nextInt(19) : 52 + player.getRandom().nextInt(40))
                : 8 + player.getRandom().nextInt(24);
        String state = success ? DnaSampleItem.stateForQuality(quality) : "damaged";

        ItemStack sample = new ItemStack(
                success && quality >= 80
                        ? ModContent.DNA_CAPSULE_IMPROVED.get()
                        : ModContent.DNA_CAPSULE_STANDARD.get()
        );
        CompoundTag sampleData = new CompoundTag();
        sampleData.putString("BioTechEntity", entityId.toString());
        sampleData.putInt("BioTechQuality", quality);
        sampleData.putString("BioTechState", state);
        sampleData.putBoolean("BioTechBaby", target.isBaby());
        sampleData.putFloat("BioTechHealthFraction", target.getHealth() / Math.max(1.0F, target.getMaxHealth()));
        if (target instanceof Player sampledPlayer) {
            sampleData.putUUID("BioTechPlayerUuid", sampledPlayer.getUUID());
            sampleData.putString("BioTechPlayerName", sampledPlayer.getGameProfile().getName());
        }
        sample.set(DataComponents.CUSTOM_DATA, CustomData.of(sampleData));
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("ДНК: ")
                .append(target.getDisplayName())
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (!player.getInventory().add(sample)) {
            player.drop(sample, false);
        }

        if (!success && target != player) {
            target.hurt(player.damageSources().playerAttack(player), 1.0F);
        }
        level.playSound(
                null,
                target.blockPosition(),
                success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS,
                0.85F,
                success ? 1.35F : 0.8F
        );
        player.displayClientMessage(
                Component.literal(success
                                ? "ДНК извлечена. Качество: " + quality + "%"
                                : "Образец получен повреждённым: " + quality + "%")
                        .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED),
                true
        );
        clearTarget(injector);
        return injector;
    }

    private InteractionResult insertDna(ItemStack injector, Player user, LivingEntity target) {
        if (user.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int sampleSlot = findDnaSample(user);
        if (sampleSlot < 0) {
            user.displayClientMessage(Component.literal("Для вставки нужна заполненная капсула ДНК").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        int gelSlot = findItem(user, ModContent.BIO_GEL.get());
        if (gelSlot < 0) {
            user.displayClientMessage(Component.literal("Для стабилизации вставки нужен био-гель").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!hasUpgrade(injector, "BioTechStabilizer")) {
            user.displayClientMessage(Component.literal("Инъектору нужен модуль стабилизации образца").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        ItemStack sample = user.getInventory().getItem(sampleSlot);
        CompoundTag data = sample.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int quality = data.getInt("BioTechQuality");
        if (quality < 65 || "damaged".equals(data.getString("BioTechState"))) {
            user.displayClientMessage(Component.literal("Качество ДНК слишком низкое для вставки").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        String source = data.getString("BioTechEntity");
        boolean nether = isNetherDna(source);
        int moduleSlot = nether ? findItem(user, ModContent.NETHER_ADAPTATION_MODULE.get()) : -1;
        if (nether && (quality < 80 || moduleSlot < 0)) {
            user.displayClientMessage(
                    Component.literal("Адская ДНК требует качество 80%+ и модуль адской адаптации")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        float chance = Mth.clamp(0.45F + quality / 180.0F, 0.55F, nether ? 0.88F : 0.97F);
        boolean success = user.getRandom().nextFloat() <= chance;
        sample.shrink(1);
        user.getInventory().getItem(gelSlot).shrink(1);
        if (nether) {
            user.getInventory().getItem(moduleSlot).shrink(1);
        }
        damageInjector(injector, nether ? 6 : 3);
        user.getCooldowns().addCooldown(this, nether ? 80 : 40);

        if (!success) {
            target.hurt(user.damageSources().magic(), nether ? 6.0F : 3.0F);
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            user.displayClientMessage(Component.literal("Вставка ДНК сорвалась").withStyle(ChatFormatting.RED), true);
            user.level().playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 0.65F);
            return InteractionResult.FAIL;
        }

        applyInsertedDna(user, target, data, source, quality);
        user.level().playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);
        user.displayClientMessage(Component.literal("ДНК успешно встроена").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    private static void applyInsertedDna(Player user, LivingEntity target, CompoundTag sample, String source, int quality) {
        target.getPersistentData().putString(INSERTED_DNA, source);
        target.getPersistentData().putInt("BioTechInsertedDnaQuality", quality);

        if (target instanceof Player playerTarget) {
            if (sample.hasUUID("BioTechPlayerUuid")) {
                playerTarget.getPersistentData().putUUID(PLAYER_BLEND_PRIMARY, sample.getUUID("BioTechPlayerUuid"));
            }
            if (sample.hasUUID("BioTechSecondaryPlayerUuid")) {
                playerTarget.getPersistentData().putUUID(PLAYER_BLEND_SECONDARY, sample.getUUID("BioTechSecondaryPlayerUuid"));
            }
            playerTarget.getPersistentData().putInt(PLAYER_BLEND_PROGRESS, 0);
            playerTarget.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, quality >= 85 ? 1 : 0));
            playerTarget.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0));
            return;
        }

        if (target instanceof Mob mob && source.endsWith("zombified_piglin")) {
            mob.getPersistentData().putUUID(PACIFIED_OWNER, user.getUUID());
            mob.setTarget(null);
        }

        if (source.endsWith("spider") || source.endsWith("cave_spider")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 8, 0));
        } else if (source.endsWith("blaze")) {
            target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 12, 0));
        } else if (source.endsWith("iron_golem")) {
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 8, 1));
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 45, 0));
        }
    }

    private static double distanceToTargetSqr(LivingEntity user, LivingEntity target) {
        Vec3 eye = user.getEyePosition();
        AABB box = target.getBoundingBox().inflate(0.35D);
        double x = Mth.clamp(eye.x, box.minX, box.maxX);
        double y = Mth.clamp(eye.y, box.minY, box.maxY);
        double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceToSqr(x, y, z);
    }

    private static boolean isNetherDna(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        return id != null && NETHER_DNA.contains(id.getPath());
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        clearTarget(stack);
    }

    public static LivingEntity findTarget(Level level, ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Entity entity = null;
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && data.hasUUID(TARGET_UUID)) {
            UUID uuid = data.getUUID(TARGET_UUID);
            entity = serverLevel.getEntity(uuid);
        } else if (data.contains(TARGET_ID)) {
            entity = level.getEntity(data.getInt(TARGET_ID));
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    public static boolean hasUpgrade(ItemStack stack, String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(key);
    }

    private static void clearTarget(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.remove(TARGET_ID);
        data.remove(TARGET_UUID);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    private static void damageInjector(ItemStack injector, int amount) {
        injector.setDamageValue(injector.getDamageValue() + amount);
        if (injector.getDamageValue() >= injector.getMaxDamage()) {
            injector.shrink(1);
        }
    }

    private static int findEmptyCapsule(Player player) {
        return findItem(player, ModContent.DNA_CAPSULE_EMPTY.get());
    }

    private static int findDnaSample(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof DnaSampleItem) {
                return slot;
            }
        }
        return -1;
    }

    private static int findItem(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }
}
