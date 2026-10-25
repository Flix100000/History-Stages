package net.bananemdnsa.historystages.api.diagnostics;

import net.bananemdnsa.historystages.data.diagnostics.AddonRegistrations;
import net.bananemdnsa.historystages.data.diagnostics.ApiExtension;
import net.bananemdnsa.historystages.data.diagnostics.ApiOwners;
import net.bananemdnsa.historystages.data.diagnostics.ReportSections;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Fired once during common setup so other mods can add sections to the debug report.
 *
 * <p>Registration is legal only during dispatch; the registry freezes when it ends.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterReportSectionsEvent.class, event ->
 *         event.register("mymod:relics", "Relic sets", out -> out.value("Sets loaded", RelicSets.count())));
 * }</pre>
 */
public class RegisterReportSectionsEvent extends Event implements IModBusEvent {

    /**
     * @param id    unique, {@code <modid>:<name>}
     * @param title the section's heading in the report
     */
    public void register(String id, String title, ReportSection section) {
        ReportSections.register(id, title, section);
        AddonRegistrations.record(ApiOwners.active(), id, ApiExtension.REPORT_SECTION);
    }
}
