package thaumcraft.common.entities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class FluxRiftGeometryTest {
    @Test void seededWalkIsDeterministicAndSizeControlsNumberOfSteps(){for(int size:new int[]{0,1,5,12,100,4096}){var one=FluxRiftGeometry.create(123,size);assertEquals(one,FluxRiftGeometry.create(123,size));assertEquals(2*(int)Math.ceil(size/3F)+2,one.points().size());assertEquals(one.points().size(),one.widths().size());assertNotEquals(one.points(),FluxRiftGeometry.create(124,size).points());}}
    @Test void sourceTwoEndedTaperIsMirroredWithZeroEndCaps(){var shape=FluxRiftGeometry.create(42,12);assertEquals(10,shape.points().size());assertEquals(0F,shape.widths().get(0));assertEquals(0F,shape.widths().get(9));for(int i=0;i<10;i++)assertEquals(shape.widths().get(i),shape.widths().get(9-i));assertEquals(.03F,shape.widths().get(4),.000001F);}
    @Test void pureRotationsUseSourceFloatTrigLookupAndNormalizeCutoff(){var p=new FluxRiftGeometry.Point(1,2,3);assertEquals(p,p.pitch(0));assertEquals(p,p.yaw(0));assertEquals(new FluxRiftGeometry.Point(0,0,0),new FluxRiftGeometry.Point(.000001,0,0).normal());assertEquals(new FluxRiftGeometry.Point(1,0,0),new FluxRiftGeometry.Point(2,0,0).normal());}
    @Test void sourceJavaRandomSeed123GoldenWalkWithoutMinecraftBootstrap(){
        // Independently evaluated from BETA26 calcSteps with the Java Random48-bit LCG.
        double[][] expected={{0.651939593598188,-0.30552142332106924,-0.5030208381419036},{0.5710286387119885,-0.2904781479176551,-0.446224880435585},{0.40920672893958954,-0.2603915971108267,-0.3326329650229478},{0.2438933301298153,-0.1845221911043311,-0.24950084962481583},{0.14706536386445718,-0.07587710489354967,-0.112313301035392},{-0.17951742651283384,0.08214938975877986,0.03201514676383702},{-0.36011590865338816,0.16750759652922864,0.04190143335080002},{-0.5474549042570869,0.23101284828579877,0.07138094834920672},{-0.7373314418621615,0.29166043854671403,0.08768618192287017},{-0.8322697106646988,0.32198423367717166,0.0958387987097019}};
        var points=FluxRiftGeometry.create(123,12).points();for(int i=0;i<expected.length;i++){assertEquals(expected[i][0],points.get(i).x(),1E-12);assertEquals(expected[i][1],points.get(i).y(),1E-12);assertEquals(expected[i][2],points.get(i).z(),1E-12);}
    }
}
