package thaumcraft.common.items.baubles;
import net.minecraft.world.item.*;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
/** Genuine source Baubles slot contract adapted to Curios. Cosmetic slots never execute effects. */
public class SourceCurioItem extends Item implements ICurioItem {
    private final String slot;
    protected SourceCurioItem(String slot) { super(new Properties().stacksTo(1).rarity(Rarity.RARE)); this.slot=slot; }
    public String slotIdentifier() { return slot; }
    @Override public boolean canEquip(SlotContext context,ItemStack stack) { return slot.equals(context.identifier()); }
}
