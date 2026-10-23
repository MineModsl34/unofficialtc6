package thaumcraft.common.items.armor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.ItemsTC;

/** Source special material, fabric repair, always-dyeable robes and cauldron washing. */
public class ItemRobeArmor extends ArmorItem implements IVisDiscountGear {
    public static final int DEFAULT_COLOR = 6961280;
    public ItemRobeArmor(Type type) { this(type,WearableRegistries.CLOTH,25,Rarity.UNCOMMON); }
    protected ItemRobeArmor(Type type,net.minecraft.core.Holder<ArmorMaterial> material,int durability,Rarity rarity) { super(material,type,new Properties().durability(type.getDurability(durability)).rarity(rarity).component(DataComponents.DYED_COLOR,new DyedItemColor(DEFAULT_COLOR,false))); }
    public int getColor(ItemStack stack) { return DyedItemColor.getOrDefault(stack,DEFAULT_COLOR); }
    public boolean hasColor(ItemStack stack) { return true; }
    public void setColor(ItemStack stack,int color) { stack.set(DataComponents.DYED_COLOR,new DyedItemColor(color & 0xFFFFFF,true)); }
    public void removeColor(ItemStack stack) { stack.set(DataComponents.DYED_COLOR,new DyedItemColor(DEFAULT_COLOR,false)); }
    @Override public boolean isValidRepairItem(ItemStack armor,ItemStack repair) { return repair.is(ItemsTC.fabric) || super.isValidRepairItem(armor,repair); }
    @Override public int getVisDiscount(ItemStack stack,Player wearer) { return getType() == Type.BOOTS ? 2 : 3; }
    @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity wearer,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner) { return ResourceLocation.parse("thaumcraft:textures/entity/armor/robes_" + (inner ? 2 : 1) + (layer.dyeable() ? "" : "_overlay") + ".png"); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) > 0) {
            if (!context.getLevel().isClientSide) { removeColor(stack); LayeredCauldronBlock.lowerFillLevel(state,context.getLevel(),context.getClickedPos()); }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.onItemUseFirst(stack,context);
    }
}
