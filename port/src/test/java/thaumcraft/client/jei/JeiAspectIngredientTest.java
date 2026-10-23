package thaumcraft.client.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JeiAspectIngredientTest {
    @Test void quantitiesAreExactButLookupIdentityIsShared() {
        var one = new JeiAspectIngredient("aer");
        var many = new JeiAspectIngredient("aer", 327);
        assertEquals(one.lookupKey(), many.lookupKey());
        assertEquals(one, many.normalized());
        assertEquals(327, many.amount());
        assertEquals(new JeiAspectIngredient("aer", 500), many.withAmount(500));
        assertEquals(327, many.amount());
    }
    @Test void allPositiveQuantitiesSurviveWithoutTruncatingOrInventingAmounts() {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        for (int index = 20; index >= 0; index--) amounts.put("addon" + index, index + 1);
        var ingredients = JeiAspectIngredient.fromAmounts(amounts);
        assertEquals(21, ingredients.size());
        assertTrue(ingredients.stream().allMatch(ingredient -> amounts.get(ingredient.tag()) == ingredient.amount()));
        assertEquals(ingredients.stream().map(JeiAspectIngredient::tag).sorted().toList(),
            ingredients.stream().map(JeiAspectIngredient::tag).toList());
        assertThrows(UnsupportedOperationException.class, () -> ingredients.add(new JeiAspectIngredient("aer")));
    }
    @Test void zeroNegativeAndMissingAspectsDoNotBecomeUsableSources() {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        amounts.put("aer", 1);
        amounts.put("terra", 0);
        amounts.put("ignis", -2);
        amounts.put("aqua", null);
        amounts.put(null, 9);
        amounts.put("", 7);
        assertEquals(List.of(new JeiAspectIngredient("aer")), JeiAspectIngredient.fromAmounts(amounts));
    }
    @Test void invalidCountsAreRejectedWithoutWraparound() {
        assertThrows(IllegalArgumentException.class, () -> new JeiAspectIngredient("aer", 0));
        assertThrows(IllegalArgumentException.class, () -> new JeiAspectIngredient("aer", -1));
        assertThrows(IllegalArgumentException.class, () -> new JeiAspectIngredient(" ", 1));
        var aspect = new JeiAspectIngredient("aer");
        assertThrows(IllegalArgumentException.class, () -> aspect.withAmount(0));
        assertThrows(IllegalArgumentException.class, () -> aspect.withAmount((long) Integer.MAX_VALUE + 1));
    }
}
