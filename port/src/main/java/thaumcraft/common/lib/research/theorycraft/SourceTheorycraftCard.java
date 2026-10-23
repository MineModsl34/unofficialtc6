package thaumcraft.common.lib.research.theorycraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.research.ResearchManager;

/** All built-in TC6 card formulae. Source class keys remain stable in saved notes and aid pools.
 * The shared implementation replaces repeated translation/import glue, not the per-card rules.
 * Source Analyze's null-category observation lookup and Ponder's unusual return are intentional. */
public final class SourceTheorycraftCard extends TheorycraftCard {
    public enum Kind {
        STUDY("CardStudy", "study", "api"), ANALYZE("CardAnalyze", "analyze", "api"),
        BALANCE("CardBalance", "balance", "api"), NOTATION("CardNotation", "notation", "api"),
        PONDER("CardPonder", "ponder", "api"), RETHINK("CardRethink", "rethink", "api"),
        REJECT("CardReject", "reject", "api"), EXPERIMENTATION("CardExperimentation", "experimentation", "api"),
        CURIO("CardCurio", "curio"), INSPIRED("CardInspired", "inspired", "api"),
        ENCHANTMENT("CardEnchantment", "enchantment"), BEACON("CardBeacon", "beacon"),
        CELESTIAL("CardCelestial", "celestial"), CONCENTRATE("CardConcentrate", "concentrate"),
        REACTIONS("CardReactions", "reactions"), SYNTHESIS("CardSynthesis", "synthesis"),
        CALIBRATE("CardCalibrate", "calibrate"), MIND_OVER_MATTER("CardMindOverMatter", "mindmatter"),
        TINKER("CardTinker", "tinker"), MEASURE("CardMeasure", "measure"), CHANNEL("CardChannel", "channel"),
        INFUSE("CardInfuse", "infuse"), FOCUS("CardFocus", "focus"), AWARENESS("CardAwareness", "awareness"),
        SPELLBINDING("CardSpellbinding", "spellbinding"), SCULPTING("CardSculpting", "sculpting"),
        SCRIPTING("CardScripting", "scripting"), SYNERGY("CardSynergy", "synergy"),
        DARK_WHISPERS("CardDarkWhispers", "darkwhisper"), GLYPHS("CardGlyphs", "glyph"), PORTAL("CardPortal", "portal"),
        REVELATION("CardRevelation", "revelation"), REALIZATION("CardRealization", "realization"),
        TRUTH("CardTruth", "truth"), DRAGON_EGG("CardDragonEgg", "dragonegg");
        private final String className, translation, location;
        Kind(String className, String translation) { this(className, translation, "common"); }
        Kind(String className, String translation, String location) { this.className = className; this.translation = translation; this.location = location; }
        public String sourceKey() { return "thaumcraft." + (location.equals("api") ? "api.research.theorycraft." : "common.lib.research.theorycraft.") + className; }
    }
    private static final String[] CURIO_VARIANTS = {"arcane", "preserved", "ancient", "eldritch", "knowledge", "twisted", "rites"};
    private static final String[] ARTIFICE_OPTIONS = {
        "thaumcraft:vis_resonator", "thaumcraft:thaumometer", "minecraft:anvil", "minecraft:activator_rail", "minecraft:dispenser",
        "minecraft:dropper", "minecraft:enchanting_table", "minecraft:ender_chest", "minecraft:jukebox", "minecraft:daylight_detector",
        "minecraft:piston", "minecraft:hopper", "minecraft:sticky_piston", "minecraft:filled_map", "minecraft:compass", "minecraft:tnt_minecart",
        "minecraft:comparator", "minecraft:clock"};
    private static final String[] INFUSION_OPTIONS = {
        "thaumcraft:alumentum", "thaumcraft:nitor_yellow", "thaumcraft:amber", "thaumcraft:brain", "thaumcraft:fabric", "thaumcraft:salis_mundus",
        "thaumcraft:ingot_thaumium", "thaumcraft:ingot_brass", "thaumcraft:quicksilver", "minecraft:gold_ingot", "minecraft:iron_ingot",
        "minecraft:diamond", "minecraft:emerald", "minecraft:blaze_rod", "minecraft:leather", "minecraft:white_wool", "minecraft:brick",
        "minecraft:arrow", "minecraft:egg", "minecraft:feather", "minecraft:glowstone_dust", "minecraft:redstone", "minecraft:ghast_tear",
        "minecraft:gunpowder", "minecraft:bow", "minecraft:golden_sword", "minecraft:iron_sword", "minecraft:iron_pickaxe",
        "minecraft:golden_pickaxe", "minecraft:quartz", "minecraft:apple"};
    private final Kind kind;
    private String cat, cat1, cat2;
    private int amount, md1, md2;
    private Aspect aspect, aspect1, aspect2, aspect3;
    private ItemStack stack = ItemStack.EMPTY;
    public SourceTheorycraftCard(Kind kind) { this.kind = kind; if (kind == Kind.STUDY || kind == Kind.CELESTIAL) cat = "BASICS"; }
    public Kind kind() { return kind; }
    @Override public int getInspirationCost() {
        return switch (kind) {
            case ANALYZE, EXPERIMENTATION, INSPIRED, PONDER -> 2;
            case REJECT -> 0;
            case RETHINK, PORTAL -> -1;
            case BEACON -> -2;
            default -> 1;
        };
    }
    @Override public boolean isAidOnly() {
        return switch (kind) { case STUDY, NOTATION, ENCHANTMENT, BEACON, DARK_WHISPERS, GLYPHS, PORTAL, DRAGON_EGG -> true; default -> false; };
    }
    @Override public String getResearchCategory() {
        return switch (kind) {
            case STUDY, ANALYZE, INSPIRED, CELESTIAL -> cat;
            case CONCENTRATE, REACTIONS, SYNTHESIS -> "ALCHEMY";
            case CALIBRATE, TINKER, MIND_OVER_MATTER -> "ARTIFICE";
            case MEASURE, CHANNEL, INFUSE -> "INFUSION";
            case FOCUS, AWARENESS, SPELLBINDING -> "AUROMANCY";
            case SCULPTING, SCRIPTING, SYNERGY -> "GOLEMANCY";
            case DARK_WHISPERS, GLYPHS, PORTAL, REVELATION, REALIZATION, TRUTH -> "ELDRITCH";
            default -> null;
        };
    }
    private static Component category(String value) { return Component.translatable("tc.research_category." + value).withStyle(ChatFormatting.BOLD); }
    private static String aspectName(Aspect value) { return value == null ? "?" : value.getName(); }
    @Override public Component getLocalizedName() {
        Object[] parameters = switch (kind) {
            case STUDY, ANALYZE -> new Object[]{category(cat).copy().withStyle(ChatFormatting.DARK_BLUE)};
            case REJECT -> new Object[]{category(cat1).copy().withStyle(ChatFormatting.DARK_BLUE)};
            case CHANNEL -> new Object[]{Component.literal(aspectName(aspect)).withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD)};
            default -> new Object[0];
        };
        return Component.translatable("card." + kind.translation + ".name", parameters);
    }
    @Override public Component getLocalizedText() {
        Object[] parameters = switch (kind) {
            case STUDY, CELESTIAL -> new Object[]{category(cat)};
            case ANALYZE -> new Object[]{category(cat), category("BASICS")};
            case INSPIRED -> new Object[]{amount, category(cat)};
            case NOTATION -> new Object[]{category(cat1), category(cat2)};
            case REJECT -> new Object[]{category(cat1)};
            case CONCENTRATE, CHANNEL -> new Object[]{aspectName(aspect)};
            case REACTIONS, SYNTHESIS -> new Object[]{aspectName(aspect1), aspectName(aspect2)};
            case INFUSE -> new Object[]{aspectName(aspect), stack.getHoverName(), value()};
            case MIND_OVER_MATTER -> new Object[]{value()};
            case TINKER -> new Object[]{value() * 2, value() * 2 + 10};
            default -> new Object[0];
        };
        return Component.translatable("card." + kind.translation + ".text", parameters);
    }
    @Override public boolean initialize(Player player, ResearchTableData data) {
        Random random = new Random(getSeed());
        switch (kind) {
            case STUDY: {
                ArrayList<String> categories = data.getAvailableCategories(player);
                cat = categories.get(random.nextInt(categories.size())); return cat != null;
            }
            case ANALYZE: {
                ArrayList<String> categories = new ArrayList<>();
                for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
                    // Preserve original reference comparison AND the uninitialized cat lookup.
                    if (category.key != "BASICS" && ThaumcraftCapabilities.getKnowledge(player).getKnowledge(EnumKnowledgeType.OBSERVATION,
                        ResearchCategories.researchCategories.get(cat)) > 0) categories.add(category.key);
                }
                if (!categories.isEmpty()) cat = categories.get(random.nextInt(categories.size())); return cat != null;
            }
            case BALANCE: return canBalance(data);
            case PONDER: return data.categoriesBlocked.size() < data.categoryTotals.size();
            case RETHINK: return sum(data) >= 10;
            case REJECT: {
                ArrayList<String> categories = new ArrayList<>();
                for (String category : data.categoryTotals.keySet()) if (!data.categoriesBlocked.contains(category)) categories.add(category);
                if (categories.isEmpty()) return false;
                cat1 = categories.get(random.nextInt(categories.size())); return cat1 != null;
            }
            case INSPIRED: {
                if (data.categoryTotals.isEmpty()) return false;
                cat = highest(data); amount = 10 + data.getTotal(cat) / 2; return true;
            }
            case NOTATION: {
                if (data.categoryTotals.size() < 2) return false;
                int lowest = Integer.MAX_VALUE; cat1 = ""; cat2 = highest(data);
                for (String category : data.categoryTotals.keySet()) if (data.getTotal(category) < lowest) { lowest = data.getTotal(category); cat1 = category; }
                return !cat2.equals(cat1) && lowest > 0;
            }
            case CELESTIAL: {
                if (data.categoryTotals.isEmpty() || !ThaumcraftCapabilities.knowsResearch(player, "CELESTIALSCANNING")) return false;
                md1 = random.nextInt(13); md2 = md1;
                while (md1 == md2) md2 = random.nextInt(13);
                cat = highest(data); return cat != null;
            }
            case CURIO: {
                ArrayList<ItemStack> curios = new ArrayList<>();
                for (ItemStack item : player.getInventory().items) if (curioType(item) != null) {
                    ItemStack copy = item.copy(); copy.setCount(1);
                    // Arbitrary custom component data on a real curio must not make the
                    // bounded owner-note transport fail during a draw. Reject, never strip it.
                    if (copy.saveOptional(registries).sizeInBytes() <= 16384) curios.add(copy);
                }
                if (!curios.isEmpty()) stack = curios.get(random.nextInt(curios.size())); return !stack.isEmpty();
            }
            case CONCENTRATE, CHANNEL: aspect = Aspect.getCompoundAspects().get(random.nextInt(Aspect.getCompoundAspects().size())); return true;
            case REACTIONS: {
                int first = random.nextInt(Aspect.getCompoundAspects().size()), second = first;
                while (first == second) second = random.nextInt(Aspect.getCompoundAspects().size());
                aspect1 = Aspect.getCompoundAspects().get(first); aspect2 = Aspect.getCompoundAspects().get(second); return true;
            }
            case SYNTHESIS: {
                aspect3 = Aspect.getCompoundAspects().get(random.nextInt(Aspect.getCompoundAspects().size()));
                aspect1 = aspect3.getComponents()[0]; aspect2 = aspect3.getComponents()[1]; return true;
            }
            case INFUSE: {
                aspect = Aspect.getCompoundAspects().get(random.nextInt(Aspect.getCompoundAspects().size()));
                stack = item(INFUSION_OPTIONS[random.nextInt(INFUSION_OPTIONS.length)]); return !stack.isEmpty();
            }
            case TINKER, MIND_OVER_MATTER: stack = item(ARTIFICE_OPTIONS[random.nextInt(ARTIFICE_OPTIONS.length)]); return !stack.isEmpty();
            case SPELLBINDING: return player.experienceLevel > 0;
            case SYNERGY: return data.getTotal("ARTIFICE") + data.getTotal("ALCHEMY") + data.getTotal("INFUSION") >= 15;
            default: return true;
        }
    }
    private static ItemStack item(String key) {
        ResourceLocation id = ResourceLocation.parse(key);
        return BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }
    public static String curioType(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String variant : CURIO_VARIANTS) if (key.equals("thaumcraft:curio_" + variant)) return variant;
        return null;
    }
    private static String highest(ResearchTableData data) {
        int total = 0; String key = "";
        for (String category : data.categoryTotals.keySet()) if (data.getTotal(category) > total) { total = data.getTotal(category); key = category; }
        return key;
    }
    private static int sum(ResearchTableData data) { int total = 0; for (int value : data.categoryTotals.values()) total += value; return total; }
    private static boolean canBalance(ResearchTableData data) {
        int total = 0, size = 0;
        for (String category : data.categoryTotals.keySet()) if (!data.categoriesBlocked.contains(category)) { total += data.getTotal(category); size++; }
        return data.categoriesBlocked.size() < data.categoryTotals.size() - 1 && total >= size;
    }
    private int value() {
        int result = kind == Kind.TINKER ? 0 : 10;
        try { result = (int)(result + Math.sqrt(ThaumcraftCraftingManager.getObjectTags(stack).visSize()) * (kind == Kind.INFUSE ? 1.5 : 1)); }
        catch (RuntimeException ignored) { /* Source retains its constant when aspect tags are absent. */ }
        return result;
    }
    @Override public ItemStack[] getRequiredItems() {
        return switch (kind) {
            case CURIO, TINKER, MIND_OVER_MATTER -> new ItemStack[]{stack};
            case CELESTIAL -> new ItemStack[]{note(md1), note(md2)};
            case CONCENTRATE -> new ItemStack[]{crystal(aspect)};
            case REACTIONS, SYNTHESIS -> new ItemStack[]{crystal(aspect1), crystal(aspect2)};
            case CHANNEL -> new ItemStack[]{ItemPhial.makeFilledPhial(aspect)};
            case INFUSE -> new ItemStack[]{stack, ItemPhial.makeFilledPhial(aspect)};
            case SCULPTING -> new ItemStack[]{new ItemStack(Items.CLAY_BALL)};
            default -> null;
        };
    }
    private static ItemStack note(int meta) { return meta >= 0 && meta < ItemsTC.celestialNotesVariants.length && ItemsTC.celestialNotesVariants[meta] != null ? new ItemStack(ItemsTC.celestialNotesVariants[meta]) : ItemStack.EMPTY; }
    private static ItemStack crystal(Aspect aspect) { return aspect == null || ItemsTC.crystalEssence == null ? ItemStack.EMPTY : ThaumcraftApiHelper.makeCrystal(aspect); }
    @Override public boolean[] getRequiredItemsConsumed() {
        ItemStack[] items = getRequiredItems();
        if (items == null) return null;
        boolean[] result = new boolean[items.length];
        if (kind == Kind.CURIO || kind == Kind.CELESTIAL || kind == Kind.SYNTHESIS || kind == Kind.MIND_OVER_MATTER || kind == Kind.INFUSE || kind == Kind.SCULPTING) Arrays.fill(result, true);
        return result;
    }
    private static int integer(RandomSource random, int minimum, int maximum) { return minimum >= maximum ? minimum : random.nextInt(maximum - minimum + 1) + minimum; }
    private static String randomCategory(Player player) { String[] keys = ResearchCategories.researchCategories.keySet().toArray(new String[0]); return keys[player.getRandom().nextInt(keys.length)]; }
    private static void warp(Player player, int amount, EnumWarpType type) { ResearchManager.addWarpToPlayer(player, amount, type); }
    @Override public boolean activate(Player player, ResearchTableData data) {
        RandomSource random = player.getRandom();
        switch (kind) {
            case STUDY: data.addTotal(cat, integer(random, 15, 25)); break;
            case ANALYZE: {
                ResearchCategory category = ResearchCategories.getResearchCategory(cat);
                var knowledge = ThaumcraftCapabilities.getKnowledge(player);
                if (knowledge.getKnowledge(EnumKnowledgeType.OBSERVATION, category) < 1) return false;
                data.addTotal("BASICS", 5); knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, category, -EnumKnowledgeType.OBSERVATION.getProgression());
                data.addTotal(cat, integer(random, 25, 50)); break;
            }
            case BALANCE: {
                if (!canBalance(data)) return false;
                int total = 0, size = 0;
                for (String category : data.categoryTotals.keySet()) if (!data.categoriesBlocked.contains(category)) { total += data.getTotal(category); size++; }
                for (String category : data.categoryTotals.keySet()) if (!data.categoriesBlocked.contains(category)) data.categoryTotals.put(category, total / size);
                data.addTotal("BASICS", 5); data.penaltyStart++; break;
            }
            case NOTATION: {
                if (cat1 == null || cat2 == null) return false;
                int lowest = data.getTotal(cat1); data.addTotal(cat1, -lowest); data.addTotal(cat2, lowest / 2 + integer(random, 0, lowest / 2)); break;
            }
            case PONDER: {
                int remaining = 25, tries = 0;
                while (remaining > 0 && tries++ < 1000) for (String category : data.categoryTotals.keySet()) {
                    if (data.categoriesBlocked.contains(category)) { if (data.categoryTotals.size() <= 1) return false; }
                    else { data.addTotal(category, 1); if (--remaining <= 0) break; }
                }
                data.addTotal("BASICS", 5); data.bonusDraws++; return remaining != 20;
            }
            case RETHINK: {
                if (sum(data) < 10) return false;
                int remaining = Math.min(sum(data), 10), tries = 0;
                while (remaining > 0 && tries++ < 1000) for (String category : data.categoryTotals.keySet()) {
                    data.addTotal(category, -1); if (--remaining <= 0 || !data.hasTotal(category)) break;
                }
                data.bonusDraws++; data.addTotal("BASICS", integer(random, 1, 10)); break;
            }
            case REJECT: if (cat1 == null) return false; data.addTotal("BASICS", 5); data.categoriesBlocked.add(cat1); break;
            case EXPERIMENTATION: data.addTotal(randomCategory(player), integer(random, 15, 30)); data.addTotal("BASICS", integer(random, 1, 10)); break;
            case INSPIRED: data.addTotal(cat, amount); break;
            case BEACON: data.bonusDraws++; data.penaltyStart++; break;
            case ENCHANTMENT:
                if (player.experienceLevel < 5) return false;
                player.giveExperienceLevels(-5); data.addTotal("INFUSION", integer(random, 15, 20)); data.addTotal("AUROMANCY", integer(random, 15, 20)); break;
            case CELESTIAL: {
                data.addTotal(cat, integer(random, 25, 50));
                if (md1 > 0 && md1 < 5 || md2 > 0 && md2 < 5) { int amount = integer(random, 0, 5); data.addTotal("ELDRITCH", amount * 2); warp(player, amount, EnumWarpType.TEMPORARY); }
                if (md1 == 0 || md2 == 0) data.penaltyStart++;
                if (md1 > 4 || md2 > 4) data.bonusDraws++; break;
            }
            case CURIO: {
                String variant = curioType(stack); if (variant == null) return false;
                data.addTotal("BASICS", 5); data.addTotal(randomCategory(player), 5);
                String category = switch (variant) { case "arcane" -> "AUROMANCY"; case "preserved" -> "ALCHEMY"; case "ancient" -> "GOLEMANCY"; case "eldritch" -> "ELDRITCH"; case "knowledge" -> "INFUSION"; case "twisted" -> "ARTIFICE"; default -> "BASICS"; };
                if (variant.equals("rites")) { data.addTotal("ELDRITCH", integer(random, 15, 20)); data.addTotal("AUROMANCY", integer(random, 10, 15)); }
                else data.addTotal(category, integer(random, 25, 35));
                if (random.nextBoolean()) data.bonusDraws++; if (random.nextBoolean()) data.bonusDraws++; break;
            }
            case CONCENTRATE: data.addTotal("ALCHEMY", 15); data.bonusDraws++; if (random.nextFloat() < .33) data.addInspiration(1); break;
            case REACTIONS: data.addTotal("ALCHEMY", 25); if (random.nextFloat() < .33) data.addInspiration(1); break;
            case SYNTHESIS: {
                data.addTotal("ALCHEMY", 40); if (random.nextFloat() < .33) data.addInspiration(1);
                ItemStack result = crystal(aspect3); if (result.isEmpty()) return false;
                if (!player.getInventory().add(result)) player.drop(result, true); break;
            }
            case CALIBRATE, MEASURE, FOCUS: data.addTotal(getResearchCategory(), 15); data.bonusDraws++; break;
            case MIND_OVER_MATTER, INFUSE: data.addTotal(getResearchCategory(), value()); break;
            case TINKER: { int value = value() * 2; data.addTotal("ARTIFICE", integer(random, value, value + 10)); break; }
            case CHANNEL: data.addTotal("INFUSION", 25); break;
            case AWARENESS:
                data.addTotal("AUROMANCY", 20);
                if (random.nextFloat() < .33) { data.addTotal("ELDRITCH", integer(random, 1, 5)); warp(player, 1, EnumWarpType.NORMAL); } break;
            case SPELLBINDING: {
                if (player.experienceLevel <= 0) return false;
                int levels = Math.min(5, player.experienceLevel); data.addTotal("AUROMANCY", levels * 5); player.giveExperienceLevels(-levels); break;
            }
            case SCULPTING: data.addTotal("GOLEMANCY", 20); data.bonusDraws++; break;
            case SCRIPTING:
                if (data.table == null || data.table.getItem(0).getDamageValue() >= data.table.getItem(0).getMaxDamage()) return false;
                data.table.consumeInkFromTable(); data.table.consumepaperFromTable(); data.addTotal("GOLEMANCY", 25); break;
            case SYNERGY: {
                if (data.getTotal("ARTIFICE") + data.getTotal("ALCHEMY") + data.getTotal("INFUSION") < 15) return false;
                int remaining = 15, tries = 0;
                while (remaining > 0 && tries++ < 1000) for (String category : new String[]{"ARTIFICE", "ALCHEMY", "INFUSION"}) {
                    if (data.getTotal(category) > 0) { data.addTotal(category, -1); if (--remaining <= 0) break; }
                }
                data.addTotal("GOLEMANCY", 30); data.penaltyStart++; break;
            }
            case DARK_WHISPERS: {
                int levels = player.experienceLevel; player.giveExperienceLevels(-(10 + levels));
                if (levels > 0) for (String category : ResearchCategories.researchCategories.keySet()) if (!random.nextBoolean()) data.addTotal(category, integer(random, 0, Math.max(1, (int)Math.sqrt(levels))));
                data.addTotal("ELDRITCH", integer(random, Math.max(1, levels / 5), Math.max(5, levels / 2)));
                warp(player, Math.max(1, (int)Math.sqrt(levels)), EnumWarpType.NORMAL); if (random.nextBoolean()) data.bonusDraws++; break;
            }
            case GLYPHS: data.addTotal(randomCategory(player), integer(random, 10, 20)); data.addTotal("ELDRITCH", integer(random, 10, 20)); warp(player, 5, EnumWarpType.TEMPORARY); break;
            case PORTAL:
                data.addTotal(randomCategory(player), integer(random, 5, 10)); data.addTotal(randomCategory(player), integer(random, 5, 10));
                data.addTotal("ELDRITCH", integer(random, 5, 10)); data.bonusDraws += 2; warp(player, 5, EnumWarpType.TEMPORARY); warp(player, 1, EnumWarpType.NORMAL); break;
            case REVELATION:
                data.addTotal(randomCategory(player), integer(random, 5, 10)); data.addTotal("ELDRITCH", 30);
                warp(player, 5, EnumWarpType.TEMPORARY); warp(player, 1, EnumWarpType.NORMAL); data.penaltyStart++; break;
            case REALIZATION:
                data.addTotal(randomCategory(player), integer(random, 5, 10)); data.addTotal(randomCategory(player), integer(random, 5, 10)); data.addTotal("ELDRITCH", 15);
                warp(player, 5, EnumWarpType.TEMPORARY); if (random.nextBoolean()) warp(player, 1, EnumWarpType.NORMAL); break;
            case TRUTH: data.addTotal("ELDRITCH", integer(random, 10, 25)); data.bonusDraws++; warp(player, 3, EnumWarpType.TEMPORARY); break;
            case DRAGON_EGG: for (int i = 0; i < 10; i++) data.addTotal(randomCategory(player), integer(random, 2, 5)); break;
        }
        return true;
    }
    @Override public CompoundTag serialize() {
        CompoundTag result = super.serialize();
        if (cat != null) result.putString("cat", cat);
        if (cat1 != null) result.putString(kind == Kind.REJECT ? "cat" : "cat1", cat1);
        if (cat2 != null) result.putString("cat2", cat2);
        if (kind == Kind.INSPIRED) result.putInt("amt", amount);
        if (kind == Kind.CELESTIAL) { result.putInt("md1", md1); result.putInt("md2", md2); }
        if (aspect != null) result.putString("aspect", aspect.getTag());
        if (aspect1 != null) result.putString("aspect1", aspect1.getTag());
        if (aspect2 != null) result.putString("aspect2", aspect2.getTag());
        if (aspect3 != null) result.putString("aspect3", aspect3.getTag());
        if (kind == Kind.CURIO || kind == Kind.INFUSE || kind == Kind.TINKER || kind == Kind.MIND_OVER_MATTER) result.put("stack", stack.saveOptional(registries));
        return result;
    }
    @Override public void deserialize(CompoundTag tag) {
        super.deserialize(tag);
        if (tag.contains("cat", 8)) cat = tag.getString("cat");
        String firstKey = kind == Kind.REJECT ? "cat" : "cat1";
        cat1 = tag.contains(firstKey, 8) ? tag.getString(firstKey) : null; cat2 = tag.contains("cat2", 8) ? tag.getString("cat2") : null;
        amount = tag.getInt("amt"); md1 = tag.getInt("md1"); md2 = tag.getInt("md2");
        aspect = Aspect.getAspect(tag.getString("aspect")); aspect1 = Aspect.getAspect(tag.getString("aspect1"));
        aspect2 = Aspect.getAspect(tag.getString("aspect2")); aspect3 = Aspect.getAspect(tag.getString("aspect3"));
        if (tag.contains("stack", 10)) stack = ItemStack.parseOptional(registries, tag.getCompound("stack"));
    }
}
