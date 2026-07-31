package neo.z_mods.biotech.item;

import neo.z_mods.biotech.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class DnaInjectorItem extends Item {
    public DnaInjectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack injector, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int capsuleSlot = findEmptyCapsule(player);
        if (capsuleSlot < 0) {
            player.displayClientMessage(Component.literal("Нужна пустая капсула ДНК").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        CompoundTag injectorData = injector.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean stabilized = injectorData.getBoolean("BioTechStabilizer");
        boolean accelerated = injectorData.getBoolean("BioTechAccelerator");
        float successChance = stabilized ? 0.92F : 0.72F;
        boolean success = player.getRandom().nextFloat() <= successChance;

        player.getInventory().getItem(capsuleSlot).shrink(1);
        injector.setDamageValue(injector.getDamageValue() + 1);
        if (injector.getDamageValue() >= injector.getMaxDamage()) {
            injector.shrink(1);
        }

        player.level().playSound(null, target.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, accelerated ? 1.35F : 1.0F);
        player.getCooldowns().addCooldown(this, accelerated ? 10 : 30);

        if (!success) {
            target.hurt(player.damageSources().playerAttack(player), 1.0F);
            player.displayClientMessage(Component.literal("Образец повреждён при извлечении").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        int quality = stabilized ? 82 + player.getRandom().nextInt(19) : 55 + player.getRandom().nextInt(36);
        ItemStack sample = new ItemStack(quality >= 80 ? ModContent.DNA_CAPSULE_IMPROVED.get() : ModContent.DNA_CAPSULE_STANDARD.get());
        CompoundTag sampleData = new CompoundTag();
        ResourceLocation entityId = EntityType.getKey(target.getType());
        sampleData.putString("BioTechEntity", entityId.toString());
        sampleData.putInt("BioTechQuality", quality);
        sample.set(DataComponents.CUSTOM_DATA, CustomData.of(sampleData));
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("ДНК: " + entityId.getPath()).withStyle(ChatFormatting.GREEN));

        if (!player.getInventory().add(sample)) {
            player.drop(sample, false);
        }
        player.displayClientMessage(Component.literal("Образец ДНК получен: " + quality + "%").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }

    private static int findEmptyCapsule(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModContent.DNA_CAPSULE_EMPTY.get())) {
                return slot;
            }
        }
        return -1;
    }
}
