package thaumcraft.client.jei;

import java.util.Optional;
import java.util.function.Consumer;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchTransfer;
import thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransfer;
import thaumcraft.common.tiles.crafting.ArcaneWorkbenchRegistries;
import thaumcraft.client.lib.ClientArcaneTransferFeedback;

/** Optional client JEI adapter. Crystal stack quantities are part of the shared planner;
 * JEI's basic transfer treats a slot as a single ingredient and cannot express those costs.
 */
public final class ArcaneRecipeTransferHandler implements IRecipeTransferHandler<ContainerArcaneWorkbench, RecipeHolder<IArcaneRecipe>> {
    private final IRecipeTransferHandlerHelper errors;
    private static int sequence;
    public ArcaneRecipeTransferHandler(IRecipeTransferHandlerHelper errors) { this.errors = errors; }
    @Override public Class<ContainerArcaneWorkbench> getContainerClass() { return ContainerArcaneWorkbench.class; }
    @Override public Optional<MenuType<ContainerArcaneWorkbench>> getMenuType() { return Optional.of(ArcaneWorkbenchRegistries.ARCANE_WORKBENCH_MENU); }
    @Override public RecipeType<RecipeHolder<IArcaneRecipe>> getRecipeType() { return ThaumcraftJeiPlugin.ARCANE; }
    @Override public IRecipeTransferError transferRecipe(ContainerArcaneWorkbench menu, RecipeHolder<IArcaneRecipe> holder,
            IRecipeSlotsView recipeSlots, Player player, boolean maximum, boolean doTransfer) {
        return transfer(menu, holder, player, maximum, doTransfer, ignored -> {});
    }
    @Override public IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<IArcaneRecipe>, ContainerArcaneWorkbench> context, boolean doTransfer) {
        boolean[] completed = {false};
        Consumer<Boolean> complete = accepted -> {
            if (!completed[0]) { completed[0] = true; context.completeRecipeTransfer(accepted ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED); }
        };
        IRecipeTransferError error;
        try { error = transfer(context.getContainer(), context.getRecipe(), context.getPlayer(), context.isMaxTransfer(), doTransfer, complete); }
        catch (RuntimeException invalid) { error = errors.createInternalError(); }
        if (doTransfer && error != null) complete.accept(false);
        return error;
    }
    private IRecipeTransferError transfer(ContainerArcaneWorkbench menu, RecipeHolder<IArcaneRecipe> holder,
            Player player, boolean maximum, boolean doTransfer, Consumer<Boolean> complete) {
        if (player.containerMenu != menu || !menu.stillValid(player) || !ArcaneWorkbenchTransfer.supported(holder.value())) return errors.createInternalError();
        if (!menu.getCarried().isEmpty()) return error("cursor");
        if (ClientArcaneTransferFeedback.busy()) return error("pending");
        if (!ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(holder.value().research())) return error("research");
        var sourceId = JeiRecipeIdentity.sourceId(holder);
        var current = player.level().getRecipeManager().byKey(sourceId).orElse(null);
        if (current == null || current.value() != holder.value()) return errors.createInternalError();
        var plan = ArcaneWorkbenchTransfer.plan(holder.value(), ArcaneWorkbenchTransfer.snapshot(menu), maximum);
        if (!plan.successful()) return error(switch (plan.failure()) {
            case MISSING_ITEMS -> "missing";
            case INVENTORY_FULL -> "space";
            default -> "unsupported";
        });
        if (doTransfer) {
            if (++sequence <= 0) sequence = 1;
            var request = new PacketArcaneRecipeTransfer(menu.containerId, menu.getStateId(), sequence, sourceId, maximum);
            if (!ClientArcaneTransferFeedback.begin(request, menu, complete)) return error("pending");
            try { PacketDistributor.sendToServer(request); }
            catch (RuntimeException failed) { ClientArcaneTransferFeedback.cancel(request.requestId()); return errors.createInternalError(); }
        }
        return null;
    }
    private IRecipeTransferError error(String key) { return errors.createUserErrorWithTooltip(Component.translatable("thaumcraft.jei.transfer." + key)); }
}
