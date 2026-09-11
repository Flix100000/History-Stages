package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.data.lock.ZoneGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Tints the screen while the player stands inside a locked zone that asked for it.
 *
 * <p>Answered from the shapes the client already has for the force field, so the tint and the wall
 * can never disagree about where the zone is.
 *
 * <p>The zone decides <em>whether</em> there is a tint, through its own {@code show_overlay};
 * the one setting decides how strong, with nought meaning none. There is deliberately no second
 * switch in the config for turning it on and off - that question already has an owner, and two
 * owners for one question is how a setting ends up quietly doing nothing.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ZoneOverlayRenderer {

    private ZoneOverlayRenderer() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (ClientZoneShapes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        if (mc.options.hideGui || mc.level == null) return;

        double opacity = Config.VISUAL.zoneLockOverlayOpacity.get();
        if (opacity <= 0.0) return;

        if (!insideAnOverlayZone(player, mc.level.getMinBuildHeight(), mc.level.getMaxBuildHeight())) {
            return;
        }

        int alpha = (int) Math.round(Math.min(1.0, opacity) * 255.0);
        // ARGB: GuiGraphics#fill takes 0xAARRGGBB.
        int colour = (alpha << 24) | 0x00C00000;

        GuiGraphics gg = event.getGuiGraphics();
        gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), colour);
    }

    /**
     * Once is enough, however many zones overlap.
     *
     * <p>Stacking the tint would make two zones twice as dark and three nearly opaque, which says
     * nothing extra and hides the game.
     */
    private static boolean insideAnOverlayZone(LocalPlayer player, int minY, int maxY) {
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());

        for (ClientZoneShapes.Visible zone : ClientZoneShapes.get()) {
            if (!zone.overlay()) continue;
            // An inverted zone's shapes mark the allowed area, so being in them is the one case
            // where nothing should be tinted.
            if (ZoneGeometry.containsAny(zone.shapes(), x, y, z, minY, maxY) != zone.inverted()) {
                return true;
            }
        }
        return false;
    }
}
