package thaumcraft.common.tiles.essentia;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Explicit conservation bridge for source input's add-before-take duplication bug.
 * One actually acquired unit is retained until the real remote store commits successfully.
 * Caller persists get()/restore() as native BE NBT; no receipt, retry, or failed pull creates units.
 */
public final class OneUnitEscrow<A> {
    private A held;
    public A get() { return held; }
    public void restore(A value) { held = value; }
    public boolean acquire(A aspect, BooleanSupplier takeOne) {
        if (aspect == null || held != null || !takeOne.getAsBoolean()) return false;
        held = aspect; return true;
    }
    public boolean deposit(A aspect) { if (aspect == null || held != null) return false; held = aspect; return true; }
    public boolean commit(Predicate<A> destinationAcceptsOne) {
        if (held == null || !destinationAcceptsOne.test(held)) return false;
        held = null; return true;
    }
}
