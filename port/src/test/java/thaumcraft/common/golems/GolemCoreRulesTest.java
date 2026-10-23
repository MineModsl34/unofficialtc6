package thaumcraft.common.golems;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GolemCoreRulesTest {
    @Test void propertiesRetainSignedHalvesAndAllEightBytes() {
        assertEquals(0xfedcba9876543210L, GolemCoreRules.joinProperties(0xfedcba98, 0x76543210));
        assertEquals(0x12345678ffffffffL, GolemCoreRules.joinProperties(0x12345678, -1));
    }
    @Test void sourceColorAndFlagBytesAreBigEndianAndOtherBytesSurvive() {
        int packed = 0x0108abcd;
        assertEquals(1, GolemCoreRules.color(packed));
        assertEquals(8, GolemCoreRules.flags(packed));
        assertEquals(0x1008abcd, GolemCoreRules.withColor(packed, (byte) 16));
        assertEquals(0x0102abcd, GolemCoreRules.withFlags(packed, (byte) 2));
        assertEquals(-1, GolemCoreRules.flags(0x00ff0000));
    }
    @Test void fragilityTruncatesBeforeRankAndAfterArmorBonus() {
        assertEquals(16, GolemCoreRules.health(6, 0, false));
        assertEquals(13, GolemCoreRules.health(6, 1, true));
        assertEquals(1, GolemCoreRules.armor(2, false, true));
        assertEquals(2, GolemCoreRules.armor(2, true, true));
    }
    @Test void brutalDamageUsesBothSourceBranchesAndNonfightersRemainHarmless() {
        assertEquals(2, GolemCoreRules.damage(1, 0, true, true));
        assertEquals(8.5, GolemCoreRules.damage(4, 10, true, true));
        assertEquals(0, GolemCoreRules.damage(4, 10, false, true));
    }
    @Test void sourceMovementModifiersCombineWithoutChangingBaseAttribute() {
        assertEquals(1.2F, GolemCoreRules.moveSpeed(0, true, false, false, false), 0.000001F);
        assertEquals(0.825F, GolemCoreRules.moveSpeed(0, false, true, false, false), 0.000001F);
        assertEquals(1.195F, GolemCoreRules.moveSpeed(10, true, true, true, true), 0.000001F);
    }
    @Test void blastproofScalesThenCapsAtHalfMaximumHealth() {
        assertEquals(3F, GolemCoreRules.blastDamage(30, 10), 0.000001F);
        assertEquals(15F, GolemCoreRules.blastDamage(30, 100), 0.000001F);
    }
}
