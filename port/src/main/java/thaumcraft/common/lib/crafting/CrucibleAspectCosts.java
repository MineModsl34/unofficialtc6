package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/** Source arithmetic and ranking without a game process, registries, inventory or world. */
public final class CrucibleAspectCosts {
    public static final int MAX_ASPECTS = 256;
    public static final int MAX_AMOUNT = 32767;
    public static final Codec<AspectList> CODEC = Codec.unboundedMap(Aspect.CODEC, Codec.intRange(1, MAX_AMOUNT))
        .validate(map -> map.size() <= MAX_ASPECTS ? DataResult.success(map) : DataResult.error(() -> "Too many crucible aspects"))
        .xmap(map -> { AspectList list = new AspectList(); list.aspects.putAll(map); return list; }, list -> new LinkedHashMap<>(list.aspects));

    private CrucibleAspectCosts() {}

    public static boolean valid(AspectList costs) {
        return costs != null && costs.size() <= MAX_ASPECTS && costs.aspects.entrySet().stream().allMatch(entry ->
            entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0 && entry.getValue() <= MAX_AMOUNT);
    }

    public static boolean matches(AspectList costs, @Nullable AspectList available) {
        if (available == null) return false;
        for (Aspect aspect : costs.getAspects()) if (available.getAmount(aspect) < costs.getAmount(aspect)) return false;
        return true;
    }

    public static AspectList subtract(AspectList costs, AspectList available) {
        AspectList remaining = new AspectList();
        // Source putAll, not copy(): retains unrelated raw zero-valued entries too.
        remaining.aspects.putAll(available.aspects);
        for (Aspect aspect : costs.getAspects()) remaining.remove(aspect, costs.getAmount(aspect));
        return remaining;
    }

    /** Greatest sum, not greatest number of aspect types; equal-cost first encounter wins.
     * TC6 starts at zero, so even a matching empty-cost recipe is not a craft candidate.
     */
    @Nullable public static <T> T greatestMatching(Iterable<T> candidates, Predicate<T> matches, ToIntFunction<T> cost) {
        int highest = 0;
        T selected = null;
        for (T candidate : candidates) if (matches.test(candidate)) {
            int current = cost.applyAsInt(candidate);
            if (current > highest) { highest = current; selected = candidate; }
        }
        return selected;
    }
}
