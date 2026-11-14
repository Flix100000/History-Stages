package net.bananemdnsa.historystages.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.bananemdnsa.historystages.network.clientbound.SyncRecipeListPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import dev.architectury.networking.NetworkManager;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The recipe list for the editor on a dedicated server.
 *
 * <p>Up to 1.21.1 every client got all recipes at login, and the editor's recipe pickers read
 * them from the client's recipe manager. Since 1.21.2 a client gets none. Only the editor needs
 * the full list, so it goes only to players allowed to open the editor (the same operator check
 * the editor command has), when they open it and again after a datapack reload changed it.
 *
 * <p>Each recipe travels as its data-pack JSON (the recipe codec every recipe has, unlike the
 * network codec, which modded recipes no longer need since nothing syncs them), gzipped like the
 * stage sync. A recipe that cannot be written is left out rather than failing the list.
 */
public final class RecipeListSync {

    /** Generous: a large pack's list is tens of megabytes of JSON, a tenth of that compressed. */
    public static final int MAX_JSON_CHARS = 256 * 1024 * 1024;
    public static final int MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;

    private static final Set<UUID> EDITORS = ConcurrentHashMap.newKeySet();
    private static RecipeManager encodedFor;
    private static byte[] encoded;
    private static RecipeManager seen;
    private static int tick;

    private RecipeListSync() {}

    public static boolean mayOpenEditor(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Sends the list to an editor player, who then also gets it again after each reload. */
    public static void sendTo(ServerPlayer player) {
        if (!mayOpenEditor(player)) return;
        byte[] payload = payload(player.level().getServer());
        if (payload == null) return;
        EDITORS.add(player.getUUID());
        NetworkManager.sendToPlayer(player, new SyncRecipeListPacket(payload));
    }

    /** Notices a datapack reload by the recipe manager being replaced, and resends. */
    public static void onServerTick(MinecraftServer server) {
        if (++tick % 20 != 0) return;
        RecipeManager current = server.getRecipeManager();
        if (seen == null) {
            seen = current;
            return;
        }
        if (current == seen) return;
        seen = current;
        resendToEditors(server);
    }

    public static void resendToEditors(MinecraftServer server) {
        for (UUID id : Set.copyOf(EDITORS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                EDITORS.remove(id);
            } else {
                sendTo(player);
            }
        }
    }

    public static void clear() {
        EDITORS.clear();
        encodedFor = null;
        encoded = null;
        seen = null;
    }

    private static synchronized byte[] payload(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == encodedFor && encoded != null) return encoded;
        try {
            // The unfiltered snapshot when there is one: the editor lists locked recipes too.
            var all = net.bananemdnsa.historystages.util.AllRecipesCache.get();
            encoded = GzipJson.compress(toJson(server, all.isEmpty() ? manager.getRecipes() : all));
            encodedFor = manager;
            return encoded;
        } catch (IOException e) {
            DebugLogger.warn("Editor Recipes", "could not compress the recipe list: " + e);
            return null;
        }
    }

    public static String toJson(MinecraftServer server, Iterable<RecipeHolder<?>> recipes) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        JsonArray out = new JsonArray();
        int skipped = 0;
        for (RecipeHolder<?> holder : recipes) {
            try {
                JsonElement recipe = Recipe.CODEC.encodeStart(ops, holder.value()).getOrThrow();
                JsonObject entry = new JsonObject();
                entry.addProperty("id", holder.id().identifier().toString());
                entry.add("recipe", recipe);
                out.add(entry);
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            DebugLogger.warn("Editor Recipes", skipped + " recipes could not be written for the editor and are left out");
        }
        return out.toString();
    }

    /** The other half of {@link #toJson}; recipes that do not read on this side are counted, not thrown. */
    public static java.util.List<RecipeHolder<?>> fromJson(String json, net.minecraft.core.HolderLookup.Provider registries,
                                                         int[] skipped) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        java.util.List<RecipeHolder<?>> out = new java.util.ArrayList<>();
        for (JsonElement element : com.google.gson.JsonParser.parseString(json).getAsJsonArray()) {
            try {
                JsonObject entry = element.getAsJsonObject();
                Recipe<?> recipe = Recipe.CODEC.parse(ops, entry.get("recipe")).getOrThrow();
                net.minecraft.resources.ResourceKey<Recipe<?>> key = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.RECIPE,
                        net.minecraft.resources.Identifier.parse(entry.get("id").getAsString()));
                out.add(new RecipeHolder<>(key, recipe));
            } catch (RuntimeException e) {
                skipped[0]++;
            }
        }
        return out;
    }

    /** Whether this player gets the list again after a reload. For the GameTests. */
    public static boolean isTracked(UUID player) {
        return EDITORS.contains(player);
    }
}
