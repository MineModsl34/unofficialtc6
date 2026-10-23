package thaumcraft.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.List;

/** Native Particle base with TC6 atlas indexing and old expiry-tick physics, not a vanilla visual substitute. */
public class SourceParticle extends Particle implements SourceFxLayer {
    protected float particleScale,particleTextureJitterX,particleTextureJitterY;
    protected int particleTextureIndexX,particleTextureIndexY;
    protected TextureAtlasSprite particleTexture;
    protected static double interpPosX,interpPosY,interpPosZ;
    protected static Vec3 cameraViewDir=Vec3.ZERO;
    public SourceParticle(ClientLevel level,double x,double y,double z) {
        super(level,x,y,z);
        particleTextureJitterX=random.nextFloat()*3;particleTextureJitterY=random.nextFloat()*3;
        particleScale=(random.nextFloat()*.5F+.5F)*2;
        lifetime=(int)(4/(random.nextFloat()*.9F+.1F));
    }
    public SourceParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz) {
        this(level,x,y,z);
        xd=vx+(Math.random()*2-1)*.4F;yd=vy+(Math.random()*2-1)*.4F;zd=vz+(Math.random()*2-1)*.4F;
        float speed=(float)(Math.random()+Math.random()+1)*.15F,d=FxMath.sqrt(xd*xd+yd*yd+zd*zd);
        xd=xd/d*speed*.4F;yd=yd/d*speed*.4F+.1F;zd=zd/d*speed*.4F;
    }
    public int getFXLayer() { return 0; }
    public void setRBGColorF(float r,float g,float b) { setColor(r,g,b); }
    public void setAlphaF(float value) { setAlpha(value); }
    public void setParticleTextureIndex(int index) { particleTextureIndexX=index%16;particleTextureIndexY=index/16; }
    public void setParticleTexture(TextureAtlasSprite sprite) { particleTexture=sprite; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public AABB getRenderBoundingBox(float partial) { return AABB.INFINITE; }
    @Override public void tick() {
        xo=x;yo=y;zo=z;
        if(age++>=lifetime)remove();
        yd-=.04*gravity;move(xd,yd,zd);xd*=.9800000190734863;yd*=.9800000190734863;zd*=.9800000190734863;
        if(onGround){xd*=.699999988079071;zd*=.699999988079071;}
    }
    @Override public void move(double mx,double my,double mz) {
        double requestedX=mx,requestedY=my,requestedZ=mz;
        if(hasPhysics){Vec3 motion=Entity.collideBoundingBox(null,new Vec3(mx,my,mz),getBoundingBox(),level,List.of());mx=motion.x;my=motion.y;mz=motion.z;}
        setBoundingBox(getBoundingBox().move(mx,my,mz));setLocationFromBoundingbox();onGround=requestedY!=my&&requestedY<0;
        if(requestedX!=mx)xd=0;if(requestedZ!=mz)zd=0;
    }
    @Override public void render(VertexConsumer ignored,Camera camera,float partial) {
        renderPrepared(camera,partial,new PoseStack());
    }
    public final void renderPrepared(Camera camera,float partial,PoseStack poses) {
        Vec3 view=camera.getPosition();interpPosX=view.x;interpPosY=view.y;interpPosZ=view.z;
        // Native Camera uses PI-yaw; old ActiveRenderInfo's atlas-right basis has the opposite sign.
        Vector3f right=new Vector3f(-1,0,0).rotate(camera.rotation()),up=new Vector3f(0,1,0).rotate(camera.rotation());
        Vector3f look=camera.getLookVector();cameraViewDir=new Vec3(look.x,look.y,look.z);
        FxPoseState.begin(poses,getFXLayer(),particleTexture==null?ParticleEngine.particleTexture:TextureAtlas.LOCATION_BLOCKS);
        renderSource(FXBuffer.current(),Minecraft.getInstance().getCameraEntity(),partial,right.x,up.y,right.z,up.x,up.z);
        FXBuffer.current().draw();
    }
    public void renderSource(FXBuffer buffer,Entity entity,float partial,float rx,float uy,float rz,float ux,float uz) {
        float minU=particleTextureIndexX/16F,maxU=minU+.0624375F,minV=particleTextureIndexY/16F,maxV=minV+.0624375F;
        if(particleTexture!=null){minU=particleTexture.getU0();maxU=particleTexture.getU1();minV=particleTexture.getV0();maxV=particleTexture.getV1();}
        float s=.1F*particleScale,cx=(float)(xo+(x-xo)*partial-interpPosX),cy=(float)(yo+(y-yo)*partial-interpPosY),cz=(float)(zo+(z-zo)*partial-interpPosZ);
        int light=getLightColor(partial),sky=light>>16&65535,block=light&65535;
        buffer.pos(cx-rx*s-ux*s,cy-uy*s,cz-rz*s-uz*s).tex(maxU,maxV).color(rCol,gCol,bCol,alpha).lightmap(sky,block).endVertex();
        buffer.pos(cx-rx*s+ux*s,cy+uy*s,cz-rz*s+uz*s).tex(maxU,minV).color(rCol,gCol,bCol,alpha).lightmap(sky,block).endVertex();
        buffer.pos(cx+rx*s+ux*s,cy+uy*s,cz+rz*s+uz*s).tex(minU,minV).color(rCol,gCol,bCol,alpha).lightmap(sky,block).endVertex();
        buffer.pos(cx+rx*s-ux*s,cy-uy*s,cz+rz*s-uz*s).tex(minU,maxV).color(rCol,gCol,bCol,alpha).lightmap(sky,block).endVertex();
    }
}
