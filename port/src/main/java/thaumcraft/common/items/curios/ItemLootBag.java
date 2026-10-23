package thaumcraft.common.items.curios;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.utils.LegacyLootRules;
import thaumcraft.common.lib.utils.SourceRandomAdapter;
import thaumcraft.common.lib.utils.TCLegacyLoot;

/** All three source treasures use the complete old pools/gear/enchantment catalog. */
public final class ItemLootBag extends Item {
    private final int metadata;
    public ItemLootBag(int metadata){super(new Properties().stacksTo(16).rarity(switch(metadata){case 0->Rarity.COMMON;case 1->Rarity.UNCOMMON;case 2->Rarity.RARE;default->throw new IllegalArgumentException("Invalid loot bag rarity");}));this.metadata=metadata;}
    public int metadata(){return metadata;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){super.appendHoverText(stack,context,lines,flags);lines.add(Component.translatable("tc.lootbag"));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide){
            // Resolve genuine rewards before rolling/consuming, never silently shorten a missing pool.
            TCLegacyLoot loot=TCLegacyLoot.create(level.registryAccess());var random=new SourceRandomAdapter(level.random);
            int rolls=8+random.nextInt(5);
            for(int index=0;index<rolls;index++){
                ItemStack reward=loot.generate(LegacyLootRules.Rarity.values()[metadata],random);
                if(!reward.isEmpty())level.addFreshEntity(new ItemEntity(level,player.getX(),player.getY(),player.getZ(),reward.copy()));
            }
            player.playSound(SoundsTC.coins,.75F,1);
        }
        stack.shrink(1); // Source predicts consumption on both logical sides, even in creative.
        return InteractionResultHolder.success(stack);
    }
}
