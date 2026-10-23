package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.LightTexture;
import thaumcraft.client.lib.ender.SourceRenderTypes;
import thaumcraft.client.lib.ender.SourceRenderMath;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;

/** Source eight ModelCube(0/32) parts, additive overlay, startup jitter and seeded craft halo. */
public final class TileInfusionMatrixRenderer implements BlockEntityRenderer<TileInfusionMatrix> {
    private final ModelPart cube;
    private final ModelPart glow;
    public TileInfusionMatrixRenderer(BlockEntityRendererProvider.Context context) {
        // The source sets ModelRenderer.mirror after addBox; ModelBox already baked its UVs.
        MeshDefinition mesh = new MeshDefinition(); mesh.getRoot().addOrReplaceChild("cube",CubeListBuilder.create().texOffs(0,0).addBox(-8,-8,-8,16,16,16),PartPose.ZERO);
        cube = LayerDefinition.create(mesh,64,64).bakeRoot().getChild("cube");
        MeshDefinition glowMesh=new MeshDefinition();glowMesh.getRoot().addOrReplaceChild("cube",CubeListBuilder.create().texOffs(0,32).addBox(-8,-8,-8,16,16,16),PartPose.ZERO);
        glow=LayerDefinition.create(glowMesh,64,64).bakeRoot().getChild("cube");
    }
    @Override public void render(TileInfusionMatrix tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        float ticks = Minecraft.getInstance().getCameraEntity() == null ? 0 : Minecraft.getInstance().getCameraEntity().tickCount + partialTick;
        String texture = "infuser_normal";
        if (tile.getLevel() != null) { var corner = tile.getLevel().getBlockState(tile.getBlockPos().offset(-1,-2,-1)); if (corner.is(BlocksTC.pillarAncient)) texture = "infuser_ancient"; if (corner.is(BlocksTC.pillarEldritch)) texture = "infuser_eldritch"; }
        var textureLocation = ResourceLocation.parse("thaumcraft:textures/block/" + texture + ".png");
        var baseType = RenderType.entityCutout(textureLocation);
        poses.pushPose(); poses.translate(.5,.5,.5);
        poses.mulPose(Axis.YP.rotationDegrees(ticks % 360 * tile.startUp)); poses.mulPose(Axis.XP.rotationDegrees(35 * tile.startUp)); poses.mulPose(Axis.ZP.rotationDegrees(45 * tile.startUp));
        float instability = SourceRenderMath.instability(tile.stability,tile.craftCount);
        // A native shared-buffer consumer expires when another RenderType begins.
        // Complete all eight base cubes before obtaining the glow consumer.
        for (int pass = 0; pass < (tile.active ? 2 : 1); pass++) {
            var vertices = buffers.getBuffer(pass == 0 ? baseType : SourceRenderTypes.additive(textureLocation,true));
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int c = 0; c < 2; c++) {
                float x = tile.active ? Mth.sin((ticks + a * 10) / 15) * .01F * tile.startUp * instability : 0;
                float y = tile.active ? Mth.sin((ticks + b * 10) / 14) * .01F * tile.startUp * instability : 0;
                float z = tile.active ? Mth.sin((ticks + c * 10) / 13) * .01F * tile.startUp * instability : 0;
                poses.pushPose(); poses.translate(x + (a == 0 ? -.25 : .25),y + (b == 0 ? -.25 : .25),z + (c == 0 ? -.25 : .25));
                if (a > 0) poses.mulPose(Axis.XP.rotationDegrees(90)); if (b > 0) poses.mulPose(Axis.YP.rotationDegrees(90)); if (c > 0) poses.mulPose(Axis.ZP.rotationDegrees(90));
                poses.scale(.45F,.45F,.45F);
                if(pass == 0) cube.render(poses,vertices,light,overlay);
                else {float alpha=(Mth.sin((ticks+a*2+b*3+c*4)/4)*.1F+.2F)*tile.startUp;int color=(Mth.clamp((int)(alpha*255),0,255)<<24)|0xcc19ff;
                    glow.render(poses,vertices,LightTexture.FULL_BRIGHT,overlay,color);}
                poses.popPose();
            }
        }
        poses.popPose();
        if(tile.crafting)SourceRayMeshes.halo(poses,buffers,tile.craftCount,Minecraft.getInstance().options.graphicsMode().get()!=GraphicsStatus.FAST);
    }
}
