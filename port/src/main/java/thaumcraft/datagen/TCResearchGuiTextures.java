package thaumcraft.datagen;

import com.google.common.hash.Hashing;
import java.awt.image.BufferedImage;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Transport adaptation for the seven TC6 research-map JPEG backgrounds. Modern NativeImage
 * accepts PNG rather than the source JPEG files. The originals remain untouched; these generated
 * companions preserve every decoded RGB pixel, dimensions and opaque alpha, without scaling,
 * filtering or new imagery. ImageIO is used only in headless datagen/tests, never client rendering.
 */
public final class TCResearchGuiTextures implements DataProvider {
    private static final int MAX_SOURCE_BYTES = 128 * 1024;
    private static final int SOURCE_SIZE = 1024;

    /** Identities of the imported, byte-identical TC6.1.BETA26 original resources. */
    public static final List<SourceIdentity> CANONICAL_SOURCES = List.of(
        new SourceIdentity(1, 60867, "4cabe672801b0713bcbb3e0eb839c89e9ec5dd09f5debf0266ad337ca2a0f573"),
        new SourceIdentity(2, 75347, "64fa59e7020c156b76e2964b1078a1bc390b0435bf1a8034f718ff91d82d248d"),
        new SourceIdentity(3, 67397, "dc824b0275b3dfb7c3a651c52969ff87487ca4d14dbf7b56e7e37c7385041c61"),
        new SourceIdentity(4, 57636, "7eadaa7fbcaae70cdf6bff7c8f847c37c219501030f6556c918034e6779c1857"),
        new SourceIdentity(5, 55962, "003d295e2a817e55755e2bed80c08b34fa8c6afb18a06d9f57213ce96c89cdfd"),
        new SourceIdentity(6, 57884, "e246194929e71e708c36e1938b2d6a2cf8d8229d749a944f9f98156ed5c479a2"),
        new SourceIdentity(7, 64386, "c5f7c4b4403b125598411e48d4a6bce25d5e651f24e4e427336416b9483cc054"));

    public record SourceIdentity(int number, int byteCount, String sha256) {
        public SourceIdentity {
            Objects.requireNonNull(sha256);
            if (number < 1 || number > 7 || byteCount < 3 || byteCount > MAX_SOURCE_BYTES
                || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid fixed TC6 research-background identity");
            }
        }

        public String classpathSource() {
            return "/assets/thaumcraft/textures/gui/gui_research_back_" + number + ".jpg";
        }

        public String relativeOutput() {
            return "thaumcraft/textures/gui/gui_research_back_" + number + ".png";
        }
    }

    @FunctionalInterface
    interface SourceOpener {
        InputStream open(String path) throws IOException;
    }

    record PreparedTexture(SourceIdentity identity, byte[] png) {
        PreparedTexture {
            Objects.requireNonNull(identity);
            png = png.clone();
        }

        @Override public byte[] png() { return png.clone(); }
    }

    private final PackOutput output;
    private final SourceOpener opener;

    public TCResearchGuiTextures(PackOutput output) {
        this(output, TCResearchGuiTextures.class::getResourceAsStream);
    }

    /** Isolated classpath-error/cache seam; production manifest and destinations stay fixed. */
    TCResearchGuiTextures(PackOutput output, SourceOpener opener) {
        this.output = Objects.requireNonNull(output);
        this.opener = Objects.requireNonNull(opener);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            Path assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).toAbsolutePath().normalize();
            validateDestinations(assets);
            List<PreparedTexture> prepared = prepareWhole(opener);
            // Validate/convert all seven sources before enrolling even the first generated file.
            for (PreparedTexture texture : prepared) {
                byte[] bytes = texture.png();
                cache.writeIfNeeded(assets.resolve(texture.identity().relativeOutput()), bytes,
                    Hashing.sha1().hashBytes(bytes));
            }
            return CompletableFuture.completedFuture(null);
        } catch (IOException | RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public String getName() {
        return "Lossless decoded-pixel TC6 research JPEG to modern PNG companions";
    }

    static List<PreparedTexture> prepareWhole(SourceOpener opener) throws IOException {
        List<PreparedTexture> result = new ArrayList<>(CANONICAL_SOURCES.size());
        for (SourceIdentity identity : CANONICAL_SOURCES) {
            byte[] jpeg;
            try (InputStream source = opener.open(identity.classpathSource())) {
                if (source == null) throw new IOException("Missing original TC6 research background: " + identity.classpathSource());
                jpeg = source.readNBytes(MAX_SOURCE_BYTES + 1);
            }
            if (jpeg.length != identity.byteCount()
                || !Hashing.sha256().hashBytes(jpeg).toString().equals(identity.sha256())) {
                throw new IOException("Original TC6 research-background SHA256/byte-count mismatch: " + identity.classpathSource());
            }
            result.add(new PreparedTexture(identity, encodePng(jpeg, SOURCE_SIZE, SOURCE_SIZE)));
        }
        return List.copyOf(result);
    }

    /** Pure, memory-only conversion: expected dimensions prevent accidentally changing the atlas. */
    static byte[] encodePng(byte[] jpeg, int expectedWidth, int expectedHeight) throws IOException {
        Objects.requireNonNull(jpeg);
        if (jpeg.length < 3 || jpeg.length > MAX_SOURCE_BYTES
            || jpeg[0] != (byte)0xff || jpeg[1] != (byte)0xd8 || jpeg[2] != (byte)0xff) {
            throw new IOException("Expected bounded original JPEG background bytes");
        }
        BufferedImage decoded;
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg))) {
            // ImageIO.read(ImageInputStream) itself closes a recognized input stream. Own it once
            // here instead, including the missing-reader/error branches, with no disk cache.
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("JPEG decoder unavailable for research backgrounds");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null || decoded.getWidth() != expectedWidth || decoded.getHeight() != expectedHeight
            || expectedWidth != SOURCE_SIZE || expectedHeight != SOURCE_SIZE) {
            throw new IOException("Expected original 1024x1024 research background dimensions");
        }
        int[] pixels = decoded.getRGB(0, 0, expectedWidth, expectedHeight, null, 0, expectedWidth);
        for (int index = 0; index < pixels.length; index++) pixels[index] |= 0xff000000;
        BufferedImage rgba = new BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_ARGB);
        rgba.setRGB(0, 0, expectedWidth, expectedHeight, pixels, 0, expectedWidth);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(bytes)) {
            if (!ImageIO.write(rgba, "png", stream)) throw new IOException("PNG encoder unavailable for research backgrounds");
        }
        return bytes.toByteArray();
    }

    /** CachedOutput follows links; preflight all fixed outputs before its first write/enrollment. */
    private static void validateDestinations(Path assets) throws IOException {
        for (SourceIdentity identity : CANONICAL_SOURCES) {
            Path file = assets.resolve(identity.relativeOutput()).normalize();
            if (!file.startsWith(assets) || file.equals(assets)) throw new IOException("Research texture output escapes assets root");
            for (Path part = file; part != null; part = part.getParent()) {
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (NoSuchFileException absent) {
                    continue;
                }
                boolean reparse = false;
                if (part.getFileSystem().getSeparator().equals("\\")) {
                    Object raw = Files.getAttribute(part, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
                    if (!(raw instanceof Number flags)) throw new IOException("Missing Windows output attributes: " + part);
                    reparse = (flags.intValue() & 0x400) != 0;
                }
                if (attributes.isSymbolicLink() || attributes.isOther() || reparse
                    || (part.equals(file) ? !attributes.isRegularFile() : !attributes.isDirectory())) {
                    throw new IOException("Linked/reparse or invalid research texture output path: " + part);
                }
            }
        }
    }
}
