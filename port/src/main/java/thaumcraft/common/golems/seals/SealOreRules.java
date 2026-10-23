package thaumcraft.common.golems.seals;
import java.util.*;
/** Reviewed 1.12 OreDictionary.initVanillaEntries + TC OreDictionaryEntries groups.
 * No arbitrary parent c: tag acts as an equivalence class (e.g. all ores/ingots).
 */
public final class SealOreRules {
 public static final Set<String> GROUP_TAGS=Set.of("minecraft:logs","minecraft:planks","minecraft:wooden_slabs","minecraft:wooden_stairs","minecraft:wooden_fences","minecraft:fence_gates","minecraft:wooden_doors","minecraft:wooden_trapdoors","minecraft:saplings","minecraft:leaves","minecraft:wool","minecraft:music_discs","minecraft:sand","c:dyes","c:glass_blocks","c:glass_panes","c:chests","c:sandstone/blocks","c:nitor","c:workbenches","c:rods/wooden","c:vines","c:slime_balls","c:ender_pearls","c:bones","c:gunpowders","c:strings","c:nether_stars","c:leathers","c:feathers","c:eggs");
 private static final Set<String> COLORS=Set.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black");
 private SealOreRules(){}
 public static boolean isReviewedTag(String id){return GROUP_TAGS.contains(id)||id.matches("c:(ingots|nuggets|dusts|gems|ores|storage_blocks|plates|rods|clusters)/[a-z0-9_]+");}
 /** Old dye wildcard also covered these four flattened materials, not just modern dye items. */
 public static String vanillaGroup(String id){if(!id.startsWith("minecraft:"))return null;String p=id.substring(10);if(Set.of("ink_sac","lapis_lazuli","bone_meal","cocoa_beans").contains(p)||p.endsWith("_dye")&&COLORS.contains(p.substring(0,p.length()-4)))return "dye";if(Set.of("chest","ender_chest","trapped_chest").contains(p))return "chest";if(Set.of("sandstone","cut_sandstone","chiseled_sandstone","red_sandstone","cut_red_sandstone","chiseled_red_sandstone").contains(p))return "sandstone";if(p.equals("glass")||p.endsWith("_stained_glass")&&COLORS.contains(p.substring(0,p.length()-14)))return "blockGlass";if(p.equals("glass_pane")||p.endsWith("_stained_glass_pane")&&COLORS.contains(p.substring(0,p.length()-19)))return "paneGlass";return null;}
 public static boolean sameVanillaGroup(String a,String b){var group=vanillaGroup(a);return group!=null&&group.equals(vanillaGroup(b));}
}

