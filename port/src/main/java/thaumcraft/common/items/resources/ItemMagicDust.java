package thaumcraft.common.items.resources;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.crafting.DustTriggers;

/** Source ordered IDustTrigger pipeline: first match, one dust before execute, creative exemption,
 * server SUCCESS/client PASS. Native bookshelf/workbench paths are preserved; cauldron/alchemy and
 * addon simple/tag triggers share the paid delayed swap. Client visuals use the original sparkle pipeline.
 */
public final class ItemMagicDust extends Item {
    public ItemMagicDust() { this(new Properties()); }
    public ItemMagicDust(Properties properties) { super(properties.rarity(Rarity.UNCOMMON).setNoRepair()); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), player.getItemInHand(context.getHand()))) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        player.swing(context.getHand()); // Source swings even when no trigger matches.
        DustTriggers.Match match = DustTriggers.match(context.getLevel(), player, context.getClickedPos(), context.getClickedFace());
        if (match == null || !match.trigger().canExecute(context.getLevel(), player, context.getClickedPos(), match.placement())) return InteractionResult.PASS;
        if (!player.getAbilities().instabuild) player.getItemInHand(context.getHand()).shrink(1);
        match.trigger().execute(context.getLevel(), player, context.getClickedPos(), match.placement(), context.getClickedFace());
        if (!context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (context.getLevel().isClientSide) {
            var world=context.getLevel();
            float angle=(player.getYRot()+(context.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND?0F:180F))/180F*3.141593F;
            var look=player.getViewVector(1F);
            var hand=new net.minecraft.world.phys.Vec3(player.getX()-net.minecraft.util.Mth.cos(angle)*.3F+look.x*.3,
                player.getBoundingBox().minY+player.getBbHeight()/2F+.25+look.y*.3,
                player.getZ()-net.minecraft.util.Mth.sin(angle)*.3F+look.z*.3);
            thaumcraft.common.lib.fx.SourceFX.local(world,"dust_hand",hand,
                net.minecraft.world.phys.Vec3.atCenterOf(context.getClickedPos()).subtract(hand),0,0);
            // No built-in client crafting event/research grant or delayed world mutation.
            context.getLevel().playLocalSound(context.getClickedPos().getX(), context.getClickedPos().getY(), context.getClickedPos().getZ(),
                SoundsTC.dust, SoundSource.PLAYERS, .33F, 1F + (float) context.getLevel().random.nextGaussian() * .05F, false);
            var sparkles=match.trigger().sparkle(world,player,context.getClickedPos(),match.placement());
            if(sparkles!=null)for(var pos:sparkles)thaumcraft.common.lib.fx.SourceFX.local(world,"dust_block",
                net.minecraft.world.phys.Vec3.atLowerCornerOf(pos),context.getClickLocation(),0,0);
        }
        return InteractionResult.PASS;
    }
}
