package thaumcraft.client.lib;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.tools.ItemThaumometer;
import thaumcraft.common.lib.utils.ScanTargeting;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.client.fx.FXDispatcher;

/** Source five-tick entity/wild-block scan highlights; cached target is visual, never scan authority. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientThaumometerHooks {
    private static Entity target;
    private static Player owner;

    private ClientThaumometerHooks() {}

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide || player != Minecraft.getInstance().player) return;
        if (owner != player) { owner = player; target = null; }
        if (!holdsLegacySlot(player)) { target = null; return; }
        if (player.tickCount % 5 == 0) {
            target = ScanTargeting.pointedEntity(player.level(), player, 1.0D, 16.0D, 5.0F, true);
            if (target != null && ScanningManager.isThingStillScannable(player,target)) FXDispatcher.INSTANCE.scanHighlight(target);
            var random=player.level().random;
            float pitch=player.getXRot()+random.nextInt(25)-random.nextInt(25);
            float yaw=player.getYRot()+random.nextInt(25)-random.nextInt(25);
            float radians=(float)(Math.PI/180),yc=Mth.cos(-yaw*radians-(float)Math.PI),ys=Mth.sin(-yaw*radians-(float)Math.PI),pc=-Mth.cos(-pitch*radians),ps=Mth.sin(-pitch*radians);
            Vec3 eye=player.getEyePosition();Vec3 end=eye.add(ys*pc*16,ps*16,yc*pc*16);
            var hit=player.level().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,player));
            if(hit.getType()==HitResult.Type.BLOCK&&ScanningManager.isThingStillScannable(player,hit.getBlockPos()))FXDispatcher.INSTANCE.scanHighlight(hit.getBlockPos());
        }
    }

    @Nullable
    public static Entity currentTarget() {
        return owner == Minecraft.getInstance().player && owner != null && holdsLegacySlot(owner)
            && target != null && !target.isRemoved() && target.level() == owner.level() ? target : null;
    }

    private static boolean holdsLegacySlot(Player player) {
        var inventory = player.getInventory();
        return thaumometer(player.getMainHandItem()) || thaumometer(inventory.items.get(0))
            || thaumometer(inventory.armor.get(0)) || thaumometer(inventory.offhand.get(0))
            || inventory.selected >= 1 && inventory.selected < inventory.armor.size()
                && thaumometer(inventory.armor.get(inventory.selected));
    }

    private static boolean thaumometer(ItemStack stack) { return stack.getItem() instanceof ItemThaumometer; }

    /** Client Item.use hook; common/server code reaches it only through a sided presentation bridge. */
    public static void drawScanRunes(Player player) {
        if(player!=Minecraft.getInstance().player||!player.level().isClientSide)return;
        Entity target=ScanTargeting.pointedEntity(player.level(),player,1,9,0,true);
        var hit=target==null?ScanTargeting.legacyBlockHit(player):null;
        if(target==null&&(hit==null||hit.getType()!=HitResult.Type.BLOCK))return;
        double x=target==null?hit.getBlockPos().getX():target.getX()-.5;
        double y=target==null?hit.getBlockPos().getY()+.25:target.getY()+target.getEyeHeight()/2F;
        double z=target==null?hit.getBlockPos().getZ():target.getZ()-.5;
        int lifetime=target==null?15:(int)(target.getBbHeight()*15);
        for(int i=0;i<10;i++)FXDispatcher.INSTANCE.blockRunes(x,y,z,.3F+player.level().random.nextFloat()*.7F,0,.3F+player.level().random.nextFloat()*.7F,lifetime,.03F);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { owner = null; target = null; }
}
