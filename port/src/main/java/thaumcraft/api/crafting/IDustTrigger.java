package thaumcraft.api.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** TC6 dust extension point, with modern Level/Player/Direction names. Registration order is
 * significant: only the first matching trigger is executed. Execute receives an already paid
 * dust attempt. Addons should register during common setup and never mutate the list in use.
 */
public interface IDustTrigger {
    ArrayList<IDustTrigger> triggers = new ArrayList<>();

    Placement getValidFace(Level world, Player player, BlockPos pos, Direction face);
    void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side);

    default List<BlockPos> sparkle(Level world, Player player, BlockPos pos, Placement placement) {
        return List.of(pos);
    }

    /** Modern pre-payment safety seam. The source had no queue bound; known adapters reject
     * unavailable outputs or a full queue here rather than charging a knowingly unusable attempt. */
    default boolean canExecute(Level world, Player player, BlockPos pos, Placement placement) { return true; }

    static void registerDustTrigger(IDustTrigger trigger) { triggers.add(Objects.requireNonNull(trigger, "trigger")); }

    class Placement {
        public int xOffset, yOffset, zOffset;
        public Direction facing;
        public Placement(int xOffset, int yOffset, int zOffset, Direction facing) {
            this.xOffset = xOffset; this.yOffset = yOffset; this.zOffset = zOffset; this.facing = facing;
        }
    }
}
