package net.bananemdnsa.historystages.data.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps every way of unlocking a stage behind the logic gate.
 *
 * <p>A stage is unlocked by writing it into {@code StageData} / {@code IndividualStageData}.
 * Anything that does so directly, instead of through {@code StageStates}, skips
 * {@link StageLogicGate} unless it asks the gate itself — the pedestal and the FTB Quests reward
 * both did, and "Blocked while" would have been silently ignored there. Each entry below is a
 * place that writes directly and is known to handle the gate, or to be an admin tool that may
 * skip it on purpose.
 */
class UnlockGateGuardTest {

    private static final Pattern ADD_STAGE = Pattern.compile("\\.addStage\\(");

    private static final Map<String, String> ALLOWED = Map.of(
            "StageStates.java", "the gate itself",
            "ResearchPedestalBlockEntity.java", "asks StageLogicGate at start, per tick and before finishing; creative research is an admin tool",
            "HistoryStageReward.java", "asks StageLogicGate before writing",
            "StageCommand.java", "admin command; the \"*\" unlock-all path is an override by design");

    @Test
    void nothingUnlocksAStagePastTheLogicGate() throws IOException {
        Path main = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages");
        assertTrue(Files.isDirectory(main), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (ALLOWED.containsKey(name)) continue;
                // GameTests set up state on purpose, and the saved-data classes define addStage.
                if (file.toString().contains("gametest")) continue;
                if (file.toString().contains("saveddata")) continue;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("*") || line.startsWith("//")) continue;
                    if (ADD_STAGE.matcher(line).find()) offenders.add(name + ":" + (i + 1));
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these places unlock a stage without going through StageStates, so logic blocks "
                        + "(\"Blocked while\") do not apply there. Route them through StageStates, or ask "
                        + "StageLogicGate and add them here with the reason:\n" + String.join("\n", offenders));
    }
}
