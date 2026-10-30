package net.bananemdnsa.historystages.platform;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.api.HistoryStagesPlugin;
import net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent;
import net.bananemdnsa.historystages.api.dependency.RegisterRequirementTypesEvent;
import net.bananemdnsa.historystages.api.diagnostics.RegisterReportSectionsEvent;
import net.bananemdnsa.historystages.api.diagnostics.RegisterStageChecksEvent;
import net.bananemdnsa.historystages.api.lock.RegisterIndividualRecipeSupportEvent;
import net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent;
import net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent;
import net.bananemdnsa.historystages.api.team.RegisterTeamProvidersEvent;
import net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent;
import net.bananemdnsa.historystages.data.auto.TriggerTypes;
import net.bananemdnsa.historystages.data.config.AddonConfigSections;
import net.bananemdnsa.historystages.data.dependency.RequirementTypes;
import net.bananemdnsa.historystages.data.diagnostics.ApiOwners;
import net.bananemdnsa.historystages.data.diagnostics.ReportSections;
import net.bananemdnsa.historystages.data.diagnostics.StageChecks;
import net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.data.settings.StageSettingsGroups;
import net.bananemdnsa.historystages.data.team.TeamProviders;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The one window in which other mods may add to this one.
 *
 * <p>Each registry is filled and then closed straight away. That order is the whole point:
 * everything that walks one of these lists — editor tabs, dual-phase detection, sync — is written
 * assuming it never changes again, and a registry left open would need invalidation everywhere
 * and would let a server and a client disagree about what exists.
 *
 * <p>Two ways in, on purpose. The events go through the mod's own bus, which is what the classes
 * shipped inside this mod use; and every {@code historystages} entrypoint is called with the same
 * event, which is how another mod joins in without depending on the bus at all.
 */
public final class AddonRegistrations {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTRYPOINT = "historystages";

    private AddonRegistrations() {}

    /**
     * Runs at the end of mod initialisation. Safe to call there whatever order the loader chose:
     * asking for the entrypoints is what loads the other mods' plugin classes, so none of them can
     * be too late.
     */
    public static void run() {
        // Containers rather than bare instances: each one names the mod that provides it, which is
        // how the debug report knows which addon registered what.
        List<EntrypointContainer<HistoryStagesPlugin>> plugins =
                FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, HistoryStagesPlugin.class);

        fire(new RegisterLockCategoriesEvent(), plugins, HistoryStagesPlugin::registerLockCategories);
        LockCategories.freeze();

        fire(new RegisterTriggerTypesEvent(), plugins, HistoryStagesPlugin::registerTriggerTypes);
        TriggerTypes.freeze();

        fire(new RegisterRequirementTypesEvent(), plugins, HistoryStagesPlugin::registerRequirementTypes);
        RequirementTypes.freeze();

        fire(new RegisterStageSettingsGroupsEvent(), plugins, HistoryStagesPlugin::registerStageSettingsGroups);
        StageSettingsGroups.freeze();

        fire(new RegisterConfigSectionsEvent(), plugins, HistoryStagesPlugin::registerConfigSections);
        // The freeze is what makes the section list safe to read from the config packets.
        AddonConfigSections.freeze();

        fire(new RegisterIndividualRecipeSupportEvent(), plugins,
                HistoryStagesPlugin::registerIndividualRecipeSupport);
        IndividualRecipeSupport.freeze();

        fire(new RegisterTeamProvidersEvent(), plugins, HistoryStagesPlugin::registerTeamProviders);
        TeamProviders.freeze();

        fire(new RegisterStageChecksEvent(), plugins, HistoryStagesPlugin::registerStageChecks);
        StageChecks.freeze();

        // The built-in sections go first, so addon sections are listed after them in the report.
        DebugLogger.registerBuiltInSections();
        fire(new RegisterReportSectionsEvent(), plugins, HistoryStagesPlugin::registerReportSections);
        ReportSections.freeze();

        // Logged here rather than inside freeze(): LockCategories is unit-tested and the test
        // classpath has no Minecraft on it. This line is also how an in-game check confirms the
        // registration window actually opened.
        var addonCategories = LockCategories.addonIds();
        LOGGER.info("[HistoryStages] Lock categories closed: {} total, {} from other mods {}",
                LockCategories.all().size(), addonCategories.size(), addonCategories);
        LOGGER.info("[HistoryStages] Stage settings groups closed: {} total",
                StageSettingsGroups.all().size());
        LOGGER.info("[HistoryStages] Config sections closed: {} total", AddonConfigSections.all().size());
        LOGGER.info("[HistoryStages] Team providers closed: {}", TeamProviders.ids());
    }

    /**
     * A plugin that throws must not close the window for everyone else: the registries are frozen
     * right after, so a failure here would leave the game running with someone else's categories
     * silently missing rather than with one broken addon.
     */
    private static <E extends Event> void fire(E event, List<EntrypointContainer<HistoryStagesPlugin>> plugins,
                                               BiConsumer<HistoryStagesPlugin, E> call) {
        EventBus.post(event);
        for (EntrypointContainer<HistoryStagesPlugin> container : plugins) {
            HistoryStagesPlugin plugin = container.getEntrypoint();
            try {
                ApiOwners.runAs(container.getProvider().getMetadata().getId(), () -> call.accept(plugin, event));
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] {} failed during {}",
                        plugin.getClass().getName(), event.getClass().getSimpleName(), e);
            }
        }
    }
}
