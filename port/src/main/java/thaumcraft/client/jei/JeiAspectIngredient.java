package thaumcraft.client.jei;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A counted presentation ingredient; lookup identity deliberately ignores the amount. */
public record JeiAspectIngredient(String tag, int amount) {
    public JeiAspectIngredient {
        Objects.requireNonNull(tag, "aspect tag");
        if (tag.isBlank()) throw new IllegalArgumentException("Empty aspect tag");
        if (amount <= 0) throw new IllegalArgumentException("Aspect amount must be positive");
    }

    public JeiAspectIngredient(String tag) { this(tag, 1); }
    public String lookupKey() { return tag; }
    public JeiAspectIngredient normalized() { return amount == 1 ? this : new JeiAspectIngredient(tag); }
    public JeiAspectIngredient withAmount(long requested) {
        if (requested < 1 || requested > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Aspect amount outside positive integer range");
        return new JeiAspectIngredient(tag, (int) requested);
    }

    public static List<JeiAspectIngredient> fromAmounts(Map<String, Integer> amounts) {
        return amounts.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank()
                && entry.getValue() != null && entry.getValue() > 0)
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new JeiAspectIngredient(entry.getKey(), entry.getValue())).toList();
    }
}
