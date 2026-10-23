package thaumcraft.client.renderers.entity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.Thaumcraft;
import thaumcraft.client.gui.GuiPech;
import thaumcraft.client.renderers.models.entity.*;
import thaumcraft.common.entities.*;
import thaumcraft.common.entities.monster.*;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Mob8NaturalClient {
    private static final ModelLayerLocation PECH=layer("pech"),CRAB=layer("eldritch_crab"),BAT=layer("firebat");
    private static ModelLayerLocation layer(String name){return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("thaumcraft",name),"main");}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(PECH,ModelPech::createBodyLayer);event.registerLayerDefinition(CRAB,ModelEldritchCrab::createBodyLayer);event.registerLayerDefinition(BAT,ModelFireBat::createBodyLayer);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(Mob8NaturalRegistries.BRAINY_ZOMBIE,context->new SourceZombieRenderer(context,false));event.registerEntityRenderer(Mob8NaturalRegistries.GIANT_BRAINY_ZOMBIE,context->new SourceZombieRenderer(context,false));event.registerEntityRenderer(Mob8NaturalRegistries.INHABITED_ZOMBIE,context->new SourceZombieRenderer(context,true));event.registerEntityRenderer(Mob8NaturalRegistries.ELDRITCH_CRAB,CrabRenderer::new);event.registerEntityRenderer(Mob8NaturalRegistries.FIREBAT,FireBatRenderer::new);event.registerEntityRenderer(Mob8NaturalRegistries.WISP,WispRenderer::new);event.registerEntityRenderer(Mob8NaturalRegistries.PECH,PechRenderer::new);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(Mob8NaturalRegistries.PECH_MENU,GuiPech::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{Mob8NaturalEffects.ambient=Mob8NaturalClientEffects::ambient;Mob8NaturalEffects.death=Mob8NaturalClientEffects::death;});}
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/entity/"+name+".png");}
    private static final class SourceZombieRenderer extends ZombieRenderer {
        private final boolean inhabited;
        SourceZombieRenderer(EntityRendererProvider.Context context,boolean inhabited){super(context);this.inhabited=inhabited;model=new TintedZombieModel(context.bakeLayer(ModelLayers.ZOMBIE));}
        @Override public ResourceLocation getTextureLocation(Zombie entity){return texture(inhabited?"czombie":"bzombie");}
        @Override protected void scale(Zombie entity,PoseStack stack,float partial){if(entity instanceof EntityGiantBrainyZombie giant){float factor=1+giant.getAnger();stack.scale(factor,factor,factor);}super.scale(entity,stack,partial);}
    }
    private static final class TintedZombieModel extends ZombieModel<Zombie> {
        private int tint=-1;
        TintedZombieModel(ModelPart root){super(root);}
        @Override public void setupAnim(Zombie entity,float limb,float amount,float age,float yaw,float pitch){super.setupAnim(entity,limb,amount,age,yaw,pitch);int channel=entity instanceof EntityGiantBrainyZombie giant?(int)(255*(1-Math.min(1,giant.getAnger())/2)):255;tint=0xffff0000|channel<<8|channel;}
        @Override public void renderToBuffer(PoseStack stack,VertexConsumer vertices,int light,int overlay,int color){super.renderToBuffer(stack,vertices,light,overlay,FastColor.ARGB32.multiply(color,tint));}
    }
    private static final class CrabRenderer extends MobRenderer<EntityEldritchCrab,ModelEldritchCrab>{CrabRenderer(EntityRendererProvider.Context context){super(context,new ModelEldritchCrab(context.bakeLayer(CRAB)),.5F);}@Override public ResourceLocation getTextureLocation(EntityEldritchCrab entity){return texture("crab");}@Override protected void scale(EntityEldritchCrab entity,PoseStack stack,float partial){stack.scale(.8F,.8F,.8F);super.scale(entity,stack,partial);}}
    private static final class FireBatRenderer extends MobRenderer<EntityFireBat,ModelFireBat>{FireBatRenderer(EntityRendererProvider.Context context){super(context,new ModelFireBat(context.bakeLayer(BAT)),.25F);}@Override public ResourceLocation getTextureLocation(EntityFireBat entity){return texture("firebat");}@Override protected int getBlockLightLevel(EntityFireBat entity,net.minecraft.core.BlockPos pos){return 15;}@Override protected void scale(EntityFireBat entity,PoseStack stack,float partial){stack.scale(.35F,.35F,.35F);}@Override protected void setupRotations(EntityFireBat entity,PoseStack stack,float age,float yaw,float partial,float scale){stack.translate(0,entity.getIsBatHanging()?-.1F:Mth.cos(age*.3F)*.1F,0);super.setupRotations(entity,stack,age,yaw,partial,scale);}}
    private static final class PechRenderer extends MobRenderer<EntityPech,ModelPech>{PechRenderer(EntityRendererProvider.Context context){super(context,new ModelPech(context.bakeLayer(PECH)),.5F);addLayer(new ItemInHandLayer<EntityPech,ModelPech>(this,context.getItemInHandRenderer()){@Override protected void renderArmWithItem(LivingEntity entity,ItemStack item,ItemDisplayContext display,HumanoidArm arm,PoseStack stack,MultiBufferSource buffers,int light){if(item.isEmpty())return;stack.pushPose();getParentModel().translateToHand(arm,stack);stack.translate(0,-.1F,.0625F);if(item.getItem() instanceof BowItem)stack.translate(-.075F,-.1,0);if(entity.isCrouching())stack.translate(0,.2F,0);stack.mulPose(Axis.XP.rotationDegrees(-90));stack.mulPose(Axis.YP.rotationDegrees(180));boolean left=arm==HumanoidArm.LEFT;stack.translate(left?-.0625F:.0625F,.125F,-.625F);context.getItemInHandRenderer().renderItem(entity,item,display,left,stack,buffers,light);stack.popPose();}});}@Override public ResourceLocation getTextureLocation(EntityPech entity){return texture(entity.getPechType()==0?"pech_forage":entity.getPechType()==1?"pech_thaum":"pech_stalker");}@Override public net.minecraft.world.phys.Vec3 getRenderOffset(EntityPech entity,float partial){return super.getRenderOffset(entity,partial).add(0,entity.isCrouching()?-.125:0,0);}}
    private static final class WispRenderer extends EntityRenderer<EntityWisp>{private static final ResourceLocation PARTICLES=ResourceLocation.parse("thaumcraft:textures/misc/particles.png"),NODES=ResourceLocation.parse("thaumcraft:textures/misc/auranodes.png");WispRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}@Override public ResourceLocation getTextureLocation(EntityWisp entity){return PARTICLES;}
        @Override public void render(EntityWisp entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){if(entity.getHealth()<=0)return;stack.pushPose();stack.mulPose(entityRenderDispatcher.cameraOrientation());int frame=entity.tickCount%16;quad(stack,buffers,PARTICLES,64,512+frame,.4F,0xffffff,1);quad(stack,buffers,PARTICLES,64,320+frame,.75F,0xffffff,.25F);quad(stack,buffers,NODES,32,800+frame,.75F,entity.getAspect()==null?0:entity.getAspect().getColor(),.5F);stack.popPose();super.render(entity,yaw,partial,stack,buffers,light);}
        private static void quad(PoseStack stack,MultiBufferSource buffers,ResourceLocation texture,int grid,int frame,float size,int color,float alpha){float u=(float)(frame%grid)/grid,v=(float)(frame/grid)/grid,cell=1F/grid;VertexConsumer out=buffers.getBuffer(RenderType.eyes(texture));int argb=(int)(alpha*255)<<24|color;var pose=stack.last();vertex(out,pose,-size,-size,u+cell,v+cell,argb);vertex(out,pose,-size,size,u+cell,v,argb);vertex(out,pose,size,size,u,v,argb);vertex(out,pose,size,-size,u,v+cell,argb);}
        private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float u,float v,int color){out.addVertex(pose,x,y,0).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose,0,0,1);}
    }
}
