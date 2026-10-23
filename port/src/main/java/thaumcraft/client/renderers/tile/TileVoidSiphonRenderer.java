package thaumcraft.client.renderers.tile;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import org.joml.Quaternionf;
import thaumcraft.common.blocks.crafting.BlockVoidSiphon;
import thaumcraft.common.tiles.crafting.TileVoidSiphon;
import thaumcraft.client.renderers.entity.RiftRenderTypes;

/** Source upper six star windows and lower six elongated windows, over the real JSON block model. */
public final class TileVoidSiphonRenderer implements BlockEntityRenderer<TileVoidSiphon>{
    public TileVoidSiphonRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(TileVoidSiphon tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(!tile.getBlockState().getValue(BlockVoidSiphon.ENABLED))return;
        for(int part=0;part<2;part++){var vertices=buffers.getBuffer(RiftRenderTypes.siphon(part==0));poses.pushPose();poses.translate(.5,part==0?.875:.3125,.5);
            for(Direction face:Direction.values()){poses.pushPose();poses.mulPose(new Quaternionf().rotationAxis((float)Math.PI/2,-face.getStepY(),face.getStepX(),-face.getStepZ()));
                double offset=part==0?.126:.26;if(face.getStepZ()<0){poses.translate(0,0,offset);poses.mulPose(Axis.YP.rotationDegrees(180));}else poses.translate(0,0,-offset);
                if(part==0||face.getStepZ()!=0)poses.mulPose(Axis.ZP.rotationDegrees(-90));if(part==0)poses.scale(.2F,.2F,.2F);else poses.scale(.25F,.5F,.25F);
                var pose=poses.last();vertices.addVertex(pose,-.5F,-.5F,0).setUv(0,1);vertices.addVertex(pose,.5F,-.5F,0).setUv(0,0);vertices.addVertex(pose,.5F,.5F,0).setUv(1,0);vertices.addVertex(pose,-.5F,.5F,0).setUv(1,1);poses.popPose();
            }poses.popPose();
        }
    }
}
