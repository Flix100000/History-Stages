package net.bananemdnsa.historystages;

import net.bananemdnsa.historystages.client.ClientHandlers;
import net.bananemdnsa.historystages.data.config.ConfigFiles;
import net.bananemdnsa.historystages.events.Handlers;
import net.bananemdnsa.historystages.network.ModPayloads;
import net.bananemdnsa.historystages.platform.Services;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.init.*;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.TriState;
import net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent;
import net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.bananemdnsa.historystages.platform.event.level.LevelEvent;
import net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent;
import net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;

public class HistoryStages {
    public static final String MOD_ID = "historystages";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Identifier location(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private HistoryStages() {
    }

    /**
     * What the NeoForge constructor did, for both loaders. Each loader's entry class calls this
     * while the mod is being constructed (NeoForge needs the registers then). What only one loader
     * has (capabilities, menu screens, item decorations) stays in that loader's entry class.
     */
    public static void init() {
        // Must run before either config spec is loaded below — see the class comment on
        // GraphConfigMigration for why capture and apply are two separate steps.
        net.bananemdnsa.historystages.data.graph.GraphConfigMigration.capture();
        // Second, never first: this one renames the old files once it has written their contents
        // into the new ones, and the graph block lives in the same common file. Reading it after
        // the rename would cost the pack its whole stage graph.
        net.bananemdnsa.historystages.data.config.LegacyConfigMigration.capture();

        // Blocks before items: the pedestal item wraps the block.
        ModBlocks.register();
        ModItems.register();
        ModCreativeTabs.register();
        ModBlockEntities.register();
        ModMenuTypes.register();
        ModRecipes.register();

        // Same files and keys as the three COMMON registrations on main, see ConfigFiles.
        ConfigFiles.loadAll();

        ConfigHandler.setupConfig();
        StageManager.load();

        ModPayloads.register();
        Handlers.registerCommon();
        if (Services.isClient()) {
            ClientHandlers.register();
        }
        // Other mods' platform @EventBusSubscriber classes (NeoForge scan data); a no-op on
        // Fabric, where addons come in through the historystages entrypoint below.
        net.bananemdnsa.historystages.platform.AddonEntrypoints.discoverSubscribers();

        // Registration window for addon lock categories: see registrationWindow(). The loaders
        // call it, because NeoForge has to queue it onto the main thread itself.

        // PORT(later): FTB Teams/Quests, Accessories, KubeJS and CraftTweaker have no 26.2 build yet,
        // and Curios moves with Task 13 into the NeoForge module. Kept as on main, not compiled.
        /*
        // FTB Teams on its own, without FTB Quests, is a common pack setup, so it gets its own check.
        if (Services.isModLoaded("ftbteams")) {
            try {
                net.bananemdnsa.historystages.compat.ftbteams.FTBTeamsIntegration.init(modEventBus);
                LOGGER.info("[HistoryStages] FTB Teams integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load FTB Teams integration.", e);
            }
        }

        // Conditional FTB Quests integration
        if (Services.isModLoaded("ftbquests")) {
            try {
                net.bananemdnsa.historystages.compat.ftbquests.FTBQuestsIntegration.init();
                LOGGER.info("[HistoryStages] FTB Quests integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load FTB Quests integration.", e);
            }
        }

        if (Services.isModLoaded("curios")) {
            try {
                EventBus.register(net.bananemdnsa.historystages.events.lock.CuriosEquipLockHandler.class);
                LOGGER.info("[HistoryStages] Curios integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load Curios integration.", e);
            }
        }

        if (Services.isModLoaded("accessories")) {
            try {
                net.bananemdnsa.historystages.events.lock.AccessoriesEquipLockHandler.register();
                LOGGER.info("[HistoryStages] Accessories integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load Accessories integration.", e);
            }
        }

        // Script bridges. Both mods find their own entry point — KubeJS through
        // kubejs.plugins.txt, CraftTweaker by scanning for @ZenRegister — so all that is needed
        // here is the NeoForge-side wiring that turns StageEvent into something scripts hear.
        if (Services.isModLoaded("kubejs")) {
            try {
                net.bananemdnsa.historystages.compat.kubejs.StageEventForwarder.register(NeoForge.EVENT_BUS);
                LOGGER.info("[HistoryStages] KubeJS integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load KubeJS integration.", e);
            }
        }

        if (Services.isModLoaded("crafttweaker")) {
            try {
                net.bananemdnsa.historystages.compat.crafttweaker.CTScriptReloadHook.register(NeoForge.EVENT_BUS);
                LOGGER.info("[HistoryStages] CraftTweaker integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load CraftTweaker integration.", e);
            }
        }
        */

        // Optional per-mod lock adapters (custom actions that bypass vanilla interaction events).
        net.bananemdnsa.historystages.compat.LockInterceptors.init();

        EventBus.register(new HistoryStages());
        EventBus.register(new net.bananemdnsa.historystages.events.AutoTriggerEventBridge());
    }

    private static volatile String registrationThread;

    /**
     * Registration window for addon lock categories, at the point main used: once every mod
     * has been constructed (FMLCommonSetupEvent), before anything plays. Every registry it fills
     * is closed on the way out. NeoForge runs common setup on parallel workers, so its entry
     * class queues this with {@code enqueueWork} as main did; Fabric calls it from SETUP.
     */
    public static void registrationWindow() {
        registrationThread = Thread.currentThread().getName();
        // Each event goes to the bus (subscribers, owned by the mod that provided them) and to
        // every historystages entrypoint on Fabric (owned by the entrypoint's mod).
        fireRegistration(new net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerLockCategories);
        net.bananemdnsa.historystages.data.lock.category.LockCategories.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerTriggerTypes);
        net.bananemdnsa.historystages.data.auto.TriggerTypes.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.dependency.RegisterRequirementTypesEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerRequirementTypes);
        net.bananemdnsa.historystages.data.dependency.RequirementTypes.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerStageSettingsGroups);
        net.bananemdnsa.historystages.data.settings.StageSettingsGroups.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerConfigSections);
        // The freeze is what makes the section list safe to read from the config
        // packets, which now go to AddonConfigSections directly rather than through a
        // registry the values had to be copied into first.
        net.bananemdnsa.historystages.data.config.AddonConfigSections.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.lock.RegisterIndividualRecipeSupportEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerIndividualRecipeSupport);
        net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.team.RegisterTeamProvidersEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerTeamProviders);
        net.bananemdnsa.historystages.data.team.TeamProviders.freeze();
        fireRegistration(new net.bananemdnsa.historystages.api.diagnostics.RegisterStageChecksEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerStageChecks);
        net.bananemdnsa.historystages.data.diagnostics.StageChecks.freeze();
        net.bananemdnsa.historystages.util.DebugLogger.registerBuiltInSections();
        fireRegistration(new net.bananemdnsa.historystages.api.diagnostics.RegisterReportSectionsEvent(), net.bananemdnsa.historystages.api.HistoryStagesPlugin::registerReportSections);
        net.bananemdnsa.historystages.data.diagnostics.ReportSections.freeze();

        // Logged here rather than inside freeze(): LockCategories is unit-tested, and
        // the test runtime classpath has no Minecraft or NeoForge on it. This line is
        // also how an in-game check confirms the event actually fired.
        var addonCategories =
                net.bananemdnsa.historystages.data.lock.category.LockCategories.addonIds();
        LOGGER.info("[HistoryStages] Lock categories closed: {} total, {} from other mods {}",
                net.bananemdnsa.historystages.data.lock.category.LockCategories.all().size(),
                addonCategories.size(), addonCategories);
        LOGGER.info("[HistoryStages] Stage settings groups closed: {} total",
                net.bananemdnsa.historystages.data.settings.StageSettingsGroups.all().size());
        LOGGER.info("[HistoryStages] Config sections closed: {} total",
                net.bananemdnsa.historystages.data.config.AddonConfigSections.all().size());
        LOGGER.info("[HistoryStages] Team providers closed: {}",
                net.bananemdnsa.historystages.data.team.TeamProviders.ids());
    }

    /** The thread the registration window ran on, null before it ran. For the GameTests. */
    public static String registrationThread() {
        return registrationThread;
    }

    @SubscribeEvent
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Wir fügen die Maschine bei den Funktions-Blöcken hinzu
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RESEARCH_PEDESTAL_ITEM.get());
        }

        // Generate a research scroll for every stage (global + individual).
        // AUTO/TEMPORARY stages have no scroll (they're unlocked via auto_trigger events).
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (var stageEntry : StageManager.getStages().entrySet()) {
                if (stageEntry.getValue().getMode().usesAutoTrigger()) continue;
                event.accept(ScrollVariants.createScroll(stageEntry.getKey()));
            }
            for (var stageEntry : StageManager.getIndividualStages().entrySet()) {
                if (stageEntry.getValue().getMode().usesAutoTrigger()) continue;
                event.accept(ScrollVariants.createScroll(stageEntry.getKey()));
            }
        }
    }

    /**
     * Posts one register event to the bus and hands it to every Fabric plugin. A plugin that throws
     * must not close the window for everyone else: the registry is frozen right after.
     */
    private static <E extends net.bananemdnsa.historystages.platform.bus.Event> void fireRegistration(
            E event, java.util.function.BiConsumer<net.bananemdnsa.historystages.api.HistoryStagesPlugin, E> call) {
        EventBus.post(event);
        net.bananemdnsa.historystages.platform.AddonEntrypoints.forEachPlugin((modId, plugin) -> {
            try {
                call.accept(plugin, event);
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] {} ({}) failed during {}", plugin.getClass().getName(), modId,
                        event.getClass().getSimpleName(), e);
            }
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        StageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Send stage definitions, unlocked stages, and server config to client
            PacketHandler.sendDefinitionsToPlayer(new SyncStageDefinitionsPacket(StageManager.getStages()), player);
            StageData data = StageData.get(player.level());
            PacketHandler.sendToPlayer(new SyncStagesPacket(data.getUnlockedStages()), player);
            PacketHandler.sendConfigToPlayer(SyncConfigPacket.fromServerConfig(), player);
            PacketHandler.sendGraphConfigToPlayer(
                    net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket
                            .fromServerConfig(), player);
            PacketHandler.sendVisualConfigToPlayer(
                    net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket
                            .fromServerConfig(), player);

            // Sync individual stages for this player
            IndividualStageData individualData = IndividualStageData.get(player.level());
            PacketHandler.sendIndividualStagesToPlayer(
                    SyncIndividualStagesPacket.of(individualData, player.getUUID()),
                    player
            );

            // Sync structure registry so editor UI can populate the searchable list
            PacketHandler.sendStructureRegistryToPlayer(
                    net.bananemdnsa.historystages.network.clientbound.SyncStructureRegistryPacket.fromServer(player),
                    player);

            DebugLogger.runtime(HsRuntimeCategories.PLAYER_LOGIN, player.getName().getString(),
                    "Synced " + StageManager.getStages().size() + " stage definitions, "
                    + data.getUnlockedStages().size() + " unlocked stages, "
                    + individualData.getUnlockedStages(player.getUUID()).size() + " individual stages");

            // Log locked items in player inventory
            logLockedInventoryItems(player);

            // Welcome message
            if (Config.VISUAL.showWelcomeMessage.get()) {
                int stageCount = StageManager.getStages().size();
                player.sendSystemMessage(Component.literal("§8§m                                                §r"));
                player.sendSystemMessage(Component.literal("  §b§lHistory Stages §7— §fWelcome!"));
                player.sendSystemMessage(Component.literal("  §7Loaded §f" + stageCount + " §7stage" + (stageCount != 1 ? "s" : "") + " from §fconfig/historystages/"));
                player.sendSystemMessage(Component.translatable("message.historystages.welcome.settings"));
                player.sendSystemMessage(Component.translatable("message.historystages.welcome.disable"));
                player.sendSystemMessage(Component.literal("§8§m                                                §r"));
            }

            // Debug error/warning messages (INFO only in log file, not in chat)
            if (Config.GAMEPLAY.showDebugErrors.get()) {
                List<StageManager.LoadingMessage> messages = StageManager.getLoadingMessages();
                List<StageManager.LoadingMessage> chatMessages = messages.stream()
                        .filter(m -> m.level() != StageManager.MessageLevel.INFO)
                        .toList();
                long infoCount = messages.size() - chatMessages.size();

                if (!chatMessages.isEmpty()) {
                    long errorCount = chatMessages.stream().filter(m -> m.level() == StageManager.MessageLevel.ERROR).count();
                    long warnCount = chatMessages.stream().filter(m -> m.level() == StageManager.MessageLevel.WARN).count();

                    // Summary header
                    StringBuilder summary = new StringBuilder("§7[HistoryStages] §fFound ");
                    if (errorCount > 0) summary.append("§c").append(errorCount).append(" error").append(errorCount != 1 ? "s" : "");
                    if (errorCount > 0 && warnCount > 0) summary.append("§f, ");
                    if (warnCount > 0) summary.append("§e").append(warnCount).append(" warning").append(warnCount != 1 ? "s" : "");
                    if (infoCount > 0) summary.append("§f (+ §b").append(infoCount).append(" info §fin log file)");
                    summary.append("§f:");
                    player.sendSystemMessage(Component.literal(summary.toString()));

                    // Show individual messages (max 10, then truncate)
                    int shown = 0;
                    for (StageManager.LoadingMessage msg : chatMessages) {
                        if (shown >= 10) {
                            player.sendSystemMessage(Component.literal("  §8... and " + (chatMessages.size() - 10) + " more (see log file)"));
                            break;
                        }
                        String prefix = switch (msg.level()) {
                            case ERROR -> "  §c[ERROR] §f";
                            case WARN ->  "  §e[WARN]  §f";
                            case INFO ->  "  §b[INFO]  §7";
                        };
                        player.sendSystemMessage(Component.literal(prefix + msg.message()));
                        shown++;
                    }

                    player.sendSystemMessage(Component.translatable("message.historystages.debug.report"));
                    player.sendSystemMessage(Component.translatable("message.historystages.debug.disable"));
                }
            }
        }
    }

    private static boolean serverInitialized = false;

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel sl) {
            StageData data = StageData.get(sl);
            StageData.refreshCache(data.getUnlockedStages());

            // Initialize individual stage cache
            IndividualStageData individualData = IndividualStageData.get(sl);
            individualData.refreshCache();

            // Drop AUTO-progress entries for stages that no longer exist or are no longer AUTO
            AutoTriggerManager.pruneOrphans(sl);

            // Only run once per server session (onWorldLoad fires for each dimension)
            if (!serverInitialized) {
                serverInitialized = true;
                LOGGER.info("[HistoryStages] Server cache initialized.");
                // Hand edits to settings/*.toml apply without a restart, as on 1.21.1. Clients get
                // the new values the way an editor save hands them out.
                ConfigFiles.startWatching(sl.getServer(),
                        () -> PacketHandler.sendConfigToAll(SyncConfigPacket.fromServerConfig()));

                // Registry validation (registries are now fully loaded)
                StageManager.validateAgainstRegistries();
                DebugLogger.writeLogFile(StageManager.getStages(), StageManager.getIndividualStages());

                DebugLogger.initRuntimeSession();
                DebugLogger.runtime(HsRuntimeCategories.SERVER, "Server started — cache initialized with "
                        + data.getUnlockedStages().size() + " unlocked stages, "
                        + StageManager.getStages().size() + " stages loaded");
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        serverInitialized = false;
        ConfigFiles.stopWatching();
        // Rolled from the trade hooks this server registered; the next one may register others.
        net.bananemdnsa.historystages.data.lock.TradeGoodsScanner.clearCache();
        DebugLogger.runtime(HsRuntimeCategories.SERVER, "Server stopping — flushing runtime log");
        DebugLogger.flushRuntimeBuffer();
    }

    private static int tickCounter = 0;
    private static final int FLUSH_INTERVAL = 600; // every 30 seconds (20 ticks/s * 30s)
    private static final int CLEANUP_INTERVAL = 6000; // every 5 minutes

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        if (tickCounter % FLUSH_INTERVAL == 0) {
            DebugLogger.flushRuntimeBuffer();
        }
        if (tickCounter % CLEANUP_INTERVAL == 0) {
            DebugLogger.cleanupThrottleMap();
        }

        // Before anything below can unlock a stage, or the change would already be part of the
        // baseline it is about to be compared against.
        if (event.getServer() != null
                && net.bananemdnsa.historystages.data.lock.VisibleRecipes.gatedSetNeedsSeeding()) {
            net.bananemdnsa.historystages.data.lock.VisibleRecipes.seedGatedSet(
                    event.getServer().getRecipeManager().getRecipes());
        }

        net.bananemdnsa.historystages.events.AutoTriggerEventBridge.pollPlayers(event.getServer(), tickCounter);

        // Deliberately here and not in RecipeManager.apply: KubeJS and CraftTweaker rewrite
        // recipes after that call, so an index built there would miss a script pack entirely.
        // A tick has, by definition, waited for all of them. Costs one boolean read when clean.
        //
        // getOrderedRecipes rather than getRecipes: the latter is gated on the server now, and a
        // fluid-gated recipe is exactly one of the recipes it leaves out — building the index from
        // it would drop that recipe from the index, which would ungate it, which would put it back
        // in the list. This one has to see every recipe there is.
        // PORT(Task 12): 26.2 has no getOrderedRecipes. getRecipes is ungated until the recipe
        // mixin moves; it must then leave getRecipes alone or these callers need their own accessor.
        if (event.getServer() != null) {
            net.bananemdnsa.historystages.data.lock.FluidRecipeIndex.rebuildIfDirty(
                    event.getServer().getRecipeManager().getRecipes(),
                    event.getServer().registryAccess());
        }

        // Advance temporary-mode re-lock timers / cooldowns.
        var server = event.getServer();
        if (server != null && server.overworld() != null && tickCounter % 20 == 0) {
            net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(server.overworld())
                    .tick(server, tickCounter, HistoryStages::resolveTemporaryConfig);
        }

        // Last in the tick on purpose: a stage unlocked anywhere above asks for a resend, and in
        // the case that needs it for a datapack reload, which blocks until it is finished. Running
        // it here rather than where it was asked for turns a bundle of unlocks into one piece of
        // work, and keeps it out of the middle of whatever else was ticking.
        if (server != null) {
            net.bananemdnsa.historystages.network.PacketHandler.runRequestedLockReload(server);
        }
    }

    /** Resolves a stage id to its temporary config, checking global then individual stages. */
    public static net.bananemdnsa.historystages.data.temporary.TemporaryConfig resolveTemporaryConfig(String stageId) {
        var entry = net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
        if (entry == null) {
            entry = net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(stageId);
        }
        return entry != null ? entry.getTemporary() : null;
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer().level().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty()) return;

        // Individual stages: prevent pickup of individually-locked items (respects lock_actions)
        if (Config.GAMEPLAY.individualLockItemPickup.get()
                && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), "pickup")) {
            event.setCanPickup(TriState.FALSE);
            Identifier itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled(HsRuntimeCategories.INVENTORY, "pickup_blocked_" + player.getUUID() + "_" + itemRL,
                    "<" + player.getName().getString() + "> Pickup of '" + itemRL + "' blocked [action: pickup]");
            return;
        }

        // Global stages: log only (existing behavior)
        if (StageLockHelper.isItemLockedForServer(stack)) {
            Identifier itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled(HsRuntimeCategories.INVENTORY, "pickup_" + player.getUUID() + "_" + itemRL,
                    "<" + player.getName().getString() + "> Picked up locked '" + itemRL + "' x" + stack.getCount() + " [action: pickup]");
        }
    }

    private static void logLockedInventoryItems(ServerPlayer player) {
        java.util.List<String> lockedItems = new java.util.ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (StageLockHelper.isItemLockedForServer(stack)) {
                Identifier itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
                lockedItems.add(itemRL + " x" + stack.getCount());
            }
        }
        if (!lockedItems.isEmpty()) {
            DebugLogger.runtime(HsRuntimeCategories.INVENTORY, player.getName().getString(),
                    "Has " + lockedItems.size() + " locked item stack(s) in inventory: " + String.join(", ", lockedItems));
        }
    }
}
