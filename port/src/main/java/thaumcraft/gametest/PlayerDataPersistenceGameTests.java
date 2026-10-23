package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.PlayerDataAttachmentPersistence;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.events.PlayerDataEvents;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/**
 * Actual Entity NBT and registered NeoForge clone tests for the modern corruption-policy boundary.
 * Unspawned FakePlayer disconnect is an engine no-op; zero S2C captures prove the login sync gate,
 * not a real TCP disconnect. Valid/source storage and executed-original baselines are separate.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerDataPersistenceGameTests {
    private PlayerDataPersistenceGameTests() {}

    @GameTest(template = "empty")
    public static void corruptEntityKnowledgePreservesFullRawNbtAndPartialModel(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper, "TC_Bad_Load");
        CompoundTag corrupt = corruptKnowledge();
        CompoundTag expected = corrupt.copy();
        loadAttachments(player, corrupt, validWarp());
        var partial = ThaumcraftCapabilities.getKnowledge(player);
        helper.assertTrue(partial.isResearchKnown("TC_SAFE_BEFORE_CORRUPT"), "source partial state before native failure is retained");
        helper.assertTrue(partial.getResearchStage("TC_SAFE_BEFORE_CORRUPT") == 2, "source partial stage remains available");
        helper.assertTrue(partial.hasResearchFlag("TC_SAFE_BEFORE_CORRUPT", EnumResearchFlag.PAGE), "source partial flags remain available");
        helper.assertFalse(partial.isResearchKnown("TC_AFTER_CORRUPT"), "loading stops at the native bare-@ error");
        helper.assertTrue(PlayerDataAttachmentPersistence.isQuarantined(player), "failed actual attachment load is quarantined, not skipped");
        helper.assertFalse(PlayerDataAttachmentPersistence.allowsLogin(player), "partial knowledge cannot authorize login sync");
        helper.assertTrue(savedAttachment(player, "knowledge").equals(expected), "entire raw tag survives actual Entity save, including unknown fields and unparsed tail");
        corrupt.putString("extension", "incoming tag changed after load");
        corrupt.getList("research", Tag.TAG_COMPOUND).getCompound(0).putInt("stage", 99);
        partial.clear();
        partial.addResearch("TC_PARTIAL_MODEL_EDIT");
        helper.assertTrue(savedAttachment(player, "knowledge").equals(expected), "input alias and later partial-model mutations cannot overwrite quarantined raw data");
        helper.assertFalse(PlayerDataAttachmentPersistence.allowsLogin(player), "clear/add is not an implicit administrative recovery");
        helper.assertTrue(ThaumcraftCapabilities.getWarp(player).get(EnumWarpType.NORMAL) == 701, "other valid attachment still loads without clamping");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void corruptKnowledgeSurvivesDeathAndEndClone(GameTestHelper helper) throws Exception {
        ServerPlayer original = player(helper, "TC_Bad_Clone");
        CompoundTag corrupt = corruptKnowledge();
        loadAttachments(original, corrupt, validWarp());
        for (boolean death : new boolean[]{true, false}) {
            ServerPlayer replacement = player(helper, death ? "TC_Bad_Death" : "TC_Bad_End");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, death));
            helper.assertTrue(PlayerDataAttachmentPersistence.isQuarantined(replacement), "registered default copy handler propagates quarantine: death=" + death);
            helper.assertFalse(PlayerDataAttachmentPersistence.allowsLogin(replacement), "clone cannot accidentally release corrupt data: death=" + death);
            helper.assertTrue(savedAttachment(replacement, "knowledge").equals(corrupt), "clone copies ALL original corrupt NBT: death=" + death);
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(replacement) != ThaumcraftCapabilities.getKnowledge(original), "clone creates independent models");
            Tag saved = savedAttachment(replacement, "knowledge");
            ((CompoundTag) saved).putString("extension", "external saved tag mutated");
            helper.assertTrue(savedAttachment(replacement, "knowledge").equals(corrupt), "quarantine writes are detached snapshots");
            ThaumcraftCapabilities.getWarp(replacement).set(EnumWarpType.NORMAL, 4);
            helper.assertTrue(ThaumcraftCapabilities.getWarp(original).get(EnumWarpType.NORMAL) == 701, "valid warp clone remains independent");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quarantinedLoginEmitsNoPersonalDataPackets(GameTestHelper helper) throws Exception {
        ServerPlayer corruptOwner = player(helper, "TC_Bad_Login");
        ServerPlayer validOwner = player(helper, "TC_Good_Login");
        CompoundTag raw = corruptKnowledge();
        loadAttachments(corruptOwner, raw, validWarp());
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((owner, payload) -> deliveries.add(new Delivery(owner, payload)))) {
            helper.assertFalse(PlayerDataAttachmentPersistence.allowsLogin(corruptOwner), "side-effect-free fail-closed predicate");
            // Exercise the real production handler directly: unrelated third-party login listeners
            // require a connected Netty channel. This is a socketless gate, not bus-route evidence.
            PlayerDataEvents.login(new PlayerEvent.PlayerLoggedInEvent(corruptOwner));
            helper.assertTrue(deliveries.isEmpty(), "production login handler returns before both source packet constructors/sends");
            helper.assertTrue(PlayerDataAttachmentPersistence.allowsLogin(validOwner), "fresh valid owner remains authorized");
            PlayerDataEvents.login(new PlayerEvent.PlayerLoggedInEvent(validOwner));
            helper.assertTrue(deliveries.size() == 2 && deliveries.stream().allMatch(value -> value.owner == validOwner), "valid login still sends only its owner's knowledge and warp");
            helper.assertTrue(deliveries.get(0).payload instanceof PacketSyncKnowledge
                && deliveries.get(1).payload instanceof PacketSyncWarp, "valid source snapshot ordering is unchanged");
        }
        helper.assertTrue(savedAttachment(corruptOwner, "knowledge").equals(raw), "rejected login does not replace raw data with the partial/empty model");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validSourceLoadsAndPublicNativeExceptionsAreUnchanged(GameTestHelper helper) throws Exception {
        ServerPlayer valid = player(helper, "TC_Good_Load");
        CompoundTag sourceKnowledge = TagParser.parseTag("{research:[{key:'TC_VALID_KEY',stage:3,flags:'PAGE'}],knowledge:[{key:'T_',amount:32}]}");
        CompoundTag sourceWarp = validWarp();
        loadAttachments(valid, sourceKnowledge, sourceWarp);
        helper.assertTrue(PlayerDataAttachmentPersistence.allowsLogin(valid), "valid negative/large warp values are not corruption");
        helper.assertFalse(PlayerDataAttachmentPersistence.isQuarantined(valid), "valid data creates no quarantine metadata");
        helper.assertTrue(savedAttachment(valid, "knowledge").equals(sourceKnowledge), "valid knowledge retains original format");
        Tag savedWarp = savedAttachment(valid, "warp");
        helper.assertTrue(savedWarp.equals(sourceWarp), "valid warp remains unclamped on load");
        ThaumcraftCapabilities.getWarp(valid).set(EnumWarpType.NORMAL, 30);
        helper.assertTrue(((CompoundTag) savedWarp).getIntArray("warp")[1] == 30, "valid entity serialization retains the source raw-array alias");
        PlayerKnowledge direct = new PlayerKnowledge();
        boolean nativeFailure = false;
        try {
            direct.deserializeNBT(corruptKnowledge());
        } catch (ArrayIndexOutOfBoundsException expected) {
            nativeFailure = true;
        }
        helper.assertTrue(nativeFailure && direct.isResearchKnown("TC_SAFE_BEFORE_CORRUPT"), "public deserialize still throws native exception with source partial state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongAttachmentRootTypesArePreservedNotDiscarded(GameTestHelper helper) {
        ServerPlayer player = player(helper, "TC_Bad_Types");
        StringTag knowledge = StringTag.valueOf("opaque incorrect knowledge root");
        ListTag warp = new ListTag();
        warp.add(StringTag.valueOf("opaque incorrect warp root"));
        loadAttachments(player, knowledge, warp);
        helper.assertFalse(PlayerDataAttachmentPersistence.allowsLogin(player), "incorrect root types fail closed");
        helper.assertTrue(savedAttachment(player, "knowledge").equals(knowledge), "incorrect knowledge tag type/value is retained exactly");
        helper.assertTrue(savedAttachment(player, "warp").equals(warp), "incorrect warp tag type/value is retained exactly");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static CompoundTag corruptKnowledge() throws Exception {
        return TagParser.parseTag("{research:[{key:'TC_SAFE_BEFORE_CORRUPT',stage:2,flags:'PAGE'},"
            + "{key:'@'},{key:'TC_AFTER_CORRUPT',stage:7,flags:'POPUP'}],"
            + "knowledge:[{key:'unparsed_knowledge_tail',amount:123}],"
            + "extension:{opaque:'preserve unknown fields',array:[I;1,-2,3],list:[{x:1},{x:2}]}}");
    }

    private static CompoundTag validWarp() {
        CompoundTag warp = new CompoundTag();
        warp.putIntArray("warp", new int[]{-9, 701, 3});
        warp.putInt("counter", -123);
        return warp;
    }

    private static void loadAttachments(ServerPlayer player, Tag knowledge, Tag warp) {
        CompoundTag entity = player.saveWithoutId(new CompoundTag());
        CompoundTag attachments = new CompoundTag();
        attachments.put("thaumcraft:knowledge", knowledge);
        attachments.put("thaumcraft:warp", warp);
        entity.put("neoforge:attachments", attachments);
        player.load(entity);
    }

    private static Tag savedAttachment(ServerPlayer player, String key) {
        Tag data = player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments").get("thaumcraft:" + key);
        if (data == null) throw new AssertionError("Actual Entity save dropped " + key);
        return data;
    }

    private record Delivery(ServerPlayer owner, Object payload) {}
}
