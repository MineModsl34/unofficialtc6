package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.projectile.EntityGrapple;

/** Original ModelGrappler cubes and sinusoidal rope path; native four-sided textured tube
 * replaces the unavailable LWJGL CoreGLE polycone. No runtime/physics dependency on visuals.
 */
public final class RenderGrapple extends EntityRenderer<EntityGrapple> {
    private static final ResourceLocation HOOK=ResourceLocation.parse("thaumcraft:textures/entity/grappler.png"),ROPE=ResourceLocation.parse("thaumcraft:textures/misc/rope.png"),PARTICLES=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private final ModelPart model;
    public RenderGrapple(EntityRendererProvider.Context context){
        super(context);MeshDefinition mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("core",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,-1.5F,-1.5F,3,3,3),PartPose.ZERO);
        root.addOrReplaceChild("prong1",CubeListBuilder.create().texOffs(0,10).addBox(-.5F,-.5F,-2.5F,1,1,5),PartPose.ZERO);
        root.addOrReplaceChild("prong2",CubeListBuilder.create().texOffs(0,10).addBox(-.5F,-.5F,-2.5F,1,1,5),PartPose.rotation(0,(float)Math.PI/2,0));
        root.addOrReplaceChild("prong3",CubeListBuilder.create().texOffs(0,10).addBox(-.5F,-.5F,-2.5F,1,1,5),PartPose.rotation((float)Math.PI/2,(float)Math.PI/2,0));
        model=LayerDefinition.create(mesh,64,32).bakeRoot();
    }
    @Override public ResourceLocation getTextureLocation(EntityGrapple entity){return HOOK;}
    @Override public void render(EntityGrapple hook,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial,hook.yRotO,hook.getYRot())-90));poses.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partial,hook.xRotO,hook.getXRot())));
        model.render(poses,buffers.getBuffer(RenderType.entityCutoutNoCull(HOOK)),light,OverlayTexture.NO_OVERLAY);poses.popPose();
        poses.pushPose();poses.mulPose(entityRenderDispatcher.cameraOrientation());float bob=Mth.sin(hook.tickCount/5F)*.2F+.2F;poses.scale(1+bob,1+bob,1+bob);
        float u=(1+hook.tickCount%6)/32F,v=.21875F;var glow=buffers.getBuffer(RenderType.entityTranslucent(PARTICLES));
        sprite(glow,poses.last(),-.5F,-.5F,u,v+1/32F);sprite(glow,poses.last(),.5F,-.5F,u+1/32F,v+1/32F);sprite(glow,poses.last(),.5F,.5F,u+1/32F,v);sprite(glow,poses.last(),-.5F,.5F,u,v);poses.popPose();
        if(hook.getThrower()!=null){
            var points=points(hook,partial);var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE));
            for(int index=0;index<points.size()-1;index++){
                Vec3 a=points.get(index),b=points.get(index+1),direction=b.subtract(a).normalize();if(direction.lengthSqr()<1E-12)continue;
                Vec3 side=direction.cross(Math.abs(direction.y)>.99?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(.025),up=direction.cross(side).normalize().scale(.025);
                Vec3[] around={side,up,side.scale(-1),up.scale(-1)};
                for(int face=0;face<4;face++){int next=(face+1)%4;vertex(vertices,poses.last(),a.add(around[face]),face/4F,index,light);vertex(vertices,poses.last(),b.add(around[face]),face/4F,index+1,light);vertex(vertices,poses.last(),b.add(around[next]),(face+1)/4F,index+1,light);vertex(vertices,poses.last(),a.add(around[next]),(face+1)/4F,index,light);}
            }
        }
        super.render(hook,yaw,partial,poses,buffers,light);
    }
    private static ArrayList<Vec3> points(EntityGrapple hook,float partial){
        var owner=hook.getThrower();double tx=Mth.lerp(partial,owner.xo,owner.getX()),ty=Mth.lerp(partial,owner.yo,owner.getY()),tz=Mth.lerp(partial,owner.zo,owner.getZ());
        if(Minecraft.getInstance().options.getCameraType().isFirstPerson()){
            double yy=Mth.rotLerp(partial,owner.yRotO,owner.getYRot()),angle=(yy-.5)/180*3.141593F,sign=hook.hand()==net.minecraft.world.InteractionHand.MAIN_HAND?1:-1;var look=owner.getLookAngle();
            tx+=-Math.cos(angle)*.1F*sign+look.x/5;ty+=owner.getEyeHeight()/2.6+look.y/5;tz+=-Math.sin(angle)*.1F*sign+look.z/5;
        }
        Vec3 end=new Vec3(tx-Mth.lerp(partial,hook.xo,hook.getX()),ty-Mth.lerp(partial,hook.yo,hook.getY())+owner.getBbHeight()/2F,tz-Mth.lerp(partial,hook.zo,hook.getZ()));
        float length=(float)(end.length()*5);int steps=Math.min(4096,(int)length);var points=new ArrayList<Vec3>();points.add(Vec3.ZERO);
        for(int index=1;index<steps-1;index++){float dist=index*(length/steps),ss=1-index/(steps*1.25F);points.add(end.scale((double)index/steps).add(Mth.sin(dist/10)*hook.ampl*ss,Mth.sin(dist/8)*hook.ampl*ss,Mth.sin(dist/6)*hook.ampl*ss));}
        points.add(end);points.add(end);return points;
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 point,float u,float v,int light){out.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0);}
    private static void sprite(VertexConsumer out,PoseStack.Pose pose,float x,float y,float u,float v){out.addVertex(pose,x,y,0).setColor(255,255,255,54).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose,0,1,0);}
}
