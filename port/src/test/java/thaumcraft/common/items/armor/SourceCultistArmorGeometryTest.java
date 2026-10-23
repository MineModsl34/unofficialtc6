package thaumcraft.common.items.armor;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry.Kind;

class SourceCultistArmorGeometryTest {
    @Test void allOriginalBoxesAttachmentsAndSourceFingerprintsRemainFiniteAndComplete() {
        int[] boxes = {38, 58, 50}, inner = {26, 49, 32}, outer = {36, 45, 50}, mirrors = {5, 11, 6};
        String[] hashes = {"fb42ded8c643d05102ab326b44ca58f04252476af85c6c1449ff38b93a599c3f", "de76c2c434187bc1b9194c597e785b3af4009445fd10b627303f6913a72fd751", "9783b6d7b8a5f0e66b8ed03a34c5fbb161ef64046adb6611a4cea524edf67198"};
        for (Kind kind : Kind.values()) {
            var geometry = SourceCultistArmorGeometry.load(kind); int index = kind.ordinal();
            assertEquals(boxes[index], geometry.parts().size()); assertEquals(inner[index], geometry.parts().stream().filter(p -> p.included(true)).count());
            assertEquals(outer[index], geometry.parts().stream().filter(p -> p.included(false)).count()); assertEquals(mirrors[index], geometry.parts().stream().filter(SourceCultistArmorGeometry.Part::mirror).count());
            assertEquals(hashes[index], geometry.sha256()); assertEquals(geometry.parts().size(), geometry.parts().stream().map(SourceCultistArmorGeometry.Part::name).distinct().count());
            assertThrows(UnsupportedOperationException.class, () -> geometry.parts().clear());
        }
    }
    @Test void originalLateMirrorAssignmentsAndHalfMeshConditionAreNotSilentlyCorrected() {
        var knight = SourceCultistArmorGeometry.load(Kind.KNIGHT);
        var right = find(knight, "GauntletR"); var left = find(knight, "ShoulderL1");
        assertFalse(right.mirror(), "source mirror=true AFTER addBox leaves already-created box unmirrored");
        assertTrue(left.mirror(), "source BEFORE addBox really mirrors the box");
        assertEquals(new SourceCultistArmorGeometry.Vector(-3.5F, 3.5F, -2.5F), right.box().origin());
        assertFalse(find(knight, "Cloak1").included(true)); assertTrue(find(knight, "Cloak1").included(false));
        assertFalse(find(knight, "BeltR").included(true)); assertFalse(find(knight, "BeltR").included(false)); // Unused source object, not an extra visible belt.
        var robe = SourceCultistArmorGeometry.load(Kind.ROBE);
        for (String name : List.of("FrontclothR1", "ClothBackL3", "Focipouch")) { assertTrue(find(robe, name).included(true)); assertFalse(find(robe, name).included(false)); }
        assertTrue(find(robe, "Chestplate").included(false)); assertFalse(find(robe, "Chestplate").included(true));
        assertEquals(new SourceCultistArmorGeometry.Vector(-3, 11, -2.9F), find(robe, "FrontclothR1").offset());
    }
    private static SourceCultistArmorGeometry.Part find(SourceCultistArmorGeometry geometry, String name) { return geometry.parts().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow(); }
}
