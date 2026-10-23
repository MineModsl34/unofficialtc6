package thaumcraft.datagen;
import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NaturalMobAssetsTest {
    private static JsonObject manifest()throws Exception{return JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCNaturalMobAssetsProvider.RESOURCE))).getAsJsonObject();}
    @Test void allEightSnapshotsAndThirteenSourceHashesStayExact()throws Exception{var manifest=manifest();TCNaturalMobAssetsProvider.validate(manifest);for(var entry:manifest.getAsJsonArray("entries")){var row=entry.getAsJsonObject();assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(Path.of("src/generated/resources",row.get("path").getAsString()))));}for(var entry:manifest.getAsJsonArray("sourceFiles")){var row=entry.getAsJsonObject();assertEquals(row.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("../reference/tc6/src",row.get("path").getAsString())))));}}
    @Test void sourceGeometryCountsAndNativeTexturesResolve()throws Exception{var manifest=manifest();var models=manifest.getAsJsonArray("models");assertEquals(3,models.size());assertEquals(9,models.get(0).getAsJsonObject().get("parts").getAsInt());assertEquals(8,models.get(1).getAsJsonObject().get("parts").getAsInt());assertEquals(21,models.get(2).getAsJsonObject().get("parts").getAsInt());for(String texture:List.of("entity/bzombie","entity/czombie","entity/crab","entity/firebat","entity/pech_forage","entity/pech_thaum","entity/pech_stalker","misc/particles","misc/auranodes","gui/gui_pech"))assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/thaumcraft/textures",texture+".png")),texture);}
    @Test void pechLootCurioAndEnchantedProbabilityHaveActualNativeBindings()throws Exception{var loot=JsonParser.parseString(Files.readString(Path.of("src/generated/resources/data/thaumcraft/loot_table/entities/pech.json"))).getAsJsonObject();var special=loot.getAsJsonArray("pools").get(1).getAsJsonObject();assertEquals("thaumcraft:curio_knowledge",special.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());var chance=special.getAsJsonArray("conditions").get(1).getAsJsonObject();assertEquals(.0125F,chance.get("unenchanted_chance").getAsFloat());assertEquals(.0225F,chance.getAsJsonObject("enchanted_chance").get("base").getAsFloat());assertEquals(.01F,chance.getAsJsonObject("enchanted_chance").get("per_level_above_first").getAsFloat());}
    @Test void allThirtyPechAndCrabPartsKeepSourceUvCuboidsPivotsAndAngles()throws Exception{
        for(String model:List.of("ModelPech","ModelEldritchCrab")){
            String source=Files.readString(Path.of("../reference/tc6/src/thaumcraft/client/renderers/models/entity",model+".java")),nativeModel=Files.readString(Path.of("src/main/java/thaumcraft/client/renderers/models/entity",model+".java"));
            // A constructor's initial pose is not its setRotationAngles rest pose.
            // Crab legs start at zero; their .2094395/.4363323 angles are applied only before rendering.
            String constructor=constructorBody(source,model);
            var matcher=java.util.regex.Pattern.compile("(?:part|p)\\((?:root|r),\"([A-Za-z_0-9]+)\",([^)]*)\\)").matcher(nativeModel);int count=0;
            while(matcher.find()){
                String name=matcher.group(1),original=switch(name){case "body"->"bipedBody";case "head"->"bipedHead";case "right_leg"->"bipedRightLeg";case "left_leg"->"bipedLeftLeg";case "right_arm"->"bipedRightArm";case "left_arm"->"bipedLeftArm";case "jowls"->"Jowls";case "lower_pack"->"LowerPack";case "upper_pack"->"UpperPack";default->name;};
                String[] values=matcher.group(2).split(",");assertEquals(15,values.length,name);
                float[] uv=find(constructor,"this\\."+original+" = new ModelRenderer\\(this, ([^)]*)\\)",2),box=find(constructor,"this\\."+original+"\\.addBox\\(([^)]*)\\)",6),pivot=find(constructor,"this\\."+original+"\\.setRotationPoint\\(([^)]*)\\)",3),rotation=findOptional(constructor,"this\\.setRotation\\(this\\."+original+", ([^)]*)\\)",3);
                float[] expected=new float[14];System.arraycopy(uv,0,expected,0,2);System.arraycopy(box,0,expected,2,6);System.arraycopy(pivot,0,expected,8,3);System.arraycopy(rotation,0,expected,11,3);
                for(int i=0;i<14;i++)assertEquals(expected[i],number(values[i]),.000001F,model+" "+name+" field "+i);count++;
            }
            assertEquals(model.equals("ModelPech")?9:21,count);
            if(model.equals("ModelEldritchCrab"))assertCrabAnimationBases(source,nativeModel);
        }
    }
    private static String constructorBody(String source,String model){
        var matcher=java.util.regex.Pattern.compile("public\\s+"+model+"\\s*\\(\\s*\\)\\s*\\{").matcher(source);assertTrue(matcher.find(),model+" source constructor");
        int start=matcher.end(),depth=1;
        for(int i=start;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i);}
        throw new AssertionError(model+" unbalanced source constructor");
    }
    private static void assertCrabAnimationBases(String source,String nativeModel){
        int start=source.indexOf("public void setRotationAngles(");assertTrue(start>=0);String animation=source.substring(start);
        var x=java.util.regex.Pattern.compile("leg\\.xRot\\s*=\\s*([^;]+);").matcher(nativeModel);assertTrue(x.find());float nativeX=number(x.group(1));
        for(String family:List.of("RRLeg","RFLeg","LRLeg","LFLeg")){
            var call=java.util.regex.Pattern.compile("leg\\(\""+family+"\"\\+i,([^,]+),([^,]+),").matcher(nativeModel);assertTrue(call.find(),family+" native animation base");
            for(int suffix=0;suffix<2;suffix++){
                float[] original=find(animation,"this\\.setRotation\\(this\\."+family+suffix+", ([^)]*)\\)",3);
                assertEquals(original[0],nativeX,.000001F,family+suffix+" animated x");
                assertEquals(original[1],number(call.group(1)),.000001F,family+suffix+" animated y");
                assertEquals(original[2],number(call.group(2)),.000001F,family+suffix+" animated z");
            }
        }
    }
    private static float number(String text){return Float.parseFloat(text.trim().replace("F","").replace("f",""));}
    private static float[] find(String source,String regex,int count){var matcher=java.util.regex.Pattern.compile(regex).matcher(source);assertTrue(matcher.find(),regex);String[] fields=matcher.group(1).split(",");assertEquals(count,fields.length);float[] result=new float[count];for(int i=0;i<count;i++)result[i]=number(fields[i]);return result;}
    private static float[] findOptional(String source,String regex,int count){return java.util.regex.Pattern.compile(regex).matcher(source).find()?find(source,regex,count):new float[count];}
}
