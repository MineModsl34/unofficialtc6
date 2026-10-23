package thaumcraft.client.lib.warp;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;

/** Client-only source guardian tick presentation; keeps dedicated-server entity classes client-free. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class WarpEntityPresentation {
    private WarpEntityPresentation() {}
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() instanceof EntityEldritchGuardian guardian && guardian.level().isClientSide
                && guardian.level() == minecraft.level && !guardian.isRemoved()) {
            var random = guardian.getRandom();
            float x = (float)(guardian.getX() + (random.nextFloat() - random.nextFloat()) * 0.2F);
            float z = (float)(guardian.getZ() + (random.nextFloat() - random.nextFloat()) * 0.2F);
            float y = (float)(guardian.getY() + 0.22 * guardian.getBbHeight());
            FXDispatcher.INSTANCE.wispFXEG(x,y,z,guardian);
        }
    }
}
