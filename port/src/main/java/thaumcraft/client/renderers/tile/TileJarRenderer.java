package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.tiles.essentia.TileJarFillable;

public final class TileJarRenderer<T extends TileJarFillable> implements BlockEntityRenderer<T> {
    private final ModelPart lid,extension;
    private static final ResourceLocation BRINE = ResourceLocation.parse("thaumcraft:textures/models/jarbrine.png");
    public TileJarRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("lid",CubeListBuilder.create().texOffs(32,24).mirror().addBox(-3,-14,-3,6,2,6),PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("extension",CubeListBuilder.create().texOffs(0,23).mirror().addBox(-2,-16,-2,4,2,4),PartPose.ZERO);
        ModelPart root = LayerDefinition.create(mesh,64,32).bakeRoot(); lid = root.getChild("lid"); extension = root.getChild("extension");
    }
    @Override public void render(T tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if (tile.blocked || tile.getLevel() != null && tile.getLevel().getBlockEntity(tile.getBlockPos().above()) instanceof IEssentiaTransport transport && transport.isConnectable(Direction.DOWN)) {
            poses.pushPose(); poses.translate(.5,.01,.5); poses.mulPose(Axis.XP.rotationDegrees(180));
            var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(BRINE));
            if (tile.blocked) { poses.pushPose(); poses.scale(1.001F,1.001F,1.001F); lid.render(poses,vertices,light,overlay); poses.popPose(); }
            if (tile.getLevel() != null && tile.getLevel().getBlockEntity(tile.getBlockPos().above()) instanceof IEssentiaTransport transport && transport.isConnectable(Direction.DOWN)) { poses.scale(.9F,1F,.9F); extension.render(poses,vertices,light,overlay); }
            poses.popPose();
        }
        if (tile.aspectFilter != null) {
            float rotation = (tile.aspectFilter.getTag().hashCode() + tile.getBlockPos().getX() + tile.facing) % 4 - 2;
            AlchemyRenderSupport.label(poses,buffers,tile.aspectFilter,tile.facing,.41F,.315F,.5F,.336F,ModConfig.CONFIG_GRAPHICS.crooked ? rotation : 0,light);
        }
        if (tile.amount <= 0) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse("thaumcraft:block/animatedglow"));
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float bottom = .0725F, top = bottom + tile.amount / 250F * .625F;
        int color = 0xff000000 | (tile.aspect == null ? 0 : tile.aspect.getColor()), glow = LightTexture.pack(12,12);
        float[][][] faces = {
            {{.25F,bottom,.75F},{.75F,bottom,.75F},{.75F,bottom,.25F},{.25F,bottom,.25F}},
            {{.25F,top,.25F},{.75F,top,.25F},{.75F,top,.75F},{.25F,top,.75F}},
            {{.25F,bottom,.25F},{.75F,bottom,.25F},{.75F,top,.25F},{.25F,top,.25F}},
            {{.75F,bottom,.75F},{.25F,bottom,.75F},{.25F,top,.75F},{.75F,top,.75F}},
            {{.25F,bottom,.75F},{.25F,bottom,.25F},{.25F,top,.25F},{.25F,top,.75F}},
            {{.75F,bottom,.25F},{.75F,bottom,.75F},{.75F,top,.75F},{.75F,top,.25F}}
        };
        Direction[] normals = {Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
        for (int face = 0; face < faces.length; face++) for (int corner = 0; corner < 4; corner++) {
            float[] xyz = faces[face][corner];
            AlchemyRenderSupport.vertex(vertices,poses.last(),xyz[0],xyz[1],xyz[2],corner == 0 || corner == 3 ? sprite.getU0() : sprite.getU1(),corner < 2 ? sprite.getV1() : sprite.getV0(),color,glow,normals[face].getStepX(),normals[face].getStepY(),normals[face].getStepZ());
        }
    }
}
