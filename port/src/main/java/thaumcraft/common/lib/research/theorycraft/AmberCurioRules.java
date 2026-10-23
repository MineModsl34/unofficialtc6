package thaumcraft.common.lib.research.theorycraft;

/** Literal source chance, isolated from block bootstrap for deterministic comparison. */
public final class AmberCurioRules {
    private AmberCurioRules() {}
    public static boolean isCurioRoll(float value) { return value < 0.066D; }
}
