package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Headless asset checks only: no Minecraft client, native window, GL or texture sampler. */
class TCResearchGuiTexturesTest {
    private static final byte[] PNG_SIGNATURE = {(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    @TempDir Path directory;

    @Test
    void manifestIsExactlyTheSevenSourceBackgroundsWithoutChangingTheirNamesOrJpegIdentity() {
        var sources = TCResearchGuiTextures.CANONICAL_SOURCES;
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), sources.stream().map(TCResearchGuiTextures.SourceIdentity::number).toList());
        assertEquals(439479, sources.stream().mapToInt(TCResearchGuiTextures.SourceIdentity::byteCount).sum());
        assertThrows(UnsupportedOperationException.class, sources::clear);
        assertThrows(IllegalArgumentException.class, () -> new TCResearchGuiTextures.SourceIdentity(8, 100, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new TCResearchGuiTextures.SourceIdentity(1, 100, "bad"));
        for (var identity : sources) {
            assertEquals("/assets/thaumcraft/textures/gui/gui_research_back_" + identity.number() + ".jpg", identity.classpathSource());
            assertEquals("thaumcraft/textures/gui/gui_research_back_" + identity.number() + ".png", identity.relativeOutput());
        }
    }

    @Test
    void allSevenPngCompanionsPreserveEveryDecodedSourceRgbPixelDimensionsAndOpaqueAlpha() throws Exception {
        var prepared = TCResearchGuiTextures.prepareWhole(TCResearchGuiTexturesTest.class::getResourceAsStream);
        assertEquals(7, prepared.size());
        for (var texture : prepared) {
            byte[] original = sourceBytes(texture.identity());
            assertEquals(texture.identity().byteCount(), original.length);
            assertEquals(texture.identity().sha256(), Hashing.sha256().hashBytes(original).toString());
            BufferedImage jpeg = decode(original);
            byte[] png = texture.png();
            assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(png, 8));
            BufferedImage converted = decode(png);
            assertEquals(1024, jpeg.getWidth());
            assertEquals(1024, jpeg.getHeight());
            assertEquals(jpeg.getWidth(), converted.getWidth());
            assertEquals(jpeg.getHeight(), converted.getHeight());
            int[] sourcePixels = jpeg.getRGB(0, 0, 1024, 1024, null, 0, 1024);
            int[] modernPixels = converted.getRGB(0, 0, 1024, 1024, null, 0, 1024);
            assertArrayEquals(sourcePixels, modernPixels, texture.identity().classpathSource());
            assertTrue(Arrays.stream(modernPixels).allMatch(pixel -> (pixel >>> 24) == 255), "All 1,048,576 pixels must remain opaque");
            assertArrayEquals(original, sourceBytes(texture.identity()), "No source JPEG rewrite");
        }
        assertThrows(UnsupportedOperationException.class, prepared::clear);
        byte[] saved = prepared.getFirst().png();
        byte[] modified = prepared.getFirst().png();
        modified[0] = 0;
        assertArrayEquals(saved, prepared.getFirst().png(), "Prepared output must not leak mutable internal bytes");
    }

    @Test
    void repeatedMemoryOnlyConversionIsByteDeterministicForAllSevenCanonicalSources() throws Exception {
        for (var identity : TCResearchGuiTextures.CANONICAL_SOURCES) {
            byte[] jpeg = sourceBytes(identity);
            assertArrayEquals(TCResearchGuiTextures.encodePng(jpeg, 1024, 1024),
                TCResearchGuiTextures.encodePng(jpeg, 1024, 1024), identity.classpathSource());
        }
    }

    @Test
    void datagenEnrollsExactlySevenPngPathsAndContentHashesWithoutWritingOriginalJpegs() throws Exception {
        Path generated = directory.resolve("not-created-generated");
        CacheSpy cache = new CacheSpy();
        new TCResearchGuiTextures(new PackOutput(generated)).run(cache).join();
        assertEquals(7, cache.writes.size());
        for (var identity : TCResearchGuiTextures.CANONICAL_SOURCES) {
            Path output = generated.resolve("assets").resolve(identity.relativeOutput()).toAbsolutePath().normalize();
            byte[] bytes = cache.writes.get(output);
            assertNotNull(bytes, identity.relativeOutput());
            assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(bytes, 8));
            assertEquals(Hashing.sha1().hashBytes(bytes), cache.hashes.get(output));
            assertFalse(output.toString().endsWith(".jpg"));
        }
        assertFalse(Files.exists(generated), "Cache spy proves provider creates no extra files itself");
    }

    @Test
    void missingLastSourceRejectsWholeSetBeforeFirstCacheEnrollment() {
        Path output = directory.resolve("missing-source-output");
        CacheSpy cache = new CacheSpy();
        var last = TCResearchGuiTextures.CANONICAL_SOURCES.getLast();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchGuiTextures(new PackOutput(output), path -> path.equals(last.classpathSource())
                ? null : TCResearchGuiTexturesTest.class.getResourceAsStream(path)).run(cache).join());
        assertTrue(failure.getCause().getMessage().contains("Missing original"));
        assertTrue(cache.writes.isEmpty());
        assertFalse(Files.exists(output));
    }

    @Test
    void modifiedLastSourceRejectsWholeSetEvenIfByteCountAndJpegHeaderMatch() throws Exception {
        Path output = directory.resolve("changed-source-output");
        CacheSpy cache = new CacheSpy();
        var last = TCResearchGuiTextures.CANONICAL_SOURCES.getLast();
        byte[] changed = sourceBytes(last);
        changed[changed.length / 2] ^= 1;
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchGuiTextures(new PackOutput(output), path -> path.equals(last.classpathSource())
                ? new ByteArrayInputStream(changed) : TCResearchGuiTexturesTest.class.getResourceAsStream(path)).run(cache).join());
        assertTrue(failure.getCause().getMessage().contains("SHA256/byte-count mismatch"));
        assertTrue(cache.writes.isEmpty());
        assertFalse(Files.exists(output));
    }

    @Test
    void conversionRejectsWrongHeadersMalformedJpegOversizeAndUnexpectedDimensions() throws Exception {
        byte[] jpeg = sourceBytes(TCResearchGuiTextures.CANONICAL_SOURCES.getFirst());
        assertThrows(IOException.class, () -> TCResearchGuiTextures.encodePng(PNG_SIGNATURE, 1024, 1024));
        assertThrows(IOException.class, () -> TCResearchGuiTextures.encodePng(new byte[]{(byte)0xff, (byte)0xd8, (byte)0xff}, 1024, 1024));
        byte[] tooLarge = Arrays.copyOf(jpeg, 128 * 1024 + 1);
        assertThrows(IOException.class, () -> TCResearchGuiTextures.encodePng(tooLarge, 1024, 1024));
        assertThrows(IOException.class, () -> TCResearchGuiTextures.encodePng(jpeg, 256, 256));
        assertThrows(IOException.class, () -> TCResearchGuiTextures.encodePng(jpeg, 1024, 1));
    }

    @Test
    void sourceIoFailureIsPropagatedBeforeAnyCacheEnrollment() {
        CacheSpy cache = new CacheSpy();
        var last = TCResearchGuiTextures.CANONICAL_SOURCES.getLast();
        CompletionException failure = assertThrows(CompletionException.class,
            () -> new TCResearchGuiTextures(new PackOutput(directory.resolve("io-failure-output")), path -> {
                if (path.equals(last.classpathSource())) throw new IOException("Isolated source stream failure");
                return TCResearchGuiTexturesTest.class.getResourceAsStream(path);
            }).run(cache).join());
        assertEquals("Isolated source stream failure", failure.getCause().getMessage());
        assertTrue(cache.writes.isEmpty());
    }

    private static byte[] sourceBytes(TCResearchGuiTextures.SourceIdentity identity) throws IOException {
        try (InputStream input = TCResearchGuiTexturesTest.class.getResourceAsStream(identity.classpathSource())) {
            assertNotNull(input, identity.classpathSource());
            return input.readAllBytes();
        }
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            assertTrue(readers.hasNext(), "ImageIO must recognize the actual resource bytes");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                BufferedImage image = reader.read(0);
                assertNotNull(image, "ImageIO must decode the actual resource bytes");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static final class CacheSpy implements CachedOutput {
        private final Map<Path, byte[]> writes = new LinkedHashMap<>();
        private final Map<Path, HashCode> hashes = new LinkedHashMap<>();

        @Override public void writeIfNeeded(Path path, byte[] data, HashCode hash) {
            if (writes.put(path, data.clone()) != null) throw new AssertionError("Duplicate enrollment");
            hashes.put(path, hash);
        }
    }
}
