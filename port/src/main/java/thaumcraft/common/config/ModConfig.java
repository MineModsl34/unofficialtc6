package thaumcraft.common.config;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.world.WorldgenPolicy;
import thaumcraft.common.world.WorldgenBlacklists;

/**
 * TC6 config on ModConfigSpec. Static fields in nested classes are kept so that ported code
 * accesses them as in 1.12.2 (ModConfig.CONFIG_WORLD.oreDensity); values are synced from the spec on
 * load and reload, like ConfigManager.sync in the original.
 *
 * Files: thaumcraft-client.toml (graphics), thaumcraft-server.toml (misc, in the world folder), thaumcraft-common.toml (world).
 * Removed: overworldDim and dimensionOuterId; 1.21 has no numeric dimension ids.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class ModConfig {
    public static final float auraSize = 4.0F;
    public static ArrayList<Aspect> aspectOrder = new ArrayList<>();
    public static boolean foundCopperIngot = false;
    public static boolean foundTinIngot = false;
    public static boolean foundSilverIngot = false;
    public static boolean foundLeadIngot = false;
    public static boolean foundCopperOre = false;
    public static boolean foundTinOre = false;
    public static boolean foundSilverOre = false;
    public static boolean foundLeadOre = false;
    public static boolean isHalloween;

    // Loot bags, crop defaults and tagged mining/smelting live in their native subsystem registries.
    public static void modCompatibility() {
        foundCopperIngot = hasMaterialTag("ingots/copper");
        foundTinIngot = hasMaterialTag("ingots/tin");
        foundSilverIngot = hasMaterialTag("ingots/silver");
        foundLeadIngot = hasMaterialTag("ingots/lead");
        foundCopperOre = hasMaterialTag("ores/copper");
        foundTinOre = hasMaterialTag("ores/tin");
        foundSilverOre = hasMaterialTag("ores/silver");
        foundLeadOre = hasMaterialTag("ores/lead");
    }

    private static boolean hasMaterialTag(String path) {
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", path));
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag)
            .map(items -> items.size() > 0).orElse(false);
    }

    /** Native seed registrations retain explicit addon definitions; cocoa becomes cocoa beans. */
    public static void postInitMisc() {
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item instanceof net.minecraft.world.item.ItemNameBlockItem seed) {
                var block = seed.getBlock();
                if (missingSeed(block)) thaumcraft.api.ThaumcraftApi.registerSeed(block, new net.minecraft.world.item.ItemStack(item));
            }
        }
        if (missingSeed(net.minecraft.world.level.block.Blocks.COCOA)) {
            thaumcraft.api.ThaumcraftApi.registerSeed(net.minecraft.world.level.block.Blocks.COCOA,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COCOA_BEANS));
        }
        for (Aspect aspect : Aspect.aspects.values()) if (!aspectOrder.contains(aspect)) aspectOrder.add(aspect);
    }

    private static boolean missingSeed(net.minecraft.world.level.block.Block block) {
        return thaumcraft.api.ThaumcraftApi.getSeed(block) == null;
    }

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec COMMON_SPEC;

    private static final GraphicsSpec GRAPHICS;
    private static final JeiSpec JEI;
    private static final MiscSpec MISC;
    private static final WorldSpec WORLD;

    static {
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        GRAPHICS = new GraphicsSpec(client);
        JEI = new JeiSpec(client);
        CLIENT_SPEC = client.build();
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        MISC = new MiscSpec(server);
        SERVER_SPEC = server.build();
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        WORLD = new WorldSpec(common);
        COMMON_SPEC = common.build();
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        sync(event.getConfig().getSpec());
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        sync(event.getConfig().getSpec());
    }

    private static void sync(Object spec) {
        if (spec == CLIENT_SPEC) {
            GRAPHICS.sync();
            JEI.sync();
        } else if (spec == SERVER_SPEC) {
            MISC.sync();
        } else if (spec == COMMON_SPEC) {
            WORLD.sync();
        }
    }

    public static class CONFIG_GRAPHICS {
        public static boolean largeTagText = false;
        public static boolean disableShaders = false;
        public static boolean nostress = false;
        public static boolean crooked = true;
        public static boolean dialBottom = false;
        public static boolean showTags = false;
        public static boolean blueBiome = false;
        public static boolean showGolemEmotes = true;
    }

    public static class CONFIG_JEI {
        public static volatile boolean hideRecipesIfMissingResearch = true;
    }

    private static final class JeiSpec {
        final ModConfigSpec.BooleanValue hideRecipesIfMissingResearch;
        JeiSpec(ModConfigSpec.Builder builder) {
            builder.push("jei");
            hideRecipesIfMissingResearch = builder.comment("Hide Thaumcraft JEI recipes until the required research is known.")
                .define("hideRecipesIfMissingResearch", true);
            builder.pop();
        }
        void sync() { CONFIG_JEI.hideRecipesIfMissingResearch = hideRecipesIfMissingResearch.get(); }
    }

    public static class CONFIG_MISC {
        public static boolean noSleep = false;
        public static boolean wussMode = false;
        public static boolean allowCheatSheet = false;
        public static int shieldRecharge = 2000;
        public static int shieldWait = 4000;
        public static int shieldCost = 1;
    }

    public static class CONFIG_WORLD {
        // These settings are also read by asynchronous chunk generation worker threads.
        public static volatile int oreDensity = 100;
        public static volatile WorldgenPolicy worldgenPolicy = WorldgenPolicy.EMPTY;
        public static boolean generateMagicForest = true;
        public static int biomeMagicalForestWeight = 5;
        public static float taintSpreadRate = 100.0F;
        public static int taintSpreadArea = 32;
        public static boolean generateAura = true;
        public static boolean generateStructure = true;
        public static volatile boolean generateCinnabar = true;
        public static volatile boolean generateAmber = true;
        public static volatile boolean generateQuartz = true;
        public static volatile boolean generateCrystals = true;
        public static volatile boolean generateTrees = true;
        public static String regenKey = "DEFAULT";
        public static boolean regenAura = false;
        public static boolean regenStructure = false;
        public static boolean regenCinnabar = false;
        public static boolean regenAmber = false;
        public static boolean regenQuartz = false;
        public static boolean regenCrystals = false;
        public static boolean regenTrees = false;
        public static boolean allowSpawnAngryZombie = true;
        public static boolean allowSpawnFireBat = true;
        public static boolean allowSpawnWisp = true;
        public static boolean allowSpawnTaintacle = true;
        public static boolean allowSpawnPech = true;
        public static boolean allowSpawnElder = true;
        public static boolean allowChampionMobs = true;
    }

    private static final class GraphicsSpec {
        final ModConfigSpec.BooleanValue largeTagText, disableShaders, nostress, crooked, dialBottom, showTags, blueBiome, showGolemEmotes;

        GraphicsSpec(ModConfigSpec.Builder b) {
            b.translation("thaumcraft.config.graphics").push("graphics");
            largeTagText = b.comment("Setting this to true will make the amount text in aspect tags twice as large. Useful for certain resolutions and custom fonts.")
                .define("largeTagText", false);
            disableShaders = b.comment("This setting will disable certain thaumcraft shaders for those who experience FPS drops.")
                .define("disableShaders", false);
            nostress = b.comment("Set to true to disable anxiety triggers like the heartbeat sound.").define("nostress", false);
            crooked = b.comment("Hate crooked labels, kittens, puppies and all things awesome? If yes, set this to false.").define("crooked", true);
            dialBottom = b.comment("Set to true to have the wand dial display in the bottom left instead of the top left.").define("dialBottom", false);
            showTags = b.comment(
                    "Item aspects are hidden by default and pressing shift reveals them.",
                    "Changing this setting to 'true' will reverse this behaviour and always",
                    "display aspects unless shift is pressed.")
                .define("showTags", false);
            blueBiome = b.comment("Set this to true to get the old blue magical forest back.").define("blueBiome", false);
            showGolemEmotes = b.comment("Will golems display emote particles if they recieve orders or encounter problems").define("showGolemEmotes", true);
            b.pop();
        }

        void sync() {
            CONFIG_GRAPHICS.largeTagText = largeTagText.get();
            CONFIG_GRAPHICS.disableShaders = disableShaders.get();
            CONFIG_GRAPHICS.nostress = nostress.get();
            CONFIG_GRAPHICS.crooked = crooked.get();
            CONFIG_GRAPHICS.dialBottom = dialBottom.get();
            CONFIG_GRAPHICS.showTags = showTags.get();
            CONFIG_GRAPHICS.blueBiome = blueBiome.get();
            CONFIG_GRAPHICS.showGolemEmotes = showGolemEmotes.get();
        }
    }

    private static final class MiscSpec {
        final ModConfigSpec.BooleanValue noSleep, wussMode, allowCheatSheet;
        final ModConfigSpec.IntValue shieldRecharge, shieldWait, shieldCost;

        MiscSpec(ModConfigSpec.Builder b) {
            b.translation("thaumcraft.config.misc").push("misc");
            noSleep = b.comment("Setting this to true will make you get the recipe book for salis mundus without having to sleep first.").define("noSleep", false);
            wussMode = b.comment("Setting this to true disables Warp, Taint spread and similar mechanics. You wuss.").define("wussMode", false);
            allowCheatSheet = b.comment("Enables a version of the Thauminomicon in creative mode that grants you all the research when you first use it.")
                .define("allowCheatSheet", false);
            shieldRecharge = b.comment("How many milliseconds passes between runic shielding recharge ticks.", "Lower values equals faster recharge. Minimum of 500.")
                .defineInRange("shieldRecharge", 2000, 500, 500000);
            shieldWait = b.comment("How many milliseconds passes after a shield has been reduced to zero", "before it can start recharging again. Minimum of 0.")
                .defineInRange("shieldWait", 4000, 0, 50000);
            shieldCost = b.comment("How much vis it costs to reacharge a single unit of shielding. Minimum of 0.").defineInRange("shieldCost", 1, 0, 500);
            b.pop();
        }

        void sync() {
            CONFIG_MISC.noSleep = noSleep.get();
            CONFIG_MISC.wussMode = wussMode.get();
            CONFIG_MISC.allowCheatSheet = allowCheatSheet.get();
            CONFIG_MISC.shieldRecharge = shieldRecharge.get();
            CONFIG_MISC.shieldWait = shieldWait.get();
            CONFIG_MISC.shieldCost = shieldCost.get();
        }
    }

    private static final class WorldSpec {
        final ModConfigSpec.IntValue oreDensity, biomeMagicalForestWeight, taintSpreadArea;
        final ModConfigSpec.DoubleValue taintSpreadRate;
        final ModConfigSpec.ConfigValue<String> regenKey;
        final ModConfigSpec.ConfigValue<List<? extends String>> dimensionBlacklist, biomeBlacklist;
        final ModConfigSpec.BooleanValue generateMagicForest, generateAura, generateStructure, generateCinnabar, generateAmber, generateQuartz,
            generateCrystals, generateTrees, regenAura, regenStructure, regenCinnabar, regenAmber, regenQuartz, regenCrystals, regenTrees,
            allowSpawnAngryZombie, allowSpawnFireBat, allowSpawnWisp, allowSpawnTaintacle, allowSpawnPech, allowSpawnElder, allowChampionMobs;

        WorldSpec(ModConfigSpec.Builder b) {
            b.translation("thaumcraft.config.world").push("world");
            dimensionBlacklist = b.comment(
                    "TC6 world-generation blacklist, registry-key adaptation of its numeric-ID IMC.",
                    "Entries: namespace:path=mode. 0/2 block ores; any mode except -1 blocks global vegetation and structures.",
                    "Aura generation checks the biome blacklist only, as in the original TC6 generateAura path.")
                .defineListAllowEmpty("dimensionBlacklist", List.<String>of(), () -> "minecraft:overworld=0", WorldgenPolicy::isValidEntry);
            biomeBlacklist = b.comment(
                    "Entries: namespace:path=mode. 0/2 block ores; any mode except -1 blocks global vegetation and new aura.",
                    "Biome-owned decoration is not disabled by TC6's global blacklist. Existing generated chunks are not rewritten.")
                .defineListAllowEmpty("biomeBlacklist", List.<String>of(), () -> "minecraft:plains=0", WorldgenPolicy::isValidEntry);
            oreDensity = b.comment("The % of normal ore amounts that will be spawned. For example 50 will spawn half", "the ores while 200 will spawn double. Default 100")
                .worldRestart().defineInRange("oreDensity", 100, 1, 500);
            generateMagicForest = b.worldRestart().define("generateMagicForest", true);
            biomeMagicalForestWeight = b.comment(
                    "Higher values increases number of magical forest biomes. If you are using biome",
                    "addon mods you probably want to increase this weight quite a bit")
                .worldRestart().defineInRange("biomeMagicalForestWeight", 5, 0, 100);
            taintSpreadRate = b.comment("The % chance of taint fibres spreading on a block tick.", "Setting this to 0 will effectively stop taint fibre spread.")
                .defineInRange("taintSpreadRate", 100.0, 0.0, 500.0);
            taintSpreadArea = b.comment("The range at which taint can spread from a taint seed.", "This value is only a base and will be modified by flux levels.")
                .defineInRange("taintSpreadArea", 32, 8, 256);
            generateAura = b.define("generateAura", true);
            generateStructure = b.define("generateStructure", true);
            generateCinnabar = b.define("generateCinnabar", true);
            generateAmber = b.define("generateAmber", true);
            generateQuartz = b.define("generateQuartz", true);
            generateCrystals = b.define("generateCrystals", true);
            generateTrees = b.define("generateTrees", true);
            regenKey = b.comment(
                    "This key is used to keep track of which chunk have been generated/regenerated.",
                    "Changing it will cause the regeneration code to run again, so only change it if you want it to happen.",
                    "Useful to regen only one world feature at a time.")
                .define("regenKey", "DEFAULT");
            regenAura = b.define("regenAura", false);
            regenStructure = b.define("regenStructure", false);
            regenCinnabar = b.define("regenCinnabar", false);
            regenAmber = b.define("regenAmber", false);
            regenQuartz = b.define("regenQuartz", false);
            regenCrystals = b.define("regenCrystals", false);
            regenTrees = b.define("regenTrees", false);
            allowSpawnAngryZombie = b.define("allowSpawnAngryZombie", true);
            allowSpawnFireBat = b.define("allowSpawnFireBat", true);
            allowSpawnWisp = b.define("allowSpawnWisp", true);
            allowSpawnTaintacle = b.define("allowSpawnTaintacle", true);
            allowSpawnPech = b.define("allowSpawnPech", true);
            allowSpawnElder = b.define("allowSpawnElder", true);
            allowChampionMobs = b.comment(
                    "Setting this to false will disable spawning champion mobs. Even when false they will still",
                    "have a greatly reduced chance of spawning in certain dangerous places.")
                .define("allowChampionMobs", true);
            b.pop();
        }

        void sync() {
            CONFIG_WORLD.oreDensity = oreDensity.get();
            WorldgenBlacklists.setConfig(WorldgenPolicy.fromEntries(dimensionBlacklist.get(), biomeBlacklist.get()));
            CONFIG_WORLD.generateMagicForest = generateMagicForest.get();
            CONFIG_WORLD.biomeMagicalForestWeight = biomeMagicalForestWeight.get();
            CONFIG_WORLD.taintSpreadRate = taintSpreadRate.get().floatValue();
            CONFIG_WORLD.taintSpreadArea = taintSpreadArea.get();
            CONFIG_WORLD.generateAura = generateAura.get();
            CONFIG_WORLD.generateStructure = generateStructure.get();
            CONFIG_WORLD.generateCinnabar = generateCinnabar.get();
            CONFIG_WORLD.generateAmber = generateAmber.get();
            CONFIG_WORLD.generateQuartz = generateQuartz.get();
            CONFIG_WORLD.generateCrystals = generateCrystals.get();
            CONFIG_WORLD.generateTrees = generateTrees.get();
            CONFIG_WORLD.regenKey = regenKey.get();
            CONFIG_WORLD.regenAura = regenAura.get();
            CONFIG_WORLD.regenStructure = regenStructure.get();
            CONFIG_WORLD.regenCinnabar = regenCinnabar.get();
            CONFIG_WORLD.regenAmber = regenAmber.get();
            CONFIG_WORLD.regenQuartz = regenQuartz.get();
            CONFIG_WORLD.regenCrystals = regenCrystals.get();
            CONFIG_WORLD.regenTrees = regenTrees.get();
            CONFIG_WORLD.allowSpawnAngryZombie = allowSpawnAngryZombie.get();
            CONFIG_WORLD.allowSpawnFireBat = allowSpawnFireBat.get();
            CONFIG_WORLD.allowSpawnWisp = allowSpawnWisp.get();
            CONFIG_WORLD.allowSpawnTaintacle = allowSpawnTaintacle.get();
            CONFIG_WORLD.allowSpawnPech = allowSpawnPech.get();
            CONFIG_WORLD.allowSpawnElder = allowSpawnElder.get();
            CONFIG_WORLD.allowChampionMobs = allowChampionMobs.get();
        }
    }
}
