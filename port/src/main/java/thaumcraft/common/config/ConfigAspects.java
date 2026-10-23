package thaumcraft.common.config;

import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.NeoForge;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectEventProxy;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.AspectRegistryEvent;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Aspects of vanilla items and mobs. TC6's registration order is kept: some values are derived from already
 * registered ones (new AspectList(stack)) or from recipes (registerComplexObjectTag).
 *
 * Name mapping 1.12.2 -> 1.21.1:
 *  - Ore Dictionary -> c:/minecraft: tags where their contents match; otherwise an explicit item list
 *    (for example, "stone" in Forge 1.12 is only smooth stone, while c:stones is wider).
 *  - meta variants (Blocks.X, 1, n) -> separate items; wildcard 32767 -> all former variants.
 *  - Blocks without an item in 1.12.2 (fire, portals, tripwire) gave an empty stack and are skipped.
 *  - Mobs -> EntityType id; TC's own mobs are "thaumcraft:snake_case" (stage 8 uses the same ids).
 */
public class ConfigAspects {
    public static void postInit() {
        CommonInternals.objectTags.clear();
        CommonInternals.objectTagStacks.clear();
        CommonInternals.scanEntities.clear();
        registerItemAspects();
        registerEntityAspects();
        AspectRegistryEvent are = new AspectRegistryEvent();
        are.register = new AspectEventProxy();
        NeoForge.EVENT_BUS.post(are);
    }

    private static void registerEntityAspects() {
        ThaumcraftApi.registerEntityTag("minecraft:zombie", new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 10).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerEntityTag("minecraft:husk", new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 10).add(Aspect.FIRE, 5));
        ThaumcraftApi.registerEntityTag("minecraft:giant", new AspectList().add(Aspect.UNDEAD, 25).add(Aspect.MAN, 15).add(Aspect.EARTH, 10));
        ThaumcraftApi.registerEntityTag("minecraft:skeleton", new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerEntityTag("minecraft:wither_skeleton", new AspectList().add(Aspect.UNDEAD, 25).add(Aspect.MAN, 5).add(Aspect.ENTROPY, 10));
        ThaumcraftApi.registerEntityTag("minecraft:creeper", new AspectList().add(Aspect.PLANT, 15).add(Aspect.FIRE, 15));
        ThaumcraftApi.registerEntityTag(
            "minecraft:creeper", new AspectList().add(Aspect.PLANT, 15).add(Aspect.FIRE, 15).add(Aspect.ENERGY, 15), new ThaumcraftApi.EntityTagsNBT("powered", (byte)1)
        );
        ThaumcraftApi.registerEntityTag("minecraft:horse", new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("minecraft:donkey", new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("minecraft:mule", new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("minecraft:skeleton_horse", new AspectList().add(Aspect.BEAST, 5).add(Aspect.UNDEAD, 10).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("minecraft:zombie_horse", new AspectList().add(Aspect.BEAST, 10).add(Aspect.UNDEAD, 5).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("minecraft:pig", new AspectList().add(Aspect.BEAST, 10).add(Aspect.EARTH, 10).add(Aspect.DESIRE, 5));
        ThaumcraftApi.registerEntityTag("minecraft:experience_orb", new AspectList().add(Aspect.MIND, 10));
        ThaumcraftApi.registerEntityTag("minecraft:sheep", new AspectList().add(Aspect.BEAST, 10).add(Aspect.EARTH, 10));
        ThaumcraftApi.registerEntityTag("minecraft:cow", new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 15));
        ThaumcraftApi.registerEntityTag("minecraft:mooshroom", new AspectList().add(Aspect.BEAST, 15).add(Aspect.PLANT, 15).add(Aspect.EARTH, 15));
        ThaumcraftApi.registerEntityTag("minecraft:snow_golem", new AspectList().add(Aspect.COLD, 10).add(Aspect.MAN, 5).add(Aspect.MECHANISM, 5).add(Aspect.MAGIC, 5));
        ThaumcraftApi.registerEntityTag("minecraft:ocelot", new AspectList().add(Aspect.BEAST, 10).add(Aspect.ENTROPY, 10));
        ThaumcraftApi.registerEntityTag("minecraft:chicken", new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("minecraft:squid", new AspectList().add(Aspect.BEAST, 5).add(Aspect.WATER, 10));
        ThaumcraftApi.registerEntityTag("minecraft:wolf", new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 10).add(Aspect.AVERSION, 5));
        ThaumcraftApi.registerEntityTag("minecraft:bat", new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.DARKNESS, 5));
        ThaumcraftApi.registerEntityTag("minecraft:spider", new AspectList().add(Aspect.BEAST, 10).add(Aspect.ENTROPY, 10).add(Aspect.TRAP, 10));
        ThaumcraftApi.registerEntityTag("minecraft:slime", new AspectList().add(Aspect.LIFE, 10).add(Aspect.WATER, 10).add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerEntityTag("minecraft:ghast", new AspectList().add(Aspect.UNDEAD, 15).add(Aspect.FIRE, 15));
        ThaumcraftApi.registerEntityTag("minecraft:zombified_piglin", new AspectList().add(Aspect.UNDEAD, 15).add(Aspect.FIRE, 15).add(Aspect.BEAST, 10));
        ThaumcraftApi.registerEntityTag("minecraft:enderman", new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.MOTION, 15).add(Aspect.DESIRE, 5));
        ThaumcraftApi.registerEntityTag("minecraft:cave_spider", new AspectList().add(Aspect.BEAST, 5).add(Aspect.DEATH, 10).add(Aspect.TRAP, 10));
        ThaumcraftApi.registerEntityTag("minecraft:silverfish", new AspectList().add(Aspect.BEAST, 5).add(Aspect.EARTH, 10));
        ThaumcraftApi.registerEntityTag("minecraft:blaze", new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.FIRE, 15).add(Aspect.FLIGHT, 5));
        ThaumcraftApi.registerEntityTag("minecraft:magma_cube", new AspectList().add(Aspect.WATER, 5).add(Aspect.FIRE, 10).add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerEntityTag(
            "minecraft:ender_dragon", new AspectList().add(Aspect.ELDRITCH, 50).add(Aspect.BEAST, 30).add(Aspect.ENTROPY, 50).add(Aspect.FLIGHT, 10)
        );
        ThaumcraftApi.registerEntityTag("minecraft:wither", new AspectList().add(Aspect.UNDEAD, 50).add(Aspect.ENTROPY, 25).add(Aspect.FIRE, 25));
        ThaumcraftApi.registerEntityTag("minecraft:witch", new AspectList().add(Aspect.MAN, 15).add(Aspect.MAGIC, 5).add(Aspect.ALCHEMY, 10));
        ThaumcraftApi.registerEntityTag("minecraft:villager", new AspectList().add(Aspect.MAN, 15));
        ThaumcraftApi.registerEntityTag(
            "minecraft:iron_golem", new AspectList().add(Aspect.METAL, 15).add(Aspect.MAN, 5).add(Aspect.MECHANISM, 5).add(Aspect.MAGIC, 5)
        );
        ThaumcraftApi.registerEntityTag("minecraft:end_crystal", new AspectList().add(Aspect.ELDRITCH, 15).add(Aspect.AURA, 15).add(Aspect.LIFE, 15));
        ThaumcraftApi.registerEntityTag("minecraft:item_frame", new AspectList().add(Aspect.SENSES, 5).add(Aspect.CRAFT, 5));
        ThaumcraftApi.registerEntityTag("minecraft:painting", new AspectList().add(Aspect.SENSES, 10).add(Aspect.CRAFT, 5));
        ThaumcraftApi.registerEntityTag("minecraft:guardian", new AspectList().add(Aspect.BEAST, 10).add(Aspect.ELDRITCH, 10).add(Aspect.WATER, 10));
        // In 1.12.2 the elder guardian is a Guardian with NBT Elder=true; in 1.21 it is a separate mob
        ThaumcraftApi.registerEntityTag("minecraft:elder_guardian", new AspectList().add(Aspect.BEAST, 10).add(Aspect.ELDRITCH, 15).add(Aspect.WATER, 15));
        ThaumcraftApi.registerEntityTag("minecraft:rabbit", new AspectList().add(Aspect.BEAST, 5).add(Aspect.EARTH, 5).add(Aspect.MOTION, 5));
        ThaumcraftApi.registerEntityTag("minecraft:endermite", new AspectList().add(Aspect.BEAST, 5).add(Aspect.ELDRITCH, 5).add(Aspect.MOTION, 5));
        ThaumcraftApi.registerEntityTag("minecraft:polar_bear", new AspectList().add(Aspect.BEAST, 15).add(Aspect.COLD, 10));
        ThaumcraftApi.registerEntityTag("minecraft:shulker", new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.TRAP, 5).add(Aspect.FLIGHT, 5).add(Aspect.PROTECT, 5));
        ThaumcraftApi.registerEntityTag("minecraft:evoker", new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 10));
        ThaumcraftApi.registerEntityTag("minecraft:vindicator", new AspectList().add(Aspect.AVERSION, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 10));
        ThaumcraftApi.registerEntityTag("minecraft:illusioner", new AspectList().add(Aspect.SENSES, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 10));
        ThaumcraftApi.registerEntityTag("minecraft:llama", new AspectList().add(Aspect.BEAST, 15).add(Aspect.WATER, 5));
        ThaumcraftApi.registerEntityTag("minecraft:parrot", new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.SENSES, 5));
        ThaumcraftApi.registerEntityTag("minecraft:stray", new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 5).add(Aspect.TRAP, 5));
        ThaumcraftApi.registerEntityTag("minecraft:vex", new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.FLIGHT, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:flux_rift", new AspectList().add(Aspect.FLUX, 20).add(Aspect.ELDRITCH, 20).add(Aspect.AURA, 20));
        ThaumcraftApi.registerEntityTag("thaumcraft:firebat", new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.FIRE, 10));
        ThaumcraftApi.registerEntityTag(
            "thaumcraft:pech",
            new AspectList().add(Aspect.MAN, 10).add(Aspect.AURA, 5).add(Aspect.EXCHANGE, 10).add(Aspect.DESIRE, 5),
            new ThaumcraftApi.EntityTagsNBT("PechType", (byte)0)
        );
        ThaumcraftApi.registerEntityTag(
            "thaumcraft:pech",
            new AspectList().add(Aspect.MAN, 10).add(Aspect.AURA, 5).add(Aspect.EXCHANGE, 10).add(Aspect.AVERSION, 5),
            new ThaumcraftApi.EntityTagsNBT("PechType", (byte)1)
        );
        ThaumcraftApi.registerEntityTag(
            "thaumcraft:pech",
            new AspectList().add(Aspect.MAN, 10).add(Aspect.AURA, 5).add(Aspect.EXCHANGE, 10).add(Aspect.MAGIC, 5),
            new ThaumcraftApi.EntityTagsNBT("PechType", (byte)2)
        );
        ThaumcraftApi.registerEntityTag(
            "thaumcraft:thaumic_slime", new AspectList().add(Aspect.LIFE, 5).add(Aspect.WATER, 5).add(Aspect.FLUX, 5).add(Aspect.ALCHEMY, 5)
        );
        ThaumcraftApi.registerEntityTag(
            "thaumcraft:brainy_zombie", new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 10).add(Aspect.MIND, 5).add(Aspect.AVERSION, 5)
        );
        ThaumcraftApi.registerEntityTag(
            "thaumcraft:giant_brainy_zombie", new AspectList().add(Aspect.UNDEAD, 25).add(Aspect.MAN, 15).add(Aspect.MIND, 5).add(Aspect.AVERSION, 10)
        );
        ThaumcraftApi.registerEntityTag("thaumcraft:taintacle", new AspectList().add(Aspect.FLUX, 15).add(Aspect.BEAST, 10));
        ThaumcraftApi.registerEntityTag("thaumcraft:taint_seed", new AspectList().add(Aspect.FLUX, 20).add(Aspect.AURA, 10).add(Aspect.PLANT, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:taint_seed_prime", new AspectList().add(Aspect.FLUX, 25).add(Aspect.AURA, 15).add(Aspect.PLANT, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:taintacle_tiny", new AspectList().add(Aspect.FLUX, 5).add(Aspect.BEAST, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:taint_swarm", new AspectList().add(Aspect.FLUX, 15).add(Aspect.AIR, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:mind_spider", new AspectList().add(Aspect.FLUX, 5).add(Aspect.FIRE, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:eldritch_guardian", new AspectList().add(Aspect.ELDRITCH, 20).add(Aspect.DEATH, 20).add(Aspect.UNDEAD, 20));
        ThaumcraftApi.registerEntityTag("thaumcraft:cultist_knight", new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.MAN, 15).add(Aspect.AVERSION, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:cultist_cleric", new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.MAN, 15).add(Aspect.AVERSION, 5));
        ThaumcraftApi.registerEntityTag("thaumcraft:eldritch_crab", new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.BEAST, 10).add(Aspect.TRAP, 10));
        ThaumcraftApi.registerEntityTag("thaumcraft:inhabited_zombie", new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.UNDEAD, 10).add(Aspect.MAN, 5));
        // TC6 registers taint seeds twice; the lookup takes the last matching entry, so keep both
        ThaumcraftApi.registerEntityTag("thaumcraft:taint_seed", new AspectList().add(Aspect.PLANT, 20).add(Aspect.BEAST, 20).add(Aspect.FLUX, 20));
        ThaumcraftApi.registerEntityTag("thaumcraft:taint_seed_prime", new AspectList().add(Aspect.PLANT, 30).add(Aspect.BEAST, 30).add(Aspect.FLUX, 30));
        ThaumcraftApi.registerEntityTag("thaumcraft:eldritch_warden", new AspectList().add(Aspect.ELDRITCH, 40).add(Aspect.DEATH, 40).add(Aspect.UNDEAD, 40));
        ThaumcraftApi.registerEntityTag("thaumcraft:eldritch_golem", new AspectList().add(Aspect.ELDRITCH, 40).add(Aspect.ENERGY, 40).add(Aspect.MECHANISM, 40));
        ThaumcraftApi.registerEntityTag("thaumcraft:cultist_leader", new AspectList().add(Aspect.ELDRITCH, 40).add(Aspect.AVERSION, 40).add(Aspect.MAN, 40));
        ThaumcraftApi.registerEntityTag("thaumcraft:taintacle_giant", new AspectList().add(Aspect.ELDRITCH, 40).add(Aspect.BEAST, 40).add(Aspect.FLUX, 40));

        for (Aspect tag : Aspect.aspects.values()) {
            ThaumcraftApi.registerEntityTag(
                "thaumcraft:wisp",
                new AspectList().add(tag, 5).add(Aspect.AURA, 5).add(Aspect.FLIGHT, 5),
                new ThaumcraftApi.EntityTagsNBT("Type", tag.getTag())
            );
        }

        ThaumcraftApi.registerEntityTag("thaumcraft:golem", new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MAN, 10).add(Aspect.MOTION, 10));
    }

    private static Item item(String id) {
        // Unqualified vanilla IDs retain their default namespace; explicit TC IDs must not
        // become the invalid minecraft:thaumcraft:<item> path during real server postInit.
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    private static ItemStack stack(String id) {
        return new ItemStack(item(id));
    }

    private static void items(AspectList aspects, Item... items) {
        for (Item i : items) {
            ThaumcraftApi.registerObjectTag(new ItemStack(i), aspects.copy());
        }
    }

    private static void registerItemAspects() {
        ThaumcraftApi.registerObjectTag("c:ores/lapis", new AspectList().add(Aspect.EARTH, 5).add(Aspect.SENSES, 15));
        ThaumcraftApi.registerObjectTag("c:ores/diamond", new AspectList().add(Aspect.EARTH, 5).add(Aspect.DESIRE, 15).add(Aspect.CRYSTAL, 15));
        ThaumcraftApi.registerObjectTag("c:gems/diamond", new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.DESIRE, 15));
        ThaumcraftApi.registerObjectTag("c:ores/redstone", new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENERGY, 15));
        ThaumcraftApi.registerObjectTag("c:ores/emerald", new AspectList().add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10).add(Aspect.CRYSTAL, 15));
        ThaumcraftApi.registerObjectTag("c:gems/emerald", new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerObjectTag("c:ores/quartz", new AspectList().add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag("c:gems/quartz", new AspectList().add(Aspect.CRYSTAL, 5));
        ThaumcraftApi.registerObjectTag("c:ores/iron", new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 15));
        ThaumcraftApi.registerObjectTag("c:dusts/iron", new AspectList().add(Aspect.METAL, 15).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag("c:ingots/iron", new AspectList().add(Aspect.METAL, 15));
        ThaumcraftApi.registerObjectTag("c:ores/gold", new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 10).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerObjectTag("c:dusts/gold", new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag("c:ingots/gold", new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerObjectTag("c:ores/coal", new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENERGY, 15).add(Aspect.FIRE, 15));
        // Items.COAL wildcard: coal and charcoal were meta variants
        items(new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10), Items.COAL, Items.CHARCOAL);
        ThaumcraftApi.registerObjectTag("c:dusts/redstone", new AspectList().add(Aspect.ENERGY, 10));
        ThaumcraftApi.registerObjectTag("c:dusts/glowstone", new AspectList().add(Aspect.SENSES, 5).add(Aspect.LIGHT, 10));
        ThaumcraftApi.registerObjectTag(stack(Items.GLOWSTONE), new AspectList(stack(Items.GLOWSTONE)));
        ThaumcraftApi.registerObjectTag("c:ingots/copper", new AspectList().add(Aspect.METAL, 10).add(Aspect.EXCHANGE, 5));
        ThaumcraftApi.registerObjectTag("c:dusts/copper", new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.EXCHANGE, 5));
        ThaumcraftApi.registerObjectTag("c:ores/copper", new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.EXCHANGE, 5));
        ThaumcraftApi.registerObjectTag("c:ingots/tin", new AspectList().add(Aspect.METAL, 10).add(Aspect.CRYSTAL, 5));
        ThaumcraftApi.registerObjectTag("c:dusts/tin", new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.CRYSTAL, 5));
        ThaumcraftApi.registerObjectTag("c:ores/tin", new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 5));
        ThaumcraftApi.registerObjectTag("c:ingots/silver", new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 5));
        ThaumcraftApi.registerObjectTag("c:dusts/silver", new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.DESIRE, 5));
        ThaumcraftApi.registerObjectTag("c:ores/silver", new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 5));
        ThaumcraftApi.registerObjectTag("c:ingots/lead", new AspectList().add(Aspect.METAL, 10).add(Aspect.ORDER, 5));
        ThaumcraftApi.registerObjectTag("c:dusts/lead", new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.ORDER, 5));
        ThaumcraftApi.registerObjectTag("c:ores/lead", new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.ORDER, 5));
        // Native cluster assignments are installed by registerThaumcraftItemAspects below.
        ThaumcraftApi.registerObjectTag("c:ingots/brass", new AspectList().add(Aspect.METAL, 10).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerObjectTag("c:dusts/brass", new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerObjectTag("c:ingots/bronze", new AspectList().add(Aspect.METAL, 10).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerObjectTag("c:dusts/bronze", new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerObjectTag("c:ores/uranium", new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ENERGY, 10));
        ThaumcraftApi.registerObjectTag("c:raw_materials/uranium", new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ENERGY, 10));
        ThaumcraftApi.registerObjectTag("c:ingots/uranium", new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ENERGY, 10));
        ThaumcraftApi.registerObjectTag("c:gems/ruby", new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerObjectTag("c:gems/green_sapphire", new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerObjectTag("c:gems/sapphire", new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerObjectTag("c:ingots/steel", new AspectList().add(Aspect.METAL, 15).add(Aspect.ORDER, 5));
        ThaumcraftApi.registerObjectTag("c:rubbers", new AspectList().add(Aspect.MOTION, 5).add(Aspect.TOOL, 5));
        // Forge 1.12: "stone" is only smooth stone, "stoneGranite" etc. only the unpolished variants
        items(new AspectList().add(Aspect.EARTH, 5), Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE);
        ThaumcraftApi.registerObjectTag(stack(Items.COBBLESTONE), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag(
            stack(Items.BEDROCK), new AspectList().add(Aspect.VOID, 25).add(Aspect.ENTROPY, 25).add(Aspect.EARTH, 25).add(Aspect.DARKNESS, 25)
        );
        items(new AspectList().add(Aspect.EARTH, 5), Items.DIRT, Items.COARSE_DIRT, Items.PODZOL);
        ThaumcraftApi.registerObjectTag(stack(Items.PODZOL), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.FARMLAND), new AspectList().add(Aspect.EARTH, 5).add(Aspect.WATER, 2).add(Aspect.ORDER, 2));
        items(new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 5), Items.SAND, Items.RED_SAND);
        ThaumcraftApi.registerObjectTag(stack(Items.GRASS_BLOCK), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.DIRT_PATH), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 2).add(Aspect.ORDER, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.END_STONE), new AspectList().add(Aspect.EARTH, 5).add(Aspect.DARKNESS, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.GRAVEL), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.MYCELIUM), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 1).add(Aspect.FLUX, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.CLAY_BALL), new AspectList().add(Aspect.WATER, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.TERRACOTTA), new AspectList(stack(Items.CLAY)).add(Aspect.FIRE, 1));
        for (DyeColor color : DyeColor.values()) {
            ThaumcraftApi.registerObjectTag(
                stack(color.getName() + "_terracotta"), new AspectList(stack(Items.CLAY)).add(Aspect.FIRE, 1).add(Aspect.SENSES, 1)
            );
        }
        ThaumcraftApi.registerObjectTag(stack(Items.BRICK), new AspectList(stack(Items.CLAY_BALL)).add(Aspect.FIRE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.NETHERRACK), new AspectList().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.NETHER_BRICK), new AspectList(stack(Items.NETHERRACK)).add(Aspect.FIRE, 1).add(Aspect.ORDER, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.SOUL_SAND), new AspectList().add(Aspect.EARTH, 3).add(Aspect.TRAP, 1).add(Aspect.SOUL, 3));
        // "blockGlass": glass and the 16 stained glasses
        ThaumcraftApi.registerObjectTag(stack(Items.GLASS), new AspectList().add(Aspect.CRYSTAL, 5));
        for (DyeColor color : DyeColor.values()) {
            ThaumcraftApi.registerObjectTag(stack(color.getName() + "_stained_glass"), new AspectList().add(Aspect.CRYSTAL, 5));
        }
        ThaumcraftApi.registerObjectTag(stack(Items.MOSSY_COBBLESTONE), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 3).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.OBSIDIAN), new AspectList().add(Aspect.EARTH, 5).add(Aspect.FIRE, 5).add(Aspect.DARKNESS, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.MOSSY_STONE_BRICKS), new AspectList(stack(Items.STONE_BRICKS)).add(Aspect.PLANT, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.CRACKED_STONE_BRICKS), new AspectList(stack(Items.STONE_BRICKS)).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.CHISELED_STONE_BRICKS), new AspectList(stack(Items.STONE_BRICKS)).add(Aspect.ORDER, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.CHISELED_SANDSTONE), new AspectList(stack(Items.SANDSTONE)).add(Aspect.ORDER, 1));
        // 1.12.2 "smooth sandstone" (meta 2) became cut_sandstone
        ThaumcraftApi.registerObjectTag(stack(Items.CUT_SANDSTONE), new AspectList(stack(Items.SANDSTONE)).add(Aspect.ORDER, 1));
        for (DyeColor color : DyeColor.values()) {
            ThaumcraftApi.registerComplexObjectTag(
                stack(color.getName() + "_concrete"), new AspectList(stack(color.getName() + "_concrete_powder")).add(Aspect.WATER, 1).add(Aspect.ORDER, 1)
            );
        }
        for (DyeColor color : DyeColor.values()) {
            ThaumcraftApi.registerObjectTag(
                stack(color.getName() + "_glazed_terracotta"), new AspectList(stack(Items.WHITE_TERRACOTTA)).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1)
            );
        }
        ThaumcraftApi.registerObjectTag("minecraft:logs_that_burn", new AspectList().add(Aspect.PLANT, 20));
        ThaumcraftApi.registerObjectTag("minecraft:saplings", new AspectList().add(Aspect.PLANT, 15).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag("minecraft:leaves", new AspectList().add(Aspect.PLANT, 5));
        // TALLGRASS wildcard: dead bush, grass, fern
        items(new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1), Items.SHORT_GRASS, Items.FERN);
        // DOUBLE_PLANT 0..5: sunflower, lilac, tall grass, large fern, rose bush, peony
        items(new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1).add(Aspect.SENSES, 5).add(Aspect.LIFE, 1), Items.SUNFLOWER, Items.LILAC);
        items(new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1), Items.TALL_GRASS, Items.LARGE_FERN);
        items(new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1).add(Aspect.SENSES, 5).add(Aspect.LIFE, 1), Items.ROSE_BUSH, Items.PEONY);
        ThaumcraftApi.registerObjectTag(stack(Items.LILY_PAD), new AspectList().add(Aspect.PLANT, 5).add(Aspect.WATER, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.DEAD_BUSH), new AspectList().add(Aspect.PLANT, 5).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.VINE), new AspectList().add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.WHEAT_SEEDS), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.MELON_SEEDS), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.PUMPKIN_SEEDS), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.BEETROOT_SEEDS), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.NETHER_WART), new AspectList().add(Aspect.PLANT, 1).add(Aspect.FLUX, 2).add(Aspect.ALCHEMY, 3));
        // RED_FLOWER wildcard: the 9 flowers of 1.12.2; YELLOW_FLOWER: dandelion
        items(
            new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.SENSES, 5),
            Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP,
            Items.OXEYE_DAISY
        );
        ThaumcraftApi.registerObjectTag(stack(Items.DANDELION), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.SENSES, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.CACTUS), new AspectList().add(Aspect.PLANT, 5).add(Aspect.WATER, 5).add(Aspect.AVERSION, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.BROWN_MUSHROOM), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.EARTH, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.RED_MUSHROOM), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.FIRE, 2));
        // In 1.12.2 the mushroom stem was a meta variant of both mushroom blocks; take the first (brown)
        items(new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.EARTH, 2), Items.BROWN_MUSHROOM_BLOCK, Items.MUSHROOM_STEM);
        ThaumcraftApi.registerObjectTag(stack(Items.RED_MUSHROOM_BLOCK), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.FIRE, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.SUGAR_CANE), new AspectList().add(Aspect.PLANT, 5).add(Aspect.WATER, 3).add(Aspect.AIR, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.WHEAT), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.APPLE), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.CARROT), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5).add(Aspect.SENSES, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.POTATO), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.BEETROOT), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5).add(Aspect.DESIRE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.BAKED_POTATO), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.POISONOUS_POTATO), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DEATH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.PUMPKIN), new AspectList().add(Aspect.PLANT, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.MELON), new AspectList().add(Aspect.PLANT, 10).remove(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.MELON_SLICE), new AspectList().add(Aspect.PLANT, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.SPONGE), new AspectList().add(Aspect.EARTH, 5).add(Aspect.TRAP, 5).add(Aspect.VOID, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.WET_SPONGE), new AspectList().add(Aspect.EARTH, 5).add(Aspect.TRAP, 5).add(Aspect.WATER, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.SUGAR), new AspectList().add(Aspect.DESIRE, 1).add(Aspect.ENERGY, 1));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.CAKE), new AspectList().add(Aspect.DESIRE, 1).add(Aspect.LIFE, 2));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.PUMPKIN_PIE), new AspectList().add(Aspect.DESIRE, 1).add(Aspect.LIFE, 2));
        // TC6 has only white wool (meta 0); coloured wool is derived from recipes
        ThaumcraftApi.registerObjectTag(stack(Items.WHITE_WOOL), new AspectList().add(Aspect.BEAST, 15).add(Aspect.CRAFT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.EXPERIENCE_BOTTLE), new AspectList().add(Aspect.MIND, 20));
        ThaumcraftApi.registerObjectTag(stack(Items.NAME_TAG), new AspectList().add(Aspect.MIND, 10).add(Aspect.BEAST, 10));
        ThaumcraftApi.registerObjectTag(stack(Items.IRON_HORSE_ARMOR), new AspectList().add(Aspect.METAL, 15).add(Aspect.PROTECT, 10).add(Aspect.BEAST, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.GOLDEN_HORSE_ARMOR), new AspectList().add(Aspect.METAL, 10).add(Aspect.PROTECT, 15).add(Aspect.BEAST, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.DIAMOND_HORSE_ARMOR), new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.PROTECT, 20).add(Aspect.BEAST, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.MAGMA_BLOCK), new AspectList().add(Aspect.FIRE, 10).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.CHORUS_FLOWER), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.SENSES, 5).add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.CHORUS_PLANT), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.CHORUS_FRUIT), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.SENSES, 5).add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(
            stack(Items.POPPED_CHORUS_FRUIT), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.SENSES, 5).add(Aspect.PLANT, 4).add(Aspect.FIRE, 1)
        );
        ThaumcraftApi.registerObjectTag(stack(Items.ICE), new AspectList().add(Aspect.COLD, 20));
        ThaumcraftApi.registerObjectTag(stack(Items.PACKED_ICE), new AspectList().add(Aspect.COLD, 15).add(Aspect.ORDER, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.SNOWBALL), new AspectList().add(Aspect.COLD, 1));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.COOKIE), new AspectList().add(Aspect.DESIRE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.POTION), new AspectList().add(Aspect.WATER, 5).add(Aspect.CRYSTAL, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.TORCH), new AspectList().add(Aspect.LIGHT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.COBWEB), new AspectList().add(Aspect.TRAP, 5).add(Aspect.BEAST, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.FLINT), new AspectList().add(Aspect.EARTH, 5).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.STRING), new AspectList().add(Aspect.BEAST, 5).add(Aspect.CRAFT, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.SLIME_BALL), new AspectList().add(Aspect.WATER, 5).add(Aspect.LIFE, 5).add(Aspect.ALCHEMY, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.LEATHER), new AspectList().add(Aspect.BEAST, 5).add(Aspect.PROTECT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.ROTTEN_FLESH), new AspectList().add(Aspect.MAN, 5).add(Aspect.LIFE, 5).add(Aspect.ENTROPY, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.FEATHER), new AspectList().add(Aspect.FLIGHT, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.BONE), new AspectList().add(Aspect.DEATH, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.EGG), new AspectList().add(Aspect.LIFE, 5).add(Aspect.BEAST, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.SPIDER_EYE), new AspectList().add(Aspect.SENSES, 5).add(Aspect.BEAST, 5).add(Aspect.DEATH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.GUNPOWDER), new AspectList().add(Aspect.FIRE, 10).add(Aspect.ENTROPY, 10).add(Aspect.ALCHEMY, 5));
        // FISH / COOKED_FISH wildcard
        items(
            new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.WATER, 5), Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH
        );
        items(new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5), Items.COOKED_COD, Items.COOKED_SALMON);
        ThaumcraftApi.registerObjectTag(stack(Items.CHICKEN), new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.AIR, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.COOKED_CHICKEN), new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.PORKCHOP), new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.COOKED_PORKCHOP), new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.BEEF), new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.COOKED_BEEF), new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.MUTTON), new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.COOKED_MUTTON), new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.RABBIT), new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.COOKED_RABBIT), new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.RABBIT_HIDE), new AspectList().add(Aspect.BEAST, 5).add(Aspect.PROTECT, 2));
        ThaumcraftApi.registerObjectTag(
            stack(Items.RABBIT_FOOT), new AspectList().add(Aspect.BEAST, 5).add(Aspect.PROTECT, 5).add(Aspect.MOTION, 10).add(Aspect.ALCHEMY, 5)
        );
        ThaumcraftApi.registerObjectTag(stack(Items.BLAZE_ROD), new AspectList().add(Aspect.FIRE, 15).add(Aspect.ENERGY, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.SADDLE), new AspectList().add(Aspect.BEAST, 10).add(Aspect.MOTION, 10).add(Aspect.ORDER, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.ENDER_PEARL), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.MOTION, 15));
        ThaumcraftApi.registerObjectTag(stack(Items.GHAST_TEAR), new AspectList().add(Aspect.UNDEAD, 5).add(Aspect.SOUL, 10).add(Aspect.ALCHEMY, 10));
        // SKULL 0..5
        ThaumcraftApi.registerObjectTag(stack(Items.SKELETON_SKULL), new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.UNDEAD, 10));
        ThaumcraftApi.registerObjectTag(stack(Items.WITHER_SKELETON_SKULL), new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.UNDEAD, 10));
        ThaumcraftApi.registerObjectTag(stack(Items.ZOMBIE_HEAD), new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.MAN, 10));
        ThaumcraftApi.registerObjectTag(stack(Items.PLAYER_HEAD), new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.MAN, 10));
        ThaumcraftApi.registerObjectTag(
            stack(Items.CREEPER_HEAD), new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.ENTROPY, 5).add(Aspect.FIRE, 5)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.DRAGON_HEAD), new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.FIRE, 10).add(Aspect.DARKNESS, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.DRAGON_BREATH), new AspectList().add(Aspect.DARKNESS, 10).add(Aspect.ENTROPY, 10).add(Aspect.FIRE, 10).add(Aspect.ALCHEMY, 10)
        );
        ThaumcraftApi.registerComplexObjectTag(stack(Items.CAULDRON), new AspectList().add(Aspect.ALCHEMY, 15));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.FERMENTED_SPIDER_EYE), new AspectList().add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.BLAZE_POWDER), new AspectList().add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.GLISTERING_MELON_SLICE), new AspectList().add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.MAGMA_CREAM), new AspectList().add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerObjectTag(
            stack(Items.TOTEM_OF_UNDYING), new AspectList().add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10).add(Aspect.LIFE, 25).add(Aspect.UNDEAD, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.SHULKER_SHELL), new AspectList().add(Aspect.PROTECT, 10).add(Aspect.ELDRITCH, 5).add(Aspect.BEAST, 5).add(Aspect.VOID, 5)
        );
        // In TC6 coloured shulker boxes inherited the purple one's aspects (the base in 1.12.2). In 1.21 the base is undyed,
        // and dyeing is a special recipe without ingredients, so inherit from the undyed one.
        for (DyeColor color : DyeColor.values()) {
            ThaumcraftApi.registerObjectTag(stack(color.getName() + "_shulker_box"), new AspectList(stack(Items.SHULKER_BOX)));
        }
        ThaumcraftApi.registerObjectTag(stack(Items.MUSIC_DISC_11), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.DESIRE, 15));
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_13), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.WATER, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_CAT), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.BEAST, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_CHIRP), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_FAR), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.ELDRITCH, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_MALL), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.MAN, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_MELLOHI), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.CRAFT, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_STAL), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.DARKNESS, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_STRAD), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.ENERGY, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_WARD), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.LIFE, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_BLOCKS), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.TOOL, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.MUSIC_DISC_WAIT), new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.TRAP, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.NETHER_STAR), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.MAGIC, 20).add(Aspect.ORDER, 20).add(Aspect.AURA, 10)
        );
        ThaumcraftApi.registerObjectTag(stack(Items.CHAINMAIL_HELMET), new AspectList().add(Aspect.METAL, 42));
        ThaumcraftApi.registerObjectTag(stack(Items.CHAINMAIL_CHESTPLATE), new AspectList().add(Aspect.METAL, 67));
        ThaumcraftApi.registerObjectTag(stack(Items.CHAINMAIL_LEGGINGS), new AspectList().add(Aspect.METAL, 58));
        ThaumcraftApi.registerObjectTag(stack(Items.CHAINMAIL_BOOTS), new AspectList().add(Aspect.METAL, 33));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.PAPER), new AspectList().add(Aspect.MIND, 2));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.ENCHANTED_BOOK), new AspectList(stack(Items.BOOK)));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.BOOKSHELF), new AspectList().add(Aspect.MIND, 8));
        ThaumcraftApi.registerObjectTag(
            stack(Items.DRAGON_EGG),
            new AspectList().add(Aspect.ELDRITCH, 15).add(Aspect.BEAST, 15).add(Aspect.DARKNESS, 15).add(Aspect.MOTION, 15).add(Aspect.MAGIC, 5)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.END_PORTAL_FRAME), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.ENERGY, 10).add(Aspect.MOTION, 10).add(Aspect.MAGIC, 5)
        );
        ThaumcraftApi.registerObjectTag(
            stack(Items.SPAWNER), new AspectList().add(Aspect.BEAST, 20).add(Aspect.MOTION, 20).add(Aspect.UNDEAD, 20).add(Aspect.MAGIC, 20)
        );
        ThaumcraftApi.registerObjectTag(stack(Items.PRISMARINE_SHARD), new AspectList().add(Aspect.WATER, 5).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.PRISMARINE_CRYSTALS), new AspectList().add(Aspect.WATER, 5).add(Aspect.CRYSTAL, 5).add(Aspect.LIGHT, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.ELYTRA), new AspectList().add(Aspect.FLIGHT, 20).add(Aspect.MOTION, 15));
        ThaumcraftApi.registerObjectTag(stack(Items.END_ROD), new AspectList().add(Aspect.FIRE, 1).add(Aspect.LIGHT, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.CHEST), new AspectList().add(Aspect.VOID, 15));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.TRAPPED_CHEST), new AspectList().add(Aspect.TRAP, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.ENDER_EYE), new AspectList().add(Aspect.SENSES, 10).add(Aspect.MAGIC, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.ARROW), new AspectList().add(Aspect.AVERSION, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.GLASS_BOTTLE), new AspectList().add(Aspect.VOID, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.GOLDEN_APPLE), new AspectList().add(Aspect.MAGIC, 5).add(Aspect.LIFE, 10));
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.ENCHANTED_GOLDEN_APPLE), new AspectList().add(Aspect.MAGIC, 5).add(Aspect.LIFE, 15).add(Aspect.PROTECT, 15)
        );
        ThaumcraftApi.registerComplexObjectTag(stack(Items.BOWL), new AspectList().add(Aspect.VOID, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.MUSHROOM_STEW), new AspectList().add(Aspect.LIFE, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.MINECART), new AspectList().add(Aspect.MOTION, 15));
        for (Item door : new Item[]{Items.IRON_DOOR, Items.ACACIA_DOOR, Items.DARK_OAK_DOOR, Items.JUNGLE_DOOR, Items.OAK_DOOR, Items.SPRUCE_DOOR, Items.BIRCH_DOOR}) {
            ThaumcraftApi.registerComplexObjectTag(stack(door), new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
        }
        for (Item boat : new Item[]{Items.OAK_BOAT, Items.ACACIA_BOAT, Items.BIRCH_BOAT, Items.DARK_OAK_BOAT, Items.JUNGLE_BOAT, Items.SPRUCE_BOAT}) {
            ThaumcraftApi.registerComplexObjectTag(stack(boat), new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
        }
        ThaumcraftApi.registerComplexObjectTag(stack(Items.FLINT_AND_STEEL), new AspectList().add(Aspect.FIRE, 10).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.FISHING_ROD), new AspectList().add(Aspect.WATER, 10).add(Aspect.TOOL, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.SHIELD), new AspectList().add(Aspect.PROTECT, 20));
        // TC6 also registered 16 shields with banner colour NBT; in 1.21 these are components, and such shields are found
        // through the "item without components" lookup, so no separate registration is needed.
        ThaumcraftApi.registerComplexObjectTag(stack(Items.SPECTRAL_ARROW), new AspectList().add(Aspect.SENSES, 10).add(Aspect.MAGIC, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.BUCKET), new AspectList().add(Aspect.VOID, 5));
        ThaumcraftApi.registerObjectTag(stack(Items.WATER_BUCKET), new AspectList(stack(Items.BUCKET)).add(Aspect.WATER, 20));
        ThaumcraftApi.registerObjectTag(stack(Items.LAVA_BUCKET), new AspectList(stack(Items.BUCKET)).add(Aspect.FIRE, 15).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(
            stack(Items.MILK_BUCKET), new AspectList(stack(Items.BUCKET)).add(Aspect.LIFE, 10).add(Aspect.BEAST, 5).add(Aspect.WATER, 5)
        );
        ThaumcraftApi.registerComplexObjectTag(stack(Items.BREWING_STAND), new AspectList().add(Aspect.CRAFT, 15).add(Aspect.ALCHEMY, 25));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.STONE_BUTTON), new AspectList().add(Aspect.MECHANISM, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.RAIL), new AspectList().add(Aspect.MOTION, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.DETECTOR_RAIL), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 1));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.POWERED_RAIL), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.ENERGY, 1));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.ACTIVATOR_RAIL), new AspectList().add(Aspect.MECHANISM, 5));
        // Fence gate list as in TC6 (no oak, dark oak twice)
        for (Item gate : new Item[]{Items.ACACIA_FENCE_GATE, Items.DARK_OAK_FENCE_GATE, Items.JUNGLE_FENCE_GATE, Items.SPRUCE_FENCE_GATE, Items.BIRCH_FENCE_GATE}) {
            ThaumcraftApi.registerComplexObjectTag(stack(gate), new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
        }
        for (Item plate : new Item[]{Items.OAK_PRESSURE_PLATE, Items.STONE_PRESSURE_PLATE, Items.LIGHT_WEIGHTED_PRESSURE_PLATE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE}) {
            ThaumcraftApi.registerComplexObjectTag(stack(plate), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 5));
        }
        ThaumcraftApi.registerComplexObjectTag(stack(Items.LEVER), new AspectList().add(Aspect.MECHANISM, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.PISTON), new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MOTION, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.STICKY_PISTON), new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MOTION, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.JUKEBOX), new AspectList().add(Aspect.SENSES, 20).add(Aspect.MECHANISM, 10).add(Aspect.AIR, 15));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.NOTE_BLOCK), new AspectList().add(Aspect.SENSES, 20).add(Aspect.MECHANISM, 10).add(Aspect.AIR, 15));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.OAK_TRAPDOOR), new AspectList().add(Aspect.MOTION, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.FURNACE), new AspectList().add(Aspect.FIRE, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.ENCHANTING_TABLE), new AspectList().add(Aspect.MAGIC, 25).add(Aspect.CRAFT, 15));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.CRAFTING_TABLE), new AspectList().add(Aspect.CRAFT, 20));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.CLOCK), new AspectList().add(Aspect.MECHANISM, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.BEACON), new AspectList().add(Aspect.AURA, 10).add(Aspect.MAGIC, 10).add(Aspect.EXCHANGE, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.OAK_BUTTON), new AspectList().add(Aspect.MECHANISM, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.CARROT_ON_A_STICK), new AspectList().add(Aspect.MOTION, 5).add(Aspect.DESIRE, 10));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.FLOWER_POT), new AspectList().add(Aspect.VOID, 5).add(Aspect.PLANT, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(Items.GOLDEN_CARROT), new AspectList().add(Aspect.SENSES, 10).add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.ENDER_CHEST), new AspectList().merge(Aspect.EXCHANGE, 10).merge(Aspect.MOTION, 10).merge(Aspect.VOID, 20)
        );
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.COMPARATOR), new AspectList().merge(Aspect.MECHANISM, 15).merge(Aspect.ORDER, 5).merge(Aspect.SENSES, 5)
        );
        ThaumcraftApi.registerComplexObjectTag(stack(Items.REPEATER), new AspectList().merge(Aspect.MECHANISM, 15).merge(Aspect.ENERGY, 10));
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.HOPPER), new AspectList().merge(Aspect.MECHANISM, 5).merge(Aspect.EXCHANGE, 10).merge(Aspect.VOID, 5)
        );
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.DROPPER), new AspectList().merge(Aspect.MECHANISM, 5).merge(Aspect.EXCHANGE, 10).merge(Aspect.VOID, 5)
        );
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.DISPENSER), new AspectList().merge(Aspect.MECHANISM, 5).merge(Aspect.EXCHANGE, 10).merge(Aspect.VOID, 5)
        );
        ThaumcraftApi.registerObjectTag(stack(Items.TRIPWIRE_HOOK), new AspectList().add(Aspect.SENSES, 5).add(Aspect.MECHANISM, 5).add(Aspect.TRAP, 5));
        ThaumcraftApi.registerComplexObjectTag(
            stack(Items.DAYLIGHT_DETECTOR), new AspectList().merge(Aspect.SENSES, 10).merge(Aspect.LIGHT, 10).merge(Aspect.MECHANISM, 5)
        );
        ThaumcraftApi.registerComplexObjectTag("c:gears*", new AspectList().add(Aspect.MECHANISM, 5));

        for (Holder.Reference<Potion> potiontype : BuiltInRegistries.POTION.holders().toList()) {
            ItemStack stack = PotionContents.createItemStack(Items.POTION, potiontype);
            ThaumcraftApi.registerObjectTag(stack, getPotionAspects(stack).add(Aspect.WATER, 5));
            ItemStack stack2 = PotionContents.createItemStack(Items.TIPPED_ARROW, potiontype);
            ThaumcraftApi.registerObjectTag(stack2, getPotionAspects(stack2).add(Aspect.AVERSION, 5));
            ItemStack stack3 = PotionContents.createItemStack(Items.SPLASH_POTION, potiontype);
            ThaumcraftApi.registerObjectTag(stack3, getPotionAspects(stack3).add(Aspect.ENERGY, 5));
            ItemStack stack4 = PotionContents.createItemStack(Items.LINGERING_POTION, potiontype);
            ThaumcraftApi.registerObjectTag(stack4, getPotionAspects(stack4).add(Aspect.TRAP, 5));
        }

        // DYE 0, 2, 3, 4, 15: ink sac, green dye, cocoa beans, lapis lazuli, bone meal
        ThaumcraftApi.registerObjectTag(stack(Items.INK_SAC), new AspectList().add(Aspect.WATER, 2).add(Aspect.BEAST, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.GREEN_DYE), new AspectList(stack(Items.CACTUS)).add(Aspect.WATER, 1).add(Aspect.FIRE, 1));
        ThaumcraftApi.registerObjectTag(stack(Items.COCOA_BEANS), new AspectList().add(Aspect.DESIRE, 2).add(Aspect.ENERGY, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.LAPIS_LAZULI), new AspectList().add(Aspect.EARTH, 2).add(Aspect.DESIRE, 2));
        ThaumcraftApi.registerObjectTag(stack(Items.BONE_MEAL), new AspectList().add(Aspect.LIFE, 2).add(Aspect.DEATH, 1).add(Aspect.PLANT, 1));

        registerThaumcraftItemAspects();

        registerModernItemAspects();
    }

    /**
     * Thaumcraft items and blocks (the tail of TC6's registerItemAspects). In TC6 some went through TC's oredict
     * ("clusterIron", "quicksilver", "gemAmber"); here they go straight through the items.
     */
    private static void registerThaumcraftItemAspects() {
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.clusterIron), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5));
        ThaumcraftApi.registerObjectTag(
            stack(ItemsTC.clusterGold), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(ItemsTC.clusterCinnabar),
            new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.ALCHEMY, 5).add(Aspect.DEATH, 5)
        );
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.clusterQuartz), new AspectList().add(Aspect.ORDER, 5).add(Aspect.CRYSTAL, 10));
        // From the metals block at the start of TC6's registerItemAspects ("clusterCopper" etc.)
        ThaumcraftApi.registerObjectTag(
            stack(ItemsTC.clusterCopper), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.EXCHANGE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(ItemsTC.clusterTin), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(ItemsTC.clusterSilver), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10)
        );
        ThaumcraftApi.registerObjectTag(
            stack(ItemsTC.clusterLead), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.ORDER, 10)
        );
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.quicksilver), new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ALCHEMY, 5));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.amber), new AspectList().add(Aspect.TRAP, 10).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.nuggetRareEarth), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ORDER, 5).add(Aspect.METAL, 5));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.salisMundus), new AspectList().add(Aspect.MAGIC, 5).add(Aspect.ENERGY, 5));
        ThaumcraftApi.registerObjectTag("c:ores/cinnabar", new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 10).add(Aspect.ALCHEMY, 5).add(Aspect.DEATH, 5));
        ThaumcraftApi.registerObjectTag("c:ores/amber", new AspectList().add(Aspect.EARTH, 5).add(Aspect.TRAP, 10).add(Aspect.CRYSTAL, 10));
        for (BlockCrystal crystal : ConfigBlocks.CRYSTALS) {
            ThaumcraftApi.registerObjectTag(stack(crystal.asItem()), new AspectList().add(crystal.aspect, 15).add(Aspect.CRYSTAL, 10));
        }
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.logGreatwood.asItem()), new AspectList().add(Aspect.PLANT, 20).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.logSilverwood.asItem()), new AspectList().add(Aspect.PLANT, 20).add(Aspect.AURA, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.leafGreatwood.asItem()), new AspectList().add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.leafSilverwood.asItem()), new AspectList().add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.saplingGreatwood.asItem()), new AspectList().add(Aspect.PLANT, 15).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.saplingSilverwood.asItem()), new AspectList().add(Aspect.PLANT, 15).add(Aspect.AURA, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.shimmerleaf.asItem()), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 10).add(Aspect.ENERGY, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.cinderpearl.asItem()), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 5).add(Aspect.FIRE, 10));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.vishroom.asItem()),
            new AspectList().add(Aspect.PLANT, 2).add(Aspect.DEATH, 1).add(Aspect.MAGIC, 1).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.grassAmbient.asItem()), new AspectList(stack(Items.GRASS_BLOCK)).add(Aspect.LIGHT, 5));
        ThaumcraftApi.registerComplexObjectTag(stack(BlocksTC.tableWood.asItem()), new AspectList().add(Aspect.TOOL, 1));
        ThaumcraftApi.registerComplexObjectTag(stack(BlocksTC.tableStone.asItem()), new AspectList().add(Aspect.TOOL, 1));
        // ConfigAspects:799: a converted table inherits the wooden table and adds MIND 5.
        // This is an explicit source tag, not an invented crafting recipe for research_table.
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.researchTable.asItem()),
            new AspectList(stack(BlocksTC.tableWood.asItem())).add(Aspect.MIND, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.arcaneWorkbench.asItem()),
            new AspectList(stack(Items.CRAFTING_TABLE)).add(Aspect.MAGIC, 5).add(Aspect.AURA, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stoneAncient.asItem()), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stoneAncientTile.asItem()), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stoneAncientRock.asItem()), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stoneEldritchTile.asItem()), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stoneAncientDoorway.asItem()),
            new AspectList().add(Aspect.METAL, 5).add(Aspect.ELDRITCH, 5).add(Aspect.TRAP, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stoneAncientGlyphed.asItem()),
            new AspectList().add(Aspect.METAL, 5).add(Aspect.ELDRITCH, 5).add(Aspect.MIND, 5));
        ThaumcraftApi.registerObjectTag(stack(BlocksTC.stonePorous.asItem()), new AspectList().add(Aspect.EARTH, 5).add(Aspect.VOID, 5));
        // ConfigAspects:820-822 wildcard; only the normal registered book exists in this slice.
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.thaumonomicon),
            new AspectList(stack(Items.BOOKSHELF)).merge(Aspect.MAGIC, 10));
        // Original explicit supplement; recipe-derived contribution waits for stage-5 arcane recipes.
        ThaumcraftApi.registerComplexObjectTag(stack(ItemsTC.thaumometer),
            new AspectList().add(Aspect.SENSES, 10).add(Aspect.AURA, 10));
        // BETA26 ConfigAspects:826 supplements the real goggles recipe, rather than replacing it.
        ThaumcraftApi.registerComplexObjectTag(stack(ItemsTC.goggles),
            new AspectList().merge(Aspect.SENSES, 10).merge(Aspect.AURA, 10));
        // Source ConfigAspects:885 wildcard applies to all thirteen original note metas.
        for (Item note : ItemsTC.celestialNotesVariants) {
            ThaumcraftApi.registerObjectTag(stack(note), new AspectList().add(Aspect.MIND, 5).add(Aspect.DARKNESS, 5).add(Aspect.LIGHT, 5));
        }
        // Source metadata 0..6 flattened without collapsing any knowledge-bearing variant.
        Aspect[] curioAspects = {Aspect.MAGIC, Aspect.BEAST, Aspect.DEATH, Aspect.ELDRITCH, Aspect.MIND, Aspect.FLUX};
        for (int i = 0; i < curioAspects.length; i++)
            reg("thaumcraft:curio_" + thaumcraft.common.items.curios.ItemCurio.VARIANTS.get(i), new AspectList().add(Aspect.MIND, 15).add(curioAspects[i], 15));
        reg("thaumcraft:curio_rites",
            new AspectList().add(Aspect.MIND, 15).add(Aspect.ELDRITCH, 5).add(Aspect.SOUL, 5).add(Aspect.MAGIC, 5));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.brain), new AspectList().add(Aspect.LIFE, 5).add(Aspect.MIND, 20).add(Aspect.UNDEAD, 10));
        reg("thaumcraft:phial_empty", new AspectList().add(Aspect.VOID, 3));
        reg("thaumcraft:phial_filled", new AspectList());
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.tripleMeatTreat), new AspectList().add(Aspect.LIFE, 10).add(Aspect.DESIRE, 10));
        for (String variant : thaumcraft.common.items.consumables.ItemChunksEdible.VARIANTS)
            reg("thaumcraft:chunk_" + variant, new AspectList().add(Aspect.LIFE, 5).add(Aspect.ENTROPY, 1));
        ThaumcraftApi.registerObjectTag(stack(thaumcraft.common.blocks.misc.PurifyingFluidRegistries.BUCKET),
            new AspectList(stack(Items.BUCKET)).add(Aspect.MIND, 15).add(Aspect.ORDER, 15));
        ThaumcraftApi.registerObjectTag(stack(thaumcraft.common.blocks.misc.DeathFluidRegistries.BUCKET),
            new AspectList(stack(Items.BUCKET)).add(Aspect.DEATH, 15).add(Aspect.ENTROPY, 15));
        Item[] plate = {ItemsTC.crimsonPlateHelm, ItemsTC.crimsonPlateChest, ItemsTC.crimsonPlateLegs, ItemsTC.crimsonBoots};
        Item[] iron = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        for (int i = 0; i < plate.length; i++) ThaumcraftApi.registerObjectTag(stack(plate[i]), new AspectList(stack(iron[i])).add(Aspect.ELDRITCH, 5));
        Item[] praetor = {ItemsTC.crimsonPraetorHelm, ItemsTC.crimsonPraetorChest, ItemsTC.crimsonPraetorLegs};
        for (int i = 0; i < praetor.length; i++) ThaumcraftApi.registerObjectTag(stack(praetor[i]),
            new AspectList(stack(iron[i])).add(Aspect.ELDRITCH, 10));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.crimsonBlade),
            new AspectList(stack(Items.IRON_SWORD)).add(Aspect.ELDRITCH, 10).add(Aspect.DEATH, 10));
        // Flattened source lootBag metadata 0/1/2, ConfigAspects:801-803.
        String[] lootBags = {"common", "uncommon", "rare"};
        for (int i = 0; i < lootBags.length; i++) reg("thaumcraft:loot_bag_" + lootBags[i],
            new AspectList().add(Aspect.DESIRE, 10 * (i + 1)));
        Item[] robes = {ItemsTC.crimsonRobeHelm, ItemsTC.crimsonRobeChest, ItemsTC.crimsonRobeLegs};
        Item[] leather = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS};
        for (int i = 0; i < robes.length; i++) ThaumcraftApi.registerObjectTag(stack(robes[i]),
            new AspectList(stack(leather[i])).add(Aspect.MAGIC, 5).add(Aspect.ELDRITCH, 5));
        // Explicit TC6 tags. Other ordinary baubles/gear derive their aspects from the real recipes.
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.amuletVis),
            new AspectList().add(Aspect.AURA, 20).add(Aspect.METAL, 5).add(Aspect.MAGIC, 5));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.ringApprentice),
            new AspectList().add(Aspect.AURA, 5).add(Aspect.METAL, 5).add(Aspect.MAGIC, 20));
        ThaumcraftApi.registerObjectTag(stack(ItemsTC.primordialPearl),
            new AspectList().add(Aspect.AIR, 10).add(Aspect.EARTH, 10).add(Aspect.FIRE, 10)
                .add(Aspect.WATER, 10).add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10));
        ConfigBlockAspects.register();
    }

    private static void reg(String id, AspectList aspects) {
        ThaumcraftApi.registerObjectTag(stack(id), aspects);
    }

    private static AspectList al() {
        return new AspectList();
    }

    private static AspectList scaled(AspectList src, int num, int den) {
        AspectList out = new AspectList();
        for (Aspect a : src.getAspects()) {
            int v = src.getAmount(a) * num / den;
            if (v > 0) {
                out.add(a, v);
            }
        }
        return out;
    }

    /**
     * Not in TC6: items added after 1.12.2 that cannot be derived from recipes (the list comes from GameTest reportMissing).
     * Values are chosen by analogy with similar TC6 items. Derived blocks (slabs, stairs, polished
     * and brick variants) are not listed: they are derived from recipes of the base blocks.
     * Technical items intentionally stay without aspects: command blocks, barrier, light, structure blocks,
     * spawn eggs, debug_stick, knowledge_book.
     */
    private static void registerModernItemAspects() {
        // Stone and earth
        reg("deepslate", al().add(Aspect.EARTH, 5).add(Aspect.DARKNESS, 1));
        reg("cobbled_deepslate", al().add(Aspect.EARTH, 5).add(Aspect.DARKNESS, 1).add(Aspect.ENTROPY, 1));
        reg("calcite", al().add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 1));
        reg("tuff", al().add(Aspect.EARTH, 5).add(Aspect.FIRE, 1));
        reg("dripstone_block", al().add(Aspect.EARTH, 5).add(Aspect.WATER, 1));
        reg("pointed_dripstone", al().add(Aspect.EARTH, 2).add(Aspect.AVERSION, 1));
        reg("rooted_dirt", al().add(Aspect.EARTH, 5).add(Aspect.PLANT, 2));
        reg("mud", al().add(Aspect.EARTH, 5).add(Aspect.WATER, 3));
        reg("crimson_nylium", al().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2).add(Aspect.PLANT, 2));
        reg("warped_nylium", al().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2).add(Aspect.PLANT, 2));
        reg("soul_soil", al().add(Aspect.EARTH, 3).add(Aspect.SOUL, 2));
        reg("basalt", al().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2));
        reg("blackstone", al().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2).add(Aspect.DARKNESS, 1));
        reg("gilded_blackstone", al().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2).add(Aspect.DARKNESS, 1).add(Aspect.DESIRE, 5));
        reg("crying_obsidian", new AspectList(stack(Items.OBSIDIAN)).add(Aspect.SOUL, 5));
        reg("reinforced_deepslate", al().add(Aspect.EARTH, 15).add(Aspect.ORDER, 15).add(Aspect.DARKNESS, 10));
        reg("suspicious_sand", new AspectList(stack(Items.SAND)).add(Aspect.DESIRE, 2));
        reg("suspicious_gravel", new AspectList(stack(Items.GRAVEL)).add(Aspect.DESIRE, 2));
        reg("ancient_debris", al().add(Aspect.EARTH, 5).add(Aspect.METAL, 10).add(Aspect.FIRE, 5).add(Aspect.DESIRE, 5));
        reg("heavy_core", al().add(Aspect.METAL, 20).add(Aspect.EARTH, 20).add(Aspect.ORDER, 10));

        // Raw metals: like the ore without the host rock's "earth"
        reg("raw_iron", al().add(Aspect.METAL, 15).add(Aspect.EARTH, 2));
        reg("raw_gold", al().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10).add(Aspect.EARTH, 2));
        reg("raw_copper", al().add(Aspect.METAL, 10).add(Aspect.EXCHANGE, 5).add(Aspect.EARTH, 2));

        // Amethyst, sculk
        reg("amethyst_shard", al().add(Aspect.CRYSTAL, 5).add(Aspect.SENSES, 5));
        reg("budding_amethyst", al().add(Aspect.CRYSTAL, 15).add(Aspect.LIFE, 5).add(Aspect.MAGIC, 5));
        reg("small_amethyst_bud", al().add(Aspect.CRYSTAL, 1));
        reg("medium_amethyst_bud", al().add(Aspect.CRYSTAL, 2));
        reg("large_amethyst_bud", al().add(Aspect.CRYSTAL, 3).add(Aspect.SENSES, 1));
        reg("amethyst_cluster", al().add(Aspect.CRYSTAL, 5).add(Aspect.SENSES, 5));
        reg("echo_shard", al().add(Aspect.SENSES, 10).add(Aspect.DARKNESS, 5).add(Aspect.ELDRITCH, 5));
        reg("sculk", al().add(Aspect.DARKNESS, 5).add(Aspect.SOUL, 3).add(Aspect.SENSES, 2));
        reg("sculk_vein", al().add(Aspect.DARKNESS, 1).add(Aspect.SOUL, 1));
        reg("sculk_catalyst", al().add(Aspect.DARKNESS, 10).add(Aspect.SOUL, 10).add(Aspect.LIFE, 5));
        reg("sculk_shrieker", al().add(Aspect.SENSES, 10).add(Aspect.DARKNESS, 10).add(Aspect.SOUL, 5).add(Aspect.AIR, 5));
        reg("sculk_sensor", al().add(Aspect.SENSES, 10).add(Aspect.DARKNESS, 5).add(Aspect.MECHANISM, 5));

        // Nether wood and plants, bamboo, mangrove
        for (String wood : new String[]{"crimson_stem", "warped_stem", "crimson_hyphae", "warped_hyphae",
            "stripped_crimson_stem", "stripped_warped_stem", "stripped_crimson_hyphae", "stripped_warped_hyphae"}) {
            reg(wood, al().add(Aspect.PLANT, 20).add(Aspect.FIRE, 2));
        }
        reg("bamboo", al().add(Aspect.PLANT, 2).add(Aspect.AIR, 1));
        reg("mangrove_roots", al().add(Aspect.PLANT, 5).add(Aspect.EARTH, 1));
        reg("muddy_mangrove_roots", al().add(Aspect.PLANT, 3).add(Aspect.EARTH, 3).add(Aspect.WATER, 2));
        reg("crimson_fungus", al().add(Aspect.PLANT, 5).add(Aspect.FIRE, 2).add(Aspect.DARKNESS, 1));
        reg("warped_fungus", al().add(Aspect.PLANT, 5).add(Aspect.FIRE, 2).add(Aspect.DARKNESS, 1));
        reg("crimson_roots", al().add(Aspect.PLANT, 3).add(Aspect.FIRE, 1));
        reg("warped_roots", al().add(Aspect.PLANT, 3).add(Aspect.FIRE, 1));
        reg("nether_sprouts", al().add(Aspect.PLANT, 2).add(Aspect.FIRE, 1));
        reg("weeping_vines", al().add(Aspect.PLANT, 5).add(Aspect.FIRE, 1));
        reg("twisting_vines", al().add(Aspect.PLANT, 5).add(Aspect.FIRE, 1));
        reg("warped_wart_block", al().add(Aspect.PLANT, 5).add(Aspect.FLUX, 2).add(Aspect.ALCHEMY, 3));
        reg("shroomlight", al().add(Aspect.LIGHT, 10).add(Aspect.PLANT, 5));

        // Flowers and plants
        for (String flower : new String[]{"cornflower", "lily_of_the_valley", "torchflower"}) {
            reg(flower, al().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.SENSES, 5));
        }
        reg("wither_rose", al().add(Aspect.PLANT, 5).add(Aspect.DEATH, 5).add(Aspect.SENSES, 5));
        reg("pitcher_plant", al().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.SENSES, 5));
        reg("pink_petals", al().add(Aspect.PLANT, 2).add(Aspect.SENSES, 2));
        reg("spore_blossom", al().add(Aspect.PLANT, 5).add(Aspect.SENSES, 3).add(Aspect.AIR, 2));
        reg("torchflower_seeds", al().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.LIGHT, 1));
        reg("pitcher_pod", al().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
        reg("moss_block", al().add(Aspect.PLANT, 5).add(Aspect.EARTH, 2));
        reg("hanging_roots", al().add(Aspect.PLANT, 2).add(Aspect.EARTH, 1));
        reg("big_dripleaf", al().add(Aspect.PLANT, 5).add(Aspect.WATER, 2));
        reg("small_dripleaf", al().add(Aspect.PLANT, 3).add(Aspect.WATER, 1));
        reg("glow_lichen", al().add(Aspect.PLANT, 2).add(Aspect.LIGHT, 3));
        reg("glow_berries", al().add(Aspect.PLANT, 3).add(Aspect.LIGHT, 3).add(Aspect.LIFE, 2));
        reg("sweet_berries", al().add(Aspect.PLANT, 3).add(Aspect.LIFE, 3).add(Aspect.AVERSION, 1));
        reg("carved_pumpkin", new AspectList(stack(Items.PUMPKIN)));

        // Ocean
        reg("seagrass", al().add(Aspect.PLANT, 3).add(Aspect.WATER, 2));
        reg("kelp", al().add(Aspect.PLANT, 3).add(Aspect.WATER, 3));
        reg("sea_pickle", al().add(Aspect.WATER, 3).add(Aspect.LIGHT, 3).add(Aspect.BEAST, 1));
        for (String coral : new String[]{"tube", "brain", "bubble", "fire", "horn"}) {
            reg(coral + "_coral_block", al().add(Aspect.WATER, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 3));
            reg("dead_" + coral + "_coral_block", al().add(Aspect.EARTH, 5).add(Aspect.DEATH, 2));
            reg(coral + "_coral", al().add(Aspect.WATER, 3).add(Aspect.LIFE, 3));
            reg(coral + "_coral_fan", al().add(Aspect.WATER, 3).add(Aspect.LIFE, 3));
            reg("dead_" + coral + "_coral", al().add(Aspect.DEATH, 2).add(Aspect.EARTH, 1));
            reg("dead_" + coral + "_coral_fan", al().add(Aspect.DEATH, 2).add(Aspect.EARTH, 1));
        }
        reg("nautilus_shell", al().add(Aspect.WATER, 5).add(Aspect.PROTECT, 3).add(Aspect.BEAST, 2));
        reg("heart_of_the_sea", al().add(Aspect.WATER, 15).add(Aspect.MAGIC, 10).add(Aspect.MIND, 5));
        reg("trident", al().add(Aspect.WATER, 10).add(Aspect.AVERSION, 15).add(Aspect.MAGIC, 5));
        reg("glow_ink_sac", al().add(Aspect.WATER, 2).add(Aspect.BEAST, 2).add(Aspect.LIGHT, 3));

        // Animals and mobs
        reg("honeycomb", al().add(Aspect.DESIRE, 3).add(Aspect.BEAST, 2).add(Aspect.ORDER, 1));
        reg("bee_nest", al().add(Aspect.PLANT, 5).add(Aspect.BEAST, 5).add(Aspect.DESIRE, 5));
        reg("turtle_egg", al().add(Aspect.LIFE, 5).add(Aspect.BEAST, 5).add(Aspect.WATER, 2));
        reg("sniffer_egg", al().add(Aspect.LIFE, 10).add(Aspect.BEAST, 10).add(Aspect.EARTH, 5));
        reg("frogspawn", al().add(Aspect.LIFE, 3).add(Aspect.WATER, 3).add(Aspect.BEAST, 1));
        for (String light : new String[]{"ochre_froglight", "verdant_froglight", "pearlescent_froglight"}) {
            reg(light, al().add(Aspect.LIGHT, 10).add(Aspect.BEAST, 3));
        }
        reg("turtle_scute", al().add(Aspect.PROTECT, 5).add(Aspect.BEAST, 5).add(Aspect.WATER, 2));
        reg("armadillo_scute", al().add(Aspect.PROTECT, 5).add(Aspect.BEAST, 5).add(Aspect.EARTH, 2));
        reg("phantom_membrane", al().add(Aspect.FLIGHT, 5).add(Aspect.UNDEAD, 5).add(Aspect.AIR, 3));
        reg("breeze_rod", al().add(Aspect.AIR, 15).add(Aspect.MOTION, 5));
        reg("goat_horn", al().add(Aspect.BEAST, 5).add(Aspect.SENSES, 10).add(Aspect.AIR, 5));
        reg("piglin_head", al().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.BEAST, 5).add(Aspect.DESIRE, 5));

        // Buckets of creatures = water bucket + the creature
        reg("cod_bucket", new AspectList(stack(Items.WATER_BUCKET)).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        reg("salmon_bucket", new AspectList(stack(Items.WATER_BUCKET)).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        reg("tropical_fish_bucket", new AspectList(stack(Items.WATER_BUCKET)).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        reg("pufferfish_bucket", new AspectList(stack(Items.WATER_BUCKET)).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        reg("axolotl_bucket", new AspectList(stack(Items.WATER_BUCKET)).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
        reg("tadpole_bucket", new AspectList(stack(Items.WATER_BUCKET)).add(Aspect.BEAST, 2).add(Aspect.LIFE, 3));
        reg("powder_snow_bucket", new AspectList(stack(Items.BUCKET)).add(Aspect.COLD, 10));

        // Miscellaneous
        reg("bell", al().add(Aspect.METAL, 10).add(Aspect.SENSES, 10).add(Aspect.DESIRE, 5));
        reg("petrified_oak_slab", new AspectList(stack(Items.OAK_SLAB)));
        reg("written_book", new AspectList(stack(Items.WRITABLE_BOOK)).add(Aspect.MIND, 5));
        reg("filled_map", new AspectList(stack(Items.MAP)));
        reg("firework_star", al().add(Aspect.FIRE, 5).add(Aspect.ENTROPY, 5).add(Aspect.SENSES, 5).add(Aspect.LIGHT, 3));
        reg("suspicious_stew", new AspectList(stack(Items.MUSHROOM_STEW)).add(Aspect.MAGIC, 2));
        reg("trial_spawner", al().add(Aspect.BEAST, 20).add(Aspect.MOTION, 20).add(Aspect.UNDEAD, 20).add(Aspect.MAGIC, 20));
        reg("vault", al().add(Aspect.METAL, 15).add(Aspect.DESIRE, 15).add(Aspect.TRAP, 10));
        reg("trial_key", al().add(Aspect.METAL, 5).add(Aspect.DESIRE, 5).add(Aspect.MECHANISM, 5));
        reg("ominous_trial_key", al().add(Aspect.METAL, 5).add(Aspect.DESIRE, 5).add(Aspect.MECHANISM, 5).add(Aspect.ELDRITCH, 5));
        reg("ominous_bottle", new AspectList(stack(Items.GLASS_BOTTLE)).add(Aspect.ELDRITCH, 5).add(Aspect.MAGIC, 5).add(Aspect.ALCHEMY, 5));
        reg("disc_fragment_5", al().add(Aspect.SENSES, 3).add(Aspect.AIR, 1));
        for (String sherd : BuiltInRegistries.ITEM.keySet().stream()
            .filter(k -> k.getNamespace().equals("minecraft") && k.getPath().endsWith("_pottery_sherd"))
            .map(ResourceLocation::getPath).toList()) {
            reg(sherd, al().add(Aspect.EARTH, 3).add(Aspect.FIRE, 1).add(Aspect.MIND, 2));
        }
        for (String pattern : new String[]{"globe_banner_pattern", "piglin_banner_pattern", "flow_banner_pattern", "guster_banner_pattern"}) {
            reg(pattern, al().add(Aspect.CRAFT, 3).add(Aspect.SENSES, 5));
        }
        // Music discs after 1.12.2, following TC6's pattern: senses 15, air 5, desire 10 + a themed aspect
        reg("music_disc_pigstep", al().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.FIRE, 5).add(Aspect.DESIRE, 10));
        reg("music_disc_otherside", al().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.MIND, 5).add(Aspect.DESIRE, 10));
        reg("music_disc_relic", al().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10));
        reg("music_disc_creator", al().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.MAGIC, 5).add(Aspect.DESIRE, 10));
        reg("music_disc_creator_music_box", al().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.MECHANISM, 5).add(Aspect.DESIRE, 10));
        reg("music_disc_precipice", al().add(Aspect.SENSES, 15).add(Aspect.AIR, 10).add(Aspect.DESIRE, 10));

        // Not derivable from recipes (axe stripping, slab cycles, smelting a derived block)
        reg("stripped_bamboo_block", new AspectList(stack(Items.BAMBOO_BLOCK)));
        reg("bamboo_mosaic_slab", al().add(Aspect.PLANT, 1)); // the bamboo recipe chain yields <1
        reg("mud_brick_slab", scaled(new AspectList(stack(Items.MUD_BRICKS)), 1, 2));
        reg("cracked_deepslate_tiles", new AspectList(stack(Items.DEEPSLATE_TILES)));

        // Anvils: damaged ones are a share of the intact one
        AspectList anvil = new AspectList(stack(Items.ANVIL));
        reg("chipped_anvil", scaled(anvil, 2, 3));
        reg("damaged_anvil", scaled(anvil, 1, 3));

        // Infested blocks = base + bestia
        for (String base : new String[]{"stone", "cobblestone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "deepslate"}) {
            reg("infested_" + base, new AspectList(stack(base)).add(Aspect.BEAST, 2));
        }

        // Copper oxidation: stages = original block + entropy 1..3; waxed = unwaxed
        String[] copperBases = {"copper_block", "chiseled_copper", "cut_copper", "cut_copper_stairs", "cut_copper_slab", "copper_door",
            "copper_trapdoor", "copper_grate", "copper_bulb"};
        String[] stages = {"exposed_", "weathered_", "oxidized_"};
        for (String base : copperBases) {
            for (int i = 0; i < stages.length; i++) {
                String name = stages[i] + (base.equals("copper_block") ? "copper" : base);
                if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.withDefaultNamespace(name))) {
                    reg(name, new AspectList(stack(base)).add(Aspect.ENTROPY, i + 1));
                    String waxed = "waxed_" + name;
                    if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.withDefaultNamespace(waxed))) {
                        reg(waxed, new AspectList(stack(name)));
                    }
                }
            }
        }
    }

    public static AspectList getPotionAspects(ItemStack itemstack) {
        AspectList tmp = new AspectList();
        boolean didit = false;
        PotionContents contents = itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        Holder<Potion> potion = contents.potion().orElse(null);
        if (potion != null && !potion.is(Potions.WATER)) {
            HashSet<ItemStack> hs = new HashSet<>();
            ThaumcraftCraftingManager.getPotionReagentsRecursive(potion, hs);

            for (ItemStack his : hs) {
                AspectList tmp2 = ThaumcraftCraftingManager.getObjectTags(his);
                if (tmp2 != null) {
                    for (Aspect a : tmp2.aspects.keySet()) {
                        tmp.add(a, tmp2.getAmount(a));
                    }
                }

                didit = true;
                tmp.add(Aspect.ALCHEMY, 3);
            }

            try {
                for (Aspect a : tmp.aspects.keySet().toArray(new Aspect[0])) {
                    int amt = tmp.getAmount(a);
                    tmp.remove(a, (int)(amt * 0.66));
                }
            } catch (Exception var10) {
            }

            if (!didit) {
                tmp.add(Aspect.MAGIC, 5);
                tmp.add(Aspect.ALCHEMY, 5);
            }
        } else {
            tmp.add(Aspect.WATER, 5);
        }

        return tmp;
    }
}
