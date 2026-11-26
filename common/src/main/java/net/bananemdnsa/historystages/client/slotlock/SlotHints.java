package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.client.display.BlockedLines;
import net.bananemdnsa.historystages.client.display.RequiredStageLines;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLine;
import net.bananemdnsa.historystages.data.tooltip.SlotTooltipLayout;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The tooltip of a locked slot, built from the configurable {@link SlotTooltipLayout}: title,
 * the pack's hint, and the "required" header with its stage list.
 */
public final class SlotHints {

    /** One "required" block: its header lang key and the stage lines under it. */
    public record RequiredBlock(String headerKey, Function<ChatFormatting, List<Component>> bullets) {}

    private SlotHints() {}

    public static List<Component> lines(SlotLocks.Info info) {
        String hint = info.entry() != null ? info.entry().hint() : "";
        List<RequiredBlock> blocks = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        addBlock(blocks, missing, StageScope.GLOBAL, info.globalGating(), ClientStageStates.global(),
                "tooltip.historystages.required_progress");
        addBlock(blocks, missing, StageScope.INDIVIDUAL, info.individualGating(), ClientStageStates.individual(),
                "tooltip.historystages.required_individual_progress");
        return build(SlotTooltipLayout.active(), hint, String.join(", ", missing), blocks);
    }

    private static void addBlock(List<RequiredBlock> blocks, List<String> missing, StageScope scope,
                                 List<String> gating, StageStateView state, String headerKey) {
        if (gating.isEmpty() || !LockResolution.isLocked(scope, gating, state)) return;
        boolean individual = scope == StageScope.INDIVIDUAL;
        for (String id : LockResolution.missingStages(scope, gating, state)) {
            missing.add(BlockedLines.displayName(id, individual));
        }
        blocks.add(new RequiredBlock(headerKey, bullet -> RequiredStageLines.lines(scope, gating, state,
                Config.VISUAL.slotShowAllUntilComplete.get(), Config.VISUAL.groupInterchangeableStages.get(), bullet,
                id -> Component.literal(BlockedLines.displayName(id, individual)))));
    }

    /**
     * The lines for a layout. Shared by the real tooltip and the editor's preview, which passes
     * made-up stages, so the preview can never drift from what players see.
     */
    public static List<Component> build(List<ScrollTooltipLine> layout, String hint, String stages,
                                        List<RequiredBlock> required) {
        Map<String, String> vars = Map.of("hint", hint, "stages", stages);
        List<Component> out = new ArrayList<>();
        ScrollTooltipLine stageColor = layout.stream()
                .filter(l -> l.id().equals(SlotTooltipLayout.STAGE_COLOR_ID)).findFirst()
                .orElse(SlotTooltipLayout.defaultLine(SlotTooltipLayout.STAGE_COLOR_ID));

        for (ScrollTooltipLine line : layout) {
            if (!line.enabled() || !SlotTooltipLayout.MOVABLE_IDS.contains(line.id())) continue;
            List<Component> part = switch (line.id()) {
                case "title" -> simple(line, "tooltip.historystages.slot_locked", vars, ChatFormatting.RED);
                case "hint" -> hint.isEmpty() ? List.of()
                        : line.text().isEmpty() ? List.of(applyStyle(Component.literal(hint), line.style(), ChatFormatting.GRAY))
                        : styledLines(ScrollTooltipLayout.fill(line.text(), vars), line.style(), ChatFormatting.GRAY);
                case "required" -> requiredLines(line, required, stageColor.text(), vars);
                default -> List.of();
            };
            if (part.isEmpty()) continue;
            if (line.spacerBefore() && !out.isEmpty()) out.add(Component.empty());
            out.addAll(part);
        }
        return out;
    }

    private static List<Component> requiredLines(ScrollTooltipLine line, List<RequiredBlock> blocks,
                                                 String stageColor, Map<String, String> vars) {
        List<Component> out = new ArrayList<>();
        ChatFormatting named = net.bananemdnsa.historystages.util.Formats.byName(stageColor);
        boolean hex = GraphColors.isValid(stageColor);
        for (RequiredBlock block : blocks) {
            out.addAll(line.text().isEmpty()
                    ? List.of(applyStyle(Component.translatable(block.headerKey()), line.style(), ChatFormatting.DARK_RED))
                    : styledLines(ScrollTooltipLayout.fill(line.text(), vars), line.style(), ChatFormatting.DARK_RED));
            List<Component> bullets = block.bullets().apply(named != null && net.bananemdnsa.historystages.util.Formats.colorOf(named) != null ? named : ChatFormatting.GOLD);
            for (Component bullet : bullets) {
                out.add(hex ? bullet.copy().withStyle(s -> s.withColor(TextColor.fromRgb(GraphColors.parse(stageColor, 0))))
                        : bullet);
            }
        }
        return out;
    }

    private static List<Component> simple(ScrollTooltipLine line, String langKey, Map<String, String> vars,
                                          ChatFormatting fallback) {
        if (line.text().isEmpty()) return List.of(applyStyle(Component.translatable(langKey), line.style(), fallback));
        return styledLines(ScrollTooltipLayout.fill(line.text(), vars), line.style(), fallback);
    }

    /** A custom text may hold line breaks; each becomes its own tooltip line. Codes are {@code &}. */
    private static List<Component> styledLines(String text, String style, ChatFormatting fallback) {
        List<Component> out = new ArrayList<>();
        for (String part : text.split("\n", -1)) {
            out.add(applyStyle(Component.literal(part.replace('&', '§')), style, fallback));
        }
        return out;
    }

    /** Same rules as the scroll tooltip: {@code #RRGGBB} or formatting names joined by {@code +}. */
    private static MutableComponent applyStyle(MutableComponent text, String style, ChatFormatting fallback) {
        List<String> tokens = ScrollTooltipLayout.styleTokens(style);
        if (tokens.isEmpty()) return text.withStyle(fallback);
        Style result = Style.EMPTY;
        boolean any = false;
        for (String token : tokens) {
            if (GraphColors.isValid(token)) {
                result = result.withColor(TextColor.fromRgb(GraphColors.parse(token, 0)));
                any = true;
                continue;
            }
            ChatFormatting format = net.bananemdnsa.historystages.util.Formats.byName(token);
            if (format == null) continue;
            result = result.applyFormat(format);
            any = true;
        }
        return any ? text.setStyle(result) : text.withStyle(fallback);
    }
}
