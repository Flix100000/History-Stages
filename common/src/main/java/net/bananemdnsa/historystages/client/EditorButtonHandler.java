package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageGraphScreen;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.event.client.ScreenEvent;

@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public class EditorButtonHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = event.getScreen().width;

        // The button is a shortcut, not the only door: '/history editor' opens the same
        // screen behind the same permission check, so hiding it costs an operator nothing.
        if (mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && Config.VISUAL.showEditorButton.get()) {
            event.addListener(Button.builder(
                    Component.translatable("editor.historystages.title"),
                    btn -> mc.gui.setScreen(new StageOverviewScreen())
            ).bounds(screenWidth - 110, 5, 100, 20).build());
        }

        if (GraphConfig.GRAPH.enabled.get()) {
            event.addListener(Button.builder(
                    Component.translatable("graph.historystages.button"),
                    btn -> mc.gui.setScreen(StageGraphScreen.forPlayer(mc.gui.screen()))
            ).bounds(screenWidth - 110, 29, 100, 20).build());
        }
    }
}
