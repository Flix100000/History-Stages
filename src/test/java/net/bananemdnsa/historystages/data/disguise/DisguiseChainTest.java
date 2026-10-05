package net.bananemdnsa.historystages.data.disguise;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class DisguiseChainTest {

    private static Map<String, DisguiseRule> rules(String... pairs) {
        Map<String, DisguiseRule> out = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], new DisguiseRule(pairs[i], pairs[i + 1], DropsMode.REAL, true, null));
        }
        return out;
    }

    private static Predicate<String> locked(String... ids) {
        return Set.of(ids)::contains;
    }

    @Test
    void noRuleNoResult() {
        assertNull(DisguiseChain.follow("a:ore", rules()::get, locked("a:ore")));
    }

    @Test
    void unlockedStartNoResult() {
        assertNull(DisguiseChain.follow("a:ore", rules("a:ore", "a:stone")::get, locked()));
    }

    @Test
    void singleStep() {
        DisguiseChain.Result r = DisguiseChain.follow("a:ore",
                rules("a:ore", "a:stone")::get, locked("a:ore"));

        assertEquals("a:stone", r.finalId());
        assertEquals("a:ore", r.first().key());
    }

    @Test
    void followsLockedTarget() {
        DisguiseChain.Result r = DisguiseChain.follow("a:ore",
                rules("a:ore", "a:stone", "a:stone", "a:andesite")::get, locked("a:ore", "a:stone"));

        assertEquals("a:andesite", r.finalId());
        assertEquals("a:ore", r.first().key());
    }

    @Test
    void stopsAtUnlockedTarget() {
        DisguiseChain.Result r = DisguiseChain.follow("a:ore",
                rules("a:ore", "a:stone", "a:stone", "a:andesite")::get, locked("a:ore"));

        assertEquals("a:stone", r.finalId());
    }

    @Test
    void cycleStopsBeforeRepeat() {
        DisguiseChain.Result r = DisguiseChain.follow("a:x",
                rules("a:x", "a:y", "a:y", "a:x")::get, locked("a:x", "a:y"));

        assertEquals("a:y", r.finalId());
    }

    @Test
    void depthIsCapped() {
        Map<String, DisguiseRule> chain = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            chain.put("a:" + i, new DisguiseRule("a:" + i, "a:" + (i + 1), DropsMode.REAL, true, null));
        }

        DisguiseChain.Result r = DisguiseChain.follow("a:0", chain::get, id -> true);

        assertEquals("a:" + DisguiseChain.MAX_DEPTH, r.finalId());
    }
}
