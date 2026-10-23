package thaumcraft.common.golems;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.common.golems.seals.SealRecovery;

/** Source native interactions, not direct block mutation. The caller transfers ownership of clickStack. */
public final class GolemInteractionHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GameProfile PROFILE = new GameProfile(UUID.nameUUIDFromBytes("Thaumcraft:FakeThaumcraftGolem".getBytes(StandardCharsets.UTF_8)), "FakeThaumcraftGolem");
    private static final Set<FakePlayer> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private GolemInteractionHelper() {}

    public static void golemClick(Level world, IGolemAPI golem, BlockPos pos, Direction face, ItemStack clickStack, boolean sneaking, boolean rightClick) {
        interact(world, golem, pos, face, clickStack, sneaking, rightClick, true);
    }
    /** Replanting pays the actual seed, but its seal awards XP only after successful placement. */
    public static void golemClickForReplant(Level world, IGolemAPI golem, BlockPos pos, Direction face, ItemStack ownedSeed) {
        interact(world, golem, pos, face, ownedSeed, false, true, false);
    }
    private static void interact(Level world, IGolemAPI golem, BlockPos pos, Direction face, ItemStack clickStack, boolean sneaking, boolean rightClick, boolean awardAttempt) {
        if (!(world instanceof ServerLevel level)) return;
        var entity = golem.getGolemEntity();
        if (!level.hasChunkAt(pos) || entity.level() != world || !entity.isAlive()) { returnItem(golem, clickStack); return; }
        // Retain the source one-fake-player-per-world cache, not one heavyweight player per
        // golem. The scoped guard rejects nested borrowing and returns the supplied item.
        FakePlayer player = FakePlayerFactory.get(level, PROFILE);
        if (!ACTIVE.add(player)) { returnItem(golem, clickStack); return; }
        var inventory = player.getInventory();
        ItemStack[] saved = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < saved.length; slot++) saved[slot] = inventory.getItem(slot);
        int selected = inventory.selected;
        Vec3 oldPosition = player.position();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        boolean oldSneaking = player.isShiftKeyDown();
        var oldMenu = player.containerMenu;
        ItemStack oldCursor = player.inventoryMenu.getCarried();
        try {
            inventory.clearContent();
            inventory.selected = 0;
            player.containerMenu = player.inventoryMenu;
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
            player.setItemInHand(InteractionHand.MAIN_HAND, clickStack == null ? ItemStack.EMPTY : clickStack);
            player.setShiftKeyDown(sneaking);
            if (!rightClick) {
                player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, face, level.getMaxBuildHeight(), 0);
            } else {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
                if (player.getMainHandItem().getItem() instanceof BlockItem block && !mayPlace(level, player, block, hit)) {
                    // Source moves the golem away from placement, not the fake player. Use the
                    // real world-space native shape instead of the original unoffset local AABB.
                    var moved = entity.getBoundingBox().move(face.getStepX(), face.getStepY(), face.getStepZ());
                    if (level.noCollision(entity, moved)) entity.setPos(entity.position().add(face.getStepX(), face.getStepY(), face.getStepZ()));
                }
                player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            }
            if (awardAttempt) {
                // Source use-seal awards XP for attempting; replant-seal awards it on success itself.
                golem.addRankXp(1);
                golem.swingArm();
            }
        } catch (RuntimeException error) {
            LOGGER.warn("Golem interaction failed at {}", pos, error);
        } finally {
            try {
                if (player.containerMenu != player.inventoryMenu) player.closeContainer();
                returnItem(golem, player.inventoryMenu.getCarried());
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack remaining = inventory.getItem(slot);
                    inventory.setItem(slot, ItemStack.EMPTY);
                    returnItem(golem, remaining);
                }
            } finally {
                for (int slot = 0; slot < saved.length; slot++) inventory.setItem(slot, saved[slot]);
                inventory.selected = selected;
                player.inventoryMenu.setCarried(oldCursor);
                player.containerMenu = oldMenu;
                player.moveTo(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
                player.setShiftKeyDown(oldSneaking);
                ACTIVE.remove(player);
            }
        }
    }
    private static boolean mayPlace(ServerLevel level, FakePlayer player, BlockItem item, BlockHitResult hit) {
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        var state = item.getBlock().getStateForPlacement(context);
        return state == null || level.isUnobstructed(state, context.getClickedPos(), CollisionContext.empty());
    }
    private static void returnItem(IGolemAPI golem, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var entity = golem.getGolemEntity();
        ItemStack remaining = entity.isAlive() && !entity.isRemoved() ? golem.holdItem(stack) : stack;
        if (!remaining.isEmpty()) {
            BlockPos refundPos = entity.blockPosition();
            var item = new ItemEntity(golem.getGolemWorld(), entity.getX(), entity.getY(), entity.getZ(), remaining);
            item.setDefaultPickUpDelay();
            if (!golem.getGolemWorld().addFreshEntity(item)
                    && SealRecovery.store(golem.getGolemWorld(), refundPos, item.getItem())) {
                // Ownership now belongs only to the serialized queue, not this canceled entity.
                item.setItem(ItemStack.EMPTY);
            }
        }
    }
}
