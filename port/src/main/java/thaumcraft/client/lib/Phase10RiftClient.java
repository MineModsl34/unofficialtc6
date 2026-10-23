package thaumcraft.client.lib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.client.gui.GuiVoidSiphon;
import thaumcraft.client.renderers.entity.RenderFluxRift;
import thaumcraft.client.renderers.tile.TileVoidSiphonRenderer;
import thaumcraft.common.entities.*;
import thaumcraft.common.tiles.crafting.Phase10RiftRegistries;
import thaumcraft.client.fx.FXDispatcher;

@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Phase10RiftClient{
    private Phase10RiftClient(){}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(Phase10RiftRegistries.FLUX_RIFT,RenderFluxRift::new);event.registerBlockEntityRenderer(Phase10RiftRegistries.VOID_SIPHON,TileVoidSiphonRenderer::new);RiftPresentationHooks.ambient=Phase10RiftClient::ambient;
        RiftPresentationHooks.collapse=rift->{}; // Source has no fabricated final portal burst.
        RiftPresentationHooks.siphon=(tile,seed)->{for(var rift:tile.getValidRifts())FXDispatcher.INSTANCE.voidStreak(rift.getX(),rift.getY(),rift.getZ(),tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5625,tile.getBlockPos().getZ()+.5,seed,.04F);};}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(Phase10RiftRegistries.VOID_SIPHON_MENU,GuiVoidSiphon::new);}
    private static void ambient(EntityFluxRift rift){int size=rift.points.size();if(size<=2)return;var random=rift.getRandom();boolean collapsing=rift.getCollapse();if(!collapsing&&(rift.getRiftStability()>=0||random.nextInt(150)>=Math.abs(rift.getRiftStability())))return;int index=1+random.nextInt(size-2);var point=rift.points.get(index).add(rift.position());float green=collapsing?.3F+random.nextFloat()*.1F:1,blue=collapsing?.3F+random.nextFloat()*.1F:1;
        FXDispatcher.INSTANCE.drawCurlyWisp(point.x,point.y,point.z,0,0,0,.1F+rift.pointsWidth.get(index)*3,1,green,blue,collapsing?.4F:.25F,null,1,0,0);}
}
