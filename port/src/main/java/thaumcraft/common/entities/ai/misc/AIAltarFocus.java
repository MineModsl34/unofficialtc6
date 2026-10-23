package thaumcraft.common.entities.ai.misc;

import java.util.EnumSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.monster.cult.EntityCultistCleric;

/** Source ritual locks move/look/jump and checks genuine eldritch altar/home every40ticks. */
public final class AIAltarFocus extends Goal {
    private final EntityCultistCleric cleric;
    public AIAltarFocus(EntityCultistCleric cleric) { this.cleric = cleric; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
    @Override public boolean canUse() { return cleric.getIsRitualist() && cleric.hasRestriction(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void tick() {
        if (cleric.hasRestriction() && cleric.tickCount % 40 == 0 && (Vec3.atLowerCornerOf(cleric.getRestrictCenter()).distanceToSqr(cleric.position()) > 16
            || !BuiltInRegistries.BLOCK.getKey(cleric.level().getBlockState(cleric.getRestrictCenter()).getBlock()).equals(ResourceLocation.parse("thaumcraft:eldritch")))) cleric.setIsRitualist(false);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
