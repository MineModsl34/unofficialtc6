package thaumcraft.client.fx.other;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.SourceParticle;
import thaumcraft.client.lib.warp.SourceSonicMesh;
import thaumcraft.common.entities.monster.cult.EntityCultist;

/** TC6 hemis.obj shield/pedestal: source facing, entity-centre tracking and fifteen real frames. */
public final class FXShieldRunes extends SourceParticle {
    private static final ResourceLocation MODEL=ResourceLocation.parse("thaumcraft:models/obj/hemis.obj");
    private static SourceSonicMesh model;
    private final Entity target;
    private final float yaw,pitch;
    public FXShieldRunes(ClientLevel level,double x,double y,double z,Entity target,int duration,float yaw,float pitch) {
        super(level,x,y,z,0,0,0);this.target=target;this.yaw=yaw;this.pitch=pitch;
        xd=yd=zd=0;gravity=0;lifetime=duration+random.nextInt(Math.max(1,duration/2));particleScale=1;setSize(.01F,.01F);
        if(target!=null){setPos(target.getX(),target.getBoundingBox().getCenter().y,target.getZ());xo=this.x;yo=this.y;zo=this.z;}
    }
    public static void reload() { model=null; }
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(age++>=lifetime)remove();
        if(target!=null){x=target.getX();y=target.getBoundingBox().getCenter().y;z=target.getZ();}
    }
    @Override public void renderSource(FXBuffer buffer,Entity viewer,float partial,float rx,float uy,float rz,float ux,float uz) {
        if(model==null)try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(MODEL)){model=SourceSonicMesh.read(reader);}
        catch(IOException failure){throw new IllegalStateException("Cannot load source shield hemisphere",failure);}
        float fade=(age+partial)/lifetime;int frame=Math.min(15,(int)(14*fade)+1);
        boolean ripple=target instanceof Monster&&!(target instanceof EntityCultist);
        FxPoseState.bindTexture(ResourceLocation.parse("thaumcraft:textures/models/"+(ripple?"ripple":"hemis")+frame+".png"));
        FxPoseState.glBlendFunc(770,1);FxPoseState.glDepthMask(false);FxPoseState.glPushMatrix();
        FxPoseState.glTranslated(xo+(x-xo)*partial-interpPosX,yo+(y-yo)*partial-interpPosY,zo+(z-zo)*partial-interpPosZ);
        FxPoseState.glRotatef(180-yaw,0,1,0);FxPoseState.glRotatef(-pitch,1,0,0);
        double scale=.2*(target==null?2:target.getBbHeight());FxPoseState.glScaled(scale,scale,scale);
        float tint=ripple?.5F:1,red=target==null?.65F:tint,green=target==null?.1F:tint,blue=target==null?.5F:tint,opacity=Math.min(1,(1-fade)*3);
        buffer.begin(7,0);
        for(var face:model.triangles()) {
            vertex(buffer,face.a(),red,green,blue,opacity);vertex(buffer,face.b(),red,green,blue,opacity);
            vertex(buffer,face.c(),red,green,blue,opacity);vertex(buffer,face.c(),red,green,blue,opacity);
        }
        buffer.draw();FxPoseState.glPopMatrix();FxPoseState.bindTexture(ParticleEngine.particleTexture);
    }
    private static void vertex(FXBuffer buffer,SourceSonicMesh.Corner point,float r,float g,float b,float a) {
        buffer.pos(point.x(),point.y(),point.z()).tex(point.u(),point.v()).color(r,g,b,a).lightmap(0,220).endVertex();
    }
}
