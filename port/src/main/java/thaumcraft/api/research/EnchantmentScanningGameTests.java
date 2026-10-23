package thaumcraft.api.research;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.research.LegacyEnchantmentScanNames;

/**
 * Real accepted enchantment registry, codecs, production scan dispatch and player attachments.
 * Same package only to call the existing dispatch seam with a local handler list: no changes
 * to global scanner/catalog registration and no assertion of real socket/client feedback.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class EnchantmentScanningGameTests {
    @GameTest(template = "empty")
    public static void allThirtyVanillaTargetsMatchOrdinaryAndStoredBookEnchantments(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var descriptor : LegacyEnchantmentScanNames.VANILLA) {
            var holder = registry.getHolderOrThrow(descriptor.target());
            var values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            values.set(holder, 1);
            ItemStack ordinary = new ItemStack(Items.PAPER);
            ordinary.set(DataComponents.ENCHANTMENTS, values.toImmutable());
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(DataComponents.STORED_ENCHANTMENTS, values.toImmutable());
            var scanner = descriptor.scanner();
            helper.assertTrue(scanner.checkThing(null, ordinary), "presence ignores item applicability: " + descriptor.originalLocation());
            helper.assertTrue(scanner.checkThing(null, book), "stored book: " + descriptor.originalLocation());
            helper.assertTrue(scanner.getResearchKey(null, ordinary).equals(descriptor.researchKey()), "opaque legacy marker");
            for (var other : LegacyEnchantmentScanNames.VANILLA) {
                if (other == descriptor) continue;
                helper.assertFalse(other.scanner().checkThing(null, ordinary), "different target must not match " + descriptor.originalLocation());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void codecRetainedLevelZeroStillCountsAsPresence(GameTestHelper helper) {
        ItemEnchantments zeroes = decode(helper, LegacyEnchantmentScanNames.VANILLA, 0);
        helper.assertTrue(zeroes.size() == 30, "actual component codec retains thirty level-zero entries");
        ItemStack ordinary = new ItemStack(Items.DIAMOND_SWORD);
        ordinary.set(DataComponents.ENCHANTMENTS, zeroes);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, zeroes);
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var descriptor : LegacyEnchantmentScanNames.VANILLA) {
            helper.assertTrue(zeroes.getLevel(registry.getHolderOrThrow(descriptor.target())) == 0, "stored level really is zero");
            helper.assertTrue(descriptor.scanner().checkThing(null, ordinary), "zero-level ordinary presence " + descriptor.originalLocation());
            helper.assertTrue(descriptor.scanner().checkThing(null, book), "zero-level book presence " + descriptor.originalLocation());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void helperSelectsExactlyOneComponentRatherThanUnion(GameTestHelper helper) {
        var sharpness = LegacyEnchantmentScanNames.VANILLA.get(11);
        var silk = LegacyEnchantmentScanNames.VANILLA.get(19);
        ItemStack ordinary = new ItemStack(Items.DIAMOND_SWORD);
        ordinary.set(DataComponents.ENCHANTMENTS, decode(helper, List.of(sharpness), 1));
        ordinary.set(DataComponents.STORED_ENCHANTMENTS, decode(helper, List.of(silk), 1));
        helper.assertTrue(sharpness.scanner().checkThing(null, ordinary), "ordinary uses ordinary component");
        helper.assertFalse(silk.scanner().checkThing(null, ordinary), "ordinary ignores stored component");
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.ENCHANTMENTS, decode(helper, List.of(sharpness), 1));
        book.set(DataComponents.STORED_ENCHANTMENTS, decode(helper, List.of(silk), 1));
        helper.assertFalse(sharpness.scanner().checkThing(null, book), "book ignores ordinary component");
        helper.assertTrue(silk.scanner().checkThing(null, book), "book uses stored component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceObjectConversionSupportsDropAndRejectsEmptyNullAndPlainBlocks(GameTestHelper helper) {
        var owner = player(helper, "TC_Ench_Object");
        var descriptor = LegacyEnchantmentScanNames.VANILLA.get(11);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.ENCHANTMENTS, decode(helper, List.of(descriptor), 1));
        var drop = new ItemEntity(helper.getLevel(), 0, 0, 0, sword);
        var scanner = descriptor.scanner();
        helper.assertTrue(scanner.checkThing(owner, drop), "source helper scans dropped item enchantments");
        helper.assertFalse(scanner.checkThing(owner, null), "null bypasses item conversion");
        helper.assertFalse(scanner.checkThing(owner, ItemStack.EMPTY), "empty stack");
        helper.assertFalse(scanner.checkThing(owner, new ItemStack(Items.DIAMOND_SWORD)), "ordinary unenchanted item");
        helper.assertFalse(scanner.checkThing(owner, new Object()), "unsupported source parameter");
        helper.assertFalse(scanner.checkThing(owner, helper.absolutePos(new BlockPos(1, 1, 1))), "plain terrain clone has no enchantments");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allMatchingMarkersDispatchInOrderOnceAndCancelWithoutAwards(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Ench_Order");
        var other = player(helper, "TC_Ench_Other");
        var canceled = player(helper, "TC_Ench_Cancel");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var canceledData = ThaumcraftCapabilities.getKnowledge(canceled);
        var canceledBefore = canceledData.serializeNBT().copy();
        List<IScanThing> handlers = LegacyEnchantmentScanNames.VANILLA.stream().map(d -> (IScanThing) d.scanner()).toList();
        var expected = LegacyEnchantmentScanNames.VANILLA.stream().map(LegacyEnchantmentScanNames.Descriptor::researchKey).toList();
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ENCHANTMENTS, decode(helper, LegacyEnchantmentScanNames.VANILLA, 1));
        List<ServerPlayer> recipients = new ArrayList<>();
        try (var events = new Probe(owner, canceled);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {
                 recipients.add(recipient);
                 helper.assertTrue(payload instanceof PacketSyncKnowledge, "enchantment scanner has no knowledge-gain callback");
             })) {
            var first = ScanningManager.scanHandlers(owner, stack, handlers,
                key -> thaumcraft.api.ThaumcraftApi.internalMethods.progressResearch(owner, key));
            helper.assertTrue(first.found() && !first.suppress(), "all nonempty markers use normal scan status path");
            helper.assertTrue(events.ownerKeys.equals(expected), "actual research events preserve all thirty handler order");
            for (String key : expected) {
                helper.assertTrue(knowledge.isResearchComplete(key) && knowledge.getResearchStage(key) == 0, "opaque unknown marker completes at zero: " + key);
                helper.assertFalse(knowledge.hasResearchFlag(key, EnumResearchFlag.POPUP) || knowledge.hasResearchFlag(key, EnumResearchFlag.RESEARCH), "no catalog popup/research flags");
            }
            for (var category : ResearchCategories.researchCategories.values()) {
                for (EnumKnowledgeType type : EnumKnowledgeType.values()) {
                    helper.assertTrue(knowledge.getKnowledgeRaw(type, category) == 0, "no invented knowledge award");
                }
            }
            helper.assertTrue(owner.totalExperience == 0 && events.knowledgeEvents == 0 && recipients.isEmpty(), "no XP, knowledge event or immediate payload");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "real progress queues owner knowledge snapshot");
            helper.assertFalse(ScanningManager.scanHandlers(owner, stack, handlers,
                key -> thaumcraft.api.ThaumcraftApi.internalMethods.progressResearch(owner, key)).found(), "repeat scan gives no new successes");
            helper.assertTrue(events.ownerKeys.equals(expected), "repeat scan gives no duplicate events");
            PlayerDataSync.syncKnowledgeNow(owner);
            helper.assertTrue(recipients.size() == 1 && recipients.getFirst() == owner, "snapshot addresses only scanning owner");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).getResearchList().isEmpty(), "bystander attachment untouched");
            var failed = ScanningManager.scanHandlers(canceled, stack, handlers,
                key -> thaumcraft.api.ThaumcraftApi.internalMethods.progressResearch(canceled, key));
            helper.assertFalse(failed.found(), "canceled research events reject every matching scanner");
            helper.assertTrue(events.canceledKeys.equals(expected), "cancellation does not stop later matching handlers");
            helper.assertTrue(canceledData.serializeNBT().equals(canceledBefore), "canceled owner's full knowledge NBT remains unchanged");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(canceled), "canceled scans do not queue snapshots");
            helper.assertTrue(recipients.size() == 1, "canceled scans send no payload");
        } finally {
            PlayerDataSync.forget(owner);
            PlayerDataSync.forget(other);
            PlayerDataSync.forget(canceled);
        }
        helper.succeed();
    }

    private static ItemEnchantments decode(GameTestHelper helper, List<LegacyEnchantmentScanNames.Descriptor> entries, int level) {
        JsonObject values = new JsonObject();
        for (var entry : entries) values.addProperty(entry.target().location().toString(), level);
        // The public codec, unlike Mutable.set(0), preserves an existing zero-level map entry.
        return ItemEnchantments.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), values).getOrThrow();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        private final ServerPlayer canceled;
        final List<String> ownerKeys = new ArrayList<>();
        final List<String> canceledKeys = new ArrayList<>();
        int knowledgeEvents;

        Probe(ServerPlayer owner, ServerPlayer canceled) {
            this.owner = owner;
            this.canceled = canceled;
            NeoForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() == owner) ownerKeys.add(event.getResearchKey());
            if (event.getPlayer() == canceled) {
                canceledKeys.add(event.getResearchKey());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent public void knowledge(ResearchEvent.Knowledge event) {
            if (event.getPlayer() == owner || event.getPlayer() == canceled) knowledgeEvents++;
        }

        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
