package thaumcraft.common.blocks.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.utils.LegacyLootRules;
import thaumcraft.common.lib.utils.LegacyLootRules.Rarity;
import thaumcraft.common.lib.utils.TCLegacyLoot;

/** TC6 crate/urn; contents are dynamic drops, so silk and NeoForge loot modifiers use normal loot tables. */
public class BlockLootTC extends Block {
    public static final ResourceLocation CONTENTS_ID = ResourceLocation.fromNamespaceAndPath("thaumcraft", "loot_container_contents");
    public static final MapCodec<BlockLootTC> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        propertiesCodec(),
        Codec.BOOL.fieldOf("crate").forGetter(BlockLootTC::isCrate),
        Codec.intRange(0, 2).fieldOf("rarity").forGetter(block -> block.rarity.index())
    ).apply(instance, (properties, crate, rarity) -> new BlockLootTC(properties, crate, Rarity.values()[rarity])));

    private static final VoxelShape CRATE = box(1, 0, 1, 15, 14, 15);
    private static final VoxelShape URN = box(2, 1, 2, 14, 13, 14);
    private final boolean crate;
    private final Rarity rarity;
    // Original BlockLoot owns one java.util.Random per registered block singleton,
    // unrelated to world seed/loot RNG. Synchronization serializes whole rolls.
    private final Random lootRandom = new Random();

    public BlockLootTC(Properties properties, boolean crate, Rarity rarity) {
        super(legacyProperties(properties, crate));
        this.crate = crate;
        this.rarity = Objects.requireNonNull(rarity, "rarity");
    }

    private static Properties legacyProperties(Properties properties, boolean crate) {
        properties.mapColor(crate ? MapColor.WOOD : MapColor.STONE)
            .strength(0.15F, 0.0F).sound(crate ? SoundType.WOOD : SoundsTC.URN).noOcclusion()
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false);
        // Material.WOOD.getCanBurn allowed lava ignition. BlockLoot did not
        // register normal FireBlock flammability/spread values, so don't add them.
        if (crate) properties.ignitedByLava();
        return properties;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public boolean isCrate() {
        return crate;
    }

    public Rarity rarity() {
        return rarity;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return crate ? CRATE : URN;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        params.withDynamicDrop(CONTENTS_ID, output -> {
            List<ItemStack> rewards = generateContents(params.getLevel());
            Float radius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
            if (radius != null) {
                rewards = applyExplosionChance(rewards, radius, params.getLevel().random);
            }
            rewards.forEach(output);
        });
        return super.getDrops(state, params);
    }

    /**
     * TC6 always filtered each entire stack with chance 1/radius, including TNT.
     * Modern TNT defaults to no decay and ExplosionDecay is per item count, so
     * neither can reproduce this. This compatibility override affects only these
     * six TC blocks; vanilla explosions, blocks and gamerules are not changed.
     */
    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                   BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) {
            return;
        }
        if (level instanceof ServerLevel serverLevel && state.canDropFromExplosion(level, pos, explosion)) {
            LootParams.Builder params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());
            state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, explosion.getIndirectSourceEntity() instanceof Player);
            state.getDrops(params).forEach(stack -> dropConsumer.accept(stack, pos));
        }
        state.onBlockExploded(level, pos, explosion);
    }

    public static List<ItemStack> applyExplosionChance(List<ItemStack> rewards, float radius, RandomSource worldRandom) {
        List<ItemStack> surviving = new ArrayList<>();
        for (ItemStack reward : rewards) {
            if (LegacyLootRules.survivesExplosion(radius, worldRandom.nextFloat())) {
                surviving.add(reward);
            }
        }
        return surviving;
    }

    public synchronized List<ItemStack> generateContents(ServerLevel level) {
        return generateContents(TCLegacyLoot.create(level.registryAccess()), lootRandom);
    }

    /** Explicit random source for conformance tests; normal gameplay always uses the block-owned source. */
    public List<ItemStack> generateContents(TCLegacyLoot loot, Random random) {
        int count = LegacyLootRules.containerDrawCount(rarity, random);
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int draw = 0; draw < count; draw++) {
            ItemStack reward = loot.generate(rarity, random);
            if (!reward.isEmpty()) {
                stacks.add(reward.copy());
            }
        }
        return stacks;
    }
}
