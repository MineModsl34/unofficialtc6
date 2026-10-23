package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class TCScanTestStructureProviderTest {
    @Test
    void scanFixtureHasDeclaredTwelveCubeAndNoGameplayContent() {
        var tag = TCScanTestStructureProvider.template();
        assertEquals(3955, tag.getInt("DataVersion"));
        var size = tag.getList("size", Tag.TAG_INT);
        assertEquals(3, size.size());
        for (int axis = 0; axis < 3; axis++) assertEquals(12, size.getInt(axis));
        assertEquals("minecraft:air", tag.getList("palette", Tag.TAG_COMPOUND).getCompound(0).getString("Name"));
        assertTrue(tag.getList("blocks", Tag.TAG_COMPOUND).isEmpty());
        assertTrue(tag.getList("entities", Tag.TAG_COMPOUND).isEmpty());
    }

    @Test
    void runEnrollsExactCompressedTestTemplateAndHashWithoutFilesystemWrites() {
        var base = Path.of("build", "datagen-scan-test-only");
        AtomicInteger writes = new AtomicInteger();
        CachedOutput capture = (destination, bytes, hash) -> {
            assertEquals(base.resolve("data/thaumcraft/structure/scan_test.nbt"), destination);
            assertEquals(Hashing.sha1().hashBytes(bytes), hash);
            assertEquals(TCScanTestStructureProvider.template(),
                NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(4096)));
            writes.incrementAndGet();
        };
        new TCScanTestStructureProvider(new PackOutput(base)).run(capture).join();
        assertEquals(1, writes.get());
    }
}
