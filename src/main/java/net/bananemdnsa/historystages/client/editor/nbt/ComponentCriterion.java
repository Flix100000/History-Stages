package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * A known item property, named by its path in the item's tag ({@code RepairCost},
 * {@code display.Name}). On 1.20.1 that tag is what {@code NbtMatcher} reads, so the path is written
 * as nested keys and matches as it stands.
 */
public final class ComponentCriterion implements NbtCriterion {

    private final String componentId;
    /** Raw JSON as typed. Parsed on save; unparseable text keeps the criterion out of the result. */
    public String valueJson;
    /** Friendly label when the criterion came from {@link NbtPresets}, else null. */
    public final String presetName;
    /** How the value is edited. Decides what the card shows and which dialog opens. */
    public final ValueKind valueKind;

    public ComponentCriterion(String componentId, String valueJson, String presetName) {
        this(componentId, valueJson, presetName, NbtPresets.valueKindFor(componentId));
    }

    public ComponentCriterion(String componentId, String valueJson, String presetName,
                              ValueKind valueKind) {
        this.componentId = componentId;
        this.valueJson = valueJson == null ? "" : valueJson;
        this.presetName = presetName;
        this.valueKind = valueKind;
    }

    public String componentId() {
        return componentId;
    }

    /** The name and lore live under {@code display} as serialised text components, see {@link NbtText}. */
    public boolean isTextComponent() {
        return componentId.startsWith("display.");
    }

    /**
     * The value as a person should see and type it: a text component's name without its JSON
     * quotes, a number as digits, raw JSON unchanged.
     */
    public String displayValue() {
        if (valueKind != ValueKind.TEXT) return valueJson;
        try {
            JsonElement parsed = JsonParser.parseString(valueJson);
            if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
                return isTextComponent() ? NbtText.unwrap(parsed.getAsString()) : parsed.getAsString();
            }
        } catch (Exception ignored) {
            // half-typed or hand-edited JSON — show it as it stands
        }
        return valueJson;
    }

    /**
     * Takes what was typed into the value field and stores the JSON the matcher needs.
     *
     * <p>The number case keeps a {@code 1-4} range as a string on purpose: that is the only form
     * {@code NbtMatcher.matchesElement} reads as a range, while a plain number has to stay a number
     * to compare against a {@code NumericTag} at all.
     */
    public void setFromDisplay(String typed) {
        String trimmed = typed == null ? "" : typed.trim();
        switch (valueKind) {
            case TEXT -> valueJson = trimmed.isEmpty() ? ""
                    : new JsonPrimitive(isTextComponent() ? NbtText.wrap(trimmed) : trimmed).toString();
            case NUMBER -> {
                if (trimmed.matches("\\d+")) {
                    valueJson = trimmed;
                } else if (trimmed.matches("\\d+-\\d+")) {
                    valueJson = new JsonPrimitive(trimmed).toString();
                } else {
                    valueJson = "";
                }
            }
            default -> valueJson = trimmed;
        }
    }

    @Override
    public CriterionKind kind() {
        return CriterionKind.COMPONENT;
    }

    @Override
    public String identity() {
        return componentId;
    }

    @Override
    public boolean isEmpty() {
        return valueJson.isBlank();
    }
}
