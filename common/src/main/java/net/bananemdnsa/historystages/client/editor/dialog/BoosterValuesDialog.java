package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.PedestalTierDropdown;
import net.bananemdnsa.historystages.research.TierMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The values of one research booster: how much faster and how much more expensive it makes
 * research, and from which pedestal tier on it counts.
 *
 * <p>Hands the values back instead of writing into the list row, the same way
 * {@link EffectValuesDialog} does, so cancelling leaves the row exactly as it was.
 */
public class BoosterValuesDialog extends AbstractInputScreen {

    @FunctionalInterface
    public interface OnConfirm {
        void accept(int speed, int cost, int tier, TierMode mode);
    }

    /** Same cap the config parser applies, so the dialog cannot accept what the file would clamp. */
    private static final int MAX_PERCENT = 90;
    private static final int LABEL_TO_BUTTON = 11;
    private static final int ROW_STEP = LABEL_TO_BUTTON + PedestalTierDropdown.BUTTON_HEIGHT + FIELD_GAP;

    private final int speed;
    private final int cost;
    private final OnConfirm onConfirm;

    private int tier;
    private TierMode mode;
    private PedestalTierDropdown tierDropdown;
    private EnumDropdown modeDropdown;

    public BoosterValuesDialog(Screen parent, Component blockName, int speed, int cost, int tier,
                               TierMode mode, OnConfirm onConfirm) {
        super(parent, blockName);
        this.speed = speed;
        this.cost = cost;
        this.tier = tier;
        this.mode = mode;
        this.onConfirm = onConfirm;
    }

    @Override
    protected List<InputField> fields() {
        return List.of(
                InputField.number("speed")
                        .label(Component.translatable("editor.historystages.booster.speed"))
                        .hint(Component.translatable("editor.historystages.booster.speed_hint"))
                        .range(0, MAX_PERCENT)
                        .initial(speed),
                InputField.number("cost")
                        .label(Component.translatable("editor.historystages.booster.cost"))
                        .hint(Component.translatable("editor.historystages.booster.cost_hint"))
                        .range(0, MAX_PERCENT)
                        .initial(cost));
    }

    @Override
    protected void init() {
        super.init();
        // Built here rather than in the constructor: both take their width from the box, which
        // only exists once the base has laid it out. Rebuilt on resize from the picked values.
        int w = boxW - PAD * 2;
        tierDropdown = new PedestalTierDropdown(tier, w, picked -> tier = picked);
        modeDropdown = new EnumDropdown(List.of(TierMode.MIN.name(), TierMode.EXACT.name()), mode.name(), w,
                name -> Component.translatable(modeKey(TierMode.valueOf(name))),
                name -> mode = TierMode.valueOf(name));
    }

    static String modeKey(TierMode mode) {
        return mode == TierMode.EXACT
                ? "editor.historystages.tier_mode.exact"
                : "editor.historystages.tier_mode.min";
    }

    /** Two label-and-dropdown rows. Constant, because the base lays the box out once. */
    @Override
    protected int extraContentHeight() {
        return ROW_STEP * 2 - FIELD_GAP;
    }

    @Override
    protected void renderExtraContent(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        g.text(this.font, Component.translatable("editor.historystages.field.min_pedestal_tier"),
                x, y, LABEL_GREY, false);
        tierDropdown.setPosition(x, y + LABEL_TO_BUTTON);
        tierDropdown.renderButton(g, this.font, mouseX, mouseY);

        int modeY = y + ROW_STEP;
        g.text(this.font, Component.translatable("editor.historystages.field.pedestal_tier_mode"),
                x, modeY, LABEL_GREY, false);
        modeDropdown.setPosition(x, modeY + LABEL_TO_BUTTON);
        modeDropdown.renderButton(g, this.font, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // After everything else, so an open list covers the OK button instead of sliding under it.
        g.pose().pushMatrix();
        g.pose().translate(0, 0);
        tierDropdown.renderPopup(g, this.font, mouseX, mouseY);
        modeDropdown.renderPopup(g, this.font, mouseX, mouseY);
        g.pose().popMatrix();
    }

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        // An open list owns the click, wherever it lands: picking closes it, and a click
        // elsewhere only closes it instead of also pressing whatever sits underneath.
        if (tierDropdown.isExpanded()) {
            tierDropdown.mouseClicked(mx, my);
            tierDropdown.close();
            return true;
        }
        if (modeDropdown.isExpanded()) {
            modeDropdown.mouseClicked(mx, my);
            modeDropdown.close();
            return true;
        }
        return tierDropdown.mouseClicked(mx, my) || modeDropdown.mouseClicked(mx, my);
    }

    @Override
    protected boolean extraContentKeyPressed(int keyCode) {
        // ESC closes an open list first rather than the whole dialog.
        if (keyCode == 256 && (tierDropdown.isExpanded() || modeDropdown.isExpanded())) {
            tierDropdown.close();
            modeDropdown.close();
            return true;
        }
        return false;
    }

    @Override
    protected void onConfirm(InputValues values) {
        onConfirm.accept(values.getInt("speed"), values.getInt("cost"), tier, mode);
        this.minecraft.gui.setScreen(parent);
    }
}
