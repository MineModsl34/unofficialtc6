package thaumcraft.api.items;

import net.minecraft.world.item.Item;

/**
 * Fields bind registered native items. eldritchEye/runedTablet/creativePlacer were unregistered in BETA26.
 *
 * 1.12.2 multi-variant items (meta) are split into separate ones (id = base_variant):
 * ingots, nuggets, clusters, plate, mind, modules -> the fields below. The other multi-variant ones (curio, lootBag,
 * primordialPearl, celestialNotes, baubles, chunks, phial, label and seals use native variants/components.
 */
public class ItemsTC {
    /** Old metadata family handles: native aliases denote their metadata-zero item.
     * Explicit variants or ThaumcraftApi.registerObjectTag(metadata[]) select the other flattened items. */
    @Deprecated public static Item ingots, nuggets, clusters, plate, mind, modules;
    public static Item thaumonomicon;
    public static Item curio;
    public static Item lootBag;
    public static Item primordialPearl;
    public static Item eldritchEye;
    public static Item runedTablet;
    public static Item pechWand;
    public static Item celestialNotes;
    /** Modern flattened variants in the original meta order 0..12; celestialNotes aliases sun. */
    public static final Item[] celestialNotesVariants = new Item[13];
    public static Item alumentum;
    public static Item amber;
    public static Item quicksilver;

    // ingots: thaumium, void, brass
    public static Item ingotThaumium;
    public static Item ingotVoid;
    public static Item ingotBrass;
    // nuggets: iron, copper, tin, silver, lead, quicksilver, thaumium, void, brass, quartz, rareearth
    public static Item nuggetIron;
    public static Item nuggetCopper;
    public static Item nuggetTin;
    public static Item nuggetSilver;
    public static Item nuggetLead;
    public static Item nuggetQuicksilver;
    public static Item nuggetThaumium;
    public static Item nuggetVoid;
    public static Item nuggetBrass;
    public static Item nuggetQuartz;
    public static Item nuggetRareEarth;
    // clusters: iron, gold, copper, tin, silver, lead, cinnabar, quartz
    public static Item clusterIron;
    public static Item clusterGold;
    public static Item clusterCopper;
    public static Item clusterTin;
    public static Item clusterSilver;
    public static Item clusterLead;
    public static Item clusterCinnabar;
    public static Item clusterQuartz;

    public static Item crystalEssence;
    public static Item tallow;
    public static Item fabric;
    public static Item mechanismSimple;
    public static Item mechanismComplex;
    // plate: brass, iron, thaumium, void
    public static Item plateBrass;
    public static Item plateIron;
    public static Item plateThaumium;
    public static Item plateVoid;
    public static Item voidSeed;
    public static Item salisMundus;
    public static Item mirroredGlass;
    public static Item filter;
    // mind: clockwork, biothaumic
    public static Item mindClockwork;
    public static Item mindBiothaumic;
    public static Item morphicResonator;
    // modules: vision, aggression
    public static Item moduleVision;
    public static Item moduleAggression;
    public static Item visResonator;
    public static Item chunks;
    public static Item tripleMeatTreat;
    public static Item brain;
    public static Item phial;
    public static Item label;
    public static Item bottleTaint;
    public static Item jarBrace;
    public static Item causalityCollapser;
    public static Item scribingTools;
    public static Item thaumometer;
    public static Item thaumiumAxe;
    public static Item thaumiumSword;
    public static Item thaumiumShovel;
    public static Item thaumiumPick;
    public static Item thaumiumHoe;
    public static Item elementalAxe;
    public static Item elementalSword;
    public static Item elementalShovel;
    public static Item elementalPick;
    public static Item elementalHoe;
    public static Item voidAxe;
    public static Item voidSword;
    public static Item voidShovel;
    public static Item voidPick;
    public static Item voidHoe;
    public static Item crimsonBlade;
    public static Item primalCrusher;
    public static Item sanityChecker;
    public static Item resonator;
    public static Item handMirror;
    public static Item grappleGun;
    public static Item grappleGunTip;
    public static Item grappleGunSpool;
    public static Item goggles;
    public static Item travellerBoots;
    public static Item thaumiumHelm;
    public static Item thaumiumChest;
    public static Item thaumiumLegs;
    public static Item thaumiumBoots;
    public static Item clothChest;
    public static Item clothLegs;
    public static Item clothBoots;
    public static Item fortressHelm;
    public static Item fortressChest;
    public static Item fortressLegs;
    public static Item voidHelm;
    public static Item voidChest;
    public static Item voidLegs;
    public static Item voidBoots;
    public static Item voidRobeHelm;
    public static Item voidRobeChest;
    public static Item voidRobeLegs;
    public static Item crimsonBoots;
    public static Item crimsonPlateHelm;
    public static Item crimsonPlateChest;
    public static Item crimsonPlateLegs;
    public static Item crimsonRobeHelm;
    public static Item crimsonRobeChest;
    public static Item crimsonRobeLegs;
    public static Item crimsonPraetorHelm;
    public static Item crimsonPraetorChest;
    public static Item crimsonPraetorLegs;
    public static Item baubles;
    // Legacy baubles metadata 0..6, flattened to seven registry entries.
    public static Item amuletMundane;
    public static Item ringMundane;
    public static Item girdleMundane;
    public static Item ringApprentice;
    public static Item amuletFancy;
    public static Item ringFancy;
    public static Item girdleFancy;
    public static Item amuletVis;
    /** Modern split of source amulet_vis metadata 1, not the found stone amulet. */
    public static Item amuletVisCrafted;
    public static Item charmVerdant;
    public static Item charmVoidseer;
    public static Item bandCuriosity;
    public static Item ringCloud;
    public static Item charmUndying;
    public static Item creativePlacer;
    public static Item creativeFluxSponge;
    public static Item bathSalts;
    public static Item sanitySoap;
    public static Item turretPlacer;
    public static Item enchantedPlaceholder;
    public static Item casterBasic;
    public static Item focus1;
    public static Item focus2;
    public static Item focus3;
    public static Item focusPouch;
    public static Item golemBell;
    public static Item golemPlacer;
    public static Item seals;
}
