package thaumcraft.client.fx;

import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

/** GLE ANGLE/CAP polycone slice used by TC6 streams. Modern mesh, source bisector-plane intersections and fixed UV slices. */
public final class NativeGLE {
    private int sides=20,style=1042;
    public static void render(PoseStack poses,ResourceLocation texture,boolean ender,boolean additive,boolean depthWrite,
            int tessellation,int join,double[][] points,float[][] colors,double[] radii,float slice,float start) {
        FxPoseState.begin(poses,additive?0:1,texture);FxPoseState.useEnder(ender);FxPoseState.glDepthMask(depthWrite);
        NativeGLE gle=new NativeGLE();gle.set_POLYCYL_TESS(tessellation);gle.set__ROUND_TESS_PIECES(1);gle.gleSetJoinStyle(join);
        gle.glePolyCone(points.length,points,colors,radii,slice,start);FXBuffer.current().draw();
    }
    public void set_POLYCYL_TESS(int value) { sides=Math.max(3,value); }
    public void set__ROUND_TESS_PIECES(int pieces) {
        if(pieces!=1)throw new IllegalArgumentException("TC6 streams use ANGLE join with one round tessellation piece");
    }
    public void gleSetJoinStyle(int value) {
        if((value&15)!=2)throw new IllegalArgumentException("Unsupported source polycone join: "+value);
        style=value;
    }
    public void glePolyCone(int count,double[][] coordinates,float[][] colors,double[] radii,float slice,float start) {
        if(count<4)return;
        Vec3[] p=new Vec3[count];
        for(int i=0;i<count;i++)p[i]=new Vec3(coordinates[i][0],coordinates[i][1],coordinates[i][2]);
        int first=0;while(first+1<count&&p[first+1].distanceToSqr(p[first])<1E-12)first++;
        if(first+1>=count)return;
        Vec3 tangent=p[first+1].subtract(p[first]).normalize();
        Vec3 up=tangent.x==0&&tangent.z==0?new Vec3(1,1,1):new Vec3(0,1,0);
        up=up.subtract(tangent.scale(up.dot(tangent))).normalize();
        FXBuffer buffer=FXBuffer.current();buffer.begin(7,0);float progress=start;
        for(int i=1;i<count-2;i++) {
            Vec3 delta=p[i+1].subtract(p[i]);if(delta.lengthSqr()<1E-12)continue;
            tangent=delta.normalize();Vec3 frontPlane=bisector(p[i-1],p[i],p[i+1]),backPlane=bisector(p[i],p[i+1],p[i+2]);
            up=up.subtract(frontPlane.scale(2*up.dot(frontPlane)));
            Vec3 axis=tangent.cross(up).normalize();
            if(axis.lengthSqr()<1E-12)axis=tangent.cross(Math.abs(tangent.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
            Vec3 perpendicular=tangent.cross(axis).normalize();
            Vec3[] front=new Vec3[sides],back=new Vec3[sides];
            for(int j=0;j<sides;j++) {
                double angle=j*Math.PI*2/sides;Vec3 radial=axis.scale(Math.cos(angle)).add(perpendicular.scale(Math.sin(angle)));
                front[j]=intersect(p[i],radial.scale(radii[i]),tangent,frontPlane);
                back[j]=intersect(p[i+1],radial.scale(radii[i+1]),tangent,backPlane);
            }
            for(int j=0;j<sides;j++) {
                int next=(j+1)%sides;float u=j/(float)sides,un=(j+1)/(float)sides;
                emit(buffer,front[j],colors[i],u,progress);emit(buffer,back[j],colors[i+1],u,progress+slice);
                emit(buffer,back[next],colors[i+1],un,progress+slice);emit(buffer,front[next],colors[i],un,progress);
            }
            if((style&16)!=0&&i==1)cap(buffer,front,colors[i],true,progress);
            if((style&16)!=0&&i==count-3)cap(buffer,back,colors[i+1],false,progress+slice);
            progress+=slice;
        }
        buffer.draw();
    }
    private static Vec3 bisector(Vec3 before,Vec3 center,Vec3 after) {
        Vec3 incoming=center.subtract(before).normalize(),outgoing=after.subtract(center).normalize();
        double dot=incoming.dot(outgoing);return Math.abs(dot)>=.999998?incoming:incoming.add(outgoing).normalize();
    }
    private static Vec3 intersect(Vec3 center,Vec3 radial,Vec3 axis,Vec3 normal) {
        double d=axis.dot(normal);return center.add(radial).add(axis.scale(Math.abs(d)<1E-12?0:-radial.dot(normal)/d));
    }
    private static void emit(FXBuffer buffer,Vec3 p,float[] color,float u,float v) {
        buffer.pos(p.x,p.y,p.z).tex(u,v).color(color[0],color[1],color[2],color[3]).lightmap(0,240).endVertex();
    }
    private static void cap(FXBuffer buffer,Vec3[] ring,float[] color,boolean reverse,float v) {
        for(int j=1;j<ring.length-1;j++) {
            emit(buffer,ring[0],color,0,v);emit(buffer,ring[reverse?j+1:j],color,j/(float)ring.length,v);
            emit(buffer,ring[reverse?j:j+1],color,(j+1)/(float)ring.length,v);emit(buffer,ring[reverse?j:j+1],color,(j+1)/(float)ring.length,v);
        }
    }
}
