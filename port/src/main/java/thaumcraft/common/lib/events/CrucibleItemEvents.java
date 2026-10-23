package thaumcraft.common.lib.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import thaumcraft.Thaumcraft;

/** Original PlayerEvents.droppedItem username receipt; only actual player tosses have an owner. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class CrucibleItemEvents {
    private CrucibleItemEvents() {}
    @SubscribeEvent public static void droppedItem(ItemTossEvent event) {
        if (!event.getPlayer().level().isClientSide)
            event.getEntity().getPersistentData().putString("thrower", event.getPlayer().getGameProfile().getName());
    }
}
