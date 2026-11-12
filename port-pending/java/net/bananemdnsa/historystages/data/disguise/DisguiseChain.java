package net.bananemdnsa.historystages.data.disguise;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Follows a disguise to its end: emerald ore looks like stone, and while stone is itself locked
 * and disguised as andesite, the ore looks like andesite too. Otherwise the player would see two
 * different "stone" blocks and know one of them is lying.
 */
public final class DisguiseChain {

    /** More than any sane pack needs; only a guard against a loop running through tags. */
    public static final int MAX_DEPTH = 16;

    private DisguiseChain() {}

    /**
     * @param first   the rule matched for the starting item; it decides drops and hints
     * @param finalId the item the chain ends on; it decides the look, sounds and break behaviour
     */
    public record Result(DisguiseRule first, String finalId) {}

    /**
     * @param ruleFor  rule for an item id, or null; the caller does tag and NBT matching
     * @param isLocked whether an item id is locked for the viewer
     * @return null when the start has no rule or is not locked
     */
    public static Result follow(String startId, Function<String, DisguiseRule> ruleFor,
                                Predicate<String> isLocked) {
        if (!isLocked.test(startId)) return null;
        DisguiseRule first = ruleFor.apply(startId);
        if (first == null) return null;

        Set<String> visited = new HashSet<>();
        visited.add(startId);
        String current = first.as();
        for (int depth = 1; depth < MAX_DEPTH; depth++) {
            if (!isLocked.test(current)) break;
            DisguiseRule next = ruleFor.apply(current);
            if (next == null) break;
            if (!visited.add(current) || visited.contains(next.as())) break;
            current = next.as();
        }
        return new Result(first, current);
    }
}
