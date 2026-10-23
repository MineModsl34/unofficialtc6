package thaumcraft.api.research.theorycraft;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class ResearchTableDataTest {
    @Test void totalsAndInspirationRetainSourceArithmeticAndRemoval() {
        var data = new ResearchTableData(null); data.inspirationStart = 5; data.inspiration = 3;
        data.addTotal("BASICS", 10); data.addTotal("BASICS", -3); assertEquals(7, data.getTotal("BASICS"));
        data.addTotal("BASICS", -7); assertFalse(data.hasTotal("BASICS"));
        data.addTotal("A", Integer.MAX_VALUE); data.addTotal("A", 1); assertFalse(data.hasTotal("A")); // Source int overflow, not saturation.
        data.addInspiration(7); assertEquals(5, data.inspiration); data.addInspiration(-7); assertEquals(-2, data.inspiration); assertTrue(data.isComplete());
    }
    @Test void noteRoundTripKeepsOriginalFieldsAndDetachesNestedLists() {
        var data = new ResearchTableData(null); data.player = "Researcher"; data.owner = UUID.randomUUID();
        data.inspiration = 3; data.inspirationStart = 7; data.bonusDraws = 2; data.placedCards = 4; data.aidsChosen = 1; data.penaltyStart = 2;
        data.savedCards.add(99L); data.aidCards.add("Source.Card"); data.categoriesBlocked.add("A"); data.addTotal("B", 40); data.addTotal("A", 10);
        CompoundTag serialized = data.serialize(RegistryAccess.EMPTY);
        var copy = new ResearchTableData(null); copy.deserialize(serialized, RegistryAccess.EMPTY);
        assertEquals(serialized, copy.serialize(RegistryAccess.EMPTY));
        data.savedCards.add(123L); data.addTotal("A", 50); assertEquals(1, copy.savedCards.size()); assertEquals(10, copy.getTotal("A"));
        serialized.putString("player", "changed"); assertEquals("Researcher", copy.player);
    }
    @Test void unknownCardsAreNotInventedAndOversizedNotesFailClosed() {
        var tag = new CompoundTag(); var choices = new ListTag(); var unknown = new CompoundTag(); unknown.putString("cardChoice", "Unknown.Card"); choices.add(unknown); tag.put("cardChoices", choices);
        var data = new ResearchTableData(null); data.deserialize(tag, RegistryAccess.EMPTY); assertTrue(data.cardChoices.isEmpty()); assertNull(data.lastDraw);
        for (int index = 0; index < 3; index++) choices.add(unknown.copy()); assertThrows(IllegalArgumentException.class, () -> data.deserialize(tag, RegistryAccess.EMPTY));
        var huge = new ListTag(); for (int index = 0; index < 4097; index++) huge.add(new CompoundTag());
        var oversized = new CompoundTag(); oversized.put("savedCards", huge); assertThrows(IllegalArgumentException.class, () -> data.deserialize(oversized, RegistryAccess.EMPTY));
    }
}
