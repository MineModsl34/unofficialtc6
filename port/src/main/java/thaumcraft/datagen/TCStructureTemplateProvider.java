package thaumcraft.datagen;

import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import thaumcraft.common.world.validation.LegacyMoundImporter;

/** Explicit private import of executed actual-state geometry, not merely 1.12's stored state capture. */
public class TCStructureTemplateProvider implements DataProvider {
    private final PackOutput output;
    private final Path source;

    public TCStructureTemplateProvider(PackOutput output, Path source) {
        this.output = output;
        this.source = source;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            Path destination = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve("thaumcraft/structure/mound.nbt");
            CompoundTag modern;
            if (source != null) {
                modern = LegacyMoundImporter.read(source).fixedTag().copy();
            } else {
                if (!Files.isRegularFile(destination)) {
                    throw new IllegalStateException("Missing validated modern mound: perform the explicit private import with -PtcMoundTemplateSource first");
                }
                // Keep the already imported generated asset in HashCache on ordinary datagen runs.
                // Raw 1.12 assets are not a hidden requirement for every subsequent build.
                modern = NbtIo.readCompressed(destination, NbtAccounter.create(16L * 1024 * 1024));
                LegacyMoundImporter.validateModern(modern);
            }
            var counts = LegacyMoundImporter.blockCounts(modern);
            ListTag size = modern.getList("size", Tag.TAG_INT);
            if (size.size() != 3 || size.getInt(0) != 19 || size.getInt(1) != 17 || size.getInt(2) != 19
                || counts.getOrDefault("minecraft:air", 0) != 774
                || modern.getList("blocks", Tag.TAG_COMPOUND).size() != 2446
                || counts.containsKey("minecraft:trapped_chest") || counts.containsKey("minecraft:tnt")) {
                throw new IllegalArgumentException("Expected canonical original normal mound, not a background capture or trapped variant");
            }
            ListTag palette = modern.getList("palette", Tag.TAG_COMPOUND);
            int chests = 0;
            int bars = 0;
            int stairs = 0;
            for (Tag raw : modern.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                CompoundTag state = palette.getCompound(entry.getInt("state"));
                String block = state.getString("Name");
                if (block.equals("minecraft:iron_bars")) {
                    ListTag pos = entry.getList("pos", Tag.TAG_INT);
                    CompoundTag properties = state.getCompound("Properties");
                    if (pos.getInt(0) != 3 || (pos.getInt(1) != 10 && pos.getInt(1) != 11) || pos.getInt(2) != 9
                        || !properties.getString("north").equals("true") || !properties.getString("south").equals("true")
                        || !properties.getString("east").equals("false") || !properties.getString("west").equals("false")) {
                        throw new IllegalArgumentException("Expected executed original getActualState bar connections at " + pos
                            + "; fixed state " + state + "; import the actual-state fixture, not stored-only NBT");
                    }
                    bars++;
                }
                if (block.equals("minecraft:cobblestone_stairs")) {
                    if (!state.getCompound("Properties").getString("shape").equals("straight")) {
                        throw new IllegalArgumentException("Canonical original reference has only straight stairs");
                    }
                    stairs++;
                }
                if (block.equals("minecraft:chest")) {
                    ListTag pos = entry.getList("pos", Tag.TAG_INT);
                    if (pos.getInt(0) != 10 || pos.getInt(1) != 2 || pos.getInt(2) != 9) {
                        throw new IllegalArgumentException("Unexpected mound chest coordinate");
                    }
                    // Template placement reseeds ANY chest with NBT. Our piece creates the actual
                    // simple_dungeon loot table/seed after placement using its original decision stream.
                    entry.remove("nbt");
                    chests++;
                }
            }
            if (chests != 1 || bars != 2 || stairs != 59) {
                throw new IllegalArgumentException("Expected one mound chest, two actual-state bars and 59 canonical stairs");
            }
            LegacyMoundImporter.validateModern(modern);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(modern, bytes);
            byte[] data = bytes.toByteArray();
            cache.writeIfNeeded(destination, data, Hashing.sha1().hashBytes(data));
            return CompletableFuture.completedFuture(null);
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public String getName() {
        return "Validated modern TC6 mound template (private research import)";
    }
}
