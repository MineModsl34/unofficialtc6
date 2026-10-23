package thaumcraft.api.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.LegacyEnchantmentScanNames;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.research.LegacyVanillaScanIdentities;
import thaumcraft.common.lib.research.ScanEnchantment;
import thaumcraft.common.lib.research.ScanGeneric;
import thaumcraft.common.lib.research.ScanPotion;

@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyVanillaScanningGameTests {
    @GameTest(template = "empty")
    public static void declaredVanillaMappingsResolveToRealItemsAndGenericKeys(GameTestHelper h) {
        var generic = new ScanGeneric();
        for (var entry : LegacyVanillaScanIdentities.knownItems().entrySet()) {
            var item = BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElseThrow();
            h.assertFalse(item == Items.AIR, "mapped vanilla item must not fall back to AIR: " + entry.getKey());
            ItemStack stack = new ItemStack(item);
            h.assertTrue(generic.getResearchKey(null, stack).equals(LegacyScanIdentity.researchKey(entry.getValue(), stack.isDamageableItem())),
                "real stack retains original item id/meta " + entry.getKey());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exactAndWildcardScansRetainFlattenedFamilyAndPlacedVariantMetadata(GameTestHelper h) {
        var white = new ScanItem("TC_WHITE", new ItemStack(Items.WHITE_WOOL));
        var any = new ScanItem("TC_WOOL", ScanItem.wildcard(new ItemStack(Items.WHITE_WOOL)));
        h.assertTrue(white.checkThing(null, new ItemStack(Items.WHITE_WOOL, 64)), "count is not metadata");
        h.assertFalse(white.checkThing(null, new ItemStack(Items.RED_WOOL)), "red does not match old wool:0");
        h.assertTrue(any.checkThing(null, new ItemStack(Items.RED_WOOL)), "wildcard matches old shared wool item family");
        h.assertFalse(any.checkThing(null, new ItemStack(Items.RED_CONCRETE)), "same color is a different old item family");
        var owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Vanilla_Block"));
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlock(pos, Blocks.RED_WOOL.defaultBlockState(), 2);
        h.assertTrue(new ScanGeneric().getResearchKey(owner, pos).equals("!minecraft:wool14"), "ordinary placed variant preserves old item metadata");
        h.assertTrue(LegacyScanIdentity.blockResearchKey(Blocks.MELON).equals("!minecraft:melon"),
            "default block scanner does not accidentally apply the separate melon ITEM inverse");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualStartupEnchantmentHandlersPrecedeEffectsAndWriteOriginalMarker(GameTestHelper h) throws Exception {
        int generic = -1, firstEffect = -1;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < ScanningManager.things.size(); i++) {
            var scan = ScanningManager.things.get(i);
            if (scan instanceof ScanGeneric) generic = i;
            if (scan instanceof ScanPotion && firstEffect < 0) firstEffect = i;
            if (scan instanceof ScanEnchantment) {
                h.assertTrue(generic >= 0 && firstEffect < 0, "enchantment handler follows Generic and precedes effects");
                keys.add(scan.getResearchKey(null, null));
            }
        }
        var sourceKeys = LegacyEnchantmentScanNames.VANILLA.stream().map(LegacyEnchantmentScanNames.Descriptor::researchKey).toList();
        h.assertTrue(keys.size() >= sourceKeys.size() && keys.subList(0, sourceKeys.size()).equals(sourceKeys),
            "actual startup preserves thirty source enchantment names once in declared source order");
        h.assertTrue(keys.size() == h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).size()
            && new java.util.HashSet<>(keys).size() == keys.size(), "current dynamic enchantments are included once after the source handlers");
        var owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TC_Ench_Default"));
        var sharp = LegacyEnchantmentScanNames.VANILLA.get(11);
        var values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        values.set(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(sharp.target()), 3);
        var stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.ENCHANTMENTS, values.toImmutable());
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> h.assertTrue(recipient == owner, "only owner receives scan payloads"))) {
            ScanningManager.scanTheThing(owner, stack);
            h.assertTrue(data.isResearchComplete("!enchantment.damage.all") && data.getResearchStage("!enchantment.damage.all") == 0,
                "production default registration records the exact old marker");
            h.assertFalse(data.isResearchKnown("!enchantment.minecraft.sharpness"), "modern translation key never replaces old getName");
            var first = data.serializeNBT().copy();
            ScanningManager.scanTheThing(owner, stack);
            h.assertTrue(data.serializeNBT().equals(first), "default repeat scan cannot award twice");
        } finally { PlayerDataSync.forget(owner); }
        h.succeed();
    }
}
