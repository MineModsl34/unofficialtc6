package thaumcraft.common.lib.research;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Explicit TC6/vanilla item identities, NOT a universal 1.12 registry migration.
 * Flattened item variants keep their old shared item id and metadata. Unmapped modern
 * items/entities deliberately use modern ids; those fallback keys do not claim TC6 parity.
 */
public final class LegacyScanIdentity {
    private static final Map<ResourceLocation, Identity> ITEMS = itemIdentities();
    private static final Map<String, String> ENTITIES = entityNames();

    private LegacyScanIdentity() {}

    public record Identity(ResourceLocation itemId, int metadata, boolean sourceProven) {}

    /** Raw damage is supplied separately so descriptor tests need no Minecraft registry bootstrap. */
    public static Identity identify(ResourceLocation modernId, int rawDamage) {
        Identity known = ITEMS.get(modernId);
        if (known != null && modernId.getNamespace().equals("thaumcraft") && isDurableTc(modernId.getPath())) {
            return new Identity(known.itemId(), rawDamage, true);
        }
        return known == null ? new Identity(modernId, rawDamage, false) : known;
    }

    public static Identity identify(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.equals(ResourceLocation.parse("thaumcraft:label")) && stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container) {
            var aspects = container.getAspects(stack);
            return new Identity(id,aspects != null && aspects.size() > 0 ? 1 : 0,true);
        }
        // Source durable items use their current damage, even for Unbreakable stacks.
        return identify(id, rawDamage(stack));
    }

    public static int rawDamage(ItemStack stack) {
        // NeoForge Item.getDamage clamps to maxDamage, losing a recipe's old wildcard 32767.
        return stack.getOrDefault(DataComponents.DAMAGE, 0);
    }

    public static String itemResearchKey(ItemStack stack) {
        Identity identity = identify(stack);
        return researchKey(identity, stack.isDamageableItem());
    }

    public static String researchKey(Identity identity, boolean damageable) {
        return "!" + identity.itemId() + (damageable ? "" : identity.metadata());
    }

    public static String blockResearchKey(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        // Vanilla ITEM inverses are not BLOCK inverses: banner/standing_banner, melon,
        // doors and stateful slabs differ. Keep this explicit TC-only bridge for now.
        return "!" + (id.getNamespace().equals("thaumcraft") ? identify(id, 0).itemId() : id);
    }

    /** ScanAspect's direct BlockItem construction, with no getPickBlock/bucket fallback. */
    public static ItemStack directBlockStack(BlockState state) {
        if (isLegacyDoubleSlab(state)) return ItemStack.EMPTY;
        return new ItemStack(state.getBlock());
    }

    public static boolean isLegacyDoubleSlab(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        // TC6 double slabs had no ItemBlock. BlockSlabTC.getItem returned EMPTY;
        // Block.getPickBlock delegated to that same method, NOT the half-slab drop.
        return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
            && ITEMS.containsKey(id) && id.getNamespace().equals("thaumcraft") && id.getPath().startsWith("slab_");
    }

    public static String entityResearchKey(Entity entity) {
        // Forge EntityList.getEntityString looked up the exact registered class; Player has none.
        if (entity instanceof Player) return "!null";
        return "!" + entityName(EntityType.getKey(entity.getType()));
    }

    public static String entityName(ResourceLocation modernId) {
        String legacy = ENTITIES.get(modernId.toString());
        return legacy == null ? modernId.toString() : legacy;
    }

    public static Map<ResourceLocation, Identity> knownItems() { return ITEMS; }

    private static boolean isDurableTc(String path) {
        return path.equals("primordial_pearl") || path.equals("scribing_tools") || path.equals("thaumium_sword") || path.equals("void_sword")
            || path.matches("(?:thaumium|void)_(?:helm|chest|legs|boots|axe|pick|shovel|hoe)") || path.startsWith("crimson_")
            || path.matches("(?:fortress|void_robe|cloth)_(?:helm|chest|legs|boots)")
            || path.matches("elemental_(?:axe|pick|shovel|hoe|sword)") || path.equals("primal_crusher")
            || path.equals("goggles") || path.equals("traveller_boots") || path.equals("grapple_gun");
    }

    private static Map<ResourceLocation, Identity> itemIdentities() {
        Map<ResourceLocation, Identity> map = new LinkedHashMap<>(LegacyVanillaScanIdentities.knownItems());
        // Original ConfigItems:176–198; modern ConfigItems splits each metadata family.
        family(map, "ingot", "thaumium", "void", "brass");
        family(map, "nugget", "iron", "copper", "tin", "silver", "lead", "quicksilver", "thaumium", "void", "brass", "quartz", "rareearth");
        family(map, "cluster", "iron", "gold", "copper", "tin", "silver", "lead", "cinnabar", "quartz");
        family(map, "plate", "brass", "iron", "thaumium", "void");
        family(map, "mind", "clockwork", "biothaumic");
        family(map, "module", "vision", "aggression");
        family(map, "curio", "arcane", "preserved", "ancient", "eldritch", "knowledge", "twisted", "rites");
        family(map, "phial", "empty", "filled");
        family(map, "loot_bag", "common", "uncommon", "rare");
        put(map, "turret_basic", "turret", 0);
        put(map, "turret_advanced", "turret", 1);
        put(map, "turret_bore", "turret", 2);
        // ItemSealPlacer uses metadata in ConfigItems.preInitSeals insertion order, NOT seal NBT.
        family(map, "seal", "blank", "pickup", "pickup_advanced", "fill", "fill_advanced",
            "empty", "empty_advanced", "harvest", "butcher", "guard", "guard_advanced",
            "lumber", "breaker", "use", "provider", "stock", "breaker_advanced");
        same(map, "golem", "golem_bell", "golem_builder", "placeholder_bars", "placeholder_cauldron",
            "placeholder_anvil", "placeholder_table");
        same(map, "void_siphon", "liquid_death", "sanity_soap", "creative_flux_sponge");
        // Forge 1.12 UniversalBucket supplies the filled LiquidDeath bucket; no TC ItemBlock exists.
        map.put(ResourceLocation.parse("thaumcraft:liquid_death_bucket"),
            new Identity(ResourceLocation.parse("forge:bucketfilled"), 0, true));
        same(map, "bottle_taint", "causality_collapser", "banner_crimson_cult",
            "taint_crust", "taint_soil", "taint_rock", "taint_geyser", "taint_log", "taint_feature");
        put(map,"amulet_vis_crafted","amulet_vis",1);
        family(map, "celestial_notes", "sun", "stars_1", "stars_2", "stars_3", "stars_4",
            "moon_1", "moon_2", "moon_3", "moon_4", "moon_5", "moon_6", "moon_7", "moon_8");
        String[] baubles = {"amulet_mundane", "ring_mundane", "girdle_mundane", "ring_apprentice", "amulet_fancy", "ring_fancy", "girdle_fancy"};
        for (int meta = 0; meta < baubles.length; meta++) put(map, baubles[meta], "baubles", meta);
        same(map, "enchanted_placeholder", "crimson_plate_helm", "crimson_plate_chest", "crimson_plate_legs",
            "crimson_robe_helm", "crimson_robe_chest", "crimson_robe_legs", "crimson_boots",
            "brain", "amber", "quicksilver", "fabric", "vis_resonator", "tallow", "mechanism_simple", "mechanism_complex", "label", "caster_basic", "alumentum",
            "filter", "morphic_resonator", "salis_mundus", "mirrored_glass", "void_seed", "crystal_essence", "jar_brace",
            "grapple_gun_tip", "grapple_gun_spool", "primordial_pearl", "amulet_vis", "thaumometer", "scribing_tools", "thaumonomicon",
            "thaumium_sword", "void_sword", "thaumium_helm", "thaumium_chest", "thaumium_legs", "thaumium_boots",
            "void_helm", "void_chest", "void_legs", "void_boots");
        same(map,"thaumium_axe","thaumium_pick","thaumium_shovel","thaumium_hoe",
            "void_axe","void_pick","void_shovel","void_hoe","elemental_axe","elemental_pick","elemental_shovel","elemental_hoe","elemental_sword",
            "primal_crusher","crimson_blade","grapple_gun","sanity_checker","pech_wand","resonator",
            "cloth_chest","cloth_legs","cloth_boots","fortress_helm","fortress_chest","fortress_legs","void_robe_helm","void_robe_chest","void_robe_legs",
            "crimson_praetor_helm","crimson_praetor_chest","crimson_praetor_legs","goggles","traveller_boots",
            "verdant_charm","voidseer_charm","curiosity_band","cloud_ring","charm_undying","focus_1","focus_2","focus_3","focus_pouch","wand_workbench");
        // Original ConfigBlocks:172–315. These are individually registered TC6 BlockItems.
        same(map, "ore_amber", "ore_cinnabar", "ore_quartz", "crystal_aer", "crystal_ignis", "crystal_aqua", "crystal_terra",
            "crystal_ordo", "crystal_perditio", "crystal_vitium", "stone_arcane", "stone_arcane_brick", "stone_ancient",
            "stone_ancient_tile", "stone_ancient_rock", "stone_ancient_glyphed", "stone_ancient_doorway", "stone_eldritch_tile",
            "stone_porous", "stairs_arcane", "stairs_arcane_brick", "stairs_ancient", "slab_arcane_stone", "slab_arcane_brick",
            "slab_ancient", "slab_eldritch", "sapling_greatwood", "sapling_silverwood", "log_greatwood", "log_silverwood",
            "leaves_greatwood", "leaves_silverwood", "shimmerleaf", "cinderpearl", "vishroom", "plank_greatwood",
            "plank_silverwood", "stairs_greatwood", "stairs_silverwood", "slab_greatwood", "slab_silverwood", "amber_block",
            "amber_brick", "flesh_block", "loot_crate_common", "loot_crate_uncommon", "loot_crate_rare", "loot_urn_common",
            "loot_urn_uncommon", "loot_urn_rare", "grass_ambient", "table_wood", "table_stone", "research_table", "arcane_workbench", "metal_brass", "metal_thaumium",
            "metal_void", "metal_alchemical", "metal_alchemical_advanced", "paving_stone_travel", "paving_stone_barrier",
            "pillar_arcane", "pillar_ancient", "pillar_eldritch", "matrix_speed", "matrix_cost",
            "candle_white", "candle_orange", "candle_magenta", "candle_yellow", "candle_lime", "candle_pink", "candle_gray",
            "candle_cyan", "candle_purple", "candle_blue", "candle_brown", "candle_green", "candle_red", "candle_black");
        // Old EnumDyeColor.getTranslationKey().toLowerCase(), not its registry/color name.
        put(map, "candle_light_blue", "candle_lightblue", 0);
        put(map, "candle_light_gray", "candle_silver", 0);
        // Original ConfigBlocks:328-366 + BlockCrucible/BlockInfusionMatrix constructors.
        // These are separate registered BlockItems, NOT old metadata families. Omitting
        // them made real native crafting events unable to satisfy their research receipts.
        same(map, "crucible", "infusion_matrix", "pedestal_arcane", "pedestal_ancient", "pedestal_eldritch",
            "stabilizer", "arcane_workbench_charger", "jar_normal", "jar_void", "alembic",
            "smelter_basic", "smelter_thaumium", "smelter_void", "bellows",
            "tube", "tube_valve", "tube_restrict", "tube_oneway", "tube_filter", "tube_buffer",
            "nitor_white", "nitor_orange", "nitor_magenta", "nitor_yellow", "nitor_lime", "nitor_pink",
            "nitor_gray", "nitor_cyan", "nitor_purple", "nitor_blue", "nitor_brown", "nitor_green", "nitor_red", "nitor_black");
        put(map, "nitor_light_blue", "nitor_lightblue", 0);
        put(map, "nitor_light_gray", "nitor_silver", 0);
        return java.util.Collections.unmodifiableMap(map);
    }

    private static void family(Map<ResourceLocation, Identity> map, String base, String... variants) {
        for (int meta = 0; meta < variants.length; meta++) put(map, base + "_" + variants[meta], base, meta);
    }

    private static void same(Map<ResourceLocation, Identity> map, String... paths) {
        for (String path : paths) put(map, path, path, 0);
    }

    private static void put(Map<ResourceLocation, Identity> map, String modern, String legacy, int metadata) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumcraft", modern);
        Identity old = map.put(id, new Identity(ResourceLocation.fromNamespaceAndPath("thaumcraft", legacy), metadata, true));
        if (old != null) throw new IllegalStateException("Duplicate legacy scan identity: " + id);
    }

    private static Map<String, String> entityNames() {
        Map<String, String> map = new LinkedHashMap<>();
        // Forge-patched 1.12 EntityList:277–359 uses EntityEntry.name, not registry id.
        String[][] names = {
            {"experience_orb", "XPOrb"}, {"area_effect_cloud", "AreaEffectCloud"}, {"elder_guardian", "ElderGuardian"},
            {"wither_skeleton", "WitherSkeleton"}, {"stray", "Stray"}, {"egg", "ThrownEgg"}, {"leash_knot", "LeashKnot"},
            {"painting", "Painting"}, {"arrow", "Arrow"}, {"snowball", "Snowball"}, {"fireball", "Fireball"},
            {"small_fireball", "SmallFireball"}, {"ender_pearl", "ThrownEnderpearl"}, {"eye_of_ender", "EyeOfEnderSignal"},
            {"potion", "ThrownPotion"}, {"experience_bottle", "ThrownExpBottle"}, {"item_frame", "ItemFrame"},
            {"wither_skull", "WitherSkull"}, {"tnt", "PrimedTnt"}, {"falling_block", "FallingSand"},
            {"firework_rocket", "FireworksRocketEntity"}, {"husk", "Husk"}, {"spectral_arrow", "SpectralArrow"},
            {"shulker_bullet", "ShulkerBullet"}, {"dragon_fireball", "DragonFireball"}, {"zombie_villager", "ZombieVillager"},
            {"skeleton_horse", "SkeletonHorse"}, {"zombie_horse", "ZombieHorse"}, {"armor_stand", "ArmorStand"},
            {"donkey", "Donkey"}, {"mule", "Mule"}, {"evoker_fangs", "EvocationFangs"}, {"evoker", "EvocationIllager"},
            {"vex", "Vex"}, {"vindicator", "VindicationIllager"}, {"illusioner", "IllusionIllager"},
            {"command_block_minecart", "MinecartCommandBlock"}, {"boat", "Boat"}, {"minecart", "MinecartRideable"},
            {"chest_minecart", "MinecartChest"}, {"furnace_minecart", "MinecartFurnace"}, {"tnt_minecart", "MinecartTNT"},
            {"hopper_minecart", "MinecartHopper"}, {"spawner_minecart", "MinecartSpawner"}, {"creeper", "Creeper"},
            {"skeleton", "Skeleton"}, {"spider", "Spider"}, {"giant", "Giant"}, {"zombie", "Zombie"}, {"slime", "Slime"},
            {"ghast", "Ghast"}, {"zombified_piglin", "PigZombie"}, {"enderman", "Enderman"}, {"cave_spider", "CaveSpider"},
            {"silverfish", "Silverfish"}, {"blaze", "Blaze"}, {"magma_cube", "LavaSlime"}, {"ender_dragon", "EnderDragon"},
            {"wither", "WitherBoss"}, {"bat", "Bat"}, {"witch", "Witch"}, {"endermite", "Endermite"},
            {"guardian", "Guardian"}, {"shulker", "Shulker"}, {"pig", "Pig"}, {"sheep", "Sheep"}, {"cow", "Cow"},
            {"chicken", "Chicken"}, {"squid", "Squid"}, {"wolf", "Wolf"}, {"mooshroom", "MushroomCow"},
            {"snow_golem", "SnowMan"}, {"ocelot", "Ozelot"}, {"iron_golem", "VillagerGolem"}, {"horse", "Horse"},
            {"rabbit", "Rabbit"}, {"polar_bear", "PolarBear"}, {"llama", "Llama"}, {"llama_spit", "LlamaSpit"},
            {"parrot", "Parrot"}, {"villager", "Villager"}, {"end_crystal", "EnderCrystal"}
        };
        for (String[] entry : names) map.put("minecraft:" + entry[0], entry[1]);
        // Original ConfigEntities passes these exact EntityEntry names to old Forge.
        // HomingShard/RiftBlast were not registered there, so retain honest native fallback ids.
        map.put("thaumcraft:cultist_portal_greater", "CultistPortalGreater");
        map.put("thaumcraft:cultist_portal_lesser", "CultistPortalLesser");
        map.put("thaumcraft:special_item", "SpecialItem");
        map.put("thaumcraft:following_item", "FollowItem");
        map.put("thaumcraft:falling_taint", "FallingTaint");
        map.put("thaumcraft:alumentum", "Alumentum");
        map.put("thaumcraft:golem_dart", "GolemDart");
        map.put("thaumcraft:golem", "Golem");
        map.put("thaumcraft:flux_rift", "FluxRift");
        map.put("thaumcraft:eldritch_orb", "EldritchOrb");
        map.put("thaumcraft:bottle_taint", "BottleTaint");
        map.put("thaumcraft:golem_orb", "GolemOrb");
        map.put("thaumcraft:grapple", "Grapple");
        map.put("thaumcraft:causality_collapser", "CausalityCollapser");
        map.put("thaumcraft:focus_projectile", "FocusProjectile");
        map.put("thaumcraft:focus_cloud", "FocusCloud");
        map.put("thaumcraft:focus_mine", "Focusmine");
        map.put("thaumcraft:turret_basic", "TurretBasic");
        map.put("thaumcraft:turret_advanced", "TurretAdvanced");
        map.put("thaumcraft:arcane_bore", "ArcaneBore");
        map.put("thaumcraft:eldritch_warden", "EldritchWarden");
        map.put("thaumcraft:eldritch_golem", "EldritchGolem");
        map.put("thaumcraft:cultist_leader", "CultistLeader");
        map.put("thaumcraft:taintacle_giant", "TaintacleGiant");
        map.put("thaumcraft:brainy_zombie", "BrainyZombie");
        map.put("thaumcraft:giant_brainy_zombie", "GiantBrainyZombie");
        map.put("thaumcraft:wisp", "Wisp");
        map.put("thaumcraft:firebat", "Firebat");
        map.put("thaumcraft:spellbat", "Spellbat");
        map.put("thaumcraft:pech", "Pech");
        map.put("thaumcraft:mind_spider", "MindSpider");
        map.put("thaumcraft:eldritch_guardian", "EldritchGuardian");
        map.put("thaumcraft:cultist_knight", "CultistKnight");
        map.put("thaumcraft:cultist_cleric", "CultistCleric");
        map.put("thaumcraft:eldritch_crab", "EldritchCrab");
        map.put("thaumcraft:inhabited_zombie", "InhabitedZombie");
        map.put("thaumcraft:thaumic_slime", "ThaumSlime");
        map.put("thaumcraft:taint_crawler", "TaintCrawler");
        map.put("thaumcraft:taintacle", "Taintacle");
        map.put("thaumcraft:taintacle_small", "TaintacleTiny");
        map.put("thaumcraft:taint_swarm", "TaintSwarm");
        map.put("thaumcraft:taint_seed", "TaintSeed");
        map.put("thaumcraft:taint_seed_prime", "TaintSeedPrime");
        return Map.copyOf(map);
    }
}
