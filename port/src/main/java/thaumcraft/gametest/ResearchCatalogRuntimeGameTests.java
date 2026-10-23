package thaumcraft.gametest;

import io.netty.buffer.Unpooled;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.config.ConfigResearch;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceLoader;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceManagerSource;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;
import thaumcraft.common.lib.research.catalog.ResearchCatalogStaging;

/** Real accepted server resources/API metadata/codec, not a remote-client or progression claim. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class ResearchCatalogRuntimeGameTests {
    @GameTest(template = "empty")
    public static void initialServerAcceptancePublishesWholeBundledCatalogAndBoundedTransport(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        h.assertTrue(server.isSameThread(), "catalog acceptance test uses the server owner thread");
        var snapshot = ResearchCatalogRuntime.currentSnapshot(server);
        var packet = ResearchCatalogRuntime.currentPayload(server);
        h.assertTrue(snapshot != null && packet != null, "ServerAboutToStart accepted actual managers before players/world tests");
        var map = ResearchCategories.researchCategories;
        h.assertTrue(ResearchCatalogRuntime.commitAccepted(server).status() == ResearchCatalogStaging.Status.ALREADY_CURRENT,
            "exact same accepted managers do not republish metadata");
        h.assertTrue(ResearchCategories.researchCategories == map, "repeat acceptance preserves public map identity");
        h.assertTrue(map.keySet().containsAll(Set.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH")),
            "all seven source categories are present");
        h.assertTrue(packet.sources().size() == 8 && packet.sources().keySet().equals(new LinkedHashSet<>(ConfigResearch.BUILTIN_RESEARCH_LOCATIONS)),
            "all eight exact original registration identities, no suffix conversion on the wire");
        h.assertTrue(snapshot.entries().size() == 148 && snapshot.ownedEntries().size() == 148,
            "entire bundled source catalog, not only valid-looking resolved items");
        int stages = snapshot.entries().values().stream().mapToInt(entry -> entry.getStages() == null ? 0 : entry.getStages().length).sum();
        int addenda = snapshot.entries().values().stream().mapToInt(entry -> entry.getAddenda() == null ? 0 : entry.getAddenda().length).sum();
        h.assertTrue(stages == 271 && addenda == 16, "source stage/addendum counts preserved in production API");
        for (var entry : snapshot.entries().entrySet()) {
            h.assertTrue(ResearchCategories.getResearch(entry.getKey()) == entry.getValue(), "public API indexes exact accepted entry " + entry.getKey());
        }
        var selected = ResearchCatalogResourceLoader.load(List.copyOf(packet.sources().keySet()), new LinkedHashSet<>(map.keySet()),
            new ResearchCatalogResourceManagerSource(server.getResourceManager()));
        h.assertTrue(selected.spec().sources().equals(snapshot.spec().sources()), "accepted raw text is the actual highest-priority server data");
        h.assertTrue(selected.utf8Bytes() == 106387, "bundled eight source files retain original exact UTF-8 byte total");
        h.assertTrue(packet.encodedBytes() <= PacketSyncResearchCatalog.MAX_ENCODED_BYTES && packet.encodedBytes() < 1024 * 1024,
            "metadata frame is bounded below the actual custom-payload ceiling");
        var wire = Unpooled.buffer();
        try {
            PacketSyncResearchCatalog.STREAM_CODEC.encode(wire, packet);
            var decoded = PacketSyncResearchCatalog.STREAM_CODEC.decode(wire);
            h.assertTrue(decoded.sources().equals(packet.sources()) && decoded.digest().equals(packet.digest()), "real accepted envelope survives stream codec");
            h.assertTrue(List.copyOf(decoded.sources().keySet()).equals(List.copyOf(packet.sources().keySet())), "source HashMap snapshot iteration order survives codec");
            h.assertFalse(wire.isReadable(), "bounded frame consumed exactly");
        } finally { wire.release(); }
        h.succeed();
    }
}
