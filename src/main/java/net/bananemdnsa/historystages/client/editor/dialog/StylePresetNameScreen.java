package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.serverbound.SaveStylePresetPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Asks for a style preset's name — for a new preset and for renaming one. Any characters are
 * fine; the id the file uses is made from the name separately, so the name is free text.
 */
public class StylePresetNameScreen extends AbstractInputScreen {

    /** The preset being renamed, or null for a new one. */
    private final String presetId;
    private final String currentName;
    private final Consumer<String> onAccept;

    public StylePresetNameScreen(Screen parent, Component title, String presetId, String currentName,
                                 Consumer<String> onAccept) {
        super(parent, title);
        this.presetId = presetId;
        this.currentName = currentName;
        this.onAccept = onAccept;
    }

    @Override
    protected int dialogWidth() { return 300; }

    @Override
    protected List<InputField> fields() {
        return List.of(InputField.text("name")
                .label(Component.translatable("editor.historystages.graph.preset.name"))
                .maxLength(SaveStylePresetPacket.MAX_NAME_LENGTH)
                .initial(currentName == null ? "" : currentName)
                .validator(this::checkName));
    }

    private Component checkName(String name) {
        if (name.trim().isEmpty()) return Component.translatable("editor.historystages.input.empty");
        if (GraphStageData.get().presetNameTaken(name, presetId)) {
            return Component.translatable("editor.historystages.graph.preset.name_exists");
        }
        return null;
    }

    @Override
    protected void onConfirm(InputValues values) {
        // Back to the parent first: the callback may open a screen of its own.
        this.minecraft.setScreen(parent);
        onAccept.accept(values.getString("name").trim());
    }
}
