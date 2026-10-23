package thaumcraft.common.entities;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Original floaty item: cancel dry gravity and damp upward motion, explosion immune. */
public class EntitySpecialItem extends ItemEntity {
    public EntitySpecialItem(EntityType<? extends ItemEntity> type, Level level) { super(type, level); }
    public EntitySpecialItem(Level level, double x, double y, double z, ItemStack stack) {
        this(SpecialItemRegistries.TYPE, level); setPos(x, y, z); setItem(stack);
        setYRot((float)(Math.random() * 360));
        setDeltaMovement((float)(Math.random() * .2F - .1F), .2F, (float)(Math.random() * .2F - .1F));
    }
    @Override protected double getDefaultGravity() { return .04F; }
    @Override public void tick() {
        // Both native levels, like the source World, advance this counter before tick().
        if (tickCount <= 1) return;
        var motion = getDeltaMovement();
        setDeltaMovement(motion.x, (motion.y > 0 ? motion.y * .9F : motion.y) + .04F, motion.z);
        super.tick();
    }
    @Override public boolean hurt(DamageSource source, float amount) { return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurt(source, amount); }
}
