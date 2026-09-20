package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.api.HistoryStagesPlugin;
import net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent;
import net.bananemdnsa.historystages.api.dependency.RegisterRequirementTypesEvent;
import net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent;
import net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent;
import net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent;

/**
 * The way into the stand-in addon, and the reason it is worth copying: this is the whole of how a
 * mod announces itself to History Stages on this loader.
 *
 * <p>The entrypoint is declared in {@code fabric.mod.json} under {@code historystages}. Nothing
 * else is needed — no bus, no listener registration, no worrying about whether this mod loaded
 * first. Asking for the entrypoints is what builds this class, so it cannot be too early or too
 * late.
 *
 * <p>Each method does nothing unless the demo is switched on, which the pieces below check for
 * themselves.
 */
public final class DemoPlugin implements HistoryStagesPlugin {

    @Override
    public void registerLockCategories(RegisterLockCategoriesEvent event) {
        DemoAddonCategory.onRegisterCategories(event);
    }

    @Override
    public void registerTriggerTypes(RegisterTriggerTypesEvent event) {
        DemoAddonCategory.onRegisterTriggerTypes(event);
    }

    @Override
    public void registerRequirementTypes(RegisterRequirementTypesEvent event) {
        DemoRequirement.onRegisterRequirementTypes(event);
    }

    @Override
    public void registerStageSettingsGroups(RegisterStageSettingsGroupsEvent event) {
        DemoSettingsGroup.onRegisterGroups(event);
    }

    @Override
    public void registerConfigSections(RegisterConfigSectionsEvent event) {
        DemoConfigSections.onRegisterConfigSections(event);
    }
}
