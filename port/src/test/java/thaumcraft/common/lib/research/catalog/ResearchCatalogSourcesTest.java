package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSources.RegistrationSnapshot;

class ResearchCatalogSourcesTest {
    @Test
    void snapshotCopiesOrderedRegistrationValuesAndCategoryKeys() {
        var registrations = new ArrayList<>(List.of(id("addon:last"), id("thaumcraft:first")));
        var categories = new LinkedHashSet<>(List.of("BASICS", "ADDON"));
        var snapshot = new RegistrationSnapshot(registrations, categories);
        registrations.clear(); categories.clear();
        assertEquals(List.of(id("addon:last"), id("thaumcraft:first")), snapshot.registrations());
        assertEquals(List.of("BASICS", "ADDON"), List.copyOf(snapshot.categoryKeys()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.registrations().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.categoryKeys().clear());
    }

    @Test
    void physicalSuffixIsConditionalAndRegistrationIdentityUnchanged() {
        var bare = id("addon:custom/nested");
        var suffixed = id("addon:custom/nested.json");
        assertEquals(suffixed, ResearchCatalogSources.physicalLocation(bare));
        assertSame(suffixed, ResearchCatalogSources.physicalLocation(suffixed));
        assertEquals(id("addon:custom.json.backup.json"), ResearchCatalogSources.physicalLocation(id("addon:custom.json.backup")));
        assertEquals("addon:custom/nested", bare.toString());
    }

    @Test
    void exactRawTextAndRegistrationIterationOrderSurvive() {
        String first = "\n  " + entry("Opaque@AbC", 1, "Текст ☃ 😀") + " \r\n";
        String second = entry("SECOND", 2, "Text");
        List<ResourceLocation> opened = new ArrayList<>();
        var snapshot = snapshot("addon:z", "addon:a.json");
        var loaded = ResearchCatalogSources.load(snapshot, physical -> {
            opened.add(physical);
            return stream(physical.getPath().equals("z.json") ? first : second);
        });
        assertEquals(List.of(id("addon:z.json"), id("addon:a.json")), opened);
        assertEquals(List.of("addon:z", "addon:a.json"), List.copyOf(loaded.spec().sources().keySet()));
        assertEquals(List.of(id("addon:z"), id("addon:a.json")), List.copyOf(loaded.payload().sources().keySet()));
        assertEquals(first, loaded.spec().sources().get("addon:z"));
        assertEquals(first, loaded.payload().sources().get(id("addon:z")));
        assertEquals("Opaque@AbC", loaded.spec().entries().getFirst().key());
    }

    @Test
    void resourceManagerUsesOneSelectedResourceNotStackOrDirectoryScan() {
        AtomicInteger opens = new AtomicInteger();
        String selected = entry("TOP_PACK", 1, "selected");
        ResourceManager manager = manager(physical -> {
            assertEquals(id("addon:research.json"), physical);
            opens.incrementAndGet();
            return Optional.of(new Resource(null, () -> stream(selected)));
        });
        var loaded = ResearchCatalogSources.load(snapshot("addon:research"), manager);
        assertEquals("TOP_PACK", loaded.spec().entries().getFirst().key());
        assertEquals(1, opens.get());
    }

    @Test
    void snapshotDeduplicatesExactAliasesAndRejectsExcessSourcesOrIdentifierBeforeIo() {
        assertEquals(List.of(id("addon:a"), id("addon:b")), new RegistrationSnapshot(
            List.of(id("addon:a"), id("addon:b"), id("addon:a")), Set.of("BASICS")).registrations());
        List<ResourceLocation> excessive = new ArrayList<>();
        for (int i = 0; i <= PacketSyncResearchCatalog.MAX_SOURCES; i++) excessive.add(id("addon:p" + i));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationSnapshot(excessive, Set.of("BASICS")));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationSnapshot(
            List.of(id("addon:" + "x".repeat(1100))), Set.of("BASICS")));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationSnapshot(
            java.util.Collections.nCopies(ResearchCatalogResourceLoader.MAX_INPUT_REGISTRATIONS + 1, id("addon:a")), Set.of("BASICS")));
    }

    @Test
    void bareAndSuffixedRegistrationsAreNotMergedAndDuplicateJsonEntriesFailWholeLoad() {
        AtomicInteger reads = new AtomicInteger();
        assertThrows(ResearchCatalogParser.CatalogException.class, () -> ResearchCatalogSources.load(
            snapshot("addon:research", "addon:research.json"), physical -> {
                assertEquals(id("addon:research.json"), physical);
                reads.incrementAndGet();
                return stream(entry("DUPLICATE", 1, "same physical source"));
            }));
        assertEquals(2, reads.get());
    }

    @Test
    void malformedUtf8IsNotReplacedAndStreamIsClosed() {
        boolean[] closed = {false};
        assertThrows(IllegalArgumentException.class, () -> ResearchCatalogSources.load(snapshot("addon:a"), physical ->
            new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28}) {
                @Override public void close() throws IOException { closed[0] = true; super.close(); }
            }));
        assertTrue(closed[0]);
    }

    @Test
    void sourceLimitAllowsExactUtf8Boundary() {
        String json = entry("BOUNDARY", 1, "😀");
        int pad = PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES - json.getBytes(StandardCharsets.UTF_8).length;
        String raw = json + " ".repeat(pad);
        var loaded = ResearchCatalogSources.load(snapshot("addon:a"), physical -> stream(raw));
        assertEquals(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES,
            loaded.payload().sources().get(id("addon:a")).getBytes(StandardCharsets.UTF_8).length);
        assertEquals("BOUNDARY", loaded.spec().entries().getFirst().key());
    }

    @Test
    void oversizedInputReadsAtMostOneExcessByteAndCloses() {
        int[] read = {0}; boolean[] closed = {false};
        InputStream endless = new InputStream() {
            @Override public int read() { read[0]++; return ' '; }
            @Override public int read(byte[] bytes, int offset, int length) {
                java.util.Arrays.fill(bytes, offset, offset + length, (byte) ' ');
                read[0] += length; return length;
            }
            @Override public void close() { closed[0] = true; }
        };
        assertThrows(IllegalArgumentException.class, () -> ResearchCatalogSources.load(snapshot("addon:a"), physical -> endless));
        assertEquals(PacketSyncResearchCatalog.MAX_SOURCE_UTF8_BYTES + 1, read[0]);
        assertTrue(closed[0]);
    }

    @Test
    void aggregateByteLimitCountsEveryRegisteredSource() {
        String raw = entry("FIRST", 1, "text") + " ".repeat(300_000);
        AtomicInteger reads = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> ResearchCatalogSources.load(snapshot("addon:a", "addon:b"), physical -> {
            reads.incrementAndGet(); return stream(raw);
        }));
        assertEquals(2, reads.get());
    }

    @Test
    void missingResourceAndBadJsonFailWithoutTouchingPublicCatalog() {
        var original = ResearchCategories.researchCategories;
        assertThrows(IllegalArgumentException.class, () -> ResearchCatalogSources.load(snapshot("addon:missing"), ResourceManager.Empty.INSTANCE));
        assertThrows(ResearchCatalogParser.CatalogException.class, () -> ResearchCatalogSources.load(snapshot("addon:bad"), physical -> stream("{")));
        assertSame(original, ResearchCategories.researchCategories);
    }

    @Test
    void backgroundLoaderUsesOnlyCapturedCategoriesNotMutableGlobalApi() {
        var original = ResearchCategories.researchCategories;
        try {
            ResearchCategories.researchCategories = null;
            var loaded = ResearchCatalogSources.load(snapshot("addon:a"), physical -> stream(entry("ONE", 1, "text")));
            assertEquals(1, loaded.spec().entries().size());
            assertNull(ResearchCategories.researchCategories);
        } finally { ResearchCategories.researchCategories = original; }
    }

    static RegistrationSnapshot snapshot(String... ids) {
        return new RegistrationSnapshot(Stream.of(ids).map(ResearchCatalogSourcesTest::id).toList(), Set.of("BASICS"));
    }
    static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    static InputStream stream(String raw) { return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)); }
    static String entry(String key, int x, String name) {
        return "{\"entries\":[{\"key\":\"" + key + "\",\"name\":\"" + name
            + "\",\"category\":\"BASICS\",\"location\":[" + x + ",1],\"stages\":[]}]}";
    }
    static ResourceManager manager(java.util.function.Function<ResourceLocation, Optional<Resource>> get) {
        return new ResourceManager() {
            @Override public Optional<Resource> getResource(ResourceLocation location) { return get.apply(location); }
            @Override public Set<String> getNamespaces() { return Set.of("addon"); }
            @Override public List<Resource> getResourceStack(ResourceLocation location) { throw new AssertionError("No pack-stack merge"); }
            @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) { throw new AssertionError("No directory scan"); }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) { throw new AssertionError("No stack scan"); }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        };
    }
}
