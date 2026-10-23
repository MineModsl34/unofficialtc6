package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class ResearchReadFingerprintTest {
    @Test void sourceOrderingAndTransientPopupDoNotChangeTheWireToken() {
        assertEquals(ResearchReadFingerprint.of(snapshot("PAGE,RESEARCH,POPUP", false)),
            ResearchReadFingerprint.of(snapshot("RESEARCH,PAGE", true)));
    }
    @Test void ReadFlagsAndKnowledgeChangesInvalidateTheTokenEvenAtSameStage() {
        var original = snapshot("PAGE", false);
        assertNotEquals(ResearchReadFingerprint.of(original), ResearchReadFingerprint.of(snapshot("RESEARCH", false)));
        var changed = original.copy(); changed.getList("knowledge", 10).getCompound(0).putInt("amount", 17);
        assertNotEquals(ResearchReadFingerprint.of(original), ResearchReadFingerprint.of(changed));
    }
    @Test void CanonicalizationDoesNotMutateTheReceivedBody() {
        var value = snapshot("PAGE,POPUP", false); var before = value.copy();
        assertTrue(ResearchReadFingerprint.of(value).matches("[0-9a-f]{64}"));
        assertEquals(before, value);
    }
    private static CompoundTag snapshot(String flags, boolean reversed) {
        var result = new CompoundTag(); var research = new ListTag();
        var a = new CompoundTag(); a.putString("key", "A"); a.putInt("stage", 2); a.putString("flags", flags);
        var b = new CompoundTag(); b.putString("key", "B"); b.putInt("stage", 1);
        research.add(reversed ? b : a); research.add(reversed ? a : b); result.put("research", research);
        var knowledge = new ListTag(); var pool = new CompoundTag(); pool.putString("key", "O_BASICS"); pool.putInt("amount", 16);
        knowledge.add(pool); result.put("knowledge", knowledge); return result;
    }
}
