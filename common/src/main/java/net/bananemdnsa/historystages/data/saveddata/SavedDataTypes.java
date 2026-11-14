package net.bananemdnsa.historystages.data.saveddata;

import com.mojang.serialization.Codec;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The 26.2 shape of HS's world data: a codec in place of the load/save overrides, and an
 * identifier that doubles as the file path.
 *
 * <p>The file layout inside stays what 1.21.1 wrote ({@code data} wrapper, same keys). Only the
 * location moves: 1.21.1 kept {@code <world>/data/historystages_<name>.dat}, 26.2 resolves the
 * identifier to {@code <world>/data/historystages/<name>.dat}. Vanilla's own file fixes only move
 * vanilla files, so {@link #get} moves an old file over once before the storage first reads it.
 */
public final class SavedDataTypes {

    private static final Logger LOGGER = LoggerFactory.getLogger(SavedDataTypes.class);
    private static final String LEGACY_PREFIX = HistoryStages.MOD_ID + "_";
    // get() runs on hot paths; look for the old file once per server, not on every call.
    private static final Map<MinecraftServer, Set<Identifier>> CHECKED = Collections.synchronizedMap(new WeakHashMap<>());

    private SavedDataTypes() {}

    @FunctionalInterface
    public interface Loader<T> {
        T load(CompoundTag tag, HolderLookup.Provider registries);
    }

    /**
     * @param legacyName the 1.21.1 data name, e.g. {@code historystages_individual}
     */
    public static <T extends SavedData> SavedDataType<T> of(String legacyName, Supplier<T> constructor,
                                                           Loader<T> load,
                                                           BiFunction<T, CompoundTag, CompoundTag> save) {
        // None of HS's data reads registries, so the codec has none to hand over.
        Codec<T> codec = CompoundTag.CODEC.xmap(tag -> load.load(tag, null), data -> save.apply(data, new CompoundTag()));
        // Null data fix type: NeoForge and Fabric API both patch the storage to skip fixing then.
        return new SavedDataType<>(id(legacyName), constructor, codec, null);
    }

    /** The server-wide copy, which 1.21.1 kept on the overworld's storage (the same folder then). */
    public static <T extends SavedData> T get(MinecraftServer server, String legacyName, SavedDataType<T> type) {
        if (CHECKED.computeIfAbsent(server, s -> Collections.synchronizedSet(new HashSet<>())).add(type.id())) {
            migrateLegacyFile(server, legacyName, type.id());
        }
        return server.getDataStorage().computeIfAbsent(type);
    }

    static Identifier id(String legacyName) {
        String path = legacyName.startsWith(LEGACY_PREFIX) ? legacyName.substring(LEGACY_PREFIX.length()) : legacyName;
        return Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, path);
    }

    private static void migrateLegacyFile(MinecraftServer server, String legacyName, Identifier id) {
        migrateLegacyFile(server.getWorldPath(LevelResource.DATA), legacyName, id);
    }

    /** Package-private for the test; moves {@code <dataDir>/<legacyName>.dat} once, if it is there. */
    static void migrateLegacyFile(Path dataDir, String legacyName, Identifier id) {
        Path legacy = dataDir.resolve(legacyName + ".dat");
        if (!Files.exists(legacy)) return;
        Path target = id.withSuffix(".dat").resolveAgainst(dataDir);
        if (Files.exists(target)) {
            // Both there: the new one wins, and the old one is left for whoever wants to look.
            LOGGER.warn("Left {} in place: {} already exists and is the one that is read", legacy, target);
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(legacy, target);
            LOGGER.info("Moved {} to {} (26.2 data layout)", legacy, target);
        } catch (IOException e) {
            LOGGER.error("Could not move {} to {}; it will not be read", legacy, target, e);
        }
    }
}
