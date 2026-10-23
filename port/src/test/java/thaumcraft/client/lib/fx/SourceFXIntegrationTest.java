package thaumcraft.client.lib.fx;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static sided producer/consumer contracts, not Minecraft or visual acceptance. */
class SourceFXIntegrationTest {
    private static String source(String path) throws Exception {return Files.readString(Path.of("src/main/java/thaumcraft",path));}
    @Test void serverSeamContainsNoClientImportsOrClientToServerRegistration() throws Exception {
        String bridge=source("common/lib/fx/SourceFX.java"),network=source("common/lib/network/fx/SourceFXNetwork.java");
        assertFalse(bridge.contains("import net.minecraft.client"));assertFalse(bridge.contains("import thaumcraft.client"));
        assertTrue(network.contains("playToClient"));assertFalse(network.contains("playToServer"));assertTrue(network.contains("Bus.MOD"));
    }
    @Test void allSplitConsumersAreConnectedAndWorldQueuesClear() throws Exception {
        String receiver=source("client/lib/fx/SourceFXClient.java"),devices=source("client/lib/fx/SourceDeviceFX.java");
        assertTrue(receiver.contains("SourceBlockFX.render(world,p)"));assertTrue(devices.contains("SourceToolFX.render(world,p)"));
        assertTrue(devices.contains("SourceMiscFX.render(world,p)"));assertTrue(devices.contains("SourceProjectileFX.render(world,p)"));
        assertTrue(receiver.contains("essentia.clear();digging.clear();count=0"));
    }
    @Test void shieldUsesNativeSourceAdmissionAndBoreCapturesReceiptState() throws Exception {
        String receiver=source("client/lib/fx/SourceFXClient.java");
        assertTrue(receiver.contains("Minecraft.getInstance().particleEngine.add(new FXShieldRunes"));
        assertFalse(receiver.contains("ParticleEngine.addEffect(world,new FXShieldRunes"));
        assertTrue(receiver.contains("new Dig(packet,bore,state,delay)"));assertTrue(receiver.contains("dig.bore(),dig.state()"));
        assertTrue(source("common/entities/construct/EntityArcaneBore.java").contains("Kind.BORE"));
    }
    @Test void paidDustChangesHaveRealSourceBamfAndToolSparkles() throws Exception {
        for(String name:new String[]{"DustConversion","BookshelfDustConversion","WorkbenchDustConversion","DustMultiblockConversion"})
            assertTrue(source("common/lib/crafting/"+name+".java").contains("SourceFX.bamf"),name);
        assertTrue(source("common/items/resources/ItemMagicDust.java").contains("\"dust_hand\""));
        assertTrue(source("common/items/resources/ItemMagicDust.java").contains("match.trigger().sparkle"));
        assertTrue(source("client/lib/fx/SourceToolFX.java").contains("for(int a=0;a<50;a++)"));
    }
}
