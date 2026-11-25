package net.bananemdnsa.historystages.client.editor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.nbt.CriterionCard;
import net.bananemdnsa.historystages.client.editor.nbt.CustomDataCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.EntityNbtPresets;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriteriaCodec;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriterion;
import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEntityNbtKeyList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemPropertyList;
import net.bananemdnsa.historystages.client.editor.widget.popup.WoodTypesPopup;
import net.bananemdnsa.historystages.data.lock.EntityNbtFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The item NBT editor's card layout, cut down to what an entity filter can hold.
 *
 * <p>An entity is matched against its whole saved tag, so every criterion is a free key and value;
 * the item editor's component and enchantment shapes have no meaning here. What it adds instead is
 * variants: the filter is a list of alternatives of which any one has to match, and each variant
 * is edited as its own set of cards.
 */
public class EntityNbtEditScreen extends Screen {

    /**
     * Keys that change every tick or describe one particular entity. Offering them under "take from
     * a creature" would only produce criteria that never match a second time.
     */
    private static final Set<String> VOLATILE_KEYS = Set.of(
            "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
            "PortalCooldown", "UUID", "TicksFrozen", "HasVisualFire", "Brain", "HurtTime",
            "HurtByTimestamp", "DeathTime", "AbsorptionAmount", "FallFlying", "attributes",
            "active_effects", "Passengers", "leash", "Leash", "SleepingX", "SleepingY", "SleepingZ",
            "ArmorDropChances", "HandDropChances", "body_armor_drop_chance", "InLove", "LoveCause",
            "ForcedAge", "neoforge:attachments", "NeoForgeData", "CanUpdate", "FireImmune");

    private static final int PADDING = 20;
    private static final int HEADER_HEIGHT = 74;
    private static final int TOOLBAR_Y = 42;
    private static final int TOOLBAR_H = 18;
    private static final int CARD_GAP = 6;
    private static final int FOOTER_HEIGHT = 40;
    private static final int CHIP_H = 14;
    private static final int CHIP_GAP = 4;
    private static final int SEARCH_RADIUS = 64;

    private final Screen parent;
    private final String entityId;
    private final EntityNbtFilter current;
    private final Consumer<EntityNbtFilter> onSave;

    /** One criteria list per alternative. Never empty: a filter with no variant still edits one. */
    private final List<List<NbtCriterion>> variants = new ArrayList<>();
    private int active = 0;
    /** Survives init(), which reruns every time a value dialog closes. */
    private boolean loaded = false;

    private final List<CriterionCard.Built> cards = new ArrayList<>();
    private CriterionCard card;
    private PickerOverlay pickerOverlay;
    private ContextMenu chipMenu = new ContextMenu();
    private JsonElement savedSnapshot;
    private boolean dirty = false;
    private boolean showJson = false;
    private List<FormattedCharSequence> jsonLines = List.of();
    private int jsonScroll = 0;

    private double scrollOffset = 0;
    private final Anim smoothScroll = new Anim();
    private int maxScroll = 0;
    private final Scrollbar scrollbar = new Scrollbar();

    public EntityNbtEditScreen(Screen parent, String entityId, EntityNbtFilter current,
                               Consumer<EntityNbtFilter> onSave) {
        super(Component.translatable("editor.historystages.entity_nbt.heading"));
        this.parent = parent;
        this.entityId = entityId;
        this.current = current != null ? current : EntityNbtFilter.NONE;
        this.onSave = onSave;
    }

    private List<NbtCriterion> criteria() {
        return variants.get(active);
    }

    @Override
    protected void init() {
        if (!loaded) {
            for (JsonObject alternative : current.alternatives()) {
                variants.add(load(alternative));
            }
            if (variants.isEmpty()) variants.add(new ArrayList<>());
            savedSnapshot = filter().toJson();
            loaded = true;
        }
        this.card = new CriterionCard(this.font, true);

        int x = PADDING;
        x += toolbarButton("editor.historystages.nbt.add_criterion", this::openKeyPicker, x);
        x += toolbarButton("editor.historystages.entity_nbt.from_entity", this::takeFromNearbyEntity, x);
        x += toolbarButton("editor.historystages.entity_nbt.add_variant", this::addVariant, x);
        toolbarButton(showJson ? "editor.historystages.nbt.hide_json" : "editor.historystages.nbt.show_json",
                () -> {
                    showJson = !showJson;
                    this.rebuildWidgets();
                }, x);

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> this.minecraft.setScreen(parent),
                PADDING, this.height - 30, 60, 20));

        Component clearLabel = Component.translatable("editor.historystages.nbt.clear_all");
        this.addRenderableWidget(StyledButton.of(clearLabel, btn -> confirmClear(),
                PADDING + 66, this.height - 30, this.font.width(clearLabel) + 16, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.nbt.save"),
                btn -> save(),
                this.width - PADDING - 100, this.height - 30, 100, 20));

        refresh();
    }

    /** Adds one toolbar button at x and returns the width it took, gap included. */
    private int toolbarButton(String key, Runnable action, int x) {
        Component label = Component.translatable(key);
        int w = this.font.width(label) + 16;
        this.addRenderableWidget(StyledButton.of(label, btn -> action.run(), x, TOOLBAR_Y, w, TOOLBAR_H));
        return w + 6;
    }

    // ==========================================
    // Model
    // ==========================================

    private static List<NbtCriterion> load(JsonObject alternative) {
        List<NbtCriterion> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : alternative.entrySet()) {
            out.add(criterion(entry.getKey(), asText(entry.getValue())));
        }
        return out;
    }

    private static String asText(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    private EntityNbtFilter filter() {
        List<JsonObject> alternatives = new ArrayList<>();
        for (List<NbtCriterion> variant : variants) {
            alternatives.add(NbtCriteriaCodec.write(variant));
        }
        return EntityNbtFilter.of(alternatives);
    }

    private void refresh() {
        cards.clear();
        if (card != null) {
            int width = contentWidth();
            for (NbtCriterion criterion : criteria()) {
                cards.add(card.layout(criterion, width, List.of()));
            }
        }
        JsonElement written = filter().toJson();
        dirty = written == null ? savedSnapshot != null : !written.equals(savedSnapshot);
        refreshJson(written);

        int total = 0;
        for (CriterionCard.Built built : cards) total += built.height() + CARD_GAP;
        maxScroll = Math.max(0, total - (listBottom() - listTop()));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void refreshJson(JsonElement json) {
        jsonScroll = 0;
        if (!showJson) {
            jsonLines = List.of();
            return;
        }
        String text = json == null
                ? Component.translatable("editor.historystages.nbt.json.empty").getString()
                : new GsonBuilder().setPrettyPrinting().create().toJson(json);
        jsonLines = this.font.split(Component.literal(text), jsonPanelWidth() - 16);
    }

    /** Drops one variant and keeps the one on screen selected, unless it was the one removed. */
    private void removeVariant(int index) {
        if (variants.size() <= 1) return;
        variants.remove(index);
        if (index < active || active >= variants.size()) active = Math.max(0, active - 1);
        scrollOffset = 0;
        smoothScroll.set(0);
        refresh();
    }

    private void addVariant() {
        variants.add(new ArrayList<>());
        switchTo(variants.size() - 1);
    }

    private void switchTo(int index) {
        active = index;
        scrollOffset = 0;
        smoothScroll.set(0);
        refresh();
    }

    /**
     * Clears the variant on screen. With more than one variant the variant itself goes too: an
     * empty alternative would match every entity and quietly cancel out all the others.
     */
    private void confirmClear() {
        if (criteria().isEmpty() && variants.size() == 1) return;
        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.nbt.clear_all.confirm.title"),
                Component.translatable("editor.historystages.nbt.clear_all.confirm.message", criteria().size()),
                () -> {
                    if (variants.size() > 1) {
                        variants.remove(active);
                        active = Math.min(active, variants.size() - 1);
                    } else {
                        criteria().clear();
                    }
                    refresh();
                }));
    }

    private void save() {
        // Empty variants would match everything, so they are dropped rather than written.
        variants.removeIf(List::isEmpty);
        if (variants.isEmpty()) variants.add(new ArrayList<>());
        active = Math.min(active, variants.size() - 1);

        EntityNbtFilter filter = filter();
        onSave.accept(filter);
        savedSnapshot = filter.toJson();
        refresh();
    }

    // ==========================================
    // Adding criteria
    // ==========================================

    /**
     * The item editor's "add criterion" picker. "Common" names the properties people actually lock
     * by, "All keys" lists what a fresh entity of the type saves with its default value, and the
     * last tab takes a key typed by hand.
     */
    private void openKeyPicker() {
        Map<String, String> defaults = defaultKeys();

        List<SearchableEntityNbtKeyList.Option> common = new ArrayList<>();
        for (EntityNbtPresets.Preset preset : EntityNbtPresets.all()) {
            if (!applies(preset)) continue;
            common.add(new SearchableEntityNbtKeyList.Option(preset.key(),
                    Component.translatable(preset.nameKey()).getString(), preset.key(),
                    Component.translatable(preset.descriptionKey()).getString()));
        }

        List<SearchableEntityNbtKeyList.Option> keys = new ArrayList<>();
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            String value = entry.getValue();
            keys.add(new SearchableEntityNbtKeyList.Option(entry.getKey(), entry.getKey(), value,
                    Component.translatable("editor.historystages.entity_nbt.key_tooltip", value).getString()));
        }

        SearchableEntityNbtKeyList picker = new SearchableEntityNbtKeyList(common, keys, key -> {
            closePicker();
            if (SearchableEntityNbtKeyList.CUSTOM_KEY.equals(key)) {
                openAddDialog();
                return;
            }
            criteria().removeIf(c -> c.identity().equals(key));
            criteria().add(criterion(key, startValue(key, defaults)));
            refresh();
        }, () -> criteria().stream().map(NbtCriterion::identity).collect(Collectors.toList()));
        pickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    /** A criterion for the key, carrying the preset's name and description when it has one. */
    private static CustomDataCriterion criterion(String key, String value) {
        CustomDataCriterion criterion = new CustomDataCriterion(key, value, false);
        EntityNbtPresets.Preset preset = EntityNbtPresets.byKey(key);
        if (preset != null) {
            criterion.presetName = preset.nameKey();
            criterion.presetDescription = preset.descriptionKey();
        }
        return criterion;
    }

    /**
     * What a newly added card starts with: the entity's own default where it saves one, so the
     * value already has the right shape, else the preset's example, else the first choice.
     */
    private String startValue(String key, Map<String, String> defaults) {
        String own = defaults.get(key);
        if (own != null && !own.isBlank()) return own;
        EntityNbtPresets.Preset preset = EntityNbtPresets.byKey(key);
        if (preset == null) return "";
        if (!preset.defaultValue().isBlank()) return preset.defaultValue();
        List<Choice> choices = choicesFor(key);
        return choices.isEmpty() ? "" : choices.get(0).value();
    }

    private boolean applies(EntityNbtPresets.Preset preset) {
        Entity probe = probe();
        return switch (preset.scope()) {
            case ANY -> true;
            case LIVING -> probe instanceof LivingEntity;
            case MOB -> probe instanceof Mob;
            case KEYED -> probeTag().contains(preset.key());
            case TYPES -> preset.types().contains(entityId);
        };
    }

    private Entity probe;
    private CompoundTag probeTag;
    private boolean probed = false;

    /**
     * A fresh entity of the type, made once. Never added to the world; it only answers what the
     * type is and what it saves.
     */
    private Entity probe() {
        if (!probed) {
            probed = true;
            EntityType<?> type = entityType();
            if (type != null && this.minecraft.level != null) {
                try {
                    probe = type.create(this.minecraft.level);
                    if (probe != null) probeTag = probe.saveWithoutId(new CompoundTag());
                } catch (RuntimeException e) {
                    // Some modded entities refuse to be created or saved outside a real spawn;
                    // the picker then still offers the common flags and the hand-typed key.
                    probe = null;
                    probeTag = null;
                }
            }
        }
        return probe;
    }

    private CompoundTag probeTag() {
        probe();
        return probeTag != null ? probeTag : new CompoundTag();
    }

    /** Keys and default values of a fresh entity of this type, volatile ones left out. */
    private Map<String, String> defaultKeys() {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, probeTag());
        if (!json.isJsonObject()) return out;
        List<String> keys = new ArrayList<>(json.getAsJsonObject().keySet());
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        for (String key : keys) {
            if (!VOLATILE_KEYS.contains(key)) out.put(key, asText(json.getAsJsonObject().get(key)));
        }
        return out;
    }

    // ==========================================
    // Value choices
    // ==========================================

    /** One suggested value and what it means, e.g. 14 = Red for a sheep's colour. */
    private record Choice(String value, String label) {
        String display() {
            return label == null || label.equals(value) ? value : value + CHOICE_SEPARATOR + label;
        }
    }

    private static final String CHOICE_SEPARATOR = "  —  ";

    /**
     * The values a key can take on this entity type. Read from the game rather than written down,
     * so modded entries show up too where they live in a registry.
     */
    private List<Choice> choicesFor(String key) {
        EntityNbtPresets.Preset preset = EntityNbtPresets.byKey(key);
        List<Choice> out = new ArrayList<>();
        if (preset != null && preset.kind() == EntityNbtPresets.Kind.BOOL) {
            out.add(new Choice("true", null));
            out.add(new Choice("false", null));
            return out;
        }
        Entity probe = probe();
        switch (key) {
            case "Type" -> {
                if (probe instanceof Boat) {
                    for (Boat.Type wood : Boat.Type.values()) out.add(new Choice(wood.getSerializedName(), null));
                } else if (probe instanceof Fox) {
                    for (Fox.Type fox : Fox.Type.values()) out.add(new Choice(fox.getSerializedName(), null));
                } else if (probe instanceof MushroomCow) {
                    for (MushroomCow.MushroomType cow : MushroomCow.MushroomType.values()) {
                        out.add(new Choice(cow.getSerializedName(), null));
                    }
                }
            }
            case "variant" -> {
                if (probe instanceof Cat) {
                    for (ResourceLocation id : BuiltInRegistries.CAT_VARIANT.keySet()) out.add(new Choice(id.toString(), null));
                } else if (probe instanceof Frog) {
                    for (ResourceLocation id : BuiltInRegistries.FROG_VARIANT.keySet()) out.add(new Choice(id.toString(), null));
                } else if (probe instanceof Wolf && this.minecraft.level != null) {
                    for (ResourceLocation id : this.minecraft.level.registryAccess()
                            .registryOrThrow(Registries.WOLF_VARIANT).keySet()) {
                        out.add(new Choice(id.toString(), null));
                    }
                }
                out.sort((a, b) -> a.value().compareTo(b.value()));
            }
            case "Variant" -> {
                if (probe instanceof Axolotl) {
                    for (Axolotl.Variant v : Axolotl.Variant.values()) out.add(new Choice(String.valueOf(v.getId()), v.getName()));
                } else if (probe instanceof Parrot) {
                    for (Parrot.Variant v : Parrot.Variant.values()) out.add(new Choice(String.valueOf(v.getId()), v.getSerializedName()));
                } else if (probe instanceof Llama) {
                    for (Llama.Variant v : Llama.Variant.values()) out.add(new Choice(String.valueOf(v.getId()), v.getSerializedName()));
                }
            }
            case "Color" -> {
                for (DyeColor color : DyeColor.values()) {
                    out.add(new Choice(String.valueOf(color.getId()),
                            Component.translatable("color.minecraft." + color.getName()).getString()));
                }
            }
            case "RabbitType" -> {
                for (Rabbit.Variant v : Rabbit.Variant.values()) out.add(new Choice(String.valueOf(v.id()), v.getSerializedName()));
            }
            case "MainGene" -> {
                for (Panda.Gene gene : Panda.Gene.values()) out.add(new Choice(gene.getSerializedName(), null));
            }
            case "VillagerData" -> {
                for (ResourceLocation id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
                    out.add(new Choice("{\"profession\": \"" + id + "\"}", null));
                }
                out.sort((a, b) -> a.value().compareTo(b.value()));
            }
            default -> { }
        }
        return out;
    }

    /** Turns a picked "14  —  Red" back into the 14 that is stored. */
    private static String stripChoiceLabel(String text) {
        int cut = text.indexOf(CHOICE_SEPARATOR);
        return cut < 0 ? text : text.substring(0, cut);
    }

    private void closePicker() {
        if (pickerOverlay != null) {
            pickerOverlay.hide();
            pickerOverlay = null;
        }
    }

    private void openAddDialog() {
        this.minecraft.setScreen(new KeyValueDialog(this, (key, value) -> {
            criteria().removeIf(c -> c.identity().equals(key));
            criteria().add(criterion(key, value));
            refresh();
        }));
    }

    /**
     * Reads the criteria off a real entity of this type: the one under the crosshair, or else the
     * nearest one loaded. Same idea as "take from an item" — nobody has to know the key names.
     *
     * <p>The client only knows the data the server syncs, so a mod's server-side fields can be
     * missing here. They still match on the server once typed in by hand.
     */
    private void takeFromNearbyEntity() {
        Entity source = findSourceEntity();
        Component title = Component.translatable("editor.historystages.entity_nbt.from_entity");
        if (source == null) {
            EditorToastHandler.show(EditorToast.Level.INFO, title,
                    Component.translatable("editor.historystages.entity_nbt.from_entity.none", entityName()));
            return;
        }

        CompoundTag tag;
        try {
            tag = source.saveWithoutId(new CompoundTag());
        } catch (RuntimeException e) {
            tag = new CompoundTag();
        }
        JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);

        Map<String, NbtCriterion> byKey = new LinkedHashMap<>();
        if (json.isJsonObject()) {
            List<String> keys = new ArrayList<>(json.getAsJsonObject().keySet());
            keys.sort(String.CASE_INSENSITIVE_ORDER);
            for (String key : keys) {
                if (VOLATILE_KEYS.contains(key)) continue;
                byKey.put(key, criterion(key, asText(json.getAsJsonObject().get(key))));
            }
        }
        if (byKey.isEmpty()) {
            EditorToastHandler.show(EditorToast.Level.INFO, title,
                    Component.translatable("editor.historystages.nbt.from_item.nothing", entityName()));
            return;
        }

        SearchableItemPropertyList list = new SearchableItemPropertyList(
                List.copyOf(byKey.values()),
                key -> {
                    NbtCriterion picked = byKey.get(key);
                    if (picked != null && criteria().stream().noneMatch(c -> c.identity().equals(key))) {
                        criteria().add(picked);
                    }
                    refresh();
                },
                () -> criteria().stream().map(NbtCriterion::identity).collect(Collectors.toList()));
        pickerOverlay = list;
        list.show(this.width / 2, this.height / 2, this.width);
    }

    private Entity findSourceEntity() {
        if (this.minecraft.level == null || this.minecraft.player == null) return null;
        EntityType<?> type = entityType();
        if (type == null) return null;

        Entity aimed = this.minecraft.crosshairPickEntity;
        if (aimed != null && aimed.getType() == type) return aimed;

        Entity nearest = null;
        double best = (double) SEARCH_RADIUS * SEARCH_RADIUS;
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity.getType() != type) continue;
            double dist = entity.distanceToSqr(this.minecraft.player);
            if (dist < best) {
                best = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private EntityType<?> entityType() {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        return rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
    }

    private String entityName() {
        EntityType<?> type = entityType();
        return type != null ? type.getDescription().getString() : entityId;
    }

    // ==========================================
    // Layout helpers
    // ==========================================

    private int scrollbarX() {
        return this.width - PADDING - Scrollbar.WIDTH;
    }

    private int contentWidth() {
        return this.width - PADDING * 2 - Scrollbar.WIDTH - 4;
    }

    private int listTop() {
        return HEADER_HEIGHT;
    }

    private int listBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    private int jsonPanelWidth() {
        return Math.min(380, this.width - PADDING * 2);
    }

    private int jsonPanelHeight() {
        return Math.min(this.height - 80, jsonLines.size() * 10 + 22);
    }

    private String chipLabel(int index) {
        return Component.translatable("editor.historystages.entity_nbt.variant", index + 1).getString();
    }

    private int chipWidth(int index) {
        return this.font.width(chipLabel(index)) + 10;
    }

    /** Left edge of each variant chip, right-aligned in the header. Only drawn with two or more. */
    private int[] chipXs() {
        int[] xs = new int[variants.size()];
        int x = this.width - PADDING;
        for (int i = variants.size() - 1; i >= 0; i--) {
            x -= chipWidth(i);
            xs[i] = x;
            x -= CHIP_GAP;
        }
        return xs;
    }

    private static final int CHIP_Y = 13;

    // ==========================================
    // Rendering
    // ==========================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Own background in render(); skips the menu blur.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        renderHeader(g, mouseX, mouseY);

        int listTop = listTop();
        int listBottom = listBottom();
        g.enableScissor(0, listTop, this.width, listBottom);
        if (criteria().isEmpty()) {
            renderEmptyState(g, listTop, listBottom);
        } else {
            int y = listTop - Math.round(smoothScroll.value());
            for (CriterionCard.Built built : cards) {
                if (y + built.height() > listTop && y < listBottom) {
                    card.render(g, built, PADDING, y, mouseX, mouseY);
                }
                y += built.height() + CARD_GAP;
            }
        }
        g.disableScissor();

        scrollbar.render(g, scrollbarX(), listTop, listBottom,
                smoothScroll.value(), maxScroll, mouseX, mouseY);

        if (dirty) renderUnsavedMarker(g);

        super.render(g, mouseX, mouseY, partialTick);

        if (chipMenu.isVisible()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            chipMenu.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        }

        syncPickerState();
        if (pickerOverlay != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, this.width, this.height, 0x80000000);
            pickerOverlay.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        } else if (showJson) {
            renderJsonOverlay(g);
        }
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack icon = EntityPreviewRenderer.iconFor(entityId);
        if (!icon.isEmpty()) g.renderItem(icon, PADDING, 12);
        g.drawString(this.font, entityName(), PADDING + 22, 14, 0xFFFFFF);
        g.drawString(this.font, entityId, PADDING + 22, 28, 0x888888);

        if (variants.size() > 1) {
            int[] xs = chipXs();
            String label = Component.translatable("editor.historystages.entity_nbt.variants_label").getString();
            g.drawString(this.font, label, xs[0] - 6 - this.font.width(label), CHIP_Y + 3, 0x888888);
            for (int i = 0; i < variants.size(); i++) {
                int w = chipWidth(i);
                boolean selected = i == active;
                boolean hovered = !selected && mouseX >= xs[i] && mouseX < xs[i] + w
                        && mouseY >= CHIP_Y && mouseY < CHIP_Y + CHIP_H;
                int border = selected ? 0xFFFFCC00 : hovered ? 0xFF6A6A6A : 0xFF3A3A3A;
                g.fill(xs[i], CHIP_Y, xs[i] + w, CHIP_Y + CHIP_H, border);
                g.fill(xs[i] + 1, CHIP_Y + 1, xs[i] + w - 1, CHIP_Y + CHIP_H - 1,
                        selected ? 0xFF2A2410 : 0xFF181818);
                g.drawString(this.font, chipLabel(i), xs[i] + 5, CHIP_Y + 3,
                        selected ? 0xFFCC00 : 0xAAAAAA);
            }
        }

        int size = criteria().size();
        String count = size == 0
                ? Component.translatable("editor.historystages.nbt.count.none").getString()
                : size == 1
                        ? Component.translatable("editor.historystages.nbt.count.one").getString()
                        : Component.translatable("editor.historystages.nbt.count", size).getString();
        g.drawString(this.font, count, this.width - PADDING - this.font.width(count),
                TOOLBAR_Y + (TOOLBAR_H - 8) / 2, 0x888888);

        g.fill(PADDING, HEADER_HEIGHT - 6, this.width - PADDING, HEADER_HEIGHT - 5, 0x40FFCC00);
    }

    private void renderEmptyState(GuiGraphics g, int listTop, int listBottom) {
        String title = Component.translatable(variants.size() > 1
                ? "editor.historystages.entity_nbt.empty.variant"
                : "editor.historystages.entity_nbt.empty.title").getString();
        String hint = Component.translatable("editor.historystages.entity_nbt.empty.hint").getString();
        int centerY = (listTop + listBottom) / 2;
        g.drawString(this.font, title, (this.width - this.font.width(title)) / 2, centerY - 10, 0x7A7A7A);
        g.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2, centerY + 4, 0x5F5F5F);
    }

    private void renderJsonOverlay(GuiGraphics g) {
        int panelW = jsonPanelWidth();
        int panelH = jsonPanelHeight();
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(0, 0, this.width, this.height, 0x80000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF2F2F2F);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0xFF0D0D0D);

        String title = Component.translatable("editor.historystages.nbt.show_json").getString();
        g.drawString(this.font, title, panelX + 8, panelY + 6, 0xFFCC00);
        g.fill(panelX + 8, panelY + 17, panelX + panelW - 8, panelY + 18, 0x40FFCC00);

        int textTop = panelY + 22;
        int textBottom = panelY + panelH - 4;
        g.enableScissor(panelX + 1, textTop, panelX + panelW - 1, textBottom);
        int y = textTop - jsonScroll;
        for (FormattedCharSequence line : jsonLines) {
            g.drawString(this.font, line, panelX + 8, y, 0xCCCCCC);
            y += 10;
        }
        g.disableScissor();
        g.pose().popPose();
    }

    private int maxJsonScroll() {
        return Math.max(0, jsonLines.size() * 10 - (jsonPanelHeight() - 26));
    }

    /** Same breathing marker as the item editor, on the same cycle. */
    private void renderUnsavedMarker(GuiGraphics g) {
        float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                / Timing.BREATHE_PERIOD_MS;
        int alpha = (int) ((0.35f + 0.45f * Ease.breathe(phase)) * 255);
        String label = Component.translatable("editor.historystages.unsaved").getString();
        int labelX = this.width - PADDING - 100 - 8 - this.font.width(label);
        int y = this.height - 24;
        g.fill(labelX - 10, y + 1, labelX - 4, y + 7, (alpha << 24) | 0xFFCC00);
        g.drawString(this.font, label, labelX, y, 0xFFCC00);
    }

    // ==========================================
    // Input
    // ==========================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chipMenu.isVisible()) {
            chipMenu.mouseClicked(mouseX, mouseY, button);
            chipMenu.hide();
            return true;
        }
        syncPickerState();
        if (pickerOverlay != null) {
            pickerOverlay.mouseClicked(mouseX, mouseY);
            syncPickerState();
            return true;
        }
        if (showJson) {
            showJson = false;
            this.rebuildWidgets();
            return true;
        }

        if (variants.size() > 1 && mouseY >= CHIP_Y && mouseY < CHIP_Y + CHIP_H) {
            int[] xs = chipXs();
            for (int i = 0; i < xs.length; i++) {
                if (mouseX >= xs[i] && mouseX < xs[i] + chipWidth(i)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        int index = i;
                        chipMenu = new ContextMenu();
                        chipMenu.addEntry(Component.translatable(
                                "editor.historystages.entity_nbt.variant.remove").getString(),
                                () -> removeVariant(index));
                        chipMenu.show((int) mouseX, (int) mouseY, this.font);
                        return true;
                    }
                    if (i != active) {
                        playClick();
                        switchTo(i);
                    }
                    return true;
                }
            }
        }

        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            scrollOffset = scrollbar.scrollFor(mouseY);
            return true;
        }

        if (mouseY >= listTop() && mouseY < listBottom()) {
            int y = listTop() - Math.round(smoothScroll.value());
            for (int i = 0; i < cards.size(); i++) {
                CriterionCard.Built built = cards.get(i);
                CriterionCard.Hit hit = card.hitTest(built, PADDING, y, mouseX, mouseY);
                if (hit != null) {
                    playClick();
                    handleHit(i, hit);
                    return true;
                }
                y += built.height() + CARD_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleHit(int index, CriterionCard.Hit hit) {
        CustomDataCriterion criterion = (CustomDataCriterion) criteria().get(index);
        if (hit instanceof CriterionCard.Hit.Remove) {
            criteria().remove(index);
            refresh();
        } else if (hit instanceof CriterionCard.Hit.EditKey) {
            openInput(Component.translatable("editor.historystages.nbt.custom.key_label").getString(),
                    criterion.key, List.of(), value -> {
                        criterion.key = value;
                        EntityNbtPresets.Preset preset = EntityNbtPresets.byKey(value);
                        criterion.presetName = preset == null ? null : preset.nameKey();
                        criterion.presetDescription = preset == null ? null : preset.descriptionKey();
                    });
        } else if (hit instanceof CriterionCard.Hit.EditValue) {
            openInput(Component.translatable("editor.historystages.nbt.custom.value_label").getString(),
                    criterion.valueText, valueSuggestions(criterion.key),
                    value -> criterion.valueText = stripChoiceLabel(value));
        }
    }

    private List<String> valueSuggestions(String key) {
        List<String> out = new ArrayList<>();
        for (Choice choice : choicesFor(key)) out.add(choice.display());
        return out;
    }

    private void openInput(String title, String value, List<String> suggestions, Consumer<String> onDone) {
        this.minecraft.setScreen(new NbtItemEditScreen.SuggestingInputScreen(this, title, value, suggestions, v -> {
            onDone.accept(v);
            refresh();
        }));
    }

    private void playClick() {
        this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }

    private void syncPickerState() {
        if (pickerOverlay != null && !pickerOverlay.isVisible()) pickerOverlay = null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncPickerState();
        if (pickerOverlay != null) return pickerOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (showJson) {
            jsonScroll = (int) Math.max(0, Math.min(maxJsonScroll(), jsonScroll - scrollY * 12));
            return true;
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 12));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.mouseDragged(mouseX, mouseY)) return true;
        if (scrollbar.isDragging()) {
            scrollOffset = scrollbar.scrollFor(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.mouseReleased()) return true;
        if (scrollbar.isDragging()) {
            scrollbar.mouseReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (chipMenu.isVisible() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            chipMenu.hide();
            return true;
        }
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.keyPressed(keyCode)) {
            syncPickerState();
            return true;
        }
        if (showJson && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            showJson = false;
            this.rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    /** Escape goes back to the stage screen, not out of the editor. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ==========================================
    // Key/value dialog
    // ==========================================

    /** The item editor's custom-key dialog with entity wording: its hint names an item key. */
    static class KeyValueDialog extends AbstractInputScreen {
        private final Screen parent;
        private final java.util.function.BiConsumer<String, String> onDone;

        KeyValueDialog(Screen parent, java.util.function.BiConsumer<String, String> onDone) {
            super(parent, Component.translatable("editor.historystages.entity_nbt.add_heading"));
            this.parent = parent;
            this.onDone = onDone;
        }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(
                    InputField.text("key")
                            .label(Component.translatable("editor.historystages.nbt.custom.key_label"))
                            .hint(Component.translatable("editor.historystages.entity_nbt.key_hint"))
                            .maxLength(128)
                            .validator(v -> v.isBlank()
                                    ? Component.translatable("editor.historystages.input.empty") : null),
                    InputField.text("value")
                            .label(Component.translatable("editor.historystages.nbt.custom.value_label"))
                            .hint(Component.translatable("editor.historystages.entity_nbt.value_hint"))
                            .maxLength(2048)
                            .validator(v -> v.isBlank()
                                    ? Component.translatable("editor.historystages.input.empty") : null));
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("key").trim(), values.getString("value"));
            this.minecraft.setScreen(parent);
        }
    }
}
