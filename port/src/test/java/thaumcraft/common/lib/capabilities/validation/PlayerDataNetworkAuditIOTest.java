package thaumcraft.common.lib.capabilities.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataNetworkAuditIOTest {
    @TempDir Path directory;

    @Test
    void progressAppendsFramesAndFinalReportRemainsSeparateCreateNewDocument() throws Exception {
        Path progress = directory.resolve("client-state.json");
        JsonObject value = new JsonObject();
        value.addProperty("revision", 1);
        PlayerDataNetworkAudit.writeJson(progress, value, false);
        value.addProperty("revision", 2);
        PlayerDataNetworkAudit.writeJson(progress, value, false);
        assertEquals(2, Files.readAllLines(progress, StandardCharsets.UTF_8).size());
        assertEquals(2, PlayerDataNetworkAudit.readProgressJson(progress, 4096).get("revision").getAsInt());
        Path report = directory.resolve("client-report.json");
        PlayerDataNetworkAudit.writeJson(report, value, true);
        assertTrue(Files.readString(report).contains("\n"));
        String originalReport = Files.readString(report);
        value.addProperty("revision", 3);
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.writeJson(report, value, true));
        assertEquals(originalReport, Files.readString(report));
    }

    @Test
    void windowsProgressAppendsWhileAReaderDeniesDeleteSharing() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows sharing regression");
        Path progress = directory.resolve("client-state.json");
        JsonObject value = new JsonObject();
        value.addProperty("revision", 1);
        PlayerDataNetworkAudit.writeJson(progress, value, false);
        String firstFrame = Files.readString(progress);
        try (FileChannel heldReader = FileChannel.open(progress, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            value.addProperty("revision", 2);
            PlayerDataNetworkAudit.writeJson(progress, value, false);
            assertEquals(2, PlayerDataNetworkAudit.readProgressJson(progress, 4096).get("revision").getAsInt());
            assertTrue(heldReader.size() > firstFrame.getBytes(StandardCharsets.UTF_8).length);
            assertTrue(Files.readString(progress).startsWith(firstFrame));
        }
        assertEquals(2, Files.readAllLines(progress).size());
    }

    @Test
    void progressAndFinalHelpersRefuseSymbolicLinkWithoutChangingTarget() throws Exception {
        Path target = directory.resolve("real-state.json");
        Files.writeString(target, "{\"revision\":1}\n");
        Path link = directory.resolve("linked-state.json");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symlink creation unavailable in this test environment: " + unavailable);
            return;
        }
        JsonObject value = new JsonObject();
        value.addProperty("revision", 2);
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.writeJson(link, value, false));
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.writeJson(link, value, true));
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(link, 4096));
        assertEquals("{\"revision\":1}\n", Files.readString(target));
    }

    @Test
    void incompleteFirstFrameAndPartialTailWaitForLfWithoutDiscardingPriorSnapshot() throws Exception {
        Path progress = directory.resolve("server-state.json");
        assertNull(PlayerDataNetworkAudit.readProgressJson(progress, 4096));
        Files.writeString(progress, "{\"revision\":1}", StandardOpenOption.CREATE_NEW);
        assertNull(PlayerDataNetworkAudit.readProgressJson(progress, 4096));
        Files.writeString(progress, "\n{\"revision\":2}\n{\"revision\":3", StandardOpenOption.APPEND);
        assertEquals(2, PlayerDataNetworkAudit.readProgressJson(progress, 4096).get("revision").getAsInt());
        Files.writeString(progress, "}\n", StandardOpenOption.APPEND);
        assertEquals(3, PlayerDataNetworkAudit.readProgressJson(progress, 4096).get("revision").getAsInt());
        Files.writeString(progress, "BROKEN_COMPLETE_FRAME\n", StandardOpenOption.APPEND);
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(progress, 4096));
    }

    @Test
    void boundedTailFindsLatestMaxSizedFrameBehindPartialMaxSizedFrame() throws Exception {
        Path progress = directory.resolve("client-state.json");
        String frame = "{\"value\":\"" + "q".repeat(100) + "\"}\n";
        int bound = frame.getBytes(StandardCharsets.UTF_8).length;
        Files.writeString(progress, "{\"old\":true}\n".repeat(1000) + frame + frame.substring(0, frame.length() - 1));
        assertEquals("q".repeat(100), PlayerDataNetworkAudit.readProgressJson(progress, bound).get("value").getAsString());
        Files.writeString(progress, "\n", StandardOpenOption.APPEND);
        assertEquals("q".repeat(100), PlayerDataNetworkAudit.readProgressJson(progress, bound).get("value").getAsString());
    }

    @Test
    void byteBoundsIncludeLfAndRejectOversizedCompleteIncompleteAndJournal() throws Exception {
        Path progress = directory.resolve("client-state.json");
        Files.writeString(progress, "{}\n");
        assertNotOversized(progress);
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(progress, 2));
        Path incomplete = directory.resolve("incomplete-state.json");
        Files.writeString(incomplete, "1234");
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(incomplete, 4));
        Path journal = directory.resolve("large-state.json");
        try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(33_554_432L); // 32 MiB contract plus one byte; sparse fixture, not a live audit.
            channel.write(ByteBuffer.wrap(new byte[]{'\n'}));
        }
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(journal, 4096));
        JsonObject value = new JsonObject();
        value.addProperty("revision", 1);
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.writeJson(journal, value, false));
    }

    @Test
    void utf8FrameBoundCountsBytesAndMalformedCompletedUtf8Fails() throws Exception {
        Path progress = directory.resolve("client-state.json");
        String frame = "{\"value\":\"Я\"}\n";
        Files.writeString(progress, frame, StandardCharsets.UTF_8);
        int bytes = frame.getBytes(StandardCharsets.UTF_8).length;
        assertEquals("Я", PlayerDataNetworkAudit.readProgressJson(progress, bytes).get("value").getAsString());
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(progress, bytes - 1));
        Path malformed = directory.resolve("malformed-state.json");
        Files.write(malformed, new byte[]{'{', '"', 'x', '"', ':', '"', (byte)0xc3, '"', '}', '\n'});
        assertThrows(Exception.class, () -> PlayerDataNetworkAudit.readProgressJson(malformed, 4096));
    }

    private static void assertNotOversized(Path path) throws Exception {
        assertTrue(PlayerDataNetworkAudit.readProgressJson(path, 3).isJsonObject());
    }
}
