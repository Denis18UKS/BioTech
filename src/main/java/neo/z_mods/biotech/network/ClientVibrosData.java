package neo.z_mods.biotech.network;

import neo.z_mods.biotech.VibrosEventHandler;
import net.minecraft.util.Mth;

/**
 * Клиентская копия состояния биовыброса.
 *
 * <p>Класс не содержит ссылок на {@code net.minecraft.client}, поэтому может
 * безопасно присутствовать и на выделенном сервере.</p>
 */
public final class ClientVibrosData {
    private static int phase = VibrosEventHandler.PHASE_IDLE;
    private static int remainingTicks;
    private static int totalTicks;
    private static boolean automaticEvents;

    private ClientVibrosData() {
    }

    public static void update(int newPhase, int newRemainingTicks, int newTotalTicks, boolean newAutomaticEvents) {
        int sanitizedRemaining = Math.max(0, newRemainingTicks);
        int sanitizedTotal = Math.max(0, newTotalTicks);

        // Сервер присылает контрольное состояние раз в секунду, а клиент между
        // пакетами сам уменьшает таймер. При сетевой задержке пакет может быть на
        // несколько тиков «старше» клиентского значения. Не позволяем такому
        // пакету визуально откатывать таймер и восстанавливать прогресс-бар.
        boolean sameTimedPhase = phase == newPhase
                && newPhase != VibrosEventHandler.PHASE_IDLE
                && totalTicks == sanitizedTotal;

        phase = newPhase;
        if (sameTimedPhase) {
            remainingTicks = Math.min(remainingTicks, sanitizedRemaining);
        } else {
            remainingTicks = sanitizedRemaining;
            totalTicks = sanitizedTotal;
        }
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

    /** Прогресс текущей фазы от 0 в начале до 1 в конце. */
    public static float phaseProgress() {
        if (totalTicks <= 0) {
            return 0.0F;
        }
        return Mth.clamp(1.0F - remainingTicks / (float) totalTicks, 0.0F, 1.0F);
    }

    public static float remainingFraction() {
        if (totalTicks <= 0) {
            return 0.0F;
        }
        return Mth.clamp(remainingTicks / (float) totalTicks, 0.0F, 1.0F);
    }

    public static boolean isWarning() {
        return phase == VibrosEventHandler.PHASE_WARNING;
    }

    public static boolean isActive() {
        return phase == VibrosEventHandler.PHASE_ACTIVE;
    }

    public static boolean isDissipating() {
        return phase == VibrosEventHandler.PHASE_DISSIPATING;
    }

    public static boolean isStormSequence() {
        return isWarning() || isActive() || isDissipating();
    }
}
