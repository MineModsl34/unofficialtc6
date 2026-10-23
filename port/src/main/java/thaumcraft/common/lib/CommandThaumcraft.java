package thaumcraft.common.lib;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketWarpMessage;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchAdminCommands;

/**
 * The /thaumcraft command (aliases /thaum, /tc). The source's OP-only research/warp plus a modern reload.
 * The aspects subcommand is for debugging and is not in TC6.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class CommandThaumcraft {
    private CommandThaumcraft() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        for (String name : new String[]{"thaumcraft", "thaum", "tc"}) {
            d.register(root(name));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("help").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("/tc research <list|player> <list|all|reset|key>; /tc research <player> revoke <key>; /tc warp <player> <add|set> <amount> [PERM|TEMP]; /tc reload"), false);
                return 1;
            }))
            // Modern reload publishes the validated catalog atomically, never source's clear-first loss.
            .then(Commands.literal("reload").executes(ctx -> ctx.getSource().getServer().getCommands().getDispatcher().execute("reload", ctx.getSource())))
            .then(Commands.literal("research")
                .then(Commands.literal("list").executes(ctx -> {
                    var keys = ResearchCategories.researchCategories.values().stream().flatMap(category -> category.research.keySet().stream()).toList();
                    ctx.getSource().sendSuccess(() -> Component.literal(String.join(", ", keys)), false); return keys.size();
                }))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("list").executes(ctx -> researchList(ctx, EntityArgument.getPlayer(ctx, "player"))))
                    .then(Commands.literal("all").executes(ctx -> researchAll(ctx, EntityArgument.getPlayer(ctx, "player"))))
                    .then(Commands.literal("reset").executes(ctx -> researchReset(ctx, EntityArgument.getPlayer(ctx, "player"))))
                    .then(Commands.literal("revoke").then(Commands.argument("key", StringArgumentType.word()).suggests(CommandThaumcraft::researchSuggestions)
                        .executes(ctx -> researchChange(ctx, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "key"), true))))
                    .then(Commands.argument("key", StringArgumentType.word()).suggests(CommandThaumcraft::researchSuggestions)
                        .executes(ctx -> researchChange(ctx, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "key"), false)))))
            .then(Commands.literal("warp").then(Commands.argument("player", EntityArgument.player())
                .then(warpAction("add", -100, 100)).then(warpAction("set", 0, Integer.MAX_VALUE))))
            .then(Commands.literal("aspects")
                .then(Commands.literal("hand").executes(CommandThaumcraft::aspectsHand))
                .then(Commands.literal("entity")
                    .then(Commands.argument("target", EntityArgument.entity()).executes(CommandThaumcraft::aspectsEntity)))
                .then(Commands.literal("missing").executes(CommandThaumcraft::aspectsMissing))
                .then(Commands.literal("dump").executes(CommandThaumcraft::aspectsDump)))
            .then(Commands.literal("aura")
                .executes(ctx -> aura(ctx, BlockPos.containing(ctx.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> aura(ctx, BlockPosArgument.getBlockPos(ctx, "pos")))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> researchSuggestions(
        CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ResearchCategories.researchCategories.values().stream()
            .flatMap(category -> category.research.keySet().stream()).limit(4096), builder);
    }
    private static LiteralArgumentBuilder<CommandSourceStack> warpAction(String action, int min, int max) {
        return Commands.literal(action).then(Commands.argument("amount", IntegerArgumentType.integer(min, max))
            .executes(ctx -> warp(ctx, action, ""))
            .then(Commands.argument("type", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("PERM", "TEMP"), builder))
                .executes(ctx -> warp(ctx, action, StringArgumentType.getString(ctx, "type")))));
    }
    private static int warp(CommandContext<CommandSourceStack> ctx, String action, String rawType) throws CommandSyntaxException {
        ServerPlayer owner = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        EnumWarpType type = rawType.equalsIgnoreCase("PERM") ? EnumWarpType.PERMANENT
            : rawType.equalsIgnoreCase("TEMP") ? EnumWarpType.TEMPORARY : EnumWarpType.NORMAL;
        var data = ThaumcraftCapabilities.getWarp(owner);
        // Source commands deliberately use storage add/set, NOT gameplay addWarpToPlayer:
        // they do not set event counter or unlock WARP and always use message type0 for add.
        if (action.equals("add")) data.add(type, amount); else data.set(type, amount);
        data.sync(owner);
        if (action.equals("add")) PlayerDataSync.sendWarpMessage(owner, new PacketWarpMessage(amount, (byte)0));
        owner.sendSystemMessage(Component.literal(ctx.getSource().getTextName() + " " + action + " warp: " + amount));
        ctx.getSource().sendSuccess(() -> Component.literal("Success!"), false); return 1;
    }
    private static int researchList(CommandContext<CommandSourceStack> ctx, ServerPlayer owner) {
        var keys = ThaumcraftCapabilities.getKnowledge(owner).getResearchList();
        ctx.getSource().sendSuccess(() -> Component.literal(String.join(", ", keys)), false); return keys.size();
    }
    private static int researchChange(CommandContext<CommandSourceStack> ctx, ServerPlayer owner, String key, boolean revoke) {
        if (key.length() > 256 || ResearchCategories.getResearch(key) == null) {
            ctx.getSource().sendFailure(Component.literal("Research does not exist.")); return 0;
        }
        boolean complete;
        if (revoke) { ResearchAdminCommands.revokeRecursiveResearch(owner, key); complete = true; }
        else complete = ResearchAdminCommands.giveRecursiveResearch(owner, key);
        ThaumcraftCapabilities.getKnowledge(owner).sync(owner);
        if (!complete) { ctx.getSource().sendFailure(Component.literal("Research grant rejected: malformed metadata, cancelled event or unavailable reward content.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Success!"), false); return 1;
    }
    private static int researchAll(CommandContext<CommandSourceStack> ctx, ServerPlayer owner) {
        int complete = 0, total = 0;
        for (var category : ResearchCategories.researchCategories.values()) for (var entry : category.research.values()) {
            total++; if (ResearchAdminCommands.giveRecursiveResearch(owner, entry.getKey())) complete++;
        }
        ThaumcraftCapabilities.getKnowledge(owner).sync(owner);
        int accepted = complete, count = total;
        ctx.getSource().sendSuccess(() -> Component.literal("Completed research: " + accepted + "/" + count + ". Unavailable content is not fabricated."), false);
        return complete;
    }
    private static int researchReset(CommandContext<CommandSourceStack> ctx, ServerPlayer owner) {
        var data = ThaumcraftCapabilities.getKnowledge(owner); data.clear();
        for (var category : ResearchCategories.researchCategories.values()) for (var entry : category.research.values())
            if (entry.hasMeta(ResearchEntry.EnumResearchMeta.AUTOUNLOCK)) ResearchManager.completeResearchAsOperator(owner, entry.getKey(), false);
        data.sync(owner); ctx.getSource().sendSuccess(() -> Component.literal("Success!"), false); return 1;
    }

    /** Reads the index only: the command does not load or generate missing chunks. */
    private static int aura(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        AuraChunk aura = AuraHandler.getAuraChunk(ctx.getSource().getLevel(), pos.getX() >> 4, pos.getZ() >> 4);
        if (aura == null) {
            ctx.getSource().sendFailure(Component.literal("No aura in loaded chunk at " + pos.toShortString()));
            return 0;
        }
        String report = String.format(Locale.ROOT, "%s chunk [%d, %d]: base=%d, vis=%.2f, flux=%.2f",
            ctx.getSource().getLevel().dimension().location(), pos.getX() >> 4, pos.getZ() >> 4,
            aura.getBase(), aura.getVis(), aura.getFlux());
        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    private static int aspectsHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
        AspectList aspects = ThaumcraftCraftingManager.getObjectTags(stack);
        ctx.getSource().sendSuccess(() -> Component.literal(stack.getHoverName().getString() + ": " + format(aspects)), false);
        return aspects == null ? 0 : aspects.size();
    }

    private static int aspectsEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "target");
        AspectList aspects = AspectHelper.getEntityAspects(entity);
        ctx.getSource().sendSuccess(() -> Component.literal(entity.getName().getString() + ": " + format(aspects)), false);
        return aspects == null ? 0 : aspects.size();
    }

    /** Items without aspects, a list for ConfigAspects.registerModernItemAspects; written to logs/. */
    private static int aspectsMissing(CommandContext<CommandSourceStack> ctx) {
        List<String> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            AspectList aspects = ThaumcraftCraftingManager.getObjectTags(new ItemStack(item));
            if (aspects == null || aspects.size() == 0) {
                missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }

        Path out = ctx.getSource().getServer().getServerDirectory().resolve("logs").resolve("thaumcraft-missing-aspects.txt");
        try {
            Files.createDirectories(out.getParent());
            Files.write(out, missing);
        } catch (IOException e) {
            Thaumcraft.log.error("Could not write {}", out, e);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(missing.size() + " items without aspects, list written to " + out), false);
        return missing.size();
    }

    /** Not in TC6: every item with aspects to logs/thaumcraft-aspects.txt, to compare tables between versions. */
    private static int aspectsDump(CommandContext<CommandSourceStack> ctx) {
        List<String> lines = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            AspectList aspects = item == Items.AIR ? null : ThaumcraftCraftingManager.getObjectTags(new ItemStack(item));
            if (aspects != null && aspects.size() > 0) {
                lines.add(BuiltInRegistries.ITEM.getKey(item) + "	" + format(aspects));
            }
        }

        Path out = ctx.getSource().getServer().getServerDirectory().resolve("logs").resolve("thaumcraft-aspects.txt");
        try {
            Files.createDirectories(out.getParent());
            Files.write(out, lines);
        } catch (IOException e) {
            Thaumcraft.log.error("Could not write {}", out, e);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(lines.size() + " items with aspects, list written to " + out), false);
        return lines.size();
    }

    private static String format(AspectList aspects) {
        if (aspects == null || aspects.size() == 0) {
            return "(no aspects)";
        }
        StringBuilder sb = new StringBuilder();
        for (Aspect a : aspects.getAspectsSortedByAmount()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(a.getTag()).append(' ').append(aspects.getAmount(a));
        }
        return sb.toString();
    }
}
