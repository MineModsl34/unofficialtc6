package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.blocks.devices.BlockBellows;
import thaumcraft.common.tiles.devices.TileBellows;

/** ModelBellows source geometry and inflation; old GL state is replaced with native ModelPart. */
public final class TileBellowsRenderer implements BlockEntityRenderer<TileBellows> {
    private final ModelPart bottom,middle,top,bag,nozzle;
    public TileBellowsRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("bottom",CubeListBuilder.create().texOffs(0,0).mirror().addBox(-6,0,-6,12,2,12),PartPose.offset(0,22,0));
        root.addOrReplaceChild("middle",CubeListBuilder.create().texOffs(0,0).mirror().addBox(-6,-1,-6,12,2,12),PartPose.offset(0,16,0));
        root.addOrReplaceChild("top",CubeListBuilder.create().texOffs(0,0).mirror().addBox(-6,0,-6,12,2,12),PartPose.offset(0,8,0));
        root.addOrReplaceChild("bag",CubeListBuilder.create().texOffs(48,0).mirror().addBox(-10,-12.03333F,-10,20,24,20),PartPose.offset(0,.5F,0));
        root.addOrReplaceChild("nozzle",CubeListBuilder.create().texOffs(0,36).mirror().addBox(-2,-2,0,4,4,2),PartPose.offset(0,16,6));
        ModelPart model = LayerDefinition.create(mesh,128,128).bakeRoot(); bottom = model.getChild("bottom"); middle = model.getChild("middle"); top = model.getChild("top"); bag = model.getChild("bag"); nozzle = model.getChild("nozzle");
    }
    @Override public void render(TileBellows tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        var vertices = buffers.getBuffer(RenderType.entityCutout(ResourceLocation.parse("thaumcraft:textures/block/bellows.png")));
        poses.pushPose(); poses.translate(.5,-.5,.5);
        switch(tile.getBlockState().getValue(BlockBellows.FACING)) {
            case DOWN -> { poses.translate(0,1,-1); poses.mulPose(Axis.XP.rotationDegrees(90)); }
            case UP -> { poses.translate(0,1,1); poses.mulPose(Axis.XP.rotationDegrees(270)); }
            case NORTH -> poses.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> poses.mulPose(Axis.YP.rotationDegrees(270));
            case EAST -> poses.mulPose(Axis.YP.rotationDegrees(90));
            default -> {}
        }
        float scale = tile.inflation,tscale = .125F + scale * .875F;
        poses.pushPose(); poses.translate(0,1,0); poses.scale(.5F,(scale + .1F) / 2,.5F); bag.render(poses,vertices,light,overlay); poses.popPose();
        poses.pushPose(); poses.translate(0,-tscale / 2 + .5,0); top.render(poses,vertices,light,overlay); poses.popPose();
        poses.pushPose(); poses.translate(0,tscale / 2 - .5,0); bottom.render(poses,vertices,light,overlay); poses.popPose();
        middle.render(poses,vertices,light,overlay); nozzle.render(poses,vertices,light,overlay); poses.popPose();
    }
}
