package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.ItemsTC;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class InfusionRunicAugmentRecipe extends InfusionRecipe {
    public static final MapCodec<InfusionRunicAugmentRecipe> CODEC = Codec.STRING.validate(value -> value.equals("RUNICSHIELDING")
        ? DataResult.success(value) : DataResult.error(() -> "Runic recipe has a fixed source research gate"))
        .optionalFieldOf("research", "RUNICSHIELDING")
        .xmap(ignored -> new InfusionRunicAugmentRecipe(), InfusionRecipe::getResearch);
    public InfusionRunicAugmentRecipe() { super("RUNICSHIELDING", null, 0, null, Ingredient.EMPTY, "c:gems/amber", ItemsTC.salisMundus); }
    public InfusionRunicAugmentRecipe(ItemStack input) { this(); sourceInput = CrucibleRecipe.ingredient(input); components = getComponents(input); }
    public static int getRunicCharge(ItemStack stack) { CustomData data = stack == null || stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA); return data == null ? 0 : data.copyTag().getByte("TC.RUNIC"); }
    @Override public boolean matches(List<ItemStack> items, ItemStack central, Level level, Player owner) {
        if (owner == null || central == null || central.isEmpty() || !ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(research)) return false;
        int charge = getRunicCharge(central);
        // Original signed-byte/exponential overflow can yield a free upgrade: fail closed instead.
        return charge >= 0 && InfusionCosts.runicVis(charge) > 0 && (central.getItem() instanceof ArmorItem || central.getItem() instanceof ICurioItem)
            && centralMatches(central) && componentsMatch(items, getComponents(central));
    }
    public NonNullList<Ingredient> getComponents(ItemStack input) {
        NonNullList<Ingredient> result = NonNullList.create(); result.add(Ingredient.of(ItemsTC.salisMundus));
        for (int index = 0; index <= Math.max(0, getRunicCharge(input)); index++) result.add(CrucibleRecipe.ingredient("c:gems/amber"));
        return result;
    }
    @Override public Object getRecipeOutput(Player owner, ItemStack input, List<ItemStack> components) {
        if (input == null || input.isEmpty() || getRunicCharge(input) < 0 || InfusionCosts.runicVis(getRunicCharge(input)) <= 0) return null;
        ItemStack output = input.copy(); CustomData data = output.get(DataComponents.CUSTOM_DATA); CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putByte("TC.RUNIC", (byte)(getRunicCharge(input) + 1)); output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return output;
    }
    @Override public AspectList getAspects(Player owner, ItemStack input, List<ItemStack> components) {
        AspectList result = new AspectList(); int vis = InfusionCosts.runicVis(getRunicCharge(input));
        if (vis > 0) result.add(Aspect.PROTECT, vis).add(Aspect.CRYSTAL, vis / 2).add(Aspect.ENERGY, vis / 2);
        return result;
    }
    @Override public int getInstability(Player owner, ItemStack input, List<ItemStack> components) { return InfusionCosts.runicInstability(getRunicCharge(input)); }
    @Override public RecipeSerializer<?> getSerializer() { return InfusionRecipes.RUNIC_SERIALIZER; }
}
