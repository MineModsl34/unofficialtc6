package thaumcraft.datagen;
import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SealAssetsTest {
 @Test void sourceRegistersSixteenSealsButSeventeenItems()throws Exception{var m=JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCSealResourcesProvider.RESOURCE))).getAsJsonObject();TCSealResourcesProvider.validate(m);assertEquals(16,m.get("registered_seal_count").getAsInt());assertEquals(17,m.getAsJsonArray("files").size());assertEquals("provider",m.getAsJsonArray("items").get(14).getAsString());}
 @Test void everyNativeSnapshotAndOriginalIconExists()throws Exception{var m=JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCSealResourcesProvider.RESOURCE))).getAsJsonObject();for(var el:m.getAsJsonArray("files")){var row=el.getAsJsonObject();assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets",row.get("path").getAsString()))));String texture=row.getAsJsonObject("payload").getAsJsonObject("textures").get("layer0").getAsString();assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/thaumcraft/textures",texture.substring("thaumcraft:".length())+".png")),texture);}}
 @Test void animationsRetainOriginalMetadataExceptBlank()throws Exception{var m=JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCSealResourcesProvider.RESOURCE))).getAsJsonObject();for(var name:m.getAsJsonArray("items"))if(!name.getAsString().equals("blank"))assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/thaumcraft/textures/item/seals/seal_"+name.getAsString()+".png.mcmeta")));}
}

