package thaumcraft.api.aspects;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Native sided extension of the original IEssentiaTransport contract, not fluid conversion. */
public final class EssentiaCapabilities {
    public static final BlockCapability<IEssentiaTransport, Direction> TRANSPORT = BlockCapability.createSided(
        ResourceLocation.parse("thaumcraft:essentia_transport"), IEssentiaTransport.class);
    private EssentiaCapabilities() {}
}
