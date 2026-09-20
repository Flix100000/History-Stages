package net.bananemdnsa.historystages.platform;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The config spec this mod is written against, rebuilt on Fabric.
 *
 * <p>NeoForge hands out a builder that declares keys with defaults, ranges and comments, and then
 * owns the TOML file behind them. Fabric has nothing of the kind, so this is that class again,
 * kept to the shape the mod already uses: the same builder calls, the same value types, and the
 * same two trees behind {@link #getValues()} and {@link #getSpec()}.
 *
 * <p>It stands on NightConfig, which is the library NeoForge builds on as well. That is not
 * convenience: five files in this tree walk those trees generically and read NightConfig types
 * straight out of them, and the files this writes have to stay readable by the other loader. A
 * hand-rolled TOML layer would have broken both.
 *
 * <p>What is deliberately missing is a file watcher. On NeoForge it reloads a config mid-session
 * and has been the cause of test flakes there for exactly that reason. Nothing in this mod needs
 * a config to change without someone asking for it.
 */
public final class ModConfigSpec {

    private final Config values;
    private final Config specTree;

    private CommentedFileConfig file;
    private final List<ConfigValue<?>> declared;
    private Runnable onReload = () -> {};

    private ModConfigSpec(Config values, Config specTree, List<ConfigValue<?>> declared) {
        this.values = values;
        this.specTree = specTree;
        this.declared = declared;
    }

    /** The declared keys, with a {@link ConfigValue} at every leaf. */
    public UnmodifiableConfig getValues() {
        return values;
    }

    /** The same tree shape, with a {@link ValueSpec} at every leaf. */
    public UnmodifiableConfig getSpec() {
        return specTree;
    }

    public boolean isLoaded() {
        return file != null;
    }

    /** Runs after every {@link #load(Path)}, standing in for NeoForge's config load/reload event. */
    public void setReloadListener(Runnable listener) {
        this.onReload = listener == null ? () -> {} : listener;
    }

    /**
     * Reads the file, writes back anything missing or out of range, and points the declared values
     * at it. Creating the file when it is not there is the normal first-run path, not an error.
     */
    public void load(Path path) {
        try {
            java.nio.file.Files.createDirectories(path.getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not create the directory for " + path, e);
        }

        CommentedFileConfig loaded = CommentedFileConfig.builder(path)
                .sync()
                .preserveInsertionOrder()
                .build();
        loaded.load();

        this.file = loaded;
        correct(specTree, new ArrayList<>());
        loaded.save();

        for (ConfigValue<?> value : declared) {
            value.invalidate();
        }
        onReload.run();
    }

    /** Fills in every key the file does not have, or holds a value the spec rejects. */
    private void correct(UnmodifiableConfig level, List<String> prefix) {
        for (UnmodifiableConfig.Entry entry : level.entrySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.add(entry.getKey());
            Object raw = entry.getRawValue();
            if (raw instanceof UnmodifiableConfig nested) {
                correct(nested, path);
            } else if (raw instanceof ValueSpec spec) {
                Object current = file.getRaw(path);
                Object usable = spec.fromStored(current);
                if (usable == null || !spec.test(usable)) {
                    file.set(path, spec.toStored(spec.getDefault()));
                }
                if (spec.getComment() != null) {
                    file.setComment(path, " " + String.join("\n ", spec.getComment()));
                }
            }
        }
    }

    public void save() {
        if (file != null) {
            file.save();
        }
    }

    // ---------------------------------------------------------------- values

    /**
     * One declared key. Reads go through a cache because these are read per tooltip and per tick;
     * the cache is dropped whenever the file is reloaded or the value is written.
     */
    public static class ConfigValue<T> implements Supplier<T> {

        private final ModConfigSpec owner;
        private final List<String> path;
        private final ValueSpec spec;
        private T cached;
        private boolean cacheValid;

        ConfigValue(ModConfigSpec owner, List<String> path, ValueSpec spec) {
            this.owner = owner;
            this.path = path;
            this.spec = spec;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            if (!cacheValid) {
                if (owner.file == null) {
                    return (T) spec.getDefault();
                }
                Object stored = owner.file.getRaw(path);
                Object usable = spec.fromStored(stored);
                cached = (T) (usable != null && spec.test(usable) ? usable : spec.getDefault());
                cacheValid = true;
            }
            return cached;
        }

        public void set(T value) {
            if (owner.file == null) {
                throw new IllegalStateException("Cannot write " + getPath() + " before the config has been loaded");
            }
            owner.file.set(path, spec.toStored(value));
            cached = value;
            cacheValid = true;
        }

        @SuppressWarnings("unchecked")
        public T getDefault() {
            return (T) spec.getDefault();
        }

        public ValueSpec getSpec() {
            return spec;
        }

        public String getPath() {
            return String.join(".", path);
        }

        void invalidate() {
            cacheValid = false;
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(ModConfigSpec owner, List<String> path, ValueSpec spec) {
            super(owner, path, spec);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        IntValue(ModConfigSpec owner, List<String> path, ValueSpec spec) {
            super(owner, path, spec);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        DoubleValue(ModConfigSpec owner, List<String> path, ValueSpec spec) {
            super(owner, path, spec);
        }
    }

    public static final class EnumValue<T extends Enum<T>> extends ConfigValue<T> {
        EnumValue(ModConfigSpec owner, List<String> path, ValueSpec spec) {
            super(owner, path, spec);
        }
    }

    // ------------------------------------------------------------ value spec

    /**
     * What a key accepts. TOML has no enum type, so an enum travels as its constant name and comes
     * back through {@link #fromStored(Object)}; everything else is stored as it is.
     */
    public static final class ValueSpec {

        private final Object defaultValue;
        private final List<String> comment;
        private final Range<?> range;
        private final Predicate<Object> validator;
        private final Class<?> enumClass;

        ValueSpec(Object defaultValue, List<String> comment, Range<?> range,
                  Predicate<Object> validator, Class<?> enumClass) {
            this.defaultValue = defaultValue;
            this.comment = comment;
            this.range = range;
            this.validator = validator;
            this.enumClass = enumClass;
        }

        public Object getDefault() {
            return defaultValue;
        }

        public List<String> getComment() {
            return comment;
        }

        public Range<?> getRange() {
            return range;
        }

        public boolean test(Object value) {
            return value != null && validator.test(value);
        }

        /** Turns what the file holds into the type the declaration asked for, or null if it cannot. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object fromStored(Object stored) {
            if (stored == null) {
                return null;
            }
            if (enumClass != null) {
                if (enumClass.isInstance(stored)) {
                    return stored;
                }
                if (stored instanceof String name) {
                    try {
                        return Enum.valueOf((Class<Enum>) enumClass, name.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
                return null;
            }
            // TOML numbers come back as the widest type that fits, so an int key can arrive long.
            if (defaultValue instanceof Integer && stored instanceof Number n) {
                return n.intValue();
            }
            if (defaultValue instanceof Double && stored instanceof Number n) {
                return n.doubleValue();
            }
            return stored;
        }

        Object toStored(Object value) {
            return value instanceof Enum<?> constant ? constant.name() : value;
        }
    }

    /** An inclusive pair of bounds, as declared by {@code defineInRange}. */
    public static final class Range<V extends Comparable<? super V>> implements Predicate<Object> {

        private final V min;
        private final V max;

        Range(V min, V max) {
            this.min = min;
            this.max = max;
        }

        public V getMin() {
            return min;
        }

        public V getMax() {
            return max;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean test(Object value) {
            if (!min.getClass().isInstance(value)) {
                return false;
            }
            V typed = (V) value;
            return min.compareTo(typed) <= 0 && max.compareTo(typed) >= 0;
        }

        @Override
        public String toString() {
            return "[" + min + ", " + max + "]";
        }
    }

    // --------------------------------------------------------------- builder

    public static final class Builder {

        private final Config values = Config.of(InMemoryFormat.withUniversalSupport());
        private final Config specTree = Config.of(InMemoryFormat.withUniversalSupport());
        private final List<ConfigValue<?>> declared = new ArrayList<>();
        private final List<String> stack = new ArrayList<>();
        private List<String> pendingComment = new ArrayList<>();

        private ModConfigSpec built;

        public Builder comment(String... lines) {
            pendingComment.addAll(Arrays.asList(lines));
            return this;
        }

        public Builder push(String section) {
            List<String> path = pathTo(section);
            values.set(path, Config.of(InMemoryFormat.withUniversalSupport()));
            specTree.set(path, Config.of(InMemoryFormat.withUniversalSupport()));
            stack.add(section);
            // A comment written just before a push belongs to the table, not to the next key.
            pendingComment = new ArrayList<>();
            return this;
        }

        public Builder pop() {
            if (stack.isEmpty()) {
                throw new IllegalStateException("pop() without a matching push()");
            }
            stack.remove(stack.size() - 1);
            return this;
        }

        public BooleanValue define(String key, boolean defaultValue) {
            return add(key, new ValueSpec(defaultValue, takeComment(), null,
                    o -> o instanceof Boolean, null), BooleanValue::new);
        }

        public <T> ConfigValue<T> define(String key, T defaultValue) {
            Class<?> type = defaultValue.getClass();
            return add(key, new ValueSpec(defaultValue, takeComment(), null,
                    type::isInstance, null), ConfigValue::new);
        }

        public IntValue defineInRange(String key, int defaultValue, int min, int max) {
            Range<Integer> range = new Range<>(min, max);
            return add(key, new ValueSpec(defaultValue, takeComment(), range, range, null), IntValue::new);
        }

        public DoubleValue defineInRange(String key, double defaultValue, double min, double max) {
            Range<Double> range = new Range<>(min, max);
            return add(key, new ValueSpec(defaultValue, takeComment(), range, range, null), DoubleValue::new);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String key, V defaultValue) {
            Class<?> declaring = defaultValue.getDeclaringClass();
            return add(key, new ValueSpec(defaultValue, takeComment(), null,
                    declaring::isInstance, declaring), EnumValue::new);
        }

        public <T> ConfigValue<List<? extends T>> defineList(String key, List<? extends T> defaultValue,
                                                             Predicate<Object> elementValidator) {
            Predicate<Object> listCheck = o -> {
                if (!(o instanceof List<?> list)) {
                    return false;
                }
                for (Object element : list) {
                    if (!elementValidator.test(element)) {
                        return false;
                    }
                }
                return true;
            };
            return add(key, new ValueSpec(defaultValue, takeComment(), null, listCheck, null),
                    ConfigValue::new);
        }

        /** Runs the declaration body and hands back what it produced next to the finished spec. */
        public <T> Pair<T, ModConfigSpec> configure(Function<Builder, T> body) {
            T holder = body.apply(this);
            if (!stack.isEmpty()) {
                throw new IllegalStateException("Config sections left open: " + String.join(".", stack));
            }
            return Pair.of(holder, build());
        }

        private ModConfigSpec build() {
            return pendingOwner();
        }

        private <V extends ConfigValue<?>> V add(String key, ValueSpec spec, ValueFactory<V> factory) {
            List<String> path = pathTo(key);
            specTree.set(path, spec);
            // The spec object is not finished yet, so the value is parked and hooked up in build().
            V value = factory.create(pendingOwner(), path, spec);
            declared.add(value);
            values.set(path, value);
            return value;
        }

        /**
         * The values have to know their spec before {@code configure} returns, because the
         * declaration body stores them in final fields. So the instance is created up front and
         * filled in as the last step of {@link #build()}.
         */
        private ModConfigSpec pendingOwner() {
            if (built == null) {
                built = new ModConfigSpec(values, specTree, declared);
            }
            return built;
        }

        private List<String> pathTo(String key) {
            List<String> path = new ArrayList<>(stack);
            path.add(key);
            return path;
        }

        private List<String> takeComment() {
            List<String> comment = pendingComment.isEmpty() ? null : List.copyOf(pendingComment);
            pendingComment = new ArrayList<>();
            return comment;
        }

        private interface ValueFactory<V> {
            V create(ModConfigSpec owner, List<String> path, ValueSpec spec);
        }
    }
}
