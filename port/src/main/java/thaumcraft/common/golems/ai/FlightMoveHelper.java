package thaumcraft.common.golems.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/** Source reusable bobbing flight controller; the golem itself has its separate acceleration controller. */
public class FlightMoveHelper extends MoveControl {
    public FlightMoveHelper(Mob mob) { super(mob); }
    @Override public void tick() {
        if (operation != Operation.MOVE_TO || mob.getNavigation().isDone()) { mob.setSpeed(0); return; }
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-8) { mob.setSpeed(0); return; }
        dy /= distance;
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 30));
        mob.yBodyRot = mob.getYRot();
        float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        mob.setSpeed(mob.getSpeed() + (speed - mob.getSpeed()) * 0.125F);
        double bob = Math.sin((mob.tickCount + mob.getId()) * 0.5) * 0.05;
        double cosine = Math.cos(mob.getYRot() * Math.PI / 180), sine = Math.sin(mob.getYRot() * Math.PI / 180);
        double yBob = Math.sin((mob.tickCount + mob.getId()) * 0.75) * 0.05;
        mob.setDeltaMovement(mob.getDeltaMovement().add(bob * cosine, yBob * (sine + cosine) * 0.25 + mob.getSpeed() * dy * 0.1, bob * sine));
        var look = mob.getLookControl();
        double x = mob.getX() + dx / distance * 2, y = mob.getEyeY() + dy / distance, z = mob.getZ() + dz / distance * 2;
        double previousX = look.isLookingAtTarget() ? look.getWantedX() : x;
        double previousY = look.isLookingAtTarget() ? look.getWantedY() : y;
        double previousZ = look.isLookingAtTarget() ? look.getWantedZ() : z;
        look.setLookAt(previousX + (x - previousX) * 0.125, previousY + (y - previousY) * 0.125, previousZ + (z - previousZ) * 0.125, 10, 40);
    }
}
