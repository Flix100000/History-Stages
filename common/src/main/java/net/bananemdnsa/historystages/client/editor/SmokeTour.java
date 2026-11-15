package net.bananemdnsa.historystages.client.editor;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.dialog.CreditsScreen;
import net.bananemdnsa.historystages.client.editor.dialog.StylePresetNameScreen;
import net.bananemdnsa.historystages.client.editor.graph.GraphLegend;
import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.client.scroll.OpenScrollScreen;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.event.client.ClientTickEvent;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Dev runs only: opens a fixed list of screens one after the other once the player is in a world
 * and saves a screenshot of each as {@code screenshots/hs-tour-<scene>.png} in the run folder.
 *
 * <p>Started with {@code -Dhistorystages.smoke.tour=all} (or a comma-separated list of scene
 * names) and ignored outside the development environment. It exists because the GUI breaks in
 * ways no unit test or GameTest sees: a colour without alpha, a blit with the wrong argument
 * order or a popup on the wrong layer all pass every automated check.
 *
 * <p>The editor scenes expect a global stage {@code hs_smoke}, and the pedestal requirements scene
 * {@code hs_smoke_deps}; the smoke script writes both.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class SmokeTour {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "historystages.smoke.tour";
    private static final String STAGE = "hs_smoke";
    /** Time in the world before the first scene, so chunks and the stage sync are in. */
    private static final int SETTLE_TICKS = 100;
    /** Time each scene gets before its picture; covers the editor's open animations. */
    private static final int SHOT_TICK = 40;

    private record Scene(String name, Runnable open, Map<Integer, Runnable> actions) {}

    private static List<Scene> scenes;
    private static int index = -1;
    private static int ticks;

    private SmokeTour() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        String wanted = System.getProperty(PROPERTY);
        if (wanted == null || !Platform.isDevelopmentEnvironment()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (scenes == null) {
            scenes = select(wanted);
            LOGGER.info("[HistoryStages] Smoke tour: {} scenes", scenes.size());
        }
        if (index >= scenes.size()) return;

        ticks++;
        if (index < 0) {
            if (ticks >= SETTLE_TICKS) next();
            return;
        }
        Scene scene = scenes.get(index);
        Runnable action = scene.actions().get(ticks);
        if (action != null) attempt(scene, action);
        if (ticks == SHOT_TICK) {
            Screenshot.grab(mc.gameDirectory, "hs-tour-" + scene.name() + ".png",
                    mc.gameRenderer.mainRenderTarget(), 1, message -> {});
        }
        if (ticks >= SHOT_TICK + 10) next();
    }

    private static void next() {
        index++;
        ticks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (index >= scenes.size()) {
            mc.gui.setScreen(null);
            LOGGER.info("[HistoryStages] Smoke tour done");
            return;
        }
        attempt(scenes.get(index), scenes.get(index).open());
    }

    private static void attempt(Scene scene, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            LOGGER.error("[HistoryStages] Smoke tour: scene {} failed", scene.name(), t);
        }
    }

    private static List<Scene> select(String wanted) {
        List<Scene> all = all();
        if (wanted.isBlank() || wanted.equals("all") || wanted.equals("true")) return all;
        List<String> names = Arrays.stream(wanted.split(",")).map(String::trim).toList();
        return all.stream().filter(s -> names.contains(s.name())).toList();
    }

    private static List<Scene> all() {
        List<Scene> list = new ArrayList<>();
        list.add(scene("new_stage", () -> show(new StageOverviewScreen.StageIdInputScreen(
                        parent(new StageOverviewScreen()), null, false, false, "", false)),
                Map.of(15, () -> type("smoke_eisen"))));
        list.add(scene("style_preset_add", () -> show(new StylePresetNameScreen(
                        parent(new StylePresetsScreen(parent(new StageOverviewScreen()))),
                        Component.translatable("editor.historystages.graph.preset.new.title"),
                        null, null, name -> {})),
                Map.of(15, () -> type("Smoke Preset"))));
        list.add(scene("graph_legend", () -> show(new StageGraphScreen(parent(new StageOverviewScreen()))),
                Map.of(5, () -> {
                    GraphLegend legend = field(screen(), "legend");
                    setField(legend, "expanded", true);
                })));
        list.add(scene("credits", () -> show(new CreditsScreen(parent(new StageOverviewScreen()))), Map.of()));
        list.add(picker("entity_picker", 8, Map.of()));
        list.add(picker("item_picker", 0, Map.of()));
        list.add(picker("recipe_picker", 6, Map.of(20, () -> {
            SearchBar bar = field(activeOverlay(6), "searchBar");
            bar.setText("iron");
            setField(bar.filters(), "expanded", true);
        })));
        list.add(scene("inventory_lock", () -> {
            onServer(player -> player.getInventory().add(new ItemStack(Items.IRON_INGOT, 5)));
            show(new InventoryScreen(Minecraft.getInstance().player));
        }, Map.of()));
        Supplier<List<Block>> pedestals = () -> List.of(ModBlocks.RESEARCH_PEDESTAL.get(),
                ModBlocks.RESEARCH_PEDESTAL_TIER_2.get(), ModBlocks.RESEARCH_PEDESTAL_TIER_3.get(),
                ModBlocks.RESEARCH_PEDESTAL_TIER_4.get());
        for (int tier = 1; tier <= 4; tier++) {
            int t = tier;
            // Placed first and opened a moment later: the client needs the block entity by then.
            list.add(scene("pedestal_t" + tier, () -> placePedestal(pedestals.get().get(t - 1), t),
                    Map.of(10, () -> openPedestal(t))));
        }
        // The research scroll of a stage with a requirement, so the requirements panel opens
        // beside the sheet, where JEI's item list must keep clear of it.
        list.add(scene("pedestal_requirements", () -> placePedestal(pedestals.get().get(0), 1),
                Map.of(10, () -> openPedestal(1),
                        15, () -> onServer(player -> player.containerMenu.getSlot(PEDESTAL_SCROLL_SLOT)
                                .set(ScrollVariants.createScroll(DEPS_STAGE))))));
        list.add(scene("jei_boosters", SmokeTour::showJeiBoosters, Map.of()));
        list.add(scene("open_scroll", () -> show(new OpenScrollScreen(STAGE)), Map.of()));
        list.add(scene("mod_list", SmokeTour::openModList, Map.of()));
        // A locked block under the crosshair, for Jade's lines.
        list.add(scene("jade_locked_block", () -> {
            show(null);
            onServer(player -> {
                runCommand(player, "setblock 18 97 -7 minecraft:iron_block");
                runCommand(player, "tp @s 18.5 96 -4.5 180 20");
            });
        }, Map.of()));
        // Water and stained glass between the camera and the force fields and the selection box.
        list.add(scene("overlays_water", () -> {
            show(null);
            onServer(player -> {
                for (String command : WATER_AND_GLASS) runCommand(player, command);
            });
        }, Map.of()));
        return list;
    }

    private static final String DEPS_STAGE = "hs_smoke_deps";
    private static final int PEDESTAL_SCROLL_SLOT = 36;

    /**
     * Built around the player's spot from the smoke data pack (18.5 96 -4.5): a water column in front
     * of the village's red field straight ahead, stained glass in front of the zone's wall on the
     * right and of the selection box on the left.
     */
    private static final List<String> WATER_AND_GLASS = List.of(
            "time set noon",
            "tp @s 18.5 96 -4.5 -160 15",
            // Barriers hold the water in place and are not drawn.
            "fill 16 95 -8 19 100 -6 minecraft:barrier",
            "fill 17 96 -7 18 99 -7 minecraft:water",
            "fill 20 95 -7 20 99 -5 minecraft:lime_stained_glass",
            "fill 14 95 -6 15 98 -6 minecraft:lime_stained_glass");

    private static void runCommand(ServerPlayer player, String command) {
        var source = player.createCommandSourceStack().withSuppressedOutput()
                .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
    }

    /** JEI's recipe screen on the booster category, looked up by name so a run without JEI skips it. */
    private static void showJeiBoosters() {
        if (!Platform.isModLoaded("jei")) {
            LOGGER.info("[HistoryStages] Smoke tour: JEI not loaded");
            return;
        }
        try {
            Class<?> plugin = Class.forName("net.bananemdnsa.historystages.compat.jei.JEIPlugin");
            Object runtime = field(null, plugin, "RUNTIME");
            Object type = Class.forName("net.bananemdnsa.historystages.compat.jei.BoosterRecipeCategory")
                    .getField("TYPE").get(null);
            Object gui = Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getRecipesGui").invoke(runtime);
            Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("showTypes", List.class)
                    .invoke(gui, List.of(type));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Scene scene(String name, Runnable open, Map<Integer, Runnable> actions) {
        return new Scene(name, open, new TreeMap<>(actions));
    }

    /** The stage editor on {@link #STAGE}, switched to a tab, with that tab's "+ Add" picker open. */
    private static Scene picker(String name, int tab, Map<Integer, Runnable> more) {
        Map<Integer, Runnable> actions = new TreeMap<>(more);
        actions.put(5, () -> {
            invoke(screen(), "switchTab", tab);
            invoke(screen(), "openAddDialog");
        });
        return new Scene(name, () -> show(new StageDetailScreen(parent(new StageOverviewScreen()), STAGE,
                StageManager.getStages().get(STAGE), false)), actions);
    }

    private static Object activeOverlay(int tab) {
        Map<Integer, CategoryTab> tabs = field(screen(), "categoryTabs");
        return tabs.get(tab).activeOverlay();
    }

    private static BlockPos pedestalPos(ServerPlayer player, int tier) {
        return player.blockPosition().offset(-3 + tier * 2, 3, 3);
    }

    private static void placePedestal(Block block, int tier) {
        onServer(player -> player.level().setBlock(pedestalPos(player, tier), block.defaultBlockState(),
                Block.UPDATE_ALL));
    }

    private static void openPedestal(int tier) {
        onServer(player -> {
            BlockPos pos = pedestalPos(player, tier);
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof MenuProvider provider)) {
                LOGGER.error("[HistoryStages] Smoke tour: no pedestal block entity at {}", pos);
                return;
            }
            dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(player, provider,
                    buf -> buf.writeBlockPos(pos));
        });
    }

    /** NeoForge's mod list, looked up by name so the Fabric run simply skips it. */
    private static void openModList() {
        try {
            Class<?> type = Class.forName("net.neoforged.neoforge.client.gui.modlist.ModListScreen");
            show((Screen) type.getMethod("create", Screen.class).invoke(null, (Screen) null));
        } catch (ClassNotFoundException notNeoForge) {
            LOGGER.info("[HistoryStages] Smoke tour: no mod list screen on this loader");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void onServer(java.util.function.Consumer<ServerPlayer> work) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("needs a singleplayer world");
        var uuid = mc.player.getUUID();
        server.execute(() -> work.accept(server.getPlayerList().getPlayer(uuid)));
    }

    /** A screen that only ever sits behind another one still has to be laid out once. */
    private static <S extends Screen> S parent(S screen) {
        var window = Minecraft.getInstance().getWindow();
        screen.init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        return screen;
    }

    private static void show(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    private static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    /** Types into whatever has focus, through the same path a key press takes. */
    private static void type(String text) {
        Screen screen = screen();
        text.codePoints().forEach(cp -> screen.charTyped(new CharacterEvent(cp)));
    }

    private static Object field(Object owner, Class<?> type, String name) throws ReflectiveOperationException {
        Field f = findField(type, name);
        f.setAccessible(true);
        return f.get(owner);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field f = findField(owner.getClass(), name);
            f.setAccessible(true);
            return (T) f.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            Field f = findField(owner.getClass(), name);
            f.setAccessible(true);
            f.set(owner, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void invoke(Object owner, String name, Object... args) {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    try {
                        m.setAccessible(true);
                        m.invoke(owner, args);
                        return;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new IllegalStateException("no method " + name + " on " + owner.getClass().getName());
    }
}
