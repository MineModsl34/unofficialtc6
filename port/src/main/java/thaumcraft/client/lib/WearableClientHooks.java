package thaumcraft.client.lib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.items.IGogglesDisplayExtended;
import thaumcraft.common.items.baubles.ItemCloudRing;
import thaumcraft.common.lib.events.WearableEvents;
import thaumcraft.common.lib.network.WearableNetwork;
import thaumcraft.common.lib.network.misc.PacketCloudJump;

/** Source armor display/cloud input; goggles world billboards belong to RenderEventHandler. */
@EventBusSubscriber(modid=Thaumcraft.MODID,value=Dist.CLIENT)
public final class WearableClientHooks {
    private static Player owner;private static boolean held,jumped;private static int sequence;
    private WearableClientHooks(){}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){var mc=Minecraft.getInstance();Player player=event.getEntity();if(player!=mc.player||!player.level().isClientSide)return;if(owner!=player){owner=player;held=jumped=false;sequence=0;}
        boolean down=mc.options.keyJump.isDown();if(player.onGround()&&player.noJumpDelay==0)jumped=false;
        if(down&&!held&&!jumped&&!player.onGround()&&!player.isInWater()&&player.noJumpDelay==0&&WearableEvents.equipped(player,ItemCloudRing.class)>=0){jumped=true;WearableNetwork.apply(player);PacketDistributor.sendToServer(new PacketCloudJump(sequence==Integer.MAX_VALUE?sequence=1:++sequence));player.level().playLocalSound(player.getX(),player.getY(),player.getZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.1F,1+(float)player.getRandom().nextGaussian()*.05F,false);for(int i=0;i<16;i++)player.level().addParticle(ParticleTypes.CLOUD,player.getX(),player.getY()+.5,player.getZ(),(player.getRandom().nextDouble()-.5)*.2,.02,(player.getRandom().nextDouble()-.5)*.2);net.neoforged.neoforge.common.CommonHooks.onLivingJump(player);}held=down;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){owner=null;held=jumped=false;sequence=0;}
    /** Forge 1.12 getArmorDisplay affects the bar, not armor attributes or damage reduction. */
    public static boolean renderSourceArmor(GuiGraphics graphics){var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.gameMode==null||!mc.gameMode.canHurtPlayer()||!(mc.getCameraEntity() instanceof Player player))return false;int armor=WearableEvents.displayedArmor(player);if(armor==player.getArmorValue())return false;if(armor>0){int x=graphics.guiWidth()/2-91,y=graphics.guiHeight()-mc.gui.leftHeight;com.mojang.blaze3d.systems.RenderSystem.enableBlend();for(int i=0;i<10;i++){String sprite=i*2+1<armor?"armor_full":i*2+1==armor?"armor_half":"armor_empty";graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/"+sprite),x+i*8,y,9,9);}com.mojang.blaze3d.systems.RenderSystem.disableBlend();mc.gui.leftHeight+=10;}return true;}
    // Goggles are not a screen-space list: source billboards are drawn in RenderEventHandler.
}
