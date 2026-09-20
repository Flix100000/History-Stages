package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageGraphScreen;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.event.client.RegisterClientCommandsEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
/**
 * The two screens that never need the server: {@code /history editor} and {@code /history graph}
 * only swap the local screen, so they sit on the client dispatcher next to
 * {@link ClientDebugCommand} rather than on {@code StageCommand}.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class ClientScreenCommand {

    private ClientScreenCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("history")
                .then(ClientCommandManager.literal("editor")
                        .executes(ctx -> openEditor(ctx.getSource())))
                .then(ClientCommandManager.literal("graph")
                        .executes(ctx -> openGraph(ctx.getSource()))));
    }

    private static int openEditor(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            source.sendError(Component.translatable("command.historystages.player_only"));
            return 0;
        }
        mc.tell(() -> mc.setScreen(new StageOverviewScreen()));
        return 1;
    }

    private static int openGraph(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            source.sendError(Component.translatable("command.historystages.player_only"));
            return 0;
        }
        // The same door the pause-screen button is: with the graph switched off players have none.
        // An operator keeps it either way, so a pack author can look at the player view before
        // turning it on for everyone. The check sits here rather than in a requires() because
        // requires() is evaluated once while the command tree is merged, and at that point the
        // server has not necessarily pushed graph.toml to us yet.
        if (!GraphConfig.GRAPH.enabled.get() && !source.hasPermission(2)) {
            source.sendError(Component.translatable("command.historystages.graph_disabled"));
            return 0;
        }
        // By the time this runs the chat has closed, so the parent is null and ESC drops straight
        // back into the world instead of the pause screen the button opens it from.
        mc.tell(() -> mc.setScreen(StageGraphScreen.forPlayer(mc.screen)));
        return 1;
    }
}
