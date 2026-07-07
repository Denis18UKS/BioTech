package neo.z_mods.biotech;

import neo.z_mods.biotech.network.GhostDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GhostSyncHandler {
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 20 != 0) return;
        
        var players = event.getServer().getPlayerList().getPlayers();
        
        for (ServerPlayer viewer : players) {
            Set<UUID> hiddenPlayers = new HashSet<>();
            Set<UUID> ghostPlayers = new HashSet<>();
            
            for (ServerPlayer target : players) {
                if (viewer == target) continue;
                
                if (DimensionSystemHandler.isHiddenFor(viewer, target)) {
                    hiddenPlayers.add(target.getUUID());
                } else if (DimensionSystemHandler.isGhostFor(viewer, target)) {
                    ghostPlayers.add(target.getUUID());
                }
            }
            
            PacketDistributor.sendToPlayer(viewer, 
                new GhostDataPacket(hiddenPlayers, ghostPlayers));
        }
    }
}