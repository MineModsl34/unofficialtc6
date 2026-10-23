package thaumcraft.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;

/** Source float-precision particle arithmetic; these helpers deliberately preserve TC6's formulas. */
public final class FxMath {
    private FxMath() {}
    public static float sin(float value) { return Mth.sin(value); }
    public static float cos(float value) { return Mth.cos(value); }
    public static double atan2(double y, double x) { return Mth.atan2(y, x); }
    public static float sqrt(double value) { return (float)Math.sqrt(value); }
    public static int floor(double value) { return Mth.floor(value); }
    public static int ceil(double value) { return Mth.ceil(value); }
    public static float clamp(float value, float min, float max) { return Mth.clamp(value, min, max); }
    public static double clamp(double value, double min, double max) { return Mth.clamp(value, min, max); }
    public static int getInt(RandomSource random, int min, int max) { return min >= max ? min : min + random.nextInt(max - min + 1); }
    public static float wrapDegrees(float value) { return Mth.wrapDegrees(value); }
    public static float distance(Entity entity, double x, double y, double z) { return entity==null?0:sqrt(entity.distanceToSqr(x,y,z)); }
    public static AABB bounds(ClientLevel level,BlockPos pos) {
        var shape=level.getBlockState(pos).getShape(level,pos);return shape.isEmpty()?new AABB(0,0,0,0,0,0):shape.bounds();
    }
    public static double distanceSquared3d(Vec3 from, Vec3 to) { return from.distanceToSqr(to); }
    public static Vec3 rotateAroundY(Vec3 vector, float angle) {
        float c = cos(angle), s = sin(angle);
        return new Vec3(vector.x*c+vector.z*s,vector.y,vector.z*c-vector.x*s);
    }
    public static Vec3 calculateVelocity(Vec3 from, Vec3 to, double heightGain, double gravity) {
        double endGain=to.y-from.y, dx=to.x-from.x, dz=to.z-from.z;
        double horizontal=Math.sqrt(dx*dx+dz*dz), gain=Math.max(heightGain,endGain+heightGain);
        double a=-horizontal*horizontal/(4*gain), b=horizontal, c=-endGain;
        double slope=-b/(2*a)-Math.sqrt(b*b-4*a*c)/(2*a), vy=Math.sqrt(gain*gravity), vh=vy/slope;
        // A vertical arc has no horizontal direction. Source generated NaNs here; native mesh must remain finite.
        return horizontal==0 ? new Vec3(0,vy,0) : new Vec3(vh*dx/horizontal,vy,vh*dz/horizontal);
    }
}
