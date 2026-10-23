package thaumcraft.common.lib.network;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.armor.WearableRules;
import thaumcraft.common.items.baubles.ItemCloudRing;
import thaumcraft.common.lib.events.WearableEvents;
import thaumcraft.common.lib.network.misc.PacketCloudJump;

@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class WearableNetwork {
    private record Airborne(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,int sequence){}
    private static final Map<UUID,Airborne> JUMPED=new HashMap<>();
    private WearableNetwork(){}
    public static void jump(ServerPlayer player,PacketCloudJump packet){
        if(player.isSpectator()||!player.isAlive()||player.onGround()||player.isInWater()||player.noJumpDelay!=0||WearableEvents.equipped(player,ItemCloudRing.class)<0)return;
        if(JUMPED.containsKey(player.getUUID()))return;JUMPED.put(player.getUUID(),new Airborne(player.level().dimension(),packet.sequence()));
        apply(player);player.hurtMarked=true;net.neoforged.neoforge.common.CommonHooks.onLivingJump(player);
    }
    public static void apply(Player player){var boost=player.getEffect(MobEffects.JUMP);var old=player.getDeltaMovement();double x=old.x,z=old.z;
        if(player.isSprinting()){float angle=player.getYRot()*(float)Math.PI/180;x-=Mth.sin(angle)*.2F;z+=Mth.cos(angle)*.2F;}
        player.setDeltaMovement(x,WearableRules.cloudJump(boost==null?-1:boost.getAmplifier()),z);player.fallDistance=0;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){if(event.getEntity().level().isClientSide)return;var player=event.getEntity();var state=JUMPED.get(player.getUUID());if(state!=null&&(player.onGround()&&player.noJumpDelay==0||!state.dimension().equals(player.level().dimension())))JUMPED.remove(player.getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){JUMPED.remove(event.getEntity().getUUID());}
    @EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar(PacketHandler.PROTOCOL_VERSION).playToServer(PacketCloudJump.TYPE,PacketCloudJump.STREAM_CODEC,(packet,context)->{if(context.player() instanceof ServerPlayer player)jump(player,packet);});}
    }
}
