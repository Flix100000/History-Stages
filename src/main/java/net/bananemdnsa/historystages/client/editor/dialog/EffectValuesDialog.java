package net.bananemdnsa.historystages.client.editor.dialog;

import java.util.List;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEffectList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * How long one potion effect lasts and how strong it is.
 *
 * <p>Shared rather than copied: the biome effects in the common config and the effects on a zone
 * ask the same two questions with the same limits. Two dialogs would drift apart the first time
 * one of those limits moved, and the limits are the part a pack author notices.
 *
 * <p>Hands the two numbers back instead of writing into a row, because the callers keep them in
 * different shapes — a mutable row in the config editor, an immutable
 * {@code ZoneEffectSpec} on a zone.
 */
public class EffectValuesDialog extends AbstractInputScreen {

    /**
     * Called with seconds and amplifier once the dialog is confirmed.
     *
     * <p>Its own type rather than {@code ObjIntConsumer}, which is the nearest thing in the JDK
     * and takes an object and an int — here both values are ints.
     */
    @FunctionalInterface
    public interface OnConfirm {
        void accept(int seconds, int amplifier);
    }

    private final Screen parent;
    private final int seconds;
    private final int amplifier;
    private final OnConfirm onConfirm;

    public EffectValuesDialog(Screen parent, String effectId, int seconds, int amplifier,
                              OnConfirm onConfirm) {
        super(parent, Component.translatable("editor.historystages.effect.edit_title",
                SearchableEffectList.displayName(effectId)));
        this.parent = parent;
        this.seconds = seconds;
        this.amplifier = amplifier;
        this.onConfirm = onConfirm;
    }

    @Override
    protected List<InputField> fields() {
        return List.of(
                InputField.number("seconds")
                        .label(Component.translatable("editor.historystages.effect.seconds"))
                        .hint(Component.translatable("editor.historystages.effect.seconds_hint"))
                        .range(1, 3600)
                        .initial(String.valueOf(seconds)),
                InputField.number("amplifier")
                        .label(Component.translatable("editor.historystages.effect.amplifier"))
                        .hint(Component.translatable("editor.historystages.effect.amplifier_hint"))
                        .range(0, 255)
                        .initial(String.valueOf(amplifier)));
    }

    @Override
    protected void onConfirm(InputValues values) {
        onConfirm.accept(values.getInt("seconds"), values.getInt("amplifier"));
        this.minecraft.setScreen(parent);
    }
}
