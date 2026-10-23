package thaumcraft.datagen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.config.ConfigBlocks;

public class TCBlockStateProvider extends BlockStateProvider {
    /** Shard groups in models/obj/crystal.obj (8 of them, standing on the bottom face). */
    private static final List<String> SHARDS = List.of("s014", "s015", "s016", "s017", "s018", "s019", "s024", "s026");
    private static final int CRYSTAL_VARIANTS = 4;

    public TCBlockStateProvider(PackOutput output, ExistingFileHelper files) {
        super(output, Thaumcraft.MODID, files);
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private ResourceLocation tex(String name) {
        return modLoc("block/" + name);
    }

    @Override
    protected void registerStatesAndModels() {
        cube(BlocksTC.oreAmber);
        cube(BlocksTC.oreCinnabar);
        cube(BlocksTC.oreQuartz);
        cube(BlocksTC.plankGreatwood);
        cube(BlocksTC.plankSilverwood);
        log(BlocksTC.logGreatwood);
        log(BlocksTC.logSilverwood);
        leaves(BlocksTC.leafGreatwood);
        leaves(BlocksTC.leafSilverwood);
        cross(BlocksTC.saplingGreatwood);
        cross(BlocksTC.saplingSilverwood);
        cross(BlocksTC.shimmerleaf);
        cross(BlocksTC.cinderpearl);
        cross(BlocksTC.vishroom);
        crystals();

        // Stage 2c: decoration
        rotated(BlocksTC.stoneArcane, existing("arcane_stone"), false);
        ModelFile arcaneBrick = cubeTex(BlocksTC.stoneArcaneBrick, "arcane_brick_stone");
        rotated(BlocksTC.stoneAncient, existing("ancient_stone"), true);
        cubeTex(BlocksTC.stoneAncientTile, "ancient_tile");
        randomTextures(BlocksTC.stoneAncientRock, "ancient_rock_stone_1", "ancient_rock_stone_2", "ancient_rock_stone_3");
        cubeTex(BlocksTC.stoneAncientGlyphed, "ancient_glyph");
        cubeTex(BlocksTC.stoneAncientDoorway, "eldritch_door");
        rotated(BlocksTC.stoneEldritchTile, existing("eldritch_stone"), false);
        cubeTex(BlocksTC.stonePorous, "porous_stone");
        cubeTex(BlocksTC.matrixSpeed, "matrix_speed");
        cubeTex(BlocksTC.matrixCost, "matrix_cost");
        cubeTex(BlocksTC.metalBlockBrass, "metal_brass");
        cubeTex(BlocksTC.metalBlockThaumium, "metal_thaumium");
        cubeTex(BlocksTC.metalBlockVoid, "metal_void");
        cubeTex(BlocksTC.metalAlchemical, "metal_alchemical");
        cubeTex(BlocksTC.metalAlchemicalAdvanced, "metal_alchemical_advanced");
        cubeTex(BlocksTC.fleshBlock, "flesh");

        stairs(BlocksTC.stairsArcane, "arcane_stone_3", "arcane_stone_1", "arcane_stone_2");
        stairs(BlocksTC.stairsArcaneBrick, "arcane_brick_stone", "arcane_brick_stone", "arcane_brick_stone");
        stairs(BlocksTC.stairsAncient, "ancient_tile", "ancient_tile", "ancient_tile");
        stairs(BlocksTC.stairsGreatwood, "plank_greatwood", "plank_greatwood", "plank_greatwood");
        stairs(BlocksTC.stairsSilverwood, "plank_silverwood", "plank_silverwood", "plank_silverwood");
        slab(BlocksTC.slabArcaneStone, existing("arcane_stone"), "arcane_stone_3", "arcane_stone_1", "arcane_stone_2");
        slab(BlocksTC.slabArcaneBrick, arcaneBrick, "arcane_brick_stone", "arcane_brick_stone", "arcane_brick_stone");
        slab(BlocksTC.slabAncient, existing("ancient_stone"), "ancient_stone_3", "ancient_stone_1", "ancient_stone_2");
        slab(BlocksTC.slabEldritch, existing("eldritch_stone"), "eldritch_stone_3", "eldritch_stone_1", "eldritch_stone_2");
        slab(BlocksTC.slabGreatwood, models().getExistingFile(modLoc("block/plank_greatwood")), "plank_greatwood", "plank_greatwood", "plank_greatwood");
        slab(BlocksTC.slabSilverwood, models().getExistingFile(modLoc("block/plank_silverwood")), "plank_silverwood", "plank_silverwood", "plank_silverwood");

        simpleBlockWithItem(BlocksTC.amberBlock, models().cubeBottomTop("amber_block", tex("amber_side"), tex("amber_top"), tex("amber_top"))
            .renderType("translucent"));
        simpleBlockWithItem(BlocksTC.amberBrick, models().cubeAll("amber_brick", tex("amber_brick")).renderType("translucent"));
        simpleBlockWithItem(BlocksTC.empty, models().cubeAll("empty", tex("empty")).renderType("translucent"));

        grassAmbient();
        simpleBlockWithItem(BlocksTC.tableWood, existing("table_wood"));
        simpleBlockWithItem(BlocksTC.arcaneWorkbench, existing("arcane_workbench"));
        simpleBlockWithItem(BlocksTC.crucible, existing("crucible"));
        simpleBlockWithItem(BlocksTC.pedestalArcane,existing("pedestal_normal"));
        simpleBlockWithItem(BlocksTC.pedestalAncient,existing("pedestal_ancient"));
        simpleBlockWithItem(BlocksTC.pedestalEldritch,existing("pedestal_eldritch"));
        simpleBlockWithItem(BlocksTC.infusionMatrix,existing("infusion_matrix"));
        simpleBlockWithItem(BlocksTC.stabilizer,existing("stabilizer"));
        simpleBlockWithItem(BlocksTC.jarNormal,existing("jar_normal"));
        simpleBlockWithItem(BlocksTC.jarVoid,existing("jar_void"));
        for (Block jar : new Block[]{BlocksTC.jarNormal,BlocksTC.jarVoid}) {
            var item = itemModels().withExistingParent(name(jar),modLoc("block/" + name(jar)));
            for (int index = 1; index <= 4; index++) item.override().predicate(mcLoc("fill"),index).model(existing(name(jar) + "_" + index * 25)).end();
        }
        simpleBlockWithItem(BlocksTC.alembic,existing("alembic_normal"));
        for (Block smelter : new Block[]{BlocksTC.smelterBasic,BlocksTC.smelterThaumium,BlocksTC.smelterVoid}) {
            getVariantBuilder(smelter).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(existing(name(smelter) + (state.getValue(thaumcraft.common.blocks.essentia.BlockSmelter.ENABLED) ? "_on" : "_off")))
                .rotationY((int)state.getValue(thaumcraft.common.blocks.essentia.BlockSmelter.FACING).toYRot() + 180).build());
            simpleBlockItem(smelter,existing(name(smelter) + "_off"));
        }
        simpleBlock(BlocksTC.effectGlimmer,models().getExistingFile(mcLoc("block/air")));
        // Exact source NONE/SIDE/EXT multipart + weighted rune variants is an authored resource.
        // Do not replace it with a generic dot/line generated approximation.
        for (Block nitor : BlocksTC.nitor.values()) {
            simpleBlock(nitor, models().getExistingFile(mcLoc("block/air")));
            itemModels().withExistingParent(name(nitor), mcLoc("item/generated"))
                .texture("layer0", tex("nitor")).texture("layer1", tex("nitor_core"));
        }
        simpleBlockWithItem(BlocksTC.tableStone, existing("table_stone"));
        // Source facing=north has zero rotation, east=90, south=180, west=270.
        horizontalBlock(BlocksTC.researchTable, existing("research_table"), 180);
        simpleBlockItem(BlocksTC.researchTable, existing("research_table"));
        simpleBlockWithItem(BlocksTC.pavingStoneTravel, existing("paving_stone_travel"));
        simpleBlockWithItem(BlocksTC.pavingStoneBarrier, existing("paving_stone_barrier"));
        for (Block candle : BlocksTC.candles.values()) {
            ModelFile m = existing("candle");
            getVariantBuilder(candle).partialState().setModels(
                new ConfiguredModel(m), new ConfiguredModel(m, 0, 90, false),
                new ConfiguredModel(m, 0, 180, false), new ConfiguredModel(m, 0, 270, false));
            simpleBlockItem(candle, m);
        }
        pillar(BlocksTC.pillarArcane, "pillar_normal");
        pillar(BlocksTC.pillarAncient, "pillar_ancient");
        pillar(BlocksTC.pillarEldritch, "pillar_eldritch");
        lootContainer(BlocksTC.lootCrateCommon, true, 0);
        lootContainer(BlocksTC.lootCrateUncommon, true, 1);
        lootContainer(BlocksTC.lootCrateRare, true, 2);
        lootContainer(BlocksTC.lootUrnCommon, false, 0);
        lootContainer(BlocksTC.lootUrnUncommon, false, 1);
        lootContainer(BlocksTC.lootUrnRare, false, 2);
    }

    /** Exact original model elements; the narrower urn collision is intentional TC6 behavior. */
    private void lootContainer(Block block, boolean crate, int rarity) {
        String prefix = crate ? "loot_crate" : "loot_urn";
        BlockModelBuilder model = models().getBuilder(name(block))
            .texture("side", tex(prefix + "_side_" + rarity))
            .texture("top", tex(prefix + "_top"))
            .texture("bottom", tex(prefix + "_top"))
            .texture("particle", tex(prefix + "_side_" + rarity));
        if (crate) {
            lootElement(model, 1, 0, 1, 15, 14, 15, true, false);
        } else {
            lootElement(model, 3, 0, 3, 13, 1, 13, true, false);
            lootElement(model, 2, 1, 2, 14, 13, 14, false, false);
            lootElement(model, 4, 13, 4, 12, 16, 12, false, true);
        }
        simpleBlockWithItem(block, model);
    }

    private void lootElement(BlockModelBuilder model, int x1, int y1, int z1, int x2, int y2, int z2,
                             boolean cullBottom, boolean cullTop) {
        model.element().from(x1, y1, z1).to(x2, y2, z2).allFaces((direction, face) -> {
            face.texture(direction == Direction.UP ? "#top" : direction == Direction.DOWN ? "#bottom" : "#side");
            if (direction == Direction.DOWN && cullBottom || direction == Direction.UP && cullTop) {
                face.cullface(direction);
            }
        }).end();
    }

    private ModelFile existing(String model) {
        return models().getExistingFile(modLoc("block/" + model));
    }

    private ModelFile cubeTex(Block block, String texture) {
        ModelFile m = models().cubeAll(name(block), tex(texture));
        simpleBlockWithItem(block, m);
        return m;
    }

    /** Random model rotations as in TC6's blockstate (4 variants: x/y by 90; 16 variants: all x/y). */
    private void rotated(Block block, ModelFile model, boolean all) {
        java.util.List<ConfiguredModel> variants = new ArrayList<>();
        if (all) {
            for (int y = 0; y < 360; y += 90) {
                for (int x = 0; x < 360; x += 90) {
                    variants.add(new ConfiguredModel(model, x, y, false));
                }
            }
        } else {
            variants.add(new ConfiguredModel(model));
            variants.add(new ConfiguredModel(model, 90, 0, false));
            variants.add(new ConfiguredModel(model, 0, 90, false));
            variants.add(new ConfiguredModel(model, 90, 90, false));
        }
        getVariantBuilder(block).partialState().setModels(variants.toArray(new ConfiguredModel[0]));
        simpleBlockItem(block, model);
    }

    private void randomTextures(Block block, String... textures) {
        ConfiguredModel[] variants = new ConfiguredModel[textures.length];
        for (int i = 0; i < textures.length; i++) {
            variants[i] = new ConfiguredModel(models().cubeAll(name(block) + "_" + i, tex(textures[i])));
        }
        getVariantBuilder(block).partialState().setModels(variants);
        simpleBlockItem(block, variants[1].model);
    }

    private void stairs(Block block, String side, String bottom, String top) {
        stairsBlock((StairBlock) block, tex(side), tex(bottom), tex(top));
        simpleBlockItem(block, models().getExistingFile(modLoc("block/" + name(block))));
    }

    private void slab(Block block, ModelFile doubleModel, String side, String bottom, String top) {
        String n = name(block);
        ModelFile half = models().slab(n, tex(side), tex(bottom), tex(top));
        ModelFile upper = models().slabTop(n + "_top", tex(side), tex(bottom), tex(top));
        slabBlock((SlabBlock) block, half, upper, doubleModel);
        simpleBlockItem(block, half);
    }

    private void grassAmbient() {
        ModelFile grass = models().getExistingFile(mcLoc("block/grass_block"));
        ModelFile snow = models().getExistingFile(mcLoc("block/grass_block_snow"));
        getVariantBuilder(BlocksTC.grassAmbient)
            .partialState().with(GrassBlock.SNOWY, false).setModels(
                new ConfiguredModel(grass), new ConfiguredModel(grass, 0, 90, false),
                new ConfiguredModel(grass, 0, 180, false), new ConfiguredModel(grass, 0, 270, false))
            .partialState().with(GrassBlock.SNOWY, true).setModels(new ConfiguredModel(snow));
        simpleBlockItem(BlocksTC.grassAmbient, grass);
    }

    /** Pillar: OBJ from TC6 (models/block/pillar.obj), the texture is set by the model. North without rotation, as in TC6. */
    private void pillar(Block block, String texture) {
        String n = name(block);
        BlockModelBuilder m = models().getBuilder(n)
            .texture("texture", tex(texture))
            .texture("particle", tex(texture));
        m.customLoader(ObjModelBuilder::begin)
            .modelLocation(modLoc("models/block/pillar.obj"))
            .flipV(true)
            .automaticCulling(false)
            .end();
        horizontalBlock(block, m, 180);
        itemModels().withExistingParent(n, mcLoc("block/block"))
            .texture("texture", tex(texture))
            .texture("particle", tex(texture))
            .customLoader(ObjModelBuilder::begin)
            .modelLocation(modLoc("models/block/pillar.obj"))
            .flipV(true)
            .automaticCulling(false)
            .end();
    }

    private void cube(Block block) {
        simpleBlockWithItem(block, cubeAll(block));
    }

    private void log(Block block) {
        String n = name(block);
        logBlock((RotatedPillarBlock) block);
        simpleBlockItem(block, models().getExistingFile(modLoc("block/" + n)));
    }

    private void leaves(Block block) {
        String n = name(block);
        simpleBlockWithItem(block, models().withExistingParent(n, mcLoc("block/leaves"))
            .texture("all", tex(n)).renderType("cutout_mipped"));
    }

    private void cross(Block block) {
        String n = name(block);
        simpleBlock(block, models().cross(n, tex(n)).renderType("cutout"));
        itemModels().withExistingParent(n, mcLoc("item/generated")).texture("layer0", tex(n));
    }

    /**
     * Replacement for TC6's CrystalModel: for each support face, size+1 random shards from crystal.obj,
     * turned towards the support. TC6's randomness (shuffle by the position seed) is replaced by weighted multipart variants.
     */
    private void crystals() {
        ResourceLocation obj = modLoc("models/obj/crystal.obj");
        ResourceLocation crystalTex = tex("crystal");
        ModelFile[][] models = new ModelFile[4][CRYSTAL_VARIANTS];
        Random random = new Random(0x7C0FFEEL);
        for (int size = 0; size < 4; size++) {
            for (int v = 0; v < CRYSTAL_VARIANTS; v++) {
                List<String> shuffled = new ArrayList<>(SHARDS);
                Collections.shuffle(shuffled, random);
                List<String> visible = shuffled.subList(0, size + 1);
                BlockModelBuilder b = models().getBuilder("block/crystal/size" + size + "_" + v)
                    .texture("crystal", crystalTex)
                    .texture("particle", crystalTex)
                    .renderType("translucent");
                var loader = b.customLoader(ObjModelBuilder::begin)
                    .modelLocation(obj)
                    .flipV(true)
                    .automaticCulling(false);
                for (String shard : SHARDS) {
                    loader.visibility(shard, visible.contains(shard));
                }
                models[size][v] = loader.end();
            }
        }

        for (BlockCrystal crystal : ConfigBlocks.CRYSTALS) {
            MultiPartBlockStateBuilder mp = getMultipartBuilder(crystal);
            for (Direction face : Direction.values()) {
                int rx = switch (face) {
                    case DOWN -> 0;
                    case UP -> 180;
                    default -> 90;
                };
                int ry = switch (face) {
                    case NORTH -> 180;
                    case WEST -> 90;
                    case EAST -> 270;
                    default -> 0;
                };
                for (int size = 0; size < 4; size++) {
                    ConfiguredModel.Builder<MultiPartBlockStateBuilder.PartBuilder> part = mp.part();
                    for (int v = 0; v < CRYSTAL_VARIANTS; v++) {
                        if (v > 0) {
                            part = part.nextModel();
                        }
                        part = part.modelFile(models[size][v]).rotationX(rx).rotationY(ry);
                    }
                    part.addModel()
                        .condition(BlockCrystal.FACES.get(face), true)
                        .condition(BlockCrystal.SIZE, size)
                        .end();
                }
            }

            // Item: a full cluster of all shards on the bottom face
            ItemModelBuilder item = itemModels().withExistingParent(name(crystal), mcLoc("block/block"))
                .texture("crystal", crystalTex)
                .texture("particle", crystalTex)
                .renderType("translucent");
            item.customLoader(ObjModelBuilder::begin)
                .modelLocation(obj)
                .flipV(true)
                .automaticCulling(false)
                .end();
        }
    }
}
