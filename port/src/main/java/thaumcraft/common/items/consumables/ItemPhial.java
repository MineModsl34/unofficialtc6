package thaumcraft.common.items.consumables;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.TCDataComponents;

/** The genuine ten-unit source phial, flattened empty/filled. No automatic random aspect filling.
 * Container interaction is conditional on later-stage real TC essentia blocks implementing the API. */
public final class ItemPhial extends ItemGenericEssentiaContainer {
    public static ItemPhial emptyVariant, filledVariant;
    private final boolean filled;
    public ItemPhial(Properties properties, boolean filled) {
        super(properties, 10); this.filled = filled;
        if (filled) filledVariant = this; else emptyVariant = this;
    }
    public static ItemStack makePhial(Aspect aspect, int amount) {
        if (filledVariant == null || aspect == null) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(filledVariant);
        filledVariant.setAspects(result, new AspectList().add(aspect, amount)); return result;
    }
    public static ItemStack makeFilledPhial(Aspect aspect) { return makePhial(aspect, 10); }
    @Override public List<ItemStack> getTabVariants() { return filled ? super.getTabVariants() : List.of(new ItemStack(this)); }
    @Override public Component getName(ItemStack stack) {
        AspectList aspects = getAspects(stack);
        return filled && aspects != null && aspects.size() > 0 ? Component.translatable(getDescriptionId(), aspects.getAspects()[0].getName()) : super.getName(stack);
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && filled && !stack.has(TCDataComponents.ASPECTS) && emptyVariant != null && entity instanceof Player player
            && slot >= 0 && slot < player.getInventory().getContainerSize() && player.getInventory().getItem(slot) == stack)
            player.getInventory().setItem(slot, new ItemStack(emptyVariant, stack.getCount()));
        // Do not call ItemGenericEssentiaContainer.inventoryTick: source empty phials stay empty.
    }
    @Override public void onCraftedBy(ItemStack stack, Level level, Player player) {
        // Source ItemPhial.onCreated overrides the generic random-aspect callback.
        // Flattened malformed filled identities are normalized by inventoryTick;
        // this void callback must leave the actual crafted stack/components intact.
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer(); if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel(); var position = context.getClickedPos();
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString();
        boolean jar = block.equals("thaumcraft:jar_normal") || block.equals("thaumcraft:jar_void");
        boolean alembic = block.equals("thaumcraft:alembic");
        if ((!jar && !alembic) || !(level.getBlockEntity(position) instanceof IAspectContainer container)) return InteractionResult.PASS;
        ItemStack result;
        if (!filled) {
            AspectList contents = container.getAspects();
            if (contents == null || contents.size() == 0 || container.containerContains(contents.getAspects()[0]) < 10) return InteractionResult.PASS;
            Aspect aspect = contents.getAspects()[0];
            if (level.isClientSide) { player.swing(context.getHand()); return InteractionResult.PASS; }
            if (!container.takeFromContainer(aspect, 10)) return InteractionResult.PASS;
            result = makeFilledPhial(aspect);
        } else {
            AspectList contents = getAspects(stack);
            if (!jar || contents == null || contents.size() != 1 || emptyVariant == null) return InteractionResult.PASS;
            Aspect aspect = contents.getAspects()[0];
            AspectList current = container.getAspects();
            int existing = current == null ? 0 : current.visSize();
            if (existing > 240 || !container.doesContainerAccept(aspect)) return InteractionResult.PASS;
            if (level.isClientSide) { player.swing(context.getHand()); return InteractionResult.PASS; }
            if (container.addToContainer(aspect, 10) != 0) return InteractionResult.PASS;
            result = new ItemStack(emptyVariant); level.sendBlockUpdated(position, level.getBlockState(position), level.getBlockState(position), 3);
        }
        stack.shrink(1);
        if (!player.getInventory().add(result)) player.drop(result, false);
        if (level.getBlockEntity(position) != null) level.getBlockEntity(position).setChanged();
        player.playSound(SoundEvents.BOTTLE_FILL, .25F, 1F); player.containerMenu.broadcastChanges();
        return InteractionResult.SUCCESS;
    }
}
