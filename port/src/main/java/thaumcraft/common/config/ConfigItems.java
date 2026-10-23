package thaumcraft.common.config;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.items.curios.ItemPrimordialPearl;
import thaumcraft.common.items.baubles.ItemAmuletVis;
import thaumcraft.common.items.baubles.ItemBaubles;
import thaumcraft.common.items.armor.ItemThaumiumArmor;
import thaumcraft.common.items.armor.ItemVoidArmor;
import thaumcraft.common.items.tools.ItemThaumiumSword;
import thaumcraft.common.items.tools.ItemVoidSword;
import thaumcraft.common.items.tools.ItemThaumometer;
import thaumcraft.common.items.tools.ItemScribingTools;
import thaumcraft.common.items.curios.ItemCelestialNotes;
import thaumcraft.common.items.curios.ItemThaumonomicon;
import thaumcraft.common.items.resources.ItemMagicDust;

public class ConfigItems {
    /**
     * Simple items without behaviour and the texture of each (if it differs from the id). Datagen needs it for the models.
     * In TC6 these were ItemTCBase with variants.
     */
    public static final Map<Item, String> SIMPLE_ITEM_TEXTURES = new LinkedHashMap<>();
    public static final Map<Item, String> GEAR_ITEM_TEXTURES = new LinkedHashMap<>();

    private static RegisterEvent.RegisterHelper<Item> registry;

    private static Item simple(String id) {
        return simple(id, id);
    }

    private static Item simple(String id, String texture) {
        Item item = new Item(new Item.Properties());
        register(id, item);
        SIMPLE_ITEM_TEXTURES.put(item, texture);
        return item;
    }

    private static <T extends Item> T register(String id, T item) {
        registry.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id), item);
        return item;
    }

    private static <T extends Item> T gear(String id, T item) {
        register(id, item);
        GEAR_ITEM_TEXTURES.put(item, id);
        return item;
    }

    public static void initItems(RegisterEvent.RegisterHelper<Item> iForgeRegistry) {
        registry = iForgeRegistry;
        // Dedicated OBJ model; not a generated flat texture or a stage-5 crafting shortcut.
        ItemsTC.thaumometer = register("thaumometer", new ItemThaumometer(new Item.Properties()));
        ItemsTC.scribingTools = register("scribing_tools", new ItemScribingTools());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.scribingTools, "scribing_tools");
        // Normal acquisition first; cheat-sheet variant and browser/page UI remain separate.
        ItemsTC.thaumonomicon = register("thaumonomicon", new ItemThaumonomicon());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.thaumonomicon, "thaumonomicon");
        for (int meta = 0; meta < ItemsTC.celestialNotesVariants.length; meta++) {
            var note = new ItemCelestialNotes(meta);
            ItemsTC.celestialNotesVariants[meta] = register("celestial_notes_" + note.variantName(), note);
        }
        ItemsTC.celestialNotes = ItemsTC.celestialNotesVariants[0];
        for (int meta = 0; meta < thaumcraft.common.items.curios.ItemCurio.VARIANTS.size(); meta++) {
            String variant = thaumcraft.common.items.curios.ItemCurio.VARIANTS.get(meta);
            var curio = register("curio_" + variant, new thaumcraft.common.items.curios.ItemCurio(meta));
            SIMPLE_ITEM_TEXTURES.put(curio, meta == 6 ? "crimson_rites" : "curio_" + variant);
            if (meta == 0) ItemsTC.curio = curio;
        }
        ItemsTC.phial = register("phial_empty", new thaumcraft.common.items.consumables.ItemPhial(new Item.Properties(), false));
        var phialFilled = register("phial_filled", new thaumcraft.common.items.consumables.ItemPhial(new Item.Properties(), true));
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.phial, "phial");
        SIMPLE_ITEM_TEXTURES.put(phialFilled, "phial");
        ItemsTC.brain = register("brain", new thaumcraft.common.items.consumables.ItemZombieBrain());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.brain, "brain");
        ItemsTC.enchantedPlaceholder = register("enchanted_placeholder", new thaumcraft.common.items.curios.ItemEnchantmentPlaceholder());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.enchantedPlaceholder, "enchanted_placeholder");
        // Loot bags and source research-discovery pech wand are registered with equipment below.
        ItemsTC.amber = simple("amber");
        ItemsTC.quicksilver = simple("quicksilver");
        ItemsTC.ingotThaumium = simple("ingot_thaumium");
        ItemsTC.ingotVoid = simple("ingot_void");
        ItemsTC.ingotBrass = simple("ingot_brass");
        ItemsTC.nuggetIron = simple("nugget_iron");
        ItemsTC.nuggetCopper = simple("nugget_copper");
        ItemsTC.nuggetTin = simple("nugget_tin");
        ItemsTC.nuggetSilver = simple("nugget_silver");
        ItemsTC.nuggetLead = simple("nugget_lead");
        ItemsTC.nuggetQuicksilver = simple("nugget_quicksilver");
        ItemsTC.nuggetThaumium = simple("nugget_thaumium");
        ItemsTC.nuggetVoid = simple("nugget_void");
        ItemsTC.nuggetBrass = simple("nugget_brass");
        ItemsTC.nuggetQuartz = simple("nugget_quartz");
        ItemsTC.nuggetRareEarth = simple("nugget_rareearth", "rare_earth");
        ItemsTC.clusterIron = simple("cluster_iron");
        ItemsTC.clusterGold = simple("cluster_gold");
        ItemsTC.clusterCopper = simple("cluster_copper");
        ItemsTC.clusterTin = simple("cluster_tin");
        ItemsTC.clusterSilver = simple("cluster_silver");
        ItemsTC.clusterLead = simple("cluster_lead");
        ItemsTC.clusterCinnabar = simple("cluster_cinnabar");
        ItemsTC.clusterQuartz = simple("cluster_quartz");
        ItemsTC.fabric = simple("fabric");
        ItemsTC.visResonator = simple("vis_resonator");
        ItemsTC.tallow = simple("tallow");
        ItemsTC.mechanismSimple = simple("mechanism_simple");
        ItemsTC.mechanismComplex = simple("mechanism_complex");
        ItemsTC.plateBrass = simple("plate_brass");
        ItemsTC.plateIron = simple("plate_iron");
        ItemsTC.plateThaumium = simple("plate_thaumium");
        ItemsTC.plateVoid = simple("plate_void");
        ItemsTC.filter = simple("filter");
        ItemsTC.morphicResonator = simple("morphic_resonator");
        // Narrow stage-4 dependency: only source bookshelf -> book, not all dust triggers.
        ItemsTC.salisMundus = register("salis_mundus", new ItemMagicDust());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.salisMundus, "salis_mundus");
        ItemsTC.mirroredGlass = simple("mirrored_glass");
        ItemsTC.voidSeed = simple("void_seed");
        ItemsTC.mindClockwork = simple("mind_clockwork");
        ItemsTC.mindBiothaumic = simple("mind_biothaumic");
        ItemsTC.moduleVision = simple("module_vision");
        ItemsTC.moduleAggression = simple("module_aggression");
        ItemsTC.ingots = ItemsTC.ingotThaumium;
        ItemsTC.nuggets = ItemsTC.nuggetIron;
        ItemsTC.clusters = ItemsTC.clusterIron;
        ItemsTC.plate = ItemsTC.plateBrass;
        ItemsTC.mind = ItemsTC.mindClockwork;
        ItemsTC.modules = ItemsTC.moduleVision;
        ItemsTC.crystalEssence = register("crystal_essence", new ItemCrystalEssence());
        ItemsTC.alumentum = register("alumentum", new thaumcraft.common.items.consumables.ItemAlumentum(new Item.Properties()));
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.alumentum,"alumentum");
        ItemsTC.casterBasic = register("caster_basic", new thaumcraft.common.items.casters.ItemCaster(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON),0));
        ItemsTC.label = register("label", new thaumcraft.common.items.consumables.ItemLabel(new Item.Properties()));
        ItemsTC.resonator = register("resonator", new thaumcraft.common.items.tools.ItemResonator(new Item.Properties()));
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.resonator,"resonator");
        ItemsTC.handMirror = register("hand_mirror",new thaumcraft.common.items.tools.ItemHandMirror());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.handMirror,"hand_mirror");
        for(String variant:thaumcraft.common.items.consumables.ItemChunksEdible.VARIANTS){
            Item chunk=register("chunk_"+variant,new thaumcraft.common.items.consumables.ItemChunksEdible());
            SIMPLE_ITEM_TEXTURES.put(chunk,"chunk_"+variant);if(variant.equals("beef"))ItemsTC.chunks=chunk;
        }
        ItemsTC.tripleMeatTreat=register("triple_meat_treat",new thaumcraft.common.items.consumables.ItemTripleMeatTreat());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.tripleMeatTreat,"tripletreat");
        ItemsTC.bathSalts=register("bath_salts",new thaumcraft.common.items.consumables.ItemBathSalts());
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.bathSalts,"bath_salts");
        thaumcraft.common.blocks.misc.PurifyingFluidRegistries.registerItems(iForgeRegistry);
        SIMPLE_ITEM_TEXTURES.put(ItemsTC.label,"label");
        ItemsTC.jarBrace = simple("jar_brace");
        ItemsTC.grappleGunTip = simple("grapple_gun_tip");
        ItemsTC.grappleGunSpool = simple("grapple_gun_spool");
        // Narrow stage-7 prerequisite: actual mound loot, not placeholder items.
        ItemsTC.primordialPearl = register("primordial_pearl", new ItemPrimordialPearl());
        ItemsTC.amuletVis = gear("amulet_vis", new ItemAmuletVis(false));
        GEAR_ITEM_TEXTURES.put(ItemsTC.amuletVis, "amulet_vis_stone");
        ItemsTC.amuletMundane = gear("amulet_mundane", new ItemBaubles(0));
        ItemsTC.ringMundane = gear("ring_mundane", new ItemBaubles(1));
        ItemsTC.girdleMundane = gear("girdle_mundane", new ItemBaubles(2));
        ItemsTC.ringApprentice = gear("ring_apprentice", new ItemBaubles(3));
        ItemsTC.amuletFancy = gear("amulet_fancy", new ItemBaubles(4));
        ItemsTC.ringFancy = gear("ring_fancy", new ItemBaubles(5));
        ItemsTC.girdleFancy = gear("girdle_fancy", new ItemBaubles(6));
        ItemsTC.baubles = ItemsTC.amuletMundane;
        ItemsTC.thaumiumSword = gear("thaumium_sword", new ItemThaumiumSword());
        ItemsTC.voidSword = gear("void_sword", new ItemVoidSword());
        ItemsTC.thaumiumHelm = gear("thaumium_helm", new ItemThaumiumArmor(ArmorItem.Type.HELMET));
        ItemsTC.thaumiumChest = gear("thaumium_chest", new ItemThaumiumArmor(ArmorItem.Type.CHESTPLATE));
        ItemsTC.thaumiumLegs = gear("thaumium_legs", new ItemThaumiumArmor(ArmorItem.Type.LEGGINGS));
        ItemsTC.thaumiumBoots = gear("thaumium_boots", new ItemThaumiumArmor(ArmorItem.Type.BOOTS));
        ItemsTC.voidHelm = gear("void_helm", new ItemVoidArmor(ArmorItem.Type.HELMET));
        ItemsTC.voidChest = gear("void_chest", new ItemVoidArmor(ArmorItem.Type.CHESTPLATE));
        ItemsTC.voidLegs = gear("void_legs", new ItemVoidArmor(ArmorItem.Type.LEGGINGS));
        ItemsTC.voidBoots = gear("void_boots", new ItemVoidArmor(ArmorItem.Type.BOOTS));
        ItemsTC.crimsonPlateHelm = gear("crimson_plate_helm", new thaumcraft.common.items.armor.ItemCultistPlateArmor(ArmorItem.Type.HELMET, thaumcraft.common.items.TCGearMaterials.CULTIST_PLATE));
        ItemsTC.crimsonPlateChest = gear("crimson_plate_chest", new thaumcraft.common.items.armor.ItemCultistPlateArmor(ArmorItem.Type.CHESTPLATE, thaumcraft.common.items.TCGearMaterials.CULTIST_PLATE));
        ItemsTC.crimsonPlateLegs = gear("crimson_plate_legs", new thaumcraft.common.items.armor.ItemCultistPlateArmor(ArmorItem.Type.LEGGINGS, thaumcraft.common.items.TCGearMaterials.CULTIST_PLATE));
        ItemsTC.crimsonRobeHelm = gear("crimson_robe_helm", new thaumcraft.common.items.armor.ItemCultistRobeArmor(ArmorItem.Type.HELMET, thaumcraft.common.items.TCGearMaterials.CULTIST_ROBE));
        ItemsTC.crimsonRobeChest = gear("crimson_robe_chest", new thaumcraft.common.items.armor.ItemCultistRobeArmor(ArmorItem.Type.CHESTPLATE, thaumcraft.common.items.TCGearMaterials.CULTIST_ROBE));
        ItemsTC.crimsonRobeLegs = gear("crimson_robe_legs", new thaumcraft.common.items.armor.ItemCultistRobeArmor(ArmorItem.Type.LEGGINGS, thaumcraft.common.items.TCGearMaterials.CULTIST_ROBE));
        ItemsTC.crimsonBoots = gear("crimson_boots", new thaumcraft.common.items.armor.ItemCultistBoots());
        thaumcraft.common.items.tools.EquipmentRegistries.createItems().forEach(ConfigItems::register);
        var wearables = thaumcraft.common.items.armor.WearableRegistries.createItems();
        wearables.forEach(ConfigItems::register);
        ItemsTC.clothChest = wearables.get("cloth_chest"); ItemsTC.clothLegs = wearables.get("cloth_legs"); ItemsTC.clothBoots = wearables.get("cloth_boots");
        ItemsTC.goggles = wearables.get("goggles"); ItemsTC.travellerBoots = wearables.get("traveller_boots");
        ItemsTC.fortressHelm = wearables.get("fortress_helm"); ItemsTC.fortressChest = wearables.get("fortress_chest"); ItemsTC.fortressLegs = wearables.get("fortress_legs");
        ItemsTC.voidRobeHelm = wearables.get("void_robe_helm"); ItemsTC.voidRobeChest = wearables.get("void_robe_chest"); ItemsTC.voidRobeLegs = wearables.get("void_robe_legs");
        ItemsTC.crimsonPraetorHelm = wearables.get("crimson_praetor_helm"); ItemsTC.crimsonPraetorChest = wearables.get("crimson_praetor_chest"); ItemsTC.crimsonPraetorLegs = wearables.get("crimson_praetor_legs");
        ItemsTC.amuletVisCrafted = wearables.get("amulet_vis_crafted"); ItemsTC.charmVerdant = wearables.get("verdant_charm"); ItemsTC.charmVoidseer = wearables.get("voidseer_charm");
        ItemsTC.bandCuriosity = wearables.get("curiosity_band"); ItemsTC.ringCloud = wearables.get("cloud_ring"); ItemsTC.charmUndying = wearables.get("charm_undying");
        thaumcraft.common.items.casters.CasterRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.entities.Mob8NaturalRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.entities.construct.Mob8ConstructRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.entities.Mob8MiscRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.entities.Mob8SpawnEggs.createItems().forEach(ConfigItems::register);
        thaumcraft.common.tiles.crafting.Golem9BuilderRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.golems.seals.Golem9SealRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.blocks.misc.DeathFluidRegistries.createItems().forEach(ConfigItems::register);
        thaumcraft.common.items.misc.Eldritch10ItemRegistries.createItems().forEach(ConfigItems::register);
        ItemsTC.sanitySoap = thaumcraft.common.items.misc.Eldritch10ItemRegistries.SOAP;
        ItemsTC.creativeFluxSponge = thaumcraft.common.items.misc.Eldritch10ItemRegistries.SPONGE;
    }
}
