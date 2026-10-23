package thaumcraft.client.gui.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ResearchFocusIconTest {
    @Test void allTwentyOneSourceElementsHaveImportedArtAndCorrectLayerKind() {
        for (String key : new String[]{"ROOT","TOUCH","BOLT","PROJECTILE","CLOUD","MINE","PLAN","SPELLBAT",
            "FIRE","FROST","AIR","EARTH","FLUX","BREAK","RIFT","EXCHANGE","CURSE","HEAL","SCATTER","SPLITTARGET","SPLITTRAJECTORY"}) {
            var icon = ResearchFocusIcon.parse("focus:thaumcraft." + key); assertNotNull(icon);
            assertNotNull(getClass().getResource("/assets/thaumcraft/" + icon.icon().getPath()));
            if (icon.backing() != null) assertNotNull(getClass().getResource("/assets/thaumcraft/" + icon.backing().getPath()));
        }
        assertEquals(16734721, ResearchFocusIcon.parse("focus:thaumcraft.FIRE").color());
        assertTrue(ResearchFocusIcon.parse("focus:thaumcraft.FIRE").backing().getPath().endsWith("_effect.png"));
        assertTrue(ResearchFocusIcon.parse("focus:thaumcraft.TOUCH").backing().getPath().endsWith("_medium.png"));
        assertNull(ResearchFocusIcon.parse("focus:thaumcraft.SCATTER").backing());
    }
    @Test void focusRenderingDoesNotGuessUnregisteredOrCaseFoldedNames() {
        assertNull(ResearchFocusIcon.parse(null)); assertNull(ResearchFocusIcon.parse("focus:other.FIRE"));
        assertNull(ResearchFocusIcon.parse("focus:thaumcraft.fire")); assertNull(ResearchFocusIcon.parse("thaumcraft.FIRE"));
        assertNotNull(ResearchFocusIcon.parse("focus: thaumcraft.FIRE "));
    }
}
