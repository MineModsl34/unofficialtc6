package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketResearchReadToServer;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.ServerResearchReadHandler;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler;
import thaumcraft.common.lib.research.ResearchReadFingerprint;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/** Server read-ack route on real accepted catalog identity; FakePlayer is NOT a TCP client. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchReadGameTests {
    @GameTest(template = "empty")
    public static void completedEntryReadClearsOnlyItsPageResearchAndSynchronizesOnlyOwner(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Read_Owner"); var other = player(helper, "TC_Read_Other");
        var data = ThaumcraftCapabilities.getKnowledge(owner); var category = ResearchCategories.getResearchCategory("BASICS");
        completed(owner, "FIRSTSTEPS"); completed(owner, "THEORYRESEARCH");
        for (String key : List.of("FIRSTSTEPS", "THEORYRESEARCH")) {
            data.setResearchFlag(key, EnumResearchFlag.PAGE); data.setResearchFlag(key, EnumResearchFlag.RESEARCH);
        }
        data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 17);
        owner.getInventory().items.set(0, new ItemStack(Items.PAPER, 3));
        var otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        var warpBefore = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        int stage = data.getResearchStage("FIRSTSTEPS"), xp = owner.totalExperience;
        var recipients = new ArrayList<ServerPlayer>(); var packets = new ArrayList<PacketSyncKnowledge>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            helper.assertTrue(packet instanceof PacketSyncKnowledge, "only knowledge ACK, no award packet");
            recipients.add(recipient); packets.add((PacketSyncKnowledge) packet);
        }); var probe = new ServerResearchProgressionGameTests.Probe(owner)) {
            helper.assertTrue(ServerResearchReadHandler.tryHandle(owner, packet(owner, "FIRSTSTEPS")), "completed node may be read, not mayRequestProgress gate");
            helper.assertFalse(data.hasResearchFlag("FIRSTSTEPS", EnumResearchFlag.PAGE) || data.hasResearchFlag("FIRSTSTEPS", EnumResearchFlag.RESEARCH), "requested flags cleared");
            helper.assertTrue(data.hasResearchFlag("THEORYRESEARCH", EnumResearchFlag.PAGE) && data.hasResearchFlag("THEORYRESEARCH", EnumResearchFlag.RESEARCH), "other node flags retained");
            helper.assertTrue(recipients.equals(List.of(owner)) && packets.getFirst().data().equals(data.serializeNBT()), "exact owner ACK snapshot");
            helper.assertTrue(data.getResearchStage("FIRSTSTEPS") == stage && data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 17
                && owner.totalExperience == xp && owner.getInventory().items.get(0).getCount() == 3 && probe.research.isEmpty() && probe.payments.isEmpty()
                && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(warpBefore), "read cannot advance, pay, award, generate XP or warp");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore), "bystander has no mutation");
            helper.assertFalse(ServerResearchReadHandler.tryHandle(owner, packet(owner, "FIRSTSTEPS")), "immediate replay bounded/no second ACK");
            helper.assertTrue(packets.size() == 1, "one accepted read produces one ACK");
        } finally { forget(owner); forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleSameStageReadCannotEraseNewAddendumAndRecoversCurrentOwnerSnapshot(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Read_Stale"); var data = ThaumcraftCapabilities.getKnowledge(owner);
        completed(owner, "FIRSTSTEPS"); data.setResearchFlag("FIRSTSTEPS", EnumResearchFlag.RESEARCH);
        var stale = packet(owner, "FIRSTSTEPS");
        data.setResearchFlag("FIRSTSTEPS", EnumResearchFlag.PAGE); // New addendum, same entry/stage/catalog.
        var before = data.serializeNBT().copy(); var snapshots = new ArrayList<PacketSyncKnowledge>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, value) -> {
            helper.assertTrue(recipient == owner && value instanceof PacketSyncKnowledge, "stale recovery only contextual owner");
            snapshots.add((PacketSyncKnowledge) value);
        })) {
            helper.assertFalse(ServerResearchReadHandler.tryHandle(owner, stale), "state token matters even when stage/revision unchanged");
            helper.assertTrue(data.serializeNBT().equals(before) && data.hasResearchFlag("FIRSTSTEPS", EnumResearchFlag.PAGE)
                && data.hasResearchFlag("FIRSTSTEPS", EnumResearchFlag.RESEARCH), "new flags never erased by stale received view");
            helper.assertTrue(snapshots.size() == 1 && snapshots.getFirst().data().equals(before), "bounded current snapshot permits later legitimate retry");
        } finally { forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownOpaqueUnownedReplacedAndStaleCatalogReadsHaveNoGrantOrSync(GameTestHelper helper) throws Exception {
        for (String key : List.of("FIRSTSTEPS", "!opaque", "[#]123", "FIRSTSTEPS@1", "TC_NOT_ACCEPTED")) {
            var owner = player(helper, "TC_Read_Unknown"); var before = ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().copy();
            try (var capture = PlayerDataSync.captureGameTestPackets((recipient, value) -> helper.fail("unauthorized read has no ACK"))) {
                helper.assertFalse(ServerResearchReadHandler.tryHandle(owner, packet(owner, key)), "read has no start/grant capability: " + key);
                helper.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(before), "unknown read unchanged");
            } finally { forget(owner); }
        }
        var owner = player(helper, "TC_Read_Revision"); completed(owner, "FIRSTSTEPS");
        var data = ThaumcraftCapabilities.getKnowledge(owner); data.setResearchFlag("FIRSTSTEPS", EnumResearchFlag.PAGE);
        var before = data.serializeNBT().copy(); var legitimate = packet(owner, "FIRSTSTEPS");
        String digest = legitimate.reference().catalogDigest();
        String stale = (digest.charAt(0) == '0' ? "1" : "0") + digest.substring(1);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, value) -> helper.fail("stale catalog has no knowledge ACK"))) {
            helper.assertFalse(ServerResearchReadHandler.tryHandle(owner, new PacketResearchReadToServer(
                new PacketSyncProgressToServer("FIRSTSTEPS", legitimate.reference().expectedStage(), stale), legitimate.readStateDigest())), "old catalog rejected");
            helper.assertTrue(data.serializeNBT().equals(before), "stale catalog flags retained");
            forget(owner);
            var category = ResearchCategories.getResearchCategory("BASICS"); var accepted = category.research.get("FIRSTSTEPS");
            var replacement = new ResearchEntry(); replacement.setKey("FIRSTSTEPS"); replacement.setCategory("BASICS");
            category.research.put("FIRSTSTEPS", replacement);
            try { helper.assertFalse(ServerResearchReadHandler.tryHandle(owner, legitimate), "same string key cannot borrow accepted object identity"); }
            finally { category.research.put("FIRSTSTEPS", accepted); }
            helper.assertTrue(data.serializeNBT().equals(before), "replacement cannot erase flags");
        } finally { forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void readAndAdvanceRateLimitsAreSeparateButReadNoopsAreBounded(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Read_Window"); completed(owner, "FIRSTSTEPS");
        var data = ThaumcraftCapabilities.getKnowledge(owner); data.setResearchFlag("FIRSTSTEPS", EnumResearchFlag.PAGE);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, value) -> {})) {
            var read = packet(owner, "FIRSTSTEPS");
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, read.reference()).status() == ServerResearchRequestHandler.Status.BROWSER_UNAVAILABLE,
                "completed progress request rejected, but occupies only advance limiter");
            helper.assertTrue(ServerResearchReadHandler.tryHandle(owner, read), "advance limiter cannot suppress legitimate same-tick read");
            data.setResearchFlag("FIRSTSTEPS", EnumResearchFlag.PAGE);
            helper.assertFalse(ServerResearchReadHandler.tryHandle(owner, packet(owner, "FIRSTSTEPS")), "same-tick read replay limited");
        }
        helper.runAfterDelay(5, () -> {
            try (var capture = PlayerDataSync.captureGameTestPackets((recipient, value) -> {})) {
                helper.assertTrue(ServerResearchReadHandler.tryHandle(owner, packet(owner, "FIRSTSTEPS")), "read window expires in server ticks");
                helper.succeed();
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { forget(owner); }
        });
    }

    private static PacketResearchReadToServer packet(ServerPlayer owner, String key) {
        var payload = ResearchCatalogRuntime.currentPayload(owner.getServer());
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        return new PacketResearchReadToServer(new PacketSyncProgressToServer(key, data.getResearchStage(key), payload.digest()), ResearchReadFingerprint.of(data.serializeNBT()));
    }
    private static void completed(ServerPlayer owner, String key) {
        var data = ThaumcraftCapabilities.getKnowledge(owner); data.addResearch(key);
        data.setResearchStage(key, ResearchCategories.getResearch(key).getStages().length + 1);
    }
    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
    private static void forget(ServerPlayer owner) {
        PlayerDataSync.forget(owner);
        ServerResearchRequestHandler.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));
    }
}
