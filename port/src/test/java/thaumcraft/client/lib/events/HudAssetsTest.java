package thaumcraft.client.lib.events;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Resource/source closure only: these checks do not execute a client or claim pixel acceptance. */
class HudAssetsTest {
    private static final Path SOURCE=Path.of("../reference/tc6/src/assets/thaumcraft");
    private static final Path NATIVE=Path.of("src/main/resources/assets/thaumcraft");
    @Test void originalHudAndOverlayPngPixelsAreUnchangedAfterOnlyPathMigration() throws Exception {
        for(String path:List.of("gui/hud.png","research/knowledge_theory.png","research/knowledge_observation.png","research/quill.png","misc/particles.png","misc/frame_corner.png","misc/frame_side.png","misc/architect_arrows.png","misc/radial.png","misc/radial2.png","misc/vignette.png","item/thaumonomicon.png","block/research_table_model.png")){
            String legacy=path.replace("item/","items/").replace("block/","blocks/");
            byte[] expected=Files.readAllBytes(SOURCE.resolve("textures/"+legacy));
            try(var input=getClass().getResourceAsStream("/assets/thaumcraft/textures/"+path)){
                assertNotNull(input,path);byte[] actual=input.readAllBytes();assertArrayEquals(expected,actual,path);
                assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},java.util.Arrays.copyOf(actual,8),path);
                var dimensions=ByteBuffer.wrap(actual,16,8);assertTrue(dimensions.getInt()>0,path);assertTrue(dimensions.getInt()>0,path);
            }
        }
    }
    @Test void quillIsAnActualBlockAtlasSpriteAndResearchNoteOnlyExposesPresence() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/minecraft/atlases/blocks.json")){
            assertNotNull(stream);var atlas=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(atlas.getAsJsonArray("sources").asList().stream().anyMatch(value->{var source=value.getAsJsonObject();return source.has("resource")&&source.get("resource").getAsString().equals("thaumcraft:research/quill")&&source.get("sprite").getAsString().equals("thaumcraft:research/quill");}));
        }
        String tile=Files.readString(Path.of("src/main/java/thaumcraft/common/tiles/crafting/TileResearchTable.java"));
        assertTrue(tile.contains("visible.remove(\"note\")"));assertTrue(tile.contains("visible.putBoolean(\"renderNote\",data != null)"));
        String registration=Files.readString(Path.of("src/main/java/thaumcraft/client/gui/ResearchTableClientRegistration.java"));assertTrue(registration.contains("registerBlockEntityRenderer"));assertTrue(registration.contains("Bus.MOD"));
    }
    @Test void allTwentyFiveUsedObjMeshesKeepSourceGeometryAndUnusedSourceFilesStayHonest() throws Exception {
        int active=0,faces=0,records=0,unused=0;
        try(var files=Files.walk(SOURCE.resolve("models"))){
            for(Path source:files.filter(path->path.toString().endsWith(".obj")).toList()){
                Path relative=SOURCE.relativize(source);String name=source.getFileName().toString();
                if(name.equals("orb.obj")||name.equals("turret.obj")){unused++;assertFalse(Files.isRegularFile(NATIVE.resolve(relative)),"Unused source OBJ is not a fabricated consumer: "+name);continue;}
                Path nativeFile=NATIVE.resolve(relative);assertTrue(Files.isRegularFile(nativeFile),relative.toString());
                var original=geometry(source);assertEquals(original,geometry(nativeFile),relative.toString());
                active++;records+=original.size();faces+=original.stream().filter(line->line.startsWith("f ")).count();
            }
        }
        assertEquals(25,active);assertEquals(2,unused);assertEquals(2798,faces);assertEquals(6227,records);
    }
    private static List<String> geometry(Path path) throws Exception {return Files.readString(path).lines().filter(line->line.matches("^(v|vt|vn|f|g|o) .*" )).toList();}
}
