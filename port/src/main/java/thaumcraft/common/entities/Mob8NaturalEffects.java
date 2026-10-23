package thaumcraft.common.entities;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.fx.PacketFXWispZap;
/** Side-neutral hooks; native client effects are installed by our own client setup. */
@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class Mob8NaturalEffects {
    public static Consumer<EntityWisp> ambient=entity->{};
    public static Consumer<EntityWisp> death=entity->{};
    private Mob8NaturalEffects(){}
    public static void wispAmbient(EntityWisp wisp){ambient.accept(wisp);}public static void wispDeath(EntityWisp wisp){death.accept(wisp);}
    public static void zap(EntityWisp wisp,LivingEntity target){if(wisp.level() instanceof ServerLevel server)PacketDistributor.sendToPlayersNear(server,null,wisp.getX(),wisp.getY(),wisp.getZ(),32,new PacketFXWispZap(wisp.getId(),target.getId()));}
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){event.registrar(PacketHandler.PROTOCOL_VERSION).playToClient(PacketFXWispZap.TYPE,PacketFXWispZap.STREAM_CODEC,(packet,context)->context.enqueueWork(()->thaumcraft.client.renderers.entity.Mob8NaturalClientEffects.zap(packet)));}
}
