package thaumcraft.client.gui.model;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;

/** Render-only codec adaptation for the seven builtin TC6 JPEG map backgrounds.
 * Original category metadata, public catalog identity and source assets stay unchanged.
 * Modern NativeImage validates PNG headers; the generated PNG retains every decoded pixel.
 * Arbitrary addon paths and unrelated JPEGs are deliberately not rewritten.
 */
public final class ResearchTexturePaths {
    private ResearchTexturePaths() {}

    private static final Map<ResourceLocation, ResourceLocation> BACKGROUNDS = IntStream.rangeClosed(1, 7)
        .boxed().collect(Collectors.toUnmodifiableMap(
            index -> ResourceLocation.parse("thaumcraft:textures/gui/gui_research_back_" + index + ".jpg"),
            index -> ResourceLocation.parse("thaumcraft:textures/gui/gui_research_back_" + index + ".png")));

    public static ResourceLocation forRendering(ResourceLocation source) {
        return source == null ? null : BACKGROUNDS.getOrDefault(source, source);
    }
}
