package net.bananemdnsa.historystages.api.diagnostics;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;

/** What a {@link StageCheck} gets to look at. An interface, so later versions can add to it. */
public interface StageCheckContext {
    String stageId();
    StageScope scope();
    StageEntry entry();
    /** Where to report. Stays tied to this stage's file even if kept and used later. */
    Problems problems();
}
