package thaumcraft.common.lib.research.theorycraft;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard.Kind;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Independent literal TC6 card-effect oracle. It uses java.util.Random and a plain
 * TreeMap, not production card helpers or ResearchTableData mutation methods. Parameters
 * come from real initialized card NBT; fixtures are not survival-acquisition evidence. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class TheorycraftCardOracleGameTests {
    private static final class Expected {
        final TreeMap<String, Integer> totals;
        final ArrayList<String> blocked;
        int bonus, penalty, inspiration, levels, temporaryWarp, normalWarp, observationsSpent, extraInk, extraPaper;
        String crystal;
        long nextRandomLong;
        Expected(ResearchTableData data, int levels) {
            totals = new TreeMap<>(data.categoryTotals); blocked = new ArrayList<>(data.categoriesBlocked);
            bonus = data.bonusDraws; penalty = data.penaltyStart; inspiration = data.inspiration; this.levels = levels;
        }
        void add(String category, int amount) {
            int next = totals.getOrDefault(category, 0) + amount;
            if (next <= 0) totals.remove(category); else totals.put(category, next);
        }
        int get(String category) { return totals.getOrDefault(category, 0); }
    }
    private static int inclusive(Random rng, int min, int max) { return min >= max ? min : min + rng.nextInt(max - min + 1); }
    private static String category(Random rng, String[] categories) { return categories[rng.nextInt(categories.length)]; }
    private static int taggedValue(ItemStack stack, int base, double multiplier) {
        try { return (int)(base + Math.sqrt(ThaumcraftCraftingManager.getObjectTags(stack).visSize()) * multiplier); }
        catch (RuntimeException absent) { return base; }
    }
    private static Expected original(Kind kind, CompoundTag parameters, ResearchTableData before, ServerPlayer player, long seed) {
        var expected = new Expected(before, player.experienceLevel); var rng = new Random(seed);
        String[] categories = ResearchCategories.researchCategories.keySet().toArray(new String[0]);
        String cat = parameters.getString("cat");
        switch (kind) {
            case STUDY -> expected.add(cat, inclusive(rng, 15, 25));
            case ANALYZE -> { expected.add("BASICS", 5); expected.observationsSpent = 16; expected.add(cat, inclusive(rng, 25, 50)); }
            case BALANCE -> {
                int sum = 0, count = 0;
                for (var entry : expected.totals.entrySet()) if (!expected.blocked.contains(entry.getKey())) { sum += entry.getValue(); count++; }
                for (String key : expected.totals.keySet()) if (!expected.blocked.contains(key)) expected.totals.put(key, sum / count);
                expected.add("BASICS", 5); expected.penalty++;
            }
            case NOTATION -> {
                String lowest = parameters.getString("cat1"), highest = parameters.getString("cat2"); int amount = expected.get(lowest);
                expected.add(lowest, -amount); expected.add(highest, amount / 2 + inclusive(rng, 0, amount / 2));
            }
            case PONDER -> {
                int left = 25;
                while (left > 0) for (String key : expected.totals.keySet()) if (!expected.blocked.contains(key)) { expected.add(key, 1); if (--left == 0) break; }
                expected.add("BASICS", 5); expected.bonus++;
            }
            case RETHINK -> {
                int left = 10;
                while (left > 0) for (String key : List.copyOf(expected.totals.keySet())) {
                    expected.add(key, -1); if (--left == 0 || !expected.totals.containsKey(key)) break;
                }
                expected.bonus++; expected.add("BASICS", inclusive(rng, 1, 10));
            }
            case REJECT -> { expected.add("BASICS", 5); expected.blocked.add(cat); }
            case EXPERIMENTATION -> { expected.add(category(rng, categories), inclusive(rng, 15, 30)); expected.add("BASICS", inclusive(rng, 1, 10)); }
            case INSPIRED -> expected.add(cat, parameters.getInt("amt"));
            case BEACON -> { expected.bonus++; expected.penalty++; }
            case ENCHANTMENT -> { expected.levels -= 5; expected.add("INFUSION", inclusive(rng, 15, 20)); expected.add("AUROMANCY", inclusive(rng, 15, 20)); }
            case CELESTIAL -> {
                int first = parameters.getInt("md1"), second = parameters.getInt("md2"); expected.add(cat, inclusive(rng, 25, 50));
                if (first > 0 && first < 5 || second > 0 && second < 5) { expected.temporaryWarp = inclusive(rng, 0, 5); expected.add("ELDRITCH", expected.temporaryWarp * 2); }
                if (first == 0 || second == 0) expected.penalty++;
                if (first > 4 || second > 4) expected.bonus++;
            }
            case CURIO -> {
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), parameters.getCompound("stack"));
                String variant = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().substring("curio_".length());
                expected.add("BASICS", 5); expected.add(category(rng, categories), 5);
                if (variant.equals("rites")) { expected.add("ELDRITCH", inclusive(rng, 15, 20)); expected.add("AUROMANCY", inclusive(rng, 10, 15)); }
                else {
                    String key = switch (variant) {
                        case "arcane" -> "AUROMANCY"; case "preserved" -> "ALCHEMY"; case "ancient" -> "GOLEMANCY";
                        case "eldritch" -> "ELDRITCH"; case "knowledge" -> "INFUSION"; case "twisted" -> "ARTIFICE";
                        default -> throw new IllegalArgumentException(variant);
                    };
                    expected.add(key, inclusive(rng, 25, 35));
                }
                if (rng.nextBoolean()) expected.bonus++; if (rng.nextBoolean()) expected.bonus++;
            }
            case CONCENTRATE -> { expected.add("ALCHEMY", 15); expected.bonus++; if (rng.nextFloat() < .33) expected.inspiration = Math.min(before.inspirationStart, expected.inspiration + 1); }
            case REACTIONS -> { expected.add("ALCHEMY", 25); if (rng.nextFloat() < .33) expected.inspiration = Math.min(before.inspirationStart, expected.inspiration + 1); }
            case SYNTHESIS -> { expected.add("ALCHEMY", 40); if (rng.nextFloat() < .33) expected.inspiration = Math.min(before.inspirationStart, expected.inspiration + 1); expected.crystal = parameters.getString("aspect3"); }
            case CALIBRATE -> { expected.add("ARTIFICE", 15); expected.bonus++; }
            case MEASURE -> { expected.add("INFUSION", 15); expected.bonus++; }
            case FOCUS -> { expected.add("AUROMANCY", 15); expected.bonus++; }
            case MIND_OVER_MATTER -> expected.add("ARTIFICE", taggedValue(ItemStack.parseOptional(player.registryAccess(), parameters.getCompound("stack")), 10, 1));
            case INFUSE -> expected.add("INFUSION", taggedValue(ItemStack.parseOptional(player.registryAccess(), parameters.getCompound("stack")), 10, 1.5));
            case TINKER -> {
                int low = taggedValue(ItemStack.parseOptional(player.registryAccess(), parameters.getCompound("stack")), 0, 1) * 2;
                expected.add("ARTIFICE", inclusive(rng, low, low + 10));
            }
            case CHANNEL -> expected.add("INFUSION", 25);
            case AWARENESS -> { expected.add("AUROMANCY", 20); if (rng.nextFloat() < .33) { expected.add("ELDRITCH", inclusive(rng, 1, 5)); expected.normalWarp = 1; } }
            case SPELLBINDING -> { int spent = Math.min(5, expected.levels); expected.add("AUROMANCY", spent * 5); expected.levels -= spent; }
            case SCULPTING -> { expected.add("GOLEMANCY", 20); expected.bonus++; }
            case SCRIPTING -> { expected.extraInk = 1; expected.extraPaper = 1; expected.add("GOLEMANCY", 25); }
            case SYNERGY -> {
                int left = 15;
                while (left > 0) for (String key : new String[]{"ARTIFICE", "ALCHEMY", "INFUSION"}) if (expected.get(key) > 0) { expected.add(key, -1); if (--left == 0) break; }
                expected.add("GOLEMANCY", 30); expected.penalty++;
            }
            case DARK_WHISPERS -> {
                int levels = expected.levels; expected.levels = 0;
                if (levels > 0) for (String key : categories) if (!rng.nextBoolean()) expected.add(key, inclusive(rng, 0, Math.max(1, (int)Math.sqrt(levels))));
                expected.add("ELDRITCH", inclusive(rng, Math.max(1, levels / 5), Math.max(5, levels / 2)));
                expected.normalWarp = Math.max(1, (int)Math.sqrt(levels)); if (rng.nextBoolean()) expected.bonus++;
            }
            case GLYPHS -> { expected.add(category(rng, categories), inclusive(rng, 10, 20)); expected.add("ELDRITCH", inclusive(rng, 10, 20)); expected.temporaryWarp = 5; }
            case PORTAL -> {
                expected.add(category(rng, categories), inclusive(rng, 5, 10)); expected.add(category(rng, categories), inclusive(rng, 5, 10));
                expected.add("ELDRITCH", inclusive(rng, 5, 10)); expected.bonus += 2; expected.temporaryWarp = 5; expected.normalWarp = 1;
            }
            case REVELATION -> { expected.add(category(rng, categories), inclusive(rng, 5, 10)); expected.add("ELDRITCH", 30); expected.temporaryWarp = 5; expected.normalWarp = 1; expected.penalty++; }
            case REALIZATION -> {
                expected.add(category(rng, categories), inclusive(rng, 5, 10)); expected.add(category(rng, categories), inclusive(rng, 5, 10));
                expected.add("ELDRITCH", 15); expected.temporaryWarp = 5; if (rng.nextBoolean()) expected.normalWarp = 1;
            }
            case TRUTH, DRAGON_EGG -> throw new IllegalArgumentException("Not registered in source " + kind);
        }
        expected.nextRandomLong = rng.nextLong(); return expected;
    }
    @GameTest(template = "scan_test", timeoutTicks = 600)
    public static void everyRegisteredCardMatchesIndependentSourceStateRngCostsAndWarpOracle(GameTestHelper helper) throws Exception {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Card_Oracle"));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3)); helper.getLevel().setBlock(pos, BlocksTC.researchTable.defaultBlockState(), Block.UPDATE_ALL);
        var table = (TileResearchTable)helper.getLevel().getBlockEntity(pos); var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        knowledge.addResearch("CELESTIALSCANNING"); knowledge.setResearchStage("CELESTIALSCANNING", 99);
        for (var category : ResearchCategories.researchCategories.values()) if (category.researchKey != null) { knowledge.addResearch(category.researchKey); knowledge.setResearchStage(category.researchKey, 99); }
        String[] curios = {"arcane", "preserved", "ancient", "eldritch", "knowledge", "twisted", "rites"}; int checked = 0;
        try (var capture = PlayerDataSync.captureGameTestPackets((owner, packet) -> {})) {
            for (int trial = 0; trial < 14; trial++) for (Kind kind : Kind.values()) {
                if (!TheorycraftManager.cards.containsKey(kind.sourceKey())) continue;
                player.getInventory().clearContent(); player.experienceLevel = trial % 2 == 0 ? 20 : 5;
                player.getInventory().setItem(9, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:curio_" + curios[trial % 7]))));
                ThaumcraftCapabilities.getWarp(player).clear();
                for (var category : ResearchCategories.researchCategories.values()) knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 160);
                knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, null, 160);
                table.setItem(0, new ItemStack(ItemsTC.scribingTools)); table.setItem(1, new ItemStack(Items.PAPER, 64));
                var data = new ResearchTableData(player, table); data.inspirationStart = 15; data.inspiration = trial % 2 == 0 ? 14 : 15;
                int amount = 17; for (String key : ResearchCategories.researchCategories.keySet()) { data.addTotal(key, amount); amount += 17; }
                if (trial % 2 == 1) data.categoriesBlocked.add("ALCHEMY");
                SourceTheorycraftCard card = null;
                for (long seed = 1 + trial * 8192L; seed < 4096 + trial * 8192L; seed++) {
                    var candidate = new SourceTheorycraftCard(kind); candidate.bindRegistries(player.registryAccess()); candidate.setSeed(seed);
                    if (candidate.initialize(player, data)) { card = candidate; break; }
                }
                helper.assertTrue(card != null, "genuine initialized card " + kind);
                var parameters = card.serialize(); long rngSeed = 54735L + trial * 37L + kind.ordinal();
                Expected expected = original(kind, parameters, data, player, rngSeed);
                var category = ResearchCategories.getResearchCategory(parameters.getString("cat")); int observationBefore = knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category);
                player.getRandom().setSeed(rngSeed); helper.assertTrue(card.activate(player, data), "source activation succeeds " + kind);
                String context = kind + " trial " + trial;
                helper.assertTrue(data.categoryTotals.equals(expected.totals), "literal original totals " + context + " expected=" + expected.totals + " actual=" + data.categoryTotals);
                helper.assertTrue(data.categoriesBlocked.equals(expected.blocked) && data.bonusDraws == expected.bonus && data.penaltyStart == expected.penalty && data.inspiration == expected.inspiration, "source blocked/bonus/penalty/inspiration " + context);
                helper.assertTrue(player.experienceLevel == expected.levels, "source real XP level payment " + context);
                helper.assertTrue(ThaumcraftCapabilities.getWarp(player).get(EnumWarpType.NORMAL) == expected.normalWarp && ThaumcraftCapabilities.getWarp(player).get(EnumWarpType.TEMPORARY) == expected.temporaryWarp, "source real attachment warp " + context);
                helper.assertTrue(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == observationBefore - expected.observationsSpent, "source Analyze actual observation payment " + context);
                helper.assertTrue(table.getItem(0).getDamageValue() == expected.extraInk && table.getItem(1).getCount() == 64 - expected.extraPaper, "source Scripting EXTRA ink/paper " + context);
                if (expected.crystal != null) {
                    int crystals = 0; Aspect aspect = Aspect.getAspect(expected.crystal);
                    for (ItemStack item : player.getInventory().items) if (item.is(ItemsTC.crystalEssence) && item.get(TCDataComponents.ASPECTS).getAmount(aspect) == 1) crystals += item.getCount();
                    helper.assertTrue(crystals == 1, "source Synthesis returns one genuine composite crystal " + context);
                }
                helper.assertTrue(player.getRandom().nextLong() == expected.nextRandomLong, "independent post-effect RNG witness " + context);
                checked++;
            }
            helper.assertTrue(checked == 33 * 14, "all33 cards, fourteen independent seeded cases, all7curio variants");
        } finally { PlayerDataSync.forget(player); }
        helper.succeed();
    }
}
