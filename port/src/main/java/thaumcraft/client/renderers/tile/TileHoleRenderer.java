package thaumcraft.client.renderers.tile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.misc.TileHole;
/** Source face visibility and inset .499 aperture with TC's own ender algorithm. */
public final class TileHoleRenderer implements BlockEntityRenderer<TileHole>{
    public TileHoleRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(TileHole tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.getLevel()==null)return;var out=buffers.getBuffer(thaumcraft.client.lib.ender.SourceRenderTypes.ender(false,false,true));poses.pushPose();poses.translate(.5,.5,.5);
        for(Direction face:Direction.values()){var pos=tile.getBlockPos().relative(face);var state=tile.getLevel().getBlockState(pos);if(state.is(BlocksTC.hole)||!state.isSolidRender(tile.getLevel(),pos))continue;
            poses.pushPose();poses.mulPose(new org.joml.Quaternionf().rotationAxis((float)Math.PI/2,-face.getStepY(),face.getStepX(),-face.getStepZ()));
            if(face.getStepZ()<0){poses.translate(0,0,-.499F);poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));}else poses.translate(0,0,.499F);
            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));var pose=poses.last();
            out.addVertex(pose,-.5F,.5F,0).setUv(1,1);out.addVertex(pose,.5F,.5F,0).setUv(1,0);out.addVertex(pose,.5F,-.5F,0).setUv(0,0);out.addVertex(pose,-.5F,-.5F,0).setUv(0,1);poses.popPose();
        }poses.popPose();
    }
}
