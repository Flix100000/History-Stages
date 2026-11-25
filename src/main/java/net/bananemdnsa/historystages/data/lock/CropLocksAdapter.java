package net.bananemdnsa.historystages.data.lock;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code "crops"} as a flat array (hand-written packs) or as
 * {@code {"crops": [...], "mod_linked": [...]}}; always writes the object form.
 *
 * <p>An empty block is written as null, which Gson then leaves out of the file: most stages never
 * lock a plant and should not carry an empty {@code crops} object around.
 */
public class CropLocksAdapter extends TypeAdapter<CropLocks> {

    @Override
    public void write(JsonWriter out, CropLocks value) throws IOException {
        if (value == null || value.isEmpty()) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("crops");
        writeList(out, value.getCrops());
        if (!value.getModLinked().isEmpty()) {
            out.name("mod_linked");
            writeList(out, value.getModLinked());
        }
        out.endObject();
    }

    @Override
    public CropLocks read(JsonReader in) throws IOException {
        CropLocks result = new CropLocks();

        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return result;
        }

        if (in.peek() == JsonToken.BEGIN_ARRAY) {
            result.setCrops(readList(in));
            return result;
        }

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "crops" -> result.setCrops(readList(in));
                case "mod_linked" -> result.setModLinked(readList(in));
                default -> in.skipValue();
            }
        }
        in.endObject();
        return result;
    }

    private static void writeList(JsonWriter out, List<String> values) throws IOException {
        out.beginArray();
        for (String s : values) out.value(s);
        out.endArray();
    }

    private static List<String> readList(JsonReader in) throws IOException {
        List<String> list = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) list.add(in.nextString());
        in.endArray();
        return list;
    }
}
