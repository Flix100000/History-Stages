package net.bananemdnsa.historystages.client.editor.logic;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicGroups;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws one logic block as a card: type and live status on top, then its groups, each with an
 * All/One-of switch and one row per term.
 *
 * <p>Built like {@code CriterionCard}: {@link #layout} produces the piece list, and drawing, height
 * and hit-testing all read that same list, so the three can never disagree about where a row is.
 */
public final class LogicBlockCard {

    public sealed interface Hit {
        record MoveUp() implements Hit {}
        record MoveDown() implements Hit {}
        record Remove() implements Hit {}
        record SetGroupAll(int group, boolean all) implements Hit {}
        record RemoveGroup(int group) implements Hit {}
        record AddGroup() implements Hit {}
        record SetTermUnlocked(int group, int term, boolean unlocked) implements Hit {}
        record PickStage(int group, int term) implements Hit {}
        record RemoveTerm(int group, int term) implements Hit {}
        record AddTerm(int group) implements Hit {}
        record SetHiddenMode(net.bananemdnsa.historystages.data.logic.LogicBlockParams.HiddenMode mode) implements Hit {}
        record EditPercent(String key) implements Hit {}
    }

    public record Built(List<Piece> pieces, int width, int height) {}

    public record Piece(int x, int y, int w, int h, Kind kind, String text, int color, Hit hit) {}

    public enum Kind { PANEL, TITLE, TEXT, PILL, SEG_ON, SEG_OFF, FIELD, ICON, ADD }

    /** What the card needs to know about the world to label and check terms. */
    public record Context(String ownerId, StageScope owner, Map<String, StageEntry> globalStages,
                          Map<String, StageEntry> individualStages, StageStateView global,
                          StageStateView individual) {}

    private static final int PAD = 8;
    private static final int HEADER_H = 12;
    private static final int ROW_H = 18;
    private static final int GAP = 4;
    private static final int ICON_W = 10;
    private static final int LABEL_W = 90;

    private static final int CARD_BG = 0xFF1A1A1A;
    private static final int CARD_BORDER = 0xFF333333;
    private static final int BLOCKED_ACCENT = 0xFFFF5555;
    private static final int GROUP_BG = 0xFF161616;
    private static final int GOLD = 0xFFFFCC00;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int DIM = 0xFF888888;
    private static final int WARN = 0xFFFF7777;
    private static final int OK = 0xFF55FF55;
    private static final int FIELD_BG = 0xFF0D0D0D;
    private static final int FIELD_BORDER = 0xFF4A4A4A;
    private static final int FIELD_BORDER_HOVER = 0xFF6A6A6A;

    private final Font font;

    public LogicBlockCard(Font font) {
        this.font = font;
    }

    // ---- layout ----------------------------------------------------------------------

    public Built layout(LogicDraft.Block block, boolean first, boolean last, int width, Context ctx) {
        List<Piece> pieces = new ArrayList<>();
        int inner = width - PAD * 2;
        int y = PAD;

        // Header: title, live status, then ▲ ▼ ✕ on the right.
        String title = Component.translatable(typeTitleKey(block.type)).getString();
        pieces.add(new Piece(PAD, y + 2, font.width(title), 8, Kind.TITLE, title, accent(block), null));
        int rx = PAD + inner - ICON_W;
        pieces.add(new Piece(rx, y, ICON_W, HEADER_H, Kind.ICON, "✕", DIM, new Hit.Remove()));
        rx -= ICON_W + 2;
        pieces.add(new Piece(rx, y, ICON_W, HEADER_H, Kind.ICON, "▼", last ? 0xFF444444 : DIM,
                last ? null : new Hit.MoveDown()));
        rx -= ICON_W + 2;
        pieces.add(new Piece(rx, y, ICON_W, HEADER_H, Kind.ICON, "▲", first ? 0xFF444444 : DIM,
                first ? null : new Hit.MoveUp()));

        if (block.editable() && !block.isEmpty()) {
            boolean holds = Condition.evaluate(block.toLogicBlock().condition(), ctx.owner(), ctx.global(), ctx.individual());
            String status = Component.translatable(statusKey(block.type, holds)).getString();
            int sx = PAD + font.width(title) + 8;
            if (sx + font.width(status) + 6 < rx - 4) {
                pieces.add(new Piece(sx, y, font.width(status) + 6, HEADER_H, Kind.PILL, status,
                        holds ? 0xFF6B1D1D : 0xFF1F5D2E, null));
            }
        }
        y += HEADER_H + 6;

        if (!block.editable()) {
            for (var line : font.split(Component.translatable(LogicBlockTypes.isKnown(block.type)
                    ? "editor.historystages.logic.read_only.tree"
                    : "editor.historystages.logic.read_only.type", block.type), inner)) {
                pieces.add(new Piece(PAD, y, inner, 8, Kind.TEXT, plain(line), DIM, null));
                y += 10;
            }
            return new Built(pieces, width, y + PAD);
        }

        y = layoutSettings(pieces, block, y, inner);

        List<LogicDraft.Group> groups = block.groups;
        for (int gi = 0; gi < groups.size(); gi++) {
            if (gi > 0) {
                String and = Component.translatable("editor.historystages.logic.and").getString();
                pieces.add(new Piece((width - font.width(and)) / 2, y, font.width(and), 8, Kind.TEXT, and, DIM, null));
                y += 12;
            }
            y = layoutGroup(pieces, groups.get(gi), gi, groups.size(), y, inner, ctx);
        }
        if (groups.size() < LogicGroups.MAX_GROUPS) {
            String add = Component.translatable("editor.historystages.logic.add_group").getString();
            pieces.add(new Piece(PAD, y, font.width(add) + 12, 12, Kind.ADD, add, DIM, new Hit.AddGroup()));
            y += 14;
        }
        if (block.isEmpty()) {
            for (var line : font.split(Component.translatable("editor.historystages.logic.warn.empty"), inner)) {
                pieces.add(new Piece(PAD, y, inner, 8, Kind.TEXT, plain(line), WARN, null));
                y += 10;
            }
        }
        return new Built(pieces, width, y + PAD - 2);
    }

    /** The type's own settings above the condition: hidden's mode, cost's three percentages. */
    private int layoutSettings(List<Piece> pieces, LogicDraft.Block block, int y, int inner) {
        if (LogicBlockTypes.HIDDEN_WHILE.equals(block.type)) {
            String label = Component.translatable("editor.historystages.logic.hidden.mode").getString();
            pieces.add(new Piece(PAD, y + 2, font.width(label), 8, Kind.TEXT, label, DIM, null));
            var mode = block.hiddenMode();
            int x = PAD + LABEL_W;
            x = segment(pieces, x, y, "editor.historystages.logic.hidden.anonymous",
                    mode == net.bananemdnsa.historystages.data.logic.LogicBlockParams.HiddenMode.ANONYMOUS,
                    new Hit.SetHiddenMode(net.bananemdnsa.historystages.data.logic.LogicBlockParams.HiddenMode.ANONYMOUS));
            segment(pieces, x, y, "editor.historystages.logic.hidden.vanish",
                    mode == net.bananemdnsa.historystages.data.logic.LogicBlockParams.HiddenMode.VANISH,
                    new Hit.SetHiddenMode(net.bananemdnsa.historystages.data.logic.LogicBlockParams.HiddenMode.VANISH));
            return y + 18;
        }
        if (LogicBlockTypes.REVOKE_WHEN.equals(block.type)) {
            String hint = Component.translatable("editor.historystages.logic.revoke.hint").getString();
            for (var line : font.split(Component.literal(hint), inner)) {
                pieces.add(new Piece(PAD, y, inner, 8, Kind.TEXT, plain(line), 0xFF666666, null));
                y += 10;
            }
            return y + 4;
        }
        if (LogicBlockTypes.COST_WHILE.equals(block.type)) {
            for (String key : new String[] {"time", "items", "xp"}) {
                String label = Component.translatable("editor.historystages.logic.cost." + key).getString();
                pieces.add(new Piece(PAD, y + 4, font.width(label), 8, Kind.TEXT, label, DIM, null));
                int percent = block.percent(key);
                pieces.add(new Piece(PAD + LABEL_W, y, 56, ROW_H - 2, Kind.FIELD, percent + " %",
                        percent == 100 ? DIM : TEXT, new Hit.EditPercent(key)));
                y += ROW_H;
            }
            String hint = Component.translatable("editor.historystages.logic.cost.hint").getString();
            for (var line : font.split(Component.literal(hint), inner)) {
                pieces.add(new Piece(PAD, y, inner, 8, Kind.TEXT, plain(line), 0xFF666666, null));
                y += 10;
            }
            return y + 4;
        }
        return y;
    }

    private static String statusKey(String type, boolean holds) {
        if (LogicBlockTypes.HIDDEN_WHILE.equals(type)) {
            return holds ? "editor.historystages.logic.status.hidden" : "editor.historystages.logic.status.visible";
        }
        if (LogicBlockTypes.COST_WHILE.equals(type)) {
            return holds ? "editor.historystages.logic.status.cost_active" : "editor.historystages.logic.status.cost_inactive";
        }
        if (LogicBlockTypes.REVOKE_WHEN.equals(type)) {
            return holds ? "editor.historystages.logic.status.revoke_holds" : "editor.historystages.logic.status.revoke_not_holds";
        }
        return holds ? "editor.historystages.logic.status.holds" : "editor.historystages.logic.status.not_holds";
    }

    private int layoutGroup(List<Piece> pieces, LogicDraft.Group group, int gi, int groupCount, int y,
                            int inner, Context ctx) {
        int top = y;
        int panelIndex = pieces.size();
        pieces.add(null); // the panel, sized once its height is known

        int gx = PAD + 6;
        int gw = inner - 12;
        y += 5;
        String label = Component.translatable("editor.historystages.logic.group", gi + 1).getString();
        pieces.add(new Piece(gx, y + 1, font.width(label), 8, Kind.TITLE, label, GOLD, null));
        int sx = gx + font.width(label) + 8;
        sx = segment(pieces, sx, y - 1, "editor.historystages.logic.all", group.all,
                new Hit.SetGroupAll(gi, true));
        segment(pieces, sx, y - 1, "editor.historystages.logic.one_of", !group.all,
                new Hit.SetGroupAll(gi, false));
        if (groupCount > 1) {
            pieces.add(new Piece(gx + gw - ICON_W, y, ICON_W, 10, Kind.ICON, "✕", DIM, new Hit.RemoveGroup(gi)));
        }
        y += 14;

        for (int ti = 0; ti < group.terms.size(); ti++) {
            LogicDraft.Term term = group.terms.get(ti);
            int x = gx;
            x = segment(pieces, x, y + 2, "editor.historystages.logic.is_unlocked", term.unlocked,
                    new Hit.SetTermUnlocked(gi, ti, true));
            x = segment(pieces, x, y + 2, "editor.historystages.logic.is_not_unlocked", !term.unlocked,
                    new Hit.SetTermUnlocked(gi, ti, false)) + 6;

            TermInfo info = describe(term, ctx);
            int checkW = 14;
            int fieldW = Math.max(40, gx + gw - ICON_W - GAP - checkW - x);
            pieces.add(new Piece(x, y, fieldW, ROW_H - 2, Kind.FIELD, info.label, info.color,
                    new Hit.PickStage(gi, ti)));
            if (info.met != null) {
                pieces.add(new Piece(x + fieldW + 4, y + 4, checkW, 8, Kind.TEXT,
                        info.met ? "✔" : "–", info.met ? OK : DIM, null));
            }
            pieces.add(new Piece(gx + gw - ICON_W, y + 3, ICON_W, 10, Kind.ICON, "✕", DIM,
                    new Hit.RemoveTerm(gi, ti)));
            y += ROW_H;
            if (info.warning != null) {
                for (var line : font.split(Component.literal(info.warning), gw)) {
                    pieces.add(new Piece(gx, y, gw, 8, Kind.TEXT, plain(line), WARN, null));
                    y += 10;
                }
            }
        }

        String add = Component.translatable("editor.historystages.logic.add_term").getString();
        pieces.add(new Piece(gx, y, font.width(add) + 12, 12, Kind.ADD, add, DIM, new Hit.AddTerm(gi)));
        y += 16;

        pieces.set(panelIndex, new Piece(PAD, top, inner, y - top, Kind.PANEL, "", GROUP_BG, null));
        return y + 2;
    }

    private int segment(List<Piece> pieces, int x, int y, String key, boolean on, Hit hit) {
        String text = Component.translatable(key).getString();
        int w = font.width(text) + 8;
        pieces.add(new Piece(x, y, w, 12, on ? Kind.SEG_ON : Kind.SEG_OFF, text, on ? 0xFF111111 : 0xFFAAAAAA,
                on ? null : hit));
        return x + w;
    }

    private record TermInfo(String label, int color, Boolean met, String warning) {}

    private TermInfo describe(LogicDraft.Term term, Context ctx) {
        if (term.stageId == null || term.stageId.isBlank()) {
            return new TermInfo(Component.translatable("editor.historystages.logic.pick_stage").getString(),
                    0xFF555555, null, null);
        }
        StageScope scope = term.scope != null ? term.scope : ctx.owner();
        // Only an individual stage can mix both kinds, so only there does a term say which it is.
        String suffix = ctx.owner() == StageScope.INDIVIDUAL
                ? " " + Component.translatable(scope == StageScope.GLOBAL
                        ? "editor.historystages.logic.scope_tag.global"
                        : "editor.historystages.logic.scope_tag.individual").getString()
                : "";
        if (ctx.owner() == StageScope.GLOBAL && scope == StageScope.INDIVIDUAL) {
            return new TermInfo(term.stageId, WARN, null,
                    Component.translatable("editor.historystages.logic.warn.scope", term.stageId).getString());
        }
        StageEntry entry = (scope == StageScope.GLOBAL ? ctx.globalStages() : ctx.individualStages()).get(term.stageId);
        if (entry == null) {
            return new TermInfo("? " + term.stageId + suffix, WARN, null,
                    Component.translatable("editor.historystages.logic.warn.missing", term.stageId).getString());
        }
        boolean unlocked = (scope == StageScope.GLOBAL ? ctx.global() : ctx.individual()).isUnlocked(term.stageId);
        return new TermInfo(entry.getDisplayName() + suffix, TEXT, unlocked == term.unlocked, null);
    }

    private static int accent(LogicDraft.Block block) {
        if (LogicBlockTypes.BLOCKED_WHILE.equals(block.type)) return BLOCKED_ACCENT;
        if (LogicBlockTypes.HIDDEN_WHILE.equals(block.type)) return 0xFFAA77FF;
        if (LogicBlockTypes.COST_WHILE.equals(block.type)) return 0xFF55CCAA;
        if (LogicBlockTypes.REVOKE_WHEN.equals(block.type)) return 0xFFFFAA00;
        return 0xFFAAAAAA;
    }

    public static String typeTitleKey(String type) {
        return LogicBlockTypes.isKnown(type)
                ? "editor.historystages.logic.type." + type
                : "editor.historystages.logic.type.unknown";
    }

    private static String plain(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder out = new StringBuilder();
        seq.accept((i, style, cp) -> {
            out.appendCodePoint(cp);
            return true;
        });
        return out.toString();
    }

    // ---- drawing ---------------------------------------------------------------------

    public void render(GuiGraphics g, Built built, LogicDraft.Block block, int x, int y, int mouseX, int mouseY) {
        g.fill(x, y, x + built.width(), y + built.height(), CARD_BORDER);
        g.fill(x + 1, y + 1, x + built.width() - 1, y + built.height() - 1, CARD_BG);
        g.fill(x, y, x + 3, y + built.height(), accent(block));

        for (Piece p : built.pieces()) {
            int px = x + p.x();
            int py = y + p.y();
            boolean hovered = p.hit() != null && inside(p, mouseX - x, mouseY - y);
            switch (p.kind()) {
                case PANEL -> {
                    g.fill(px, py, px + p.w(), py + p.h(), 0xFF2A2A2A);
                    g.fill(px + 1, py + 2, px + p.w() - 1, py + p.h() - 1, p.color());
                    g.fill(px, py, px + p.w(), py + 2, GOLD);
                }
                case TITLE, TEXT -> g.drawString(font, p.text(), px, py, p.color(), false);
                case PILL -> {
                    g.fill(px, py, px + p.w(), py + p.h(), p.color());
                    g.drawString(font, p.text(), px + 3, py + 2, 0xFFFFFFFF, false);
                }
                case SEG_ON, SEG_OFF -> {
                    boolean on = p.kind() == Kind.SEG_ON;
                    g.fill(px, py, px + p.w(), py + p.h(), 0xFF555555);
                    g.fill(px + 1, py + 1, px + p.w() - 1, py + p.h() - 1,
                            on ? GOLD : (hovered ? 0xFF3A3A3A : 0xFF222222));
                    g.drawString(font, p.text(), px + 4, py + 2, on ? 0xFF111111 : (hovered ? 0xFFFFFFFF : p.color()), false);
                }
                case FIELD -> {
                    g.fill(px - 1, py - 1, px + p.w() + 1, py + p.h() + 1, hovered ? FIELD_BORDER_HOVER : FIELD_BORDER);
                    g.fill(px, py, px + p.w(), py + p.h(), FIELD_BG);
                    g.drawString(font, fit(p.text(), p.w() - 8), px + 4, py + (p.h() - 8) / 2, p.color(), false);
                }
                case ICON -> g.drawString(font, p.text(), px + 1, py + 2,
                        hovered ? (p.text().equals("✕") ? 0xFFFF6666 : GOLD) : p.color(), false);
                case ADD -> {
                    g.fill(px, py, px + p.w(), py + p.h(), hovered ? 0x40FFCC00 : 0x20FFFFFF);
                    g.drawString(font, p.text(), px + 6, py + 2, hovered ? GOLD : p.color(), false);
                }
            }
        }
    }

    private String fit(String text, int room) {
        if (font.width(text) <= room) return text;
        int ellipsis = font.width("...");
        if (room <= ellipsis) return "";
        return font.plainSubstrByWidth(text, room - ellipsis) + "...";
    }

    // ---- clicks ----------------------------------------------------------------------

    public Hit hitTest(Built built, int x, int y, double mouseX, double mouseY) {
        for (Piece p : built.pieces()) {
            if (p.hit() != null && inside(p, mouseX - x, mouseY - y)) return p.hit();
        }
        return null;
    }

    private static boolean inside(Piece p, double lx, double ly) {
        return lx >= p.x() && lx < p.x() + p.w() && ly >= p.y() && ly < p.y() + p.h();
    }
}
