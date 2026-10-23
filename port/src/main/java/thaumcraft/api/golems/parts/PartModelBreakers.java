package thaumcraft.api.golems.parts;

import net.minecraft.resources.ResourceLocation;

/** Source animation descriptor; physical callback is registered only on the client. */
public final class PartModelBreakers extends PartModel {
    public PartModelBreakers(ResourceLocation model,ResourceLocation texture,EnumAttachPoint attachment) { super(model,texture,attachment,Animation.BREAKERS); }
}
