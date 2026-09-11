package net.bananemdnsa.historystages.client.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.CountInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.FormattedTextScreen;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.cache.ClientZoneSelection;
import net.bananemdnsa.historystages.client.editor.dialog.EffectValuesDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.ZoneShapePreview;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownOverlay;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableDimensionList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEffectList;
import net.bananemdnsa.historystages.client.editor.zone.ZoneSectionRail;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainSampler;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainTexture;
import net.bananemdnsa.historystages.data.lock.ZoneEffectSpec;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneMapView;
import net.bananemdnsa.historystages.data.lock.ZoneRowText;
import net.bananemdnsa.historystages.data.lock.ZoneRules;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.lock.ZoneShapeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Everything about one zone: what it is called, where it is, what shapes it is made of, and what
 * it does to a player standing inside it.
 *
 * <p>A form, not a list. That distinction decides this screen's whole shape: the editor draws
 * lists of things as cards and settings as flat labelled rows, and two earlier attempts used
 * cards for both. A name drawn as a card reads as an entry in a list, because here that is what a
 * card means.
 *
 * <p>Sections down the left rather than everything at once, each carrying a count of how much
 * inside it is on — so the question a zone raises most often, <em>is something still switched on
 * somewhere</em>, is answered without opening anything.
 *
 * <p>Edits a copy. Cancel discards it, and the caller only ever sees a zone that was confirmed —
 * which is what lets the stage editor's own cancel still mean something.
 */
public class ZoneEditScreen extends Screen {

    /** The margin every editor screen keeps to the window edge. */
    private static final int MARGIN = 10;
    private static final int TITLE_Y = 6;
    private static final int HAIRLINE_Y = 19;
    private static final int TOP = 26;
    private static final int BOTTOM_BAR = 34;
    private static final int RAIL_GAP = 12;

    /** How far a row that depends on the one above it is pushed right. */
    private static final int INDENT = 12;

    private static final int FIELD_H = 20;
    private static final int LABEL_H = 11;
    private static final int TEXT_INSET_Y = (FIELD_H - 8) / 2;

    /** The map never grows past this, however much room the section has. */
    private static final int MAP_MAX = 190;

    /** Samples per axis for the thumbnail. It is small; more would be paid for and not seen. */
    private static final int PREVIEW_MAX_CELLS = 96;

    private static final int BACKDROP = 0xE0101010;
    private static final int HAIRLINE = 0x40FFFFFF;
    private static final int SEPARATOR = 0xFF555555;
    private static final int LABEL_GREY = 0xFF999999;
    private static final int FIELD_BG = 0xFF0D0D0D;
    private static final int FIELD_BORDER = 0xFF4A4A4A;
    private static final int ACCENT = 0xFFFFCC00;

    /** The three shape types as the dropdown wants them: serialized names, in declaration order. */
    private static final List<String> TYPE_NAMES =
            Arrays.stream(ZoneShapeType.values()).map(ZoneShapeType::serializedName).toList();

    private enum Section { GENERAL, SHAPES, EFFECT, PROTECTION, DISPLAY }

    private final Screen parent;
    private final ZoneEntry zone;
    private final Consumer<ZoneEntry> onDone;

    private Section active = Section.GENERAL;

    private final ZoneSectionRail rail = new ZoneSectionRail();
    private final ZoneTerrainTexture previewTerrain = new ZoneTerrainTexture("preview");
    @Nullable
    private ZoneTerrainSampler.Surface previewSurface;
    private int previewAge;

    /** Which shape the map picks out, kept across a trip to the full map and back. */
    private int selectedShape = -1;
    private final EditorRowList formRows = new EditorRowList().flat();
    private final EditorRowList shapeRows = new EditorRowList().flat();
    private final Scrollbar scrollbar = new Scrollbar();

    private float scroll;
    private int maxScroll;

    private EditBox nameBox;
    private PickerOverlay dimensionPicker;
    private SearchableEffectList effectPicker;
    @Nullable
    private DropdownOverlay typePicker;
    /** Which shape row the type popup belongs to, or -1 for "adding a new one". */
    private int typeRow = -1;

    private final EditorTooltip tooltip = new EditorTooltip();
    @Nullable
    private String tooltipKey;
    private String tooltipText = "";

    /** Rebuilt every frame: adding a shape or an effect changes the list, and a stale index edits the wrong one. */
    private List<Consumer<EditorRowList.Row>> rows = List.of();

    public ZoneEditScreen(Screen parent, ZoneEntry zone, Consumer<ZoneEntry> onDone) {
        super(Component.translatable("editor.historystages.zone.edit.title"));
        this.parent = parent;
        this.zone = zone.copy();
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        dimensionPicker = new SearchableDimensionList(id -> {
            zone.setDimension(id);
            dimensionPicker.hide();
        });

        effectPicker = new SearchableEffectList(id -> {
            effectPicker.hide();
            List<ZoneEffectSpec> list = new ArrayList<>(zone.getRules().getEffects().getList());
            list.add(new ZoneEffectSpec(id, 30, 0));
            zone.getRules().getEffects().setList(list);
        }, () -> zone.getRules().getEffects().getList().stream().map(ZoneEffectSpec::id).toList());

        // A real text box rather than a row with the name on a button. The frame around it is
        // drawn by this screen, focus-aware, exactly as the input dialogs draw theirs.
        nameBox = new EditBox(this.font, 0, 0, 10, 8,
                Component.translatable("editor.historystages.zone.field.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(128);
        nameBox.setValue(zone.getName());
        nameBox.setResponder(zone::setName);
        addRenderableWidget(nameBox);

        int centre = this.width / 2;
        int y = this.height - BOTTOM_BAR + 6;
        addRenderableWidget(StyledButton.of(CommonComponents.GUI_DONE, b -> {
            onDone.accept(zone);
            this.minecraft.setScreen(parent);
        }, centre - 103, y, 100, 20));
        addRenderableWidget(StyledButton.of(CommonComponents.GUI_CANCEL,
                b -> this.minecraft.setScreen(parent), centre + 3, y, 100, 20));
    }

    // -------------------------------------------------------------------------------------
    // Geometry. Render and the input paths read the same methods rather than each doing the
    // arithmetic — two copies drift, and a click then lands a row away from what it looks like.
    // -------------------------------------------------------------------------------------

    private int contentX() {
        return MARGIN + ZoneSectionRail.WIDTH + RAIL_GAP;
    }

    private int contentWidth() {
        return this.width - MARGIN - contentX();
    }

    private int contentBottom() {
        return this.height - BOTTOM_BAR;
    }

    /** Room for the rows themselves, once the scrollbar has had its strip. */
    private int listWidth() {
        return contentWidth() - Scrollbar.WIDTH - 2;
    }

    /** Square rather than wide: a 100×100 area drawn into a flat strip looks like a rectangle. */
    private int mapSize() {
        return Math.max(80, Math.min(MAP_MAX,
                Math.min(contentWidth() / 2 - 6, contentBottom() - TOP)));
    }

    private int shapeListX() {
        return contentX() + mapSize() + 10;
    }

    private int shapeListWidth() {
        return this.width - MARGIN - shapeListX() - Scrollbar.WIDTH - 2;
    }

    /** In the General section the rows start below the name field. */
    private int rowsTop() {
        return active == Section.GENERAL ? TOP + LABEL_H + FIELD_H + 8 : TOP;
    }

    private int rowsX() {
        return active == Section.SHAPES ? shapeListX() : contentX();
    }

    private int rowsWidth() {
        return active == Section.SHAPES ? shapeListWidth() : listWidth();
    }

    // -------------------------------------------------------------------------------------
    // Sections
    // -------------------------------------------------------------------------------------

    private List<ZoneSectionRail.Section> sections() {
        return List.of(
                new ZoneSectionRail.Section(label("section.general"), -1),
                new ZoneSectionRail.Section(label("section.shapes"), zone.getShapes().size()),
                new ZoneSectionRail.Section(label("section.effect"), ZoneRowText.countEffects(zone)),
                new ZoneSectionRail.Section(label("section.protection"), ZoneRowText.countProtection(zone)),
                new ZoneSectionRail.Section(label("section.display"), ZoneRowText.countDisplay(zone)));
    }

    private List<Consumer<EditorRowList.Row>> buildRows() {
        return switch (active) {
            case GENERAL -> buildGeneralRows();
            case SHAPES -> buildShapeRows();
            case EFFECT -> buildEffectRows();
            case PROTECTION -> buildProtectionRows();
            case DISPLAY -> buildDisplayRows();
        };
    }

    private List<Consumer<EditorRowList.Row>> buildGeneralRows() {
        // Centred rather than hung off the slot: a panel centred on a control near the right edge
        // runs off two edges at once, and the searchable list only clamps its left and top.
        return List.of(row -> row.text(label("field.dimension"))
                .dropdown(zone.getDimension(), null, dimensionPicker.isVisible(),
                        (x, y, w, h) -> dimensionPicker.show(this.width / 2, this.height / 2,
                                this.width)));
    }

    /** Where the zone is: its shapes, and the switch that turns it inside out. */
    private List<Consumer<EditorRowList.Row>> buildShapeRows() {
        List<Consumer<EditorRowList.Row>> out = new ArrayList<>();
        List<ZoneShape> list = new ArrayList<>(zone.getShapes());

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final ZoneShape shape = list.get(i);
            out.add(row -> row
                    .text(ZoneRowText.describeShape(shape))
                    .button("×", label("shape.remove"), () -> removeShape(index))
                    .button(label("shape.edit"), () -> editShape(index, shape.type(), shape))
                    .dropdown(typeLabel(shape.type()), null,
                            typePicker != null && typePicker.isVisible() && typeRow == index,
                            (x, y, w, h) -> openTypePicker(index, x, y, h)));
        }

        out.add(row -> row.text(label("shape.add"))
                .dropdown("+", label("shape.type"),
                        typePicker != null && typePicker.isVisible() && typeRow < 0,
                        (x, y, w, h) -> openTypePicker(-1, x, y, h)));

        if (ClientZoneSelection.isComplete()
                && ClientZoneSelection.dimension().equals(zone.getDimension())) {
            out.add(row -> row.text(label("selection.take"))
                    .button(label("selection.apply"), this::takeSelection));
        }

        // Inverting belongs to the geometry, not to the rules: it does not change what the zone
        // does, it changes where it applies — everywhere but here.
        ZoneRules rules = zone.getRules();
        out.add(toggleRow("rule.inverted", rules.isInverted(), rules::setInverted));

        return out;
    }

    private List<Consumer<EditorRowList.Row>> buildEffectRows() {
        ZoneRules rules = zone.getRules();
        ZoneRules.Message message = rules.getMessage();
        ZoneRules.Damage damage = rules.getDamage();
        ZoneRules.Effects effects = rules.getEffects();
        List<Consumer<EditorRowList.Row>> out = new ArrayList<>();

        // Dependent rows are indented and only there while their switch is on. That is what a
        // frame around a group would otherwise have said, without drawing a box.
        out.add(toggleRow("rule.message", message.isEnabled(), message::setEnabled));
        if (message.isEnabled()) {
            out.add(row -> row.indent(INDENT).text(label("rule.message_text"))
                    .button(message.getText().isEmpty() ? "—" : trim(message.getText()),
                            this::editMessage));
            out.add(indented(toggleRow("rule.message_in_chat", message.isInChat(), message::setInChat)));
        }

        out.add(toggleRow("rule.damage", damage.isEnabled(), damage::setEnabled));
        if (damage.isEnabled()) {
            out.add(row -> row.indent(INDENT).text(label("rule.damage_amount"))
                    .button(String.valueOf(damage.getAmount()), this::editDamageAmount));
            out.add(row -> row.indent(INDENT).text(label("rule.damage_interval"))
                    .button(String.valueOf(damage.getInterval()), this::editDamageInterval));
        }

        out.add(toggleRow("rule.effects", effects.isEnabled(), effects::setEnabled));
        if (effects.isEnabled()) {
            List<ZoneEffectSpec> specs = new ArrayList<>(effects.getList());
            for (int i = 0; i < specs.size(); i++) {
                final int index = i;
                final ZoneEffectSpec spec = specs.get(i);
                out.add(row -> row.indent(INDENT)
                        .text(SearchableEffectList.displayName(spec.id()))
                        .button("×", null, () -> removeEffect(index))
                        .button(Component.translatable("editor.historystages.zone.effect.summary",
                                        spec.seconds(), spec.amplifier() + 1).getString(),
                                null, () -> editEffect(index)));
            }
            out.add(row -> row.indent(INDENT).text(label("effect.add"))
                    .button("+", null, this::openEffectPicker));
            out.add(indented(toggleRow("rule.clear_on_leave", effects.isClearOnLeave(),
                    effects::setClearOnLeave)));
        }
        return out;
    }

    private List<Consumer<EditorRowList.Row>> buildProtectionRows() {
        ZoneRules rules = zone.getRules();
        return List.of(
                toggleRow("rule.block_right_click", rules.isBlockRightClick(), rules::setBlockRightClick),
                toggleRow("rule.block_left_click", rules.isBlockLeftClick(), rules::setBlockLeftClick),
                toggleRow("rule.block_projectiles", rules.isBlockProjectiles(), rules::setBlockProjectiles),
                toggleRow("rule.block_explosions", rules.isBlockExplosions(), rules::setBlockExplosions),
                toggleRow("rule.barrier", rules.isBarrier(), rules::setBarrier),
                toggleRow("rule.block_spawns", rules.isBlockSpawns(), rules::setBlockSpawns));
    }

    private List<Consumer<EditorRowList.Row>> buildDisplayRows() {
        ZoneRules rules = zone.getRules();
        return List.of(
                toggleRow("rule.show_border", rules.isShowBorder(), rules::setShowBorder),
                toggleRow("rule.show_overlay", rules.isShowOverlay(), rules::setShowOverlay));
    }

    private Consumer<EditorRowList.Row> toggleRow(String key, boolean value, Consumer<Boolean> setter) {
        return row -> row.text(label(key)).toggle(value, label(key + ".desc"), setter::accept);
    }

    private static Consumer<EditorRowList.Row> indented(Consumer<EditorRowList.Row> inner) {
        return row -> inner.accept(row.indent(INDENT));
    }

    private static String label(String key) {
        return Component.translatable("editor.historystages.zone." + key).getString();
    }

    private static String typeLabel(ZoneShapeType type) {
        return Component.translatable("editor.historystages.zone.shape." + type.serializedName()).getString();
    }

    private static String trim(String text) {
        return text.length() <= 18 ? text : text.substring(0, 17) + "…";
    }

    // -------------------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------------------

    private void editMessage() {
        this.minecraft.setScreen(new FormattedTextScreen(this,
                Component.translatable("editor.historystages.zone.rule.message_text"),
                zone.getRules().getMessage().getText(),
                Component.translatable("editor.historystages.zone.rule.message_text.desc").getString(),
                List.of("{zone}", "{stage}"),
                text -> zone.getRules().getMessage().setText(text)));
    }

    private void editDamageAmount() {
        ZoneRules.Damage damage = zone.getRules().getDamage();
        this.minecraft.setScreen(new CountInputScreen(
                this, Component.translatable("editor.historystages.zone.rule.damage_amount"),
                "", (int) Math.round(damage.getAmount()), 1, 100, damage::setAmount));
    }

    private void editDamageInterval() {
        ZoneRules.Damage damage = zone.getRules().getDamage();
        this.minecraft.setScreen(new CountInputScreen(
                this, Component.translatable("editor.historystages.zone.rule.damage_interval"),
                "", damage.getInterval(), 1, 600, damage::setInterval));
    }

    private void editShape(int index, ZoneShapeType type, ZoneShape initial) {
        editShape(this, index, type, initial);
    }

    /**
     * @param returnTo where the dialog goes back to — the map opens it too, and landing in the tab
     *                 from there would look like the map had closed itself
     */
    private void editShape(Screen returnTo, int index, ZoneShapeType type, ZoneShape initial) {
        this.minecraft.setScreen(new ZoneShapeEditScreen(returnTo, type, initial, shape -> {
            List<ZoneShape> list = new ArrayList<>(zone.getShapes());
            if (index < 0) {
                list.add(shape);
            } else {
                list.set(index, shape);
            }
            zone.setShapes(list);
        }));
    }

    private void removeShape(int index) {
        List<ZoneShape> list = new ArrayList<>(zone.getShapes());
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            zone.setShapes(list);
        }
    }

    /**
     * Opens the type popup under the slot that was clicked.
     *
     * <p>Replaces a caret that cycled. It wore the chevron every other menu in this editor wears
     * while doing something else entirely, and a control that looks like a menu but is not is the
     * kind of surprise that makes people stop trusting the first click.
     *
     * <p>{@link DropdownOverlay} rather than a dropdown drawing itself: the rows are scissored, so
     * a popup painted by the row would be cut off at the bottom of the section.
     */
    private void openTypePicker(int index, int x, int y, int height) {
        if (typePicker == null) {
            // Fires on every pick: on the add row this is a menu, and the type it happens to be
            // showing would otherwise be the one type you could not add.
            typePicker = new DropdownOverlay(new EnumDropdown(TYPE_NAMES, TYPE_NAMES.get(0), 0,
                    name -> Component.translatable("editor.historystages.zone.shape." + name),
                    this::applyType).alwaysFires());
        }
        typeRow = index;
        ZoneShapeType current = index < 0 || index >= zone.getShapes().size()
                ? ZoneShapeType.values()[0]
                : zone.getShapes().get(index).type();
        typePicker.dropdown().setValue(current.serializedName());
        typePicker.openAt(x, y, height);
    }

    /** Picked out of the type popup: a new shape below zero, otherwise an existing one changes. */
    private void applyType(String name) {
        typePicker.hide();
        ZoneShapeType picked = ZoneShapeType.valueOf(name.toUpperCase(Locale.ROOT));
        if (typeRow < 0) {
            editShape(-1, picked, null);
        } else if (typeRow < zone.getShapes().size()
                && zone.getShapes().get(typeRow).type() != picked) {
            // On a shape row the dropdown does hold a value, so picking the one already shown
            // stays a no-op — the popup fires either way now, and this is where that is decided.
            changeType(typeRow, picked);
        }
        typeRow = -1;
    }

    /** Keeps whatever numbers still mean something when a shape changes type. */
    private void changeType(int index, ZoneShapeType next) {
        List<ZoneShape> list = new ArrayList<>(zone.getShapes());
        if (index < 0 || index >= list.size()) return;

        ZoneShape shape = list.get(index);
        list.set(index, new ZoneShape(next, shape.fromX(), shape.fromY(), shape.fromZ(),
                shape.toX(), shape.toY(), shape.toZ(),
                shape.radius() == 0 ? 16 : shape.radius(),
                shape.height() == 0 ? 32 : shape.height(),
                shape.fullHeight()));
        zone.setShapes(list);
    }

    /** Turns the two corners marked out in the world into a cube. */
    private void takeSelection() {
        List<ZoneShape> list = new ArrayList<>(zone.getShapes());
        list.add(ZoneShape.cube(
                ClientZoneSelection.firstX(), ClientZoneSelection.firstY(), ClientZoneSelection.firstZ(),
                ClientZoneSelection.secondX(), ClientZoneSelection.secondY(), ClientZoneSelection.secondZ(),
                false));
        zone.setShapes(list);
    }

    private void openEffectPicker() {
        effectPicker.setFilter("");
        effectPicker.show(this.width / 2, this.height / 2, this.width);
    }

    private void removeEffect(int index) {
        List<ZoneEffectSpec> list = new ArrayList<>(zone.getRules().getEffects().getList());
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            zone.getRules().getEffects().setList(list);
        }
    }

    private void editEffect(int index) {
        List<ZoneEffectSpec> list = new ArrayList<>(zone.getRules().getEffects().getList());
        if (index < 0 || index >= list.size()) return;

        ZoneEffectSpec spec = list.get(index);
        this.minecraft.setScreen(new EffectValuesDialog(this, spec.id(), spec.seconds(),
                spec.amplifier(), (seconds, amplifier) -> {
                    List<ZoneEffectSpec> updated =
                            new ArrayList<>(zone.getRules().getEffects().getList());
                    if (index < updated.size()) {
                        updated.set(index, new ZoneEffectSpec(spec.id(), seconds, amplifier));
                        zone.getRules().getEffects().setList(updated);
                    }
                }));
    }

    private void selectSection(Section section) {
        if (active == section) return;
        active = section;
        scroll = 0;
        formRows.resetSlideIn();
        shapeRows.resetSlideIn();
    }

    // -------------------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------------------

    /** Own background, drawn in render — this dodges 1.21's menu blur, as the config screen does. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        nameBox.visible = active == Section.GENERAL;

        g.fill(0, 0, this.width, this.height, BACKDROP);
        g.drawCenteredString(this.font, title(), this.width / 2, TITLE_Y, 0xFFFFFF);
        renderBetaMark(g);
        g.fill(MARGIN, HAIRLINE_Y, this.width - MARGIN, HAIRLINE_Y + 1, HAIRLINE);
        g.fill(MARGIN, contentBottom() + 4, this.width - MARGIN, contentBottom() + 5, SEPARATOR);

        rows = buildRows();
        PickerOverlay open = openPicker();
        boolean blocked = open != null;
        tooltipKey = null;

        rail.render(g, this.font, MARGIN, TOP, sections(), active.ordinal(), mouseX, mouseY, blocked);

        if (active == Section.GENERAL) renderNameField(g, blocked);
        int hovered = active == Section.SHAPES ? renderMap(g, mouseX, mouseY, blocked) : -1;
        renderRows(g, mouseX, mouseY, blocked);

        // Late, so the buttons sit on top of the sections rather than under them.
        super.render(g, mouseX, mouseY, partialTick);

        if (open != null) {
            open.render(g, this.font, mouseX, mouseY);
        } else {
            tooltip.render(g, this.font, tooltipKey, tooltipText, mouseX, mouseY,
                    this.width, this.height);
        }
    }

    /** Zones are the newest category and the least walked-in; the corner says so while that lasts. */
    private void renderBetaMark(GuiGraphics g) {
        String mark = label("beta");
        g.drawString(this.font, mark, this.width - MARGIN - this.font.width(mark), TITLE_Y,
                ACCENT, false);
    }

    /** The title carries the name, so the screen says which zone you are standing in. */
    private Component title() {
        return zone.getName().isEmpty()
                ? Component.translatable("editor.historystages.zone.edit.title")
                : Component.translatable("editor.historystages.zone.edit.title_named", zone.getName());
    }

    private void renderNameField(GuiGraphics g, boolean blocked) {
        int x = contentX();
        int w = Math.min(240, contentWidth());
        int fy = TOP + LABEL_H;

        g.drawString(this.font, label("field.name"), x, TOP, LABEL_GREY, false);
        g.fill(x - 1, fy - 1, x + w + 1, fy + FIELD_H + 1,
                nameBox.isFocused() ? ACCENT : FIELD_BORDER);
        g.fill(x, fy, x + w, fy + FIELD_H, FIELD_BG);

        nameBox.setX(x + 4);
        nameBox.setY(fy + TEXT_INSET_Y);
        nameBox.setWidth(w - 8);
        nameBox.active = !blocked;
    }

    /** @return the shape index under the cursor, so the map can pick it out. */
    private int renderMap(GuiGraphics g, int mouseX, int mouseY, boolean blocked) {
        int size = mapSize();
        int hovered = blocked ? -1 : shapeRows.rowAt(inputContext(shapeListX(),
                TOP - (int) scroll, shapeListWidth(), TOP, contentBottom(), mouseX, mouseY),
                rows.size());
        // The list holds more than shapes — the add row and the invert switch sit below them, and
        // hovering those must not light one up on the map.
        if (hovered >= zone.getShapes().size()) hovered = -1;

        refreshPreviewTerrain(size);
        // The row under the cursor wins over the standing selection: pointing at a row is a
        // question about that row, and answering it with the previous pick would be a lie.
        int shown = hovered >= 0 ? hovered : selectedShape;
        ZoneShapePreview.render(g, this.font, contentX(), TOP, size, size,
                zone.getShapes(), shown, playerInSameDimension(), previewTerrain, previewSurface);

        boolean overMap = !blocked && mouseX >= contentX() && mouseX < contentX() + size
                && mouseY >= TOP && mouseY < TOP + size;
        if (overMap) {
            g.fill(contentX(), TOP, contentX() + size, TOP + 1, ACCENT);
            tooltipKey = "map";
            tooltipText = label("map.enlarge");
        }
        return hovered;
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY, boolean blocked) {
        int x = rowsX();
        int top = rowsTop();
        int bottom = contentBottom();
        int w = rowsWidth();
        EditorRowList list = active == Section.SHAPES ? shapeRows : formRows;

        maxScroll = Math.max(0, list.heightForRows(rows.size()) - (bottom - top));
        scroll = Math.min(scroll, maxScroll);

        g.enableScissor(x, top, x + w + Scrollbar.WIDTH + 2, bottom);
        list.render(context(g, x, top - (int) scroll, w, top, bottom, mouseX, mouseY, blocked),
                rows.size(), (row, i) -> rows.get(i).accept(row));
        g.disableScissor();

        scrollbar.render(g, x + w + 2, top, bottom, scroll, maxScroll, mouseX, mouseY);
    }

    private TabRenderContext context(GuiGraphics g, int x, int y, int width, int clipTop,
                                     int clipBottom, int mouseX, int mouseY, boolean blocked) {
        return new TabRenderContext(g, this.font, x, y, width, clipTop, clipBottom,
                mouseX, mouseY, blocked, (key, text) -> {
            tooltipKey = key;
            tooltipText = text;
        });
    }

    private static TabInputContext inputContext(int x, int y, int width, int clipTop,
                                                int clipBottom, double mouseX, double mouseY) {
        return new TabInputContext(x, y, width, clipTop, clipBottom, mouseX, mouseY);
    }

    /**
     * Sampled again when the fit has moved, and after that only while samples are still unknown.
     * Chunks trickle in; without the second case a hole would sit there until something else
     * changed.
     */
    private void refreshPreviewTerrain(int size) {
        if (this.minecraft == null || this.minecraft.level == null || !playerInSameDimension()) {
            previewSurface = null;
            return;
        }

        ZoneMapView view = ZoneMapView.fitting(zone.getShapes(), size, size);
        double needed = Math.max(view.blocksPerPixel(),
                size * view.blocksPerPixel() / PREVIEW_MAX_CELLS);
        int step = 1;
        while (step < needed) step <<= 1;

        // Snapped to a fixed grid, the same as the full map: measured from wherever the view starts
        // instead, every change of zoom moves all the sample points and the ground crawls.
        int minX = Math.floorDiv((int) Math.floor(view.worldMinX()), step) * step - step;
        int minZ = Math.floorDiv((int) Math.floor(view.worldMinZ()), step) * step - step;
        int cells = Math.min(PREVIEW_MAX_CELLS,
                (int) Math.ceil(size * view.blocksPerPixel() / step) + 3);

        boolean same = previewSurface != null && previewSurface.step() == step
                && previewSurface.minX() == minX && previewSurface.minZ() == minZ
                && previewSurface.cols() == cells;
        boolean retry = previewSurface != null && previewSurface.anyUnknown() && ++previewAge > 20;
        if (same && !retry) return;

        previewAge = 0;
        previewSurface = ZoneTerrainSampler.sample(this.minecraft.level, minX, minZ,
                step, cells, cells);
        previewTerrain.upload(previewSurface);
    }

    @Override
    public void removed() {
        previewTerrain.close();
        super.removed();
    }

    /** The marker belongs on the map only when the player is in the world the zone lives in. */
    private boolean playerInSameDimension() {
        return this.minecraft.player != null
                && this.minecraft.player.level().dimension().location().toString()
                        .equals(zone.getDimension());
    }

    /** Whichever picker is up, or null. Any of them blocks the section underneath. */
    @Nullable
    private PickerOverlay openPicker() {
        if (dimensionPicker != null && dimensionPicker.isVisible()) return dimensionPicker;
        if (effectPicker != null && effectPicker.isVisible()) return effectPicker;
        if (typePicker != null && typePicker.isVisible()) return typePicker;
        return null;
    }

    // -------------------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PickerOverlay open = openPicker();
        if (open != null) {
            if (open.mouseClicked(mouseX, mouseY)) return true;
            open.hide();
            return true;
        }

        if (button == 0) {
            if (scrollbar.mouseClicked(mouseX, mouseY)) {
                scroll = scrollbar.scrollFor(mouseY);
                return true;
            }

            int section = rail.sectionAt(MARGIN, TOP, Section.values().length, mouseX, mouseY);
            if (section >= 0) {
                selectSection(Section.values()[section]);
                return true;
            }

            if (active == Section.SHAPES) {
                int size = mapSize();
                if (mouseX >= contentX() && mouseX < contentX() + size
                        && mouseY >= TOP && mouseY < TOP + size) {
                    this.minecraft.setScreen(new ZoneMapScreen(this, zone, selectedShape,
                            index -> selectedShape = index,
                            (returnTo, index) -> {
                                ZoneShape shape = zone.getShapes().get(index);
                                editShape(returnTo, index, shape.type(), shape);
                            }));
                    return true;
                }
            }

            EditorRowList list = active == Section.SHAPES ? shapeRows : formRows;
            if (list.mouseClicked(inputContext(rowsX(), rowsTop() - (int) scroll, rowsWidth(),
                    rowsTop(), contentBottom(), mouseX, mouseY))) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbar.isDragging()) {
            scroll = scrollbar.scrollFor(mouseY);
            return true;
        }
        PickerOverlay open = openPicker();
        if (open != null && open.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbar.mouseReleased();
        PickerOverlay open = openPicker();
        if (open != null && open.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PickerOverlay open = openPicker();
        if (open != null && open.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (float) scrollY * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        PickerOverlay open = openPicker();
        if (open != null && open.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        PickerOverlay open = openPicker();
        if (open != null && open.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    // Escape counts as Cancel, not as leaving the editor: without this the default closes the
    // whole GUI and drops the player back into the world instead of one level up.
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
