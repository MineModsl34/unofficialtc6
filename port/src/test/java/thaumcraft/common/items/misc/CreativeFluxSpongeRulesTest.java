package thaumcraft.common.items.misc;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreativeFluxSpongeRulesTest {
    @Test void sourceDrainIs81DistinctChunksWithFiveHundredCap() {
        var chunks = new HashSet<String>();
        for (int x = -CreativeFluxSpongeRules.CHUNK_RADIUS; x <= CreativeFluxSpongeRules.CHUNK_RADIUS; x++)
            for (int z = -CreativeFluxSpongeRules.CHUNK_RADIUS; z <= CreativeFluxSpongeRules.CHUNK_RADIUS; z++)
                chunks.add(x + "," + z);
        assertEquals(81, chunks.size());
        assertEquals(chunks.size(), CreativeFluxSpongeRules.CHUNK_COUNT);
        assertEquals(500.0F, CreativeFluxSpongeRules.DRAIN_PER_CHUNK);
        assertEquals(32.0, CreativeFluxSpongeRules.RIFT_RANGE);
    }
    @Test void sourceTruncatesFractionalFluxAfterEveryChunkNotAfterTheSum() {
        int count = 0;
        for (int index = 0; index < 81; index++) count = CreativeFluxSpongeRules.accumulate(count, .75F);
        assertEquals(0, count);
        assertEquals(2, CreativeFluxSpongeRules.accumulate(1, 1.75F));
        assertEquals(40500, 81 * CreativeFluxSpongeRules.accumulate(0, 500.0F));
    }
}
