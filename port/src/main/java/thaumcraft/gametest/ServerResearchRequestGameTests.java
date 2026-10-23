package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler.Status;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ServerResearchProgression;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/** Authoritative trusted-server helper against the REAL accepted startup catalog. FakePlayers
 * are unspawned fixtures, NOT real TCP senders; receive(context)/registration/codec are separate
 * boundaries. No GUI, socket, normal book acquisition or complete research-port claim.
 * Mutable API/category substitutions below are short synchronous scopes and always restored.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ServerResearchRequestGameTests {
    @GameTest(template = "empty")
    public static void actualAcceptedFirststepsOwnerContextAdvancesWithExplicitBookFixture(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Request_Start"), other = player(helper, "TC_Request_Other");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("request queues normal sync, not immediate packets"))) {
            // Explicit source marker fixture. This does not prove ordinary thaumonomicon acquisition/use.
            helper.assertTrue(ResearchManager.progressResearch(owner, "!gotthaumonomicon"), "real trusted marker API fixture");
            PlayerDataSync.forget(owner);
            var snapshot = ResearchCatalogRuntime.currentSnapshot(owner.getServer());
            helper.assertTrue(snapshot != null && snapshot.entries().get("FIRSTSTEPS") == ResearchCategories.getResearch("FIRSTSTEPS")
                && snapshot.ownedEntries().contains(snapshot.entries().get("FIRSTSTEPS")), "actual accepted JSON-owned original entry, not a fabricated revision");
            var request = packet(helper, "FIRSTSTEPS", -1);
            var result = ServerResearchRequestHandler.tryHandle(owner, request);
            helper.assertTrue(result.applied() && result.progression().applied(), "accepted request delegates real checked requirements: " + result);
            helper.assertTrue(data.getResearchStage("FIRSTSTEPS") == 1 && !data.isResearchComplete("FIRSTSTEPS")
                && ThaumcraftCapabilities.knowsResearchStrict(owner, "KNOWLEDGETYPES", "!gotdream"), "source start+sibling behavior; unresolved active workbench stage not bypassed");
            var beforeReplay = data.serializeNBT().copy();
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.RATE_LIMITED, "immediate duplicate request consumes protected window without second event/cost");
            helper.assertTrue(data.serializeNBT().equals(beforeReplay) && PlayerDataSync.isKnowledgeDirty(owner), "replay leaves queued owner snapshot/data unchanged");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore) && !PlayerDataSync.isKnowledgeDirty(other), "context owner only, bystander is neither charged nor granted");
        } finally { forget(owner); forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleDigestUnknownOpaqueAndQualifiedKeysNeverReachEventsOrMutateOwner(GameTestHelper helper) {
        ServerPlayer stale = player(helper, "TC_Request_Stale");
        try (var probe = new Probe(stale)) {
            var before = state(stale);
            String digest = digest(helper);
            String different = (digest.charAt(0) == '0' ? "1" : "0") + digest.substring(1);
            var request = new PacketSyncProgressToServer("FIRSTSTEPS", -1, different);
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(stale, request).status() == Status.STALE_CATALOG, "accepted current digest required");
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(stale, packet(helper, "FIRSTSTEPS", -1)).status() == Status.RATE_LIMITED, "stale attempt consumes same cheap limiter");
            helper.assertTrue(probe.calls == 0 && state(stale).equals(before) && !PlayerDataSync.isKnowledgeDirty(stale), "no NBT/event/dirty on stale revision");
        } finally { forget(stale); }
        for (String key : List.of("NO_SUCH_ENTRY", "!gotthaumonomicon", "[#]123", "FIRSTSTEPS@1", "FIRSTSTEPS&&ORE")) {
            var owner = player(helper, "TC_Request_Key");
            try (var probe = new Probe(owner)) {
                var before = state(owner);
                helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, packet(helper, key, -1)).status() == Status.NOT_ACCEPTED_ENTRY, "only exact accepted owned keys: " + key);
                helper.assertTrue(probe.calls == 0 && state(owner).equals(before) && !PlayerDataSync.isKnowledgeDirty(owner), "key rejection adds no OUR mutation/events");
            } finally { forget(owner); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeAddonAndReplacementCannotBorrowAcceptedEntryDigestOrOwnership(GameTestHelper helper) throws Exception {
        var category = ResearchCategories.getResearchCategory("BASICS");
        ResearchEntry accepted = ResearchCategories.getResearch("FIRSTSTEPS");
        ResearchEntry nativeEntry = simple("TC_REQUEST_NATIVE");
        ResearchEntry replaced = simple("FIRSTSTEPS");
        ResearchEntry oldNative = category.research.get(nativeEntry.getKey());
        try {
            category.research.put(nativeEntry.getKey(), nativeEntry);
            var nativeOwner = player(helper, "TC_Request_Addon");
            try (var probe = new Probe(nativeOwner)) {
                var before = state(nativeOwner);
                helper.assertTrue(ServerResearchRequestHandler.tryHandle(nativeOwner, packet(helper, nativeEntry.getKey(), -1)).status() == Status.NOT_ACCEPTED_ENTRY, "native addon metadata is not revisioned by raw JSON digest");
                helper.assertTrue(probe.calls == 0 && state(nativeOwner).equals(before), "no native free-stage grant");
            } finally { forget(nativeOwner); }
            category.research.put("FIRSTSTEPS", replaced);
            var replacementOwner = player(helper, "TC_Request_Replace");
            try (var probe = new Probe(replacementOwner)) {
                var before = state(replacementOwner);
                helper.assertTrue(ServerResearchRequestHandler.tryHandle(replacementOwner, packet(helper, "FIRSTSTEPS", -1)).status() == Status.NOT_ACCEPTED_ENTRY, "same key is insufficient when object identity replaced");
                helper.assertTrue(probe.calls == 0 && state(replacementOwner).equals(before), "no free replacement grant under old accepted digest");
            } finally { forget(replacementOwner); }
        } finally {
            category.research.put("FIRSTSTEPS", accepted);
            if (oldNative == null) category.research.remove(nativeEntry.getKey()); else category.research.put(nativeEntry.getKey(), oldNative);
        }
        var restoredOwner = player(helper, "TC_Request_Restore");
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            ResearchManager.progressResearch(restoredOwner, "!gotthaumonomicon");
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(restoredOwner, packet(helper, "FIRSTSTEPS", -1)).applied(), "restoring exact accepted entry identity restores legitimate request path");
        } finally { forget(restoredOwner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualHiddenNullParentFluxBrowserCannotStartButTrustedScanningApiStillCan(GameTestHelper helper) throws Exception {
        ServerPlayer browser = player(helper, "TC_Request_Hidden"), trusted = player(helper, "TC_Request_Trusted");
        var flux = ResearchCategories.getResearch("FLUX");
        helper.assertTrue(flux.getParents() == null && flux.hasMeta(ResearchEntry.EnumResearchMeta.HIDDEN), "actual source hidden entry with null parents");
        try (var probe = new Probe(browser);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            var before = state(browser);
            var result = ServerResearchRequestHandler.tryHandle(browser, packet(helper, "FLUX", -1));
            helper.assertTrue(result.status() == Status.BROWSER_UNAVAILABLE && result.detail().equals("NOT_VISIBLE"), "unknown hidden/null-parent entry cannot be discovered/granted by browser packet");
            helper.assertTrue(state(browser).equals(before) && probe.calls == 0 && !PlayerDataSync.isKnowledgeDirty(browser), "browser rejection preserves owner");
            helper.assertTrue(ResearchManager.startResearchWithPopup(trusted, "FLUX") && ThaumcraftCapabilities.getKnowledge(trusted).getResearchStage("FLUX") == 1,
                "trusted source scanner start is not incorrectly restricted by browser HIDDEN policy");
            helper.assertTrue(state(browser).equals(before), "trusted other player operation cannot mutate request owner");
        } finally { forget(browser); forget(trusted); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void categoryLockDeniesUnknownStartButKnownIncompleteSourceSearchPathCanAdvance(GameTestHelper helper) throws Exception {
        ServerPlayer unknown = player(helper, "TC_Request_CatNew"), known = player(helper, "TC_Request_CatKnown");
        var category = ResearchCategories.getResearchCategory("BASICS");
        String oldGate = category.researchKey;
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            ResearchManager.progressResearch(unknown, "!gotthaumonomicon");
            ResearchManager.progressResearch(known, "FLUX");
            ResearchManager.progressResearch(known, "f_toomuchflux"); // Explicit trusted scanner-marker fixture.
            PlayerDataSync.forget(unknown); PlayerDataSync.forget(known);
            category.researchKey = "!TC_REQUEST_LOCKED_CATEGORY";
            try (var unknownProbe = new Probe(unknown); var knownProbe = new Probe(known)) {
                var before = state(unknown);
                var result = ServerResearchRequestHandler.tryHandle(unknown, packet(helper, "FIRSTSTEPS", -1));
                helper.assertTrue(result.status() == Status.BROWSER_UNAVAILABLE && result.detail().equals("CATEGORY_LOCKED"), "unknown visible entry still requires accessible category");
                helper.assertTrue(state(unknown).equals(before) && unknownProbe.calls == 0, "locked-category start has no OUR mutation");
                result = ServerResearchRequestHandler.tryHandle(known, packet(helper, "FLUX", 1));
                helper.assertTrue(result.applied() && ThaumcraftCapabilities.getKnowledge(known).getResearchStage("FLUX") == 3,
                    "known incomplete node can use source search/page route across locked tab; actual research requirement checked");
                helper.assertTrue(knownProbe.calls == 1 && unknownProbe.calls == 0, "only contextual known owner progresses");
            }
        } finally { category.researchKey = oldGate; forget(unknown); forget(known); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void parentsAndExpectedStageAreCurrentServerDataNotClientFlags(GameTestHelper helper) throws Exception {
        var noParent = player(helper, "TC_Request_Parent");
        try (var probe = new Probe(noParent)) {
            var before = state(noParent);
            var result = ServerResearchRequestHandler.tryHandle(noParent, packet(helper, "FIRSTSTEPS", -1));
            helper.assertTrue(result.status() == Status.BROWSER_UNAVAILABLE && result.detail().equals("MISSING_PARENTS"), "real source required book marker cannot be supplied as a client boolean");
            helper.assertTrue(probe.calls == 0 && state(noParent).equals(before), "missing-parent request has no progress event/data");
        } finally { forget(noParent); }
        var stale = player(helper, "TC_Request_Stage");
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            ResearchManager.progressResearch(stale, "!gotthaumonomicon");
            PlayerDataSync.forget(stale);
            try (var probe = new Probe(stale)) {
                var before = state(stale);
                var result = ServerResearchRequestHandler.tryHandle(stale, packet(helper, "FIRSTSTEPS", 0));
                helper.assertTrue(result.status() == Status.PROGRESSION_REJECTED && result.progression().status() == ServerResearchProgression.Status.STALE_STAGE,
                    "known0/unknown-1 exact stage not guessed from client first flag");
                helper.assertTrue(probe.calls == 0 && state(stale).equals(before) && !PlayerDataSync.isKnowledgeDirty(stale), "stale expected stage costs/events/dirty unchanged");
            }
        } finally { forget(stale); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void invalidAttemptsUseServerTicksNotWorldClockAndDistinctOwnersHaveIndependentWindows(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Request_Rate"), other = player(helper, "TC_Request_Rate2");
        var request = packet(helper, "TC_REQUEST_INVALID", -1);
        var before = state(owner);
        int initialServerTick = owner.getServer().getTickCount();
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.NOT_ACCEPTED_ENTRY, "first invalid attempt admitted and consumes window");
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(other, request).status() == Status.NOT_ACCEPTED_ENTRY, "another owner's limiter independent in same server tick");
        var level = helper.getLevel();
        var clock = (ServerLevelData) level.getLevelData();
        long gameTime = level.getGameTime(), dayTime = level.getDayTime();
        int ownerTick = owner.tickCount;
        try {
            clock.setGameTime(gameTime + 1_000_000L); level.setDayTime(dayTime + 2_000_000L); owner.tickCount += 10_000;
            helper.assertTrue(owner.getServer().getTickCount() == initialServerTick && ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.RATE_LIMITED,
                "world game/day time and owner tickCount cannot bypass monotonic server tick interval");
        } finally { clock.setGameTime(gameTime); level.setDayTime(dayTime); owner.tickCount = ownerTick; }
        helper.runAfterDelay(4, () -> helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.RATE_LIMITED, "still protected before five real server ticks"));
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(Integer.toUnsignedLong(owner.getServer().getTickCount() - initialServerTick) >= 5, "real server ticks elapsed, not a fake clock setter");
                helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.NOT_ACCEPTED_ENTRY, "window expires after real five ticks even though all requests were invalid");
                helper.assertTrue(state(owner).equals(before) && !PlayerDataSync.isKnowledgeDirty(owner), "rejected and rate-limited attempts never alter owner NBT");
                helper.succeed();
            } finally { forget(owner); forget(other); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void replayAfterIntervalStillFailsExactStoredStageWithoutAnotherEvent(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Request_Replay");
        var request = packet(helper, "FIRSTSTEPS", -1);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            ResearchManager.progressResearch(owner, "!gotthaumonomicon");
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).applied(), "initial real accepted request");
        }
        var before = state(owner);
        helper.runAfterDelay(5, () -> {
            try (var probe = new Probe(owner)) {
                var result = ServerResearchRequestHandler.tryHandle(owner, request);
                helper.assertTrue(result.status() == Status.PROGRESSION_REJECTED && result.progression().status() == ServerResearchProgression.Status.STALE_STAGE,
                    "replay is not authorized again when limiter expires");
                helper.assertTrue(probe.calls == 0 && state(owner).equals(before), "no additional payment/research event or data mutation");
                helper.succeed();
            } finally { forget(owner); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cancelledRequestedEventCanRetryOnlyAfterFiveRealServerTicks(GameTestHelper helper) {
        var owner = player(helper, "TC_Request_Retry");
        ResearchManager.progressResearch(owner, "!gotthaumonomicon"); // Explicit trusted acquisition fixture.
        PlayerDataSync.forget(owner);
        var request = packet(helper, "FIRSTSTEPS", -1);
        var before = state(owner);
        int initialTick = owner.getServer().getTickCount();
        try (var probe = new Probe(owner)) {
            probe.cancelKey = "FIRSTSTEPS";
            var result = ServerResearchRequestHandler.tryHandle(owner, request);
            helper.assertTrue(result.status() == Status.PROGRESSION_REJECTED && !result.progression().applied() && probe.calls == 1,
                "actual cancelled Research event reaches checked service exactly once");
            helper.assertTrue(state(owner).equals(before) && !PlayerDataSync.isKnowledgeDirty(owner), "cancelled attempt has no OUR progress/cost/dirty");
            helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.RATE_LIMITED, "cancelled request consumes limiter rather than granting unbounded event retries");
        }
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(Integer.toUnsignedLong(owner.getServer().getTickCount() - initialTick) >= 5, "real server interval elapsed");
                helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).applied()
                    && ThaumcraftCapabilities.getKnowledge(owner).getResearchStage("FIRSTSTEPS") == 1, "same body may retry a cancelled request; rejection was not permanently replay-cached");
                helper.succeed();
            } finally { forget(owner); }
        });
    }

    /** Separate batch: intentionally invokes server-scoped lifecycle cleanup, which must not
     * reset another rate test while its real five-tick assertion is still pending. */
    @GameTest(template = "empty", batch = "research_request_lifecycle")
    public static void ownerLogoutNewSameUuidSessionAndServerStopCallbacksResetOnlyLimiterState(GameTestHelper helper) {
        var owner = player(helper, "TC_Request_Session");
        var request = packet(helper, "TC_REQUEST_INVALID", -1);
        var before = state(owner);
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.NOT_ACCEPTED_ENTRY, "initial invalid owner attempt");
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.RATE_LIMITED, "same owner object protected");
        ServerPlayer newSession = new FakePlayer(helper.getLevel(), owner.getGameProfile());
        helper.assertTrue(newSession != owner && newSession.getUUID().equals(owner.getUUID()), "explicit same UUID but distinct simulated session object");
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(newSession, request).status() == Status.NOT_ACCEPTED_ENTRY, "new object session is independent, not a durable UUID cooldown");
        ServerResearchRequestHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(owner));
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.NOT_ACCEPTED_ENTRY, "actual production logout callback removes exact session limiter");
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(newSession, request).status() == Status.RATE_LIMITED, "logout does not erase distinct same-UUID session window");
        ServerResearchRequestHandler.stopped(new ServerStoppedEvent(owner.getServer()));
        helper.assertTrue(ServerResearchRequestHandler.tryHandle(owner, request).status() == Status.NOT_ACCEPTED_ENTRY
            && ServerResearchRequestHandler.tryHandle(newSession, request).status() == Status.NOT_ACCEPTED_ENTRY, "production stopped callback removes server-scoped limiter only; catalog still real accepted fixture");
        helper.assertTrue(state(owner).equals(before) && state(newSession).equals(before), "lifecycle callback cleanup never modifies owner research/warp attachments");
        forget(owner); forget(newSession);
        helper.succeed();
    }

    private static PacketSyncProgressToServer packet(GameTestHelper helper, String key, int expectedStage) {
        return new PacketSyncProgressToServer(key, expectedStage, digest(helper));
    }
    private static String digest(GameTestHelper helper) {
        var payload = ResearchCatalogRuntime.currentPayload(helper.getLevel().getServer());
        helper.assertTrue(payload != null && ResearchCatalogRuntime.currentSnapshot(helper.getLevel().getServer()) != null, "actual engine-accepted startup catalog required");
        return payload.digest();
    }
    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
    private static CompoundTag state(ServerPlayer owner) {
        CompoundTag state = new CompoundTag();
        state.put("knowledge", ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().copy());
        state.put("warp", ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy());
        state.putInt("xp", owner.totalExperience); return state;
    }
    private static void forget(ServerPlayer owner) {
        ServerResearchRequestHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(owner));
        PlayerDataSync.forget(owner);
    }
    private static ResearchEntry simple(String key) {
        ResearchEntry entry = new ResearchEntry(); entry.setKey(key); entry.setName("private.test"); entry.setCategory("BASICS");
        ResearchStage stage = new ResearchStage(); stage.setText("private.test"); entry.setStages(new ResearchStage[]{stage}); return entry;
    }
    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        int calls;
        String cancelKey;
        Probe(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }
        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            calls++;
            if (event.getResearchKey().equals(cancelKey)) event.setCanceled(true);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
