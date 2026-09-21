package net.bananemdnsa.historystages;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.compat.LockInterceptors;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.config.LegacyConfigMigration;
import net.bananemdnsa.historystages.data.graph.GraphConfigMigration;
import net.bananemdnsa.historystages.events.AutoTriggerEventBridge;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModCreativeTabs;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.bananemdnsa.historystages.init.ModRecipes;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.platform.AddonRegistrations;
import net.bananemdnsa.historystages.platform.ConfigFiles;
import net.bananemdnsa.historystages.platform.EventSources;
import net.bananemdnsa.historystages.platform.Handlers;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * Everything this mod does at startup, in the one order that works.
 *
 * <p>On the other loader most of this is spread across bus listeners and the loader decides when
 * each runs. Here it is a single method, which means the order is visible — and it has to be,
 * because several steps only work in one place:
 *
 * <ul>
 *   <li>The two config migrations capture <em>before</em> anything is loaded, and in that order:
 *       the second renames the old files once it has copied them, and the graph block lives in
 *       the same file. Reading it after the rename would cost the pack its whole stage graph.
 *   <li>Blocks are registered before items, because the item for a pedestal wraps the block. The
 *       mod bus used to sort that out; here the order is the sorting.
 *   <li>Stages are loaded before the addon window opens, and the window opens before anything
 *       plays, because every registry it fills is closed on the way out.
 * </ul>
 */
public class HistoryStagesFabric implements ModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        // Before the config is loaded, and in this order. See the class comment.
        GraphConfigMigration.capture();
        LegacyConfigMigration.capture();

        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModMenuTypes.register();
        ModRecipes.register();
        ModCreativeTabs.register();

        ConfigFiles.loadAll();
        ConfigHandler.setupConfig();
        StageManager.load();

        // What a pipe or a tank sees of the pedestal. Vanilla hoppers reach it through the block
        // entity being a Container; this is the other half, for everything that goes through the
        // transfer API instead. After the block entity type exists, necessarily.
        ItemStorage.SIDED.registerForBlockEntity(
                (pedestal, direction) -> InventoryStorage.of(pedestal.getItemHandler(), direction),
                ModBlockEntities.RESEARCH_PEDESTAL_BE.get());

        PacketHandler.register();
        EventSources.register();

        Handlers.registerCommon();
        // Two handlers that keep state, so the object is registered rather than the class.
        EventBus.register(new HistoryStages());
        EventBus.register(new AutoTriggerEventBridge());

        // The window in which other mods may add to this one. After the stages are loaded, since
        // nothing upstream of this point needs a category to exist, and before anything plays.
        AddonRegistrations.run();

        optionalIntegrations();

        // Per-mod adapters for custom actions that bypass the ordinary interaction events.
        LockInterceptors.init();
    }

    /**
     * Each of these is wrapped on its own: an integration that fails should cost its own mod's
     * support and nothing else. They are tried by mod id rather than by catching a missing class,
     * so a genuine error inside one is still reported instead of looking like an absent mod.
     */
    private static void optionalIntegrations() {
        integrate("ftbquests", "FTB Quests",
                net.bananemdnsa.historystages.compat.ftbquests.FTBQuestsIntegration::init);
        integrate("accessories", "Accessories",
                net.bananemdnsa.historystages.events.lock.AccessoriesEquipLockHandler::register);
        // No scripting integration on this loader. KubeJS ships no fabric build for 1.21.1 and
        // CraftTweaker none at all, so there is nothing for a script bridge to talk to; both, and
        // the shared facade behind them, are on neoforge-1.21.X when one of them arrives.
        // Curios is likewise neoforge-only here — Accessories above is what fabric packs use.
    }

    private static void integrate(String modId, String name, Runnable setup) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            return;
        }
        try {
            setup.run();
            LOGGER.info("[HistoryStages] {} integration loaded.", name);
        } catch (Exception e) {
            LOGGER.error("[HistoryStages] Failed to load the {} integration.", name, e);
        }
    }
}
