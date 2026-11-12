package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.api.diagnostics.ReportSection;
import net.bananemdnsa.historystages.api.diagnostics.ReportWriter;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.DebugReport;

import java.util.ArrayList;
import java.util.List;

/**
 * The debug report's sections: History Stages' own first, then the addons' in registration order.
 * HS uses the same mechanism, so a new HS feature adds a section instead of editing the report.
 */
public final class ReportSections {

    private record Registered(String id, String title, boolean builtIn, ReportSection section) {}

    private static final Object LOCK = new Object();
    private static final List<Registered> ALL = new ArrayList<>();
    private static boolean frozen = false;

    private ReportSections() {}

    public static void register(String id, String title, ReportSection section) {
        add(id, title, false, section);
    }

    /** For History Stages itself; not reachable through the API. */
    public static void registerBuiltIn(String id, String title, ReportSection section) {
        add(id, title, true, section);
    }

    private static void add(String id, String title, boolean builtIn, ReportSection section) {
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw new IllegalArgumentException("A report section id must be namespaced, like 'mymod:relics': " + id);
        }
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Report section '" + id + "' needs a title.");
        if (section == null) throw new IllegalArgumentException("Report section '" + id + "' is null.");
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException("Report section registration is closed; '" + id + "' tried to register after the freeze.");
            }
            if (ALL.stream().anyMatch(r -> r.id().equals(id))) {
                throw new IllegalArgumentException("A report section is already registered under id '" + id + "'.");
            }
            ALL.add(new Registered(id, title, builtIn, section));
        }
    }

    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    /** Runs every section. A section that throws keeps what it wrote and says that it failed. */
    public static List<DebugReport.Section> collect() {
        List<Registered> all;
        synchronized (LOCK) {
            all = new ArrayList<>(ALL);
        }
        // Stable, so registration order holds within each group.
        all.sort((a, b) -> Boolean.compare(b.builtIn(), a.builtIn()));

        List<DebugReport.Section> out = new ArrayList<>();
        for (Registered r : all) {
            List<String> lines = new ArrayList<>();
            ReportWriter writer = new ReportWriter() {
                @Override public void line(String text) { lines.add(String.valueOf(text)); }
                @Override public void value(String label, Object value) { lines.add(label + ": " + value); }
            };
            try {
                r.section().write(writer);
            } catch (Throwable e) {
                // Same rule as for stage checks: only a failing JVM is passed on.
                if (e instanceof VirtualMachineError vm && !(e instanceof StackOverflowError)) throw vm;
                lines.add("(section failed: " + e + ")");
                AddonRegistrations.problem(ApiExtension.REPORT_SECTION, r.id(), DebugLogger.Level.ERROR);
            }
            if (!lines.isEmpty()) out.add(new DebugReport.Section(r.id(), r.title(), r.builtIn(), lines));
        }
        return out;
    }

    public static void resetForTest() {
        synchronized (LOCK) {
            ALL.clear();
            frozen = false;
        }
    }
}
