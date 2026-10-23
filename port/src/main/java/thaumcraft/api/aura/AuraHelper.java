package thaumcraft.api.aura;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;

public class AuraHelper {
    public static float drainVis(Level world, BlockPos pos, float amount, boolean simulate) {
        return ThaumcraftApi.internalMethods.drainVis(world, pos, amount, simulate);
    }

    public static float drainFlux(Level world, BlockPos pos, float amount, boolean simulate) {
        return ThaumcraftApi.internalMethods.drainFlux(world, pos, amount, simulate);
    }

    public static void addVis(Level world, BlockPos pos, float amount) {
        ThaumcraftApi.internalMethods.addVis(world, pos, amount);
    }

    public static float getVis(Level world, BlockPos pos) {
        return ThaumcraftApi.internalMethods.getVis(world, pos);
    }

    public static void polluteAura(Level world, BlockPos pos, float amount, boolean showEffect) {
        ThaumcraftApi.internalMethods.addFlux(world, pos, amount, showEffect);
    }

    public static float getFlux(Level world, BlockPos pos) {
        return ThaumcraftApi.internalMethods.getFlux(world, pos);
    }

    public static int getAuraBase(Level world, BlockPos pos) {
        return ThaumcraftApi.internalMethods.getAuraBase(world, pos);
    }

    public static boolean shouldPreserveAura(Level world, Player player, BlockPos pos) {
        return ThaumcraftApi.internalMethods.shouldPreserveAura(world, player, pos);
    }
}
