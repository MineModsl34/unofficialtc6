package thaumcraft.common.lib.research.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure server-data catalog loader: byte/UTF-8/JSON failures reject the entire detached candidate.
 * This component itself has no event listener, public API publication, player mutation, asset
 * lookup or filesystem fallback; runtime wiring consumes its immutable result separately.
 *
 * Compatibility: TC6 loaded classpath /assets/<namespace>/<registeredPath>[.json]. The modern
 * server ResourceManager must instead select data/<namespace>/<registeredPath>[.json] from
 * datapacks. Built-ins use research/*, but addon registration paths are not restricted to that
 * prefix. The original conditional .json suffix and exact registration labels are preserved;
 * a caller registering both foo and foo.json can cause an explicit whole-candidate key collision.
 */
public final class ResearchCatalogResourceLoader {
    public static final int MAX_UTF8_BYTES = 512 * 1024;
    public static final int MAX_REGISTRATION_ID_UTF8_BYTES = 1024;
    public static final int MAX_REGISTRATIONS = 64;
    public static final int MAX_INPUT_REGISTRATIONS = 4096;

    private ResearchCatalogResourceLoader() {}

    /**
     * Must open the selected highest-priority resource only; absent sources must throw (or return
     * null, which is rejected). A failure must not silently try a lower-priority resource/asset.
     * The loader owns and closes each returned stream, including on budget/UTF-8 failure.
     */
    @FunctionalInterface
    public interface SourceAccess {
        InputStream open(ResourceLocation physicalLocation) throws IOException;
    }

    public record Loaded(ResearchCatalogSpec spec, List<ResourceLocation> registrations, int utf8Bytes) {
        public Loaded {
            Objects.requireNonNull(spec);
            registrations = List.copyOf(registrations);
            if (registrations.size() > MAX_REGISTRATIONS || utf8Bytes < 0 || utf8Bytes > MAX_UTF8_BYTES) {
                throw new IllegalArgumentException("Invalid loaded catalog bounds");
            }
        }
    }

    public static final class SourceException extends IOException {
        private final ResourceLocation registration;
        private final ResourceLocation physicalLocation;
        private SourceException(ResourceLocation registration, ResourceLocation physicalLocation, String message, Throwable cause) {
            super(registration + " (server data " + physicalLocation + "): " + message, cause);
            this.registration = registration;
            this.physicalLocation = physicalLocation;
        }
        public ResourceLocation registration() { return registration; }
        public ResourceLocation physicalLocation() { return physicalLocation; }
    }

    /**
     * Registrations are snapshotted before any I/O: exact duplicate IDs are first-seen idempotent,
     * at most 64 unique IDs, 1024 UTF-8 bytes per ID and 4096 raw inputs. Opaque category keys must come from the caller's
     * detached default+external category definitions; this method never reads global categories.
     * The 512 KiB budget counts original UTF-8 bytes only (future packet framing has separate cost).
     */
    public static Loaded load(Collection<ResourceLocation> registrations, Set<String> detachedCategoryKeys,
                              SourceAccess access) throws IOException {
        Objects.requireNonNull(registrations);
        Objects.requireNonNull(access);
        Set<String> categories = Set.copyOf(detachedCategoryKeys);
        List<ResourceLocation> ordered = snapshotRegistrations(registrations);
        Map<String, String> rawSources = new LinkedHashMap<>();
        int totalBytes = 0;
        for (ResourceLocation registration : ordered) {
            ResourceLocation physical = physicalLocation(registration);
            byte[] bytes;
            try (InputStream stream = access.open(physical)) {
                if (stream == null) throw new IOException("Registered source is missing");
                bytes = readBounded(stream, MAX_UTF8_BYTES - totalBytes);
            } catch (IOException failure) {
                throw new SourceException(registration, physical, "Unable to read whole registered source: " + failure.getMessage(), failure);
            }
            String raw;
            try {
                raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (IOException malformedUtf8) {
                throw new SourceException(registration, physical, "Invalid strict UTF-8", malformedUtf8);
            }
            totalBytes += bytes.length;
            rawSources.put(registration.toString(), raw);
        }
        // No candidate is returned until EVERY source was fully read, closed, decoded and parsed.
        ResearchCatalogSpec spec = ResearchCatalogParser.parse(rawSources, categories);
        return new Loaded(spec, ordered, totalBytes);
    }

    /** Exact registration identity is not normalized/deduplicated by this physical fetch path. */
    public static ResourceLocation physicalLocation(ResourceLocation registration) {
        Objects.requireNonNull(registration);
        String path = registration.getPath();
        return path.endsWith(".json") ? registration : registration.withPath(path + ".json");
    }

    private static List<ResourceLocation> snapshotRegistrations(Collection<ResourceLocation> input) {
        if (input.size() > MAX_INPUT_REGISTRATIONS) throw new IllegalArgumentException("Too many raw research registrations");
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        int examined = 0;
        for (ResourceLocation registration : input) {
            if (++examined > MAX_INPUT_REGISTRATIONS) throw new IllegalArgumentException("Too many raw research registrations");
            Objects.requireNonNull(registration, "Null research registration");
            // Valid modern ResourceLocation characters are ASCII: no oversized temporary byte array.
            if (registration.toString().length() > MAX_REGISTRATION_ID_UTF8_BYTES) {
                throw new IllegalArgumentException("Research registration ID exceeds 1024 UTF-8 bytes");
            }
            unique.add(registration);
            if (unique.size() > MAX_REGISTRATIONS) throw new IllegalArgumentException("Too many unique research registrations");
        }
        return List.copyOf(unique);
    }

    private static byte[] readBounded(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        while (true) {
            // Read exactly one byte beyond the remaining budget to distinguish EOF from overflow.
            int read = stream.read(buffer, 0, Math.min(buffer.length, limit - bytes.size() + 1));
            if (read < 0) return bytes.toByteArray();
            if (read == 0) throw new IOException("Resource stream made no progress");
            if (read > limit - bytes.size()) throw new IOException("Combined original UTF-8 sources exceed 512 KiB");
            bytes.write(buffer, 0, read);
        }
    }
}
