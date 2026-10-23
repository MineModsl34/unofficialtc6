package thaumcraft.client.lib.ender;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
/** Pure source constants/math, independent of a game or graphics context. */
public final class SourceRenderMath {
    public record MirrorLayer(int index,float depth,float scale,float red,float green,float blue){}
    private SourceRenderMath(){}
    public static List<MirrorLayer> mirrorLayers(){Random random=new Random(31100L);var result=new ArrayList<MirrorLayer>();for(int i=0;i<16;i++){float depth=16-i,scale=.0625F,weight=1/(depth+1);if(i==0){depth=65;scale=.125F;weight=.1F;}if(i==1)scale=.5F;float r=random.nextFloat()*.5F+.1F,g=random.nextFloat()*.5F+.4F,b=random.nextFloat()*.5F+.5F;if(i==0)r=g=b=1;result.add(new MirrorLayer(i,depth,scale,r*weight,g*weight,b*weight));}return List.copyOf(result);}
    /** Object-linear S/T and eye-linear Q, followed by the literal source texture matrix.
     * axis: X=0,Y=1,Z=2; sign is the source drawPlane's signed f8. Coordinates are camera-body-relative.
     */
    public static Vector4f mirrorProjection(MirrorLayer layer,int axis,int sign,float x,float y,float z,float surface,float px,float py,float pz,float ex,float ey,float ez,long time){
        float eye=axis==0?ex:axis==1?ey:ez;float distance=sign*(surface-eye);
        // Guard only the source undefined coplanar camera singularity; do not clamp ordinary depth.
        if(Math.abs(distance)<1E-6F)distance=Math.copySign(1E-6F,distance==0?sign:distance);
        float denominator=distance+layer.depth();if(Math.abs(denominator)<1E-6F)denominator=Math.copySign(1E-6F,denominator);
        float eyePlane=surface+distance/denominator;
        float s=axis==0?z:x,t=axis==1?z:y,q=(axis==0?x:axis==1?y:z)-eyePlane;
        float cameraS=axis==0?pz:px,cameraT=axis==1?pz:py,cameraR=axis==0?px:axis==1?py:pz;
        float offsetS=(axis==0?ez:ex)*layer.depth()/distance,offsetT=(axis==1?ez:ey)*layer.depth()/distance;
        Matrix4f matrix=new Matrix4f().translate(0,(time%700000L)/250000F,0).scale(layer.scale()).translate(.5F,.5F,0)
            .rotateZ((float)Math.toRadians((layer.index()*layer.index()*4321+layer.index()*9)*2F)).translate(-.5F,-.5F,0)
            .translate(-cameraS,-cameraT,-cameraR).translate(offsetS,offsetT,-cameraR);
        return matrix.transform(new Vector4f(s,t,0,q));
    }
    public static float instability(float stability,int count){return Math.min(6,1+(stability<0?-stability*.66F:1)*(Math.min(count,50)/50F));}
    public static float haloScale(int count){return Math.min(count,50)/1000F;}
    public static float crystalAngle(float ticks,int ordinal,int count){return ticks%720/2+(360/count)*ordinal;}
    public static float crystalBob(float ticks,int ordinal){return (float)Math.sin((ticks+ordinal*10)/12)*.02F+.02F;}
}
