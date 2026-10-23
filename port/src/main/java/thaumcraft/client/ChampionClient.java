package thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.mods.ChampionSystem;

/** One layer per renderer, not source's accumulating entity-ID layers; synced attributes decide the effect. */
@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ChampionClient {
    private static final ResourceLocation TAINT=ResourceLocation.parse("thaumcraft:textures/models/taint_fibres.png");
    private static final RenderType TAINT_INVISIBLE=RenderType.create("tc_taint_invisible",com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,false,true,RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new RenderStateShard.TextureStateShard(TAINT,false,false)).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL).setLightmapState(RenderStateShard.LIGHTMAP).setOverlayState(RenderStateShard.OVERLAY).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private ChampionClient() {}
    @SuppressWarnings({"rawtypes","unchecked"})
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){
        for(var type:event.getEntityTypes())if(event.getRenderer(type) instanceof LivingEntityRenderer renderer)renderer.addLayer(new ChampionLayer(renderer));
    }
    private static final class ChampionLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
        ChampionLayer(RenderLayerParent<T,M> parent){super(parent);}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float swing,float amount,float partial,float age,float yaw,float pitch){
            if(!ChampionSystem.sourceCreature(entity)||!entity.isAlive())return;
            int id=ChampionSystem.modifier(entity);int color=switch(id){case 4->0xff999999;case 8->0xffffbfff;case 10->0xff99ffb2;case 11->0xffffb2b2;default->0;};
            if(color!=0&&!entity.isInvisible())getParentModel().renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))),light,LivingEntityRenderer.getOverlayCoords(entity,0),color);
            if(id==13){
                var target=buffers.getBuffer(entity.isInvisible()?TAINT_INVISIBLE:RenderType.entityTranslucent(TAINT));
                float u=net.minecraft.util.Mth.cos(entity.getId()*2.5E-4F),v=entity.getId()*.001F;
                getParentModel().renderToBuffer(pose,new TaintUV(target,u,v),light,LivingEntityRenderer.getOverlayCoords(entity,0),0xa8ffffff);
            }
        }
    }
    private record TaintUV(VertexConsumer delegate,float offsetU,float offsetV) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x,float y,float z){delegate.addVertex(x,y,z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){delegate.setColor(r,g,b,a);return this;}
        @Override public VertexConsumer setUv(float u,float v){delegate.setUv((u+offsetU)*8,(v+offsetV)*4);return this;}
        @Override public VertexConsumer setUv1(int u,int v){delegate.setUv1(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){delegate.setUv2(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){delegate.setNormal(x,y,z);return this;}
    }
}
