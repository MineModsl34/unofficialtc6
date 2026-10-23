package thaumcraft.common.lib.network.fx;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketSourceFXTest {
    private static PacketSourceFX packet(PacketSourceFX.Kind kind){return new PacketSourceFX(kind,1.125,-64,30000000,.01,.02,.03,8038177,Float.floatToIntBits(.66F),3,5,"thaumcraft.FIRE%thaumcraft.AIR");}
    @Test void allEffectKindsRoundtripWithoutPlayerOrGameplayState(){
        for(var kind:PacketSourceFX.Kind.values()){
            var p=packet(kind);var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{PacketSourceFX.STREAM_CODEC.encode(buffer,p);assertEquals(p,PacketSourceFX.STREAM_CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally{buffer.release();}
        }
    }
    @Test void impossibleCoordinatesAndMotionAreRejected(){
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,33554433})
            assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,value,0,0,0,0,0,0,0,0,-1,"x"));
        assertDoesNotThrow(()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,-33554432,33554432,0,0,0,0,0,0,0,-1,"x"));
        assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,0,0,0,0,Double.NaN,0,0,0,0,-1,"x"));
    }
    @Test void flagsDirectionsAndOpaqueKeysHaveExplicitBounds(){
        assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,0,0,0,0,0,0,0,0,4,-1,"x"));
        assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,0,0,0,0,0,0,0,0,0,6,"x"));
        assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,0,0,0,0,0,0,0,0,0,-2,"x"));
        assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,0,0,0,0,0,0,0,0,0,-1,"x".repeat(1025)));
        assertDoesNotThrow(()->new PacketSourceFX(PacketSourceFX.Kind.LOCAL,0,0,0,0,0,0,0,0,0,-1,"x".repeat(1024)));
    }
    @Test void beamWidthCannotInjectNonfiniteGeometry(){
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new PacketSourceFX(PacketSourceFX.Kind.ZAP,0,0,0,0,0,0,0,Float.floatToIntBits(value),0,-1,""));
    }
    @Test void signedSourceSentinelsAndByteShortQuirksRemainRepresentable(){
        for(int target:new int[]{-1,-2,-3})assertEquals(target,new PacketSourceFX(PacketSourceFX.Kind.SHIELD,0,0,0,0,0,0,100,target,0,-1,"").extra());
        assertEquals(-128,(byte)128);assertEquals(-1,(byte)255);assertEquals(-32768,(short)32768);
        assertEquals("thaumcraft:source_fx",packet(PacketSourceFX.Kind.BAMF).type().id().toString());
    }
}
