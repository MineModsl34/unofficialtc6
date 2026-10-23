package thaumcraft.client.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceFxResourcesTest {
    @Test void genuineVanillaLavaProviderHasExactlyOneNativeEnqueue() throws Exception {
        var source=Files.readString(Path.of("src/main/java/thaumcraft/client/fx/FXDispatcher.java"));
        var method=source.substring(source.indexOf("public void furnaceLavaFx("),source.indexOf("public void blockRunes("));
        assertTrue(method.contains("particleEngine.createParticle(ParticleTypes.LAVA"));
        assertFalse(method.contains("particleEngine.add("));
        assertFalse(method.contains("ParticleEngine.addEffect("));
    }
    @Test void allThirtyOneOriginalFxClassesHaveNativeCounterparts() {
        var names=List.of("ParticleEngine","FXDispatcher","particles/FXWispEG","particles/FXVisSparkle","particles/FXVent2","particles/FXVent","particles/FXSwarmRunes","particles/FXSwarm","particles/FXSmokeSpiral","particles/FXSlimyBubble","particles/FXPlane","particles/FXGenericP2P","particles/FXGenericP2E","particles/FXGenericGui","particles/FXGeneric","particles/FXFireMote","particles/FXDigging","particles/FXBreakingFade","particles/FXBoreSparkle","particles/FXBoreParticles","particles/FXBlockRunes","other/FXVoidStream","other/FXSonic","other/FXShieldRunes","other/FXEssentiaStream","other/FXBoreStream","other/FXBlockWard","beams/FXBolt","beams/FXBeamWand","beams/FXBeamBore","beams/FXArc");
        assertEquals(31,names.size());
        for(var name:names)assertTrue(Files.isRegularFile(Path.of("src/main/java/thaumcraft/client/fx/"+name+".java")),name);
    }
    @Test void sourceAtlasBeamsAndHemisphereTexturesArePackaged() throws Exception {
        // beam/beam2/beam3 are dormant API references absent from the original BETA26 JAR itself.
        for(var name:List.of("particles","beam1","beamh","beaml","essentia","auranodes"))checkPng("misc/"+name+".png");
        for(int frame=1;frame<=15;frame++){checkPng("models/hemis"+frame+".png");checkPng("models/ripple"+frame+".png");}
        try(var model=getClass().getResourceAsStream("/assets/thaumcraft/models/obj/hemis.obj")){assertNotNull(model);}
    }
    @Test void guiShaderPreservesSourceUnlitNoFogColorAndAlphaThreshold() throws Exception {
        String prefix="/assets/thaumcraft/shaders/core/fx_gui";
        try(var vertex=getClass().getResourceAsStream(prefix+".vsh");var fragment=getClass().getResourceAsStream(prefix+".fsh");var program=getClass().getResourceAsStream(prefix+".json")) {
            assertNotNull(vertex);assertNotNull(fragment);assertNotNull(program);
            String v=new String(vertex.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),f=new String(fragment.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(v.contains("fog_distance"));assertFalse(f.contains("linear_fog"));
            assertTrue(v.contains("vertexColor = Color"));assertTrue(f.contains("0.00392156862745"));
            var json=com.google.gson.JsonParser.parseString(new String(program.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("thaumcraft:fx_gui",json.get("vertex").getAsString());assertEquals(1,json.getAsJsonArray("samplers").size());
        }
    }
    @Test void nativeFxNeverCallsRemovedCompatibilityOpenGl() throws Exception {
        try(var paths=Files.walk(Path.of("src/main/java/thaumcraft/client/fx"))) {
            for(var file:paths.filter(path->path.toString().endsWith(".java")).toList()) {
                var source=Files.readString(file);
                assertFalse(source.contains("org.lwjgl.opengl.GL11"),file.toString());
                assertFalse(source.contains("ARBShaderObjects"),file.toString());
                assertFalse(source.contains("com.sasmaster.glelwjgl"),file.toString());
                assertFalse(source.contains(".isAirBlock("),file.toString());
                assertFalse(source.contains(".isRemote"),file.toString());
            }
        }
    }
    private void checkPng(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/thaumcraft/textures/"+name)) {
            assertNotNull(stream,name);assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},stream.readNBytes(8),name);
        }
    }
}
