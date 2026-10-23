package thaumcraft.datagen;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.curios.ItemCelestialNotes;
import thaumcraft.common.config.ConfigItems;

public class TCItemModelProvider extends ItemModelProvider {
    public TCItemModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, Thaumcraft.MODID, files);
    }

    private ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "item/" + name);
    }

    @Override
    protected void registerModels() {
        for (Map.Entry<Item, String> e : ConfigItems.SIMPLE_ITEM_TEXTURES.entrySet()) {
            String id = BuiltInRegistries.ITEM.getKey(e.getKey()).getPath();
            withExistingParent(id, e.getKey() == ItemsTC.scribingTools || e.getKey() == ItemsTC.handMirror ? "item/handheld" : "item/generated")
                .texture("layer0", tex(e.getValue()));
        }
        // Tinted by aspect (ColorHandler, tintindex 0)
        for (Item item : ItemsTC.celestialNotesVariants) {
            var note = (ItemCelestialNotes) item;
            withExistingParent(BuiltInRegistries.ITEM.getKey(item).getPath(), "item/generated")
                .texture("layer0", tex("celestial/sheet"))
                .texture("layer1", tex("celestial/" + note.textureSuffix()));
        }
        withExistingParent("crystal_essence", "item/generated").texture("layer0", tex("crystal_essence"));
        withExistingParent("phial_filled", "item/generated").texture("layer0", tex("phial"))
            .texture("layer1", tex("phial_overlay"));
        var labelFilled = withExistingParent("label_filled","item/generated").texture("layer0",tex("label")).texture("layer1",tex("label_overlay"));
        withExistingParent("label","item/generated").texture("layer0",tex("label"))
            .override().predicate(modLoc("filled"),1).model(labelFilled).end();
        for (Map.Entry<Item, String> e : ConfigItems.GEAR_ITEM_TEXTURES.entrySet()) {
            String id = BuiltInRegistries.ITEM.getKey(e.getKey()).getPath();
            withExistingParent(id, id.endsWith("_sword") ? "item/handheld" : "item/generated")
                .texture("layer0", tex(e.getValue()));
        }
        var nodule = withExistingParent("primordial_pearl_nodule", "item/generated")
            .texture("layer0", tex("primordial_pearl_nodule"));
        var mote = withExistingParent("primordial_pearl_mote", "item/generated")
            .texture("layer0", tex("primordial_pearl_mote"));
        withExistingParent("primordial_pearl", "item/generated").texture("layer0", tex("primordial_pearl"))
            .override().predicate(modLoc("type"), 1).model(nodule).end()
            .override().predicate(modLoc("type"), 2).model(mote).end();
        var scanner = getBuilder("thaumometer").customLoader(ObjModelBuilder::begin)
            .modelLocation(modLoc("models/item/scanner.obj")).flipV(true).automaticCulling(false).end()
            .texture("body", tex("scanner")).texture("pane", tex("scanscreen"))
            .texture("particle", tex("scanner"));
        // Original mesh is centered on (0,0,0), whereas modern item rendering uses block center.
        scanner.rootTransforms().translation(.5F, .5F, .5F).end();
        // Old Forge normalized translations become vanilla display pixels (multiply by 16). The GUI, ground and frame
        // offsets were exactly half the scale: they undid a 1.12 Forge shift that 1.21 lacks, so they are dropped.
        // TODO(port): manually compare all hand poses with TC6; the mesh/UV are the original.
        scanner.transforms()
            .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(90, 0, 0).translation(-.96F, .096F, -6.4F).end()
            .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(90, 0, 0).translation(-16.96F, .096F, -6.4F).end()
            .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(90, 90, 0).translation(0, 0, -1.6F).scale(.2F).end()
            .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(90, 90, 0).translation(0, -1.6F, 0).scale(.2F).end()
            .transform(ItemDisplayContext.GUI).rotation(90, 0, 0).scale(.35F).end()
            .transform(ItemDisplayContext.GROUND).rotation(90, 0, 0).scale(.2F).end()
            .transform(ItemDisplayContext.FIXED).rotation(-90, 0, 0).scale(.4F).end();
        // Gear, devices, mob drops and special meshes use their dedicated asset providers.
    }
}
