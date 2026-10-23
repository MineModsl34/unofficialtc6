package thaumcraft.api.golems.seals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.tasks.Task;
/** BETA26 lifecycle. Registry context is explicit for modern component-bearing stacks. */
public interface ISeal {
 String getKey(); boolean canPlaceAt(Level world,BlockPos pos,Direction side); void tickSeal(Level world,ISealEntity seal);
 default void onTaskStarted(Level world,IGolemAPI golem,Task task){}
 boolean onTaskCompletion(Level world,IGolemAPI golem,Task task);
 default void onTaskSuspension(Level world,Task task){}
 boolean canGolemPerformTask(IGolemAPI golem,Task task);
 default void readCustomNBT(CompoundTag tag,HolderLookup.Provider lookup){}
 default void writeCustomNBT(CompoundTag tag,HolderLookup.Provider lookup){}
 ResourceLocation getSealIcon();
 default void onRemoval(Level world,BlockPos pos,Direction side){}
 default Object returnContainer(Level world,Player player,BlockPos pos,Direction side,ISealEntity seal){return thaumcraft.common.golems.seals.SealGuiBridge.container(player,seal);}
 /** Client factories belong to the dist-gated registration, never a common class load. */
 default Object returnGui(Level world,Player player,BlockPos pos,Direction side,ISealEntity seal){return thaumcraft.common.golems.seals.SealGuiBridge.screen(player,seal);}
 EnumGolemTrait[] getRequiredTags(); EnumGolemTrait[] getForbiddenTags();
}
