package neo.z_mods.biotech;

import neo.z_mods.biotech.item.DnaInjectorItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.UUID;

/** Долговременные последствия вставки ДНК. */
public final class DnaIntegrationHandler {
    private static final DustParticleOptions PLAYER_BLEND_PARTICLE = new DustParticleOptions(
            new Vector3f(0.18F, 0.95F, 0.48F),
            0.7F
    );

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tickPlayerBlend(player);
            suppressPacifiedMobs(player);
        }
    }

    private static void tickPlayerBlend(ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(DnaInjectorItem.PLAYER_BLEND_PRIMARY)) {
            return;
        }
        int progress = Math.min(12_000, player.getPersistentData().getInt(DnaInjectorItem.PLAYER_BLEND_PROGRESS) + 10);
        player.getPersistentData().putInt(DnaInjectorItem.PLAYER_BLEND_PROGRESS, progress);

        if (player.serverLevel().random.nextFloat() < 0.28F) {
            player.serverLevel().sendParticles(
                    PLAYER_BLEND_PARTICLE,
                    player.getX(),
                    player.getY() + player.getBbHeight() * 0.7D,
                    player.getZ(),
                    2,
                    0.22D, 0.34D, 0.22D,
                    0.0D
            );
        }
    }

    private static void suppressPacifiedMobs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(64.0D);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, candidate ->
                candidate.getPersistentData().hasUUID(DnaInjectorItem.PACIFIED_OWNER))) {
            UUID owner = mob.getPersistentData().getUUID(DnaInjectorItem.PACIFIED_OWNER);
            if (!owner.equals(player.getUUID())) {
                continue;
            }
            // Нейтральность снимается, если владелец сам атаковал модифицированного моба.
            if (mob.getLastHurtByMob() == player) {
                mob.getPersistentData().remove(DnaInjectorItem.PACIFIED_OWNER);
                continue;
            }
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
    }
}
