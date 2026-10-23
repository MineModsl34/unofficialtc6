package thaumcraft.datagen;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mob8AssetsTest {
    private JsonObject manifest()throws Exception{return JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCMob8AssetsProvider.RESOURCE))).getAsJsonObject();}
    @Test void ownedSnapshotsMatchExactly()throws Exception{var manifest=manifest();TCMob8AssetsProvider.validate(manifest);assertEquals(22,manifest.getAsJsonArray("files").size());for(var entry:manifest.getAsJsonArray("files")){var row=entry.getAsJsonObject();var path=Path.of("src/generated/resources",row.get("target").getAsString().equals("resource_pack")?"assets":"data",row.get("path").getAsString());assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(path)),path.toString());}}
    @Test void manifestRejectsTraversalDuplicateAndBadTarget()throws Exception{for(String mutation:new String[]{"path","target","duplicate"}){var data=manifest();var first=data.getAsJsonArray("files").get(0).getAsJsonObject();if(mutation.equals("path"))first.addProperty("path","thaumcraft/../escape.json");else if(mutation.equals("target"))first.addProperty("target","outside");else data.getAsJsonArray("files").add(first.deepCopy());assertThrows(IllegalArgumentException.class,()->TCMob8AssetsProvider.validate(data));}}
    @Test void nativeTagsContainActualSourceFamilies()throws Exception{assertTrue(Files.readString(Path.of("src/generated/resources/data/minecraft/tags/entity_type/zombies.json")).contains("inhabited_zombie"));assertTrue(Files.readString(Path.of("src/generated/resources/data/minecraft/tags/entity_type/arthropod.json")).contains("eldritch_crab"));assertTrue(Files.readString(Path.of("src/generated/resources/data/thaumcraft/tags/worldgen/biome/legacy_overworld_spawn_pool.json")).contains("magical_forest"));}
    @Test void allSourceDependenciesHavePixels()throws Exception{for(String texture:new String[]{"item/bottle_taint","item/causality_collapser","item/banner_stand","item/banner_cultist","models/banner_cultist","models/taint_fibres"})assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/thaumcraft/textures",texture+".png")),texture);}
}
