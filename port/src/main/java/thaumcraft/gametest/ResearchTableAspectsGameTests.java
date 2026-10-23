package thaumcraft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** ConfigAspects:799's explicit conversion tag, not a fabricated ordinary table recipe. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchTableAspectsGameTests {
    @GameTest(template = "empty")
    public static void convertedTableKeepsTheWoodenTableAspectsAndAddsExactlyFiveMindWithoutAliasing(GameTestHelper helper) {
        ItemStack wood = new ItemStack(BlocksTC.tableWood), research = new ItemStack(BlocksTC.researchTable);
        AspectList base = ThaumcraftCraftingManager.getObjectTags(wood);
        helper.assertTrue(base != null && base.size() > 0, "actual wooden-table recipe/complex tag available");
        AspectList before = base.copy();
        AspectList actual = ThaumcraftCraftingManager.getObjectTags(research);
        helper.assertTrue(actual != null && actual.size() == before.size() + (before.getAmount(Aspect.MIND) == 0 ? 1 : 0),
            "research table has only inherited aspects plus the source mind aspect");
        for (Aspect aspect : Aspect.aspects.values()) {
            helper.assertTrue(actual.getAmount(aspect) == before.getAmount(aspect) + (aspect == Aspect.MIND ? 5 : 0),
                "source conversion amount for " + aspect.getTag());
        }
        helper.assertTrue(ThaumcraftCraftingManager.getObjectTags(wood).aspects.equals(before.aspects),
            "research-table registration did not add mind to its wooden predecessor");
        actual.add(Aspect.MIND, 100);
        helper.assertTrue(ThaumcraftCraftingManager.getObjectTags(research).getAmount(Aspect.MIND) == before.getAmount(Aspect.MIND) + 5,
            "callers receive a detached aspect list");
        helper.assertTrue(LegacyScanIdentity.identify(research).sourceProven()
            && LegacyScanIdentity.itemResearchKey(research).equals("!thaumcraft:research_table0")
            && LegacyScanIdentity.blockResearchKey(BlocksTC.researchTable).equals("!thaumcraft:research_table"),
            "original item/block generic keys remain distinct and proven");
        helper.succeed();
    }
}
