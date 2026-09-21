package net.bananemdnsa.historystages.data.lock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Reads and writes the {@code zones} list.
 *
 * <pre>
 * "zones": [
 *   { "name": "Krater", "dimension": "minecraft:overworld",
 *     "shapes": [ { "type": "cube", "from": [0,0,0], "to": [10,10,10], "full_height": true } ],
 *     "rules": { "damage": { "enabled": true, "amount": 1.0, "interval": 20 } } }
 * ]
 * </pre>
 *
 * <p>Every zone keeps the JSON object it was read from. Writing amends that object rather than
 * building a new one, which is what makes an addon's own key survive a round trip through the
 * editor. Building fresh has silently dropped unknown data twice in this repo.
 *
 * <p>Malformed input is skipped, never fatal. Stage files are hand-written often enough that the
 * loader naming the stage beats an exception thrown from inside Gson — a missing corner drops the
 * one shape, a stray array element drops that element, and the rest of the file still loads.
 */
public class ZoneEntryListAdapter extends TypeAdapter<List<ZoneEntry>> {

    /** Rules and effects are ordinary POJOs; only the outer list and the shapes need help. */
    private static final Gson INNER = new Gson();

    @Override
    public void write(JsonWriter out, List<ZoneEntry> zones) throws IOException {
        if (zones == null) {
            out.nullValue();
            return;
        }
        JsonArray array = new JsonArray();
        for (ZoneEntry zone : zones) {
            if (zone == null) continue;
            array.add(toJson(zone));
        }
        INNER.toJson(array, out);
    }

    private static JsonObject toJson(ZoneEntry zone) {
        JsonObject object = zone.getRawJson() != null
                ? zone.getRawJson().deepCopy()
                : new JsonObject();
        object.addProperty("name", zone.getName());
        object.addProperty("dimension", zone.getDimension());

        JsonArray shapes = new JsonArray();
        for (ZoneShape shape : zone.getShapes()) {
            shapes.add(shapeToJson(shape));
        }
        object.add("shapes", shapes);
        object.add("rules", INNER.toJsonTree(zone.getRules()));
        return object;
    }

    private static JsonObject shapeToJson(ZoneShape shape) {
        JsonObject object = new JsonObject();
        object.addProperty("type", shape.type().serializedName());
        switch (shape.type()) {
            case CUBE -> {
                object.add("from", writeTriple(shape.fromX(), shape.fromY(), shape.fromZ()));
                object.add("to", writeTriple(shape.toX(), shape.toY(), shape.toZ()));
                object.addProperty("full_height", shape.fullHeight());
            }
            case SPHERE -> {
                object.add("center", writeTriple(shape.fromX(), shape.fromY(), shape.fromZ()));
                object.addProperty("radius", shape.radius());
            }
            case CYLINDER -> {
                object.add("center", writeTriple(shape.fromX(), shape.fromY(), shape.fromZ()));
                object.addProperty("radius", shape.radius());
                object.addProperty("height", shape.height());
                object.addProperty("full_height", shape.fullHeight());
            }
        }
        return object;
    }

    private static JsonArray writeTriple(int a, int b, int c) {
        JsonArray array = new JsonArray();
        array.add(a);
        array.add(b);
        array.add(c);
        return array;
    }

    @Override
    public List<ZoneEntry> read(JsonReader in) throws IOException {
        List<ZoneEntry> zones = new ArrayList<>();
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return zones;
        }
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() != JsonToken.BEGIN_OBJECT) {
                in.skipValue();
                continue;
            }
            zones.add(fromJson(JsonParser.parseReader(in).getAsJsonObject()));
        }
        in.endArray();
        return zones;
    }

    private static ZoneEntry fromJson(JsonObject object) {
        ZoneEntry zone = new ZoneEntry();
        zone.setRawJson(object);
        if (object.has("name")) zone.setName(object.get("name").getAsString());
        if (object.has("dimension")) zone.setDimension(object.get("dimension").getAsString());

        List<ZoneShape> shapes = new ArrayList<>();
        if (object.has("shapes") && object.get("shapes").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("shapes")) {
                if (!element.isJsonObject()) continue;
                ZoneShape shape = shapeFromJson(element.getAsJsonObject());
                if (shape != null) shapes.add(shape);
            }
        }
        zone.setShapes(shapes);

        if (object.has("rules") && object.get("rules").isJsonObject()) {
            zone.setRules(INNER.fromJson(object.getAsJsonObject("rules"), ZoneRules.class));
        }
        return zone;
    }

    private static ZoneShape shapeFromJson(JsonObject object) {
        ZoneShapeType type = ZoneShapeType.fromSerializedName(
                object.has("type") ? object.get("type").getAsString() : null);
        boolean fullHeight = object.has("full_height") && object.get("full_height").getAsBoolean();
        int radius = object.has("radius") ? object.get("radius").getAsInt() : 0;
        int height = object.has("height") ? object.get("height").getAsInt() : 0;

        int[] first = readTriple(object, type == ZoneShapeType.CUBE ? "from" : "center");
        if (first == null) return null;

        return switch (type) {
            case CUBE -> {
                int[] second = readTriple(object, "to");
                // Half a cube describes nothing. Dropping it beats guessing the other corner.
                yield second == null ? null
                        : ZoneShape.cube(first[0], first[1], first[2],
                                         second[0], second[1], second[2], fullHeight);
            }
            case SPHERE -> ZoneShape.sphere(first[0], first[1], first[2], radius);
            case CYLINDER -> ZoneShape.cylinder(first[0], first[1], first[2],
                                                radius, height, fullHeight);
        };
    }

    private static int[] readTriple(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) return null;
        JsonArray array = object.getAsJsonArray(key);
        if (array.size() < 3) return null;
        return new int[]{
                array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt() };
    }
}
