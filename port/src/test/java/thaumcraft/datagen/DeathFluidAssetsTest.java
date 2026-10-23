package thaumcraft.datagen;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeathFluidAssetsTest {
    private static Path root() { return Files.isDirectory(Path.of("src/main/resources")) ? Path.of("") : Path.of("port"); }
    @Test void allReviewedResourcesMatchPackagedSnapshots() throws Exception {
        var rows = TCDeathFluidResourcesProvider.read().getAsJsonArray("files");
        assertEquals(3, rows.size());
        for (var value : rows) {
            var row = value.getAsJsonObject();
            Path file = root().resolve("src/generated/resources/assets").resolve(row.get("path").getAsString());
            assertTrue(Files.isRegularFile(file), file.toString());
            assertEquals(row.get("payload"), JsonParser.parseString(Files.readString(file)));
        }
    }
    @Test void bucketUsesGenuineRegisteredDeathFluidAndOriginalAnimatedTexture() throws Exception {
        var bucket = JsonParser.parseString(Files.readString(root().resolve("src/generated/resources/assets/thaumcraft/models/item/liquid_death_bucket.json"))).getAsJsonObject();
        assertEquals("neoforge:fluid_container", bucket.get("loader").getAsString());
        assertEquals("thaumcraft:liquid_death", bucket.get("fluid").getAsString());
        assertTrue(Files.isRegularFile(root().resolve("src/main/resources/assets/thaumcraft/textures/block/animatedglow.png")));
        assertTrue(Files.isRegularFile(root().resolve("src/main/resources/assets/thaumcraft/textures/block/animatedglow.png.mcmeta")));
    }
    @Test void runtimeRetainsSingleUpdateOwnerAndAllSourceTaintMaterialMembers() throws Exception {
        String fluid = Files.readString(root().resolve("src/main/java/thaumcraft/common/blocks/misc/DeathFluid.java"));
        assertFalse(fluid.contains("super.tick("));
        assertTrue(fluid.contains("public void tick(Level level, BlockPos pos, FluidState state)"));
        String blocks = Files.readString(root().resolve("src/main/java/thaumcraft/common/blocks/world/taint/TaintHelper.java"));
        for (String member : new String[]{"fluxGoo", "taintFibre", "taintCrust", "taintSoil", "taintRock", "taintLog", "taintFeature", "taintGeyser"})
            assertTrue(blocks.contains("block==BlocksTC." + member), member);
        String fibre = Files.readString(root().resolve("src/main/java/thaumcraft/common/blocks/world/taint/BlockTaintFibre.java"));
        assertTrue(fibre.contains("TaintSpreadRules.isNonTaintSupport(state.isAir(),TaintHelper.isTaint(state)"));
        String goo = Files.readString(root().resolve("src/main/java/thaumcraft/common/blocks/world/taint/BlockFluxGoo.java"));
        assertTrue(goo.contains("SourceFluidDisplacements.protectedBlock(state)"));
        assertTrue(goo.contains("TaintHelper.isTaint(state)"));
        String protection = Files.readString(root().resolve("src/main/java/thaumcraft/common/blocks/misc/SourceFluidDisplacements.java"));
        for (String family : new String[]{"DoorBlock", "TrapDoorBlock", "FenceBlock", "FenceGateBlock", "BasePressurePlateBlock", "LadderBlock", "IronBarsBlock", "WallBlock", "AbstractBannerBlock", "CakeBlock", "SignBlock", "SugarCaneBlock"})
            assertTrue(protection.contains("instanceof " + family), family);
        for (String exception : new String[]{"ShulkerBoxBlock", "LeavesBlock", "TrapDoorBlock", "AbstractCauldronBlock", "StainedGlassBlock"})
            assertTrue(protection.contains("instanceof " + exception), exception);
        for (String exception : new String[]{"BEACON", "GLASS", "GLOWSTONE", "SEA_LANTERN", "PISTON", "STICKY_PISTON", "PISTON_HEAD"})
            assertTrue(protection.contains("block == Blocks." + exception), exception);
    }
}
