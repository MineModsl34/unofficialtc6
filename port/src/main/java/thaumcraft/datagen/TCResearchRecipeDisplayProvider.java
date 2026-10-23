package thaumcraft.datagen;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog;

/** Keep the reviewed executed-original display capture in HashCache during ordinary datagen.
 * No original game installation/report is an implicit build prerequisite. This is display data,
 * never registration of future arcane/crucible/infusion crafting engines. */
public final class TCResearchRecipeDisplayProvider implements DataProvider {
    public static final String SHA256 = "a21d695f4e39e03e665300d35a1c3d53f4573d2579b0d53006a20a5ac3bd2cb2";
    public static final int BYTE_COUNT = 379966;
    private final PackOutput output;
    public TCResearchRecipeDisplayProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        try {
            Path destination = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                .resolve("thaumcraft/research_recipe_display/catalog.json").toAbsolutePath().normalize();
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination))
                throw new IOException("Missing executed TC6 recipe display capture; restore the checked-in generated asset first");
            byte[] bytes;
            try (var input = Files.newInputStream(destination)) { bytes = input.readNBytes(BYTE_COUNT + 1); }
            validate(bytes);
            cache.writeIfNeeded(destination, bytes, Hashing.sha1().hashBytes(bytes));
            return CompletableFuture.completedFuture(null);
        } catch (IOException | RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }
    static ResearchRecipeDisplayCatalog validate(byte[] bytes) {
        if (bytes.length != BYTE_COUNT || !Hashing.sha256().hashBytes(bytes).toString().equals(SHA256))
            throw new IllegalArgumentException("Executed original recipe display SHA256/size mismatch");
        var catalog = ResearchRecipeDisplayCatalog.parse(new String(bytes, StandardCharsets.UTF_8));
        if (catalog.all().size() != 870) throw new IllegalArgumentException("Expected833 original recipes and37 groups");
        for (var display : catalog.all().values()) for (var member : display.groupMembers())
            if (catalog.find(member) == null) throw new IllegalArgumentException("Missing source group member " + member);
        return catalog;
    }
    @Override public String getName() { return "Executed TC6 recipe display descriptors (not crafting engines)"; }
}
