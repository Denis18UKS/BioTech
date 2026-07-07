package neo.z_mods.biotech.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClientGhostData {
    public static final Set<UUID> HIDDEN_PLAYERS = new HashSet<>();
    public static final Set<UUID> GHOST_PLAYERS = new HashSet<>();
    
    public static boolean isHidden(UUID uuid) {
        return HIDDEN_PLAYERS.contains(uuid);
    }
    
    public static boolean isGhost(UUID uuid) {
        return GHOST_PLAYERS.contains(uuid);
    }
}