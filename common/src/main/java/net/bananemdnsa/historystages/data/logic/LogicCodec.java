package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.bananemdnsa.historystages.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes the {@code logic} array of a stage file.
 *
 * <p>The contract that matters: {@code write(read(x))} equals {@code x} for anything this version
 * did not change, including block types and condition nodes it has never heard of. A pack edited
 * in an older editor must not lose rules a newer version wrote.
 */
public final class LogicCodec {

    private LogicCodec() {}

    public static List<LogicBlock> read(@Nullable JsonArray array) {
        if (array == null || array.isEmpty()) return List.of();
        List<LogicBlock> blocks = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            blocks.add(readBlock(element));
        }
        return List.copyOf(blocks);
    }

    public static JsonArray write(List<LogicBlock> blocks) {
        JsonArray out = new JsonArray();
        for (LogicBlock block : blocks) {
            out.add(writeBlock(block));
        }
        return out;
    }

    private static LogicBlock readBlock(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new LogicBlock(null, new Condition.Unknown(element), element);
        }
        JsonObject obj = element.getAsJsonObject();
        String type = obj.has("type") && obj.get("type").isJsonPrimitive()
                ? obj.get("type").getAsString() : null;
        Condition condition = obj.has("condition")
                ? readCondition(obj.get("condition"))
                : new Condition.Unknown(null);
        return new LogicBlock(type, condition, element);
    }

    private static JsonElement writeBlock(LogicBlock block) {
        if (block.raw() != null && !block.raw().isJsonObject()) return block.raw().deepCopy();
        JsonObject obj = block.raw() != null ? block.raw().getAsJsonObject().deepCopy() : new JsonObject();
        if (block.type() != null) obj.addProperty("type", block.type());
        // An unknown condition that had no element at all stays absent, as it was read.
        if (!(block.condition() instanceof Condition.Unknown u && u.raw() == null)) {
            obj.add("condition", writeCondition(block.condition()));
        }
        return obj;
    }

    public static Condition readCondition(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return new Condition.Unknown(element);
        JsonObject obj = element.getAsJsonObject();
        int size = obj.size();

        if (size == 1 && obj.has("all") && obj.get("all").isJsonArray()) {
            return new Condition.All(readChildren(obj.getAsJsonArray("all")));
        }
        if (size == 1 && obj.has("any") && obj.get("any").isJsonArray()) {
            return new Condition.Any(readChildren(obj.getAsJsonArray("any")));
        }
        if (size == 1 && obj.has("not")) {
            return new Condition.Not(readCondition(obj.get("not")));
        }
        if (obj.has("unlocked") && isString(obj.get("unlocked"))
                && (size == 1 || (size == 2 && obj.has("scope")))) {
            StageScope scope = null;
            if (obj.has("scope")) {
                scope = parseScope(obj.get("scope"));
                if (scope == null) return new Condition.Unknown(element);
            }
            return new Condition.Unlocked(obj.get("unlocked").getAsString(), scope);
        }
        // Anything else, including a known key with extra fields beside it, is kept whole: reading
        // part of it would drop the rest on the next save.
        return new Condition.Unknown(element);
    }

    public static JsonElement writeCondition(Condition condition) {
        return switch (condition) {
            case Condition.All all -> wrap("all", writeChildren(all.children()));
            case Condition.Any any -> wrap("any", writeChildren(any.children()));
            case Condition.Not not -> wrap("not", writeCondition(not.child()));
            case Condition.Unlocked u -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("unlocked", u.stageId());
                if (u.scope() != null) obj.addProperty("scope", u.scope().name().toLowerCase(Locale.ROOT));
                yield obj;
            }
            case Condition.Unknown u -> u.raw() == null ? new JsonObject() : u.raw().deepCopy();
        };
    }

    private static List<Condition> readChildren(JsonArray array) {
        List<Condition> children = new ArrayList<>(array.size());
        for (JsonElement child : array) children.add(readCondition(child));
        return List.copyOf(children);
    }

    private static JsonArray writeChildren(List<Condition> children) {
        JsonArray out = new JsonArray();
        for (Condition child : children) out.add(writeCondition(child));
        return out;
    }

    private static JsonObject wrap(String key, JsonElement value) {
        JsonObject obj = new JsonObject();
        obj.add(key, value);
        return obj;
    }

    private static boolean isString(JsonElement e) {
        return e.isJsonPrimitive() && ((JsonPrimitive) e).isString();
    }

    @Nullable
    private static StageScope parseScope(JsonElement e) {
        if (!isString(e)) return null;
        return switch (e.getAsString().toLowerCase(Locale.ROOT)) {
            case "global" -> StageScope.GLOBAL;
            case "individual" -> StageScope.INDIVIDUAL;
            default -> null;
        };
    }
}
