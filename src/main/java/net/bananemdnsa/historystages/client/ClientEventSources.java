package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.ClientPlayerNetworkEvent;
import net.bananemdnsa.historystages.platform.event.client.RegisterClientCommandsEvent;
import net.bananemdnsa.historystages.platform.event.client.RenderGuiEvent;
import net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent;
import net.bananemdnsa.historystages.platform.event.client.ScreenEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

/**
 * The events that only exist on a client.
 *
 * <p>Its own class for the same reason as the client packet registration: a dedicated server has
 * none of these callbacks, and a class naming them must not be loaded there.
 */
@Environment(EnvType.CLIENT)
public final class ClientEventSources {

    private ClientEventSources() {}

    public static void register() {
        // The list handed over is the live one, which is what the handlers expect: they add lines
        // to it in place rather than answering with a new list.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                EventBus.post(new ItemTooltipEvent(stack, lines)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                EventBus.post(new ClientPlayerNetworkEvent.LoggingOut(client.player)));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            EventBus.post(new RegisterClientCommandsEvent(dispatcher));
            // Kept so the command tree can be repaired once the server's own arrives; see
            // ClientCommandTree for what the loader's copy leaves behind.
            ClientCommandTree.remember(dispatcher);
        });

        // After the translucent pass, which is the only stage this mod draws in: a wall you can
        // see through has to come after the blocks it is drawn over.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                EventBus.post(new RenderLevelStageEvent(
                        RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS,
                        context.matrixStack(), context.camera())));

        HudRenderCallback.EVENT.register((graphics, tickDelta) ->
                EventBus.post(new RenderGuiEvent.Post(graphics)));

        screens();

    }

    /**
     * The three screen events. Two of them are per screen rather than global, so they are hooked
     * while each screen is being built — which is also the moment a widget may be added to it.
     */
    private static void screens() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            EventBus.post(new ScreenEvent.Init.Post(screen, listener -> {
                if (listener instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    Screens.getButtons(screen).add(widget);
                }
            }));

            ScreenEvents.afterRender(screen).register((rendered, graphics, mouseX, mouseY, delta) ->
                    EventBus.post(new ScreenEvent.Render.Post(rendered, graphics)));

            ScreenMouseEvents.allowMouseClick(screen).register((clicked, mouseX, mouseY, button) -> {
                ScreenEvent.MouseButtonPressed.Pre event = EventBus.post(
                        new ScreenEvent.MouseButtonPressed.Pre(clicked, mouseX, mouseY, button));
                // Answering false is what stops the screen seeing the click at all.
                return !event.isCanceled();
            });
        });
    }
}
