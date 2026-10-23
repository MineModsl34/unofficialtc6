package thaumcraft.api.items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
/** Source popup permission, not a grant of research or scanning. */
public interface IGoggles { boolean showIngamePopups(ItemStack stack, LivingEntity wearer); }
