package thaumcraft.common.entities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import thaumcraft.Thaumcraft;
/** Deferred invalidation: tags event may precede a world, rebuilding waits for a genuine level. */
@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class PechTradeReload {
    private PechTradeReload(){}
    @SubscribeEvent public static void tags(TagsUpdatedEvent event){if(event.shouldUpdateStaticData())PechTrades.invalidate();}
}
