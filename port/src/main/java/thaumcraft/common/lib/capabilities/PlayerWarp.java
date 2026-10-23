package thaumcraft.common.lib.capabilities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import thaumcraft.api.capabilities.IPlayerWarp;

/** Production attachment factory for the TC6 storage implementation, without gameplay warp events. */
public final class PlayerWarp implements IPlayerWarp {
    private int[] warp = new int[EnumWarpType.values().length];
    private int counter;

    public PlayerWarp() {}

    @Override
    public void clear() {
        warp = new int[EnumWarpType.values().length];
        counter = 0;
    }

    @Override
    public int get(EnumWarpType type) {
        return warp[type.ordinal()];
    }

    @Override
    public void set(EnumWarpType type, int amount) {
        warp[type.ordinal()] = Mth.clamp(amount, 0, 500);
    }

    @Override
    public int add(EnumWarpType type, int amount) {
        warp[type.ordinal()] = Mth.clamp(warp[type.ordinal()] + amount, 0, 500);
        return warp[type.ordinal()];
    }

    @Override
    public int reduce(EnumWarpType type, int amount) {
        warp[type.ordinal()] = Mth.clamp(warp[type.ordinal()] - amount, 0, 500);
        return warp[type.ordinal()];
    }

    @Override
    public void sync(ServerPlayer player) {
        PlayerDataSync.syncWarpNow(player);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        // TC6 NBTTagIntArray and modern IntArrayTag both alias this array. Network snapshots copy.
        tag.putIntArray("warp", warp);
        tag.putInt("counter", getCounter());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        clear();
        int[] values = tag.getIntArray("warp");
        for (int i = 0; i < Math.min(values.length, EnumWarpType.values().length); i++) {
            // Loading is deliberately not clamped; only subsequent mutators clamp their own value.
            warp[i] = values[i];
        }
        setCounter(tag.getInt("counter"));
    }

    @Override
    public int getCounter() { return counter; }

    @Override
    public void setCounter(int amount) { counter = amount; }
}
