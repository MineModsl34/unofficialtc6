package thaumcraft.api.research;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.research.LegacySkyScanGeometry;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.ScanTargeting;

/** Production registrations/use with explicit complete-research fixture, NOT a play-through. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class CelestialScanningIntegrationGameTests {
    @GameTest(template = "empty")
    public static void allNotesKeepOriginalAspectValuesGenericMetadataAndWildcardFamily(GameTestHelper h) {
        var wildcard = new ScanItem("TC_NOTE_FAMILY", ScanItem.wildcard(new ItemStack(ItemsTC.celestialNotes)));
        var sun = new ScanItem("TC_SUN_ONLY", new ItemStack(ItemsTC.celestialNotes));
        for (int meta = 0; meta < 13; meta++) {
            var stack = new ItemStack(ItemsTC.celestialNotesVariants[meta]);
            var aspects = AspectHelper.getObjectAspects(stack);
            h.assertTrue(aspects != null && aspects.size() == 3 && aspects.getAmount(Aspect.MIND) == 5
                && aspects.getAmount(Aspect.DARKNESS) == 5 && aspects.getAmount(Aspect.LIGHT) == 5,
                "each note retains the original wildcard aspect registration");
            h.assertTrue(LegacyScanIdentity.itemResearchKey(stack).equals("!thaumcraft:celestial_notes" + meta), "original generic note meta key");
            h.assertTrue(wildcard.checkThing(null, stack), "source wildcard matches every flattened note");
            h.assertTrue(sun.checkThing(null, stack) == (meta == 0), "ordinary sun descriptor does not accept stars/moons");
        }
        var tool = new ItemStack(ItemsTC.scribingTools);
        tool.setDamageValue(99);
        h.assertTrue(LegacyScanIdentity.itemResearchKey(tool).equals("!thaumcraft:scribing_tools"), "damageable source tool key omits wear");
        h.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void actualThaumometerUseRetainsStrictGateThenAwardsSunWithoutInkDamage(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        long previousDayTime = level.getDayTime();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TC_Sky_Item_Use"));
        var pos = h.absolutePos(new BlockPos(2, 4, 2));
        owner.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        owner.setYRot(-90);
        owner.setXRot(-90);
        var tool = new ItemStack(ItemsTC.thaumometer);
        var ink = new ItemStack(ItemsTC.scribingTools);
        ink.setDamageValue(62);
        owner.setItemInHand(InteractionHand.MAIN_HAND, tool);
        owner.getInventory().items.set(1, ink);
        owner.getInventory().items.set(2, new ItemStack(Items.PAPER, 2));
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> h.assertTrue(recipient == owner, "scan payload addresses owner only"))) {
            level.setDayTime(6000);
            h.assertTrue(ScanTargeting.scanTarget(owner) == null, "real upward item ray misses terrain/entities");
            h.assertFalse(ResearchManager.progressResearch(owner, "CELESTIALSCANNING"), "complex prerequisite is not automatically unlocked by the narrow manager");
            ItemsTC.thaumometer.use(level, owner, InteractionHand.MAIN_HAND);
            h.assertTrue(owner.getInventory().getItem(2).getCount() == 2 && countNotes(owner) == 0, "locked research consumes and awards nothing");
            // Explicit fixture only: the real catalog requirement is not weakened or replaced.
            data.addResearch("CELESTIALSCANNING");
            data.setResearchStage("CELESTIALSCANNING", 2);
            h.assertTrue(ThaumcraftCapabilities.knowsResearchStrict(owner, "CELESTIALSCANNING"), "fixture has actually complete source one-stage entry");
            ItemsTC.thaumometer.use(level, owner, InteractionHand.MAIN_HAND);
            String key = LegacySkyScanGeometry.dayPrefix(level.getGameTime()) + "Sun";
            h.assertTrue(data.isResearchComplete(key) && data.getResearchStage(key) == 0, "actual item/default scanner records the original daily marker");
            h.assertTrue(owner.getInventory().getItem(2).getCount() == 1 && countNotes(owner) == 1, "one source paper produces one sun note");
            h.assertTrue(ink.getDamageValue() == 62 && ink.getCount() == 1, "celestial scanning never consumes ink/tool durability");
            ItemsTC.thaumometer.use(level, owner, InteractionHand.MAIN_HAND);
            h.assertTrue(owner.getInventory().getItem(2).getCount() == 1 && countNotes(owner) == 1, "same daily sun is once-only");
        } finally {
            level.setDayTime(previousDayTime);
            PlayerDataSync.forget(owner);
        }
        h.succeed();
    }

    private static int countNotes(net.minecraft.server.level.ServerPlayer owner) {
        int count = 0;
        for (var stack : owner.getInventory().items) if (stack.is(ItemsTC.celestialNotes)) count += stack.getCount();
        return count;
    }
}
