package thaumcraft.common.golems;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Read-only source/API provenance; no Minecraft classes are instantiated or initialized. */
class GolemCoreSourceCoverageTest {
    private static final Path SOURCE = Path.of("../reference/tc6/src/thaumcraft");
    private static final Path NATIVE = Path.of("src/main/java/thaumcraft");
    private static final Set<String> AI = Set.of("AIArrowAttack.java", "AIFollowOwner.java", "AIGoto.java", "AIGotoBlock.java", "AIGotoEntity.java",
            "AIGotoHome.java", "AINearestValidTarget.java", "AIOwnerHurtByTarget.java", "AIOwnerHurtTarget.java", "FlightMoveHelper.java",
            "FlightNodeProcessor.java", "GolemNodeProcessor.java", "ITargets.java", "PathNavigateGolemAir.java", "PathNavigateGolemGround.java");

    @Test void everyOriginalAiAndNavigationClassHasANativeImplementation() throws Exception {
        try (var files = Files.list(SOURCE.resolve("common/golems/ai"))) {
            assertEquals(AI, files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".java")).collect(Collectors.toSet()));
        }
        for (String name : AI) assertTrue(Files.isRegularFile(NATIVE.resolve("common/golems/ai").resolve(name)), name);
    }
    private Set<String> interfaceMethods(Path file) throws Exception {
        var matches = Pattern.compile("\\b([A-Za-z_][A-Za-z_0-9]*)\\s*\\([^{};]*\\)\\s*;").matcher(Files.readString(file));
        Set<String> names = new TreeSet<>();
        while (matches.find()) names.add(matches.group(1));
        return names;
    }
    @Test void sourceGolemApiAndTargetApiMethodsAreAllRetained() throws Exception {
        for (String file : new String[]{"api/golems/IGolemAPI.java", "common/golems/ai/ITargets.java"})
            assertEquals(interfaceMethods(SOURCE.resolve(file)), interfaceMethods(NATIVE.resolve(file)), file);
    }
    @Test void commonCoreDoesNotImportClientClassesOrUseUnimplementedExceptions() throws Exception {
        for (Path directory : new Path[]{NATIVE.resolve("api/golems"), NATIVE.resolve("common/golems/ai"), NATIVE.resolve("common/golems/tasks")}) {
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String java = Files.readString(file);
                    assertFalse(java.matches("(?s).*import\\s+(net\\.minecraft\\.client|thaumcraft\\.client)\\..*"), file.toString());
                    assertFalse(java.contains("UnsupportedOperationException"), file.toString());
                }
            }
        }
        assertFalse(Files.readString(NATIVE.resolve("common/golems/EntityThaumcraftGolem.java")).contains("import thaumcraft.client"));
    }
}
