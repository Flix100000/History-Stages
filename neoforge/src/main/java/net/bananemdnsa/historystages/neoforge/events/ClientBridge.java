package net.bananemdnsa.historystages.neoforge.events;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client-only events. Never loaded on a dedicated server: every type in here pulls in
 * {@code net.minecraft.client}.
 */
public final class ClientBridge {

    private ClientBridge() {}

    public static void register(IEventBus bus) {
        Bridges.listen(bus, ClientPlayerNetworkEvent.LoggingOut.class, ClientBridge::loggingOut);
        Bridges.listen(bus, ClientTickEvent.Post.class, ClientBridge::tickPost);
        Bridges.listen(bus, RecipesReceivedEvent.class, ClientBridge::recipesReceived);
        Bridges.listen(bus, RegisterClientCommandsEvent.class, ClientBridge::registerClientCommands);
        Bridges.listen(bus, RenderGuiEvent.Post.class, ClientBridge::renderGuiPost);
        Bridges.listen(bus, RenderLevelStageEvent.AfterTranslucentFeatures.class, ClientBridge::beforeTranslucentBlocks);
        Bridges.listen(bus, RenderLevelStageEvent.AfterTranslucentBlocks.class, ClientBridge::afterTranslucentBlocks);
        Bridges.listen(bus, ScreenEvent.Init.Post.class, ClientBridge::screenInitPost);
        Bridges.listen(bus, ScreenEvent.Render.Post.class, ClientBridge::screenRenderPost);
        Bridges.listenCancellable(bus, ScreenEvent.MouseButtonPressed.Pre.class, ClientBridge::mousePressedPre);
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.ClientPlayerNetworkEvent.LoggingOut.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.ClientPlayerNetworkEvent.LoggingOut(
                    event.getPlayer()), priority);
        }
    }

    private static void tickPost(ClientTickEvent.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.ClientTickEvent.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.ClientTickEvent.Post(), priority);
        }
    }

    /** NeoForge's counterpart of the platform {@code RecipesUpdatedEvent}. */
    private static void recipesReceived(RecipesReceivedEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.RecipesUpdatedEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.RecipesUpdatedEvent(
                    event.getRecipeMap()), priority);
        }
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.RegisterClientCommandsEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.RegisterClientCommandsEvent(
                    event.getDispatcher()), priority);
        }
    }

    private static void renderGuiPost(RenderGuiEvent.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.RenderGuiEvent.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.RenderGuiEvent.Post(
                    event.getGuiGraphics()), priority);
        }
    }

    /** The overlays draw on either side of the translucent terrain; see the platform event. */
    private static void beforeTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentFeatures event, EventPriority priority) {
        var camera = event.getLevelRenderState().cameraRenderState;
        postRenderStage(net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent.Stage.BEFORE_TRANSLUCENT, camera.pos, camera.orientation, priority);
    }

    private static void afterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event, EventPriority priority) {
        var camera = event.getLevelRenderState().cameraRenderState;
        postRenderStage(net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT, camera.pos, camera.orientation, priority);
    }

    private static void postRenderStage(net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent.Stage stage, net.minecraft.world.phys.Vec3 pos,
                                        org.joml.Quaternionfc orientation, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent(stage, pos, orientation), priority);
        }
    }

    private static void screenInitPost(ScreenEvent.Init.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.ScreenEvent.Init.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.ScreenEvent.Init.Post(
                    event.getScreen(), event::addListener), priority);
        }
    }

    private static void screenRenderPost(ScreenEvent.Render.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.ScreenEvent.Render.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.client.ScreenEvent.Render.Post(
                    event.getScreen(), event.getGuiGraphics()), priority);
        }
    }

    private static void mousePressedPre(ScreenEvent.MouseButtonPressed.Pre event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.client.ScreenEvent.MouseButtonPressed.Pre.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.client.ScreenEvent.MouseButtonPressed.Pre(
                    event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton()), priority);
        }
    }
}
