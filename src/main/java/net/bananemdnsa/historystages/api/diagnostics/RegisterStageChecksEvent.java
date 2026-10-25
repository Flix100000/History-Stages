package net.bananemdnsa.historystages.api.diagnostics;

import net.bananemdnsa.historystages.data.diagnostics.AddonRegistrations;
import net.bananemdnsa.historystages.data.diagnostics.ApiExtension;
import net.bananemdnsa.historystages.data.diagnostics.ApiOwners;
import net.bananemdnsa.historystages.data.diagnostics.StageChecks;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Fired once during common setup so other mods can check their data in stage files.
 *
 * <p>Registration is legal only during dispatch; the registry freezes when it ends.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterStageChecksEvent.class, event ->
 *         event.register("mymod:relic_sets", ctx -> {
 *             for (String set : MyCategory.get().read(ctx.entry())) {
 *                 if (!RelicSets.exists(set)) ctx.problems().warn("Relic Sets", "Unknown relic set '" + set + "'.");
 *             }
 *         }));
 * }</pre>
 */
public class RegisterStageChecksEvent extends Event implements IModBusEvent {

    /**
     * @param id    unique, {@code <modid>:<name>}; named in the report if the check crashes
     * @param check runs once per loaded stage
     */
    public void register(String id, StageCheck check) {
        StageChecks.register(id, check);
        AddonRegistrations.record(ApiOwners.active(), id, ApiExtension.STAGE_CHECK);
    }
}
