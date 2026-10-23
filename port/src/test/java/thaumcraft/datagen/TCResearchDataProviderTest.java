package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thaumcraft.common.config.ConfigResearch;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceLoader;
import thaumcraft.datagen.TCResearchDataProvider.SourceIdentity;

/** Bootstrap-free validation/cache seam; all test writes are confined to TempDir. */
class TCResearchDataProviderTest {
    @TempDir Path directory;

    @Test
    void canonicalManifestIsImmutableFixedEightExecutedIdentitiesAnd106387Bytes() {
        assertEquals(8, TCResearchDataProvider.CANONICAL_DEFAULTS.size());
        assertEquals(106387, TCResearchDataProvider.CANONICAL_DEFAULTS.stream().mapToInt(SourceIdentity::byteCount).sum());
        assertEquals(List.of("alchemy", "eldritch", "basics", "auromancy", "scans", "artifice", "infusion", "golemancy"),
            TCResearchDataProvider.CANONICAL_DEFAULTS.stream().map(value -> value.registration().getPath().substring("research/".length())).toList());
        assertThrows(UnsupportedOperationException.class, () -> TCResearchDataProvider.CANONICAL_DEFAULTS.clear());
        assertThrows(IllegalArgumentException.class, () -> new SourceIdentity(ConfigResearch.BUILTIN_RESEARCH_LOCATIONS.getFirst(), 1, "bad"));
    }

    @Test
    void preparationPreservesExactCrLfUtf8BytesAndRawTextWithoutWritingAnyOutput() throws Exception {
        Fixture fixture = fixture();
        Path privateSource = writePrivate(fixture);
        Path outputRoot = directory.resolve("not-created/data");
        var prepared = TCResearchDataProvider.prepareWhole(privateSource, outputRoot, fixture.manifest());
        assertFalse(Files.exists(outputRoot));
        assertEquals(8, prepared.sources().size());
        for (var source : prepared.sources()) {
            byte[] original = fixture.bytes().get(source.registration());
            assertArrayEquals(original, source.bytes());
            assertEquals(new String(original, StandardCharsets.UTF_8), prepared.spec().sources().get(source.registration().toString()));
        }
        byte[] defensive = prepared.sources().getFirst().bytes();
        defensive[0] = '!';
        assertArrayEquals(fixture.bytes().get(prepared.sources().getFirst().registration()), prepared.sources().getFirst().bytes());
        assertThrows(UnsupportedOperationException.class, () -> prepared.sources().clear());
    }

    @Test
    void explicitImportEnqueuesAllEightExactBytesWithStandardHashCacheSha1AfterValidation() throws Exception {
        Fixture fixture = fixture();
        Path source = writePrivate(fixture);
        Path output = directory.resolve("generated");
        CacheSpy cache = new CacheSpy();
        new TCResearchDataProvider(new PackOutput(output), source, fixture.manifest()).run(cache).join();
        assertEquals(8, cache.writes.size());
        for (SourceIdentity identity : fixture.manifest()) {
            Path destination = generated(output, identity.registration());
            assertArrayEquals(fixture.bytes().get(identity.registration()), cache.writes.get(destination));
            assertEquals(Hashing.sha1().hashBytes(fixture.bytes().get(identity.registration())), cache.hashes.get(destination));
        }
        assertFalse(Files.exists(output)); // Spy enrollment only; no actual project/HashCache runner.
    }

    @Test
    void ordinaryDatagenValidatesExistingDefaultsAndEnrollsAllEightWithoutPrivateSource() throws Exception {
        Fixture fixture = fixture();
        Path output = directory.resolve("generated");
        for (var identity : fixture.manifest()) write(generated(output, identity.registration()), fixture.bytes().get(identity.registration()));
        CacheSpy cache = new CacheSpy();
        new TCResearchDataProvider(new PackOutput(output), null, fixture.manifest()).run(cache).join();
        assertEquals(8, cache.writes.size());
        for (var identity : fixture.manifest()) assertArrayEquals(fixture.bytes().get(identity.registration()), Files.readAllBytes(generated(output, identity.registration())));
        assertFalse(Files.exists(directory.resolve("private-source")));
    }

    @Test
    void allMissingAndPartialExistingOutputFailBeforeAnyCacheEnrollmentWithExplicitImportMessage() throws Exception {
        Fixture fixture = fixture();
        Path output = directory.resolve("generated");
        CacheSpy cache = new CacheSpy();
        CompletionException missing = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(output), null, fixture.manifest()).run(cache).join());
        assertTrue(missing.getCause().getMessage().contains("All eight"));
        assertTrue(missing.getCause().getMessage().contains("explicit private first import"));
        assertTrue(cache.writes.isEmpty());
        var first = fixture.manifest().getFirst();
        write(generated(output, first.registration()), fixture.bytes().get(first.registration()));
        CompletionException partial = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(output), null, fixture.manifest()).run(cache).join());
        assertTrue(partial.getCause().getMessage().contains("Missing/unreadable"));
        assertTrue(cache.writes.isEmpty());
        assertEquals(1, countRegularFiles(output));
    }

    @Test
    void lastMissingOrDigestMismatchRejectsWholeImportBeforeAnyOutputWrite() throws Exception {
        Fixture fixture = fixture();
        Path source = writePrivate(fixture);
        Path output = directory.resolve("generated");
        CacheSpy cache = new CacheSpy();
        var last = fixture.manifest().getLast();
        Path lastFile = source.resolve(fileName(last.registration()));
        Files.delete(lastFile); // Only this test's temporary fixture.
        assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(output), source, fixture.manifest()).run(cache).join());
        assertTrue(cache.writes.isEmpty());
        write(lastFile, fixture.bytes().get(last.registration()));
        byte[] changed = fixture.bytes().get(last.registration()).clone();
        changed[0] = '\t'; // Same byte-count but wrong SHA-256, even though valid whitespace.
        Files.write(lastFile, changed);
        CompletionException mismatch = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(output), source, fixture.manifest()).run(cache).join());
        assertTrue(mismatch.getCause().getMessage().contains("SHA256/byte-count mismatch"));
        assertTrue(cache.writes.isEmpty());
        assertFalse(Files.exists(output));
    }

    @Test
    void malformedLastJsonOrInvalidUtf8RejectsWholeCandidateEvenWithMatchingDigest() throws Exception {
        Fixture fixture = fixture();
        ResourceLocation last = fixture.manifest().getLast().registration();
        for (byte[] bad : List.of("{\"entries\":TRUE}".getBytes(StandardCharsets.UTF_8), new byte[]{(byte)0xc3, 0x28})) {
            Fixture changed = changed(fixture, last, bad);
            Path source = writePrivate(changed);
            CacheSpy cache = new CacheSpy();
            CompletionException failure = assertThrows(CompletionException.class,
                () -> new TCResearchDataProvider(new PackOutput(directory.resolve("generated")), source, changed.manifest()).run(cache).join());
            assertTrue(failure.getCause() instanceof ResearchCatalogParser.CatalogException
                || failure.getCause() instanceof ResearchCatalogResourceLoader.SourceException);
            assertTrue(cache.writes.isEmpty());
        }
    }

    @Test
    void duplicateCrossFileResearchKeyRejectsWholeEightFileCandidateRatherThanPartialCatalog() throws Exception {
        Fixture fixture = fixture();
        byte[] entry = "{\"entries\":[{\"key\":\"OPAQUE@2\",\"category\":\"BASICS\",\"name\":\"n\"}]}".getBytes(StandardCharsets.UTF_8);
        Fixture changed = changed(changed(fixture, fixture.manifest().getFirst().registration(), entry),
            fixture.manifest().getLast().registration(), entry);
        CacheSpy cache = new CacheSpy();
        Path source = writePrivate(changed);
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(directory.resolve("generated")), source, changed.manifest()).run(cache).join());
        assertTrue(failure.getCause() instanceof ResearchCatalogParser.CatalogException);
        assertTrue(cache.writes.isEmpty());
    }

    @Test
    void unreviewedButWellFormedPrivateDataCannotReplaceCanonicalDefaults() throws Exception {
        Fixture fixture = fixture();
        Path source = writePrivate(fixture);
        CacheSpy cache = new CacheSpy();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(directory.resolve("generated")), source).run(cache).join());
        assertTrue(failure.getCause().getMessage().contains("SHA256/byte-count mismatch"));
        assertTrue(cache.writes.isEmpty());
    }

    @Test
    void sourceMustBeExplicitExistingDirectoryAndManifestMustBeExactlyEightBuiltinRegistrations() {
        Fixture fixture = fixture();
        CacheSpy cache = new CacheSpy();
        assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(directory.resolve("generated")), directory.resolve("missing-source"), fixture.manifest()).run(cache).join());
        assertTrue(cache.writes.isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> new TCResearchDataProvider(new PackOutput(directory), null, fixture.manifest().subList(0, 7)));
        List<SourceIdentity> duplicate = new ArrayList<>(fixture.manifest());
        duplicate.set(7, duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> new TCResearchDataProvider(new PackOutput(directory), null, duplicate));
    }

    @Test
    void combinedRawByteBoundFailsBeforeCacheEnrollmentEvenWithMatchingLargeFileIdentities() throws Exception {
        Fixture fixture = fixture();
        String empty = "{\"entries\":[]}";
        byte[] large = (empty + " ".repeat(300_000 - empty.length())).getBytes(StandardCharsets.UTF_8);
        Fixture changed = changed(changed(fixture, fixture.manifest().getFirst().registration(), large),
            fixture.manifest().getLast().registration(), large);
        Path source = writePrivate(changed);
        CacheSpy cache = new CacheSpy();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(directory.resolve("generated")), source, changed.manifest()).run(cache).join());
        assertTrue(failure.getCause().getMessage().contains("exceed 512 KiB"));
        assertTrue(cache.writes.isEmpty());
        assertFalse(Files.exists(directory.resolve("generated")));
    }

    @Test
    void detachedBuiltinCategoryDefinitionsRejectUnknownCategoryBeforeCacheEnrollment() throws Exception {
        Fixture fixture = fixture();
        byte[] unknownCategory = "{\"entries\":[{\"key\":\"KEY\",\"category\":\"ADDON_NOT_BUILTIN\",\"name\":\"n\"}]}".getBytes(StandardCharsets.UTF_8);
        Fixture changed = changed(fixture, fixture.manifest().getLast().registration(), unknownCategory);
        Path source = writePrivate(changed);
        CacheSpy cache = new CacheSpy();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(directory.resolve("generated")), source, changed.manifest()).run(cache).join());
        assertTrue(failure.getCause() instanceof ResearchCatalogParser.CatalogException);
        assertTrue(cache.writes.isEmpty());
    }

    @Test
    void linkedDestinationDirectoryRejectsAllEightBeforeCacheEnrollmentAndPreservesExternalSentinel() throws Exception {
        Fixture fixture = fixture();
        Path source = writePrivate(fixture);
        Path output = directory.resolve("generated");
        Path external = directory.resolve("external-directory");
        byte[] sentinel = "external directory sentinel".getBytes(StandardCharsets.UTF_8);
        write(external.resolve("alchemy.json"), sentinel);
        Path research = output.resolve("data/thaumcraft/research");
        Files.createDirectories(research.getParent());
        linkIfSupported(research, external);
        assertWindowsReparseFlagIfWindows(research);
        CacheSpy cache = new CacheSpy();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(output), source, fixture.manifest()).run(cache).join());
        assertTrue(failure.getCause().getMessage().contains("Linked/reparse"));
        assertTrue(cache.writes.isEmpty());
        assertArrayEquals(sentinel, Files.readAllBytes(external.resolve("alchemy.json")));
        assertEquals(1, countRegularFiles(external));
    }

    @Test
    void linkedLastDestinationFileRejectsEarlierSevenCacheEnrollmentsAndPreservesExternalSentinel() throws Exception {
        assumeDirectorySymbolicLinksSupported();
        Fixture fixture = fixture();
        Path source = writePrivate(fixture);
        Path output = directory.resolve("generated");
        Path external = directory.resolve("external-file.json");
        byte[] sentinel = "external file sentinel".getBytes(StandardCharsets.UTF_8);
        write(external, sentinel);
        Path last = generated(output, fixture.manifest().getLast().registration());
        Files.createDirectories(last.getParent());
        linkIfSupported(last, external);
        assertWindowsReparseFlagIfWindows(last);
        CacheSpy cache = new CacheSpy();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchDataProvider(new PackOutput(output), source, fixture.manifest()).run(cache).join());
        assertTrue(failure.getCause().getMessage().contains("Linked/reparse"));
        assertTrue(cache.writes.isEmpty());
        assertArrayEquals(sentinel, Files.readAllBytes(external));
    }

    @Test
    void optionalPrivateExecutedDefaultsValidateWithoutCopyingOrWritingGeneratedResources() throws Exception {
        String requested = System.getProperty("thaumcraft.researchSourceDirectory");
        Assumptions.assumeTrue(requested != null && !requested.isBlank(), "Optional explicit original research source directory");
        var prepared = TCResearchDataProvider.prepareWhole(Path.of(requested), directory.resolve("unused-data"), TCResearchDataProvider.CANONICAL_DEFAULTS);
        assertEquals(8, prepared.sources().size());
        assertEquals(106387, prepared.sources().stream().mapToInt(value -> value.bytes().length).sum());
        assertEquals(148, prepared.spec().entries().size());
        assertEquals(271, prepared.spec().entries().stream().mapToInt(value -> value.stages().values().size()).sum());
        assertEquals(16, prepared.spec().entries().stream().mapToInt(value -> value.addenda().values().size()).sum());
        assertFalse(Files.exists(directory.resolve("unused-data")));
    }

    private Fixture fixture() {
        Map<ResourceLocation, byte[]> bytes = new LinkedHashMap<>();
        for (SourceIdentity identity : TCResearchDataProvider.CANONICAL_DEFAULTS) {
            bytes.put(identity.registration(), (" \r\n{\"entries\":[],\"note\":\"Ж " + identity.registration() + "\"}\r\n\t").getBytes(StandardCharsets.UTF_8));
        }
        return withIdentities(bytes);
    }

    private Fixture changed(Fixture fixture, ResourceLocation location, byte[] value) {
        Map<ResourceLocation, byte[]> bytes = new LinkedHashMap<>(fixture.bytes());
        bytes.put(location, value);
        return withIdentities(bytes);
    }

    private Fixture withIdentities(Map<ResourceLocation, byte[]> bytes) {
        return new Fixture(bytes, bytes.entrySet().stream()
            .map(entry -> new SourceIdentity(entry.getKey(), entry.getValue().length, Hashing.sha256().hashBytes(entry.getValue()).toString())).toList());
    }

    private Path writePrivate(Fixture fixture) throws IOException {
        Path source = directory.resolve("private-source");
        for (var entry : fixture.bytes().entrySet()) write(source.resolve(fileName(entry.getKey())), entry.getValue());
        return source;
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static Path generated(Path output, ResourceLocation registration) {
        ResourceLocation physical = ResearchCatalogResourceLoader.physicalLocation(registration);
        return output.resolve("data").resolve(physical.getNamespace()).resolve(physical.getPath());
    }

    private static String fileName(ResourceLocation registration) {
        String path = ResearchCatalogResourceLoader.physicalLocation(registration).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static long countRegularFiles(Path root) throws IOException {
        try (var files = Files.walk(root)) { return files.filter(Files::isRegularFile).count(); }
    }

    private static void linkIfSupported(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symlink creation unavailable: " + unavailable);
        }
    }

    private void assumeDirectorySymbolicLinksSupported() throws IOException {
        // Windows can leave an undeletable leaf after a denied FILE symlink creation. Probe the
        // same symlink privilege with a directory first; the observed denied-directory path is
        // safely handled by normal TempDir cleanup. Never attempt the hazardous file branch when
        // this capability is unavailable, and keep the real file-link regression when it exists.
        Path probe = Files.createDirectory(directory.resolve("symbolic-link-capability-probe"));
        Path target = Files.createDirectory(probe.resolve("target"));
        Path link = probe.resolve("directory-link");
        linkIfSupported(link, target);
        assertTrue(Files.isSymbolicLink(link));
        Files.delete(link); // Delete only the test-created link, never its target recursively.
        Files.delete(target);
        Files.delete(probe);
    }

    private static void assertWindowsReparseFlagIfWindows(Path path) throws IOException {
        if (path.getFileSystem().getSeparator().equals("\\")) {
            Object raw = Files.getAttribute(path, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
            assertTrue(raw instanceof Number);
            assertEquals(0x400, ((Number)raw).intValue() & 0x400); // Actual Windows reparse flag, not a spoofed Boolean.
        }
    }

    private record Fixture(Map<ResourceLocation, byte[]> bytes, List<SourceIdentity> manifest) {}

    private static final class CacheSpy implements CachedOutput {
        private final Map<Path, byte[]> writes = new LinkedHashMap<>();
        private final Map<Path, HashCode> hashes = new LinkedHashMap<>();
        @Override public void writeIfNeeded(Path path, byte[] data, HashCode hash) {
            if (writes.put(path, data.clone()) != null) throw new AssertionError("Duplicate enrollment");
            hashes.put(path, hash);
        }
    }
}
