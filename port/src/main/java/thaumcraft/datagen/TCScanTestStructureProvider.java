package thaumcraft.datagen;

import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Generated test enclosure only, never a gameplay/worldgen structure or original TC asset.
 * A 3-cube GameTest installs barriers in the scan rays; this 12-cube keeps the actual targets,
 * nine-block entity ray and five-block block ray inside the isolated test's own bounds. */
public final class TCScanTestStructureProvider implements DataProvider {
    private final PackOutput output;

    public TCScanTestStructureProvider(PackOutput output) { this.output = output; }

    static CompoundTag template() {
        CompoundTag template = new CompoundTag();
        template.putInt("DataVersion", 3955); // Exact target MC1.21.1.
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(12));
        size.add(IntTag.valueOf(12));
        size.add(IntTag.valueOf(12));
        template.put("size", size);
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        ListTag palette = new ListTag();
        palette.add(air);
        template.put("palette", palette);
        // GameTest's standard structure preparation clears the whole declared volume first.
        template.put("blocks", new ListTag());
        template.put("entities", new ListTag());
        return template;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            Path destination = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve("thaumcraft/structure/scan_test.nbt");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(template(), bytes);
            byte[] data = bytes.toByteArray();
            cache.writeIfNeeded(destination, data, Hashing.sha1().hashBytes(data));
            return CompletableFuture.completedFuture(null);
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public String getName() { return "Isolated 12-cube thaumometer GameTest enclosure"; }
}
