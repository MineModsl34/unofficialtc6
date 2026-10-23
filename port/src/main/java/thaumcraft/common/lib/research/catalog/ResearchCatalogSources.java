package thaumcraft.common.lib.research.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;

/**
 * Bounded server-data I/O, independent of the mutable public research API. A registration identity
 * remains the source label; only the physical lookup adds a missing .json suffix, as TC6
 * ResearchManager.java:262-265 did. ResourceManager.getResourceOrThrow selects the highest-priority
 * visible resource, NOT every member of its pack stack. Moving old assets to server-data and
 * rejecting an entire invalid/oversized reload are explicit modern lifecycle adaptations.
 */
public final class ResearchCatalogSources {
    private ResearchCatalogSources() {}

    /** Must be constructed on the API owner's thread, never while an addon mutates public maps. */
    public record RegistrationSnapshot(List<ResourceLocation> registrations, Set<String> categoryKeys) {
        public RegistrationSnapshot {
            if (registrations.size() > ResearchCatalogResourceLoader.MAX_INPUT_REGISTRATIONS) {
                throw new IllegalArgumentException("Too many raw research registrations");
            }
            // Public jsonLocs may contain aliases for the same value: original location registration
            // is idempotent, so preserve first-seen identity order rather than count aliases as files.
            var unique = new LinkedHashSet<ResourceLocation>();
            int examined = 0;
            for (ResourceLocation registration : registrations) {
                if (++examined > ResearchCatalogResourceLoader.MAX_INPUT_REGISTRATIONS) {
                    throw new IllegalArgumentException("Too many raw research registrations");
                }
                unique.add(Objects.requireNonNull(registration));
                if (unique.size() > PacketSyncResearchCatalog.MAX_SOURCES) {
                    throw new IllegalArgumentException("Too many unique research registrations");
                }
            }
            registrations = List.copyOf(unique);
            categoryKeys = Collections.unmodifiableSet(new LinkedHashSet<>(categoryKeys));
            if (categoryKeys.contains(null)) throw new IllegalArgumentException("Null research category key");
            if (registrations.size() > PacketSyncResearchCatalog.MAX_SOURCES) {
                throw new IllegalArgumentException("Too many research registrations");
            }
            Map<ResourceLocation, String> identities = new LinkedHashMap<>();
            for (ResourceLocation registration : registrations) {
                identities.put(registration, "");
            }
            // Validates identifier lengths/syntax before any resource is opened.
            new PacketSyncResearchCatalog(identities);
        }
    }

    /** Both components are immutable, bounded, and detached from ResearchCategories. */
    public record Loaded(ResearchCatalogSpec spec, PacketSyncResearchCatalog payload) {
        public Loaded { Objects.requireNonNull(spec); Objects.requireNonNull(payload); }
    }

    @FunctionalInterface
    public interface SourceOpener {
        InputStream open(ResourceLocation physicalLocation) throws IOException;
    }

    public static Loaded load(RegistrationSnapshot snapshot, ResourceManager resources) {
        Objects.requireNonNull(resources);
        return load(snapshot, new ResearchCatalogResourceManagerSource(resources)::open);
    }

    /** Testable I/O boundary; opener must select one highest-priority resource per physical ID. */
    public static Loaded load(RegistrationSnapshot snapshot, SourceOpener opener) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(opener);
        ResearchCatalogResourceLoader.Loaded loaded;
        try {
            loaded = ResearchCatalogResourceLoader.load(snapshot.registrations(), snapshot.categoryKeys(), opener::open);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Cannot load whole research catalog", invalid);
        }
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        loaded.spec().sources().forEach((identity, raw) -> sources.put(ResourceLocation.parse(identity), raw));
        // Transport construction must succeed BEFORE a candidate can reach publication.
        PacketSyncResearchCatalog payload = new PacketSyncResearchCatalog(sources);
        return new Loaded(loaded.spec(), payload);
    }

    public static ResourceLocation physicalLocation(ResourceLocation registration) {
        return ResearchCatalogResourceLoader.physicalLocation(registration);
    }
}
