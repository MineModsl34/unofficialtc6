package thaumcraft.api.golems;

import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;

/** Source API bridge. Provision requests are logical-server state only. */
public class GolemHelper {
    public static final HashMap<ResourceKey<Level>, ArrayList<ProvisionRequest>> provisionRequests = new HashMap<>();
    static final int LISTLIMIT = 1000;

    public static void registerSeal(ISeal seal) { ThaumcraftApi.internalMethods.registerSeal(seal); }
    public static ISeal getSeal(String key) { return ThaumcraftApi.internalMethods.getSeal(key); }
    public static ItemStack getSealStack(String key) { return ThaumcraftApi.internalMethods.getSealStack(key); }
    public static ISealEntity getSealEntity(ResourceKey<Level> dimension, SealPos pos) {
        return pos == null ? null : ThaumcraftApi.internalMethods.getSealEntity(dimension, pos);
    }
    public static void addGolemTask(ResourceKey<Level> dimension, Task task) {
        ThaumcraftApi.internalMethods.addGolemTask(dimension, task);
    }

    public static void requestProvisioning(Level world, ISealEntity seal, ItemStack stack) {
        if (!world.isClientSide) append(world, new ProvisionRequest(seal, stack));
    }
    public static void requestProvisioning(Level world, BlockPos pos, Direction side, ItemStack stack) {
        if (!world.isClientSide) append(world, new ProvisionRequest(pos, side, stack));
    }
    public static void requestProvisioning(Level world, Entity entity, ItemStack stack) {
        if (!world.isClientSide) append(world, new ProvisionRequest(entity, stack));
    }
    public static void requestProvisioning(Level world, BlockPos pos, Direction side, ItemStack stack, int ui) {
        if (world.isClientSide) return;
        ProvisionRequest request = new ProvisionRequest(pos, side, stack);
        request.setUI(ui);
        append(world, request);
    }
    public static void requestProvisioning(Level world, Entity entity, ItemStack stack, int ui) {
        if (world.isClientSide) return;
        ProvisionRequest request = new ProvisionRequest(entity, stack);
        request.setUI(ui);
        append(world, request);
    }
    private static void append(Level world, ProvisionRequest request) {
        ArrayList<ProvisionRequest> list = provisionRequests.computeIfAbsent(world.dimension(), ignored -> new ArrayList<>());
        if (!list.contains(request)) list.add(request);
        if (list.size() > LISTLIMIT) list.remove(0);
    }

    public static BlockPos getPosInArea(ISealEntity seal, int count) {
        Direction face = seal.getSealPos().face;
        BlockPos area = seal.getArea();
        int xx = 1 + (area.getX() - 1) * (face.getStepX() == 0 ? 2 : 1);
        int yy = 1 + (area.getY() - 1) * (face.getStepY() == 0 ? 2 : 1);
        int zz = 1 + (area.getZ() - 1) * (face.getStepZ() == 0 ? 2 : 1);
        int qx = face.getStepX() != 0 ? face.getStepX() : 1;
        int qy = face.getStepY() != 0 ? face.getStepY() : 1;
        int qz = face.getStepZ() != 0 ? face.getStepZ() : 1;
        int y = qy * (count / zz / xx) % yy + face.getStepY();
        int x = qx * (count / zz) % xx + face.getStepX();
        int z = qz * count % zz + face.getStepZ();
        return seal.getSealPos().pos.offset(x - (face.getStepX() == 0 ? xx / 2 : 0),
                y - (face.getStepY() == 0 ? yy / 2 : 0), z - (face.getStepZ() == 0 ? zz / 2 : 0));
    }

    public static AABB getBoundsForArea(ISealEntity seal) {
        Direction face = seal.getSealPos().face;
        BlockPos area = seal.getArea();
        return new AABB(seal.getSealPos().pos).move(face.getStepX(), face.getStepY(), face.getStepZ())
                .expandTowards(face.getStepX() != 0 ? (area.getX() - 1) * face.getStepX() : 0,
                        face.getStepY() != 0 ? (area.getY() - 1) * face.getStepY() : 0,
                        face.getStepZ() != 0 ? (area.getZ() - 1) * face.getStepZ() : 0)
                .inflate(face.getStepX() == 0 ? area.getX() - 1 : 0,
                        face.getStepY() == 0 ? area.getY() - 1 : 0,
                        face.getStepZ() == 0 ? area.getZ() - 1 : 0);
    }
}
