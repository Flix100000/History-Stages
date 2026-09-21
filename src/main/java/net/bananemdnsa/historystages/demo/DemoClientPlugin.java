package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.api.HistoryStagesClientPlugin;
import net.bananemdnsa.historystages.api.editor.RegisterCategoryEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterCustomFieldScreensEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterTriggerEditorsEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The editor half of the stand-in addon, under the {@code historystages:client} entrypoint.
 *
 * <p>Separate from {@link DemoPlugin} for the same reason the interfaces are: everything reached
 * from here is user interface, and a dedicated server must not load it.
 */
@Environment(EnvType.CLIENT)
public final class DemoClientPlugin implements HistoryStagesClientPlugin {

    @Override
    public void registerCategoryEditors(RegisterCategoryEditorsEvent event) {
        DemoAddonCategoryEditor.onRegisterEditors(event);
    }

    @Override
    public void registerTriggerEditors(RegisterTriggerEditorsEvent event) {
        DemoAddonCategoryEditor.onRegisterTriggerEditors(event);
    }

    @Override
    public void registerRequirementEditors(RegisterRequirementEditorsEvent event) {
        DemoRelicSetEditor.onRegisterRequirementEditors(event);
        DemoRequirementEditor.onRegisterRequirementEditors(event);
    }

    @Override
    public void registerCustomFieldScreens(RegisterCustomFieldScreensEvent event) {
        DemoRequirementEditor.onRegisterCustomFieldScreens(event);
    }
}
