package net.bananemdnsa.historystages.api.diagnostics;

/** Writes lines into a {@link ReportSection}. History Stages does the indenting. */
public interface ReportWriter {
    void line(String text);

    /** {@code label: value} */
    void value(String label, Object value);
}
