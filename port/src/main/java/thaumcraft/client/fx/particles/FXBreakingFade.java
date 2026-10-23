package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** TC source fading slime-ball debris, terrain atlas UV crop and disabled depth writes. */
public final class FXBreakingFade extends BreakingItemParticle implements thaumcraft.client.fx.SourceFxLayer {
    private static final RenderType TYPE=WarpEntityRenderTypes.particle(TextureAtlas.LOCATION_BLOCKS);
    @Override public int getFXLayer(){return 1;}
    public FXBreakingFade(ClientLevel level,double x,double y,double z,int lifetime) {
        super(level,x,y,z,new ItemStack(Items.SLIME_BALL));
        rCol=.7F; gCol=0; bCol=1; alpha=.4F; this.lifetime=lifetime;
    }
    public FXBreakingFade(ClientLevel level,double x,double y,double z,Item item,int sourceMetadata) {
        super(level,x,y,z,new ItemStack(item));
    }
    public FXBreakingFade(ClientLevel level,double x,double y,double z,Item item) { this(level,x,y,z,item,0); }
    public FXBreakingFade(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,Item item,int sourceMetadata) {
        super(level,x,y,z,vx,vy,vz,new ItemStack(item));
    }
    public void setParticleMaxAge(int lifetime) { this.lifetime=lifetime; }
    public void setParticleGravity(float gravity) { this.gravity=gravity; }
    public void setRBGColorF(float r,float g,float b) { setColor(r,g,b); }
    public void setAlphaF(float alpha) { setAlpha(alpha); }
    public void setSpeed(double x,double y,double z) { xd=x;yd=y;zd=z; }
    public void boom() {
        float speed=(float)(Math.random()+Math.random()+1)*.15F;
        float size=thaumcraft.client.fx.FxMath.sqrt(xd*xd+yd*yd+zd*zd);
        xd=xd/size*speed*.9640000000596046;yd=yd/size*speed*.9640000000596046+.1F;zd=zd/size*speed*.9640000000596046;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(age++>=lifetime) remove();yd-=.04*gravity;move(xd,yd,zd);
        xd*=.9800000190734863;yd*=.9800000190734863;zd*=.9800000190734863;
        if(onGround){xd*=.699999988079071;zd*=.699999988079071;}
    }
    @Override public void render(VertexConsumer ignored,Camera camera,float partial) {
        PoseStack poses=new PoseStack();var eye=camera.getPosition();
        poses.translate(Mth.lerp(partial,xo,x)-eye.x,Mth.lerp(partial,yo,y)-eye.y,Mth.lerp(partial,zo,z)-eye.z);poses.mulPose(camera.rotation());
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var vertices=buffers.getBuffer(TYPE);
        float fade=alpha*(1-(float)age/lifetime), size=quadSize;
        // BreakingItemParticle's U getters are reversed relative to the original FXBreakingFade.
        vertex(vertices,poses.last(),-size,-size,getU1(),getV1(),fade,partial);
        vertex(vertices,poses.last(),-size,size,getU1(),getV0(),fade,partial);
        vertex(vertices,poses.last(),size,size,getU0(),getV0(),fade,partial);
        vertex(vertices,poses.last(),size,-size,getU0(),getV1(),fade,partial);buffers.endBatch(TYPE);
    }
    private void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float opacity,float partial) {
        vertices.addVertex(pose,x,y,0).setColor(rCol,gCol,bCol,opacity).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(getLightColor(partial)).setNormal(pose,0,1,0);
    }
}
