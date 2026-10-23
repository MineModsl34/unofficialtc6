package thaumcraft.common.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ScanBlock;
import thaumcraft.api.research.ScanEntity;
import thaumcraft.api.research.ScanItem;
import thaumcraft.api.research.ScanOreDictionary;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.common.lib.research.ScanGeneric;
import thaumcraft.common.lib.research.ScanPotion;
import thaumcraft.common.lib.research.ScanSky;
import thaumcraft.common.lib.research.LegacyEnchantmentScanNames;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;
import thaumcraft.common.entities.monster.EntityThaumicSlime;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.entities.monster.EntityFireBat;
import thaumcraft.common.entities.monster.EntityPech;
import thaumcraft.common.entities.monster.EntityBrainyZombie;
import thaumcraft.common.entities.monster.EntityEldritchCrab;
import thaumcraft.common.entities.monster.EntityInhabitedZombie;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import thaumcraft.common.entities.monster.tainted.EntityTaintSwarm;
import thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.lib.research.ScanEnchantment;

/**
 * Category/resource metadata slice from TC6 ConfigResearch.java:114-240.
 * Calling initCategories installs ONLY seven empty categories, not research entries, scanning,
 * theorycraft, progression or a JSON loader. Startup registers this metadata; the separate server
 * reload lifecycle installs the registered JSON entries atomically after successful resource load.
 */
public final class ConfigResearch {
    /** Original public category array; its order differs from initCategories' insertion order. */
    public static String[] TCCategories = {"BASICS", "ALCHEMY", "AUROMANCY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"};
    private static final ResourceLocation BACK_OVER = tc("textures/gui/gui_research_back_over.png");

    /** Default source registration order, with no .json suffix (the loader adds it separately). */
    public static final List<ResourceLocation> BUILTIN_RESEARCH_LOCATIONS = List.of(
        tc("research/basics"), tc("research/alchemy"), tc("research/auromancy"), tc("research/artifice"),
        tc("research/infusion"), tc("research/golemancy"), tc("research/eldritch"), tc("research/scans"));

    private ConfigResearch() {}

    private static final Object ENCHANTMENT_SCANS = new Object();
    /** Addons can retain opaque historical scan names without relying on translated text. */
    public static final Map<ResourceKey<Enchantment>, String> enchantmentScanNames = new java.util.concurrent.ConcurrentHashMap<>();

    /** Dynamic registry values are used only to resolve names; scanners keep resource keys. */
    public static void refreshEnchantmentScans(RegistryAccess access) {
        access.registry(Registries.ENCHANTMENT).ifPresent(registry -> {
            ArrayList<IScanThing> scanners = new ArrayList<>();
            java.util.Set<ResourceKey<Enchantment>> sourceKeys = new java.util.HashSet<>();
            // Preserve the reviewed source order even though the dynamic 1.21 registry has a
            // different order and extra enchantments. Missing datapack entries stay absent.
            for (var source : LegacyEnchantmentScanNames.VANILLA) {
                sourceKeys.add(source.target());
                if (registry.getHolder(source.target()).isPresent())
                    scanners.add(new ScanEnchantment(source.target(), enchantmentScanNames.getOrDefault(source.target(), source.legacyName())));
            }
            registry.holders().forEach(holder -> {
                ResourceKey<Enchantment> key = holder.key();
                if (sourceKeys.contains(key)) return;
                String name = enchantmentScanNames.get(key);
                if (name == null) {
                    name = holder.value().description().getContents() instanceof TranslatableContents translated
                            ? translated.getKey()
                            : "enchantment." + key.location().getNamespace() + "." + key.location().getPath().replace('/', '.');
                }
                scanners.add(new ScanEnchantment(key, name));
            });
            ScanningManager.replaceScannableGroup(ENCHANTMENT_SCANS, scanners);
        });
    }

    /** Original default registration sequence, also exposed as an explicit injection bridge. */
    public static void registerResourceLocations(Consumer<ResourceLocation> registrar) {
        BUILTIN_RESEARCH_LOCATIONS.forEach(registrar);
    }

    /**
     * Original initScannables registrations, in their original relative order.
     * Aspect handlers register in Aspect's constructor, including late addon aspects.
     * Called once after registries/common setup, not on each datapack reload.
     * Dynamic enchantments refresh with server/client registry tags, preserving legacy vanilla names.
     */
    public static void initScannables() {
        ScanningManager.addScannableThing(new ScanGeneric());
        ScanningManager.replaceScannableGroup(ENCHANTMENT_SCANS,
            LegacyEnchantmentScanNames.VANILLA.stream().map(entry -> entry.scanner()).toList());
        BuiltInRegistries.MOB_EFFECT.holders().forEach(effect -> ScanningManager.addScannableThing(new ScanPotion(effect)));
        ScanningManager.addScannableThing(new ScanEntity("!Wisp", EntityWisp.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!ThaumSlime", EntityThaumicSlime.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!Firebat", EntityFireBat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!Pech", EntityPech.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!BrainyZombie", EntityBrainyZombie.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!EldritchCrab", EntityEldritchCrab.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!EldritchCrab", EntityInhabitedZombie.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!CrimsonCultist", EntityCultist.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!EldritchGuardian", EntityEldritchGuardian.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!TaintCrawler", EntityTaintCrawler.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!Taintacle", EntityTaintacle.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!TaintSeed", EntityTaintSeed.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!TaintSwarm", EntityTaintSwarm.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_toomuchflux", EntityFluxRift.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!FluxRift", EntityFluxRift.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_golem", EntityThaumcraftGolem.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_golem", EntityOwnedConstruct.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_SPIDER", Spider.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_BAT", Bat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_BAT", EntityFireBat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Bat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Parrot.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", EntityFireBat.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", EntityTaintSwarm.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", EntityWisp.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Ghast.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_FLY", Blaze.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!ORMOB", IEldritchMob.class, true));
        ScanningManager.addScannableThing(new ScanEntity("!ORBOSS", EntityThaumcraftBoss.class, true));
        scanBlock("!ORBLOCK1", BlocksTC.stoneAncient, BlocksTC.stoneAncientTile);
        scanBlock("!ORBLOCK2", BlocksTC.stoneEldritchTile);
        scanBlock("!ORBLOCK3", BlocksTC.stoneAncientGlyphed);
        scanBlock("ORE", BlocksTC.oreAmber, BlocksTC.oreCinnabar,
            BlocksTC.crystalAir, BlocksTC.crystalFire, BlocksTC.crystalWater, BlocksTC.crystalEarth,
            BlocksTC.crystalOrder, BlocksTC.crystalEntropy, BlocksTC.crystalTaint);
        scanBlock("!OREAMBER", BlocksTC.oreAmber);
        scanBlock("!ORECINNABAR", BlocksTC.oreCinnabar);
        scanBlock("!ORECRYSTAL", BlocksTC.crystalAir, BlocksTC.crystalFire, BlocksTC.crystalWater,
            BlocksTC.crystalEarth, BlocksTC.crystalOrder, BlocksTC.crystalEntropy, BlocksTC.crystalTaint);
        scanBlock("PLANTS", BlocksTC.logGreatwood, BlocksTC.logSilverwood,
            BlocksTC.saplingGreatwood, BlocksTC.saplingSilverwood,
            BlocksTC.cinderpearl, BlocksTC.shimmerleaf, BlocksTC.vishroom);
        scanBlock("!PLANTWOOD", BlocksTC.logGreatwood);
        scanBlock("!PLANTWOOD", BlocksTC.logSilverwood);
        scanBlock("!PLANTWOOD", BlocksTC.saplingGreatwood);
        scanBlock("!PLANTWOOD", BlocksTC.saplingSilverwood);
        scanBlock("!PLANTCINDERPEARL", BlocksTC.cinderpearl);
        scanBlock("!PLANTSHIMMERLEAF", BlocksTC.shimmerleaf);
        scanBlock("!PLANTVISHROOM", BlocksTC.vishroom);
        ScanningManager.addScannableThing(new ScanItem("PRIMPEARL", ScanItem.wildcard(new ItemStack(ItemsTC.primordialPearl))));
        scanItem("!DRAGONBREATH", new ItemStack(Items.DRAGON_BREATH));
        scanItem("!TOTEMUNDYING", new ItemStack(Items.TOTEM_OF_UNDYING));
        scanBlock("f_TELEPORT", Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME);
        scanItem("f_TELEPORT", new ItemStack(Items.ENDER_PEARL));
        ScanningManager.addScannableThing(new ScanEntity("f_TELEPORT", EnderMan.class, true));
        scanItem("f_BRAIN", new ItemStack(ItemsTC.brain));
        scanBlock("f_DISPENSER", Blocks.DISPENSER);
        scanItem("f_DISPENSER", new ItemStack(Blocks.DISPENSER));
        scanItem("f_MATCLAY", new ItemStack(Items.CLAY_BALL));
        // Original one metadata block created ONLY a meta-zero (white) ItemScan. Placed
        // terracotta of every color matched the block handler; colored item stacks did not.
        Block[] terracotta = {Blocks.TERRACOTTA,
            Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA,
            Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIME_TERRACOTTA,
            Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA,
            Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.BLUE_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA};
        ScanningManager.addScannableThing(new ScanBlock("f_MATCLAY", Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA) {
            @Override public boolean checkThing(Player player, Object object) {
                return matchesBlock(player, object, terracotta);
            }
        });
        // Exact vanilla Material.CLAY replacement is block-only, never an extra clay-item scan.
        ScanningManager.addScannableThing(new IScanThing() {
            @Override public boolean checkThing(Player player, Object object) {
                return matchesBlock(player, object, Blocks.CLAY);
            }
            @Override public String getResearchKey(Player player, Object object) { return "f_MATCLAY"; }
        });
        ScanningManager.addScannableThing(new ScanOreDictionary("f_MATIRON", "oreIron", "ingotIron", "blockIron", "plateIron"));
        ScanningManager.addScannableThing(new ScanOreDictionary("f_MATBRASS", "ingotBrass", "blockBrass", "plateBrass"));
        ScanningManager.addScannableThing(new ScanOreDictionary("f_MATTHAUMIUM", "ingotThaumium", "blockThaumium", "plateThaumium"));
        ScanningManager.addScannableThing(new ScanOreDictionary("f_MATVOID", "ingotVoid", "blockVoid", "plateVoid"));
        ScanningManager.addScannableThing(new ScanEntity("f_arrow", Arrow.class, true));
        // Their old subclasses are separate modern hierarchies; do not include new Trident.
        ScanningManager.addScannableThing(new ScanEntity("f_arrow", SpectralArrow.class, true));
        scanItem("f_arrow", new ItemStack(Items.ARROW));
        ScanningManager.addScannableThing(new ScanEntity("f_fireball", Fireball.class, true));
        // Same source EntityFireball family, without widening to modern wind charges.
        ScanningManager.addScannableThing(new ScanEntity("f_fireball", DragonFireball.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_fireball", WitherSkull.class, true));
        ScanningManager.addScannableThing(new ScanEntity("f_spit", LlamaSpit.class, true));
        ScanningManager.addScannableThing(new ScanSky());
        scanItem("f_VOIDSEED", new ItemStack(ItemsTC.voidSeed));
    }

    /**
     * TC6 ConfigResearch:417-470, called after warp/queued knowledge sync every 200 player ticks.
     * BiomeDictionary NETHER/END become NeoForge biome-type tags, not dimension-id shortcuts
     * or vanilla structure tags (the source Eldritch biome has END type outside the End dimension).
     * The source compares absolute Y: deep remains Y&lt;10 even with modern negative minimum Y.
     * DimensionType.logicalHeight replaces WorldProvider.actualHeight (Nether still 128, End 256,
     * modern Overworld 384); no minY offset is added to the original height-times-.4 formula.
     * MC1.12 EntityPlayer:1852-1858's horizontal in-water SWIM_ONE_CM became modern
     * ServerPlayer:1294-1297 WALK_ON_WATER_ONE_CM. Modern SWIM_ONE_CM instead counts the new
     * 3D sprint-swimming branch and is deliberately NOT substituted or combined with it.
     */
    public static void checkPeriodicStuff(ServerPlayer player) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        var biome = player.level().getBiome(player.blockPosition());
        if (biome.is(Tags.Biomes.IS_NETHER)) periodicMarker(player, "m_hellandback", "got.hellandback");
        if (biome.is(Tags.Biomes.IS_END)) periodicMarker(player, "m_endoftheworld", "got.endoftheworld");
        if (knowledge.isResearchKnown("UNLOCKAUROMANCY@1") && !knowledge.isResearchKnown("UNLOCKAUROMANCY@2")) {
            if (player.getY() < 10.0D) periodicMarker(player, "m_deepdown", "got.deepdown");
            if (player.getY() > player.level().dimensionType().logicalHeight() * 0.4D)
                periodicMarker(player, "m_uphigh", "got.uphigh");
        }
        var stats = player.getStats();
        if (stats != null) {
            if (stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) > 160000) periodicMarker(player, "m_walker", null);
            if (stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)) > 80000) periodicMarker(player, "m_runner", null);
            if (stats.getValue(Stats.CUSTOM.get(Stats.JUMP)) > 500) periodicMarker(player, "m_jumper", null);
            if (stats.getValue(Stats.CUSTOM.get(Stats.WALK_ON_WATER_ONE_CM)) > 8000) periodicMarker(player, "m_swimmer", null);
        }
    }

    private static void periodicMarker(ServerPlayer owner, String marker, String actionBarTranslation) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        if (knowledge.isResearchKnown(marker)) return;
        // Source direct known0 mutation: no ResearchManager progression, completion XP or flags.
        knowledge.addResearch(marker);
        knowledge.sync(owner);
        if (actionBarTranslation != null)
            owner.displayClientMessage(Component.translatable(actionBarTranslation).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static void scanBlock(String research, Block... blocks) {
        ScanningManager.addScannableThing(new ScanBlock(research, blocks));
    }

    private static void scanItem(String research, ItemStack stack) {
        ScanningManager.addScannableThing(new ScanItem(research, stack));
    }

    private static boolean matchesBlock(Player player, Object object, Block... blocks) {
        if (!(object instanceof BlockPos pos)) return false;
        Block actual = player.level().getBlockState(pos).getBlock();
        for (Block block : blocks) if (block == actual) return true;
        return false;
    }

    /** Source registration sequence: BASICS, AUROMANCY, ALCHEMY, ARTIFICE, INFUSION, GOLEMANCY, ELDRITCH. */
    public static void initCategories() {
        ResearchCategories.registerCategory("BASICS", null,
            new AspectList().add(Aspect.PLANT, 5).add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5)
                .add(Aspect.AIR, 5).add(Aspect.FIRE, 5).add(Aspect.EARTH, 3).add(Aspect.WATER, 5),
            tc("textures/items/thaumonomicon_cheat.png"), tc("textures/gui/gui_research_back_1.jpg"), BACK_OVER);
        ResearchCategories.registerCategory("AUROMANCY", "UNLOCKAUROMANCY",
            new AspectList().add(Aspect.AURA, 20).add(Aspect.MAGIC, 20).add(Aspect.FLUX, 15)
                .add(Aspect.CRYSTAL, 5).add(Aspect.COLD, 5).add(Aspect.AIR, 5),
            tc("textures/research/cat_auromancy.png"), tc("textures/gui/gui_research_back_2.jpg"), BACK_OVER);
        ResearchCategories.registerCategory("ALCHEMY", "UNLOCKALCHEMY",
            new AspectList().add(Aspect.ALCHEMY, 30).add(Aspect.FLUX, 10).add(Aspect.MAGIC, 10)
                .add(Aspect.LIFE, 5).add(Aspect.AVERSION, 5).add(Aspect.DESIRE, 5).add(Aspect.WATER, 5),
            tc("textures/research/cat_alchemy.png"), tc("textures/gui/gui_research_back_3.jpg"), BACK_OVER);
        ResearchCategories.registerCategory("ARTIFICE", "UNLOCKARTIFICE",
            new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.CRAFT, 10).add(Aspect.METAL, 10)
                .add(Aspect.TOOL, 10).add(Aspect.ENERGY, 10).add(Aspect.LIGHT, 5).add(Aspect.FLIGHT, 5)
                .add(Aspect.TRAP, 5).add(Aspect.FIRE, 5),
            tc("textures/research/cat_artifice.png"), tc("textures/gui/gui_research_back_4.jpg"), BACK_OVER);
        ResearchCategories.registerCategory("INFUSION", "UNLOCKINFUSION",
            new AspectList().add(Aspect.MAGIC, 30).add(Aspect.PROTECT, 10).add(Aspect.TOOL, 10)
                .add(Aspect.FLUX, 5).add(Aspect.CRAFT, 5).add(Aspect.SOUL, 5).add(Aspect.EARTH, 3),
            tc("textures/research/cat_infusion.png"), tc("textures/gui/gui_research_back_7.jpg"), BACK_OVER);
        ResearchCategories.registerCategory("GOLEMANCY", "UNLOCKGOLEMANCY",
            new AspectList().add(Aspect.MAN, 20).add(Aspect.MOTION, 10).add(Aspect.MIND, 10)
                .add(Aspect.MECHANISM, 10).add(Aspect.EXCHANGE, 5).add(Aspect.SENSES, 5).add(Aspect.BEAST, 5)
                .add(Aspect.ORDER, 5),
            tc("textures/research/cat_golemancy.png"), tc("textures/gui/gui_research_back_5.jpg"), BACK_OVER);
        ResearchCategories.registerCategory("ELDRITCH", "UNLOCKELDRITCH",
            new AspectList().add(Aspect.ELDRITCH, 20).add(Aspect.DARKNESS, 10).add(Aspect.MAGIC, 5)
                .add(Aspect.MIND, 5).add(Aspect.VOID, 5).add(Aspect.DEATH, 5).add(Aspect.UNDEAD, 5)
                .add(Aspect.ENTROPY, 5),
            tc("textures/research/cat_eldritch.png"), tc("textures/gui/gui_research_back_6.jpg"), BACK_OVER);
        thaumcraft.api.research.theorycraft.TheorycraftManager.initialize();
    }

    private static ResourceLocation tc(String path) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", path);
    }
}
