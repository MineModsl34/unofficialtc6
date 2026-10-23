package thaumcraft.common.items.consumables;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.projectile.EntityAlumentum;

/** Source throw and dispenser behavior. Burn time is the native furnace_fuels data map. */
public final class ItemAlumentum extends Item implements ProjectileItem {
    public ItemAlumentum(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.playSound(SoundEvents.EGG_THROW, .3F, .4F / (level.random.nextFloat() * .4F + .8F));
        if (!level.isClientSide) {
            EntityAlumentum projectile = new EntityAlumentum(level, player);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), -5F, .4F, 2F);
            level.addFreshEntity(projectile);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new EntityAlumentum(level, pos.x(), pos.y(), pos.z());
    }
    @Override public DispenseConfig createDispenseConfig() {
        return DispenseConfig.builder().positionFunction((source, direction) -> DispenserBlock.getDispensePosition(source, .7, Vec3.ZERO))
            .power(1.1F).uncertainty(6F).build();
    }
    @Override public void shoot(Projectile projectile, double x, double y, double z, float velocity, float inaccuracy) {
        // Old BehaviorProjectileDispense raises the aim, not the spawn position.
        projectile.shoot(x, y + .1D, z, velocity, inaccuracy);
    }
}
