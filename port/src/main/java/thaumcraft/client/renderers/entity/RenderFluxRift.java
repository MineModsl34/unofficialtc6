package thaumcraft.client.renderers.entity;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.lib.events.WearableEvents;

/** Source six-sided procedural polycone, four oscillating radius passes and goggle depth reveal. */
public final class RenderFluxRift extends EntityRenderer<EntityFluxRift> {
    public RenderFluxRift(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(EntityFluxRift rift){return RiftRenderTypes.STARS;}
    @Override public void render(EntityFluxRift rift,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        int count=rift.points.size();if(count<=2)return;var player=Minecraft.getInstance().player;boolean goggles=player!=null&&WearableEvents.hasGoggles(player);float instability=Mth.clamp(1-rift.getRiftStability()/50,0,1.5F);
        for(int pass=0;pass<4;pass++){
            Vec3[] centers=new Vec3[count];double[] radii=new double[count];
            for(int i=0;i<count;i++){float time=rift.tickCount+partial;if(i>count/2)time-=i*10;else if(i<count/2)time+=i*10;
                centers[i]=rift.points.get(i).add(Math.sin(time/50F)*.1F*instability,Math.sin(time/60F)*.1F*instability,Math.sin(time/70F)*.1F*instability);
                radii[i]=rift.pointsWidth.get(i)*(1-Math.sin(time/8F)*.1F*instability)*(pass<3?1.25F+.5F*pass:1);
            }
            var consumer=buffers.getBuffer(pass<3?RiftRenderTypes.halo(pass==0&&goggles):RiftRenderTypes.portal());
            drawTube(consumer,poses.last(),centers,radii,false);
        }
        super.render(rift,yaw,partial,poses,buffers,light);
    }
    private static void drawTube(VertexConsumer vertices,PoseStack.Pose pose,Vec3[] points,double[] radii,boolean textured){
        // GLE ANGLE join1026: end points guide the first/last tangent, each segment intersects
        // its two circular contours with the bend's bisector plane. They are not flat rings.
        Vec3 first=points[1].subtract(points[0]).normalize();Vec3 up=first.x==0&&first.z==0?new Vec3(1,1,1):new Vec3(0,1,0);
        up=up.subtract(first.scale(up.dot(first))).normalize();double slice=0;
        for(int i=1;i<points.length-2;i++){
            Vec3 delta=points[i+1].subtract(points[i]);double length=delta.length();if(length<1E-6)continue;Vec3 tangent=delta.scale(1/length);
            Vec3 frontNormal=bisector(points[i-1],points[i],points[i+1]),backNormal=bisector(points[i],points[i+1],points[i+2]);
            up=up.subtract(frontNormal.scale(2*up.dot(frontNormal)));Vec3 u=tangent.cross(up).normalize();if(u.lengthSqr()<1E-8)u=tangent.cross(Math.abs(tangent.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();Vec3 v=tangent.cross(u).normalize();
            Vec3[] front=new Vec3[6],back=new Vec3[6],normals=new Vec3[6];
            for(int side=0;side<6;side++){double angle=side*Math.PI/3;var radial=u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));normals[side]=radial;front[side]=miter(points[i],radial.scale(radii[i]),tangent,frontNormal);back[side]=miter(points[i+1],radial.scale(radii[i+1]),tangent,backNormal);}
            for(int side=0;side<6;side++){int next=(side+1)%6;vertex(vertices,pose,front[side],normals[side],side/6F,(float)slice,textured);vertex(vertices,pose,back[side],normals[side],side/6F,(float)(slice+1),textured);vertex(vertices,pose,back[next],normals[next],(side+1)/6F,(float)(slice+1),textured);vertex(vertices,pose,front[next],normals[next],(side+1)/6F,(float)slice,textured);}slice++;
        }
    }
    private static Vec3 bisector(Vec3 before,Vec3 center,Vec3 after){var incoming=center.subtract(before).normalize();var outgoing=after.subtract(center).normalize();double dot=incoming.dot(outgoing);return Math.abs(dot)>=.999998?incoming:incoming.add(outgoing).normalize();}
    private static Vec3 miter(Vec3 center,Vec3 radial,Vec3 axis,Vec3 planeNormal){double denominator=axis.dot(planeNormal);return center.add(radial).add(axis.scale(Math.abs(denominator)<1E-8?0:-radial.dot(planeNormal)/denominator));}
    private static void vertex(VertexConsumer vertices,PoseStack.Pose pose,Vec3 point,Vec3 normal,float u,float v,boolean textured){
        vertices.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setUv(u,v);
    }
}
