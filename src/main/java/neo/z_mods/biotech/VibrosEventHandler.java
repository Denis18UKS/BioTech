package neo.z_mods.biotech;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.Random;

public class VibrosEventHandler {
    private static int vibrosTime = 0;
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("vibros")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("state", StringArgumentType.word())
                        .executes(ctx -> {
                            String arg = StringArgumentType.getString(ctx, "state");
                            if (arg.equalsIgnoreCase("on")) {
                                BioTech.VIBROS_ACTIVE = true;
                                vibrosTime = 0;
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§cВыброс начался"), true);
                            } else if (arg.equalsIgnoreCase("off")) {
                                BioTech.VIBROS_ACTIVE = false;
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§aВыброс остановлен"), true);
                            }
                            return 1;
                        })));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!BioTech.VIBROS_ACTIVE) return;
        vibrosTime++;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            level.setWeatherParameters(0, 6000, true, true);
            level.setDayTime(level.getDayTime() + 200);

            for (Player player : level.players()) {
                BlockPos pos = player.blockPosition();
                boolean exposed = level.canSeeSky(pos);

                if (exposed) {
                    if (vibrosTime % 20 == 0) {
                        player.hurt(level.damageSources().magic(), 2.0f);
                    }
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                }

                if (RANDOM.nextFloat() < 0.05f) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(player.getX(), player.getY(), player.getZ());
                        level.addFreshEntity(bolt);
                    }
                }
            }
        }

        if (vibrosTime > 2400) {
            BioTech.VIBROS_ACTIVE = false;
            BioTech.LOGGER.info("Vibros ended");
        }
    }

    @EventBusSubscriber(modid = BioTech.MODID, value = Dist.CLIENT)
    public static class ClientEffects {
        @SubscribeEvent
        public static void fogColor(ViewportEvent.ComputeFogColor event) {
            if (!BioTech.VIBROS_ACTIVE) return;
            event.setRed(0.9f);
            event.setGreen(0.05f);
            event.setBlue(0.05f);
        }

        @SubscribeEvent
        public static void fogDensity(ViewportEvent.RenderFog event) {
            if (!BioTech.VIBROS_ACTIVE) return;
            event.setNearPlaneDistance(0.0f);
            event.setFarPlaneDistance(15.0f);
        }

        @SubscribeEvent
        public static void cameraShake(ClientTickEvent.Post event) {
            if (!BioTech.VIBROS_ACTIVE) return;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            float yaw = (float) ((RANDOM.nextDouble() - 0.5) * 1.5);
            float pitch = (float) ((RANDOM.nextDouble() - 0.5) * 1.5);
            player.setYRot(player.getYRot() + yaw);
            player.setXRot(Mth.clamp(player.getXRot() + pitch, -90, 90));
        }
        
        private static final Random RANDOM = new Random();
    }
}