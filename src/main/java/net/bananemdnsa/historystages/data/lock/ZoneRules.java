package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * What a zone does to a player who is inside it while its stage is still locked.
 *
 * <p>This is the whole point of the category. Biome and structure locks keep their switches in
 * the common config, so every biome behaves alike; a zone carries its own, so one can burn and
 * the next can merely block interaction.
 *
 * <p>Five fields — {@link #barrier}, {@link #blockSpawns}, {@link #inverted}, {@link #showBorder}
 * and {@link #showOverlay} — are written, read and shown in the editor but do nothing yet. They
 * belong to round 2 and are here from the start so that a pack written between the rounds never
 * needs migrating. Migrations have twice been the expensive part in this mod.
 *
 * <p>Every getter that hands back a nested block installs one when the field is missing. Gson
 * leaves a key absent from the file as {@code null}, and stage files are hand-written often
 * enough for that to be the normal case rather than the exception.
 */
public class ZoneRules {

    public static class Message {
        private boolean enabled = true;
        private String text = "";
        @SerializedName("in_chat")
        private boolean inChat = false;

        public boolean isEnabled() { return enabled; }
        public String getText() { return text != null ? text : ""; }
        public boolean isInChat() { return inChat; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setText(String text) { this.text = text != null ? text : ""; }
        public void setInChat(boolean inChat) { this.inChat = inChat; }
    }

    public static class Damage {
        private boolean enabled = false;
        private double amount = 1.0;
        private int interval = 20;

        public boolean isEnabled() { return enabled; }
        public double getAmount() { return amount; }

        /** Never below one tick — a zero would mean damage every tick and read as a freeze. */
        public int getInterval() { return Math.max(1, interval); }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setAmount(double amount) { this.amount = amount; }
        public void setInterval(int interval) { this.interval = interval; }
    }

    public static class Effects {
        private boolean enabled = false;
        @SerializedName("clear_on_leave")
        private boolean clearOnLeave = false;
        private List<ZoneEffectSpec> list = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public boolean isClearOnLeave() { return clearOnLeave; }
        public List<ZoneEffectSpec> getList() { return list != null ? list : new ArrayList<>(); }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setClearOnLeave(boolean clearOnLeave) { this.clearOnLeave = clearOnLeave; }

        public void setList(List<ZoneEffectSpec> list) {
            this.list = list != null ? new ArrayList<>(list) : new ArrayList<>();
        }
    }

    private Message message = new Message();
    private Damage damage = new Damage();
    private Effects effects = new Effects();

    @SerializedName("block_right_click")
    private boolean blockRightClick = true;
    @SerializedName("block_left_click")
    private boolean blockLeftClick = true;
    @SerializedName("block_projectiles")
    private boolean blockProjectiles = true;
    @SerializedName("block_explosions")
    private boolean blockExplosions = true;

    // --- Round 2. Stored and shown, deliberately inert until then. ---

    private boolean barrier = false;
    @SerializedName("block_spawns")
    private boolean blockSpawns = false;
    private boolean inverted = false;
    @SerializedName("show_border")
    private boolean showBorder = false;
    @SerializedName("show_overlay")
    private boolean showOverlay = false;

    public Message getMessage() {
        if (message == null) message = new Message();
        return message;
    }

    public Damage getDamage() {
        if (damage == null) damage = new Damage();
        return damage;
    }

    public Effects getEffects() {
        if (effects == null) effects = new Effects();
        return effects;
    }

    public boolean isBlockRightClick() { return blockRightClick; }
    public boolean isBlockLeftClick() { return blockLeftClick; }
    public boolean isBlockProjectiles() { return blockProjectiles; }
    public boolean isBlockExplosions() { return blockExplosions; }

    public boolean isBarrier() { return barrier; }
    public boolean isBlockSpawns() { return blockSpawns; }
    public boolean isInverted() { return inverted; }
    public boolean isShowBorder() { return showBorder; }
    public boolean isShowOverlay() { return showOverlay; }

    public void setBlockRightClick(boolean value) { this.blockRightClick = value; }
    public void setBlockLeftClick(boolean value) { this.blockLeftClick = value; }
    public void setBlockProjectiles(boolean value) { this.blockProjectiles = value; }
    public void setBlockExplosions(boolean value) { this.blockExplosions = value; }

    public void setBarrier(boolean value) { this.barrier = value; }
    public void setBlockSpawns(boolean value) { this.blockSpawns = value; }
    public void setInverted(boolean value) { this.inverted = value; }
    public void setShowBorder(boolean value) { this.showBorder = value; }
    public void setShowOverlay(boolean value) { this.showOverlay = value; }

    /**
     * A rule set the caller may edit without touching this one.
     *
     * <p>Named field by field on purpose, like every other {@code copy()} in this package. That
     * makes adding a rule a two-line change instead of one — and the {@code copy}-carries-it test
     * beside {@link net.bananemdnsa.historystages.data.StageEntry} is what catches the second line
     * when it is forgotten.
     */
    public ZoneRules copy() {
        ZoneRules copy = new ZoneRules();

        copy.getMessage().setEnabled(getMessage().isEnabled());
        copy.getMessage().setText(getMessage().getText());
        copy.getMessage().setInChat(getMessage().isInChat());

        copy.getDamage().setEnabled(getDamage().isEnabled());
        copy.getDamage().setAmount(getDamage().getAmount());
        copy.getDamage().setInterval(getDamage().getInterval());

        copy.getEffects().setEnabled(getEffects().isEnabled());
        copy.getEffects().setClearOnLeave(getEffects().isClearOnLeave());
        // ZoneEffectSpec is a record of a string and two ints, so sharing entries is safe.
        copy.getEffects().setList(getEffects().getList());

        copy.setBlockRightClick(blockRightClick);
        copy.setBlockLeftClick(blockLeftClick);
        copy.setBlockProjectiles(blockProjectiles);
        copy.setBlockExplosions(blockExplosions);

        copy.setBarrier(barrier);
        copy.setBlockSpawns(blockSpawns);
        copy.setInverted(inverted);
        copy.setShowBorder(showBorder);
        copy.setShowOverlay(showOverlay);
        return copy;
    }
}
