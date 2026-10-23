package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import thaumcraft.common.tiles.devices.*;

/** Direct native rendering of original ModelChest/ModelBrain/ModelJar and source dioptra/gear geometry. */
public final class TileWorldDeviceRenderer implements BlockEntityRenderer<DeviceTile> {
    private final ModelPart chest,brain,brine;
    public TileWorldDeviceRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("lid",CubeListBuilder.create().texOffs(0,0).addBox(0,-5,-14,14,5,14),PartPose.offset(1,7,15));
        root.addOrReplaceChild("knob",CubeListBuilder.create().texOffs(0,0).addBox(-1,-2,-15,2,4,1),PartPose.offset(8,7,15));
        root.addOrReplaceChild("base",CubeListBuilder.create().texOffs(0,19).addBox(0,0,0,14,10,14),PartPose.offset(1,6,1)); chest = LayerDefinition.create(mesh,64,64).bakeRoot();
        mesh = new MeshDefinition(); root = mesh.getRoot();
        root.addOrReplaceChild("brain",CubeListBuilder.create().texOffs(0,0).mirror().addBox(0,0,0,12,10,16),PartPose.offset(-6,8,-8));
        root.addOrReplaceChild("stem",CubeListBuilder.create().texOffs(64,0).mirror().addBox(0,0,0,8,3,7),PartPose.offset(-4,18,0));
        root.addOrReplaceChild("cord",CubeListBuilder.create().texOffs(0,32).mirror().addBox(0,0,0,2,6,2),PartPose.offsetAndRotation(-1,18,-2,.4089647F,0,0)); brain = LayerDefinition.create(mesh,128,64).bakeRoot();
        mesh = new MeshDefinition(); mesh.getRoot().addOrReplaceChild("brine",CubeListBuilder.create().texOffs(0,0).mirror().addBox(-4,-11,-4,8,10,8),PartPose.ZERO); brine = LayerDefinition.create(mesh,64,32).bakeRoot();
    }
    private static ResourceLocation texture(String path) { return ResourceLocation.parse("thaumcraft:textures/" + path); }
    @Override public void render(DeviceTile tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        switch (tile.kind()) {
            case CHEST -> chest((TileStorageDevice)tile,partialTick,poses,buffers,light,overlay);
            case BRAIN -> brain((TileStorageDevice)tile,partialTick,poses,buffers,light,overlay);
            case RECHARGE -> { var stack = tile.getItem(0); if (stack.isEmpty()) return; poses.pushPose(); poses.translate(.5,.75,.5); poses.scale(1.5F,1.5F,1.5F); poses.mulPose(Axis.YP.rotationDegrees(ticks(partialTick) % 360)); poses.translate(0,.25,0); Minecraft.getInstance().getItemRenderer().renderStatic(stack.copyWithCount(1),ItemDisplayContext.GROUND,light,overlay,poses,buffers,tile.getLevel(),0); poses.popPose(); }
            case DIOPTRA -> dioptra((TileSignalDevice)tile,partialTick,poses,buffers);
            case CRAFTER -> crafter((TilePatternCrafter)tile,poses,buffers,light);
            default -> {}
        }
    }
    private static float ticks(float tick) { return Minecraft.getInstance().getCameraEntity() == null ? 0 : Minecraft.getInstance().getCameraEntity().tickCount + tick; }
    private void chest(TileStorageDevice tile,float tick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose(); poses.translate(0,1,1); poses.scale(1,-1,-1); poses.translate(.5,.5,.5); poses.mulPose(Axis.YP.rotationDegrees(switch (tile.facing()) { case NORTH -> 180; case WEST -> 90; case EAST -> -90; default -> 0; })); poses.translate(-.5,-.5,-.5);
        float lid = 1 - net.minecraft.util.Mth.lerp(tick,tile.prevLidAngle,tile.lidAngle); lid = 1 - lid * lid * lid; chest.getChild("lid").xRot = chest.getChild("knob").xRot = -lid * (float)Math.PI / 2; chest.render(poses,buffers.getBuffer(RenderType.entityCutout(texture("models/chesthungry.png"))),light,overlay); poses.popPose();
    }
    private void brain(TileStorageDevice tile,float tick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose(); poses.translate(.5,.01,.5); poses.mulPose(Axis.XP.rotationDegrees(180)); poses.pushPose(); poses.translate(0,-.8F + Math.sin(ticks(0) / 14F) * .03F + .03F,0); float delta = tile.rota - tile.rotb; while (delta >= Math.PI) delta -= (float)Math.PI * 2; while (delta < -Math.PI) delta += (float)Math.PI * 2; poses.mulPose(Axis.YP.rotation(tile.rotb + delta * tick)); poses.mulPose(Axis.YP.rotationDegrees(-90)); poses.scale(.4F,.4F,.4F); brain.render(poses,buffers.getBuffer(RenderType.entityCutout(texture("models/brain2.png"))),light,overlay); poses.popPose(); brine.render(poses,buffers.getBuffer(RenderType.entityTranslucent(texture("models/jarbrine.png"))),light,overlay); poses.popPose();
    }
    private static void crafter(TilePatternCrafter tile,PoseStack poses,MultiBufferSource buffers,int light) {
        poses.pushPose(); poses.translate(.5,.75,.5); poses.mulPose(Axis.YP.rotationDegrees(switch(tile.facing()) { case NORTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; })); poses.pushPose(); poses.mulPose(Axis.ZP.rotationDegrees(90)); poses.translate(0,0,-.5); AlchemyRenderSupport.quad(poses,buffers.getBuffer(RenderType.entityCutoutNoCull(texture("block/pattern_crafter_modes.png"))),.5F,0xffffffff,light,0,tile.type / 10F,1,(tile.type + 1) / 10F); poses.popPose();
        for (int side : new int[]{-1,1}) { poses.pushPose(); poses.translate(side * .2F,-.40625F,.05F); poses.mulPose(Axis.ZP.rotationDegrees(side * tile.rot % 360)); AlchemyRenderSupport.quad(poses,buffers.getBuffer(RenderType.entityCutoutNoCull(texture("misc/gear_brass.png"))),.5F,0xffffffff,light,0,0,1,1); poses.translate(0,0,.05); AlchemyRenderSupport.quad(poses,buffers.getBuffer(RenderType.entityCutoutNoCull(texture("misc/gear_brass.png"))),.5F,0xffffffff,light,0,0,1,1); poses.popPose(); } poses.popPose();
    }
    private static void dioptra(TileSignalDevice tile,float tick,PoseStack poses,MultiBufferSource buffers) {
        float t = ticks(tick), r = net.minecraft.util.Mth.sin(t / 12) * .05F + .85F, g = net.minecraft.util.Mth.sin(t / 11) * .05F + (tile.enabled() ? .9F : .45F), b = net.minecraft.util.Mth.sin(t / 10) * .05F + .95F; int color = 0xe6000000 | (int)(r * .8F * 255) << 16 | (int)(g * 255) << 8 | (int)(b * 255);
        poses.pushPose(); poses.translate(.005,1.001,.005); poses.scale(.99F,1,.99F); var vertices = buffers.getBuffer(RenderType.entityTranslucent(texture("misc/gridblock.png"))); int glow = LightTexture.pack(13,13);
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) { float[][] points = {{x / 12F,tile.grid_amt[x + z * 13] / 96F,z / 12F},{(x + 1) / 12F,tile.grid_amt[x + 1 + z * 13] / 96F,z / 12F},{(x + 1) / 12F,tile.grid_amt[x + 1 + (z + 1) * 13] / 96F,(z + 1) / 12F},{x / 12F,tile.grid_amt[x + (z + 1) * 13] / 96F,(z + 1) / 12F}}; for (int corner = 3; corner >= 0; corner--) { var p = points[corner]; AlchemyRenderSupport.vertex(vertices,poses.last(),p[0],p[1],p[2],corner == 0 || corner == 3 ? 0 : 1,corner < 2 ? 1 : 0,color,glow,0,1,0); }
            if (x == 0) edge(poses,vertices,points[3],points[0],color,glow); if (x == 11) edge(poses,vertices,points[1],points[2],color,glow); if (z == 0) edge(poses,vertices,points[0],points[1],color,glow); if (z == 11) edge(poses,vertices,points[2],points[3],color,glow); }
        poses.popPose(); poses.pushPose(); poses.translate(.5,1.5,.5); poses.mulPose(Axis.ZP.rotationDegrees(270)); for (int side = 0; side < 4; side++) { poses.pushPose(); poses.mulPose(Axis.XP.rotationDegrees(side * 90)); poses.translate(0,0,-.5); AlchemyRenderSupport.quad(poses,buffers.getBuffer(RenderType.entityTranslucent(texture("models/dioptra_side.png"))),1,(color & 0x00ffffff) | 0xcc000000,LightTexture.pack(13,13),0,0,1,1); poses.popPose(); } poses.popPose();
    }
    private static void edge(PoseStack poses,com.mojang.blaze3d.vertex.VertexConsumer vertices,float[] a,float[] b,int color,int light) { AlchemyRenderSupport.vertex(vertices,poses.last(),a[0],0,a[2],0,1,color & 0xffffff,light,0,1,0); AlchemyRenderSupport.vertex(vertices,poses.last(),a[0],a[1],a[2],0,0,color,light,0,1,0); AlchemyRenderSupport.vertex(vertices,poses.last(),b[0],b[1],b[2],1,0,color,light,0,1,0); AlchemyRenderSupport.vertex(vertices,poses.last(),b[0],0,b[2],1,1,color & 0xffffff,light,0,1,0); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(DeviceTile tile) { return new net.minecraft.world.phys.AABB(tile.getBlockPos()).expandTowards(0,1.7,0); }
}
