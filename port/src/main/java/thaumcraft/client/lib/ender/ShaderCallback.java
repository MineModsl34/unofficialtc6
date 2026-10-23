package thaumcraft.client.lib.ender;
import net.minecraft.client.renderer.ShaderInstance;
/** Native equivalent of the source callback, without obsolete ARB uniform locations. */
@FunctionalInterface public interface ShaderCallback { void call(ShaderInstance shader); }
