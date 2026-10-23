package thaumcraft.common.items.tools;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.tiles.essentia.TileTube;
import thaumcraft.common.tiles.essentia.TileTubeBuffer;
import thaumcraft.common.tiles.essentia.TransportConnections;

/** Source essentia/suction inspector, separate from the vis resonator crafting ingredient. */
public class ItemResonator extends Item {
    public ItemResonator(Properties properties) { super(properties.stacksTo(1).rarity(Rarity.UNCOMMON)); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer(); var world = context.getLevel(); var pos = context.getClickedPos();
        if (player == null) return InteractionResult.FAIL;
        var transport = TransportConnections.at(world, pos, context.getClickedFace()); if (transport == null) return InteractionResult.FAIL;
        if (world.isClientSide) { player.swing(context.getHand()); return InteractionResult.SUCCESS; }
        Direction side = context.getClickedFace(); var tile = world.getBlockEntity(pos);
        if (tile instanceof TileTube tube) { int subHit = tube.retraceSubHit(player); if (subHit >= 0 && subHit < 6) side = Direction.from3DDataValue(subHit); }
        if (tile instanceof TileTubeBuffer buffer && buffer.aspects.size() > 0) for (Aspect aspect : buffer.aspects.getAspectsSortedByName())
            player.sendSystemMessage(Component.translatable("tc.resonator1", "" + buffer.aspects.getAmount(aspect), Component.literal(aspect.getName())));
        else if (!(tile instanceof TileTubeBuffer) && transport.getEssentiaType(side) != null) player.sendSystemMessage(Component.translatable("tc.resonator1", "" + transport.getEssentiaAmount(side), Component.literal(transport.getEssentiaType(side).getName())));
        Component suctionType = transport.getSuctionType(side) == null ? Component.translatable("tc.resonator3") : Component.literal(transport.getSuctionType(side).getName());
        player.sendSystemMessage(Component.translatable("tc.resonator2", "" + transport.getSuctionAmount(side), suctionType));
        world.playSound(null, pos, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, .5F, 1.9F + world.random.nextFloat() * .1F);
        if (tile instanceof thaumcraft.common.tiles.devices.TileCondenser condenser) {
            player.sendSystemMessage(Component.translatable("tc.condenser1", "" + condenser.cost));
            player.sendSystemMessage(Component.translatable("tc.condenser2", "" + condenser.interval, "" + condenser.interval / 20));
        }
        return InteractionResult.SUCCESS;
    }
}
