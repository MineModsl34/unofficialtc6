package thaumcraft.api.research.theorycraft;

import java.util.HashMap;
import java.util.function.Supplier;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard;
import thaumcraft.common.lib.research.theorycraft.TheorycraftAids;

/** Registration order and HashMap iteration follow ConfigResearch's original built-in set. */
public final class TheorycraftManager {
    public static final HashMap<String, ITheorycraftAid> aids = new HashMap<>();
    public static final HashMap<String, Supplier<? extends TheorycraftCard>> cards = new HashMap<>();
    private TheorycraftManager() {}
    public static void registerAid(String sourceKey, ITheorycraftAid aid) { aids.putIfAbsent(sourceKey, aid); }
    public static void registerAid(ITheorycraftAid aid) { registerAid(aid.getClass().getName(), aid); }
    public static void registerCard(String sourceKey, Supplier<? extends TheorycraftCard> card) { cards.putIfAbsent(sourceKey, card); }
    public static void registerCard(Class<? extends TheorycraftCard> type) {
        registerCard(type.getName(), () -> {
            try { return type.getDeclaredConstructor().newInstance(); }
            catch (ReflectiveOperationException exception) { throw new IllegalArgumentException("Cannot instantiate theory card " + type.getName(), exception); }
        });
    }
    public static void initialize() {
        for (SourceTheorycraftCard.Kind kind : SourceTheorycraftCard.Kind.values()) {
            // TC6 omitted both registrations; in particular AidBasicEldritch's Truth draw fails.
            if (kind != SourceTheorycraftCard.Kind.TRUTH && kind != SourceTheorycraftCard.Kind.DRAGON_EGG)
                registerCard(kind.sourceKey(), () -> new SourceTheorycraftCard(kind));
        }
        TheorycraftAids.register();
    }
}
