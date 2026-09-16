package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Opens and closes the windows in which an addon may give its category an editor tab and its
 * auto-trigger type a way to be authored.
 *
 * <p>Client-only by annotation, so nothing here is ever loaded on a dedicated server — the editor
 * classes this reaches are pure UI, and pulling them onto the server is how the crash fixed in
 * commit 0469f73 happened.
 *
 * <p>Client setup runs after common setup, so the lock categories themselves are already
 * registered and frozen by the time an editor can be attached to one.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientCategoryEditorSetup {

    private ClientCategoryEditorSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModLoader.get().postEvent(new RegisterCategoryEditorsEvent());
            CategoryEditors.freeze();

            ModLoader.get().postEvent(new net.bananemdnsa.historystages.client.editor.trigger
                    .RegisterTriggerEditorsEvent());
            net.bananemdnsa.historystages.client.editor.trigger.TriggerEditors.freeze();
        });
    }
}
