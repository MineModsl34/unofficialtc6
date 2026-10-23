package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Pure source arithmetic/multiplicity only; no registries, game or recipes accepted here. */
class OrdinaryCustomRulesTest {
    @Test void defaultPurpleParticipatesEvenWithoutAStoredArmorColor() {
        SourceRobeColorMixer mixer = new SourceRobeColorMixer();
        mixer.addArmorColor(6961280); mixer.addDyeColor(0xf9fffe);
        assertEquals(2, mixer.sampleCount());
        // Source white's integer means177/155/191, brightness191.5, not undyed white.
        assertEquals(0xb19bbf, mixer.finish());
    }
    @Test void brightnessIsPreservedInsteadOfAPlainChannelAverage() {
        SourceRobeColorMixer mixer = new SourceRobeColorMixer();
        mixer.addArmorColor(0xff0000); mixer.addDyeColor(0x0000ff);
        assertEquals(0xff00ff, mixer.finish());
    }
    @Test void blackZeroBrightnessFollowsSourceNaNIntZero() {
        SourceRobeColorMixer mixer = new SourceRobeColorMixer();
        mixer.addArmorColor(0); mixer.addDyeColor(0);
        assertEquals(0, mixer.finish());
    }
    @Test void armorSampleOrderAndMultipleDyesUseSourceTruncation() {
        SourceRobeColorMixer first = new SourceRobeColorMixer(), last = new SourceRobeColorMixer();
        first.addArmorColor(0x6a3880); first.addDyeColor(0xb02e26); first.addDyeColor(0x3c44aa);
        last.addDyeColor(0xb02e26); last.addDyeColor(0x3c44aa); last.addArmorColor(0x6a3880);
        assertEquals(first.finish(), last.finish()); assertEquals(3, first.sampleCount());
    }
    @Test void onlyThreeDistinctRawMetadataAndOneSugarCompleteTheTreat() {
        SourceTripleMeatRules rules = new SourceTripleMeatRules();
        assertTrue(rules.addMeat(0)); assertTrue(rules.addMeat(1)); assertTrue(rules.addMeat(5));
        assertFalse(rules.complete()); assertTrue(rules.addSugar()); assertTrue(rules.complete());
    }
    @Test void duplicateMeatAndDuplicateSugarRejectEvenWhenIngredientCountsWouldSuffice() {
        SourceTripleMeatRules rules = new SourceTripleMeatRules();
        assertTrue(rules.addSugar()); assertFalse(rules.addSugar());
        assertTrue(rules.addMeat(1)); assertFalse(rules.addMeat(1)); assertFalse(rules.complete());
    }
    @Test void fourthMeatRejectsAndSourceDoesNotRestrictUnusualMetadataToSixVariants() {
        SourceTripleMeatRules rules = new SourceTripleMeatRules();
        assertTrue(rules.addSugar()); assertTrue(rules.addMeat(-1)); assertTrue(rules.addMeat(32767));
        assertTrue(rules.addMeat(100)); assertTrue(rules.complete()); assertFalse(rules.addMeat(2));
    }
    @Test void zeroSamplesIsAnExplicitMalformedOverrideBoundary() {
        assertThrows(IllegalStateException.class, () -> new SourceRobeColorMixer().finish());
    }
    @Test void allSixCanonicalFlattenedMeatsPreserveSourceMetadataWithoutAnExtraHook() {
        String[] variants = {"beef", "chicken", "pork", "fish", "rabbit", "mutton"};
        for (int meta = 0; meta < variants.length; meta++) {
            assertEquals(Integer.valueOf(meta), SourceTripleMeatRules.canonicalChunkMetadata("thaumcraft", "chunk_" + variants[meta]));
        }
    }
    @Test void canonicalMeatMappingNeverAcceptsForeignNamespacesPrefixesOrUnprovedVariants() {
        assertNull(SourceTripleMeatRules.canonicalChunkMetadata("othermod", "chunk_beef"));
        assertNull(SourceTripleMeatRules.canonicalChunkMetadata("minecraft", "beef"));
        for (String path : new String[]{"chunk", "chunk_", "chunk_beef_extra", "chunk_salmon", "chunk_cooked_beef", "nugget_meat"}) {
            assertNull(SourceTripleMeatRules.canonicalChunkMetadata("thaumcraft", path), path);
        }
        assertNull(SourceTripleMeatRules.canonicalChunkMetadata("thaumcraft", null));
    }
}
