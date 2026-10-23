package thaumcraft.common.lib.crafting;

/** Engine-independent source rules used by the actual dust countdown and identity adapter. */
public final class DustConversionRules {
    public static final int DELAY_DECREMENTS = 50;
    private DustConversionRules() {}

    /** Source Blocks.CAULDRON included every water LEVEL but neither lava nor powder snow. */
    public static boolean sourceCauldron(boolean emptyCauldron, boolean waterCauldron) {
        return emptyCauldron || waterCauldron;
    }

    public static final class Countdown {
        private int remaining;
        public Countdown(int delay) {
            if (delay < 0 || delay > 12000) throw new IllegalArgumentException("Dust delay outside the bounded server queue");
            remaining = delay;
        }
        /** Source checks >0 before decrement, so delay50 executes on END51, not END50. */
        public boolean readyOnEnd() {
            if (remaining > 0) { remaining--; return false; }
            return true;
        }
    }
}
