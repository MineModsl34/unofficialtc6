package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.Thaumcraft;
import top.theillusivec4.curios.api.CuriosDataProvider;

/** Original Baubles source order; head/charm are supplied with actual stage7 items. */
public class TCCuriosProvider extends CuriosDataProvider {
    public TCCuriosProvider(PackOutput output, ExistingFileHelper files,
                           CompletableFuture<HolderLookup.Provider> lookup) {
        super(Thaumcraft.MODID, output, files, lookup);
    }

    @Override
    public void generate(HolderLookup.Provider provider, ExistingFileHelper files) {
        createSlot("necklace").size(1).order(0);
        createSlot("ring").size(2).order(1);
        createSlot("belt").size(1).order(3);
        createEntities("player").addPlayer().addSlots("necklace", "ring", "belt");
    }
}
