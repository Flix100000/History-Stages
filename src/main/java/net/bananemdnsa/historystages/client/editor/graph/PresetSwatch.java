package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.data.graph.NodeState;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A style preset drawn as a small node, for the preset lists. Goes through {@link NodeShapes}
 * like the graph and the style editor's preview, so a swatch cannot drift from the real thing.
 */
public final class PresetSwatch {

    private PresetSwatch() {}

    /** The preset's unlocked look, scaled by its size within {@code r} as an upper bound. */
    public static void draw(GuiGraphics g, String presetId, int cx, int cy, int r) {
        ResolvedStyle style = StageGraphConfig.styleForPreset(presetId, NodeState.UNLOCKED);
        int radius = Math.max(3, Math.min(r, Math.round(r * 0.8f * (float) style.size())));
        NodeShapes.draw(g, style.shape(), cx, cy, radius,
                style.fillArgb(), style.border(), Math.max(1, Math.min(2, style.borderWidth())));
    }
}
