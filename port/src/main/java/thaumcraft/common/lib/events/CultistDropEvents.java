package thaumcraft.common.lib.events;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard;

/** Source EntityEvents additional rites drop, in addition to the original cultist loot table. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class CultistDropEvents {
    private static final ResourceLocation RITES = ResourceLocation.parse("thaumcraft:curio_rites");
    private CultistDropEvents() {}
    public static int denominator(Player player) {
        int chance = ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("!CrimsonCultist@2") ? 20 : 4;
        // Source getPlayerSlotFor searches only mainInventory and demands exact NBT.
        for (ItemStack stack : player.getInventory().items) if ("rites".equals(SourceTheorycraftCard.curioType(stack)) && stack.getComponentsPatch().entrySet().isEmpty()) return 50;
        return chance;
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EntityCultist cultist) || !(cultist.level() instanceof ServerLevel level)
            || !(event.getSource().getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        if (level.getRandom().nextInt(denominator(player)) == 0) {
            if (!BuiltInRegistries.ITEM.containsKey(RITES)) throw new IllegalStateException("Missing source crimson rites curio");
            event.getDrops().add(new ItemEntity(level, cultist.getX(), cultist.getY() + cultist.getEyeHeight(), cultist.getZ(), new ItemStack(BuiltInRegistries.ITEM.get(RITES))));
        }
    }
}
