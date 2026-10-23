package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
import thaumcraft.common.lib.SoundsTC;

/** BETA26 swarm is thirty individually steering atlas particles, not a substitute entity mesh. */
public final class FXSwarm extends Particle implements thaumcraft.client.fx.SourceFxLayer {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private static final ArrayList<Long> BUZZ_COUNT=new ArrayList<>();
    private final Entity target;
    private final float scale,speed,turnSpeed;
    private int deathTimer;
    private float rotationPitch,rotationYaw;
    @Override public int getFXLayer(){return 1;}
    public FXSwarm(ClientLevel level,double x,double y,double z,Entity target,float r,float g,float b,float speed,float turnSpeed,float gravity){
        super(level,x,y,z);this.target=target;this.speed=speed;this.turnSpeed=turnSpeed;this.gravity=gravity;
        rCol=r;gCol=g;bCol=b;scale=random.nextFloat()*.5F+1;setSize(.2F,.2F);
        xd=(random.nextFloat()-random.nextFloat())*.2F;yd=(random.nextFloat()-random.nextFloat())*.2F;zd=(random.nextFloat()-random.nextFloat())*.2F;
    }
    public FXSwarm(ClientLevel level,double x,double y,double z,Entity target,float r,float g,float b){this(level,x,y,z,target,r,g,b,.2F,10,.1F);}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.CUSTOM;}
    private boolean targetAlive(){return target!=null&&!target.isRemoved()&&(!(target instanceof LivingEntity living)||living.deathTime<=0);}
    @Override public void tick(){
        xo=x;yo=y;zo=z;age++;
        if(!targetAlive()){deathTimer++;xd*=.9;zd*=.9;yd-=gravity/2;if(deathTimer>50)remove();}else yd+=gravity;
        move(xd,yd,zd);xd*=.985;yd*=.985;zd*=.985;
        if(targetAlive()){
            boolean hurt=target instanceof LivingEntity living&&living.hurtTime>0;
            float sign=new Vec3(x,y,z).distanceToSqr(target.position())>target.getBbWidth()&&!hurt?1:-1;
            float yawLimit=sign*(turnSpeed/2+random.nextInt((int)(turnSpeed/2)));
            float pitchLimit=sign*(turnSpeed/2+random.nextInt((int)(turnSpeed/2)));
            double dx=target.getX()-x,dz=target.getZ()-z,dy=target.getBoundingBox().getCenter().y-getBoundingBox().getCenter().y;
            rotationPitch=updateRotation(rotationPitch,(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))),pitchLimit);
            rotationYaw=updateRotation(rotationYaw,(float)Math.toDegrees(Math.atan2(dz,dx))-90,yawLimit);
            Vec3 direction=new Vec3(-Mth.sin(rotationYaw*Mth.DEG_TO_RAD)*Mth.cos(rotationPitch*Mth.DEG_TO_RAD),-Mth.sin(rotationPitch*Mth.DEG_TO_RAD),Mth.cos(rotationYaw*Mth.DEG_TO_RAD)*Mth.cos(rotationPitch*Mth.DEG_TO_RAD)).normalize();
            xd=(direction.x+random.nextGaussian()*(random.nextBoolean()?-1:1)*.0075F*15)*speed;
            yd=(direction.y+random.nextGaussian()*(random.nextBoolean()?-1:1)*.0075F*15)*speed;
            zd=(direction.z+random.nextGaussian()*(random.nextBoolean()?-1:1)*.0075F*15)*speed;
        }
        if(BUZZ_COUNT.size()<3&&random.nextInt(50)==0&&level.getNearestPlayer(x,y,z,8,false)!=null){level.playLocalSound(x,y,z,SoundsTC.fly,SoundSource.HOSTILE,.03F,.5F+random.nextFloat()*.4F,false);BUZZ_COUNT.add(System.nanoTime()+1_500_000L);}
        if(BUZZ_COUNT.size()>=3&&BUZZ_COUNT.get(0)<System.nanoTime())BUZZ_COUNT.remove(0);
    }
    // Negative turn limits are intentional: source sequential clamps produce the swarm's circling behavior.
    private static float updateRotation(float current,float requested,float limit){float change=Mth.wrapDegrees(requested-current);if(change>limit)change=limit;if(change< -limit)change= -limit;return current+change;}
    @Override public void render(VertexConsumer ignored,Camera camera,float partial){
        float u=(7+age%8)/64F,v=4/64F,size=.1F*scale*(Mth.sin(age/3F)*.25F+1);
        float divide=target instanceof LivingEntity living&&living.hurtTime>0?2:1,opacity=Math.max(0,(50-deathTimer)/50F);
        PoseStack poses=new PoseStack();Vec3 eye=camera.getPosition();poses.translate(Mth.lerp(partial,xo,x)-eye.x,Mth.lerp(partial,yo,y)-eye.y,Mth.lerp(partial,zo,z)-eye.z);poses.mulPose(camera.rotation());
        var type=WarpEntityRenderTypes.particle(TEXTURE);var buffers=Minecraft.getInstance().renderBuffers().bufferSource();VertexConsumer vertices=buffers.getBuffer(type);
        vertex(vertices,poses.last(),-size,-size,u+1/64F,v+1/64F,divide,opacity);vertex(vertices,poses.last(),-size,size,u+1/64F,v,divide,opacity);
        vertex(vertices,poses.last(),size,size,u,v,divide,opacity);vertex(vertices,poses.last(),size,-size,u,v+1/64F,divide,opacity);buffers.endBatch(type);
    }
    private void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,float divide,float opacity){vertices.addVertex(pose,x,y,0).setColor(rCol,gCol/divide,bCol/divide,opacity).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(240).setNormal(pose,0,1,0);}
}
