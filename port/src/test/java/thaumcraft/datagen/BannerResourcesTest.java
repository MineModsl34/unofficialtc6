package thaumcraft.datagen;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BannerResourcesTest {
    @Test void sixteenRealBannersOwnAllSixtyFourRegenerableSnapshots() throws Exception {
        var manifest=TCBannerResourcesProvider.read();assertEquals(64,manifest.getAsJsonArray("files").size());var paths=new HashSet<String>();
        for(var entry:manifest.getAsJsonArray("files")){var row=entry.getAsJsonObject();String path=row.get("path").getAsString();assertTrue(paths.add(path));
            String base=row.get("target").getAsString().equals("resource_pack")?"assets":"data";
            assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(Path.of("src/generated/resources",base,path))));}
    }
    @Test void layeredSourceInventoryModelsAreNotColoredItemPlaceholders() throws Exception {
        for(String color:new String[]{"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"}){
            var model=JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/models/item/banner_"+color+".json"))).getAsJsonObject();
            assertEquals(3,model.getAsJsonObject("textures").size());assertEquals("thaumcraft:item/banner_stand",model.getAsJsonObject("textures").get("layer0").getAsString());
        }
        var owner=Files.readString(Path.of("src/main/java/thaumcraft/common/blocks/basic/BannerRegistries.java"));
        assertTrue(owner.contains("DyeColor.values()"));assertTrue(owner.contains("new BlockBannerTC(dye)"));assertTrue(owner.contains("BlocksTC.banners.put"));
    }
    @Test void quillAndNoFogGuiShaderRemainReproducibleWithoutDatagenLaunch() throws Exception {
        for(String path:TCClientResourcesProvider.PATHS)assertArrayEquals(Files.readAllBytes(Path.of("src/main/resources",path)),TCClientResourcesProvider.read(path));
        var atlas=JsonParser.parseString(new String(TCClientResourcesProvider.read("assets/minecraft/atlases/blocks.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(3,atlas.getAsJsonArray("sources").size());assertEquals("thaumcraft:research/quill",atlas.getAsJsonArray("sources").get(0).getAsJsonObject().get("sprite").getAsString());
        assertThrows(java.io.IOException.class,()->TCClientResourcesProvider.read("../outside"));
    }
}
