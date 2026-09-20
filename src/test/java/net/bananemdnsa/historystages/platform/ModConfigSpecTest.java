package net.bananemdnsa.historystages.platform;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config layer is the one part of the fabric port with no counterpart to copy from, and 348
 * compile errors' worth of code reads through it. These tests cover what the rest of the mod
 * actually asks of it rather than the whole NeoForge surface.
 */
class ModConfigSpecTest {

    enum Mode { STRICT, LOOSE }

    /** Mirrors the shape Config.java uses: final fields filled from a builder body. */
    static final class Holder {
        final ModConfigSpec.BooleanValue flag;
        final ModConfigSpec.IntValue count;
        final ModConfigSpec.DoubleValue ratio;
        final ModConfigSpec.EnumValue<Mode> mode;
        final ModConfigSpec.ConfigValue<String> text;
        final ModConfigSpec.ConfigValue<List<? extends String>> names;

        Holder(ModConfigSpec.Builder builder) {
            builder.comment("The visible section").push("section");
            flag = builder.comment("A switch").define("flag", true);
            count = builder.comment("A number").defineInRange("count", 5, 1, 10);
            ratio = builder.defineInRange("ratio", 0.5, 0.0, 1.0);
            mode = builder.defineEnum("mode", Mode.STRICT);
            text = builder.define("text", "#E61414");
            names = builder.defineList("names", List.of("alpha", "beta"), o -> o instanceof String);
            builder.pop();
        }
    }

    private static Pair<Holder, ModConfigSpec> build() {
        return new ModConfigSpec.Builder().configure(Holder::new);
    }

    @Test
    void afreshFileGetsEveryDefault(@TempDir Path dir) throws IOException {
        Pair<Holder, ModConfigSpec> pair = build();
        Path file = dir.resolve("settings").resolve("test.toml");
        pair.getRight().load(file);

        assertTrue(Files.exists(file), "load() should create the file on first run");
        Holder holder = pair.getLeft();
        assertEquals(true, holder.flag.get());
        assertEquals(5, holder.count.get());
        assertEquals(0.5, holder.ratio.get());
        assertEquals(Mode.STRICT, holder.mode.get());
        assertEquals("#E61414", holder.text.get());
        assertEquals(List.of("alpha", "beta"), holder.names.get());

        String written = Files.readString(file);
        assertTrue(written.contains("[section]"), "the pushed section should be a TOML table:\n" + written);
        assertTrue(written.contains("A switch"), "declared comments should reach the file:\n" + written);
    }

    @Test
    void anEnumTravelsAsItsConstantName(@TempDir Path dir) throws IOException {
        Pair<Holder, ModConfigSpec> pair = build();
        Path file = dir.resolve("test.toml");
        pair.getRight().load(file);

        pair.getLeft().mode.set(Mode.LOOSE);
        pair.getRight().save();

        assertTrue(Files.readString(file).contains("\"LOOSE\""),
                "an enum has to be stored as text, TOML has no enum type:\n" + Files.readString(file));

        // A second spec over the same file has to read it back as the enum, not as a string.
        Pair<Holder, ModConfigSpec> reopened = build();
        reopened.getRight().load(file);
        assertEquals(Mode.LOOSE, reopened.getLeft().mode.get());
    }

    @Test
    void aValueOutsideItsRangeIsReplacedByTheDefault(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test.toml");
        Files.createDirectories(dir);
        Files.writeString(file, "[section]\ncount = 99\nratio = 0.25\n");

        Pair<Holder, ModConfigSpec> pair = build();
        pair.getRight().load(file);

        assertEquals(5, pair.getLeft().count.get(), "99 is outside [1, 10] and must fall back to the default");
        assertEquals(0.25, pair.getLeft().ratio.get(), "0.25 is inside [0.0, 1.0] and must be kept");
        assertTrue(Files.readString(file).contains("count = 5"),
                "the corrected value has to be written back, otherwise the file keeps lying");
    }

    @Test
    void aValueOfTheWrongTypeIsReplacedByTheDefault(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test.toml");
        Files.createDirectories(dir);
        Files.writeString(file, "[section]\nflag = \"yes please\"\n");

        Pair<Holder, ModConfigSpec> pair = build();
        pair.getRight().load(file);

        assertEquals(true, pair.getLeft().flag.get());
    }

    @Test
    void aListSurvivesTheRoundTrip(@TempDir Path dir) {
        Pair<Holder, ModConfigSpec> pair = build();
        Path file = dir.resolve("test.toml");
        pair.getRight().load(file);

        pair.getLeft().names.set(List.of("gamma"));
        pair.getRight().save();

        Pair<Holder, ModConfigSpec> reopened = build();
        reopened.getRight().load(file);
        assertEquals(List.of("gamma"), reopened.getLeft().names.get());
    }

    @Test
    void aListHoldingTheWrongThingFallsBack(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test.toml");
        Files.createDirectories(dir);
        Files.writeString(file, "[section]\nnames = [1, 2, 3]\n");

        Pair<Holder, ModConfigSpec> pair = build();
        pair.getRight().load(file);

        assertEquals(List.of("alpha", "beta"), pair.getLeft().names.get(),
                "the element check has to reject numbers where strings were declared");
    }

    /**
     * ConfigSpecCodec walks getValues() and expects a ConfigValue at every leaf; GraphConfigEntries
     * walks getSpec() and expects a ValueSpec. Those two shapes are the contract the rest of the
     * mod relies on, so they are checked directly.
     */
    @Test
    void bothTreesCarryWhatTheWalkersExpect(@TempDir Path dir) {
        Pair<Holder, ModConfigSpec> pair = build();
        pair.getRight().load(dir.resolve("test.toml"));

        Object valuesLeaf = pair.getRight().getValues().getRaw(Arrays.asList("section", "count"));
        assertInstanceOf(ModConfigSpec.ConfigValue.class, valuesLeaf);
        assertEquals(5, ((ModConfigSpec.ConfigValue<?>) valuesLeaf).get());

        Object specLeaf = pair.getRight().getSpec().getRaw(Arrays.asList("section", "count"));
        assertInstanceOf(ModConfigSpec.ValueSpec.class, specLeaf);
        ModConfigSpec.ValueSpec spec = (ModConfigSpec.ValueSpec) specLeaf;
        assertEquals(5, spec.getDefault());
        assertNotNull(spec.getRange(), "defineInRange has to leave a range behind for the editor");
        assertEquals(1, spec.getRange().getMin());
        assertEquals(10, spec.getRange().getMax());
        assertTrue(spec.test(7));
        assertFalse(spec.test(99));

        Object section = pair.getRight().getSpec().getRaw(List.of("section"));
        assertInstanceOf(UnmodifiableConfig.class, section, "a pushed section has to stay a sub-config");
    }

    @Test
    void writingBeforeTheFileIsLoadedIsRefused() {
        Pair<Holder, ModConfigSpec> pair = build();
        assertFalse(pair.getRight().isLoaded());
        assertThrows(IllegalStateException.class, () -> pair.getLeft().flag.set(false),
                "writing into an unloaded spec silently dropped the value on the other loader too");
    }

    @Test
    void aReloadRefreshesWhatWasCached(@TempDir Path dir) throws IOException {
        Pair<Holder, ModConfigSpec> pair = build();
        Path file = dir.resolve("test.toml");
        pair.getRight().load(file);
        assertEquals(5, pair.getLeft().count.get(), "reads the default and caches it");

        // Someone edits the file by hand, then the mod reloads it.
        CommentedFileConfig edited = CommentedFileConfig.builder(file).sync().build();
        edited.load();
        edited.set(Arrays.asList("section", "count"), 8);
        edited.save();
        edited.close();

        pair.getRight().load(file);
        assertEquals(8, pair.getLeft().count.get(),
                "the cache has to be dropped on reload, or the editor would show stale values");
    }

    @Test
    void theReloadListenerRunsOnEveryLoad(@TempDir Path dir) {
        Pair<Holder, ModConfigSpec> pair = build();
        int[] runs = {0};
        pair.getRight().setReloadListener(() -> runs[0]++);

        Path file = dir.resolve("test.toml");
        pair.getRight().load(file);
        pair.getRight().load(file);

        assertEquals(2, runs[0], "the derived caches hang off this, so it has to fire every time");
    }
}
