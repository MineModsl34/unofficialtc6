package thaumcraft.common.world.structures;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import thaumcraft.common.world.structures.TCSurfaceAttemptRules.Completion;
import thaumcraft.common.world.structures.TCSurfaceAttemptRules.Kind;

/** No MC bootstrap, world, entity, TYPE/CODEC, Java game runtime or registry lookup. */
class TCSurfaceAttemptRulesTest {
    @Test void moundSuccessDoesNotDrawForPortal() {
        List<String> order = new ArrayList<>();
        var result = TCSurfaceAttemptRules.roll(script(order, 0, 8, 0), -2, 3, (x, z) -> {
            order.add("height:" + x + ":" + z); return 64;
        }, 320);
        assertEquals(Kind.MOUND, result.kind());
        assertEquals(-28, result.x()); assertEquals(60, result.z()); assertEquals(55, result.originY());
        assertEquals(List.of("int:9", "int:9", "height:-28:60", "int:100"), order);
    }

    @Test void portalIsSourceElseIfAndUsesSurfaceMinusOneSupport() {
        List<String> order = new ArrayList<>();
        var result = TCSurfaceAttemptRules.roll(script(order, 4, 4, 1, 0), 0, 0, (x, z) -> {
            order.add("height:" + x + ":" + z); return 64;
        }, 320);
        assertEquals(Kind.PORTAL, result.kind());
        assertEquals(8, result.x()); assertEquals(8, result.z());
        assertEquals(63, result.supportY()); assertEquals(64, result.entityY());
        assertEquals(List.of("int:9", "int:9", "height:8:8", "int:100", "int:500"), order);
    }

    @Test void failedPortalIsNotAnotherMoundAttempt() {
        List<String> order = new ArrayList<>();
        var result = TCSurfaceAttemptRules.roll(script(order, 8, 0, 99, 499), 2, -1, (x, z) -> 90, 320);
        assertEquals(Kind.NONE, result.kind());
        assertEquals(44, result.x()); assertEquals(-12, result.z());
        assertEquals(List.of("int:9", "int:9", "int:100", "int:500"), order);
    }

    @Test void sourceMaxOriginGuardRunsBeforeEitherChance() {
        List<String> order = new ArrayList<>();
        var result = TCSurfaceAttemptRules.roll(script(order, 4, 4), 0, 0, (x, z) -> 329, 320);
        assertEquals(Kind.NONE, result.kind());
        assertEquals(List.of("int:9", "int:9"), order);
        // Source compares precipitationHeight-9, not the eventual entity y (native placement bounds remain required).
        assertEquals(Kind.PORTAL, TCSurfaceAttemptRules.roll(script(new ArrayList<>(), 4, 4, 1, 0), 0, 0,
            (x, z) -> 328, 320).kind());
    }

    @Test void independentNativeStructureContextsSelectExactlyOneBranch() {
        int mounds = 0, portals = 0;
        for (long seed = 0; seed < 4096; seed++) {
            // Literal WorldgenRandom.setLargeFeatureSeed formula / LegacyRandomSource Java-compatible stream.
            Random moundContext = largeFeatureRandom(seed, 31, -17);
            Random portalContext = largeFeatureRandom(seed, 31, -17);
            var moundOutcome = TCSurfaceAttemptRules.roll(moundContext::nextInt, 31, -17, (x, z) -> 64, 320);
            var portalOutcome = TCSurfaceAttemptRules.roll(portalContext::nextInt, 31, -17, (x, z) -> 64, 320);
            assertEquals(moundOutcome, portalOutcome);
            boolean mound = moundOutcome.kind() == Kind.MOUND;
            boolean portal = portalOutcome.kind() == Kind.PORTAL;
            assertFalse(mound && portal);
            if (mound) mounds++;
            if (portal) portals++;
        }
        assertTrue(mounds > 0); assertTrue(portals > 0);
    }

    @Test void exactWorldGatesDoNotInventPeacefulMobCapOrBiomeRestrictions() {
        assertTrue(TCSurfaceAttemptRules.allowsGeneration(true, -1, true, false, true, false));
        assertTrue(TCSurfaceAttemptRules.allowsGeneration(true, -1, true, false, false, true));
        assertFalse(TCSurfaceAttemptRules.allowsGeneration(true, -1, true, false, false, false));
        assertFalse(TCSurfaceAttemptRules.allowsGeneration(false, -1, true, false, true, true));
        assertFalse(TCSurfaceAttemptRules.allowsGeneration(true, -1, false, false, true, true));
        assertFalse(TCSurfaceAttemptRules.allowsGeneration(true, -1, true, true, true, true));
        for (int mode : List.of(-9, 0, 1, 2, 3, 99))
            assertFalse(TCSurfaceAttemptRules.allowsGeneration(true, mode, true, false, true, true));
    }

    @Test void savedSpawnOrRejectionIsTerminalAcrossReplayAndEntityDeath() {
        assertFalse(Completion.PENDING.processed());
        Completion spawned = Completion.PENDING.resolve(true);
        Completion rejected = Completion.PENDING.resolve(false);
        assertTrue(spawned.processed()); assertTrue(rejected.processed());
        assertEquals(Completion.SPAWNED, Completion.fromSaved(spawned.ordinal()).resolve(false));
        assertEquals(Completion.REJECTED, Completion.fromSaved(rejected.ordinal()).resolve(true));
        assertThrows(IllegalArgumentException.class, () -> Completion.fromSaved(-1));
        assertThrows(IllegalArgumentException.class, () -> Completion.fromSaved(3));
    }

    @Test void reviewedMaterialBridgeIncludesSourceMetadataFamiliesButNotSimilarMaterials() {
        List<String> ids = TCCultistPortalSupport.sourceSupportIds();
        for (String id : List.of("minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:farmland", "minecraft:dirt_path",
            "minecraft:gravel", "minecraft:sand", "minecraft:red_sand", "minecraft:soul_sand", "minecraft:snow", "minecraft:stone",
            "minecraft:polished_granite", "minecraft:stone_pressure_plate", "minecraft:petrified_oak_slab", "minecraft:observer",
            "minecraft:coal_block", "minecraft:purple_shulker_box", "minecraft:light_gray_glazed_terracotta", "minecraft:red_concrete_powder",
            "thaumcraft:stone_porous", "thaumcraft:loot_urn_rare", "thaumcraft:infusion_matrix", "thaumcraft:ore_quartz")) assertTrue(ids.contains(id), id);
        for (String id : List.of("minecraft:grass_block", "minecraft:mycelium", "minecraft:snow_block", "minecraft:clay", "minecraft:ice",
            "minecraft:stone_button", "minecraft:infested_stone", "minecraft:lapis_block", "minecraft:iron_block", "minecraft:diamond_block",
            "minecraft:redstone_block", "minecraft:glass", "minecraft:oak_planks", "minecraft:deepslate", "minecraft:copper_ore", "minecraft:mud",
            "thaumcraft:grass_ambient", "thaumcraft:taint_rock", "thaumcraft:crystal_air", "thaumcraft:loot_crate_rare", "thaumcraft:crucible"))
            assertFalse(ids.contains(id), id);
    }

    @Test void supportListIsUniqueImmutableAndContainsNoBroadTagReferences() {
        List<String> ids = TCCultistPortalSupport.sourceSupportIds();
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(ids.stream().allMatch(id -> id.matches("(minecraft|thaumcraft):[a-z0-9_]+")));
        assertThrows(UnsupportedOperationException.class, () -> ids.add("minecraft:grass_block"));
    }

    @Test void nativeSpawnCancellationAndDiscardNeverCommitAnEntity() {
        assertTrue(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 2, -4, 2, -4, 64, -64, 320));
        assertFalse(TCSurfaceAttemptRules.allowsPortalInsertion(true, false, 2, -4, 2, -4, 64, -64, 320));
        assertFalse(TCSurfaceAttemptRules.allowsPortalInsertion(false, true, 2, -4, 2, -4, 64, -64, 320));
    }

    @Test void eventMovedEntityCannotEscapeTheOwnerSaveChunk() {
        assertFalse(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 3, -4, 2, -4, 64, -64, 320));
        assertFalse(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 2, -5, 2, -4, 64, -64, 320));
    }

    @Test void eventMovedEntityCannotEscapeTheWritableWorldHeight() {
        assertTrue(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 2, -4, 2, -4, -63, -64, 320));
        assertTrue(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 2, -4, 2, -4, 319, -64, 320));
        assertFalse(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 2, -4, 2, -4, -64, -64, 320));
        assertFalse(TCSurfaceAttemptRules.allowsPortalInsertion(false, false, 2, -4, 2, -4, 320, -64, 320));
    }

    private static Random largeFeatureRandom(long seed, int x, int z) {
        Random random = new Random(seed);
        long first = random.nextLong(), second = random.nextLong();
        random.setSeed((long)x * first ^ (long)z * second ^ seed);
        return random;
    }

    private static IntUnaryOperator script(List<String> order, int... values) {
        int[] index = {0};
        return bound -> {
            order.add("int:" + bound);
            assertTrue(index[0] < values.length, "Unexpected extra RNG draw");
            int value = values[index[0]++];
            assertTrue(value >= 0 && value < bound, "Scripted value exceeds RNG bound");
            return value;
        };
    }
}
