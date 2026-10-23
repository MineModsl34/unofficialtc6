package thaumcraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import thaumcraft.client.fx.particles.FXSwarm;
import thaumcraft.client.fx.particles.FXWispEG;
import thaumcraft.client.fx.particles.FXSmokeSpiral;
import thaumcraft.client.fx.particles.FXTaintSeed;
import thaumcraft.client.renderers.entity.Boss8RenderTypes;
import thaumcraft.client.renderers.entity.construct.RenderCultistPortalGreater;
import thaumcraft.client.renderers.entity.mob.*;
import thaumcraft.client.renderers.entity.projectile.*;
import thaumcraft.client.renderers.models.entity.ModelEldritchWarden;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.entities.Mob8BossPresentationHooks;
import thaumcraft.common.entities.monster.tainted.EntityTaintSwarm;

@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Mob8BossClientRegistration {
    private static final Map<EntityTaintSwarm,List<FXSwarm>> SWARMS=new WeakHashMap<>();
    private Mob8BossClientRegistration(){}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        Mob8BossPresentationHooks.wardenTick=entity->{if(!(entity.level() instanceof ClientLevel level))return;var random=entity.getRandom();double x=entity.getX()+(random.nextFloat()-random.nextFloat())*.2F,z=entity.getZ()+(random.nextFloat()-random.nextFloat())*.2F;for(int i=0;i<2;i++)Minecraft.getInstance().particleEngine.add(new FXWispEG(level,x,entity.getY()+.25*entity.getBbHeight(),z,entity));if(entity.getSpawnTimer()>0){float height=Math.max(1,entity.getBbHeight()*(150-entity.getSpawnTimer())/150F);for(int i=0;i<33;i++)Minecraft.getInstance().particleEngine.add(new FXSmokeSpiral(level,entity.getX(),entity.getBoundingBox().minY+height/2,entity.getZ(),height,random.nextInt(360),(int)Math.floor(entity.getBoundingBox().minY)-1,2232623));}};
        Mob8BossPresentationHooks.seedTick=entity->{if(!(entity.level() instanceof ClientLevel level))return;var random=entity.getRandom();float x=net.minecraft.util.Mth.sin(entity.tickCount*.05F-.5F)/5,z=net.minecraft.util.Mth.sin(entity.tickCount*.06F-.5F)/5+entity.hurtTime/200F+entity.attackAnim;boolean flash=random.nextFloat()<.033F;if(flash)Minecraft.getInstance().particleEngine.add(new FXTaintSeed(level,entity.getX()+x,entity.getY()+entity.getBbHeight()+.25,entity.getZ()+z,0,0,0,true,1.5F+random.nextFloat()));else Minecraft.getInstance().particleEngine.add(new FXTaintSeed(level,entity.getX()+x,entity.getY()+entity.getBbHeight()+.25,entity.getZ()+z,(float)random.nextGaussian()*.05F,.1F+.01F*random.nextFloat(),(float)random.nextGaussian()*.05F,false,2));};
        Mob8BossPresentationHooks.swarmTick=entity->{
        if(!(entity.level() instanceof ClientLevel level))return;
        List<FXSwarm> particles=SWARMS.computeIfAbsent(entity,key->new ArrayList<>());
        // Source removes at most one expired particle each tick, then creates at most one up to 30.
        for(int i=0;i<particles.size();i++)if(!particles.get(i).isAlive()){particles.remove(i);break;}
        if(particles.size()<30){var random=level.random;FXSwarm particle=new FXSwarm(level,
            entity.getX()+(random.nextFloat()-random.nextFloat())*2,entity.getY()+(random.nextFloat()-random.nextFloat())*2,entity.getZ()+(random.nextFloat()-random.nextFloat())*2,
            entity,.8F+random.nextFloat()*.2F,random.nextFloat()*.4F,1-random.nextFloat()*.2F,.22F,15,.08F);
            particles.add(particle);Minecraft.getInstance().particleEngine.add(particle);}
        };
    });}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("thaumcraft:boss_ender"),DefaultVertexFormat.POSITION_TEX),Boss8RenderTypes::install);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(Mob8BossRegistries.CULTIST_LEADER,RenderCultistLeader::new);
        event.registerEntityRenderer(Mob8BossRegistries.PORTAL_GREATER,RenderCultistPortalGreater::new);
        event.registerEntityRenderer(Mob8BossRegistries.ELDRITCH_GOLEM,RenderEldritchGolem::new);
        event.registerEntityRenderer(Mob8BossRegistries.ELDRITCH_WARDEN,RenderEldritchWarden::new);
        event.registerEntityRenderer(Mob8BossRegistries.TAINTACLE_GIANT,context->new RenderTaintacle<>(context,1,14));
        event.registerEntityRenderer(Mob8BossRegistries.TAINT_CRAWLER,RenderTaintCrawler::new);
        event.registerEntityRenderer(Mob8BossRegistries.TAINTACLE,context->new RenderTaintacle<>(context,.6F,10));
        event.registerEntityRenderer(Mob8BossRegistries.TAINTACLE_SMALL,context->new RenderTaintacle<>(context,.2F,6));
        event.registerEntityRenderer(Mob8BossRegistries.TAINT_SWARM,NoopRenderer::new);
        event.registerEntityRenderer(Mob8BossRegistries.TAINT_SEED,context->new RenderTaintSeed<>(context,.4F));
        event.registerEntityRenderer(Mob8BossRegistries.TAINT_SEED_PRIME,context->new RenderTaintSeed<>(context,.6F));
        event.registerEntityRenderer(Mob8BossRegistries.HOMING_SHARD,RenderHomingShard::new);
        event.registerEntityRenderer(Mob8BossRegistries.RIFT_BLAST,RenderRiftBlast::new);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(RenderEldritchWarden.LAYER,ModelEldritchWarden::createBodyLayer);}
}
