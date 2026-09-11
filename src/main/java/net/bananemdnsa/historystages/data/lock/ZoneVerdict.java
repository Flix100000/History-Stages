package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single answer for a player standing in one or more locked zones at once.
 *
 * <p>Overlap is normal rather than exceptional: a zone is the union of its shapes, and packs are
 * built by dropping boxes onto a map until the area looks right. So the merge rules matter, and
 * they are decisions rather than something that falls out of the code:
 *
 * <ul>
 *   <li><strong>Switches</strong> are an OR. One zone asking for interaction to be blocked is
 *       enough; a zone that does not ask cannot un-ask on another's behalf.</li>
 *   <li><strong>Damage</strong> takes the highest amount, never the sum. Nudging two zones into
 *       overlap while building must not quietly become lethal. The interval travels with the
 *       amount that won, so the winning zone's pacing is the one that applies.</li>
 *   <li><strong>The message</strong> is the first one on offer, in the order the zones were
 *       found. Three messages fighting over the action bar reads as a bug.</li>
 *   <li><strong>Effects</strong> are a union keyed by effect id: the stronger amplifier wins,
 *       and at equal strength the longer duration.</li>
 * </ul>
 *
 * <p>No Minecraft imports — this is decision-making, not application. Resolving an effect id
 * against the registry happens where the effect is handed to the player.
 */
public final class ZoneVerdict {

    private static final ZoneVerdict EMPTY = new ZoneVerdict();

    private boolean locked;

    private boolean blockRightClick;
    private boolean blockLeftClick;
    private boolean blockProjectiles;
    private boolean blockExplosions;

    private boolean damageEnabled;
    private double damageAmount;
    private int damageInterval = 20;

    private boolean messageEnabled;
    private String messageText = "";
    private boolean messageInChat;
    private String messageZoneName = "";
    private int messageZoneIndex = -1;

    private boolean clearEffectsOnLeave;
    private List<ZoneEffectSpec> effects = List.of();

    private boolean barrier;
    private boolean blockSpawns;
    private boolean showBorder;
    private boolean showOverlay;

    private ZoneVerdict() {}

    /**
     * Merges the rules of every zone the player is currently inside.
     *
     * <p>The caller has already decided which zones those are — this class knows nothing about
     * geometry, positions or stages.
     */
    public static ZoneVerdict of(List<ZoneEntry> zonesInside) {
        if (zonesInside == null || zonesInside.isEmpty()) return EMPTY;

        ZoneVerdict verdict = new ZoneVerdict();
        verdict.locked = true;

        // Keyed by effect id so the same effect from two zones resolves once rather than being
        // applied twice at different strengths.
        Map<String, ZoneEffectSpec> strongest = new LinkedHashMap<>();

        for (int i = 0; i < zonesInside.size(); i++) {
            ZoneEntry zone = zonesInside.get(i);
            if (zone == null) continue;
            ZoneRules rules = zone.getRules();

            verdict.blockRightClick |= rules.isBlockRightClick();
            verdict.blockLeftClick |= rules.isBlockLeftClick();
            verdict.blockProjectiles |= rules.isBlockProjectiles();
            verdict.blockExplosions |= rules.isBlockExplosions();

            // Inversion is missing here on purpose: it decides which zones reach this list at all,
            // not what happens once one has. Merging it would suggest it could still flip
            // something, and it would be wrong for every zone in the list but one.
            verdict.barrier |= rules.isBarrier();
            verdict.blockSpawns |= rules.isBlockSpawns();
            verdict.showBorder |= rules.isShowBorder();
            verdict.showOverlay |= rules.isShowOverlay();

            ZoneRules.Damage damage = rules.getDamage();
            if (damage.isEnabled() && (!verdict.damageEnabled
                    || damage.getAmount() > verdict.damageAmount)) {
                verdict.damageEnabled = true;
                verdict.damageAmount = damage.getAmount();
                verdict.damageInterval = damage.getInterval();
            }

            ZoneRules.Message message = rules.getMessage();
            if (!verdict.messageEnabled && message.isEnabled()) {
                verdict.messageEnabled = true;
                verdict.messageText = message.getText();
                verdict.messageInChat = message.isInChat();
                verdict.messageZoneName = zone.getName();
                verdict.messageZoneIndex = i;
            }

            ZoneRules.Effects effects = rules.getEffects();
            verdict.clearEffectsOnLeave |= effects.isClearOnLeave();
            if (!effects.isEnabled()) continue;
            for (ZoneEffectSpec spec : effects.getList()) {
                if (spec == null || !spec.isUsable()) continue;
                strongest.merge(spec.id(), spec, ZoneVerdict::stronger);
            }
        }

        verdict.effects = List.copyOf(strongest.values());
        return verdict;
    }

    /** Higher amplifier wins; at equal strength, the one that lingers longer. */
    private static ZoneEffectSpec stronger(ZoneEffectSpec a, ZoneEffectSpec b) {
        if (b.amplifier() != a.amplifier()) {
            return b.amplifier() > a.amplifier() ? b : a;
        }
        return b.seconds() > a.seconds() ? b : a;
    }

    /** True when the player is inside at least one locked zone. */
    public boolean isLocked() { return locked; }

    public boolean blockRightClick() { return blockRightClick; }
    public boolean blockLeftClick() { return blockLeftClick; }
    public boolean blockProjectiles() { return blockProjectiles; }
    public boolean blockExplosions() { return blockExplosions; }

    public boolean damageEnabled() { return damageEnabled; }
    public double damageAmount() { return damageAmount; }
    public int damageInterval() { return damageInterval; }

    public boolean messageEnabled() { return messageEnabled; }
    public String messageText() { return messageText; }
    public boolean messageInChat() { return messageInChat; }

    /** The zone the message came from, for the {@code {zone}} placeholder. */
    public String messageZoneName() { return messageZoneName; }

    /**
     * Where that zone sat in the list handed to {@link #of}, or -1 when there is no message.
     *
     * <p>Lets the caller reach whatever it keeps alongside each zone — the stage that gates it,
     * say — without this class having to know what a stage is. Matching by name instead would
     * pick the wrong one as soon as two zones share a name, which nothing forbids.
     */
    public int messageZoneIndex() { return messageZoneIndex; }

    public boolean clearEffectsOnLeave() { return clearEffectsOnLeave; }

    /** The player is pushed back out — or, for an inverted zone, back in. */
    public boolean barrier() { return barrier; }

    public boolean blockSpawns() { return blockSpawns; }
    public boolean showBorder() { return showBorder; }
    public boolean showOverlay() { return showOverlay; }

    /** Never null; empty when no zone inside applies any. */
    public List<ZoneEffectSpec> effects() { return effects; }

    /** The verdict for "not in any zone". Shared because it is immutable and asked for constantly. */
    public static ZoneVerdict empty() {
        return EMPTY;
    }

    /** Defensive copy for the caller that wants to keep the list around. */
    public List<ZoneEffectSpec> effectsCopy() {
        return new ArrayList<>(effects);
    }
}
