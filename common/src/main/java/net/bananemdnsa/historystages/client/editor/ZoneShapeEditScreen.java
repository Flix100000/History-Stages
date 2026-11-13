package net.bananemdnsa.historystages.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.api.editor.widget.ToggleControl;
import net.bananemdnsa.historystages.data.lock.ZoneCoordinateParser;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.lock.ZoneShapeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The numbers of one shape.
 *
 * <p>Which fields appear depends on the type, because the same three numbers mean different
 * things: two opposite corners for a cube, a centre for a sphere or cylinder. Naming them
 * accordingly is the whole reason this is a dialog per type rather than one form with six boxes
 * and a legend.
 *
 * <p>Pasting is handled here rather than in a field of its own: F3+C puts a whole teleport command
 * on the clipboard, and filling all three boxes from it is what turns "copy a position" into one
 * keystroke instead of three retyped numbers.
 */
public class ZoneShapeEditScreen extends AbstractInputScreen {

    private final Screen parent;
    private final ZoneShapeType type;
    private final ZoneShape initial;
    private final Consumer<ZoneShape> onDone;

    private boolean fullHeight;
    private final ToggleControl.State toggleState = new ToggleControl.State();
    private int toggleX;
    private int toggleY;

    public ZoneShapeEditScreen(Screen parent, ZoneShapeType type, ZoneShape initial,
                               Consumer<ZoneShape> onDone) {
        super(parent, Component.translatable("editor.historystages.zone.shape." + type.serializedName()));
        this.parent = parent;
        this.type = type;
        this.initial = initial;
        this.onDone = onDone;
        this.fullHeight = initial != null && initial.fullHeight();
    }

    @Override
    protected List<InputField> fields() {
        List<InputField> fields = new ArrayList<>();
        if (type == ZoneShapeType.CUBE) {
            addPoint(fields, "from", "corner1", initial == null ? 0 : initial.fromX(),
                    initial == null ? 0 : initial.fromY(), initial == null ? 0 : initial.fromZ());
            addPoint(fields, "to", "corner2", initial == null ? 0 : initial.toX(),
                    initial == null ? 0 : initial.toY(), initial == null ? 0 : initial.toZ());
        } else {
            addPoint(fields, "from", "centre", initial == null ? 0 : initial.fromX(),
                    initial == null ? 0 : initial.fromY(), initial == null ? 0 : initial.fromZ());
            fields.add(InputField.number("radius")
                    .label(Component.translatable("editor.historystages.zone.field.radius"))
                    .range(1, 30_000_000)
                    .initial(initial == null ? 16 : initial.radius()));
            if (type == ZoneShapeType.CYLINDER) {
                fields.add(InputField.number("height")
                        .label(Component.translatable("editor.historystages.zone.field.height"))
                        .range(1, 30_000_000)
                        .initial(initial == null ? 32 : initial.height()));
            }
        }
        return fields;
    }

    /** One line of three boxes. Stacked, two corners alone make the dialog taller than the screen. */
    private void addPoint(List<InputField> fields, String prefix, String labelKey,
                          int x, int y, int z) {
        Component label = Component.translatable("editor.historystages.zone.field." + labelKey);
        fields.add(InputField.number(prefix + "X").label(label).range(-30_000_000, 30_000_000).initial(x));
        fields.add(InputField.number(prefix + "Y").sameRow().range(-30_000_000, 30_000_000).initial(y));
        fields.add(InputField.number(prefix + "Z").sameRow().range(-30_000_000, 30_000_000).initial(z));
    }

    /** Cube and cylinder can span the whole build range; a sphere has no height to replace. */
    private boolean offersFullHeight() {
        return type != ZoneShapeType.SPHERE;
    }

    @Override
    protected int extraContentHeight() {
        return offersFullHeight() ? ToggleControl.height() + 8 : 0;
    }

    @Override
    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        if (!offersFullHeight()) return;

        Component label = Component.translatable("editor.historystages.zone.field.full_height");
        g.drawString(this.font, label, x, y + 3, LABEL_GREY, false);

        toggleX = x + w - ToggleControl.width(this.font);
        toggleY = y;
        toggleState.update(fullHeight, ToggleControl.segmentAt(this.font, toggleX, toggleY, mouseX, mouseY));
        ToggleControl.draw(g, this.font, toggleX, toggleY, fullHeight, toggleState, false);
    }

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (!offersFullHeight()) return false;
        Boolean picked = ToggleControl.valueAt(this.font, toggleX, mx);
        if (picked == null || my < toggleY || my > toggleY + ToggleControl.height()) return false;
        fullHeight = picked;
        return true;
    }

    /**
     * Ctrl+V fills a whole point at once when the clipboard holds one.
     *
     * <p>Handled before the focused box sees the key, so the usual single-field paste only
     * happens for text that is not a position. Which point gets filled follows the focus: pasting
     * into the second corner's row must not overwrite the first.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.isPaste(keyCode)) {
            ZoneCoordinateParser.Result parsed =
                    ZoneCoordinateParser.parse(this.minecraft.keyboardHandler.getClipboard());
            if (parsed != null && fillFocusedPoint(parsed)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Fills the x/y/z triple the focused box belongs to. False when focus is elsewhere. */
    private boolean fillFocusedPoint(ZoneCoordinateParser.Result parsed) {
        for (int i = 0; i + 2 < fieldCount(); i++) {
            if (!isPointStart(i)) continue;
            if (!box(i).isFocused() && !box(i + 1).isFocused() && !box(i + 2).isFocused()) continue;

            box(i).setValue(String.valueOf(parsed.x()));
            box(i + 1).setValue(String.valueOf(parsed.y()));
            box(i + 2).setValue(String.valueOf(parsed.z()));
            return true;
        }
        return false;
    }

    /** Points occupy the first three boxes, and for a cube the next three as well. */
    private boolean isPointStart(int index) {
        return index == 0 || (type == ZoneShapeType.CUBE && index == 3);
    }

    @Override
    protected void onConfirm(InputValues values) {
        ZoneShape shape = switch (type) {
            case CUBE -> ZoneShape.cube(
                    values.getInt("fromX"), values.getInt("fromY"), values.getInt("fromZ"),
                    values.getInt("toX"), values.getInt("toY"), values.getInt("toZ"),
                    fullHeight);
            case SPHERE -> ZoneShape.sphere(
                    values.getInt("fromX"), values.getInt("fromY"), values.getInt("fromZ"),
                    values.getInt("radius"));
            case CYLINDER -> ZoneShape.cylinder(
                    values.getInt("fromX"), values.getInt("fromY"), values.getInt("fromZ"),
                    values.getInt("radius"), values.getInt("height"), fullHeight);
        };
        onDone.accept(shape);
        this.minecraft.setScreen(parent);
    }
}
