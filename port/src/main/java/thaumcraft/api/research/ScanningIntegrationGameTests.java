package thaumcraft.api.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.common.entities.CultEntities;
import thaumcraft.common.entities.WarpEntities;

/** Actual startup registrations and item callback; scanner/catalog lists are not replaced. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ScanningIntegrationGameTests {
    @GameTest(template = "scan_test")
    public static void actualThaumometerUseScansDefaultOreOnceAndFlushesOwnerKnowledge(GameTestHelper h) throws Exception {
        ServerPlayer owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Default_Scan"));
        var position = h.absolutePos(new BlockPos(2, 2, 1));
        owner.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        owner.setYRot(0);
        owner.setXRot(0);
        BlockPos ore = h.absolutePos(new BlockPos(2, 3, 4));
        h.getLevel().setBlock(ore, BlocksTC.oreCinnabar.defaultBlockState(), 2);
        h.assertTrue(ore.equals(ScanTargeting.scanTarget(owner)), "real world ray selects the intended ore, not a fixture artifact");
        ItemStack tool = new ItemStack(ItemsTC.thaumometer);
        owner.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var aspects = AspectHelper.getObjectAspects(new ItemStack(BlocksTC.oreCinnabar));
        h.assertTrue(aspects != null && aspects.size() > 0, "source ore aspect table exists");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        int xp = owner.totalExperience;
        boolean previousNoFlags = ResearchManager.noFlags;
        ResearchManager.noFlags = false;
        record Delivery(ServerPlayer owner, CustomPacketPayload payload) {}
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> deliveries.add(new Delivery(recipient, payload)))) {
            var result = ItemsTC.thaumometer.use(h.getLevel(), owner, InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult().consumesAction() && result.getObject() == tool, "actual registered Item.use returns success and held stack");
            h.assertTrue(data.isResearchComplete("ORE") && data.getResearchStage("ORE") == 2,
                "default ore handler completes actual simple catalog entry");
            h.assertTrue(data.isResearchKnown("!ORECINNABAR") && data.isResearchKnown("!thaumcraft:ore_cinnabar0"),
                "specific and source generic item markers both recorded");
            int positiveAspects = 0;
            for (Aspect aspect : aspects.getAspects()) {
                if (aspects.getAmount(aspect) > 0) {
                    positiveAspects++;
                    h.assertTrue(data.isResearchKnown("!" + aspect.getTag()), "constructor-registered aspect scanner ran: " + aspect.getTag());
                }
            }
            for (var category : ResearchCategories.researchCategories.values()) {
                int extra = List.of("AUROMANCY", "BASICS", "ALCHEMY").contains(category.key) ? positiveAspects : 0;
                h.assertTrue(data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == extra + category.applyFormula(aspects),
                    "actual default aspect+generic raw award for " + category.key);
            }
            h.assertTrue(owner.totalExperience == xp + 5, "only actual simple ORE entry adds five XP");
            var first = data.serializeNBT().copy();
            int firstPackets = deliveries.size();
            ItemsTC.thaumometer.use(h.getLevel(), owner, InteractionHand.MAIN_HAND);
            h.assertTrue(data.serializeNBT().equals(first) && owner.totalExperience == xp + 5 && deliveries.size() == firstPackets,
                "second actual use grants no duplicate raw knowledge, marker, XP or feedback");
            h.assertTrue(deliveries.stream().allMatch(d -> d.owner() == owner && d.payload() instanceof PacketKnowledgeGain),
                "immediate feedback addressed only to scan owner");
            owner.tickCount = 19;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            h.assertTrue(deliveries.size() == firstPackets, "no off-cadence snapshot");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            h.assertTrue(deliveries.size() == firstPackets + 1 && deliveries.getLast().owner() == owner
                && deliveries.getLast().payload() instanceof PacketSyncKnowledge, "actual dirty snapshot follows on twenty-tick cadence");
            var toolAspects = AspectHelper.getObjectAspects(tool);
            h.assertTrue(toolAspects != null && toolAspects.getAmount(Aspect.SENSES) >= 10 && toolAspects.getAmount(Aspect.AURA) >= 10,
                "thaumometer retains original explicit aspect supplement; full recipe-derived total is not claimed");
        } finally { ResearchManager.noFlags = previousNoFlags; PlayerDataSync.forget(owner); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lateAddonAspectRegistersWithoutReinitializingScanners(GameTestHelper h) {
        List<IScanThing> previous = new ArrayList<>(ScanningManager.things);
        String tag = "tc4c_late_test";
        h.assertTrue(!Aspect.aspects.containsKey(tag), "fixture uses a fresh addon aspect");
        try {
            Aspect added = new Aspect(tag, 0xAA55CC, (Aspect[]) null);
            h.assertTrue(ScanningManager.things.size() == previous.size() + 1,
                "constructor appends exactly one handler after production initialization");
            var handler = ScanningManager.things.getLast();
            h.assertTrue(handler instanceof ScanAspect && ((ScanAspect) handler).aspect == added
                && handler.getResearchKey(null, null).equals("!" + tag), "source late registration uses exact aspect and opaque key");
            for (int i = 0; i < previous.size(); i++) h.assertTrue(ScanningManager.things.get(i) == previous.get(i),
                "addon registration leaves existing handler identities/order unchanged");
        } finally {
            Aspect.aspects.remove(tag);
            ScanningManager.things.clear();
            ScanningManager.things.addAll(previous);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultProjectileScanFamiliesPreserveLegacySubclassesWithoutNewContent(GameTestHelper h) {
        for (var type : List.of(EntityType.ARROW, EntityType.SPECTRAL_ARROW)) {
            h.assertTrue(matching(h, type, "f_arrow") == 1, "exactly one old arrow marker handler for " + type);
        }
        h.assertTrue(matching(h, EntityType.TRIDENT, "f_arrow") == 0, "new trident is not an old EntityArrow");
        for (var type : List.of(EntityType.FIREBALL, EntityType.SMALL_FIREBALL, EntityType.DRAGON_FIREBALL, EntityType.WITHER_SKULL)) {
            h.assertTrue(matching(h, type, "f_fireball") == 1, "exactly one old fireball marker handler for " + type);
        }
        h.assertTrue(matching(h, EntityType.WIND_CHARGE, "f_fireball") == 0
            && matching(h, EntityType.BREEZE_WIND_CHARGE, "f_fireball") == 0, "new wind charges are not old fireballs");
        h.succeed();
    }

    private static int matching(GameTestHelper h, EntityType<?> type, String key) {
        Entity entity = type.create(h.getLevel());
        if (entity == null) throw new AssertionError("Missing fixture entity " + type);
        int count = 0;
        for (var scanner : ScanningManager.things) {
            if (scanner instanceof ScanEntity && scanner.checkThing(null, entity) && key.equals(scanner.getResearchKey(null, entity))) count++;
        }
        return count;
    }

    @GameTest(template = "empty")
    public static void clayFlatteningDoesNotExpandOldMetadataZeroItemMatches(GameTestHelper h) {
        var owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Clay_Markers"));
        for (var item : List.of(Items.CLAY_BALL, Items.TERRACOTTA, Items.WHITE_TERRACOTTA)) {
            h.assertTrue(materialMatches(owner, new ItemStack(item)) == 1, "original registered clay item matches " + item);
        }
        h.assertTrue(materialMatches(owner, new ItemStack(Items.RED_TERRACOTTA)) == 0,
            "old default meta-zero descriptor never matched colored terracotta items");
        h.assertTrue(materialMatches(owner, new ItemStack(Items.CLAY)) == 0,
            "old Material.CLAY handler was block-only, not a clay-block item descriptor");
        BlockPos position = h.absolutePos(new BlockPos(1, 1, 1));
        for (var block : List.of(Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.CLAY)) {
            h.getLevel().setBlock(position, block.defaultBlockState(), 2);
            h.assertTrue(materialMatches(owner, position) == 1, "placed original clay/terracotta block still matches " + block);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBrainScanAndAlreadyPortedCreatureMarkersKeepExactSourceFamilies(GameTestHelper h) throws Exception {
        ServerPlayer owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Current_Markers"));
        ServerPlayer peer = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Current_Peer"));
        var peerBefore = ThaumcraftCapabilities.getKnowledge(peer).serializeNBT().copy();
        ItemStack brain = new ItemStack(ItemsTC.brain);
        h.assertTrue(markerMatches(owner, brain, "f_BRAIN") == 1
            && markerMatches(owner, new ItemStack(Items.ROTTEN_FLESH), "f_BRAIN") == 0,
            "actual source brain item descriptor exists without broadening to other flesh");
        var knight = CultEntities.KNIGHT.create(h.getLevel());
        var cleric = CultEntities.CLERIC.create(h.getLevel());
        var portal = CultEntities.PORTAL.create(h.getLevel());
        var orb = CultEntities.GOLEM_ORB.create(h.getLevel());
        var guardian = WarpEntities.ELDRITCH_GUARDIAN.create(h.getLevel());
        var spider = WarpEntities.MIND_SPIDER.create(h.getLevel());
        var slime = WarpEntities.THAUMIC_SLIME.create(h.getLevel());
        h.assertTrue(knight != null && cleric != null && portal != null && orb != null
            && guardian != null && spider != null && slime != null, "actual registered stage-four dependency entity constructors");
        h.assertTrue(markerMatches(owner, knight, "!CrimsonCultist") == 1
            && markerMatches(owner, cleric, "!CrimsonCultist") == 1
            && markerMatches(owner, portal, "!CrimsonCultist") == 0
            && markerMatches(owner, orb, "!CrimsonCultist") == 0, "source EntityCultist subclasses only, never portal or golem projectile");
        h.assertTrue(markerMatches(owner, guardian, "!EldritchGuardian") == 1
            && markerMatches(owner, guardian, "!ORMOB") == 1
            && markerMatches(owner, spider, "!ORMOB") == 1
            && markerMatches(owner, spider, "!EldritchGuardian") == 0
            && markerMatches(owner, knight, "!ORMOB") == 0, "exact guardian class and source IEldritchMob marker families");
        h.assertTrue(markerMatches(owner, slime, "!ThaumSlime") == 1
            && markerMatches(owner, EntityType.SLIME.create(h.getLevel()), "!ThaumSlime") == 0
            && markerMatches(owner, EntityType.ZOMBIE.create(h.getLevel()), "f_BRAIN") == 0,
            "ThaumicSlime scan preserved, no vanilla replacements for missing TC entities");
        List<CustomPacketPayload> packets = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            h.assertTrue(recipient == owner, "real item scanner grants/flushes to owner, not the peer");
            packets.add(packet);
        })) {
            ScanningManager.scanTheThing(owner, brain);
            h.assertTrue(ThaumcraftCapabilities.knowsResearch(owner, "f_BRAIN"), "real startup scanner actually progresses brain marker");
            var accepted = ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().copy();
            int firstPackets = packets.size();
            ScanningManager.scanTheThing(owner, brain);
            h.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(accepted)
                && packets.size() == firstPackets, "repeat item scan does not regrant knowledge or feedback");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            h.assertTrue(!packets.isEmpty() && packets.getLast() instanceof PacketSyncKnowledge
                && ThaumcraftCapabilities.getKnowledge(peer).serializeNBT().equals(peerBefore), "real queued owner snapshot, peer unchanged");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(peer); }
        h.succeed();
    }

    private static int markerMatches(ServerPlayer player, Object object, String key) {
        int count = 0;
        for (var scanner : ScanningManager.things)
            if ((scanner instanceof ScanEntity || scanner instanceof ScanItem) && scanner.checkThing(player, object)
                && key.equals(scanner.getResearchKey(player, object))) count++;
        return count;
    }

    private static int materialMatches(ServerPlayer player, Object object) {
        int count = 0;
        for (var scanner : ScanningManager.things) {
            if ("f_MATCLAY".equals(scanner.getResearchKey(player, object)) && scanner.checkThing(player, object)) count++;
        }
        return count;
    }
}
