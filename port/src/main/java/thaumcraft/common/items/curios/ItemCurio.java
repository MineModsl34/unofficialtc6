package thaumcraft.common.items.curios;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.research.ResearchManager;

/** TC6 source seven metadata curios flattened into real registered items. */
public final class ItemCurio extends Item {
    public static final List<String> VARIANTS = List.of("arcane", "preserved", "ancient", "eldritch", "knowledge", "twisted", "rites");
    private static final List<String> CATEGORIES = List.of("AUROMANCY", "ALCHEMY", "GOLEMANCY", "ELDRITCH", "INFUSION", "ARTIFICE", "ELDRITCH");
    private final int metadata;
    public ItemCurio(int metadata) {
        super(new Properties().setNoRepair().rarity(Rarity.UNCOMMON));
        if (metadata < 0 || metadata >= VARIANTS.size()) throw new IllegalArgumentException("Unknown source curio variant");
        this.metadata = metadata;
    }
    public int metadata() { return metadata; }
    @Override public String getDescriptionId() { return "item.curio." + VARIANTS.get(metadata); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.curio.text"));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundsTC.learn, SoundSource.NEUTRAL,
            .5F, .4F / (player.getRandom().nextFloat() * .4F + .8F));
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (metadata == 6) {
                var warp = ThaumcraftCapabilities.getWarp(player);
                if (warp.get(EnumWarpType.NORMAL) + warp.get(EnumWarpType.PERMANENT) <= 20) {
                    player.displayClientMessage(Component.translatable("fail.crimsonrites").withStyle(ChatFormatting.DARK_PURPLE), false);
                    return InteractionResultHolder.pass(held); // source early return also skips the use statistic
                }
                if (!ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("CrimsonRites"))
                    ResearchManager.completeResearch(player, "CrimsonRites");
            }
            award(player, ResearchCategories.getResearchCategory(CATEGORIES.get(metadata)));
            if (metadata == 3 || metadata == 6) {
                ResearchManager.addWarpToPlayer(player, 1, EnumWarpType.NORMAL);
                ResearchManager.addWarpToPlayer(player, 5, EnumWarpType.TEMPORARY);
                if (metadata == 6 && player.getRandom().nextBoolean())
                    ResearchManager.addWarpToPlayer(player, 1, EnumWarpType.PERMANENT);
            }
            var categories = ResearchCategories.researchCategories.values().toArray(ResearchCategory[]::new);
            if (categories.length > 0) {
                gain(player, EnumKnowledgeType.OBSERVATION, categories[player.getRandom().nextInt(categories.length)]);
                gain(player, EnumKnowledgeType.THEORY, categories[player.getRandom().nextInt(categories.length)]);
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.displayClientMessage(Component.translatable("tc.knowledge.gained").withStyle(ChatFormatting.DARK_PURPLE), false);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.pass(held); // source calls Item.onItemRightClick, which is PASS
    }
    private static void award(Player player, ResearchCategory category) {
        gain(player, EnumKnowledgeType.OBSERVATION, category);
        gain(player, EnumKnowledgeType.THEORY, category);
    }
    private static void gain(Player player, EnumKnowledgeType type, ResearchCategory category) {
        int progression = type.getProgression();
        int minimum = progression / (type == EnumKnowledgeType.OBSERVATION ? 2 : 3);
        int maximum = progression / (type == EnumKnowledgeType.OBSERVATION ? 1 : 2);
        ResearchManager.addKnowledge(player, type, category, minimum + player.getRandom().nextInt(maximum - minimum + 1));
    }
}
