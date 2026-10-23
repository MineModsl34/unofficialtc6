package thaumcraft.common.blocks.misc;

/** Forge 1.12 IFluidBlock/FluidBlockWrapper scalar volume contracts; eight finite125mB quanta. */
public final class SourceFluidBlockRules {
    private SourceFluidBlockRules() {}
    public static int finiteFill(int existingQuanta, int offeredMb) {
        if (existingQuanta < 0 || existingQuanta > 8) throw new IllegalArgumentException("Finite goo quanta must be 0..8");
        if (offeredMb <= 0) return 0;
        return Math.min(8 - existingQuanta, offeredMb / 125) * 125;
    }
    public static int finiteDrain(int quanta, int requestedMb) {
        if (quanta < 0 || quanta > 8) throw new IllegalArgumentException("Finite goo quanta must be 0..8");
        int cell = quanta * 125;
        return requestedMb >= cell ? cell : 0; // The source wrapper never partially drains a cell.
    }
    public static int classicDrain(boolean source, int requestedMb) { return source && requestedMb >= 1000 ? 1000 : 0; }
    public static int sourceTankDisplay(int effectiveQuanta, int totalQuanta) {
        return Math.round(1000 * (effectiveQuanta + 1F) / (totalQuanta + 1F));
    }
}
