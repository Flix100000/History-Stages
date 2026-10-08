package net.bananemdnsa.historystages.api.trigger;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The world as one player sees it at one poll, or the world alone for global stages.
 *
 * <p>Every value is computed on first use and then kept. Fifteen stages asking "in the Nether?"
 * cost one lookup, and a value nobody asks for costs nothing. A view lives for one poll only, so
 * the next poll always sees the world as it is then.
 *
 * <p>Ids are plain strings ({@code "minecraft:the_nether"}) so a test can build a view without
 * Minecraft. The live player and level sit behind {@link StateViews} for the same reason.
 */
public final class StateView {

    /** Structures the position lies inside: their ids and every tag they carry (without '#'). */
    public record Structures(Set<String> ids, Set<String> tags) {
        public static final Structures NONE = new Structures(Set.of(), Set.of());
    }

    private final Memo<String> dimension;
    private final Memo<String> biome;
    private final Memo<Structures> structures;
    private final Memo<Set<String>> effects;
    private final Memo<Set<String>> items;
    private final Memo<Integer> xpLevel;
    private final Memo<Boolean> raining;
    private final Memo<Boolean> thundering;
    private final Memo<Long> dayTime;
    // Object rather than ServerPlayer/ServerLevel: unit tests load this class without Minecraft.
    final Object playerHandle;
    final Object levelHandle;

    private StateView(Builder b) {
        this.dimension = new Memo<>(b.dimension);
        this.biome = new Memo<>(b.biome);
        this.structures = new Memo<>(b.structures);
        this.effects = new Memo<>(b.effects);
        this.items = new Memo<>(b.items);
        this.xpLevel = new Memo<>(b.xpLevel);
        this.raining = new Memo<>(b.raining);
        this.thundering = new Memo<>(b.thundering);
        this.dayTime = new Memo<>(b.dayTime);
        this.playerHandle = b.playerHandle;
        this.levelHandle = b.levelHandle;
    }

    public String dimension() { return dimension.get(); }
    public String biome() { return biome.get(); }
    public Set<String> structureIds() { return structures.get().ids(); }
    public Set<String> structureTags() { return structures.get().tags(); }
    public Set<String> effects() { return effects.get(); }
    public Set<String> items() { return items.get(); }
    public int xpLevel() { return xpLevel.get(); }
    public boolean raining() { return raining.get(); }
    public boolean thundering() { return thundering.get(); }
    public long dayTime() { return dayTime.get(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Supplier<String> dimension = () -> "";
        private Supplier<String> biome = () -> "";
        private Supplier<Structures> structures = () -> Structures.NONE;
        private Supplier<Set<String>> effects = Set::of;
        private Supplier<Set<String>> items = Set::of;
        private Supplier<Integer> xpLevel = () -> 0;
        private Supplier<Boolean> raining = () -> false;
        private Supplier<Boolean> thundering = () -> false;
        // -1 is no time of day at all: TimeOfDayTrigger floor-mods it to 23999, so an empty view
        // would read as "just before dawn". Real views always set it.
        private Supplier<Long> dayTime = () -> -1L;
        private Object playerHandle;
        private Object levelHandle;

        private Builder() {}

        public Builder dimension(String v) { return dimension(() -> v); }
        public Builder dimension(Supplier<String> s) { this.dimension = s; return this; }
        public Builder biome(String v) { return biome(() -> v); }
        public Builder biome(Supplier<String> s) { this.biome = s; return this; }
        public Builder structures(Structures v) { return structures(() -> v); }
        public Builder structures(Supplier<Structures> s) { this.structures = s; return this; }
        public Builder effects(Set<String> v) { return effects(() -> v); }
        public Builder effects(Supplier<Set<String>> s) { this.effects = s; return this; }
        public Builder items(Set<String> v) { return items(() -> v); }
        public Builder items(Supplier<Set<String>> s) { this.items = s; return this; }
        public Builder xpLevel(int v) { return xpLevel(() -> v); }
        public Builder xpLevel(Supplier<Integer> s) { this.xpLevel = s; return this; }
        public Builder weather(boolean raining, boolean thundering) {
            return weather(() -> raining, () -> thundering);
        }
        public Builder weather(Supplier<Boolean> raining, Supplier<Boolean> thundering) {
            this.raining = raining; this.thundering = thundering; return this;
        }
        public Builder dayTime(long v) { return dayTime(() -> v); }
        public Builder dayTime(Supplier<Long> s) { this.dayTime = s; return this; }
        /** Live handles for {@link StateViews}. Typed Object on purpose, see the field comment. */
        public Builder handles(Object player, Object level) {
            this.playerHandle = player; this.levelHandle = level; return this;
        }
        public StateView build() { return new StateView(this); }
    }

    private static final class Memo<T> {
        private Supplier<T> source;
        private T value;

        Memo(Supplier<T> source) { this.source = source; }

        T get() {
            if (source != null) {
                value = source.get();
                source = null;
            }
            return value;
        }
    }
}
