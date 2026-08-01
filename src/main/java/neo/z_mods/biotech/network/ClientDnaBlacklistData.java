package neo.z_mods.biotech.network;

import java.util.Set;

public final class ClientDnaBlacklistData {
    private static Set<String> excluded = Set.of();
    private static boolean canEdit;
    private static boolean openRequested;

    public static void update(Set<String> values, boolean editable) {
        excluded = Set.copyOf(values);
        canEdit = editable;
        openRequested = true;
    }

    public static Set<String> excluded() {
        return excluded;
    }

    public static boolean canEdit() {
        return canEdit;
    }

    public static boolean consumeOpenRequested() {
        boolean value = openRequested;
        openRequested = false;
        return value;
    }

    private ClientDnaBlacklistData() {
    }
}
