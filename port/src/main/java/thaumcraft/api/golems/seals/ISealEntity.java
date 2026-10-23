package thaumcraft.api.golems.seals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
public interface ISealEntity {
 void tickSealEntity(Level world); ISeal getSeal(); SealPos getSealPos(); byte getPriority();void setPriority(byte value);
 void readNBT(CompoundTag tag,HolderLookup.Provider lookup);CompoundTag writeNBT(HolderLookup.Provider lookup);void syncToClient(Level world);
 BlockPos getArea();void setArea(BlockPos value);boolean isLocked();void setLocked(boolean value);
 boolean isRedstoneSensitive();void setRedstoneSensitive(boolean value);String getOwner();void setOwner(String value);
 byte getColor();void setColor(byte value);boolean isStoppedByRedstone(Level world);
}
