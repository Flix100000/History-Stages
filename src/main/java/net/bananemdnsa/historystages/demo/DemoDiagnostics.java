package net.bananemdnsa.historystages.demo;

import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.api.diagnostics.Kind;
import net.bananemdnsa.historystages.api.diagnostics.RegisterReportSectionsEvent;
import net.bananemdnsa.historystages.api.diagnostics.RegisterStageChecksEvent;
import net.bananemdnsa.historystages.api.diagnostics.RuntimeCategory;
import net.bananemdnsa.historystages.api.diagnostics.RuntimeLog;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * The stand-in addon's diagnostics: a check for its relic entries, a section of its own in the
 * debug report, and a runtime log category. The three things an addon author would copy.
 */
public final class DemoDiagnostics {

    static final RuntimeCategory RELICS = RuntimeCategory.of("hsdemo:relics", "Relics", Kind.SYSTEM);

    private DemoDiagnostics() {}

    /** Called from {@link DemoPlugin}, which is how an addon on this loader reaches these events. */
    public static final class Registration {
        private Registration() {}

        public static void onRegisterChecks(RegisterStageChecksEvent event) {
            if (!DemoAddonCategory.enabled()) return;
            Set<String> known = Set.copyOf(DemoAddonCategory.candidateRelics());
            event.register("hsdemo:relics", ctx -> {
                for (String relic : DemoAddonCategory.category().read(ctx.entry())) {
                    if (!known.contains(relic)) {
                        ctx.problems().warn("Relics", "Unknown relic '" + relic + "'. It can never be found, so the lock never matters.");
                    }
                }
            });
        }

        public static void onRegisterSections(RegisterReportSectionsEvent event) {
            if (!DemoAddonCategory.enabled()) return;
            event.register("hsdemo:relics", "Relics", out -> {
                int[] counts = count();
                out.value("Relics known", DemoAddonCategory.candidateRelics().size());
                out.value("Relic entries in stages", counts[0]);
                out.value("Stages using relics", counts[1]);
            });
        }
    }

    /** Play-time events come from Fabric API directly, the way any addon would get them. */
    public static final class Game {
        private Game() {}

        public static void register() {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> onServerStarted());
        }

        private static void onServerStarted() {
            if (!DemoAddonCategory.enabled()) return;
            int[] counts = count();
            RuntimeLog.write(RELICS, counts[0] + " relic entries across " + counts[1] + " stages");
        }
    }

    /** {entries, stages} over every loaded stage, global and individual. */
    private static int[] count() {
        int entries = 0;
        int stages = 0;
        for (var stage : StageStates.loadedStages()) {
            List<String> relics = DemoAddonCategory.category().read(stage);
            entries += relics.size();
            if (!relics.isEmpty()) stages++;
        }
        return new int[] {entries, stages};
    }
}
