package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import thaumcraft.common.blocks.crafting.BlockGolemBuilder;
import thaumcraft.common.golems.client.GolemObjMeshes;
import thaumcraft.common.golems.client.GolemObjModel;
import thaumcraft.common.tiles.crafting.TileGolemBuilder;

/** Source complete press/table OBJ, facing, sine press travel and genuine lava sprite. */
public final class TileGolemBuilderRenderer implements BlockEntityRenderer<TileGolemBuilder> {
    private static final ResourceLocation MODEL = ResourceLocation.parse("thaumcraft:models/block/golembuilder.obj"),TEXTURE = ResourceLocation.parse("thaumcraft:textures/block/golembuilder.png");
    public TileGolemBuilderRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(TileGolemBuilder tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        renderPress(poses,buffers,tile.getBlockState().getValue(BlockGolemBuilder.FACING),tile.press,light,overlay);
    }
    public static void renderPress(PoseStack poses,MultiBufferSource buffers,net.minecraft.core.Direction facing,int press,int light,int overlay) {
        poses.pushPose(); poses.translate(.5,0,.5);
        poses.mulPose(Axis.YP.rotationDegrees(switch (facing) { case SOUTH -> 180; case WEST -> 90; case EAST -> 270; default -> 0; }));
        var mesh = GolemObjMeshes.get(MODEL); var out = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (String group : mesh.groups()) if (!group.equals("press")) GolemObjModel.draw(mesh,group,poses,out,light,overlay,-1,true);
        poses.pushPose(); poses.translate(0,-Math.sin(Math.toRadians(press)) * .625,0); GolemObjModel.draw(mesh,"press",poses,out,light,overlay,-1,true); poses.popPose();
        poses.translate(-.3125,.625,1.3125); poses.mulPose(Axis.XN.rotationDegrees(90));
        var sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.LAVA.defaultBlockState());
        var lava = buffers.getBuffer(RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)); var p = poses.last();
        vertex(lava,p,0,0,sprite.getU1(),sprite.getV1(),200); vertex(lava,p,.625F,0,sprite.getU0(),sprite.getV1(),200);
        vertex(lava,p,.625F,.625F,sprite.getU0(),sprite.getV0(),200); vertex(lava,p,0,.625F,sprite.getU1(),sprite.getV0(),200); poses.popPose();
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose p,float x,float y,float u,float v,int light) { out.addVertex(p,x,y,0).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,0,1); }
    @Override public AABB getRenderBoundingBox(TileGolemBuilder tile) { var p = tile.getBlockPos(); return new AABB(p.getX() - 1,p.getY(),p.getZ() - 1,p.getX() + 2,p.getY() + 2,p.getZ() + 2); }
    @Override public boolean shouldRenderOffScreen(TileGolemBuilder tile) { return true; }
    @Override public int getViewDistance() { return 64; }
}
