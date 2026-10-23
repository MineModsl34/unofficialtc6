package thaumcraft.datagen;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only provenance/binding checks. Does not bootstrap Minecraft or claim gameplay acceptance. */
class Mob8IntegrationTest {
    private JsonObject json(String path) throws Exception {return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();}
    private JsonArray rows() throws Exception {return json("../reference/validation/phase8-entity-coverage-current.json").getAsJsonArray("sourceRows");}
    @Test void originalRosterHasFortyThreeEntriesWithHonestStageBoundaries() throws Exception {
        var rows=rows();assertEquals(43,rows.size());int stage8=0;var names=new HashSet<String>();
        for(var element:rows){var row=element.getAsJsonObject();assertTrue(names.add(row.get("native").getAsString()));if(row.get("stage").getAsInt()==8)stage8++;}
        assertEquals(41,stage8);
        String registrar=Files.readString(Path.of("src/main/java/thaumcraft/Registrar.java"));
        assertTrue(registrar.contains("Mob8BossRegistries.createEntityTypes()"));
        // The roster retains the original stage ownership, while later completed stages
        // now provide genuine Golem and FluxRift implementations and registration hooks.
        var laterStages=new HashMap<String,Integer>();
        for(var element:rows){var row=element.getAsJsonObject();if(row.get("stage").getAsInt()!=8)laterStages.put(row.get("native").getAsString(),row.get("stage").getAsInt());}
        assertEquals(Map.of("golem",9,"flux_rift",10),laterStages);
        assertTrue(Files.exists(Path.of("src/main/java/thaumcraft/common/golems/EntityThaumcraftGolem.java")));
        assertTrue(Files.exists(Path.of("src/main/java/thaumcraft/common/entities/EntityFluxRift.java")));
        assertTrue(registrar.contains("Golem9EntityRegistries.createEntityTypes()"));
        assertTrue(registrar.contains("Phase10RiftRegistries.createEntityTypes()"));
        assertEquals("code_complete_manual_deferred",json("../reference/validation/phase8-entity-coverage-current.json").get("status").getAsString());
    }
    @Test void everySourceEntityHasNativeTranslationAndReviewedScanBridge() throws Exception {
        var english=json("src/main/resources/assets/thaumcraft/lang/en_us.json");
        String identity=Files.readString(Path.of("src/main/java/thaumcraft/common/lib/research/LegacyScanIdentity.java"));
        for(var element:rows()){var row=element.getAsJsonObject();if(row.get("stage").getAsInt()!=8)continue;String name=row.get("native").getAsString();
            assertTrue(english.has("entity.thaumcraft."+name),name);
            assertTrue(identity.contains("map.put(\"thaumcraft:"+name+"\", \""+row.get("source").getAsString()+"\")"),name);
        }
    }
    @Test void allTwentyFourSourceEggsHaveNativeModelsAndLocalNames() throws Exception {
        var english=json("src/main/resources/assets/thaumcraft/lang/en_us.json");var russian=json("src/main/resources/assets/thaumcraft/lang/ru_ru.json");int eggs=0;
        for(var element:rows()){var row=element.getAsJsonObject();if(row.get("egg").isJsonNull())continue;eggs++;
            String name=row.get("native").getAsString()+"_spawn_egg";
            assertTrue(english.has("item.thaumcraft."+name),name);assertTrue(russian.has("item.thaumcraft."+name),name);
            var model=json("src/generated/resources/assets/thaumcraft/models/item/"+name+".json");assertEquals(net.minecraft.resources.ResourceLocation.parse("minecraft:item/template_spawn_egg"),net.minecraft.resources.ResourceLocation.parse(model.get("parent").getAsString()));
        }assertEquals(24,eggs);
    }
    @Test void numericLegacyFormatsAndExistingFocalGuiSurviveReimport() throws Exception {
        for(String language:List.of("en_us","ru_ru")){
            var translations=json("src/main/resources/assets/thaumcraft/lang/"+language+".json");var additions=json("../tools/lang_port_additions.json").getAsJsonObject(language);
            for(String boss:List.of("CultistLeader","EldritchGolem","EldritchWarden")){
                String value=translations.get("entity.Thaumcraft."+boss+".name.custom").getAsString();assertFalse(value.matches(".*%(?:[0-9]+\\$)?[0-9.]*[df].*"),value);assertTrue(value.contains("%s")||value.contains("%1$s"),value);
            }
            for(String gui:List.of("name","reset","save","craft","rejected","incomplete")){
                String key="gui.thaumcraft.focus."+gui;assertTrue(translations.has(key));assertEquals(additions.get(key),translations.get(key));
            }
        }
    }
}
