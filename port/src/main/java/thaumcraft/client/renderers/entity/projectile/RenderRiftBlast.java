package thaumcraft.client.renderers.entity.projectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.client.fx.NativeGLE;
import thaumcraft.common.entities.projectile.EntityRiftBlast;
/** Literal source three-sided capped angle-join polycone, fixed UV slices and 1.5/.5 passes. */
public final class RenderRiftBlast extends EntityRenderer<EntityRiftBlast> {
    private static final ResourceLocation STARS=ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
    public RenderRiftBlast(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(EntityRiftBlast entity){return STARS;}
    @Override public void render(EntityRiftBlast entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        int count=entity.trail.size();if(count<=2)return;double[][] points=new double[count][3];float[][] colors=new float[count][4];double[] radii=new double[count];var current=entity.getPosition(partial);
        for(int i=0;i<count;i++){var p=entity.trail.get(i);points[i][0]=p.x+Mth.sin((i+entity.tickCount)/6F)*.01-current.x;points[i][1]=p.y+Mth.sin((i+entity.tickCount)/7F)*.01-current.y;points[i][2]=p.z+Mth.sin((i+entity.tickCount)/8F)*.01-current.z;
            radii[i]=.1*(1+Mth.sin((i+entity.tickCount)/3F)*.2)*Mth.sin(i*(float)Math.PI/(count-1));colors[i]=new float[]{1,0,0,1};}
        for(int pass=0;pass<2;pass++){double[] widths=new double[count];for(int i=0;i<count;i++)widths[i]=radii[i]*(1.5F-pass);NativeGLE.render(poses,STARS,true,pass==0,pass!=0,3,1042,points,colors,widths,1F/count,0);}
        super.render(entity,yaw,partial,poses,buffers,light);
    }
}
