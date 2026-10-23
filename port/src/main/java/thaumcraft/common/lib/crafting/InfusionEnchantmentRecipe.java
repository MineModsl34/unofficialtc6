package thaumcraft.common.lib.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.IRechargable;
import thaumcraft.common.items.baubles.ItemBaubles;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Source eight infusion enchantments, not vanilla Enchantment substitutes. */
public final class InfusionEnchantmentRecipe extends InfusionRecipe {
    public static final MapCodec<InfusionEnchantmentRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        EnumInfusionEnchantment.CODEC.fieldOf("enchantment").forGetter(recipe -> recipe.enchantment),
        CrucibleAspectCosts.CODEC.fieldOf("aspects").forGetter(InfusionRecipe::getAspects),
        Ingredient.CODEC_NONEMPTY.listOf(0, MAX_COMPONENTS).fieldOf("components").forGetter(recipe -> List.copyOf(recipe.components))
    ).apply(instance, (enchantment, costs, components) -> new InfusionEnchantmentRecipe(enchantment, costs, components.toArray())));
    public final EnumInfusionEnchantment enchantment;
    public InfusionEnchantmentRecipe(EnumInfusionEnchantment enchantment, AspectList costs, Object... ingredients) {
        super(enchantment.research, null, 4, costs, Ingredient.EMPTY, ingredients); this.enchantment = enchantment;
    }
    public InfusionEnchantmentRecipe(InfusionEnchantmentRecipe original, ItemStack input) {
        super(original.enchantment.research, null, original.instability, original.aspects, input, original.components.toArray()); enchantment = original.enchantment;
    }
    @Override public boolean matches(List<ItemStack> items, ItemStack central, Level level, Player owner) {
        if (central == null || central.isEmpty() || owner == null || !ThaumcraftCapabilities.knowsResearch(owner, research)) return false;
        int current = EnumInfusionEnchantment.getInfusionEnchantmentLevel(central, enchantment);
        // Negative corrupt legacy levels are stored faithfully but cannot turn paid crafting free.
        return current >= 0 && current < enchantment.maxLevel && accepts(enchantment, central)
            && centralMatches(central) && componentsMatch(items, getComponents());
    }
    public static boolean accepts(EnumInfusionEnchantment enchantment, ItemStack central) {
        var allowed = enchantment.toolClasses;
        if (allowed.contains("all")) return true;
        boolean[] weapon = {false};
        central.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> { if (attribute.equals(Attributes.ATTACK_DAMAGE)) weapon[0] = true; });
        if (weapon[0] && allowed.contains("weapon")) return true;
        if (central.getItem() instanceof AxeItem && allowed.contains("axe")) return true;
        if (central.getItem() instanceof PickaxeItem && allowed.contains("pickaxe")) return true;
        if (central.getItem() instanceof ShovelItem && allowed.contains("shovel")) return true;
        // ItemTool.getToolClasses was extensible. DiggerItem + native digging abilities is its
        // modern counterpart, including genuine add-on multitools rather than only vanilla classes.
        if (central.getItem() instanceof DiggerItem) {
            if (allowed.contains("axe") && central.canPerformAction(ItemAbilities.AXE_DIG)) return true;
            if (allowed.contains("pickaxe") && central.canPerformAction(ItemAbilities.PICKAXE_DIG)) return true;
            if (allowed.contains("shovel") && central.canPerformAction(ItemAbilities.SHOVEL_DIG)) return true;
        }
        if (central.getItem() instanceof ArmorItem armor) {
            String slot = switch (armor.getEquipmentSlot()) { case HEAD -> "helm"; case CHEST -> "chest"; case LEGS -> "legs"; case FEET -> "boots"; default -> "none"; };
            if (allowed.contains("armor") || allowed.contains(slot)) return true;
        }
        if (central.getItem() instanceof ICurioItem) {
            String slot = central.getItem() instanceof ItemBaubles bauble ? switch (bauble.slotIdentifier()) { case "ring" -> "ring"; case "belt" -> "belt"; case "necklace" -> "amulet"; default -> "none"; } : "none";
            if (allowed.contains("bauble") || allowed.contains(slot)) return true;
        }
        return central.getItem() instanceof IRechargable && allowed.contains("chargable");
    }
    @Override public Object getRecipeOutput(Player owner, ItemStack input, List<ItemStack> components) {
        if (input == null || input.isEmpty()) return null;
        int current = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment);
        if (current < 0 || current >= enchantment.maxLevel) return null;
        ItemStack output = input.copy();
        int roll = owner == null ? java.util.concurrent.ThreadLocalRandom.current().nextInt(10) : owner.getRandom().nextInt(10);
        if (InfusionCosts.extraWarp(EnumInfusionEnchantment.getInfusionEnchantments(input).size(), roll)) {
            CustomData custom = output.get(DataComponents.CUSTOM_DATA); CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
            tag.putByte("TC.WARP", (byte)(1 + tag.getByte("TC.WARP"))); output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        EnumInfusionEnchantment.addInfusionEnchantment(output, enchantment, current + 1);
        return output;
    }
    @Override public AspectList getAspects(Player owner, ItemStack input, List<ItemStack> components) {
        if (input == null || input.isEmpty()) return new AspectList();
        int next = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment) + 1;
        if (next <= 0 || next > enchantment.maxLevel) return new AspectList();
        List<EnumInfusionEnchantment> existing = EnumInfusionEnchantment.getInfusionEnchantments(input);
        int others = existing.size() - (existing.contains(enchantment) ? 1 : 0);
        return InfusionCosts.enchanted(aspects, next, others);
    }
    @Override public RecipeSerializer<?> getSerializer() { return InfusionRecipes.ENCHANTMENT_SERIALIZER; }
}
