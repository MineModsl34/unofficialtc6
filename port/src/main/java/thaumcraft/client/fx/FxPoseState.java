package thaumcraft.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** Port of immediate-mode matrix/state operations to a private PoseStack; no compatibility OpenGL calls. */
public final class FxPoseState {
    static PoseStack poses = new PoseStack();
    static ResourceLocation texture = ParticleEngine.particleTexture;
    static boolean additive, depthTest=true, depthWrite, ender, gui;
    private FxPoseState() {}
    static void begin(PoseStack pose, int layer, ResourceLocation initial) {
        poses=pose; texture=initial; additive=(layer&1)==0; depthTest=layer<2; depthWrite=false; ender=false; gui=layer>=4;
    }
    public static void bindTexture(ResourceLocation value) { FXBuffer.current().draw(); texture=value; }
    public static void glPushMatrix() { poses.pushPose(); }
    public static void glPopMatrix() { poses.popPose(); }
    public static void glTranslated(double x,double y,double z) { poses.translate(x,y,z); }
    public static void glTranslatef(float x,float y,float z) { poses.translate(x,y,z); }
    public static void glRotatef(float degrees,float x,float y,float z) { glRotated(degrees,x,y,z); }
    public static void glRotated(double degrees,double x,double y,double z) {
        double size=Math.sqrt(x*x+y*y+z*z);
        if(size!=0) poses.mulPose(new Quaternionf().rotationAxis((float)Math.toRadians(degrees),(float)(x/size),(float)(y/size),(float)(z/size)));
    }
    public static void glScaled(double x,double y,double z) { poses.scale((float)x,(float)y,(float)z); }
    public static void glDepthMask(boolean value) { FXBuffer.current().draw(); depthWrite=value; }
    public static void depthMask(boolean value) { glDepthMask(value); }
    public static void glBlendFunc(int source,int destination) { FXBuffer.current().draw(); additive=destination==1; }
    public static void blendFunc(int source,int destination) { glBlendFunc(source,destination); }
    public static void glEnable(int capability) { if(capability==2929) enableDepth(); }
    public static void glDisable(int capability) { if(capability==2929) disableDepth(); }
    public static void enableDepth() { FXBuffer.current().draw(); depthTest=true; }
    public static void disableDepth() { FXBuffer.current().draw(); depthTest=false; }
    // All source vertices explicitly supply RGBA; fixed-function current color is superseded by that vertex attribute.
    public static void glColor4f(float r,float g,float b,float a) {}
    public static void enableBlend() {}
    public static void disableBlend() {}
    public static void glTexParameterf(int target,int parameter,float value) {
        // Source beams wrap their scrolling texture; native AbstractTexture's default wrap is REPEAT.
    }
    public static void useEnder(boolean value) { FXBuffer.current().draw(); ender=value; }
}
