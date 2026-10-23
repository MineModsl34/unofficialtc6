package thaumcraft.common.lib.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/**
 * Real registered items, player attachments and NeoForge crafting/research events. Fixtures
 * use real InventoryMenu/ResultSlot takes, unspawned FakePlayers and packet capture, not
 * client crafting clicks or real sockets. Other cases deliberately post only the event route.
 * Physical table conversion is independently verified by the table implementation's tests.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyResearchCraftingGameTests {
    @GameTest(template = "empty")
    public static void realAlchemyCraftEventsRecordEveryRequiredSourceBlockReceiptWithoutResearchGrants(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Alchemy_Craft_Receipt");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var outputs = List.of(new ItemStack(BlocksTC.crucible), new ItemStack(BlocksTC.nitor.get(DyeColor.YELLOW)),
            table(helper), new ItemStack(BlocksTC.smelterBasic), new ItemStack(BlocksTC.infusionMatrix));
        var strings = List.of("1xtile.crucible@0", "1xtile.nitor_yellow@0", "1xtile.research_table@0", "1xtile.smelter_basic@0", "1xtile.infusion_matrix@0");
        var markers = strings.stream().map(value -> "[#]" + value.hashCode()).toList();
        try (var probe = new Probe(owner, false);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("craft receipt must use ordinary deferred owner sync"))) {
            for (int index = 0; index < outputs.size(); index++) {
                ItemStack output = outputs.get(index);
                helper.assertFalse(output.isEmpty(), "actual source alchemy output is registered");
                ItemStack before = output.copy();
                craft(owner, output); // Actual NeoForge event -> production subscriber -> declared catalog craft requirement.
                helper.assertTrue(knowledge.isResearchComplete(markers.get(index)), "real native craft event records required exact source marker: " + strings.get(index));
                helper.assertTrue(ItemStack.matches(before, output), "craft bridge does not rewrite real production output");
            }
            helper.assertTrue(probe.keys.equals(markers) && knowledge.getResearchList().size() == markers.size(),
                "only declared prerequisite craft markers are recorded in actual event order");
            for (String key : List.of("FIRSTSTEPS", "UNLOCKALCHEMY", "ESSENTIASMELTER", "INFUSION"))
                helper.assertFalse(knowledge.isResearchKnown(key), "craft event grants no research-tree stage: " + key);
            helper.assertTrue(probe.knowledgeEvents == 0 && owner.totalExperience == 0 && PlayerDataSync.isKnowledgeDirty(owner),
                "real production craft receipt queues normal owner sync without knowledge or XP fixtures");
            var complete = knowledge.serializeNBT().copy();
            for (ItemStack output : outputs) craft(owner, output);
            helper.assertTrue(probe.keys.equals(markers) && knowledge.serializeNBT().equals(complete),
                "repeat real crafts cannot replay receipt grants or mutate owner knowledge");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void actualIdentityDamageCountAndComponentsMatchOnlyTwoSourceReferences(GameTestHelper helper) {
        ItemStack tools = tools(helper);
        tools.setCount(7);
        ItemStack table = table(helper);
        table.setCount(64);
        CompoundTag tag = new CompoundTag();
        tag.putString("legacyIgnored", "NBT does not occur in MC1.12 ItemStack.toString");
        tools.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        table.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        tools.set(DataComponents.CUSTOM_NAME, Component.literal("Named scribing tools"));
        table.set(DataComponents.CUSTOM_NAME, Component.literal("Named research table"));
        ItemStack beforeTools = tools.copy();
        ItemStack beforeTable = table.copy();
        helper.assertTrue(LegacyResearchCrafting.reference(tools).orElseThrow() == LegacyResearchCrafting.SCRIBING_TOOLS,
            "actual tools item and damage zero; count/components ignored");
        helper.assertTrue(LegacyResearchCrafting.reference(table).orElseThrow() == LegacyResearchCrafting.RESEARCH_TABLE,
            "actual table item and damage zero; count/components ignored");
        helper.assertTrue(ItemStack.matches(tools, beforeTools) && ItemStack.matches(table, beforeTable),
            "reference lookup does not normalize/mutate real stacks");
        tools.setDamageValue(1);
        helper.assertTrue(LegacyResearchCrafting.reference(tools).isEmpty(), "damaged tools have a different old hash");
        table.set(DataComponents.DAMAGE, 1);
        helper.assertTrue(LegacyResearchCrafting.reference(table).isEmpty(), "even artificial table damage must not match old meta zero");
        for (int damage : List.of(Integer.MIN_VALUE, -1, 1, 99, 100, 101, 32767, Integer.MAX_VALUE)) {
            tools.set(DataComponents.DAMAGE, damage);
            table.set(DataComponents.DAMAGE, damage);
            helper.assertTrue(LegacyScanIdentity.rawDamage(tools) == damage && LegacyScanIdentity.rawDamage(table) == damage,
                "fixture retains direct raw component value, not clamped Item.getDamage: " + damage);
            helper.assertTrue(LegacyResearchCrafting.reference(tools).isEmpty() && LegacyResearchCrafting.reference(table).isEmpty(),
                "raw negative/positive/extreme metadata cannot become a zero receipt for either item: " + damage);
        }
        tools.set(DataComponents.DAMAGE, 0);
        table.set(DataComponents.DAMAGE, 0);
        helper.assertTrue(LegacyResearchCrafting.reference(tools).orElseThrow() == LegacyResearchCrafting.SCRIBING_TOOLS
            && LegacyResearchCrafting.reference(table).orElseThrow() == LegacyResearchCrafting.RESEARCH_TABLE,
            "explicit raw zero matches while names/custom data remain ignored");
        helper.assertTrue(LegacyResearchCrafting.reference(new ItemStack(Items.PAPER)).isEmpty(), "unmapped actual item");
        helper.assertTrue(LegacyResearchCrafting.reference(new ItemStack(ItemsTC.thaumometer)).isEmpty(), "other actual TC item");
        helper.assertTrue(LegacyResearchCrafting.reference(ItemStack.EMPTY).isEmpty()
            && LegacyResearchCrafting.reference((ItemStack) null).isEmpty(), "empty/null never creates a receipt");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realCraftingBusRecordsBeforeUnlockOnceAndFlushesOnlyOwner(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_CraftReceipt");
        ServerPlayer other = player(helper, "TC_CraftBystander");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        List<ServerPlayer> recipients = new ArrayList<>();
        List<PacketSyncKnowledge> packets = new ArrayList<>();
        List<String> expected = List.of(LegacyResearchCrafting.SCRIBING_TOOLS.marker(), LegacyResearchCrafting.RESEARCH_TABLE.marker());
        ItemStack tools = tools(helper);
        tools.setCount(3);
        CompoundTag custom = new CompoundTag();
        custom.putInt("ignored", 123);
        tools.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        ItemStack table = table(helper);
        ItemStack before = tools.copy();
        try (var probe = new Probe(owner, false);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
                 recipients.add(recipient);
                 helper.assertTrue(packet instanceof PacketSyncKnowledge, "receipt has no knowledge-gain or broadcast packet");
                 packets.add((PacketSyncKnowledge) packet);
             })) {
            helper.assertFalse(knowledge.isResearchKnown("FIRSTSTEPS") || knowledge.isResearchKnown("THEORYRESEARCH"),
                "fixture has not unlocked the research tree");
            craft(owner, tools);
            craft(owner, table);
            helper.assertTrue(probe.keys.equals(expected), "actual subscribed bus bridge preserves source marker order");
            for (String key : expected) {
                helper.assertTrue(knowledge.isResearchComplete(key) && knowledge.getResearchStage(key) == 0,
                    "opaque source crafting marker completes at zero: " + key);
                helper.assertFalse(knowledge.hasResearchFlag(key, EnumResearchFlag.POPUP)
                    || knowledge.hasResearchFlag(key, EnumResearchFlag.RESEARCH), "crafting markers have no catalog popup flags");
            }
            helper.assertTrue(knowledge.getResearchList().size() == 2, "no research-tree grant or extra receipt");
            helper.assertTrue(owner.totalExperience == 0 && probe.knowledgeEvents == 0 && packets.isEmpty(),
                "unknown receipt does not grant XP/knowledge or synchronously send packets");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "real Manager queued owner snapshot");
            var complete = knowledge.serializeNBT().copy();
            craft(owner, tools);
            craft(owner, table);
            helper.assertTrue(probe.keys.equals(expected) && knowledge.serializeNBT().equals(complete),
                "repeat craft emits no research event and changes no data");
            helper.assertTrue(ItemStack.matches(tools, before), "recording ignores count/NBT without mutating crafted stack");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(recipients.size() == 1 && recipients.getFirst() == owner, "only crafting owner receives the queued snapshot");
            helper.assertTrue(packets.getFirst().data().equals(complete) && !PlayerDataSync.isKnowledgeDirty(owner),
                "snapshot contains exact full receipt storage and normal tick flush clears dirty");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore), "bystander untouched");
        } finally {
            PlayerDataSync.forget(owner);
            PlayerDataSync.forget(other);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledRealCraftingReceiptsAndNonmatchesDoNotDirtyOrAward(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_CraftCancel");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var before = knowledge.serializeNBT().copy();
        List<String> expected = List.of(LegacyResearchCrafting.SCRIBING_TOOLS.marker(), LegacyResearchCrafting.RESEARCH_TABLE.marker());
        try (var probe = new Probe(owner, true);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("canceled/nonmatching receipt sent a packet"))) {
            craft(owner, tools(helper));
            craft(owner, table(helper));
            helper.assertTrue(probe.keys.equals(expected), "both actual receipt events can be canceled independently");
            ItemStack damaged = tools(helper);
            ItemStack damagedTable = table(helper);
            for (int damage : List.of(Integer.MIN_VALUE, -1, 1, 100, 101, 32767, Integer.MAX_VALUE)) {
                damaged.set(DataComponents.DAMAGE, damage);
                damagedTable.set(DataComponents.DAMAGE, damage);
                craft(owner, damaged);
                craft(owner, damagedTable);
            }
            craft(owner, new ItemStack(Items.PAPER));
            craft(owner, ItemStack.EMPTY);
            helper.assertTrue(probe.keys.equals(expected), "damaged/wrong/empty crafted items produce no research event");
            helper.assertTrue(knowledge.serializeNBT().equals(before), "full knowledge remains exact after cancellation/nonmatches");
            helper.assertTrue(owner.totalExperience == 0 && probe.knowledgeEvents == 0 && !PlayerDataSync.isKnowledgeDirty(owner),
                "no XP, knowledge event or dirty snapshot after rejection");
        } finally {
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runtimeLookupDoesNotCacheFieldsOrDereferenceMissingRegistrations(GameTestHelper helper) {
        ItemStack tools = tools(helper);
        ItemStack table = table(helper);
        var registeredTools = ItemsTC.scribingTools;
        var registeredTable = BlocksTC.researchTable;
        // Synchronous helper-only scope: no events, waits, registration mutation or production placeholder.
        try {
            ItemsTC.scribingTools = null;
            BlocksTC.researchTable = null;
            helper.assertTrue(LegacyResearchCrafting.reference(tools).isEmpty() && LegacyResearchCrafting.reference(table).isEmpty(),
                "unpopulated fields are not cached or dereferenced");
        } finally {
            ItemsTC.scribingTools = registeredTools;
            BlocksTC.researchTable = registeredTable;
        }
        helper.assertTrue(LegacyResearchCrafting.reference(tools).orElseThrow() == LegacyResearchCrafting.SCRIBING_TOOLS
            && LegacyResearchCrafting.reference(table).orElseThrow() == LegacyResearchCrafting.RESEARCH_TABLE,
            "restored actual registrations are observed immediately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realInventoryResultTakeCraftsToolsAndPublishesSourceReceipt(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_ToolsTake");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        try (var probe = new Probe(owner, false);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("receipt should queue, not immediately send"))) {
            var menu = owner.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(Items.GLASS_BOTTLE));
            menu.getSlot(2).set(new ItemStack(Items.FEATHER));
            menu.getSlot(3).set(new ItemStack(Items.INK_SAC));
            assertRealRecipe(helper, owner, "scribingtoolscraft2");
            helper.assertTrue(probe.keys.isEmpty() && knowledge.getResearchList().isEmpty(), "crafting preview does not grant a receipt");
            ItemStack result = menu.getSlot(0).safeTake(1, Integer.MAX_VALUE, owner);
            helper.assertTrue(result.is(ItemsTC.scribingTools) && result.getCount() == 1 && LegacyScanIdentity.rawDamage(result) == 0,
                "actual vanilla result take returned fresh tools");
            assertNormalTakeReceipt(helper, owner, probe);
            for (int slot = 1; slot <= 4; slot++) {
                helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "normal ResultSlot.onTake consumed the exact crafting inputs");
            }
        } finally {
            owner.inventoryMenu.clearCraftingContent();
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realInventoryResultTakeRefillsWornNamedToolsAndPublishesSourceReceipt(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_RefillTake");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        ItemStack worn = tools(helper);
        worn.setDamageValue(99);
        worn.set(DataComponents.CUSTOM_NAME, Component.literal("Source refill clears this name"));
        CompoundTag custom = new CompoundTag();
        custom.putString("sourceNBT", "ignored ingredient match, cleared output");
        worn.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        try (var probe = new Probe(owner, false);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("receipt should queue, not immediately send"))) {
            var menu = owner.inventoryMenu;
            menu.getSlot(1).set(worn);
            menu.getSlot(4).set(new ItemStack(Items.INK_SAC));
            assertRealRecipe(helper, owner, "scribingtoolsrefill");
            helper.assertTrue(probe.keys.isEmpty() && knowledge.getResearchList().isEmpty(), "refill preview does not grant a receipt");
            ItemStack result = menu.getSlot(0).safeTake(1, Integer.MAX_VALUE, owner);
            helper.assertTrue(result.is(ItemsTC.scribingTools) && result.getCount() == 1 && LegacyScanIdentity.rawDamage(result) == 0,
                "actual normal result take returned unworn tools, not the input stack");
            helper.assertFalse(result.has(DataComponents.CUSTOM_NAME) || result.has(DataComponents.CUSTOM_DATA), "source recipe produces clean output");
            assertNormalTakeReceipt(helper, owner, probe);
            for (int slot = 1; slot <= 4; slot++) {
                helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "normal ResultSlot.onTake consumes worn tools and ink");
            }
        } finally {
            owner.inventoryMenu.clearCraftingContent();
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }

    private static void assertRealRecipe(GameTestHelper helper, ServerPlayer owner, String path) {
        var result = owner.inventoryMenu.getSlot(0);
        helper.assertTrue(result.container instanceof RecipeCraftingHolder, "real menu result uses vanilla recipe tracking");
        var holder = (RecipeCraftingHolder) result.container;
        helper.assertTrue(holder.getRecipeUsed() != null
            && holder.getRecipeUsed().id().equals(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path)),
            "InventoryMenu resolved the actual registered source-compatible recipe: " + path);
        helper.assertTrue(result.hasItem() && result.getItem().is(ItemsTC.scribingTools), "menu itself assembled its recipe output");
    }

    private static void assertNormalTakeReceipt(GameTestHelper helper, ServerPlayer owner, Probe probe) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        String marker = LegacyResearchCrafting.SCRIBING_TOOLS.marker();
        helper.assertTrue(probe.keys.equals(List.of(marker)), "ResultSlot.take published the real event observed by the production receipt subscriber");
        helper.assertTrue(knowledge.isResearchComplete(marker) && knowledge.getResearchStage(marker) == 0
            && knowledge.getResearchList().size() == 1, "take records only the original crafting marker before any unlock");
        helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner) && owner.totalExperience == 0 && probe.knowledgeEvents == 0,
            "actual take follows owner dirty-sync path without knowledge/XP grants");
    }

    private static void craft(ServerPlayer owner, ItemStack crafted) {
        EventHooks.firePlayerCraftingEvent(owner, crafted, new SimpleContainer(1));
    }

    private static ItemStack tools(GameTestHelper helper) {
        helper.assertTrue(ItemsTC.scribingTools != null, "actual tools registered");
        return new ItemStack(ItemsTC.scribingTools);
    }

    private static ItemStack table(GameTestHelper helper) {
        helper.assertTrue(BlocksTC.researchTable != null && BlocksTC.researchTable.asItem() != Items.AIR,
            "actual research table and its real item must be registered for this integration slice");
        return new ItemStack(BlocksTC.researchTable);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        private final boolean cancel;
        final List<String> keys = new ArrayList<>();
        int knowledgeEvents;

        Probe(ServerPlayer owner, boolean cancel) {
            this.owner = owner;
            this.cancel = cancel;
            NeoForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            keys.add(event.getResearchKey());
            if (cancel) event.setCanceled(true);
        }

        @SubscribeEvent public void knowledge(ResearchEvent.Knowledge event) {
            if (event.getPlayer() == owner) knowledgeEvents++;
        }

        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
