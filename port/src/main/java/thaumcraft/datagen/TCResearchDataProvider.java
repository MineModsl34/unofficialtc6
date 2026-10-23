package thaumcraft.datagen;

import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.config.ConfigResearch;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceLoader;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;

/**
 * Exact-byte import of the eight TC6 builtin research files into server datapack data.
 * Source is an explicitly supplied private assets/thaumcraft/research directory, NOT an implicit
 * classpath/filesystem fallback. After the first import ordinary datagen validates the existing
 * generated defaults and enrolls every output in HashCache without needing the private source.
 * Runtime datapack overrides are a separate loader concern, not replacements for mod defaults.
 */
public final class TCResearchDataProvider implements DataProvider {
    /**
     * Executed original-research-20261007-2224 sourceFilesInOriginalIterationOrder, not a reconstructed
     * HashMap order or the separate ConfigResearch registration order. Only identities/digests are
     * public here; the original report and JSON contents are not embedded in this provider.
     * Report SHA256: fca71bacf12712c6fb4e9eec34fe82dd3319ccb60ac0c22d007c1e42333454ea.
     */
    public static final List<SourceIdentity> CANONICAL_DEFAULTS = List.of(
        source("alchemy", 20066, "195b04d9dfa811c765cdcf9b17513bd3358ff942a3cd04df06173fbc876ba5d4"),
        source("eldritch", 3720, "122f8a547debd0559512d7c0e28a74a087f31a75f49119e9738e56f335fee83e"),
        source("basics", 15754, "22addef24e9b0d0e0f4b2570d2da2e4ee9393876a341f592cfc2f32586a9d1aa"),
        source("auromancy", 16239, "654e16833b2b8715af74adf3a26b5bc68d874f559c38a56b170628f8a75da604"),
        source("scans", 3854, "36bcb8c263effada0a9b746adf09b331f50dc818e93c8c7f7a7d5a29be5c34b5"),
        source("artifice", 14365, "615ac0c944aa19bf6bb3bf5f02158eb99577aa979bf0767dfd3aef0badc7834c"),
        source("infusion", 13746, "54d76698e0f842396e992fd9dec26a18b74f593cf765bcf6eb76211660e85913"),
        source("golemancy", 18643, "a574bee623f4597c6bf37de9e020413e1fb2b5f5bdba8c68fcb2cfa77f101268"));

    public record SourceIdentity(ResourceLocation registration, int byteCount, String sha256) {
        public SourceIdentity {
            Objects.requireNonNull(registration);
            Objects.requireNonNull(sha256);
            if (byteCount < 0 || byteCount > ResearchCatalogResourceLoader.MAX_UTF8_BYTES
                || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid builtin research source identity");
            }
        }
    }

    private final PackOutput output;
    private final Path sourceDirectory;
    private final List<SourceIdentity> expected;

    public TCResearchDataProvider(PackOutput output, Path sourceDirectory) {
        this(output, sourceDirectory, CANONICAL_DEFAULTS);
    }

    /** Package-private synthetic validation seam; production always uses the executed manifest. */
    TCResearchDataProvider(PackOutput output, Path sourceDirectory, List<SourceIdentity> expected) {
        this.output = Objects.requireNonNull(output);
        this.sourceDirectory = sourceDirectory;
        this.expected = validatedManifest(expected);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            // Use the SAME absolute normalized path for validation and writes, including on Unix.
            Path dataRoot = output.getOutputFolder(PackOutput.Target.DATA_PACK).toAbsolutePath().normalize();
            Prepared candidate = prepareWhole(sourceDirectory, dataRoot, expected);
            // Validation of EVERY source, digest, UTF-8 sequence and metadata shape has completed.
            // writeIfNeeded also records unchanged outputs in this provider's HashCache entry.
            for (PreparedSource source : candidate.sources()) {
                byte[] exactBytes = source.bytes();
                cache.writeIfNeeded(destination(dataRoot, source.physicalLocation()), exactBytes,
                    Hashing.sha1().hashBytes(exactBytes));
            }
            return CompletableFuture.completedFuture(null);
        } catch (IOException | RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public String getName() {
        return "Exact TC6 builtin research server data (validated private import)";
    }

    record PreparedSource(ResourceLocation registration, ResourceLocation physicalLocation, byte[] bytes) {
        PreparedSource {
            Objects.requireNonNull(registration);
            Objects.requireNonNull(physicalLocation);
            bytes = bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    record Prepared(ResearchCatalogSpec spec, List<PreparedSource> sources) {
        Prepared {
            Objects.requireNonNull(spec);
            sources = List.copyOf(sources);
        }
    }

    /** Pure preparation/validation stage: no directory creation, cache enrollment or output writes. */
    static Prepared prepareWhole(Path sourceDirectory, Path dataRoot, List<SourceIdentity> manifest) throws IOException {
        List<SourceIdentity> expected = validatedManifest(manifest);
        Objects.requireNonNull(dataRoot);
        validateDestinations(dataRoot, expected);
        Map<ResourceLocation, Path> paths = new LinkedHashMap<>();
        Path inputRoot = sourceDirectory == null ? dataRoot : sourceDirectory;
        if (sourceDirectory != null && !Files.isDirectory(sourceDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Explicit private research source must be an existing research directory: " + sourceDirectory);
        }
        for (SourceIdentity identity : expected) {
            ResourceLocation physical = ResearchCatalogResourceLoader.physicalLocation(identity.registration());
            String fileName = physical.getPath().substring(physical.getPath().lastIndexOf('/') + 1);
            paths.put(physical, sourceDirectory == null ? destination(dataRoot, physical) : sourceDirectory.resolve(fileName));
        }
        if (sourceDirectory == null && paths.values().stream().noneMatch(Files::exists)) {
            throw new IOException("All eight generated builtin research JSON files are absent; perform the explicit private first import "
                + "with -PtcResearchSourceDirectory pointing to assets/thaumcraft/research");
        }
        Path realRoot = inputRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(realRoot) || !Files.isDirectory(realRoot, LinkOption.NOFOLLOW_LINKS)
            || !realRoot.equals(realRoot.toRealPath())) {
            throw new IOException("Research input root must be a real unlinked directory: " + inputRoot);
        }
        Map<ResourceLocation, byte[]> rawBytes = new LinkedHashMap<>();
        int totalBytes = 0;
        for (SourceIdentity identity : expected) {
            ResourceLocation physical = ResearchCatalogResourceLoader.physicalLocation(identity.registration());
            Path file = paths.get(physical).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                throw new IOException("Missing/unreadable builtin research file " + physical + " at " + file
                    + (sourceDirectory == null ? "; rerun the explicit private first import, no fallback is used" : ""));
            }
            if (!file.toRealPath().startsWith(realRoot)) throw new IOException("Linked research input escapes its selected root: " + file);
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file)) {
                bytes = readBounded(input, ResearchCatalogResourceLoader.MAX_UTF8_BYTES - totalBytes);
            }
            totalBytes += bytes.length;
            if (bytes.length != identity.byteCount() || !Hashing.sha256().hashBytes(bytes).toString().equals(identity.sha256())) {
                throw new IOException("Builtin research source SHA256/byte-count mismatch: " + identity.registration()
                    + "; expected executed TC6 defaults, not reformatted or overridden data");
            }
            rawBytes.put(physical, bytes);
        }
        var loaded = ResearchCatalogResourceLoader.load(expected.stream().map(SourceIdentity::registration).toList(),
            ResearchCategoryDefinitions.newBuiltinCategories().keySet(), physical -> new ByteArrayInputStream(rawBytes.get(physical)));
        List<PreparedSource> sources = new ArrayList<>();
        for (SourceIdentity identity : expected) {
            ResourceLocation physical = ResearchCatalogResourceLoader.physicalLocation(identity.registration());
            sources.add(new PreparedSource(identity.registration(), physical, rawBytes.get(physical)));
        }
        return new Prepared(loaded.spec(), sources);
    }

    private static List<SourceIdentity> validatedManifest(List<SourceIdentity> manifest) {
        List<SourceIdentity> copy = List.copyOf(manifest);
        var keys = new HashSet<ResourceLocation>();
        for (SourceIdentity identity : copy) {
            if (!keys.add(identity.registration())) throw new IllegalArgumentException("Duplicate builtin research manifest registration");
        }
        if (copy.size() != 8 || !keys.equals(new HashSet<>(ConfigResearch.BUILTIN_RESEARCH_LOCATIONS))) {
            throw new IllegalArgumentException("Expected exactly the eight fixed builtin research registrations");
        }
        return copy;
    }

    private static Path destination(Path dataRoot, ResourceLocation physical) {
        return dataRoot.resolve(physical.getNamespace()).resolve(physical.getPath());
    }

    /**
     * Check ALL eight paths before the first cache enrollment. CachedOutput/HashCache use ordinary
     * Files.write and follow links, so input validation alone cannot protect the generated output.
     * This is a preflight for an ordinary single-writer datagen run, not an external filesystem-race
     * lock. No output directory is created here.
     */
    private static void validateDestinations(Path dataRoot, List<SourceIdentity> expected) throws IOException {
        Path root = dataRoot.toAbsolutePath().normalize();
        for (SourceIdentity identity : expected) {
            Path file = destination(root, ResearchCatalogResourceLoader.physicalLocation(identity.registration())).normalize();
            if (!file.startsWith(root) || file.equals(root)) throw new IOException("Research output escapes its selected data root: " + file);
            for (Path component = file; component != null; component = component.getParent()) {
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(component, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (NoSuchFileException absent) {
                    continue; // Future directory/file: inspect every existing ancestor, without creating it.
                }
                // Windows junctions are NOT isSymbolicLink. JDK WindowsFileAttributes exposes
                // reparse/device objects as isOther, and Windows DOS view exposes the raw bitmask.
                boolean windowsReparse = false;
                if (component.getFileSystem().getSeparator().equals("\\")) {
                    Object raw = Files.getAttribute(component, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
                    if (!(raw instanceof Number flags)) throw new IOException("Missing Windows DOS attributes for output preflight: " + component);
                    windowsReparse = (flags.intValue() & 0x400) != 0; // FILE_ATTRIBUTE_REPARSE_POINT
                }
                if (attributes.isSymbolicLink() || attributes.isOther() || windowsReparse) {
                    throw new IOException("Linked/reparse research output path is forbidden: " + component);
                }
                if (component.equals(file) ? !attributes.isRegularFile() : !attributes.isDirectory()) {
                    throw new IOException("Unexpected research output path type: " + component);
                }
                if (!component.toRealPath().equals(component)) throw new IOException("Linked/reparse research output path resolves through an ancestor: " + component);
            }
        }
    }

    private static byte[] readBounded(InputStream input, int remaining) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(remaining, 8192));
        byte[] buffer = new byte[8192];
        while (true) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining - bytes.size() + 1));
            if (count < 0) return bytes.toByteArray();
            if (count == 0) throw new IOException("Research file stream made no progress");
            if (count > remaining - bytes.size()) throw new IOException("Combined builtin research files exceed 512 KiB");
            bytes.write(buffer, 0, count);
        }
    }

    private static SourceIdentity source(String name, int byteCount, String hash) {
        return new SourceIdentity(ResourceLocation.fromNamespaceAndPath("thaumcraft", "research/" + name), byteCount, hash);
    }
}
