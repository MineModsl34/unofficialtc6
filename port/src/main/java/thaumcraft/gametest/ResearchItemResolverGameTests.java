package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;
import thaumcraft.common.lib.research.theorycraft.TheorycraftInventory;

/** Live server registries/flattened identities and contextual source token resolution.
 * Private prepared entries prove transaction/reward semantics, not survival acquisition or TCP. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchItemResolverGameTests {
    private ResearchItemResolverGameTests() {}
    @GameTest(template="empty") public static void ordinaryPaperWildcardAndFlattenedFamilyMetadataStayDistinct(GameTestHelper helper) {
        var registries=helper.getLevel().registryAccess();
        var paper=ResearchItemResolver.parse("minecraft:paper;3;32767");
        ItemStack resolved=ResearchItemResolver.resolve(paper,registries);
        helper.assertTrue(resolved.is(Items.PAPER)&&resolved.getCount()==3&&LegacyScanIdentity.rawDamage(resolved)==0,"ordinary32767 wildcard resolves real ordinary paper, not unknown damage metadata");
        helper.assertTrue(ResearchItemResolver.matches(paper,new ItemStack(Items.PAPER),registries),"match ignores quantity; server payment separately checks aggregate count");
        helper.assertFalse(ResearchItemResolver.matches(paper,new ItemStack(Items.MAP),registries),"wildcard does not match unrelated items");
        var red=ResearchItemResolver.parse("minecraft:wool;2;14");
        helper.assertTrue(ResearchItemResolver.resolve(red,registries).is(Items.RED_WOOL),"source wool metadata14 is flattened red wool");
        helper.assertTrue(ResearchItemResolver.matches(red,new ItemStack(Items.RED_WOOL),registries),"source family metadata is preserved");
        helper.assertFalse(ResearchItemResolver.matches(red,new ItemStack(Items.WHITE_WOOL),registries),"ordinary obtain is not card OreDictionary equivalence");
        helper.assertTrue(ResearchItemResolver.alternatives(ResearchItemResolver.parse("minecraft:wool;1;32767"),registries).size()==16,"source flattened wildcard enumerates all sixteen actual colors");
        helper.assertTrue(ResearchItemResolver.resolve(ResearchItemResolver.parse("minecraft:wool;1;99"),registries).isEmpty(),"unsupported family metadata never falls back to modern damage-zero base");helper.succeed();
    }
    @GameTest(template="empty") public static void craftReferencesUseLiteralSourceTranslationKeysIgnoreCountAndNbtButKeepWear(GameTestHelper helper) {
        ItemStack paper=new ItemStack(Items.PAPER,42);paper.set(DataComponents.CUSTOM_NAME,Component.literal("not identity"));
        CompoundTag note=new CompoundTag();note.putString("test","ignored");paper.set(DataComponents.CUSTOM_DATA,CustomData.of(note));
        helper.assertTrue(ResearchItemResolver.craftReference(paper)=="1xitem.paper@0".hashCode(),"literal1xitem.paper@0 source hash, no modern ItemStack.toString");
        ItemStack red=new ItemStack(Items.RED_WOOL,64);red.set(DataComponents.CUSTOM_NAME,Component.literal("red"));
        helper.assertTrue(ResearchItemResolver.craftReference(red)=="1xtile.cloth@14".hashCode(),"flattened red wool keeps old shared tile.cloth@14");
        helper.assertTrue(ResearchItemResolver.craftReference(ResearchItemResolver.parse("minecraft:wool;12;14"))=="1xtile.cloth@14".hashCode(),"catalog token and real crafting item share source hash");
        ItemStack sword=new ItemStack(Items.WOODEN_SWORD);sword.set(DataComponents.DAMAGE,7);
        helper.assertTrue(ResearchItemResolver.craftReference(sword)=="1xitem.swordWood@7".hashCode(),"durable wear participates in source craft reference");
        sword.setCount(8);sword.set(DataComponents.CUSTOM_NAME,Component.literal("worn"));
        helper.assertTrue(ResearchItemResolver.craftReference(sword)=="1xitem.swordWood@7".hashCode(),"count and NBT still ignored for worn durable item");
        sword.set(DataComponents.DAMAGE,8);helper.assertTrue(ResearchItemResolver.craftReference(sword)=="1xitem.swordWood@8".hashCode(),"distinct damage produces distinct source craft marker");helper.succeed();
    }
    @GameTest(template="empty") public static void nativeAlchemyBlockItemsAndObsoleteNitorDescriptorsUseActualBeta26Identities(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        for (String path : new String[]{"crucible", "research_table", "smelter_basic", "infusion_matrix"}) {
            var token = ResearchItemResolver.parse("thaumcraft:" + path);
            ItemStack actual = ResearchItemResolver.resolve(token, registries);
            helper.assertFalse(actual.isEmpty(), "actual registered alchemy requirement resolves: " + path);
            helper.assertTrue(Integer.valueOf(("1xtile." + path + "@0").hashCode()).equals(ResearchItemResolver.craftReference(actual)),
                "actual BlockTC translation-key hash, not an invented modern hash: " + path);
            helper.assertTrue(ResearchItemResolver.craftReference(token).equals(ResearchItemResolver.craftReference(actual)),
                "prepared requirement and real crafted BlockItem share the source receipt: " + path);
            actual.set(DataComponents.DAMAGE, 1);
            helper.assertTrue(ResearchItemResolver.craftReference(actual) == null,
                "unproved artificial metadata does not create a receipt for ordinary BlockItem: " + path);
        }
        String[] oldColors = {"white", "orange", "magenta", "lightblue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (int metadata = 0; metadata < oldColors.length; metadata++) {
            var token = ResearchItemResolver.parse("thaumcraft:nitor;3;" + metadata);
            ItemStack actual = ResearchItemResolver.resolve(token, registries);
            helper.assertTrue(actual.is(BlocksTC.nitor.get(DyeColor.byId(metadata)).asItem()) && actual.getCount() == 3,
                "obsolete family metadata selects the real registered dye BlockItem: " + metadata);
            var identity = LegacyScanIdentity.identify(actual);
            helper.assertTrue(identity.sourceProven() && identity.metadata() == 0
                && identity.itemId().equals(ResourceLocation.parse("thaumcraft:nitor_" + oldColors[metadata])),
                "actual scan identity keeps original separate color registration including silver/lightblue: " + metadata);
            int expected = ("1xtile.nitor_" + oldColors[metadata] + "@0").hashCode();
            helper.assertTrue(Integer.valueOf(expected).equals(ResearchItemResolver.craftReference(actual))
                && Integer.valueOf(expected).equals(ResearchItemResolver.craftReference(token)),
                "obsolete descriptor and actual output share original individual color hash: " + metadata);
            helper.assertTrue(ResearchItemResolver.matches(token, actual, registries)
                && !ResearchItemResolver.matches(token, new ItemStack(BlocksTC.nitor.get(DyeColor.byId((metadata + 1) % 16))), registries),
                "specific obsolete nitor requirement rejects another color");
        }
        var wildcard = ResearchItemResolver.parse("thaumcraft:nitor;1;32767");
        helper.assertTrue(ResearchItemResolver.alternatives(wildcard, registries).size() == 16,
            "explicit old wildcard enumerates all actual sixteen color blocks");
        for (DyeColor color : DyeColor.values()) helper.assertTrue(ResearchItemResolver.matches(wildcard, new ItemStack(BlocksTC.nitor.get(color)), registries),
            "explicit wildcard accepts each registered nitor color");
        for (int invalid : new int[]{16, 99, Integer.MAX_VALUE}) {
            var token = ResearchItemResolver.parse("thaumcraft:nitor;1;" + invalid);
            helper.assertTrue(ResearchItemResolver.resolve(token, registries).isEmpty() && ResearchItemResolver.craftReference(token) == null,
                "unproved nitor metadata stays EMPTY with no invented receipt: " + invalid);
        }
        var parsed = ResearchItemResolver.parse("thaumcraft:nitor;1;0");
        var negative = new thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken(parsed.raw(), parsed.legacyText(), parsed.kind(), parsed.name(),
            parsed.count(), -1, parsed.rawSnbt(), parsed.legacySnbt(), parsed.ignoredSegments(), parsed.legacyId(), parsed.unresolvedReason());
        helper.assertTrue(ResearchItemResolver.resolve(negative, registries).isEmpty() && ResearchItemResolver.craftReference(negative) == null,
            "invalid direct descriptor cannot wrap negative metadata into a real color");
        helper.succeed();
    }
    @GameTest(template="empty") public static void normalObtainExactAndTheoryCardOreEquivalentRemainSeparate(GameTestHelper helper) {
        ItemStack required=new ItemStack(Items.RED_WOOL),actual=new ItemStack(Items.BLUE_WOOL);
        actual.set(DataComponents.CUSTOM_NAME,Component.literal("different NBT too"));
        helper.assertFalse(TheorycraftInventory.matchesExact(actual,required),"normal obtain exact source item/meta rejects other wool color");
        helper.assertTrue(TheorycraftInventory.matches(actual,required),"source cards OreDictionary membership returns early, ignoring color and NBT");
        helper.assertTrue(ResearchItemResolver.matches(ResearchItemResolver.parse("oredict:wool"),actual,helper.getLevel().registryAccess()),"explicit source ore requirement uses live wool tag");
        helper.assertFalse(TheorycraftInventory.matches(new ItemStack(Items.EMERALD),new ItemStack(Items.DIAMOND)),"generic gems group never conflates diamond and emerald");helper.succeed();
    }
    @GameTest(template="empty") public static void contextualPotionsEnchantmentsAndLegacySnbtBecomeActualComponents(GameTestHelper helper) {
        var registries=helper.getLevel().registryAccess();
        var potionToken=ResearchItemResolver.parse("minecraft:potion;2;0;{Potion:'minecraft:long_night_vision'}");
        ItemStack potion=ResearchItemResolver.resolve(potionToken,registries);
        helper.assertTrue(potion.is(Items.POTION)&&potion.getCount()==2&&potion.get(DataComponents.POTION_CONTENTS).potion().orElseThrow().is(Potions.LONG_NIGHT_VISION),"source Potion string resolves actual holder/component");
        var token=ResearchItemResolver.parse("minecraft:diamond_sword;1;7;{ench:[{id:16s,lvl:3s}],display:{Name:'Source sword',Lore:['Source lore']},customKey:5}");
        ItemStack sword=ResearchItemResolver.resolve(token,registries);
        helper.assertTrue(sword.is(Items.DIAMOND_SWORD)&&LegacyScanIdentity.rawDamage(sword)==7,"source wear retained independently of enrichment");
        var sharpness=registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharpness,sword)==3,"source numeric id16 -> real world sharpness holder");
        helper.assertTrue(sword.getHoverName().getString().equals("Source sword")&&sword.has(DataComponents.LORE)&&sword.get(DataComponents.CUSTOM_DATA).copyTag().getInt("customKey")==5,"display components and unmapped source custom NBT both retained");
        helper.assertFalse(sword.get(DataComponents.CUSTOM_DATA).copyTag().contains("ench"),"translated enchantments are not fake custom NBT");helper.succeed();
    }
    @GameTest(template="empty") public static void enchantedPlaceholdersAcceptHigherLevelsAndStoredBooksButNotMissingOrLowLevels(GameTestHelper helper) {
        var registries=helper.getLevel().registryAccess();var fortune=registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        var token=ResearchItemResolver.parse("thaumcraft:enchanted_placeholder;1;0;{ench:[{id:35s,lvl:2s}]}");
        ItemStack pick=new ItemStack(Items.DIAMOND_PICKAXE);pick.enchant(fortune,3);
        helper.assertTrue(ResearchItemResolver.matches(token,pick,registries),"placeholder requirement2 accepts actual fortune3 on a non-placeholder item");
        ItemStack low=new ItemStack(Items.DIAMOND_PICKAXE);low.enchant(fortune,1);
        helper.assertFalse(ResearchItemResolver.matches(token,low,registries),"explicit below threshold rejects");
        helper.assertFalse(ResearchItemResolver.matches(token,new ItemStack(Items.DIAMOND_PICKAXE),registries),"missing enchantment rejects");
        ItemStack book=new ItemStack(Items.ENCHANTED_BOOK);var stored=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);stored.set(fortune,3);book.set(DataComponents.STORED_ENCHANTMENTS,stored.toImmutable());
        helper.assertTrue(ResearchItemResolver.matches(token,book,registries),"source crafting enchantment lookup includes stored enchanted-book values");
        ItemStack descriptor=ResearchItemResolver.resolve(token,registries);
        helper.assertTrue(descriptor.is(ItemsTC.enchantedPlaceholder)&&ResearchItemResolver.matchesNativeRequirement(descriptor,book),"native contextual placeholder shares the same threshold semantics");
        var storedToken=ResearchItemResolver.parse("minecraft:enchanted_book;1;0;{StoredEnchantments:[{id:35s,lvl:3s}]}");
        ItemStack resolved=ResearchItemResolver.resolve(storedToken,registries);
        helper.assertTrue(resolved.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(fortune)==3,"legacy StoredEnchantments becomes actual book component");helper.succeed();
    }
    @GameTest(template="empty") public static void noAmbientRegistryFallbackAndAbsentSourceContentRemainEmpty(GameTestHelper helper) {
        var token=ResearchItemResolver.parse("minecraft:diamond_sword;1;0;{ench:[{id:16s,lvl:2s}]}");
        helper.assertTrue(ResearchItemResolver.resolve(token).isEmpty(),"no-context real enchanted item cannot use an ambient holder cache");
        helper.assertFalse(ResearchItemResolver.resolve(token,helper.getLevel().registryAccess()).isEmpty(),"owning world context supplies real enchantment holders");
        helper.assertTrue(ResearchItemResolver.resolve(token).isEmpty(),"contextual resolution does not secretly seed a later no-context fallback");
        for(String invalid:new String[]{"minecraft:mace","thaumcraft:casting_gauntlet","minecraft:not_an_item","minecraft:paper;0","minecraft:paper;1;5","minecraft:diamond_sword;1;0;{ench:[{id:999s,lvl:2s}]}"})
            helper.assertTrue(ResearchItemResolver.resolve(ResearchItemResolver.parse(invalid),helper.getLevel().registryAccess()).isEmpty(),"unproved modern/absent/malformed source descriptor stays EMPTY: "+invalid);
        helper.succeed();
    }
    @GameTest(template="empty") public static void missingPreparedRewardRefusesBeforeAnyResearchEventStageXpOrInventoryMutation(GameTestHelper helper) throws Exception {
        String categoryKey="TC_RESOLVER_MISSING",key="TC_RESOLVER_MISSING_REWARD";
        var root=ResearchCategories.researchCategories;
        var category=new ResearchCategory(categoryKey,null,new AspectList(),ResourceLocation.parse("thaumcraft:textures/items/thaumonomicon.png"),ResourceLocation.parse("thaumcraft:textures/gui/gui_research_back_1.jpg"));
        var previous=root.put(categoryKey,category);
        var player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"TC_Resolver_Missing"));
        try(var capture=PlayerDataSync.captureGameTestPackets((recipient,payload)->helper.fail("missing reward must not send"));
            var probe=new ServerResearchProgressionGameTests.Probe(player)) {
            String json="{\"entries\":[{\"key\":\""+key+"\",\"name\":\"test\",\"category\":\""+categoryKey+"\",\"location\":[0,0],\"reward_item\":[\"addon:missing_reward;2;0\"],\"stages\":[{\"text\":\"test\"}]}]}";
            var spec=ResearchCatalogParser.parse(Map.of("test:resolver-missing",json),root.keySet());
            var entry=ResearchCatalogPublication.prepareWhole(spec,root,Set.of()).entries().get(key);category.research.put(key,entry);
            var before=ThaumcraftCapabilities.getKnowledge(player).serializeNBT().copy();
            int xp=player.totalExperience;
            helper.assertTrue(ResearchManager.unsupportedReason(entry).contains("Source reward item is not available"),"actual live preflight rejects absent source reward");
            helper.assertFalse(ResearchManager.completeResearch(player,key),"no costless completion when source reward is missing");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(player).serializeNBT().equals(before)
                && player.totalExperience==xp && player.getInventory().isEmpty() && probe.research.isEmpty()
                && !PlayerDataSync.isKnowledgeDirty(player),"no research event, stage, XP, inventory reward or dirty state before refusal");
        } finally {if(previous==null)root.remove(categoryKey);else root.put(categoryKey,previous);PlayerDataSync.forget(player);}
        helper.succeed();
    }
    @GameTest(template="empty") public static void preparedContextualEnchantedRewardCompletesOnceAndPreservesRawDescriptor(GameTestHelper helper) throws Exception {
        String categoryKey="TC_RESOLVER_REWARD",key="TC_RESOLVER_CONTEXTUAL_REWARD";
        var root=ResearchCategories.researchCategories;
        var category=new ResearchCategory(categoryKey,null,new AspectList(),ResourceLocation.parse("thaumcraft:textures/items/thaumonomicon.png"),ResourceLocation.parse("thaumcraft:textures/gui/gui_research_back_1.jpg"));
        var previous=root.put(categoryKey,category);
        var player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"TC_Resolver_Reward"));
        try(var capture=PlayerDataSync.captureGameTestPackets((recipient,payload)->{})) {
            String json="{\"entries\":[{\"key\":\""+key+"\",\"name\":\"test\",\"category\":\""+categoryKey+"\",\"location\":[0,0],\"reward_item\":[\"minecraft:diamond_sword;1;7;{ench:[{id:16s,lvl:3s}],display:{Name:'Prepared sword'}}\"],\"stages\":[{\"text\":\"test\"}]}]}";
            var spec=ResearchCatalogParser.parse(Map.of("test:resolver-reward",json),root.keySet());
            var entry=ResearchCatalogPublication.prepareWhole(spec,root,Set.of()).entries().get(key);category.research.put(key,entry);
            helper.assertTrue(ResearchManager.completeResearch(player,key),"actual trusted progression preflights and resolves reward through owning world registry");
            var swords=player.getInventory().items.stream().filter(stack->stack.is(Items.DIAMOND_SWORD)).toList();
            helper.assertTrue(swords.size()==1&&swords.getFirst().getCount()==1&&LegacyScanIdentity.rawDamage(swords.getFirst())==7,"one actual reward with source wear");
            var sharpness=helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharpness,swords.getFirst())==3&&swords.getFirst().getHoverName().getString().equals("Prepared sword"),"prepared SNBT enchantment reward becomes real components, not a marker");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(player).isResearchComplete(key)&&entry.getRewardItem()==null,"immutable raw descriptor remains source of contextual rewards");
            helper.assertFalse(ResearchManager.completeResearch(player,key),"completed entry cannot reward twice");
            helper.assertTrue(player.getInventory().items.stream().filter(stack->stack.is(Items.DIAMOND_SWORD)).mapToInt(ItemStack::getCount).sum()==1,"no duplicate contextual reward");
        } finally { if(previous==null)root.remove(categoryKey);else root.put(categoryKey,previous);PlayerDataSync.forget(player); }
        helper.succeed();
    }
}
