package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LegacyScanIdentityTest {
    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }

    @Test
    void flattenedIngotsAndNuggetsRetainOldMetadata() {
        for (int meta = 0; meta < 3; meta++) {
            var value = LegacyScanIdentity.identify(id("thaumcraft:ingot_" + List.of("thaumium", "void", "brass").get(meta)), 0);
            assertEquals(id("thaumcraft:ingot"), value.itemId()); assertEquals(meta, value.metadata()); assertTrue(value.sourceProven());
        }
        assertEquals("!thaumcraft:nugget10", LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(id("thaumcraft:nugget_rareearth"), 0), false));
        assertEquals("!thaumcraft:cluster6", LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(id("thaumcraft:cluster_cinnabar"), 0), false));
        assertEquals("!thaumcraft:plate3", LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(id("thaumcraft:plate_void"), 0), false));
    }

    @Test
    void allSevenBaublesKeepOneOriginalItemIdentity() {
        var names = List.of("amulet_mundane", "ring_mundane", "girdle_mundane", "ring_apprentice", "amulet_fancy", "ring_fancy", "girdle_fancy");
        for (int meta = 0; meta < names.size(); meta++) {
            assertEquals("!thaumcraft:baubles" + meta,
                LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(id("thaumcraft:" + names.get(meta)), 0), false));
        }
    }

    @Test
    void durableKeysOmitDamageButUnbreakableKeysRetainIt() {
        var sword = LegacyScanIdentity.identify(id("thaumcraft:void_sword"), 23);
        assertEquals("!thaumcraft:void_sword", LegacyScanIdentity.researchKey(sword, true));
        assertEquals("!thaumcraft:void_sword23", LegacyScanIdentity.researchKey(sword, false));
        var pearl = LegacyScanIdentity.identify(id("thaumcraft:primordial_pearl"), 7);
        assertEquals(7, pearl.metadata());
        assertEquals("!thaumcraft:primordial_pearl", LegacyScanIdentity.researchKey(pearl, true));
    }

    @Test
    void renamedCandlesUseOriginalTranslationKeyDerivedIds() {
        assertEquals("!thaumcraft:candle_lightblue0", LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(id("thaumcraft:candle_light_blue"), 0), false));
        assertEquals("!thaumcraft:candle_silver0", LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(id("thaumcraft:candle_light_gray"), 0), false));
        assertEquals(id("thaumcraft:crystal_aer"), LegacyScanIdentity.identify(id("thaumcraft:crystal_aer"), 0).itemId());
    }

    @Test
    void normalBookKeepsSourceItemIdentityWithoutInventingCheatVariant() {
        var normal = LegacyScanIdentity.identify(id("thaumcraft:thaumonomicon"), 0);
        assertTrue(normal.sourceProven());
        assertEquals(id("thaumcraft:thaumonomicon"), normal.itemId());
        assertEquals(0, normal.metadata());
        assertEquals("!thaumcraft:thaumonomicon0", LegacyScanIdentity.researchKey(normal, false));
        assertFalse(LegacyScanIdentity.identify(id("thaumcraft:thaumonomicon_cheat"), 0).sourceProven());
    }

    @Test
    void forgeEntityNamesAreNotModernRegistryIds() {
        assertEquals("Zombie", LegacyScanIdentity.entityName(id("minecraft:zombie")));
        assertEquals("PigZombie", LegacyScanIdentity.entityName(id("minecraft:zombified_piglin")));
        assertEquals("WitherBoss", LegacyScanIdentity.entityName(id("minecraft:wither")));
        assertEquals("ThrownEnderpearl", LegacyScanIdentity.entityName(id("minecraft:ender_pearl")));
        assertEquals("minecraft:allay", LegacyScanIdentity.entityName(id("minecraft:allay")));
    }

    @Test
    void stageSevenEquipmentKeepsSourceDamageAndVariantIdentities() {
        for (String name : List.of("elemental_sword", "primal_crusher", "goggles", "traveller_boots", "fortress_helm", "void_robe_chest", "crimson_praetor_legs", "grapple_gun")) {
            var value = LegacyScanIdentity.identify(id("thaumcraft:" + name), 17);
            assertTrue(value.sourceProven(), name);
            assertEquals("!thaumcraft:" + name, LegacyScanIdentity.researchKey(value, true));
        }
        var amulet = LegacyScanIdentity.identify(id("thaumcraft:amulet_vis_crafted"), 0);
        assertEquals(id("thaumcraft:amulet_vis"), amulet.itemId());
        assertEquals(1, amulet.metadata());
        var bags = List.of("common", "uncommon", "rare");
        for (int meta = 0; meta < bags.size(); meta++) {
            var bag = LegacyScanIdentity.identify(id("thaumcraft:loot_bag_" + bags.get(meta)), 0);
            assertTrue(bag.sourceProven());
            assertEquals(id("thaumcraft:loot_bag"), bag.itemId());
            assertEquals(meta, bag.metadata());
        }
        for (String name : List.of("focus_1", "focus_2", "focus_3", "focus_pouch", "wand_workbench"))
            assertTrue(LegacyScanIdentity.identify(id("thaumcraft:" + name), 0).sourceProven(), name);
    }

    @Test
    void modernFallbackDoesNotPretendToBeSourceProven() {
        var value = LegacyScanIdentity.identify(id("minecraft:netherite_ingot"), 0);
        assertFalse(value.sourceProven());
        assertEquals("!minecraft:netherite_ingot0", LegacyScanIdentity.researchKey(value, false));
        assertFalse(LegacyScanIdentity.identify(id("thaumcraft:future_item"), 0).sourceProven());
        assertThrows(UnsupportedOperationException.class, () -> LegacyScanIdentity.knownItems().clear());
    }
    @Test
    void stageEightConstructsAndEntitiesKeepSourceScanIdentities() {
        var placers = List.of("basic", "advanced", "bore");
        for (int meta = 0; meta < placers.size(); meta++) {
            var item = LegacyScanIdentity.identify(id("thaumcraft:turret_" + placers.get(meta)), 0);
            assertTrue(item.sourceProven());
            assertEquals("!thaumcraft:turret" + meta, LegacyScanIdentity.researchKey(item, false));
        }
        assertEquals("Pech", LegacyScanIdentity.entityName(id("thaumcraft:pech")));
        assertEquals("FollowItem", LegacyScanIdentity.entityName(id("thaumcraft:following_item")));
        assertEquals("Focusmine", LegacyScanIdentity.entityName(id("thaumcraft:focus_mine")));
        assertEquals("TaintacleTiny", LegacyScanIdentity.entityName(id("thaumcraft:taintacle_small")));
        assertEquals("thaumcraft:homing_shard", LegacyScanIdentity.entityName(id("thaumcraft:homing_shard")));
        assertTrue(LegacyScanIdentity.identify(id("thaumcraft:bottle_taint"), 0).sourceProven());
    }

    @Test
    void stageNineSealMetadataRetainsActualSourceRegistrationOrder() {
        var names = List.of("blank", "pickup", "pickup_advanced", "fill", "fill_advanced",
            "empty", "empty_advanced", "harvest", "butcher", "guard", "guard_advanced",
            "lumber", "breaker", "use", "provider", "stock", "breaker_advanced");
        for (int meta = 0; meta < names.size(); meta++) {
            var value = LegacyScanIdentity.identify(id("thaumcraft:seal_" + names.get(meta)), 0);
            assertTrue(value.sourceProven(), names.get(meta));
            assertEquals(id("thaumcraft:seal"), value.itemId());
            assertEquals(meta, value.metadata());
            assertEquals("!thaumcraft:seal" + meta, LegacyScanIdentity.researchKey(value, false));
        }
        assertFalse(LegacyScanIdentity.identify(id("thaumcraft:seal_provide"), 0).sourceProven());
        assertEquals("Golem", LegacyScanIdentity.entityName(id("thaumcraft:golem")));
        for (String name : List.of("golem", "golem_bell", "golem_builder", "placeholder_bars",
                "placeholder_cauldron", "placeholder_anvil", "placeholder_table"))
            assertTrue(LegacyScanIdentity.identify(id("thaumcraft:" + name), 0).sourceProven(), name);
    }

    @Test
    void stageTenRiftAndItemsKeepSourceIdentitiesWithoutAnInventedFluidBlockItem() {
        assertEquals("FluxRift", LegacyScanIdentity.entityName(id("thaumcraft:flux_rift")));
        for (String name : List.of("void_siphon", "liquid_death", "sanity_soap", "creative_flux_sponge")) {
            var value = LegacyScanIdentity.identify(id("thaumcraft:" + name), 0);
            assertTrue(value.sourceProven(), name);
            assertEquals("!thaumcraft:" + name + "0", LegacyScanIdentity.researchKey(value, false));
        }
        var bucket = LegacyScanIdentity.identify(id("thaumcraft:liquid_death_bucket"), 0);
        assertEquals("forge:bucketfilled", bucket.itemId().toString());
        assertEquals(0, bucket.metadata());
        assertTrue(bucket.sourceProven());
    }
}
