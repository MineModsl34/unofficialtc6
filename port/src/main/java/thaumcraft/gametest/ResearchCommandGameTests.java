package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketWarpMessage;

/** Actual registered Brigadier dispatcher, explicit @s FakePlayer context; no TCP/admin UI claim. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchCommandGameTests {
    @GameTest(template = "empty")
    public static void permissionAndSourceNumericBoundsRejectWithoutMutation(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Command_Bounds"); var before = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        var dispatcher = owner.getServer().getCommands().getDispatcher();
        for (String command : List.of("tc warp @s add 101", "thaum warp @s add -101", "thaumcraft warp @s set -1")) {
            boolean rejected = false;
            try { dispatcher.execute(command, owner.createCommandSourceStack().withPermission(2)); }
            catch (CommandSyntaxException expected) { rejected = true; }
            helper.assertTrue(rejected && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(before), "source bound rejects before mutation: " + command);
        }
        boolean rejected = false;
        try { dispatcher.execute("tc warp @s add 10", owner.createCommandSourceStack().withPermission(0)); }
        catch (CommandSyntaxException expected) { rejected = true; }
        helper.assertTrue(rejected && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(before), "permission2 not ordinary client grant");
        PlayerDataSync.forget(owner); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceWarpCommandsUseStorageNotGameplayCounterOrUnlockAndAlwaysAddFeedbackTypeZero(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Command_Warp"); var other = player(helper, "TC_Command_Other");
        var data = ThaumcraftCapabilities.getWarp(owner); data.setCounter(7);
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner); knowledge.addResearch("FIRSTSTEPS");
        knowledge.setResearchStage("FIRSTSTEPS", ResearchCategories.getResearch("FIRSTSTEPS").getStages().length + 1);
        var recipients = new ArrayList<ServerPlayer>(); var messages = new ArrayList<PacketWarpMessage>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            recipients.add(recipient); if (packet instanceof PacketWarpMessage message) messages.add(message);
        })) {
            command(owner, "tc warp @s set 20 PERM"); command(owner, "thaum warp @s add 3 TEMP"); command(owner, "thaumcraft warp @s add -5 TEMP");
            helper.assertTrue(data.get(EnumWarpType.PERMANENT) == 20 && data.get(EnumWarpType.TEMPORARY) == 0 && data.getCounter() == 7,
                "source command add/set clamp storage but do NOT replenish event counter");
            helper.assertFalse(knowledge.isResearchKnown("WARP"), "even strict FIRSTSTEPS command set does not call gameplay unlock");
            helper.assertTrue(messages.equals(List.of(new PacketWarpMessage(3, (byte)0), new PacketWarpMessage(-5, (byte)0))),
                "source command always uses type0 and requested delta, not clamped gameplay amount");
            helper.assertTrue(recipients.size() == 5 && recipients.stream().allMatch(recipient -> recipient == owner)
                && ThaumcraftCapabilities.getWarp(other).getCounter() == 0, "three snapshots +two feedback packets owner only");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredOperatorResearchGrantBypassesCostsButRetainsRewardsAndRevokeChildren(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Command_Research"); var data = ThaumcraftCapabilities.getKnowledge(owner);
        var category = ResearchCategories.getResearchCategory("BASICS");
        var parent = entry("TC_COMMAND_PARENT"); var child = entry("TC_COMMAND_CHILD");
        child.setParents(new String[]{parent.getKey()});
        child.getStages()[0].setObtain(new Object[]{new ItemStack(Items.DIAMOND, 2)});
        child.getStages()[0].setKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION, category, 1)});
        child.setRewardItem(new ItemStack[]{new ItemStack(Items.EMERALD)});
        var partial = entry("TC_COMMAND_PARTIAL"); partial.setParents(new String[]{parent.getKey()});
        var previousParent = category.research.put(parent.getKey(), parent); var previousChild = category.research.put(child.getKey(), child);
        var previousPartial = category.research.put(partial.getKey(), partial);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            data.addResearch(partial.getKey()); data.setResearchStage(partial.getKey(), 1);
            helper.assertTrue(command(owner, "tc research @s " + child.getKey()) == 1 && data.isResearchComplete(parent.getKey()) && data.isResearchComplete(child.getKey()),
                "actual OP command recursively grants parent and completes cost-bearing source child");
            helper.assertTrue(owner.getInventory().items.stream().anyMatch(stack -> stack.is(Items.EMERALD))
                && data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 0, "admin bypass gives REAL source reward, no cost or invented knowledge");
            command(owner, "tc research @s revoke " + parent.getKey());
            helper.assertFalse(data.isResearchKnown(parent.getKey()) || data.isResearchKnown(child.getKey()), "source revoke removes completed descendants");
            helper.assertTrue(data.getResearchStage(partial.getKey()) == 1, "source revoke deliberately preserves incomplete child");
            ThaumcraftCapabilities.getWarp(owner).set(EnumWarpType.NORMAL, 4);
            command(owner, "tc research @s reset");
            helper.assertFalse(data.isResearchKnown(partial.getKey()), "reset clears old research");
            helper.assertTrue(ThaumcraftCapabilities.getWarp(owner).get(EnumWarpType.NORMAL) == 4
                && owner.getInventory().items.stream().anyMatch(stack -> stack.is(Items.EMERALD)), "research reset does not reset warp or revoke item rewards");
        } finally {
            restore(category.research, parent.getKey(), previousParent); restore(category.research, child.getKey(), previousChild); restore(category.research, partial.getKey(), previousPartial);
            PlayerDataSync.forget(owner);
        }
        helper.succeed();
    }
    private static ResearchEntry entry(String key) {
        var entry = new ResearchEntry(); entry.setKey(key); entry.setCategory("BASICS"); entry.setName("private.command");
        var stage = new ResearchStage(); stage.setText("private.command"); stage.setKnow(new ResearchStage.Knowledge[0]);
        entry.setStages(new ResearchStage[]{stage}); return entry;
    }
    private static void restore(java.util.Map<String,ResearchEntry> map, String key, ResearchEntry old) { if (old == null) map.remove(key); else map.put(key, old); }
    private static int command(ServerPlayer owner, String command) throws CommandSyntaxException {
        return owner.getServer().getCommands().getDispatcher().execute(command, owner.createCommandSourceStack().withPermission(2));
    }
    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
}
