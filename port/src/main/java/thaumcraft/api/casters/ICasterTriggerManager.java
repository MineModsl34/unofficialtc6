package thaumcraft.api.casters;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
public interface ICasterTriggerManager {boolean performTrigger(Level level,ItemStack caster,Player player,BlockPos pos,Direction side,int event);}
