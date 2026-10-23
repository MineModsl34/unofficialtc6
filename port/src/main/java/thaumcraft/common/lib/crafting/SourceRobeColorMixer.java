package thaumcraft.common.lib.crafting;

/** TC6 robe recipe's exact float/truncation/brightness arithmetic, not modern applyDyes. */
public final class SourceRobeColorMixer {
    private final int[] channels = new int[3];
    private int brightness, samples;

    public void addArmorColor(int color) {
        float red = (color >> 16 & 255) / 255F;
        float green = (color >> 8 & 255) / 255F;
        float blue = (color & 255) / 255F;
        brightness = (int)(brightness + Math.max(red, Math.max(green, blue)) * 255F);
        channels[0] = (int)(channels[0] + red * 255F);
        channels[1] = (int)(channels[1] + green * 255F);
        channels[2] = (int)(channels[2] + blue * 255F);
        samples++;
    }

    public void addDyeColor(int color) {
        // EnumDyeColor.getColorComponentValues() derives these same floats from RGB.
        int red = (int)((color >> 16 & 255) / 255F * 255F);
        int green = (int)((color >> 8 & 255) / 255F * 255F);
        int blue = (int)((color & 255) / 255F * 255F);
        brightness += Math.max(red, Math.max(green, blue));
        channels[0] += red; channels[1] += green; channels[2] += blue;
        samples++;
    }

    public int sampleCount() { return samples; }

    public int finish() {
        if (samples == 0) throw new IllegalStateException("A robe color needs a source armor/dye sample");
        int red = channels[0] / samples, green = channels[1] / samples, blue = channels[2] / samples;
        float meanBrightness = (float)brightness / samples;
        float maximum = Math.max(red, Math.max(green, blue));
        // A zero/zero division deliberately becomes NaN -> int0, as in the source.
        red = (int)(red * meanBrightness / maximum);
        green = (int)(green * meanBrightness / maximum);
        blue = (int)(blue * meanBrightness / maximum);
        return (((red << 8) + green) << 8) + blue;
    }
}
