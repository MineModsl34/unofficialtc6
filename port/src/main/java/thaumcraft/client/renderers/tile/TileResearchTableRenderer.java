package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.common.blocks.basic.BlockResearchTable;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Source dynamic scroll/inkwell/quill only; the original tabletop/legs remain their baked JSON. */
public final class TileResearchTableRenderer implements BlockEntityRenderer<TileResearchTable> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/block/research_table_model.png"),QUILL=ResourceLocation.parse("thaumcraft:research/quill");
    private final ModelPart model;
    public TileResearchTableRenderer(BlockEntityRendererProvider.Context context){MeshDefinition mesh=new MeshDefinition();var root=mesh.getRoot();/* Source mirror=true occurs AFTER addBox, so ModelBox already captured false. */root.addOrReplaceChild("inkwell",CubeListBuilder.create().texOffs(0,16).addBox(0,0,0,3,2,3),PartPose.offset(-6,-2,3));root.addOrReplaceChild("scroll",CubeListBuilder.create().texOffs(0,0).addBox(-8,-.5F,0,8,2,2),PartPose.offsetAndRotation(-2,-2,2,0,10,0));root.addOrReplaceChild("ribbon",CubeListBuilder.create().texOffs(0,4).addBox(-4.25F,-.275F,0,1,2,2),PartPose.offsetAndRotation(-2,-2,2,0,10,0));model=LayerDefinition.create(mesh,64,32).bakeRoot();}
    @Override public void render(TileResearchTable table,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){poses.pushPose();poses.translate(.5,1,.5);poses.mulPose(Axis.XP.rotationDegrees(180));poses.mulPose(Axis.YP.rotationDegrees(switch(table.getBlockState().getValue(BlockResearchTable.FACING)){case EAST->90;case WEST->270;case SOUTH->180;default->0;}));var out=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        if(table.hasTheoryNote()){model.getChild("scroll").render(poses,out,light,overlay);poses.pushPose();poses.scale(1.2F,1.2F,1.2F);model.getChild("ribbon").render(poses,out,light,overlay,0xff000000|Aspect.ALCHEMY.getColor());poses.popPose();}
        var tools=table.getItem(0);if(!tools.isEmpty()&&tools.getItem() instanceof IScribeTools){model.getChild("inkwell").render(poses,out,light,overlay);poses.pushPose();poses.mulPose(Axis.XP.rotationDegrees(180));poses.translate(-.5,.1,.125);poses.mulPose(Axis.YP.rotationDegrees(60));poses.scale(.5F,.5F,.5F);extrudeQuill(poses,buffers,light,overlay);poses.popPose();}poses.popPose();
    }
    private static void extrudeQuill(PoseStack poses,MultiBufferSource buffers,int light,int overlay){var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(QUILL);var out=buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));var p=poses.last();float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1(),depth=.0625F;
        // Front/back and every pixel strip, matching source ItemRenderer.renderItemIn2D depth.
        vertex(out,p,0,0,0,u1,v1,light,overlay,0,0,1);vertex(out,p,1,0,0,u0,v1,light,overlay,0,0,1);vertex(out,p,1,1,0,u0,v0,light,overlay,0,0,1);vertex(out,p,0,1,0,u1,v0,light,overlay,0,0,1);
        vertex(out,p,0,1,-depth,u1,v0,light,overlay,0,0,-1);vertex(out,p,1,1,-depth,u0,v0,light,overlay,0,0,-1);vertex(out,p,1,0,-depth,u0,v1,light,overlay,0,0,-1);vertex(out,p,0,0,-depth,u1,v1,light,overlay,0,0,-1);
        int width=sprite.contents().width(),height=sprite.contents().height();for(int i=0;i<width;i++){float x=i/(float)width,x2=(i+1)/(float)width,u=sprite.getU(1-(i+.5F)/width);for(float edge:new float[]{x,x2}){vertex(out,p,edge,0,-depth,u,v1,light,overlay,1,0,0);vertex(out,p,edge,0,0,u,v1,light,overlay,1,0,0);vertex(out,p,edge,1,0,u,v0,light,overlay,1,0,0);vertex(out,p,edge,1,-depth,u,v0,light,overlay,1,0,0);}}for(int i=0;i<height;i++){float y=i/(float)height,y2=(i+1)/(float)height,v=sprite.getV(1-(i+.5F)/height);for(float edge:new float[]{y,y2}){vertex(out,p,0,edge,0,u1,v,light,overlay,0,1,0);vertex(out,p,0,edge,-depth,u1,v,light,overlay,0,1,0);vertex(out,p,1,edge,-depth,u0,v,light,overlay,0,1,0);vertex(out,p,1,edge,0,u0,v,light,overlay,0,1,0);}}
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose p,float x,float y,float z,float u,float v,int light,int overlay,float nx,float ny,float nz){out.addVertex(p,x,y,z).setColor(-1).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(p,nx,ny,nz);}
}
