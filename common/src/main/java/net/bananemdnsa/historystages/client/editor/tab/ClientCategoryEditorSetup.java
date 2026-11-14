package net.bananemdnsa.historystages.client.editor.tab;

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
import net.bananemdnsa.historystages.platform.AddonEntrypoints;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.EventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Opens and closes the windows in which an addon may give its category an editor tab and its
 * auto-trigger type a way to be authored.
 *
 * <p>Client-only: only the loaders' client entry points call it, so nothing here is ever loaded on
 * a dedicated server — the editor classes this reaches are pure UI, and pulling them onto the
 * server is how the crash fixed in commit 0469f73 happened.
 *
 * <p>Runs after the common registration window (NeoForge: client setup, Fabric: Architectury's
 * CLIENT_SETUP, which follows SETUP), so the lock categories themselves are already registered and
 * frozen by the time an editor can be attached to one.
 *
 * <p>Each event goes to the platform bus (NeoForge subscribers) and to every
 * {@code historystages:client} entrypoint (Fabric addons).
 */
public final class ClientCategoryEditorSetup {

    private ClientCategoryEditorSetup() {}

    public static void run() {
        run(AddonEntrypoints::forEachClientPlugin);
    }

    /** {@code plugins} walks the client plugins with their mod id; a parameter so a test can hand in its own. */
    static void run(Consumer<BiConsumer<String, HistoryStagesClientPlugin>> plugins) {
        fire(new RegisterCategoryEditorsEvent(), plugins, HistoryStagesClientPlugin::registerCategoryEditors);
        CategoryEditors.freeze();

        fire(new RegisterTriggerEditorsEvent(), plugins, HistoryStagesClientPlugin::registerTriggerEditors);
        TriggerEditors.freeze();

        fire(new RegisterRequirementEditorsEvent(), plugins, HistoryStagesClientPlugin::registerRequirementEditors);
        RequirementEditors.freeze();

        // One window for both declarative axes: a CUSTOM_SCREEN field on a stage setting and
        // one in the config screen ask the same question, which screen edits this value.
        fire(new RegisterCustomFieldScreensEvent(), plugins, HistoryStagesClientPlugin::registerCustomFieldScreens);
        CustomFieldScreens.freeze();

        // Cosmetic only: what block stands for a recipe type, what colour its card gets,
        // what it is called. Client side, because nothing off the client has any use for it.
        fire(new RegisterRecipeTypeMetaEvent(), plugins, HistoryStagesClientPlugin::registerRecipeTypeMeta);
        RecipeTypeMetas.freeze();
    }

    /** A plugin that throws loses its own tab, not everyone else's — the registry freezes next. */
    // Per event rather than collected once: on Fabric each call runs in the plugin's mod context,
    // so what it registers is owned by that mod.
    private static <E extends Event> void fire(E event, Consumer<BiConsumer<String, HistoryStagesClientPlugin>> plugins,
                                               BiConsumer<HistoryStagesClientPlugin, E> call) {
        EventBus.post(event);
        plugins.accept((modId, plugin) -> {
            try {
                call.accept(plugin, event);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("[HistoryStages] {} ({}) failed during {}",
                        plugin.getClass().getName(), modId, event.getClass().getSimpleName(), e);
            }
        });
    }
}
