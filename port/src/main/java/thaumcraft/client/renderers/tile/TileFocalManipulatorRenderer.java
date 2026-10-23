package thaumcraft.client.renderers.tile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.lib.ender.SourceRenderMath;
import thaumcraft.client.lib.ender.SourceRenderTypes;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;
/** Source orbiting paid crystals, 64x64 wisp frames, two seeded rays and floating focus. */
public final class TileFocalManipulatorRenderer implements BlockEntityRenderer<TileFocalManipulator>{
    private static final ResourceLocation PARTICLES=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    public TileFocalManipulatorRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(TileFocalManipulator tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(tile.getLevel()==null)return;var mc=Minecraft.getInstance();var camera=mc.getCameraEntity();float ticks=camera==null?0:camera.tickCount+partial;
        ItemStack focus=tile.getItem(0);if(!focus.isEmpty()){poses.pushPose();poses.translate(.5,.8,.5);poses.mulPose(Axis.YP.rotationDegrees(ticks%360));item(tile,focus,poses,buffers,light,Mth.sin(ticks/14)*.2F+.2F);poses.popPose();}
        // Source crystalsSync is a paid crafting snapshot. The native synchronized validated
        // executable graph has exactly those aspects while busy; never show unpaid draft crystals.
        if(!tile.busy())return;var aspects=new LinkedHashSet<Aspect>();tile.data.values().stream().sorted(java.util.Comparator.comparingInt(row->row.id)).forEach(row->{if(row.node!=null&&row.node.getAspect()!=null)aspects.add(row.node.getAspect());});
        int ordinal=0,count=aspects.size();for(Aspect aspect:aspects){float angle=SourceRenderMath.crystalAngle(ticks,ordinal,count),bob=SourceRenderMath.crystalBob(ticks,ordinal);float red=(aspect.getColor()>>16&255)/255F,green=(aspect.getColor()>>8&255)/255F,blue=(aspect.getColor()&255)/255F;
            poses.pushPose();poses.translate(.5,1.3,.5);poses.mulPose(Axis.YP.rotationDegrees(angle));poses.translate(0,bob,.4);poses.mulPose(Axis.YP.rotationDegrees(-angle));poses.mulPose(mc.gameRenderer.getMainCamera().rotation());
            int frame=320+(camera==null?0:camera.tickCount%16);float u=frame%64/64F,v=frame/64/64F;int color=0xa8000000|aspect.getColor();var vertices=buffers.getBuffer(SourceRenderTypes.additive(PARTICLES,false));
            AlchemyRenderSupport.quad(poses,vertices,.35F,color,LightTexture.FULL_BRIGHT,u+1/64F,v,u,v+1/64F);poses.popPose();
            poses.pushPose();poses.translate(.5,1.05,.5);poses.mulPose(Axis.YP.rotationDegrees(angle));poses.translate(0,bob,.4);poses.scale(.5F,.5F,.5F);
            SourceRayMeshes.focal(poses,buffers,angle,ordinal,bob,red,green,blue,ticks);SourceRayMeshes.focal(poses,buffers,angle,(ordinal+1)*5,bob,red,green,blue,ticks);
            item(tile,ThaumcraftApiHelper.makeCrystal(aspect),poses,buffers,light,0);poses.popPose();ordinal++;
        }
    }
    private static void item(TileFocalManipulator tile,ItemStack stack,PoseStack poses,MultiBufferSource buffers,int light,float hover){
        var renderer=Minecraft.getInstance().getItemRenderer();var model=renderer.getModel(stack,tile.getLevel(),null,0);poses.pushPose();poses.translate(0,Mth.sin(hover)*.1F+.1F+.25F*model.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y(),0);poses.mulPose(Axis.YP.rotation(hover));renderer.renderStatic(stack,ItemDisplayContext.GROUND,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,poses,buffers,tile.getLevel(),0);poses.popPose();
    }
    @Override public boolean shouldRenderOffScreen(TileFocalManipulator tile){return true;}
}
