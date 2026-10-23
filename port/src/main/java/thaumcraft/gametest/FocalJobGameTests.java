package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;
import thaumcraft.common.tiles.crafting.FocusElementNode;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;
import thaumcraft.common.world.aura.AuraHandler;

/** Real paid jobs for all three focus items, including resume from native persisted job data. */
@GameTestHolder(Thaumcraft.MODID)
public final class FocalJobGameTests {
    @GameTestGenerator public static Collection<TestFunction> allTiers() {
        var tests = new ArrayList<TestFunction>();
        for (int tier = 0; tier < 3; tier++) {
            int selected = tier;
            tests.add(new TestFunction("bulk_focus_jobs", "thaumcraft.bulk_focus_paid_tier_" + (tier + 1), "thaumcraft:scan_test", 220, 0, true, h -> craft(h, selected)));
        }
        return tests;
    }
    private static void craft(GameTestHelper h, int tier) {
        var player = BulkGameplayFixtures.player(h);
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        for (var category : ResearchCategories.researchCategories.values()) for (var entry : category.research.values()) {
            knowledge.addResearch(entry.getKey()); knowledge.setResearchStage(entry.getKey(), entry.getStages() == null ? 1 : entry.getStages().length + 1);
        }
        player.giveExperienceLevels(30);
        player.getInventory().setItem(1, ThaumcraftApiHelper.makeCrystal(Aspect.AVERSION));
        player.getInventory().setItem(2, ThaumcraftApiHelper.makeCrystal(Aspect.FIRE));
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:wand_workbench"));
        var tile = BulkGameplayFixtures.tile(h, new BlockPos(4, 2, 4), block, TileFocalManipulator.class);
        var item = tier == 0 ? ItemsTC.focus1 : tier == 1 ? ItemsTC.focus2 : ItemsTC.focus3; tile.setItem(0, new ItemStack(item));
        var nodes = new ArrayList<FocusElementNode>();
        for (int i = 0; i < 3; i++) {
            var node = new FocusElementNode(); node.id = i; node.parent = i - 1; node.children = i == 2 ? new int[0] : new int[]{i + 1};
            node.node = i == 0 ? new FocusMediumRoot() : i == 1 ? new FocusMediumTouch() : new FocusEffectFire(); nodes.add(node);
        }
        var chunk = h.getLevel().getChunkAt(tile.getBlockPos()); var aura = AuraHandler.getAuraChunk(h.getLevel(), chunk.getPos().x, chunk.getPos().z);
        h.assertTrue(aura != null, "focus job uses native chunk aura"); aura.setVis(500);
        h.assertTrue(tile.apply(player, TileFocalManipulator.writeNodes(nodes), "Bulk tier " + (tier + 1), true), "actual tier job accepted");
        h.assertTrue(tile.busy() && player.experienceLevel < 30 && player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(2).isEmpty(), "XP and two typed crystals paid before job starts");
        h.assertTrue(tile.initialVis() == new int[]{43, 45, 50}[tier], "source tier-dependent vis cost: " + tile.initialVis());
        // No result/package injected: reload the paid busy job, then its ordinary BE ticker finishes it.
        var saved = tile.saveWithFullMetadata(h.getLevel().registryAccess()); tile.loadWithComponents(saved, h.getLevel().registryAccess());
        h.succeedWhen(() -> {
            var result = tile.getItem(0); var pack = ItemFocus.getPackage(result);
            h.assertTrue(!tile.busy() && result.is(item) && pack != null && pack.nodes.size() == 3 && pack.getComplexity() == 4, "paid native tier job produced its actual executable package");
            h.assertTrue(aura.getVis() < 500, "job consumed real aura after resume");
        });
    }
}
