package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.blocks.basic.BannerRegistries;
import thaumcraft.common.entities.Mob8MiscRegistries;
import thaumcraft.common.entities.EntityFallingTaint;
import thaumcraft.common.tiles.misc.TileBanner;

/** Source falling block/item presentation and exact ModelBanner five boxes/UVs. */
@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Mob8MiscClient {
    private static final ModelLayerLocation BANNER=new ModelLayerLocation(ResourceLocation.parse("thaumcraft:crimson_banner"),"main");
    private Mob8MiscClient() {}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(BANNER,Mob8MiscClient::bannerLayer);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(Mob8MiscRegistries.BOTTLE_TAINT,ThrownItemRenderer::new);
        event.registerEntityRenderer(Mob8MiscRegistries.CAUSALITY_COLLAPSER,ThrownItemRenderer::new);
        event.registerEntityRenderer(Mob8MiscRegistries.FALLING_TAINT,FallingTaintRenderer::new);
        event.registerBlockEntityRenderer(BannerRegistries.BANNER,BannerRenderer::new);
    }
    public static LayerDefinition bannerLayer() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("B1",CubeListBuilder.create().texOffs(0,29).mirror().addBox(-5,-7.5F,-1.5F,2,3,3),PartPose.ZERO);
        root.addOrReplaceChild("B2",CubeListBuilder.create().texOffs(0,29).mirror().addBox(3,-7.5F,-1.5F,2,3,3),PartPose.ZERO);
        root.addOrReplaceChild("Beam",CubeListBuilder.create().texOffs(30,0).mirror().addBox(-7,-7,-1,14,2,2),PartPose.ZERO);
        root.addOrReplaceChild("Banner",CubeListBuilder.create().texOffs(0,0).mirror().addBox(-7,0,-.5F,14,28,1),PartPose.offset(0,-5,0));
        root.addOrReplaceChild("Pole",CubeListBuilder.create().texOffs(62,0).mirror().addBox(0,0,-1,2,31,2),PartPose.offset(-1,-7,-2));
        return LayerDefinition.create(mesh,128,64);
    }
    private static final class BannerRenderer implements BlockEntityRenderer<TileBanner> {
        private final ModelPart root;
        BannerRenderer(BlockEntityRendererProvider.Context context){root=context.bakeLayer(BANNER);}
        @Override public void render(TileBanner tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            pose.pushPose();pose.translate(.5,1.5,.5);pose.mulPose(Axis.XP.rotationDegrees(180));pose.mulPose(Axis.YP.rotationDegrees(180+tile.getBannerFacing()*360/16F));
            var texture=ResourceLocation.parse("thaumcraft:textures/models/"+(tile.getAspect()==null&&tile.getColor()==-1?"banner_cultist":"banner_blank")+".png");
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
            if(!tile.getWall())root.getChild("Pole").render(pose,vertices,light,overlay);else pose.translate(0,1,-.4125);
            root.getChild("Beam").render(pose,vertices,light,overlay);
            int tint=tile.getColor()==-1?0xffffffff:0xff000000|tile.getColor();
            root.getChild("B1").render(pose,vertices,light,overlay,tint);root.getChild("B2").render(pose,vertices,light,overlay,tint);
            var pos=tile.getBlockPos();var player=net.minecraft.client.Minecraft.getInstance().player;
            float time=pos.getX()*7+pos.getY()*9+pos.getZ()*13+(player==null?0:player.tickCount)+partial;
            float wave=.02F-net.minecraft.util.Mth.sin(time/11)*.02F;
            root.getChild("Banner").xRot=wave;root.getChild("Banner").render(pose,vertices,light,overlay,tint);
            if(tile.getAspect()!=null) {
                pose.pushPose();pose.translate(0,0,.05001);pose.scale(.0375F,.0375F,.0375F);
                pose.mulPose(Axis.YP.rotationDegrees(180));pose.mulPose(Axis.XP.rotation(-wave*2));
                var glyph=buffers.getBuffer(RenderType.entityTranslucent(tile.getAspect().getImage()));var pp=pose.last();
                int color=0xbf000000|tile.getAspect().getColor();
                glyph.addVertex(pp,-8,0,0).setColor(color).setUv(0,0).setOverlay(overlay).setLight(light).setNormal(pp,0,0,-1);
                glyph.addVertex(pp,8,0,0).setColor(color).setUv(1,0).setOverlay(overlay).setLight(light).setNormal(pp,0,0,-1);
                glyph.addVertex(pp,8,16,0).setColor(color).setUv(1,1).setOverlay(overlay).setLight(light).setNormal(pp,0,0,-1);
                glyph.addVertex(pp,-8,16,0).setColor(color).setUv(0,1).setOverlay(overlay).setLight(light).setNormal(pp,0,0,-1);
                pose.popPose();
            }
            pose.popPose();
        }
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(TileBanner tile){return new net.minecraft.world.phys.AABB(tile.getBlockPos()).inflate(0,1,0);}
    }
    private static final class FallingTaintRenderer extends EntityRenderer<EntityFallingTaint> {
        private final BlockRenderDispatcher blocks;
        FallingTaintRenderer(EntityRendererProvider.Context context){super(context);blocks=context.getBlockRenderDispatcher();shadowRadius=.5F;}
        @Override public void render(EntityFallingTaint entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            var state=entity.getBlock();if(!state.isAir()&&state!=entity.level().getBlockState(entity.blockPosition())&&state.getRenderShape()==net.minecraft.world.level.block.RenderShape.MODEL){
                pose.pushPose();pose.translate(-.5,0,-.5);blocks.renderSingleBlock(state,pose,buffers,light,OverlayTexture.NO_OVERLAY);pose.popPose();
                super.render(entity,yaw,partial,pose,buffers,light);
            }
        }
        @Override public ResourceLocation getTextureLocation(EntityFallingTaint entity){return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;}
    }
}
