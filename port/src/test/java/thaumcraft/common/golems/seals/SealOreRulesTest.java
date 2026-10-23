package thaumcraft.common.golems.seals;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SealOreRulesTest {
 @Test void reviewedWildcardFamiliesRemainDistinct(){for(String group:new String[]{"minecraft:wooden_stairs","minecraft:wooden_slabs","minecraft:wooden_fences","minecraft:fence_gates","minecraft:wooden_doors","minecraft:wooden_trapdoors","minecraft:leaves","minecraft:saplings","minecraft:music_discs","minecraft:wool","c:nitor","c:dyes"})assertTrue(SealOreRules.isReviewedTag(group),group);}
 @Test void oldDyeWildcardIncludesItsFlattenedMaterialVariants(){assertTrue(SealOreRules.sameVanillaGroup("minecraft:ink_sac","minecraft:lapis_lazuli"));assertTrue(SealOreRules.sameVanillaGroup("minecraft:bone_meal","minecraft:cocoa_beans"));assertTrue(SealOreRules.sameVanillaGroup("minecraft:red_dye","minecraft:yellow_dye"));assertFalse(SealOreRules.sameVanillaGroup("minecraft:glow_ink_sac","minecraft:ink_sac"));}
 @Test void broadModernMaterialParentsAreNeverEquivalences(){for(String tag:new String[]{"c:ores","c:ingots","c:gems","c:storage_blocks","c:foods","c:tools","c:enchantables"})assertFalse(SealOreRules.isReviewedTag(tag),tag);assertTrue(SealOreRules.isReviewedTag("c:ores/copper"));assertTrue(SealOreRules.isReviewedTag("c:plates/thaumium"));}
 @Test void sourceGlassAndChestFamiliesDoNotConflateNeighbours(){assertTrue(SealOreRules.sameVanillaGroup("minecraft:white_stained_glass","minecraft:glass"));assertTrue(SealOreRules.sameVanillaGroup("minecraft:red_stained_glass_pane","minecraft:glass_pane"));assertFalse(SealOreRules.sameVanillaGroup("minecraft:glass","minecraft:glass_pane"));assertFalse(SealOreRules.sameVanillaGroup("minecraft:glass","minecraft:tinted_glass"));assertTrue(SealOreRules.sameVanillaGroup("minecraft:ender_chest","minecraft:trapped_chest"));}
}

