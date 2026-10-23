package thaumcraft.common.entities.construct;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
final class ConstructDrops {
    private ConstructDrops(){}
    static int looting(ServerLevel level,DamageSource source){return source.getEntity() instanceof LivingEntity living?EnchantmentHelper.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),living):0;}
    static void roll(EntityOwnedConstruct construct,Item item,float chance){if(construct.getRandom().nextFloat()<chance)construct.spawnAtLocation(new ItemStack(item),.5F);}
}
