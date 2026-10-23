package thaumcraft.common.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Exact seeded two-ended BETA26 walk, including float angle lookup and taper. */
public final class FluxRiftGeometry {
    public record Point(double x, double y, double z) {
        Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        Point scale(double factor) { return new Point(x * factor, y * factor, z * factor); }
        Point normal() { double length = (float)Math.sqrt(x*x + y*y + z*z); return length < 1E-4 ? new Point(0,0,0) : new Point(x/length,y/length,z/length); }
        Point pitch(float angle) { float c = cos(angle), s = sin(angle); return new Point(x, y*c + z*s, z*c - y*s); }
        Point yaw(float angle) { float c = cos(angle), s = sin(angle); return new Point(x*c + z*s, y, z*c - x*s); }
    }
    public record Shape(List<Point> points, List<Float> widths) {}
    private static final float[] SIN = new float[65536];
    static { for (int i = 0; i < SIN.length; i++) SIN[i] = (float)Math.sin(i * Math.PI * 2 / 65536); }
    private static float sin(float angle) { return SIN[(int)(angle * 10430.378F) & 65535]; }
    private static float cos(float angle) { return SIN[(int)(angle * 10430.378F + 16384F) & 65535]; }
    private FluxRiftGeometry() {}
    public static Shape create(int seed, int size) {
        var random = new Random(seed); var points = new ArrayList<Point>(); var widths = new ArrayList<Float>();
        Point right = new Point(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()).normal(), left = right.scale(-1);
        Point lr = new Point(0,0,0), ll = lr; int steps = (int)Math.ceil(FluxRiftRules.boundedSize(size) / 3F);
        float girth = FluxRiftRules.boundedSize(size) / 300F, decrement = steps == 0 ? 0 : girth / steps;
        for (int i = 0; i < steps; i++) {
            girth -= decrement; right = right.pitch((float)(random.nextGaussian() * .33)).yaw((float)(random.nextGaussian() * .33));
            lr = lr.add(right.scale(.2)); points.add(lr); widths.add(girth);
            left = left.pitch((float)(random.nextGaussian() * .33)).yaw((float)(random.nextGaussian() * .33));
            ll = ll.add(left.scale(.2)); points.add(0,ll); widths.add(0,girth);
        }
        points.add(lr.add(right.scale(.1))); widths.add(0F); points.add(0,ll.add(left.scale(.1))); widths.add(0,0F);
        return new Shape(List.copyOf(points),List.copyOf(widths));
    }
}
