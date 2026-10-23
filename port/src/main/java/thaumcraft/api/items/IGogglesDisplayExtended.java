package thaumcraft.api.items;

import net.minecraft.world.phys.Vec3;

/** Source extended goggles text API. Rendering is a client consumer, never a research grant. */
public interface IGogglesDisplayExtended {
    String[] getIGogglesText();
    default Vec3 getIGogglesTextOffset() { return Vec3.ZERO; }
}
