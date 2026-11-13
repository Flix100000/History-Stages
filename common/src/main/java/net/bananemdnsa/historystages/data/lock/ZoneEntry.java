package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * One zone: a named area in one dimension, made of one or more shapes, with its own rules.
 *
 * <p>The dimension is mandatory. Coordinates alone are ambiguous — 100/64/100 exists in the
 * Overworld, the Nether and the End — and a zone meant for a Nether fortress would otherwise also
 * fire in the middle of an Overworld village. Two worlds means two zones, on purpose: the editor
 * then shows that there are two.
 */
public class ZoneEntry {

    private String name = "";
    private String dimension = "minecraft:overworld";
    private List<ZoneShape> shapes = new ArrayList<>();
    private ZoneRules rules = new ZoneRules();

    /**
     * The JSON this zone was read from, kept so that writing can amend it rather than build a new
     * object — which is what lets a key this class has never heard of survive a round trip
     * through the editor. Rebuilding instead of amending has silently dropped unknown data twice
     * in this repo.
     *
     * <p>{@code transient} so Gson never tries to serialize the carrier itself.
     */
    private transient JsonObject rawJson;

    public String getName() { return name != null ? name : ""; }
    public String getDimension() { return dimension != null ? dimension : ""; }
    public List<ZoneShape> getShapes() { return shapes != null ? shapes : new ArrayList<>(); }

    /**
     * Installs a fresh rule set when there is none, rather than handing back a throwaway.
     *
     * <p>Returning {@code new ZoneRules()} without storing it would make every edit through this
     * getter vanish.
     */
    public ZoneRules getRules() {
        if (rules == null) rules = new ZoneRules();
        return rules;
    }

    public JsonObject getRawJson() { return rawJson; }

    public void setName(String name) { this.name = name != null ? name : ""; }

    public void setDimension(String dimension) {
        this.dimension = dimension != null ? dimension : "";
    }

    public void setShapes(List<ZoneShape> shapes) {
        this.shapes = shapes != null ? new ArrayList<>(shapes) : new ArrayList<>();
    }

    public void setRules(ZoneRules rules) {
        this.rules = rules != null ? rules : new ZoneRules();
    }

    public void setRawJson(JsonObject rawJson) { this.rawJson = rawJson; }

    /** A zone nobody has marked out yet. Legal, shown as incomplete, applies to nobody. */
    public boolean hasShapes() {
        return shapes != null && !shapes.isEmpty();
    }

    /**
     * A zone the caller may edit without touching this one.
     *
     * <p>{@link ZoneShape} is a record of primitives, so the list may be shared by value; the
     * rules block is mutable and gets rebuilt. The raw JSON is deep-copied so an addon's key
     * survives into the copy without the two zones sharing one object.
     */
    public ZoneEntry copy() {
        ZoneEntry copy = new ZoneEntry();
        copy.setName(getName());
        copy.setDimension(getDimension());
        copy.setShapes(getShapes());
        copy.setRules(getRules().copy());
        copy.setRawJson(rawJson != null ? rawJson.deepCopy() : null);
        return copy;
    }
}
