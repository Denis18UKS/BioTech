package neo.z_mods.biotech.network;

import neo.z_mods.biotech.VibrosEventHandler;

/**
 * Клиентская копия состояния биовыброса.
 * В классе намеренно нет импортов из net.minecraft.client, поэтому пакет безопасно
 * регистрируется и на выделенном сервере.
 */
public final class ClientVibrosData {
    private static int phase = VibrosEventHandler.PHASE_IDLE;
    private static int remainingTicks;
    private static int totalTicks;
    private static boolean automaticEvents;

    private ClientVibrosData() {
    }

    public static void update(int newPhase, int newRemainingTicks, int newTotalTicks, boolean newAutomaticEvents) {
        phase = newPhase;
        remainingTicks = Math.max(0, newRemainingTicks);
        totalTicks = Math.max(0, newTotalTicks);
        automaticEvents = newAutomaticEvents;
    }

    public static void clientTick() {
        if (phase != VibrosEventHandler.PHASE_IDLE && remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public static void clear() {
        phase = VibrosEventHandler.PHASE_IDLE;
        remainingTicks = 0;
        totalTicks = 0;
        automaticEvents = false;
    }

    public static int phase() {
        return phase;
    }

    public static int remainingTicks() {
        return remainingTicks;
    }

    public static int totalTicks() {
        return totalTicks;
    }

    public static boolean automaticEvents() {
        return automaticEvents;
    }

    public static boolean isWarning() {
        return phase == VibrosEventHandler.PHASE_WARNING;
    }

    public static boolean isActive() {
        return phase == VibrosEventHandler.PHASE_ACTIVE;
    }
}
