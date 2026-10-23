package thaumcraft.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Original six-layer engine: four world layers, two GUI layers, dimension queues, delayed admission and FPS thinning. */
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class ParticleEngine {
    public static final ResourceLocation particleTexture=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private static final List<Map<ResourceKey<Level>,ArrayList<Particle>>> PARTICLES=new ArrayList<>(6);
    private record Delayed(Particle particle,ResourceKey<Level> dimension,int delay) {}
    private static final List<Delayed> DELAYED=new ArrayList<>();
    private static ClientLevel session;
    static { for(int i=0;i<6;i++)PARTICLES.add(new HashMap<>()); }
    private ParticleEngine() {}
    public static int particleLimit(int setting) { return FxAnimationRules.particleLimit(setting); }
    public static boolean admit(int randomThree,int setting,int fps) { return FxAnimationRules.admit(randomThree,setting,fps); }
    public static int layer(Particle particle) { return particle instanceof SourceFxLayer source?source.getFXLayer():1; }
    public static void addEffect(ClientLevel level,Particle effect) {
        if(level==null||effect==null)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        int setting=mc.options.particles().get().getId();
        if(!admit(mc.level.random.nextInt(3),setting,mc.getFps()))return;
        int index=layer(effect);if(index<0||index>=6)throw new IllegalArgumentException("Source FX layer "+index);
        var queue=PARTICLES.get(index).computeIfAbsent(level.dimension(),unused->new ArrayList<>());
        if(queue.size()>=particleLimit(setting))queue.remove(0);
        queue.add(effect);
    }
    public static void addEffectWithDelay(ClientLevel level,Particle effect,int delay) {
        if(level!=null&&effect!=null)DELAYED.add(new Delayed(effect,level.dimension(),delay));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null){clear();session=null;return;}
        if(session!=null&&session!=level&&session.dimension()==level.dimension())clear();
        session=level;
        // Snapshot protects constructor/tick-created secondary effects without changing delayed countdown timing.
        var waiting=new ArrayList<>(DELAYED);DELAYED.clear();
        for(var delayed:waiting) {
            int remaining=delayed.delay()-1;
            if(remaining<=0){if(delayed.dimension()==level.dimension())addEffect(level,delayed.particle());}
            else DELAYED.add(new Delayed(delayed.particle(),delayed.dimension(),remaining));
        }
        for(var layers:PARTICLES) {
            var queue=layers.get(level.dimension());if(queue==null)continue;
            // Source iterates the live queue: same-layer secondary drops tick immediately, not one tick later.
            for(int i=0;i<queue.size();i++) {
                Particle particle=queue.get(i);
                try{particle.tick();}catch(RuntimeException failure){particle.remove();}
                if(!particle.isAlive()&&i<queue.size()&&queue.get(i)==particle)queue.remove(i--);
            }
        }
    }
    private static void clear() {
        DELAYED.clear();for(var layer:PARTICLES){for(var queue:layer.values())queue.forEach(Particle::remove);layer.clear();}
        thaumcraft.client.fx.other.FXEssentiaStream.clearTrails();
    }
    @SubscribeEvent public static void renderWorld(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        // AFTER_LEVEL is outside LevelRenderer's camera model-view scope. Particle vertices
        // already subtract camera position; restore only the event's view rotation here.
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            modelView.mul(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            render(3,0,event.getPartialTick().getGameTimeDeltaPartialTick(false),event.getPoseStack());
        } finally {modelView.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
    @SubscribeEvent public static void renderGui(RenderFrameEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        // Source RenderTick.END happens after HUD and screens, using its own orthographic projection.
        RenderSystem.backupProjectionMatrix();var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,1000,3000),VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity().translate(0,0,-2000);RenderSystem.applyModelViewMatrix();
            render(5,4,event.getPartialTick().getGameTimeDeltaPartialTick(false),new PoseStack());
        } finally { modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix(); }
    }
    private static void render(int top,int bottom,float partial,PoseStack poses) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var camera=mc.gameRenderer.getMainCamera();float[] previousColor=RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1,1,1,1);
        try {
            for(int index=top;index>=bottom;index--) {
                var queue=PARTICLES.get(index).get(mc.level.dimension());if(queue==null)continue;
                for(Particle particle:queue) {
                    poses.pushPose();
                    try {
                        if(particle instanceof SourceParticle source)source.renderPrepared(camera,partial,poses);
                        else particle.render(null,camera,partial);
                    } finally { poses.popPose(); }
                }
            }
        } finally {RenderSystem.setShaderColor(previousColor[0],previousColor[1],previousColor[2],previousColor[3]);}
    }
}
