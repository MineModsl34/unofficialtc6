package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.events.PlayerDataEvents;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/** Actual registered attachments/lifecycle/codec tests, not a real-client connection claim. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class PlayerDataGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name) {
        // Not added to the world's player list, logged in, spawned or written to a user save.
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static void fill(ServerPlayer player) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        knowledge.addResearch("TC_PORT_STORAGE_KEY");
        knowledge.setResearchStage("TC_PORT_STORAGE_KEY", 2);
        knowledge.setResearchFlag("TC_PORT_STORAGE_KEY", EnumResearchFlag.PAGE);
        var warp = ThaumcraftCapabilities.getWarp(player);
        warp.set(EnumWarpType.PERMANENT, 7);
        warp.set(EnumWarpType.NORMAL, 11);
        warp.set(EnumWarpType.TEMPORARY, 13);
        warp.setCounter(123);
    }

    @GameTest(template = "empty")
    public static void registeredEmptyAttachmentsArePersisted(GameTestHelper helper) {
        ServerPlayer player = player(helper, "TC_Data_Empty");
        helper.assertTrue(ThaumcraftCapabilities.KNOWLEDGE == TCAttachments.KNOWLEDGE, "public migrated knowledge alias");
        helper.assertTrue(ThaumcraftCapabilities.WARP == TCAttachments.WARP, "public migrated warp alias");
        ThaumcraftCapabilities.getKnowledge(player);
        ThaumcraftCapabilities.getWarp(player);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        CompoundTag attachments = saved.getCompound("neoforge:attachments");
        helper.assertTrue(attachments.contains("thaumcraft:knowledge"), "empty knowledge is not skipped");
        helper.assertTrue(attachments.contains("thaumcraft:warp"), "empty warp is not skipped");
        helper.assertTrue(attachments.getCompound("thaumcraft:knowledge").getList("research", 10).isEmpty(), "factory invents no research");
        helper.assertTrue(attachments.getCompound("thaumcraft:warp").getIntArray("warp").length == 3, "all three warp types are stored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPlayerNbtRoundtripDoesNotAliasModels(GameTestHelper helper) {
        ServerPlayer original = player(helper, "TC_Data_Save");
        ServerPlayer restored = player(helper, "TC_Data_Load");
        fill(original);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        restored.load(saved.copy());
        helper.assertTrue(ThaumcraftCapabilities.getKnowledge(restored).serializeNBT()
            .equals(ThaumcraftCapabilities.getKnowledge(original).serializeNBT()), "knowledge uses actual Entity NBT persistence");
        helper.assertTrue(ThaumcraftCapabilities.getWarp(restored).serializeNBT()
            .equals(ThaumcraftCapabilities.getWarp(original).serializeNBT()), "warp uses actual Entity NBT persistence");
        ThaumcraftCapabilities.getWarp(original).add(EnumWarpType.NORMAL, 50);
        helper.assertTrue(ThaumcraftCapabilities.getWarp(restored).get(EnumWarpType.NORMAL) == 11, "loaded warp array is independent");
        helper.assertTrue(saved.getCompound("neoforge:attachments").getCompound("thaumcraft:warp")
            .getIntArray("warp")[1] == 61, "raw serializer retains the documented source live-array behavior");
        helper.succeed();
    }

    private static void cloneTest(GameTestHelper helper, boolean death) {
        ServerPlayer original = player(helper, "TC_Data_Old");
        ServerPlayer replacement = player(helper, "TC_Data_New");
        fill(original);
        var originalKnowledge = ThaumcraftCapabilities.getKnowledge(original);
        originalKnowledge.addResearch("TC_PORT_ORPHAN");
        originalKnowledge.setResearchStage("TC_PORT_ORPHAN", 9);
        originalKnowledge.setResearchFlag("TC_PORT_ORPHAN", EnumResearchFlag.RESEARCH);
        originalKnowledge.removeResearch("TC_PORT_ORPHAN");
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, death));
        var copied = ThaumcraftCapabilities.getKnowledge(replacement);
        helper.assertTrue(copied != originalKnowledge, "NeoForge clone creates a new knowledge model");
        helper.assertTrue(copied.serializeNBT().equals(originalKnowledge.serializeNBT()), "clone follows source NBT roundtrip");
        copied.addResearch("TC_PORT_ORPHAN");
        helper.assertTrue(copied.getResearchStage("TC_PORT_ORPHAN") == 0, "orphan stage does not survive NBT clone");
        helper.assertFalse(copied.hasResearchFlag("TC_PORT_ORPHAN", EnumResearchFlag.RESEARCH), "orphan flag does not survive NBT clone");
        ThaumcraftCapabilities.getWarp(original).set(EnumWarpType.NORMAL, 100);
        helper.assertTrue(ThaumcraftCapabilities.getWarp(replacement).get(EnumWarpType.NORMAL) == 11, "warp copied independently");
        originalKnowledge.removeResearch("TC_PORT_STORAGE_KEY");
        helper.assertTrue(copied.isResearchKnown("TC_PORT_STORAGE_KEY"), "research collections copied independently");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deathCloneUsesRegisteredCopyOnDeath(GameTestHelper helper) {
        cloneTest(helper, true);
    }

    @GameTest(template = "empty")
    public static void nonDeathCloneUsesRegisteredSerializableCopy(GameTestHelper helper) {
        cloneTest(helper, false);
    }

    @GameTest(template = "empty")
    public static void payloadCodecsAndSnapshotsAreDetached(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Data_Codec");
        fill(owner);
        PacketSyncWarp warp = PacketSyncWarp.fromPlayer(owner);
        PacketSyncKnowledge knowledge = PacketSyncKnowledge.fromPlayer(owner);
        ThaumcraftCapabilities.getWarp(owner).add(EnumWarpType.NORMAL, 10);
        ThaumcraftCapabilities.getKnowledge(owner).removeResearch("TC_PORT_STORAGE_KEY");
        helper.assertTrue(warp.data().getIntArray("warp")[1] == 11, "network snapshot detaches the source live array");
        CompoundTag mutatedView = warp.data();
        mutatedView.getIntArray("warp")[1] = 500;
        helper.assertTrue(warp.data().getIntArray("warp")[1] == 11, "accessor cannot mutate the packet");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            PacketSyncKnowledge.STREAM_CODEC.encode(buffer, knowledge);
            PacketSyncWarp.STREAM_CODEC.encode(buffer, warp);
            helper.assertTrue(PacketSyncKnowledge.STREAM_CODEC.decode(buffer).data().equals(knowledge.data()), "knowledge codec roundtrip");
            helper.assertTrue(PacketSyncWarp.STREAM_CODEC.decode(buffer).data().equals(warp.data()), "warp codec roundtrip");
            helper.assertTrue(buffer.readableBytes() == 0, "codecs consume precisely both snapshots");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void popupSnapshotPrecedesOwnerAndClientCleanup(GameTestHelper helper) throws Exception {
        var originalCatalog = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        try {
            ResearchCategories.registerCategory("TC_PORT_POPUP_CAT", null, null, null, null);
            ResearchEntry entry = new ResearchEntry();
            entry.setCategory("TC_PORT_POPUP_CAT");
            entry.setKey("TC_PORT_POPUP");
            entry.setName("research.FIRSTSTEPS.title");
            entry.setStages(new ResearchStage[]{new ResearchStage()});
            ResearchCategories.registerResearch(entry);
            ServerPlayer owner = player(helper, "TC_Data_Owner");
            ServerPlayer bystander = player(helper, "TC_Data_Other");
            var serverKnowledge = ThaumcraftCapabilities.getKnowledge(owner);
            serverKnowledge.addResearch(entry.getKey());
            serverKnowledge.setResearchStage(entry.getKey(), 2);
            serverKnowledge.setResearchFlag(entry.getKey(), EnumResearchFlag.POPUP);
            serverKnowledge.setResearchFlag(entry.getKey(), EnumResearchFlag.PAGE);
            serverKnowledge.addResearch("TC_PORT_UNKNOWN_POPUP");
            serverKnowledge.setResearchFlag("TC_PORT_UNKNOWN_POPUP", EnumResearchFlag.POPUP);
            List<Delivery> deliveries = new ArrayList<>();
            try (var capture = PlayerDataSync.captureGameTestPackets((player, payload) -> deliveries.add(new Delivery(player, payload)))) {
                serverKnowledge.sync(owner);
            }
            helper.assertTrue(deliveries.size() == 1 && deliveries.getFirst().owner() == owner, "only owner is addressed");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(bystander).getResearchList().isEmpty(), "bystander data remains private");
            helper.assertFalse(serverKnowledge.hasResearchFlag(entry.getKey(), EnumResearchFlag.POPUP), "server clears POPUP after capture");
            helper.assertTrue(serverKnowledge.hasResearchFlag(entry.getKey(), EnumResearchFlag.PAGE), "server keeps other flags");
            PacketSyncKnowledge packet = (PacketSyncKnowledge) deliveries.getFirst().payload();
            PlayerKnowledge local = new PlayerKnowledge();
            List<String> notifications = PlayerDataSync.applyKnowledgeSnapshot(local, packet.data());
            helper.assertTrue(notifications.equals(List.of(entry.getKey())), "only known catalog POPUPs notify");
            helper.assertFalse(local.hasResearchFlag(entry.getKey(), EnumResearchFlag.POPUP), "local clears POPUP after reading snapshot");
            helper.assertFalse(local.hasResearchFlag("TC_PORT_UNKNOWN_POPUP", EnumResearchFlag.POPUP), "unknown catalog POPUPs still clear");
            helper.assertTrue(local.hasResearchFlag(entry.getKey(), EnumResearchFlag.PAGE), "local keeps PAGE");
        } finally {
            ResearchCategories.researchCategories = originalCatalog;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitAndTwentyTickKnowledgeSyncRemainDistinct(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Data_Ticks");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        knowledge.addResearch("TC_PORT_DIRTY");
        helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "raw mutators do not invent ResearchManager dirty scheduling");
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((player, payload) -> deliveries.add(new Delivery(player, payload)))) {
            PlayerDataSync.markKnowledgeDirty(owner);
            owner.tickCount = 19;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(deliveries.isEmpty(), "queued knowledge waits for tick modulo 20");
            knowledge.sync(owner);
            helper.assertTrue(deliveries.size() == 1 && PlayerDataSync.isKnowledgeDirty(owner), "explicit sync is immediate and retains queued update");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(deliveries.size() == 2 && !PlayerDataSync.isKnowledgeDirty(owner), "periodic sync consumes the queue");
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(deliveries.size() == 2, "same tick cannot send consumed update twice");
            ThaumcraftCapabilities.getWarp(owner).sync(owner);
            helper.assertTrue(deliveries.size() == 3 && deliveries.getLast().payload() instanceof PacketSyncWarp, "explicit warp sync is immediate");
        } finally {
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lifecycleEventsSendBothSnapshotsOnlyToTheirOwner(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Data_Events");
        ServerPlayer other = player(helper, "TC_Data_Events2");
        fill(owner);
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((player, payload) -> deliveries.add(new Delivery(player, payload)))) {
            // Exercise our handlers without firing third-party login listeners for a socketless FakePlayer.
            // Real event routing/login connections are verified by the separate two-client audit.
            PlayerDataEvents.login(new PlayerEvent.PlayerLoggedInEvent(owner));
            PlayerDataEvents.respawn(new PlayerEvent.PlayerRespawnEvent(owner, false));
            PlayerDataEvents.respawn(new PlayerEvent.PlayerRespawnEvent(owner, true));
            PlayerDataEvents.changedDimension(new PlayerEvent.PlayerChangedDimensionEvent(owner, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(deliveries.size() == 8, "login/death/End-return/dimension events send knowledge and warp");
            helper.assertTrue(deliveries.stream().allMatch(packet -> packet.owner() == owner), "no lifecycle broadcast or bystander delivery");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).getResearchList().isEmpty(), "unrelated player stays independent");
            PlayerDataSync.markKnowledgeDirty(owner);
            PlayerDataEvents.logout(new PlayerEvent.PlayerLoggedOutEvent(owner));
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "logout forgets a session's pending updates");
        } finally {
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }

    private record Delivery(ServerPlayer owner, CustomPacketPayload payload) {}
}
