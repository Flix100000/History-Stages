package net.bananemdnsa.historystages.api;

import net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent;
import net.bananemdnsa.historystages.api.dependency.RegisterRequirementTypesEvent;
import net.bananemdnsa.historystages.api.lock.RegisterIndividualRecipeSupportEvent;
import net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent;
import net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent;
import net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent;

/**
 * How another mod adds to History Stages on this loader.
 *
 * <p>The other loader hands these events to the mod bus and every mod can listen. There is no such
 * bus here, and mod initializers run in no particular order, so an addon that simply registered a
 * listener might do it after the registries had already closed. Declaring an entrypoint instead
 * removes the question: the loader knows every mod before any of them is asked, so this is called
 * at the right moment by construction rather than by luck.
 *
 * <p>In the addon's {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "historystages": ["com.example.mymod.MyHistoryStagesPlugin"]
 * }
 * }</pre>
 *
 * <p>Every method does nothing by default, so an addon overrides only what it needs. The events
 * themselves are the same classes as on the other loader, so the body of each method carries over
 * unchanged.
 *
 * <p>The client-side registrations — editor tabs, field screens, requirement and trigger editors
 * — are in {@code HistoryStagesClientPlugin}, under the {@code historystages:client} entrypoint,
 * because a dedicated server must not load them.
 */
public interface HistoryStagesPlugin {

    /** Add lock categories. The registry closes as soon as this has been called for every mod. */
    default void registerLockCategories(RegisterLockCategoriesEvent event) {
    }

    /** Add trigger types for AUTO and TEMPORARY stages. */
    default void registerTriggerTypes(RegisterTriggerTypesEvent event) {
    }

    /** Add requirement types the Research Pedestal can ask for. */
    default void registerRequirementTypes(RegisterRequirementTypesEvent event) {
    }

    /** Add groups of per-stage settings. */
    default void registerStageSettingsGroups(RegisterStageSettingsGroupsEvent event) {
    }

    /** Add config sections of your own. */
    default void registerConfigSections(RegisterConfigSectionsEvent event) {
    }

    /** Declare which recipe types your mod can gate per player. */
    default void registerIndividualRecipeSupport(RegisterIndividualRecipeSupportEvent event) {
    }
}
