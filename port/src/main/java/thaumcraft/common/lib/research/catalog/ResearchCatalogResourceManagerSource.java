package thaumcraft.common.lib.research.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Unwired adapter. Caller MUST pass the server reload's SERVER_DATA ResourceManager, not a client
 * assets manager. ResourceProvider.getResourceOrThrow/getResource select the highest-priority
 * unfiltered resource; no resource stack, lower-priority retry, classpath asset or file fallback.
 * Resource.openAsReader is deliberately avoided because it silently replaces malformed UTF-8.
 */
public final class ResearchCatalogResourceManagerSource implements ResearchCatalogResourceLoader.SourceAccess {
    private final ResourceManager serverData;

    public ResearchCatalogResourceManagerSource(ResourceManager serverData) {
        this.serverData = Objects.requireNonNull(serverData);
    }

    @Override
    public InputStream open(ResourceLocation physicalLocation) throws IOException {
        return serverData.getResourceOrThrow(physicalLocation).open();
    }
}
