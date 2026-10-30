package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.api.diagnostics.Problems;
import net.bananemdnsa.historystages.api.diagnostics.StageCheck;
import net.bananemdnsa.historystages.api.diagnostics.StageCheckContext;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.util.DebugLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The registered {@link StageCheck}s. Filled once through the register event, then frozen. */
public final class StageChecks {

    /** Where a check's problems go. The category already carries the addon's namespace. */
    @FunctionalInterface
    public interface ProblemSink {
        void report(String checkId, DebugLogger.Level level, String category, String message);
    }

    private static final Object LOCK = new Object();
    private static final Map<String, StageCheck> BY_ID = new LinkedHashMap<>();
    private static boolean frozen = false;

    private StageChecks() {}

    public static void register(String id, StageCheck check) {
        if (check == null) throw new IllegalArgumentException("Stage check '" + id + "' is null.");
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw new IllegalArgumentException("A stage check id must be namespaced, like 'mymod:relic_sets': " + id);
        }
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException("Stage check registration is closed; '" + id + "' tried to register after the freeze.");
            }
            if (BY_ID.containsKey(id)) {
                throw new IllegalArgumentException("A stage check is already registered under id '" + id + "'.");
            }
            BY_ID.put(id, check);
        }
    }

    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    /**
     * Runs every check against one stage. Never throws: a check that does — including a
     * {@link LinkageError} from an optional dependency that is not installed, or a stack overflow —
     * becomes an error
     * at this stage, and the rest still run.
     */
    public static void runAll(String stageId, StageScope scope, StageEntry entry, ProblemSink sink) {
        List<Map.Entry<String, StageCheck>> checks;
        synchronized (LOCK) {
            checks = List.copyOf(BY_ID.entrySet());
        }
        for (Map.Entry<String, StageCheck> c : checks) {
            String id = c.getKey();
            String prefix = id.substring(0, id.indexOf(':')) + ": ";
            Problems problems = new Problems() {
                @Override public void error(String category, String message) { sink.report(id, DebugLogger.Level.ERROR, prefix + category, message); }
                @Override public void warn(String category, String message) { sink.report(id, DebugLogger.Level.WARN, prefix + category, message); }
                @Override public void info(String category, String message) { sink.report(id, DebugLogger.Level.INFO, prefix + category, message); }
            };
            StageCheckContext ctx = new StageCheckContext() {
                @Override public String stageId() { return stageId; }
                @Override public StageScope scope() { return scope; }
                @Override public StageEntry entry() { return entry; }
                @Override public Problems problems() { return problems; }
            };
            try {
                c.getValue().check(ctx);
            } catch (Throwable e) {
                // Everything an addon can do wrong, a stack overflow from recursive data included;
                // only a JVM that is itself failing (out of memory) is passed on.
                if (e instanceof VirtualMachineError vm && !(e instanceof StackOverflowError)) throw vm;
                sink.report(id, DebugLogger.Level.ERROR, prefix + "Stage Check", "Check '" + id + "' failed: " + e);
            }
        }
    }

    public static void resetForTest() {
        synchronized (LOCK) {
            BY_ID.clear();
            frozen = false;
        }
    }
}
