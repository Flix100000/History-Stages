package net.bananemdnsa.historystages.api.diagnostics;

/**
 * A block of your own in History Stages' debug report — your version, counts, anything that helps
 * when someone attaches the report to a bug. Register it with {@link RegisterReportSectionsEvent}.
 *
 * <p>Called every time the report is written (world start, {@code /history reload}). A section
 * that writes nothing is left out; one that throws shows the exception and the report carries on.
 */
@FunctionalInterface
public interface ReportSection {
    void write(ReportWriter out);
}
