package net.bananemdnsa.historystages.api;

import net.bananemdnsa.historystages.api.editor.RegisterCategoryEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterCustomFieldScreensEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRecipeTypeMetaEvent;
import net.bananemdnsa.historystages.api.editor.RegisterRequirementEditorsEvent;
import net.bananemdnsa.historystages.api.editor.RegisterTriggerEditorsEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The client half of {@link HistoryStagesPlugin}: how an addon gives its own additions a face in
 * the in-game editor.
 *
 * <p>Separate from the common one because everything reached from here is user interface, and a
 * dedicated server must never load it.
 *
 * <p>In the addon's {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "historystages:client": ["com.example.mymod.MyHistoryStagesClientPlugin"]
 * }
 * }</pre>
 *
 * <p>This runs after the common registrations, so a category exists by the time an editor is
 * attached to it.
 */
@Environment(EnvType.CLIENT)
public interface HistoryStagesClientPlugin {

    /** Give a lock category of your own a tab in the stage editor. */
    default void registerCategoryEditors(RegisterCategoryEditorsEvent event) {
    }

    /** Give a trigger type of your own a way to be authored. */
    default void registerTriggerEditors(RegisterTriggerEditorsEvent event) {
    }

    /** Give a requirement type of your own a way to be authored. */
    default void registerRequirementEditors(RegisterRequirementEditorsEvent event) {
    }

    /** Provide the screen that edits a custom field, whether on a stage setting or in the config. */
    default void registerCustomFieldScreens(RegisterCustomFieldScreensEvent event) {
    }

    /** Cosmetic only: which block stands for a recipe type, what colour its card is, what it is called. */
    default void registerRecipeTypeMeta(RegisterRecipeTypeMetaEvent event) {
    }
}
