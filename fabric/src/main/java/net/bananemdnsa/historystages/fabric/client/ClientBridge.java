package net.bananemdnsa.historystages.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.ClientPlayerNetworkEvent;
import net.bananemdnsa.historystages.platform.event.client.ClientTickEvent;
import net.bananemdnsa.historystages.platform.event.client.RecipesUpdatedEvent;
import net.bananemdnsa.historystages.platform.event.client.RenderGuiEvent;
import net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent;
import net.bananemdnsa.historystages.platform.event.client.ScreenEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * The client-only events. Only reached from the client entrypoint: every type in here pulls in
 * {@code net.minecraft.client}, which a dedicated server does not have.
 */
public final class ClientBridge {

    private ClientBridge() {}

    public static void register() {
        // The list is the live tooltip: handlers add and remove lines in place.
        Phases.listen(ItemTooltipCallback.EVENT, priority -> (stack, context, flag, lines) -> {
            if (Phases.wanted(ItemTooltipEvent.class, priority)) {
                EventBus.post(new ItemTooltipEvent(stack, lines), priority);
            }
        });
        Phases.listen(ClientTickEvents.END_CLIENT_TICK, priority -> client -> {
            if (Phases.wanted(ClientTickEvent.Post.class, priority)) {
                EventBus.post(new ClientTickEvent.Post(), priority);
            }
        });
        Phases.listen(ClientPlayConnectionEvents.DISCONNECT, priority -> (handler, client) -> {
            if (Phases.wanted(ClientPlayerNetworkEvent.LoggingOut.class, priority)) {
                EventBus.post(new ClientPlayerNetworkEvent.LoggingOut(client.player), priority);
            }
        });

        // Fabric's counterpart of NeoForge's RecipesReceivedEvent: both only carry the recipe
        // types some mod asked the server to send (Fabric: RecipeSynchronization).
        Phases.listen(ClientRecipeSynchronizedEvent.EVENT, priority -> (client, recipes) -> {
            if (Phases.wanted(RecipesUpdatedEvent.class, priority)) {
                EventBus.post(new RecipesUpdatedEvent(RecipeMap.create(recipes.recipes())), priority);
            }
        });

        ClientCommandBridge.register();

        // After the translucent terrain, the stage this mod draws its see-through walls in.
        Phases.listen(LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN, priority -> context -> {
            if (!Phases.wanted(RenderLevelStageEvent.class, priority)) {
                return;
            }
            PoseStack poseStack = context.poseStack() != null ? context.poseStack() : new PoseStack();
            EventBus.post(new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS,
                    poseStack, Minecraft.getInstance().gameRenderer.mainCamera()), priority);
        });

        // A HUD element is a layer, not a callback: all priorities in one go, drawn last.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "platform_hud"),
                (graphics, delta) -> {
                    if (Phases.wanted(RenderGuiEvent.Post.class)) {
                        EventBus.post(new RenderGuiEvent.Post(graphics));
                    }
                });

        screens();
    }

    /**
     * Two of the screen events are per screen in Fabric, so they are hooked while each screen is
     * built, once per init and not once per priority.
     */
    private static void screens() {
        Phases.listen(ScreenEvents.AFTER_INIT, priority -> (client, screen, width, height) -> {
            if (Phases.wanted(ScreenEvent.Init.Post.class, priority)) {
                EventBus.post(new ScreenEvent.Init.Post(screen, listener -> addWidget(screen, listener)), priority);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            Phases.listen(ScreenEvents.afterExtract(screen), priority -> (rendered, graphics, mouseX, mouseY, delta) -> {
                if (Phases.wanted(ScreenEvent.Render.Post.class, priority)) {
                    EventBus.post(new ScreenEvent.Render.Post(rendered, graphics), priority);
                }
            });
            // Answering false is what keeps the click from the screen.
            Phases.listen(ScreenMouseEvents.allowMouseClick(screen), priority -> (clicked, click) -> {
                if (!Phases.wanted(ScreenEvent.MouseButtonPressed.Pre.class, priority)) {
                    return true;
                }
                return !Phases.postCancellable(
                        new ScreenEvent.MouseButtonPressed.Pre(clicked, click.x(), click.y(), click.button()), priority);
            });
        });
    }

    /**
     * Fabric only exposes the widget list. Anything that is not a widget has no place to go here;
     * the handlers on main only add buttons.
     */
    private static void addWidget(Screen screen, net.minecraft.client.gui.components.events.GuiEventListener listener) {
        if (listener instanceof AbstractWidget widget) {
            Screens.getWidgets(screen).add(widget);
        }
    }
}
