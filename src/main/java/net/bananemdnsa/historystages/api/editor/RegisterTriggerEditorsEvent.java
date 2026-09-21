package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.client.editor.trigger.TriggerEditors;

import net.bananemdnsa.historystages.api.editor.TriggerEditor;

import net.bananemdnsa.historystages.platform.bus.Event;

/**
 * Fired once on the client so an addon's auto-trigger type can be authored in the editor.
 *
 * <pre>{@code
 * public class MyPlugin implements HistoryStagesClientPlugin {
 *     public void registerTriggerEditors(RegisterTriggerEditorsEvent event) {
 *         event.register(
 *             TriggerEditor.ofIdList("mymod:relic_found",
 *                     "editor.mymod.auto_trigger.relic_found",
 *                     "editor.mymod.search.relics",
 *                     MyRelics::allIds,
 *                     RelicFoundTrigger::new));
 *     }
 * }
 * }</pre>
 */
public class RegisterTriggerEditorsEvent extends Event {

    public void register(TriggerEditor editor) {
        TriggerEditors.register(editor);
    }
}
