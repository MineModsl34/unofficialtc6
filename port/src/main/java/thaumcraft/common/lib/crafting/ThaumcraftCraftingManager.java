package thaumcraft.common.lib.crafting;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.internal.CommonInternals;

/**
 * Port of the aspect part of TC6's ThaumcraftCraftingManager.
 *
 * In 1.21 only the server has recipes, so aspect generation from recipes runs only on the
 * server thread (see {@link #setServer}); the client receives the finished table in a sync packet.
 */
public class ThaumcraftCraftingManager {
    static final int ASPECTCAP = 500;
    private static MinecraftServer server;

    /** Called on server start and after /reload; null on shutdown. */
    public static void setServer(MinecraftServer srv) {
        server = srv;
        recipesByOutput = null;
        extended = false;
        CrucibleRecipes.setServer(srv);
        ArcaneWorkbenchRecipes.setServer(srv);
        InfusionRecipes.setServer(srv);
        SourceCraftingRecipeCatalog.setServer(srv);
        SourceSmeltingBonusCatalog.setServer(srv);
        if (srv == null) thaumcraft.common.lib.events.EssentiaHandler.clear();
    }

    private static boolean canGenerate() {
        return server != null && server.isSameThread();
    }

    public static thaumcraft.api.crafting.InfusionRecipe findMatchingInfusionRecipe(java.util.List<ItemStack> components,ItemStack central,net.minecraft.world.entity.player.Player player) {
        return InfusionRecipes.findMatching(components,central,player);
    }
    public static thaumcraft.api.crafting.CrucibleRecipe findMatchingCrucibleRecipe(net.minecraft.world.entity.player.Player player, AspectList aspects, ItemStack catalyst) {
        return CrucibleRecipes.findMatching(player, aspects, catalyst);
    }

    public static AspectList getObjectTags(ItemStack itemstack) {
        return getObjectTags(itemstack, null);
    }

    /**
     * Lookup order as in TC6: exact stack -> stack without damage (meta 32767 in 1.12.2)
     * -> item without components (without NBT in 1.12.2) -> generation from recipes.
     */
    public static AspectList getObjectTags(ItemStack itemstack, ArrayList<String> history) {
        if (itemstack.isEmpty()) {
            return null;
        }

        AspectList tmp = CommonInternals.objectTags.get(CommonInternals.generateUniqueItemstackId(itemstack));
        if (tmp == null) {
            try {
                tmp = CommonInternals.objectTags.get(CommonInternals.generateUniqueItemstackId(undamaged(itemstack)));
                if (tmp == null) {
                    tmp = CommonInternals.objectTags.get(CommonInternals.generateUniqueItemstackIdStripped(itemstack));
                }

                if (tmp == null) {
                    tmp = generateTags(itemstack, history);
                }
            } catch (Exception var6) {
            }
        }

        return capAspects(getBonusTags(itemstack, tmp), ASPECTCAP);
    }

    /** Apply the source item/component bonuses to an already received raw tag value.
     * Does not read, generate or modify either side's registration table. */
    public static AspectList applyObjectTagBonuses(ItemStack stack, AspectList registered) {
        return capAspects(getBonusTags(stack, registered), ASPECTCAP);
    }

    private static ItemStack undamaged(ItemStack stack) {
        ItemStack sc = stack.copyWithCount(1);
        if (sc.isDamageableItem()) {
            // The default value removes DAMAGE from the component patch, the analogue of meta 32767
            sc.set(DataComponents.DAMAGE, 0);
        }
        return sc;
    }

    private static AspectList capAspects(AspectList sourcetags, int amount) {
        if (sourcetags == null) {
            return sourcetags;
        }

        AspectList out = new AspectList();

        for (Aspect aspect : sourcetags.getAspects()) {
            if (aspect != null) {
                out.merge(aspect, Math.min(amount, sourcetags.getAmount(aspect)));
            }
        }

        return out;
    }

    private static AspectList getBonusTags(ItemStack itemstack, AspectList sourcetags) {
        AspectList tmp = new AspectList();
        if (itemstack.isEmpty()) {
            return tmp;
        }

        Item item = itemstack.getItem();
        if (item instanceof IEssentiaContainerItem container && !container.ignoreContainedAspects()) {
            if (sourcetags != null) {
                // TC6 cleared the list from the objectTags table itself here; a copy keeps the table intact
                sourcetags = new AspectList();
            }

            tmp = container.getAspects(itemstack);
            if (tmp != null && tmp.size() > 0) {
                tmp = tmp.copy();
                for (Aspect tag : tmp.copy().getAspects()) {
                    if (tmp.getAmount(tag) <= 0) {
                        tmp.remove(tag);
                    }
                }
            }
        }

        if (tmp == null) {
            tmp = new AspectList();
        }

        if (sourcetags != null) {
            for (Aspect tag : sourcetags.getAspects()) {
                if (tag != null) {
                    tmp.add(tag, sourcetags.getAmount(tag));
                }
            }
        }

        if (item instanceof ArmorItem armor) {
            tmp.merge(Aspect.PROTECT, armor.getDefense() * 4);
        } else if (item instanceof SwordItem sword && sword.getTier().getAttackDamageBonus() + 1.0F > 0.0F) {
            tmp.merge(Aspect.AVERSION, (int)(sword.getTier().getAttackDamageBonus() + 1.0F) * 4);
        } else if (item instanceof BowItem) {
            tmp.merge(Aspect.AVERSION, 10).merge(Aspect.FLIGHT, 5);
        } else if (item instanceof ShearsItem || item instanceof HoeItem) {
            // In 1.12.2 the hoe was not an ItemTool and was counted by durability, like shears; keep that
            int maxDamage = itemstack.getMaxDamage();
            if (maxDamage <= Tiers.WOOD.getUses()) {
                tmp.merge(Aspect.TOOL, 4);
            } else if (maxDamage <= Tiers.STONE.getUses() || maxDamage <= Tiers.GOLD.getUses()) {
                tmp.merge(Aspect.TOOL, 8);
            } else if (maxDamage <= Tiers.IRON.getUses()) {
                tmp.merge(Aspect.TOOL, 12);
            } else {
                tmp.merge(Aspect.TOOL, 16);
            }
        } else if (item instanceof DiggerItem tool) {
            tmp.merge(Aspect.TOOL, (harvestLevel(tool.getTier()) + 1) * 4);
        }

        // Ore Dictionary "dye*" -> tag c:dyes
        if (itemstack.is(Tags.Items.DYES)) {
            tmp.merge(Aspect.SENSES, 5);
        }

        ItemEnchantments ench = itemstack.getTagEnchantments();
        ItemEnchantments stored = itemstack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            ench = stored;
        }

        if (!ench.isEmpty()) {
            int var5 = 0;

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : ench.entrySet()) {
                Holder<Enchantment> e = entry.getKey();
                int lvl = entry.getIntValue() * 3;
                addEnchantmentAspects(tmp, e, lvl);
                var5 += rarityBonus(e.value());
                var5 += lvl;
            }

            if (var5 > 0) {
                tmp.merge(Aspect.MAGIC, var5);
            }
        }

        return AspectHelper.cullTags(tmp);
    }

    /** 1.12.2 harvest level from the 1.21 tier (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4). */
    static int harvestLevel(Tier tier) {
        if (tier instanceof Tiers t) {
            return switch (t) {
                case WOOD, GOLD -> 0;
                case STONE -> 1;
                case IRON -> 2;
                case DIAMOND -> 3;
                case NETHERITE -> 4;
            };
        }

        var tag = tier.getIncorrectBlocksForDrops();
        if (tag == BlockTags.INCORRECT_FOR_NETHERITE_TOOL) return 4;
        if (tag == BlockTags.INCORRECT_FOR_DIAMOND_TOOL) return 3;
        if (tag == BlockTags.INCORRECT_FOR_IRON_TOOL) return 2;
        if (tag == BlockTags.INCORRECT_FOR_STONE_TOOL) return 1;
        return 0;
    }

    /** 1.12.2 rarity from the 1.21 weight: COMMON 10, UNCOMMON 5 (+2), RARE 2 (+4), VERY_RARE 1 (+6). */
    private static int rarityBonus(Enchantment e) {
        int w = e.getWeight();
        if (w >= 10) return 0;
        if (w >= 5) return 2;
        if (w >= 2) return 4;
        return 6;
    }

    private static void addEnchantmentAspects(AspectList tmp, Holder<Enchantment> e, int lvl) {
        if (is(e, Enchantments.AQUA_AFFINITY)) {
            tmp.merge(Aspect.WATER, lvl);
        } else if (is(e, Enchantments.BANE_OF_ARTHROPODS)) {
            tmp.merge(Aspect.BEAST, lvl / 2).merge(Aspect.AVERSION, lvl / 2);
        } else if (is(e, Enchantments.BLAST_PROTECTION)) {
            tmp.merge(Aspect.PROTECT, lvl / 2).merge(Aspect.ENTROPY, lvl / 2);
        } else if (is(e, Enchantments.EFFICIENCY)) {
            tmp.merge(Aspect.TOOL, lvl);
        } else if (is(e, Enchantments.FEATHER_FALLING)) {
            tmp.merge(Aspect.FLIGHT, lvl);
        } else if (is(e, Enchantments.FIRE_ASPECT)) {
            tmp.merge(Aspect.FIRE, lvl / 2).merge(Aspect.AVERSION, lvl / 2);
        } else if (is(e, Enchantments.FIRE_PROTECTION)) {
            tmp.merge(Aspect.PROTECT, lvl / 2).merge(Aspect.FIRE, lvl / 2);
        } else if (is(e, Enchantments.FLAME)) {
            tmp.merge(Aspect.FIRE, lvl);
        } else if (is(e, Enchantments.FORTUNE)) {
            tmp.merge(Aspect.DESIRE, lvl);
        } else if (is(e, Enchantments.INFINITY)) {
            tmp.merge(Aspect.CRAFT, lvl);
        } else if (is(e, Enchantments.KNOCKBACK)) {
            tmp.merge(Aspect.AIR, lvl);
        } else if (is(e, Enchantments.LOOTING)) {
            tmp.merge(Aspect.DESIRE, lvl);
        } else if (is(e, Enchantments.POWER)) {
            tmp.merge(Aspect.AVERSION, lvl);
        } else if (is(e, Enchantments.PROJECTILE_PROTECTION)) {
            tmp.merge(Aspect.PROTECT, lvl);
        } else if (is(e, Enchantments.PROTECTION)) {
            tmp.merge(Aspect.PROTECT, lvl);
        } else if (is(e, Enchantments.PUNCH)) {
            tmp.merge(Aspect.AIR, lvl);
        } else if (is(e, Enchantments.RESPIRATION)) {
            tmp.merge(Aspect.AIR, lvl);
        } else if (is(e, Enchantments.SHARPNESS)) {
            tmp.merge(Aspect.AVERSION, lvl);
        } else if (is(e, Enchantments.SILK_TOUCH)) {
            tmp.merge(Aspect.EXCHANGE, lvl);
        } else if (is(e, Enchantments.THORNS)) {
            tmp.merge(Aspect.AVERSION, lvl);
        } else if (is(e, Enchantments.SMITE)) {
            tmp.merge(Aspect.UNDEAD, lvl / 2).merge(Aspect.AVERSION, lvl / 2);
        } else if (is(e, Enchantments.UNBREAKING)) {
            tmp.merge(Aspect.EARTH, lvl);
        } else if (is(e, Enchantments.DEPTH_STRIDER)) {
            tmp.merge(Aspect.WATER, lvl);
        } else if (is(e, Enchantments.LUCK_OF_THE_SEA)) {
            tmp.merge(Aspect.DESIRE, lvl);
        } else if (is(e, Enchantments.LURE)) {
            tmp.merge(Aspect.BEAST, lvl);
        } else if (is(e, Enchantments.FROST_WALKER)) {
            tmp.merge(Aspect.COLD, lvl);
        } else if (is(e, Enchantments.MENDING)) {
            tmp.merge(Aspect.CRAFT, lvl);
        }
        // Enchantments added after 1.12.2 give only "magic" per level and rarity, like unknown enchantments in TC6.
    }

    private static boolean is(Holder<Enchantment> holder, ResourceKey<Enchantment> key) {
        return holder.is(key);
    }

    /**
     * Reagents that brew the potion, recursively down to water. As in TC6, by the first matching recipe
     * in the PotionBrewing list (accessed through the access transformer).
     */
    public static void getPotionReagentsRecursive(Holder<Potion> potion, HashSet<ItemStack> hashSet) {
        if (server == null) {
            return;
        }

        PotionBrewing brewing = server.potionBrewing();
        for (PotionBrewing.Mix<Potion> mix : brewing.potionMixes) {
            if (mix.to().is(potion)) {
                ItemStack[] items = mix.ingredient().getItems();
                if (items.length > 0) {
                    hashSet.add(items[0]);
                }
                if (!mix.from().is(Potions.WATER)) {
                    getPotionReagentsRecursive(mix.from(), hashSet);
                }
                break;
            }
        }
    }

    public static AspectList generateTags(ItemStack is) {
        return generateTags(is, new ArrayList<>());
    }

    public static AspectList generateTags(ItemStack is, ArrayList<String> history) {
        if (!canGenerate()) {
            return null;
        }

        if (history == null) {
            history = new ArrayList<>();
        }

        ItemStack stack = undamaged(is);

        // TC6 had `if (ThaumcraftApi.exists(stack)) return getObjectTags(stack, history);` here,
        // but exists() always returned false (see PORTING.md). Keep the actual behaviour.

        String ss = historyKey(stack);
        if (history.contains(ss)) {
            return null;
        }

        history.add(ss);
        if (history.size() < 100) {
            AspectList ret = generateTagsFromRecipes(stack, history);
            ret = capAspects(ret, ASPECTCAP);
            // Not in TC6: an empty result of a nested call may have been cut short by a recipe cycle
            // (slab <-> chiselled block); don't cache it, or the item stays without aspects forever
            if ((ret == null || ret.size() == 0) && !history.get(0).equals(ss)) {
                return ret;
            }
            ThaumcraftApi.registerObjectTag(is, ret);
            CommonInternals.generatedTags.add(CommonInternals.generateUniqueItemstackId(is));
            return ret;
        } else {
            return null;
        }
    }

    private static String historyKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + stack.getComponentsPatch().toString();
    }

    private static AspectList generateTagsFromCrucibleRecipes(ItemStack stack, ArrayList<String> history) {
        var recipe = ThaumcraftApi.getCrucibleRecipe(stack);
        if (recipe == null || recipe.getCatalyst().hasNoItems()) return null;
        ItemStack[] catalysts = recipe.getCatalyst().getItems();
        if (catalysts.length == 0 || catalysts[0].isEmpty()) return null;
        AspectList out = new AspectList();
        AspectList catalystTags = getObjectTags(catalysts[0], history);
        if (catalystTags != null) for (Aspect aspect : catalystTags.getAspects()) out.add(aspect, catalystTags.getAmount(aspect));
        int count = recipe.getRecipeOutput().getCount();
        if (count <= 0) return null;
        for (Aspect aspect : recipe.getAspects().getAspects())
            out.add(aspect, (int) (Math.sqrt(recipe.getAspects().getAmount(aspect)) / count));
        for (Aspect aspect : out.getAspects()) if (out.getAmount(aspect) <= 0) out.remove(aspect);
        return out;
    }

    private static AspectList generateTagsFromInfusionRecipes(ItemStack stack, ArrayList<String> history) {
        var recipe = InfusionRecipes.findByOutput(stack);
        if (recipe == null || !(recipe.getRecipeOutput() instanceof ItemStack output) || output.isEmpty()) return null;
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(recipe.getRecipeInput()); ingredients.addAll(recipe.getComponents());
        AspectList out = getAspectsFromIngredients(ingredients,output,null,history);
        for (Aspect aspect : recipe.getAspects().getAspects()) out.add(aspect,(int)(Math.sqrt(recipe.getAspects().getAmount(aspect)) / output.getCount()));
        for (Aspect aspect : out.copy().getAspects()) if (out.getAmount(aspect) <= 0) out.remove(aspect);
        return out;
    }

    private static AspectList generateTagsFromCraftingRecipes(ItemStack stack, ArrayList<String> history) {
        AspectList ret = null;
        int value = Integer.MAX_VALUE;
        HolderLookup.Provider registries = server.registryAccess();

        List<net.minecraft.world.item.crafting.Recipe<CraftingInput>> recipes = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) recipes.add(holder.value());
        for (var holder : ArcaneWorkbenchRecipes.all(server.overworld())) recipes.add(holder.value());
        for (net.minecraft.world.item.crafting.Recipe<CraftingInput> recipe : recipes) {
            ItemStack output = recipe.getResultItem(registries);
            if (output != null && !output.isEmpty() && output.getItem() == stack.getItem()) {
                try {
                    AspectList ph = getAspectsFromIngredients(recipe.getIngredients(), output, recipe, history);
                    if (recipe instanceof thaumcraft.api.crafting.IArcaneRecipe arcane && arcane.getVis() > 0)
                        ph.add(Aspect.MAGIC, (int) (Math.sqrt(1 + arcane.getVis() / 2) / output.getCount()));

                    for (Aspect as : ph.copy().getAspects()) {
                        if (ph.getAmount(as) <= 0) {
                            ph.remove(as);
                        }
                    }

                    if (ph.visSize() < value && ph.visSize() > 0) {
                        ret = ph;
                        value = ph.visSize();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return ret;
    }

    private static AspectList getAspectsFromIngredients(NonNullList<Ingredient> ingredients, ItemStack recipeOut, net.minecraft.world.item.crafting.Recipe<CraftingInput> recipe, ArrayList<String> history) {
        AspectList out = new AspectList();
        AspectList mid = new AspectList();
        List<ItemStack> exlist = List.of();
        if (recipe != null) {
            List<ItemStack> grid = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                ItemStack[] items = i < ingredients.size() ? ingredients.get(i).getItems() : new ItemStack[0];
                grid.add(items.length > 0 ? items[0].copy() : ItemStack.EMPTY);
            }

            exlist = recipe.getRemainingItems(CraftingInput.of(3, 3, grid));
        }

        for (Ingredient is : ingredients) {
            ItemStack[] items = is.getItems();
            if (items.length > 0) {
                // TC6 always takes the first variant (items[0]). In 1.21 list ingredients often start with
                // a derived item (chiselled quartz instead of the quartz block), which gives a cycle and an empty result,
                // so take the first variant that has aspects.
                AspectList obj = null;
                for (ItemStack candidate : items) {
                    obj = getObjectTags(candidate, history);
                    if (obj != null && obj.size() > 0) {
                        break;
                    }
                }
                if (obj != null) {
                    for (Aspect as : obj.getAspects()) {
                        if (as != null) {
                            mid.add(as, obj.getAmount(as));
                        }
                    }
                }
            }
        }

        for (ItemStack ri : exlist) {
            if (!ri.isEmpty()) {
                AspectList obj = getObjectTags(ri, history);
                if (obj != null) {
                    for (Aspect as : obj.getAspects()) {
                        mid.reduce(as, obj.getAmount(as));
                    }
                }
            }
        }

        for (Aspect as : mid.getAspects()) {
            if (as != null) {
                float v = mid.getAmount(as) * 0.75F / recipeOut.getCount();
                if (v < 1.0F && v > 0.75) {
                    v = 1.0F;
                }

                out.add(as, (int)v);
            }
        }

        for (Aspect as : out.getAspects()) {
            if (out.getAmount(as) <= 0) {
                out.remove(as);
            }
        }

        return out;
    }

    private static AspectList generateTagsFromRecipes(ItemStack stack, ArrayList<String> history) {
        AspectList ret = generateTagsFromCrucibleRecipes(stack, history);
        if (ret != null) {
            return ret;
        }

        ret = generateTagsFromInfusionRecipes(stack, history);
        if (ret != null) {
            return ret;
        }

        ret = generateTagsFromCraftingRecipes(stack, history);
        if (ret != null && ret.size() > 0) {
            return ret;
        }

        // Not in TC6: fallback for 1.21 items obtained only by smelting or the stonecutter
        // (smooth stone, cut copper, blackstone slabs). Same formula as for crafting.
        AspectList alt = generateTagsFromSingleInputRecipes(stack, history, RecipeType.SMELTING);
        if (alt == null || alt.size() == 0) {
            alt = generateTagsFromSingleInputRecipes(stack, history, RecipeType.STONECUTTING);
        }
        // Not in TC6: other mods' machines and altars (fluid ingredients are not counted)
        if (extended && (alt == null || alt.size() == 0)) {
            alt = generateTagsFromSingleInputRecipes(stack, history, null);
        }
        if (alt != null && alt.size() > 0) {
            return alt;
        }
        return ret != null && ret.size() > 0 || !extended ? ret : fallbackTags(stack, history);
    }

    /**
     * Not in TC6: the second pass of the aspect table (see AspectEvents), for items still empty after the normal one:
     * recipes of any type and fallback values. Off in the first pass, or the cheap fallback values would override
     * the normal derivation of already known items (the recipe with the smallest aspect sum wins).
     */
    private static boolean extended;

    public static void setExtendedDerivation(boolean on) {
        extended = on;
    }

    /**
     * Not in TC6: last resort for other mods' natural blocks without tags or recipes (their stone, grass, bricks
     * from the stonecutter), by the tool that mines the block; and for other mods' fluid buckets. The values are small,
     * and everything crafted from them (stairs, slabs) is then derived from recipes.
     */
    private static AspectList fallbackTags(ItemStack stack, ArrayList<String> history) {
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem && stack.getItem() != net.minecraft.world.item.Items.BUCKET) {
            AspectList bucket = getObjectTags(new ItemStack(net.minecraft.world.item.Items.BUCKET), history);
            return bucket == null ? null : bucket.copy().add(Aspect.WATER, 3);
        }
        // machines and containers (blocks with a BlockEntity) only from recipes, not as stone
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)
            || blockItem.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) {
            return null;
        }
        net.minecraft.world.level.block.state.BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return new AspectList().add(Aspect.EARTH, 5);
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return new AspectList().add(Aspect.EARTH, 3).add(Aspect.ENTROPY, 1);
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return new AspectList().add(Aspect.PLANT, 3);
        if (state.is(BlockTags.MINEABLE_WITH_HOE) || state.is(BlockTags.REPLACEABLE_BY_TREES)) return new AspectList().add(Aspect.PLANT, 3);
        if (state.is(BlockTags.IMPERMEABLE)) return new AspectList().add(Aspect.CRYSTAL, 3);
        return null;
    }

    private static AspectList generateTagsFromSingleInputRecipes(ItemStack stack, ArrayList<String> history, RecipeType<?> type) {
        AspectList ret = null;
        int value = Integer.MAX_VALUE;
        HolderLookup.Provider registries = server.registryAccess();

        for (RecipeHolder<?> holder : recipesByOutput().getOrDefault(stack.getItem(), List.of())) {
            // type == null: any recipe type the steps before did not cover (mod machines, altars, ...)
            RecipeType<?> recipeType = holder.value().getType();
            if (type == null ? recipeType == RecipeType.CRAFTING : recipeType != type) {
                continue;
            }
            try {
                ItemStack output = holder.value().getResultItem(registries);
                AspectList ph = getAspectsFromIngredients(holder.value().getIngredients(), output, null, history);
                if (ph.visSize() < value && ph.visSize() > 0) {
                    ret = ph;
                    value = ph.visSize();
                }
            } catch (RuntimeException e) {
                // a mod recipe that cannot list its ingredients outside its machine: skip it
            }
        }

        return ret;
    }

    /** Not in TC6: recipes by result item, built once per aspect table (scanning all recipes for every item is too slow). */
    private static Map<Item, List<RecipeHolder<?>>> recipesByOutput;

    private static Map<Item, List<RecipeHolder<?>>> recipesByOutput() {
        if (recipesByOutput == null) {
            Map<Item, List<RecipeHolder<?>>> map = new HashMap<>();
            HolderLookup.Provider registries = server.registryAccess();
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                try {
                    ItemStack output = holder.value().getResultItem(registries);
                    if (output != null && !output.isEmpty()) map.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(holder);
                } catch (RuntimeException e) {
                    // a result that needs the recipe's machine to be computed: not usable here
                }
            }
            recipesByOutput = map;
        }
        return recipesByOutput;
    }
}
