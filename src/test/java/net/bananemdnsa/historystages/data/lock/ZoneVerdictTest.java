package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens when a player stands in two locked zones at once.
 *
 * <p>The rules here are a decision, not an emergent property: the highest damage rather than the
 * sum, because overlapping zones while building a pack must not cost a life by accident.
 */
class ZoneVerdictTest {

    private static ZoneEntry zone(String name) {
        ZoneEntry zone = new ZoneEntry();
        zone.setName(name);
        return zone;
    }

    @Test
    void noZonesMeansNothingApplies() {
        ZoneVerdict verdict = ZoneVerdict.of(List.of());
        assertFalse(verdict.isLocked());
        assertFalse(verdict.damageEnabled());
        assertFalse(verdict.messageEnabled());
        assertTrue(verdict.effects().isEmpty());
    }

    /** Every switch is an OR: one zone saying yes is enough. */
    @Test
    void oneSwitchOnIsEnough() {
        ZoneEntry off = zone("off");
        off.getRules().setBlockRightClick(false);
        ZoneEntry on = zone("on");
        on.getRules().setBlockRightClick(true);

        assertTrue(ZoneVerdict.of(List.of(off, on)).blockRightClick());
        assertFalse(ZoneVerdict.of(List.of(off)).blockRightClick());
    }

    /** The highest value wins, never the sum. */
    @Test
    void damageTakesTheHighestValueNotTheSum() {
        ZoneEntry weak = zone("weak");
        weak.getRules().getDamage().setEnabled(true);
        weak.getRules().getDamage().setAmount(1.0);
        ZoneEntry strong = zone("strong");
        strong.getRules().getDamage().setEnabled(true);
        strong.getRules().getDamage().setAmount(4.0);

        ZoneVerdict verdict = ZoneVerdict.of(List.of(weak, strong));
        assertTrue(verdict.damageEnabled());
        assertEquals(4.0, verdict.damageAmount());
    }

    /** The interval comes from the zone that set the winning amount, not from another one. */
    @Test
    void theIntervalFollowsTheWinningAmount() {
        ZoneEntry slowAndWeak = zone("slow");
        slowAndWeak.getRules().getDamage().setEnabled(true);
        slowAndWeak.getRules().getDamage().setAmount(1.0);
        slowAndWeak.getRules().getDamage().setInterval(100);
        ZoneEntry fastAndStrong = zone("fast");
        fastAndStrong.getRules().getDamage().setEnabled(true);
        fastAndStrong.getRules().getDamage().setAmount(4.0);
        fastAndStrong.getRules().getDamage().setInterval(10);

        assertEquals(10, ZoneVerdict.of(List.of(slowAndWeak, fastAndStrong)).damageInterval());
    }

    /** A zone with damage switched off contributes nothing, however large its number. */
    @Test
    void damageThatIsSwitchedOffIsIgnored() {
        ZoneEntry off = zone("off");
        off.getRules().getDamage().setEnabled(false);
        off.getRules().getDamage().setAmount(99.0);

        assertFalse(ZoneVerdict.of(List.of(off)).damageEnabled());
    }

    /** One message, from the first zone that has one — not three stacked on each other. */
    @Test
    void theFirstZoneWithAMessageWins() {
        ZoneEntry silent = zone("silent");
        silent.getRules().getMessage().setEnabled(false);
        silent.getRules().getMessage().setText("nope");
        ZoneEntry first = zone("first");
        first.getRules().getMessage().setEnabled(true);
        first.getRules().getMessage().setText("A");
        ZoneEntry second = zone("second");
        second.getRules().getMessage().setEnabled(true);
        second.getRules().getMessage().setText("B");

        ZoneVerdict verdict = ZoneVerdict.of(List.of(silent, first, second));
        assertTrue(verdict.messageEnabled());
        assertEquals("A", verdict.messageText());
        assertEquals("first", verdict.messageZoneName());
    }

    /** Same effect twice: the higher level wins; at equal levels, the longer one. */
    @Test
    void effectsMergeByStrengthThenDuration() {
        ZoneEntry a = zone("a");
        a.getRules().getEffects().setEnabled(true);
        a.getRules().getEffects().setList(List.of(
                new ZoneEffectSpec("minecraft:blindness", 30, 0),
                new ZoneEffectSpec("minecraft:slowness", 10, 1)));
        ZoneEntry b = zone("b");
        b.getRules().getEffects().setEnabled(true);
        b.getRules().getEffects().setList(List.of(
                new ZoneEffectSpec("minecraft:blindness", 10, 2)));

        List<ZoneEffectSpec> merged = ZoneVerdict.of(List.of(a, b)).effects();
        assertEquals(2, merged.size(), "two distinct effects");

        ZoneEffectSpec blindness = merged.stream()
                .filter(e -> e.id().equals("minecraft:blindness")).findFirst().orElseThrow();
        assertEquals(2, blindness.amplifier(), "the stronger one wins");
    }

    @Test
    void atEqualStrengthTheLongerEffectWins() {
        ZoneEntry a = zone("a");
        a.getRules().getEffects().setEnabled(true);
        a.getRules().getEffects().setList(List.of(new ZoneEffectSpec("minecraft:blindness", 10, 1)));
        ZoneEntry b = zone("b");
        b.getRules().getEffects().setEnabled(true);
        b.getRules().getEffects().setList(List.of(new ZoneEffectSpec("minecraft:blindness", 60, 1)));

        List<ZoneEffectSpec> merged = ZoneVerdict.of(List.of(a, b)).effects();
        assertEquals(1, merged.size());
        assertEquals(60, merged.get(0).seconds());
    }

    /** An unusable entry — no id, absurd duration — never reaches the effect registry. */
    @Test
    void unusableEffectsAreDropped() {
        ZoneEntry a = zone("a");
        a.getRules().getEffects().setEnabled(true);
        a.getRules().getEffects().setList(List.of(
                new ZoneEffectSpec("", 30, 0),
                new ZoneEffectSpec("minecraft:blindness", 99999, 0),
                new ZoneEffectSpec("minecraft:slowness", 0, 0)));

        assertTrue(ZoneVerdict.of(List.of(a)).effects().isEmpty());
    }

    /** A zone with effects switched off contributes none, however full its list. */
    @Test
    void effectsThatAreSwitchedOffAreIgnored() {
        ZoneEntry off = zone("off");
        off.getRules().getEffects().setEnabled(false);
        off.getRules().getEffects().setList(List.of(new ZoneEffectSpec("minecraft:blindness", 30, 0)));

        assertTrue(ZoneVerdict.of(List.of(off)).effects().isEmpty());
    }

    /** The five that were stored but inert until round 2 merge like every other switch. */
    @Test
    void theRoundTwoSwitchesAreAnOrLikeTheRest() {
        ZoneEntry plain = zone("plain");
        ZoneEntry loud = zone("loud");
        loud.getRules().setBarrier(true);
        loud.getRules().setBlockSpawns(true);
        loud.getRules().setShowBorder(true);
        loud.getRules().setShowOverlay(true);

        ZoneVerdict quiet = ZoneVerdict.of(List.of(plain));
        assertFalse(quiet.barrier());
        assertFalse(quiet.blockSpawns());
        assertFalse(quiet.showBorder());
        assertFalse(quiet.showOverlay());

        ZoneVerdict merged = ZoneVerdict.of(List.of(plain, loud));
        assertTrue(merged.barrier());
        assertTrue(merged.blockSpawns());
        assertTrue(merged.showBorder());
        assertTrue(merged.showOverlay());
    }

    /**
     * Inversion is deliberately absent from the verdict.
     *
     * <p>It is not a property of "what happens to you here" but of "is here inside" — it decides
     * which zones reach the verdict at all, and by the time one has, the question is settled. A
     * merged {@code inverted} flag would read as if it could still flip something and would be
     * wrong for every zone in the list but one.
     */
    @Test
    void aBarrierZoneStillReportsItsOtherRules() {
        ZoneEntry cage = zone("cage");
        cage.getRules().setBarrier(true);
        cage.getRules().setInverted(true);
        cage.getRules().getDamage().setEnabled(true);
        cage.getRules().getDamage().setAmount(2.0);

        ZoneVerdict verdict = ZoneVerdict.of(List.of(cage));
        assertTrue(verdict.barrier());
        assertEquals(2.0, verdict.damageAmount());
    }

    /** Clearing on leave is an OR like the other switches. */
    @Test
    void clearOnLeaveIsSetByAnyZoneAskingForIt() {
        ZoneEntry keeps = zone("keeps");
        ZoneEntry clears = zone("clears");
        clears.getRules().getEffects().setClearOnLeave(true);

        assertFalse(ZoneVerdict.of(List.of(keeps)).clearEffectsOnLeave());
        assertTrue(ZoneVerdict.of(List.of(keeps, clears)).clearEffectsOnLeave());
    }
}
