package thaumcraft.api.internal;

import java.util.Objects;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source mutable coordinates with named dimensions. Old custom numeric IDs need an explicit mapper. */
public class WorldCoordinates implements Comparable<WorldCoordinates> {
    public BlockPos pos;
    public ResourceKey<Level> dim = Level.OVERWORLD;
    public WorldCoordinates() {}
    public WorldCoordinates(BlockPos pos, ResourceKey<Level> dimension) { set(pos, dimension); }
    public WorldCoordinates(BlockEntity tile) { this(tile.getBlockPos(), Objects.requireNonNull(tile.getLevel()).dimension()); }
    public WorldCoordinates(WorldCoordinates coordinates) { this(coordinates.pos, coordinates.dim); }
    public void set(BlockPos pos, ResourceKey<Level> dimension) { this.pos = pos.immutable(); this.dim = Objects.requireNonNull(dimension); }
    @Override public boolean equals(Object value) {
        return value instanceof WorldCoordinates coordinates && Objects.equals(pos, coordinates.pos) && dim.equals(coordinates.dim);
    }
    @Override public int hashCode() { return Objects.hash(pos, dim); }
    /** Named dimensions have a stable total order; the old cross-dimension comparator returned -1 both ways. */
    public int compareWorldCoordinate(WorldCoordinates coordinates) {
        int dimensions = dim.location().compareTo(coordinates.dim.location());
        return dimensions == 0 ? pos.compareTo(coordinates.pos) : dimensions;
    }
    @Override public int compareTo(WorldCoordinates coordinates) { return compareWorldCoordinate(coordinates); }
    public double getDistanceSquared(BlockPos pos) { return this.pos.distSqr(pos); }
    public double getDistanceSquaredToWorldCoordinates(WorldCoordinates coordinates) { return getDistanceSquared(coordinates.pos); }
    public void writeNBT(CompoundTag tag) {
        tag.putInt("w_x", pos.getX()); tag.putInt("w_y", pos.getY()); tag.putInt("w_z", pos.getZ());
        tag.putString("w_d", dim.location().toString());
    }
    public void readNBT(CompoundTag tag) { readNBT(tag, WorldCoordinates::vanillaDimension); }
    public void readNBT(CompoundTag tag, IntFunction<ResourceKey<Level>> legacyDimensions) {
        ResourceKey<Level> dimension = tag.contains("w_d", Tag.TAG_STRING)
            ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("w_d")))
            : Objects.requireNonNull(legacyDimensions.apply(tag.getInt("w_d")), "Unmapped old dimension");
        set(new BlockPos(tag.getInt("w_x"), tag.getInt("w_y"), tag.getInt("w_z")), dimension);
    }
    private static ResourceKey<Level> vanillaDimension(int id) {
        return switch (id) { case 0 -> Level.OVERWORLD; case -1 -> Level.NETHER; case 1 -> Level.END;
            default -> throw new IllegalArgumentException("Supply a dimension mapper for old id " + id); };
    }
}
