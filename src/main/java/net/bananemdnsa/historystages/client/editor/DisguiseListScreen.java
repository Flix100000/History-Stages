package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.dialog.DisguiseDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.disguise.DisguiseData;
import net.bananemdnsa.historystages.data.disguise.DisguiseRule;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.bananemdnsa.historystages.data.disguise.DropsMode;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveDisguisePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every rule in {@code disguises.json}, in one place.
 *
 * <p>The stage screen only reaches the rules of entries it lists. A rule whose item is locked
 * nowhere any more has no stage left to open it from — this is where it can still be found,
 * greyed and at the bottom, and removed.
 */
public class DisguiseListScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int LIST_TOP = 48;
    private static final int GREY_TEXT = 0xFF777777;
    private static final int SEARCH_W = 220;

    private final Screen parent;
    private final EditorRowList rows = new EditorRowList();
    private SearchBar search;
    private ContextMenu contextMenu = new ContextMenu();
    private double scroll;

    /** Built each frame from the live rule set, so a save from the dialog shows at once. */
    private List<Line> lines = List.of();

    private record Line(DisguiseRule rule, List<String> stages) {
        boolean active() {
            return !stages.isEmpty();
        }
    }

    public DisguiseListScreen(Screen parent) {
        super(Component.translatable("editor.historystages.disguise.list.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (search == null) {
            search = new SearchBar(Component.translatable("editor.historystages.disguise.list.search").getString())
                    .setLightStyle(true)
                    .onChange(q -> scroll = 0);
            search.setFocused(false);
        }
        // Not the full width: a short list of rules does not need a bar across the whole screen.
        search.setPosition(MARGIN, 22, Math.min(SEARCH_W, this.width - MARGIN * 2 - 110));

        addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.disguise.list.add"),
                b -> this.minecraft.setScreen(new DisguiseDialog(this, null, true)),
                this.width - MARGIN - 100, 22, 100, 20));
        addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.back"),
                b -> onClose(), MARGIN, this.height - 28, 60, 20));
    }

    private List<Line> buildLines() {
        String query = search != null ? search.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<Line> active = new ArrayList<>();
        List<Line> inactive = new ArrayList<>();
        for (DisguiseRule rule : DisguiseData.get().all()) {
            if (!query.isEmpty() && !matches(rule, query)) continue;
            Line line = new Line(rule, DisguiseDialog.lockingStageNames(rule.key()));
            (line.active() ? active : inactive).add(line);
        }
        active.addAll(inactive);
        return active;
    }

    private static boolean matches(DisguiseRule rule, String query) {
        return rule.key().toLowerCase(Locale.ROOT).contains(query)
                || rule.as().toLowerCase(Locale.ROOT).contains(query)
                || DisguiseDialog.keyName(rule.key()).getString().toLowerCase(Locale.ROOT).contains(query)
                || DisguiseDialog.keyName(rule.as()).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    // --- rendering ---

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn in render(); leaving this empty keeps 1.21's blur out of the editor.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        g.fill(MARGIN, 19, this.width - MARGIN, 20, 0x40FFFFFF);

        lines = buildLines();
        int listBottom = listBottom();
        int maxScroll = Math.max(0, EditorRowList.heightFor(lines.size()) - (listBottom - LIST_TOP));
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        if (lines.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("editor.historystages.disguise.list.empty"),
                    this.width / 2, LIST_TOP + 20, GREY_TEXT);
        } else {
            boolean menuOpen = contextMenu.isVisible();
            g.enableScissor(0, LIST_TOP, this.width, listBottom);
            rows.render(new TabRenderContext(g, this.font, MARGIN, LIST_TOP - (int) scroll,
                            this.width - MARGIN * 2, LIST_TOP, listBottom,
                            menuOpen ? -1 : mouseX, menuOpen ? -1 : mouseY, menuOpen, (a, b) -> {}),
                    lines.size(), (row, i) -> describe(row, lines.get(i)));
            g.disableScissor();
        }
        g.fill(MARGIN, listBottom + 1, this.width - MARGIN, listBottom + 2, 0xFF555555);

        super.render(g, mouseX, mouseY, partialTick);
        search.render(g, this.font, mouseX, mouseY);
        contextMenu.render(g, this.font, mouseX, mouseY);
    }

    private void describe(EditorRowList.Row row, Line line) {
        DisguiseRule rule = line.rule();
        Item item = rule.isTag() ? null : Disguises.itemById(rule.key());
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            row.leading(14, (g, x, y, w, h) -> {
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(0.85f, 0.85f, 1.0f);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            });
        }

        StringBuilder text = new StringBuilder(DisguiseDialog.keyName(rule.key()).getString())
                .append("  →  ").append(DisguiseDialog.keyName(rule.as()).getString());
        DisguiseRule next = DisguiseData.get().get(rule.as());
        if (next != null && !next.isTag()) {
            text.append("  →  ").append(DisguiseDialog.keyName(next.as()).getString());
        }
        row.text(text.toString());

        if (line.active()) {
            row.subtitle(Component.translatable("editor.historystages.disguise.locked_in",
                    String.join(", ", line.stages())).getString());
        } else {
            row.subtitle(Component.translatable("editor.historystages.disguise.locked_nowhere").getString(),
                    0xFFAA33);
        }

        if (rule.drops() == DropsMode.DISGUISE) {
            row.badge("[" + Component.translatable("editor.historystages.disguise.badge.drops").getString() + "]",
                    0xCCAA66);
        }
        if (!rule.hints()) {
            row.badge("[" + Component.translatable("editor.historystages.disguise.badge.no_hints").getString() + "]",
                    0xBBBBBB);
        }
    }

    private int listBottom() {
        return this.height - 36;
    }

    // --- input ---

    private TabInputContext input(double mouseX, double mouseY) {
        return new TabInputContext(MARGIN, LIST_TOP - (int) scroll, this.width - MARGIN * 2,
                LIST_TOP, listBottom(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (search.mouseClicked(mouseX, mouseY)) return true;
        search.setFocused(false);
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int index = rows.rowAt(input(mouseX, mouseY), lines.size());
        if (index < 0) return false;
        String key = lines.get(index).rule().key();
        if (button == 0) {
            this.minecraft.setScreen(new DisguiseDialog(this, key, true));
            return true;
        }
        if (button == 1) {
            contextMenu = new ContextMenu();
            contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(),
                    () -> this.minecraft.setScreen(new DisguiseDialog(this, key, true)));
            contextMenu.addEntry(Component.translatable("editor.historystages.disguise.remove").getString(),
                    () -> PacketHandler.sendToServer(new SaveDisguisePacket(key, "", "")));
            contextMenu.show((int) mouseX, (int) mouseY, this.font);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * (EditorRowList.CARD_HEIGHT + EditorRowList.CARD_GAP);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && contextMenu.isVisible()) {
            contextMenu.hide();
            return true;
        }
        if (search.isFocused() && keyCode != 256 && search.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
