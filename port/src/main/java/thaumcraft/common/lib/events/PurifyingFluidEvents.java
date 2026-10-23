package thaumcraft.common.lib.events;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.items.consumables.ItemBathSalts;

@EventBusSubscriber(modid=Thaumcraft.MODID)
public final class PurifyingFluidEvents{
    @SubscribeEvent public static void expires(ItemExpireEvent event){
        var item=event.getEntity();var level=item.level();
        if(level.isClientSide || !(item.getItem().getItem() instanceof ItemBathSalts))return;
        var pos=item.blockPosition();var state=level.getBlockState(pos);
        if(state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL)==0)level.setBlockAndUpdate(pos,BlocksTC.purifyingFluid.defaultBlockState());
    }
}
