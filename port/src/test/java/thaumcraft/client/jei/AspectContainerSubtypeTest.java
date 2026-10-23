package thaumcraft.client.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AspectContainerSubtypeTest {
    @Test void exactAspectQuantitiesAndFiltersDistinguishContainerVariants() {
        var crystal = AspectContainerSubtype.of(Map.of("aer", 1), "");
        var phial = AspectContainerSubtype.of(Map.of("aer", 8), "");
        var fire = AspectContainerSubtype.of(Map.of("ignis", 1), "");
        assertNotEquals(crystal, phial);
        assertNotEquals(crystal, fire);
        assertNotEquals(crystal, AspectContainerSubtype.of(Map.of("aer", 1), "aer"));
        assertEquals(crystal, AspectContainerSubtype.of(Map.of("aer", 1), null));
    }
    @Test void equivalentContentIgnoresInsertionOrderAndCannotAliasMutableSource() {
        Map<String, Integer> source = new LinkedHashMap<>(); source.put("terra", 4); source.put("aer", 8);
        var first = AspectContainerSubtype.of(source, "aer");
        Map<String, Integer> reverse = new LinkedHashMap<>(); reverse.put("aer", 8); reverse.put("terra", 4);
        assertEquals(first, AspectContainerSubtype.of(reverse, "aer"));
        source.clear();
        assertEquals(2, first.aspects().size());
        assertThrows(UnsupportedOperationException.class, () -> first.aspects().clear());
    }
    @Test void emptyContainersArePlainWhileAnEmptyFilteredJarIsStillDistinct() {
        assertTrue(AspectContainerSubtype.of(Map.of(), "").isEmpty());
        assertFalse(AspectContainerSubtype.of(Map.of(), "aer").isEmpty());
        assertTrue(AspectContainerSubtype.of(Map.of("aer", 0), null).isEmpty());
    }
}
