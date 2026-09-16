package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Answers "what is this property supposed to look like" by reading a real value off an item the
 * player is carrying.
 *
 * <p>On 1.21 this probes a component's codec when no item carries it. On 1.20.1 an item's tag is
 * plain NBT with no codec behind it, so an item that has the property is the only source, and the
 * probing half has nothing to ask.
 */
public final class ComponentShapes {

    private ComponentShapes() {}

    /** The value at {@code path} on the first inventory stack that has one, as JSON; null when none does. */
    public static String exampleFor(String path) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        for (ItemStack stack : mc.player.getInventory().items) {
            JsonElement value = valueAt(stack, path);
            if (value != null) return value.toString();
        }
        return null;
    }

    /** No codec to ask on this version. */
    public static String skeletonFor(String path) {
        return null;
    }

    /** No codec to ask on this version. */
    public static boolean acceptsEmptyObject(String path) {
        return false;
    }

    /** No codec to ask on this version. */
    public static String requirementHint(String path) {
        return null;
    }

    /** Whether the stack's tag holds anything at {@code path}. */
    public static boolean has(ItemStack stack, String path) {
        return valueAt(stack, path) != null;
    }

    private static JsonElement valueAt(ItemStack stack, String path) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        Tag current = tag;
        for (String part : path.split("\\.")) {
            if (!(current instanceof CompoundTag compound) || !compound.contains(part)) return null;
            current = compound.get(part);
        }
        JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, current);
        return json instanceof JsonObject && json.getAsJsonObject().size() == 0 ? null : json;
    }
}
