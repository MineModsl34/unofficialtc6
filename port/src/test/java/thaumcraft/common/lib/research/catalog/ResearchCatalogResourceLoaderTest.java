package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceLoader.Loaded;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceLoader.SourceException;

class ResearchCatalogResourceLoaderTest {
    private static final Set<String> CATEGORIES = Set.of("BASICS", "Addon Mixed");
    private static final String EMPTY = "{\"entries\":[]}";
    private static final ResourceLocation FIRST = id("thaumcraft", "research/first");
    private static final ResourceLocation SECOND = id("addon", "custom/second.json");

    @Test
    void preservesExactUtf8RawTextOrderedRegistrationLabelsAndClosesAllStreams() throws Exception {
        String firstRaw = " \r\n" + EMPTY + "\r\n\t";
        String secondRaw = "{\"entries\":[],\"note\":\"Я\"}\n";
        ClosingStream first = stream(firstRaw);
        ClosingStream second = stream(secondRaw);
        List<ResourceLocation> opened = new ArrayList<>();
        List<ResourceLocation> registrations = new ArrayList<>(List.of(FIRST, SECOND, FIRST));
        Loaded loaded = ResearchCatalogResourceLoader.load(registrations, CATEGORIES, physical -> {
            opened.add(physical);
            return physical.equals(id("thaumcraft", "research/first.json")) ? first : second;
        });
        assertEquals(List.of(FIRST, SECOND), loaded.registrations());
        assertEquals(List.of(id("thaumcraft", "research/first.json"), SECOND), opened);
        assertEquals(List.of(FIRST.toString(), SECOND.toString()), List.copyOf(loaded.spec().sources().keySet()));
        assertEquals(firstRaw, loaded.spec().sources().get(FIRST.toString()));
        assertEquals(secondRaw, loaded.spec().sources().get(SECOND.toString()));
        assertEquals(firstRaw.getBytes(StandardCharsets.UTF_8).length + secondRaw.getBytes(StandardCharsets.UTF_8).length, loaded.utf8Bytes());
        assertTrue(first.closed);
        assertTrue(second.closed);
        registrations.clear();
        assertEquals(2, loaded.registrations().size());
        assertThrows(UnsupportedOperationException.class, () -> loaded.registrations().clear());
        assertThrows(UnsupportedOperationException.class, () -> loaded.spec().sources().clear());
    }

    @Test
    void suffixIsConditionalButExactRegistrationAliasesStayDistinctAndCustomPathsAllowed() throws Exception {
        ResourceLocation noSuffix = id("addon", "custom/catalog");
        ResourceLocation withSuffix = id("addon", "custom/catalog.json");
        List<ResourceLocation> opened = new ArrayList<>();
        Loaded loaded = ResearchCatalogResourceLoader.load(List.of(noSuffix, withSuffix, noSuffix), CATEGORIES, physical -> {
            opened.add(physical);
            return stream(EMPTY);
        });
        assertEquals(List.of(withSuffix, withSuffix), opened);
        assertEquals(List.of(noSuffix.toString(), withSuffix.toString()), List.copyOf(loaded.spec().sources().keySet()));
        assertEquals(withSuffix, ResearchCatalogResourceLoader.physicalLocation(withSuffix));
        assertEquals(withSuffix, ResearchCatalogResourceLoader.physicalLocation(noSuffix));
    }

    @Test
    void aliasesReadingSameNonemptyCatalogRejectWholeDuplicateKeyRatherThanSilentlySkip() {
        ResourceLocation a = id("addon", "outside_research/catalog");
        ResourceLocation b = id("addon", "outside_research/catalog.json");
        assertThrows(ResearchCatalogParser.CatalogException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(a, b), CATEGORIES, physical -> stream(entry("OPAQUE@2", "BASICS"))));
    }

    @Test
    void detachedCategoryKeysAreExplicitAndResearchKeysRemainOpaque() throws Exception {
        Loaded loaded = ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES,
            physical -> stream(entry("!MiXeD@2", "Addon Mixed")));
        assertEquals("!MiXeD@2", loaded.spec().entries().getFirst().key());
        assertEquals("Addon Mixed", loaded.spec().entries().getFirst().category());
        assertThrows(ResearchCatalogParser.CatalogException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), Set.of("BASICS"),
                physical -> stream(entry("!MiXeD@2", "Addon Mixed"))));
    }

    @Test
    void missingRegisteredSourceRejectsWholeCandidateWithSourceIdentityAndNoFallback() {
        ClosingStream first = stream(EMPTY);
        AtomicInteger opens = new AtomicInteger();
        SourceException error = assertThrows(SourceException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST, SECOND), CATEGORIES, physical -> {
                opens.incrementAndGet();
                return physical.equals(SECOND) ? null : first;
            }));
        assertEquals(2, opens.get());
        assertTrue(first.closed);
        assertEquals(SECOND, error.registration());
        assertEquals(SECOND, error.physicalLocation());
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void streamOpenReadCloseFailuresAreNotConvertedToPartialOrEmptyCatalog() {
        assertThrows(SourceException.class, () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES,
            physical -> { throw new FileNotFoundException("missing winning resource"); }));
        InputStream readFailure = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failure"); }
        };
        assertThrows(SourceException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> readFailure));
        ClosingStream closeFailure = new ClosingStream(EMPTY.getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() throws IOException { super.close(); throw new IOException("close failure"); }
        };
        assertThrows(SourceException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> closeFailure));
        assertTrue(closeFailure.closed);
        assertThrows(ResearchCatalogParser.CatalogException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> stream("")));
    }

    @Test
    void strictUtf8RejectsMalformedAndTruncatedSequencesInsteadOfReplacementCharacters() {
        for (byte[] malformed : List.of(new byte[]{(byte)0xc3, 0x28}, new byte[]{(byte)0xe2, (byte)0x82},
            new byte[]{(byte)0xc0, (byte)0xaf}, new byte[]{(byte)0xed, (byte)0xa0, (byte)0x80})) {
            ClosingStream resource = new ClosingStream(malformed);
            SourceException failure = assertThrows(SourceException.class,
                () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> resource));
            assertTrue(failure.getMessage().contains("strict UTF-8"));
            assertTrue(resource.closed);
        }
    }

    @Test
    void exactCombinedUtf8BudgetIsAcceptedButOneExtraByteStopsBoundedReadAndCloses() throws Exception {
        int budget = ResearchCatalogResourceLoader.MAX_UTF8_BYTES;
        String exact = EMPTY + " ".repeat(budget - EMPTY.length());
        Loaded loaded = ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> stream(exact));
        assertEquals(budget, loaded.utf8Bytes());
        assertEquals(exact, loaded.spec().sources().get(FIRST.toString()));
        ClosingStream extra = stream(exact + " ".repeat(100_000));
        assertThrows(SourceException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> extra));
        assertEquals(budget + 1, extra.bytesRead);
        assertTrue(extra.closed);
    }

    @Test
    void combinedBudgetCountsUtf8BytesAcrossAllSourcesNotCharactersOrPerFileOnly() {
        int firstBytes = ResearchCatalogResourceLoader.MAX_UTF8_BYTES - EMPTY.getBytes(StandardCharsets.UTF_8).length;
        ClosingStream first = stream(EMPTY + " ".repeat(firstBytes - EMPTY.length()));
        String unicodeSecond = "{\"entries\":[],\"note\":\"Я\"}";
        ClosingStream second = stream(unicodeSecond);
        assertThrows(SourceException.class, () -> ResearchCatalogResourceLoader.load(List.of(FIRST, SECOND), CATEGORIES,
            physical -> physical.equals(SECOND) ? second : first));
        assertEquals(EMPTY.getBytes(StandardCharsets.UTF_8).length + 1, second.bytesRead);
        assertTrue(first.closed);
        assertTrue(second.closed);
    }

    @Test
    void nonprogressAndInfiniteStreamsFailWithFiniteReadsAndClose() {
        AtomicInteger bytesRead = new AtomicInteger();
        boolean[] closed = {false};
        InputStream infinite = new InputStream() {
            @Override public int read() { bytesRead.incrementAndGet(); return ' '; }
            @Override public int read(byte[] target, int offset, int length) {
                Arrays.fill(target, offset, offset + length, (byte)' ');
                bytesRead.addAndGet(length);
                return length;
            }
            @Override public void close() { closed[0] = true; }
        };
        assertThrows(SourceException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> infinite));
        assertEquals(ResearchCatalogResourceLoader.MAX_UTF8_BYTES + 1, bytesRead.get());
        assertTrue(closed[0]);
        ClosingStream stalled = new ClosingStream(new byte[1]) {
            @Override public int read(byte[] target, int offset, int length) { return 0; }
        };
        assertThrows(SourceException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES, physical -> stalled));
        assertTrue(stalled.closed);
    }

    @Test
    void registrationBudgetChecksBeforeIoAndExactDuplicatesAreFirstSeenIdempotent() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        List<ResourceLocation> unique = new ArrayList<>();
        for (int i = 0; i < 65; i++) unique.add(id("addon", "custom/" + i));
        assertThrows(IllegalArgumentException.class, () -> ResearchCatalogResourceLoader.load(unique, CATEGORIES,
            physical -> { opens.incrementAndGet(); return stream(EMPTY); }));
        assertEquals(0, opens.get());
        assertThrows(IllegalArgumentException.class, () -> ResearchCatalogResourceLoader.load(Collections.nCopies(4097, FIRST), CATEGORIES,
            physical -> { opens.incrementAndGet(); return stream(EMPTY); }));
        assertEquals(0, opens.get());
        Loaded loaded = ResearchCatalogResourceLoader.load(Collections.nCopies(4096, FIRST), CATEGORIES,
            physical -> { opens.incrementAndGet(); return stream(EMPTY); });
        assertEquals(1, opens.get());
        assertEquals(List.of(FIRST), loaded.registrations());
        Loaded full = ResearchCatalogResourceLoader.load(unique.subList(0, 64), CATEGORIES, physical -> stream(EMPTY));
        assertEquals(64, full.registrations().size());
    }

    @Test
    void registrationIdWireBudgetChecksEntireCandidateBeforeAnyIo() throws Exception {
        int limit = ResearchCatalogResourceLoader.MAX_REGISTRATION_ID_UTF8_BYTES;
        ResourceLocation exact = id("addon", "a".repeat(limit - "addon:".length()));
        assertEquals(limit, exact.toString().getBytes(StandardCharsets.UTF_8).length);
        AtomicInteger opens = new AtomicInteger();
        Loaded accepted = ResearchCatalogResourceLoader.load(List.of(exact), CATEGORIES,
            physical -> { opens.incrementAndGet(); return stream(EMPTY); });
        assertEquals(List.of(exact), accepted.registrations());
        assertEquals(1, opens.get());
        ResourceLocation tooLong = id("addon", exact.getPath() + "a");
        opens.set(0);
        assertThrows(IllegalArgumentException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST, tooLong), CATEGORIES,
                physical -> { opens.incrementAndGet(); return stream(EMPTY); }));
        assertEquals(0, opens.get());
    }

    @Test
    void resourceManagerAdapterUsesSingleSelectedWinnerNeverStackAssetsOrLowerPriorityRetry() throws Exception {
        AtomicInteger winnerOpens = new AtomicInteger();
        List<ResourceLocation> lookups = new ArrayList<>();
        Resource winner = new Resource(null, () -> {
            winnerOpens.incrementAndGet();
            return stream(EMPTY);
        }); // Resource.open does not inspect PackResources; manager selection is the test seam.
        ResourceManager manager = selectedManager(lookups, winner);
        Loaded loaded = ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES,
            new ResearchCatalogResourceManagerSource(manager));
        assertEquals(List.of(id("thaumcraft", "research/first.json")), lookups);
        assertEquals(1, winnerOpens.get());
        assertEquals(1, loaded.registrations().size());
        Resource malformedWinner = new Resource(null, () -> stream("BROKEN"));
        assertThrows(ResearchCatalogParser.CatalogException.class,
            () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES,
                new ResearchCatalogResourceManagerSource(selectedManager(new ArrayList<>(), malformedWinner))));
        assertThrows(SourceException.class, () -> ResearchCatalogResourceLoader.load(List.of(FIRST), CATEGORIES,
            new ResearchCatalogResourceManagerSource(ResourceManager.Empty.INSTANCE)));
    }

    private static ResourceManager selectedManager(List<ResourceLocation> lookups, Resource winner) {
        return new ResourceManager() {
            @Override public Optional<Resource> getResource(ResourceLocation location) {
                lookups.add(location);
                return Optional.of(winner);
            }
            @Override public Set<String> getNamespaces() { throw new AssertionError("No namespace discovery"); }
            @Override public List<Resource> getResourceStack(ResourceLocation location) { throw new AssertionError("No stack/lower-priority fallback"); }
            @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) { throw new AssertionError("No resource discovery"); }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) { throw new AssertionError("No stack discovery"); }
            @Override public Stream<PackResources> listPacks() { throw new AssertionError("No pack/asset fallback"); }
        };
    }

    private static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    private static ClosingStream stream(String raw) { return new ClosingStream(raw.getBytes(StandardCharsets.UTF_8)); }
    private static String entry(String key, String category) {
        return "{\"entries\":[{\"key\":\"" + key + "\",\"category\":\"" + category + "\",\"name\":\"research.name\","
            + "\"stages\":[{\"text\":\"stage.text\"}]}]}";
    }

    private static class ClosingStream extends ByteArrayInputStream {
        boolean closed;
        int bytesRead;
        ClosingStream(byte[] bytes) { super(bytes); }
        @Override public synchronized int read(byte[] target, int offset, int length) {
            int read = super.read(target, offset, length);
            if (read > 0) bytesRead += read;
            return read;
        }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
