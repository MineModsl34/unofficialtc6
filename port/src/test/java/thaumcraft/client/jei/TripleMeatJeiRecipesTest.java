package thaumcraft.client.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TripleMeatJeiRecipesTest {
    @Test void everySourceChoiceOfThreeDistinctMeatsAppearsExactlyOnce() {
        var triples = TripleMeatJeiRecipes.triples(); assertEquals(20, triples.size());
        assertEquals(20, new HashSet<>(triples).size());
        for (var triple : triples) {
            assertTrue(triple.first() >= 0 && triple.third() < 6);
            assertTrue(triple.first() < triple.second() && triple.second() < triple.third());
        }
        for (int first = 0; first < 6; first++) for (int second = first + 1; second < 6; second++)
            for (int third = second + 1; third < 6; third++)
                assertTrue(triples.contains(new TripleMeatJeiRecipes.Triple(first, second, third)));
    }

    @Test void eachMeatHasTenTrueUsagesAndCombinationsCannotBeMutated() {
        var triples = TripleMeatJeiRecipes.triples();
        for (int meat = 0; meat < 6; meat++) {
            int selected = meat;
            assertEquals(10, triples.stream().filter(triple -> triple.first() == selected
                || triple.second() == selected || triple.third() == selected).count());
        }
        assertThrows(UnsupportedOperationException.class, () -> triples.clear());
    }
}
