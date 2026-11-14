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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client registration window has to reach Fabric's {@code historystages:client} plugins, not
 * only the bus. Until Task 11 nothing called them on 26.2, so a Fabric addon's editor tab never
 * appeared.
 */
class ClientCategoryEditorSetupTest {

    @BeforeEach
    @AfterEach
    void reset() {
        CategoryEditors.resetForTesting();
        TriggerEditors.resetForTesting();
        RequirementEditors.resetForTesting();
        CustomFieldScreens.resetForTesting();
        RecipeTypeMetas.resetForTesting();
    }

    @Test
    void everyWindowReachesEveryPluginAndThenCloses() {
        List<String> heard = new ArrayList<>();
        HistoryStagesClientPlugin broken = new HistoryStagesClientPlugin() {
            @Override
            public void registerCategoryEditors(RegisterCategoryEditorsEvent event) {
                throw new IllegalStateException("broken addon");
            }
        };
        HistoryStagesClientPlugin listening = new HistoryStagesClientPlugin() {
            @Override
            public void registerCategoryEditors(RegisterCategoryEditorsEvent event) {
                heard.add("categories");
            }

            @Override
            public void registerTriggerEditors(RegisterTriggerEditorsEvent event) {
                heard.add("triggers");
            }

            @Override
            public void registerRequirementEditors(RegisterRequirementEditorsEvent event) {
                heard.add("requirements");
            }

            @Override
            public void registerCustomFieldScreens(RegisterCustomFieldScreensEvent event) {
                heard.add("fields");
            }

            @Override
            public void registerRecipeTypeMeta(RegisterRecipeTypeMetaEvent event) {
                heard.add("recipe types");
            }
        };
        Consumer<BiConsumer<String, HistoryStagesClientPlugin>> plugins = body -> {
            body.accept("broken", broken);
            body.accept("listening", listening);
        };

        ClientCategoryEditorSetup.run(plugins);

        assertEquals(List.of("categories", "triggers", "requirements", "fields", "recipe types"), heard,
                "a plugin after a throwing one still gets every window");
        assertTrue(CategoryEditors.isFrozen(), "category editors closed");
        assertTrue(TriggerEditors.isFrozen(), "trigger editors closed");
        assertTrue(RequirementEditors.isFrozen(), "requirement editors closed");
        assertTrue(CustomFieldScreens.isFrozen(), "custom field screens closed");
        assertTrue(RecipeTypeMetas.isFrozen(), "recipe type metas closed");
    }
}
