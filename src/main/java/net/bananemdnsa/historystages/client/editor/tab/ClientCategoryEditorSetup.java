package net.bananemdnsa.historystages.client.editor.tab;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.api.HistoryStagesClientPlugin;
import net.bananemdnsa.historystages.api.editor.CustomFieldScreens;
import net.bananemdnsa.historystages.api.editor.RegisterCategoryEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterCustomFieldScreensEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRecipeTypeMetaEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterTriggerEditorsEvent;
import net.bananemdnsa.historystages.client.editor.dep.RequirementEditors;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeTypeMetas;
import net.bananemdnsa.historystages.client.editor.trigger.TriggerEditors;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Opens and closes the windows in which an addon may give its category an editor tab and its
 * auto-trigger type a way to be authored.
 *
 * <p>Client-only by annotation, so nothing here is ever loaded on a dedicated server — the editor
 * classes this reaches are pure UI, and pulling them onto the server is how the crash fixed in
 * commit 0469f73 happened.
 *
 * <p>Called after the common registrations, so the lock categories themselves are already
 * registered and frozen by the time an editor can be attached to one.
 */
@Environment(EnvType.CLIENT)
public final class ClientCategoryEditorSetup {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTRYPOINT = "historystages:client";

    private ClientCategoryEditorSetup() {}

    public static void run() {
        List<HistoryStagesClientPlugin> plugins =
                FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, HistoryStagesClientPlugin.class);

        fire(new RegisterCategoryEditorsEvent(), plugins, HistoryStagesClientPlugin::registerCategoryEditors);
        CategoryEditors.freeze();

        fire(new RegisterTriggerEditorsEvent(), plugins, HistoryStagesClientPlugin::registerTriggerEditors);
        TriggerEditors.freeze();

        fire(new RegisterRequirementEditorsEvent(), plugins,
                HistoryStagesClientPlugin::registerRequirementEditors);
        RequirementEditors.freeze();

        // One window for both declarative axes: a CUSTOM_SCREEN field on a stage setting and one
        // in the config screen ask the same question, which screen edits this value.
        fire(new RegisterCustomFieldScreensEvent(), plugins,
                HistoryStagesClientPlugin::registerCustomFieldScreens);
        CustomFieldScreens.freeze();

        // Cosmetic only: what block stands for a recipe type, what colour its card gets, what it
        // is called. Client side, because nothing off the client has any use for it.
        fire(new RegisterRecipeTypeMetaEvent(), plugins, HistoryStagesClientPlugin::registerRecipeTypeMeta);
        RecipeTypeMetas.freeze();
    }

    /** A plugin that throws loses its own tab, not everyone else's — the registry freezes next. */
    private static <E extends Event> void fire(E event, List<HistoryStagesClientPlugin> plugins,
                                               BiConsumer<HistoryStagesClientPlugin, E> call) {
        EventBus.post(event);
        for (HistoryStagesClientPlugin plugin : plugins) {
            try {
                call.accept(plugin, event);
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] {} failed during {}",
                        plugin.getClass().getName(), event.getClass().getSimpleName(), e);
            }
        }
    }
}
