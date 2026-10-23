package thaumcraft.common.items.curios;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.research.ResearchManager;

/** TC6 Pech's Wand is a consumed research curio, NOT a focus caster. */
public final class ItemPechWand extends Item {
    private static final java.util.Random SOUND_RANDOM=new java.util.Random(); // Source Item.itemRand is separate from player research RNG.
    public ItemPechWand(){super(new Properties().rarity(Rarity.RARE));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){lines.add(Component.translatable("item.curio.text"));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var knowledge=ThaumcraftCapabilities.getKnowledge(player);ItemStack stack=player.getItemInHand(hand);
        if(!knowledge.isResearchKnown("BASEAUROMANCY")){
            if(!level.isClientSide)player.displayClientMessage(Component.translatable("not.pechwand").withStyle(ChatFormatting.RED),false);
            return InteractionResultHolder.pass(stack);
        }
        if(!player.isCreative())stack.shrink(1); // Source consumption predicts on both logical sides.
        level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundsTC.learn,net.minecraft.sounds.SoundSource.NEUTRAL,.5F,.4F/(SOUND_RANDOM.nextFloat()*.4F+.8F));
        if(!level.isClientSide){
            if(!knowledge.isResearchKnown("FOCUSPECH")){ResearchManager.progressResearch(player,"FOCUSPECH");player.displayClientMessage(Component.translatable("got.pechwand").withStyle(ChatFormatting.DARK_PURPLE),false);}
            ResearchCategory[] categories=ResearchCategories.researchCategories.values().toArray(ResearchCategory[]::new);
            if(categories.length>0){gain(player,EnumKnowledgeType.OBSERVATION,categories[player.getRandom().nextInt(categories.length)],3,2);gain(player,EnumKnowledgeType.THEORY,categories[player.getRandom().nextInt(categories.length)],5,4);}
        }
        player.awardStat(Stats.ITEM_USED.get(this));return InteractionResultHolder.pass(stack);
    }
    private static void gain(Player player,EnumKnowledgeType type,ResearchCategory category,int minDivisor,int maxDivisor){int p=type.getProgression(),min=p/minDivisor,max=p/maxDivisor;ResearchManager.addKnowledge(player,type,category,min+player.getRandom().nextInt(max-min+1));}
}
