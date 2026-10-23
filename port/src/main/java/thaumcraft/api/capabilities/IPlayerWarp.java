package thaumcraft.api.capabilities;

import javax.annotation.Nonnull;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** TC6's three independent warp values and event counter; this API does not apply warp events. */
public interface IPlayerWarp extends INBTSerializable<CompoundTag> {
    void clear();
    int get(@Nonnull EnumWarpType type);
    void set(@Nonnull EnumWarpType type, int amount);
    int add(@Nonnull EnumWarpType type, int amount);
    int reduce(@Nonnull EnumWarpType type, int amount);
    void sync(ServerPlayer player);
    int getCounter();
    void setCounter(int amount);

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);

    @Override
    default CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return serializeNBT();
    }

    @Override
    default void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        deserializeNBT(tag);
    }

    enum EnumWarpType { PERMANENT, NORMAL, TEMPORARY }
}
