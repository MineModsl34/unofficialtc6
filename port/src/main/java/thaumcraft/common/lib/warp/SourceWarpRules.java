package thaumcraft.common.lib.warp;

import java.util.function.IntUnaryOperator;

/** Literal TC6 WarpEvents arithmetic. Random values are supplied in source order. */
public final class SourceWarpRules {
    private SourceWarpRules() {}

    public enum Event {
        NONE, CREEPER_SOUND, EXPLOSION_SOUND, UNEASE, VIS_EXHAUST, THAUMARHIA,
        HUNGER, WATCHED, MIST_ONE, BLURRED, SUN_SCORNED, FATIGUE, INFECTIOUS_EXHAUST,
        NIGHT_VISION, DEATH_GAZE, FALSE_SPIDERS, VOICES, MIST_FEW, BLINDNESS,
        CLARITY, STRONG_HUNGER, PORTAL, REAL_SPIDERS, MIST_MANY
    }

    public record Roll(boolean occurred, int effectiveWarp, int nextCounter, int effect, Event event) {}

    /** TC6 InternalMethodHandler's underflow quirk is intentionally NOT corrected to -current. */
    public static int adjustedAmount(int current, int amount) {
        return amount < 0 && current + amount < 0 ? current : amount;
    }

    public static int clampedValue(int current, int amount) {
        return Math.max(0, Math.min(500, current + adjustedAmount(current, amount)));
    }

    public static boolean shouldCheck(int ticks, boolean wussMode, boolean ward) {
        return !wussMode && ticks > 0 && ticks % 2000 == 0 && !ward;
    }

    /** temporary is the value AFTER the source's unconditional temporary -1 mutation. */
    public static Roll roll(int temporary, int normal, int permanent, int gear, int counter,
                            boolean fortressMaskZero, IntUnaryOperator nextInt) {
        int warp = temporary + normal + permanent + gear;
        int r = nextInt.applyAsInt(100); // Also consumed when counter or total warp is zero.
        if (counter <= 0 || warp <= 0 || r > Math.sqrt(counter)) {
            return new Roll(false, warp, counter, 0, Event.NONE);
        }
        warp = Math.min(100, (warp + warp + counter) / 3);
        int next = (int) (counter - Math.max(5.0, Math.sqrt(counter) * 2.0 - gear * 2));
        int effect = nextInt.applyAsInt(warp) + gear;
        if (fortressMaskZero) effect -= 2 + nextInt.applyAsInt(4);
        return new Roll(true, warp, next, effect, eventFor(effect));
    }

    public static Event eventFor(int effect) {
        if (effect <= 0) return Event.NONE;
        if (effect <= 4) return Event.CREEPER_SOUND;
        if (effect <= 8) return Event.EXPLOSION_SOUND;
        if (effect <= 12) return Event.UNEASE;
        if (effect <= 16) return Event.VIS_EXHAUST;
        if (effect <= 20) return Event.THAUMARHIA;
        if (effect <= 24) return Event.HUNGER;
        if (effect <= 28) return Event.WATCHED;
        if (effect <= 32) return Event.MIST_ONE;
        if (effect <= 36) return Event.BLURRED;
        if (effect <= 40) return Event.SUN_SCORNED;
        if (effect <= 44) return Event.FATIGUE;
        if (effect <= 48) return Event.INFECTIOUS_EXHAUST;
        if (effect <= 52) return Event.NIGHT_VISION;
        if (effect <= 56) return Event.DEATH_GAZE;
        if (effect <= 60) return Event.FALSE_SPIDERS;
        if (effect <= 64) return Event.VOICES;
        if (effect <= 68) return Event.MIST_FEW;
        if (effect <= 72) return Event.BLINDNESS;
        if (effect == 76) return Event.CLARITY;
        // Source 73..75 intentionally fall through to the stronger hunger branch.
        if (effect <= 80) return Event.STRONG_HUNGER;
        if (effect <= 88) return Event.PORTAL;
        if (effect <= 92) return Event.REAL_SPIDERS;
        return Event.MIST_MANY;
    }

    public static int guardians(Event event, int warp) {
        return Math.min(8, switch (event) {
            case MIST_ONE -> 1;
            case MIST_FEW -> warp / 30;
            case MIST_MANY -> warp / 15;
            default -> 0;
        });
    }

    public static int amplifier(int warp) { return Math.min(3, warp / 15); }
}
