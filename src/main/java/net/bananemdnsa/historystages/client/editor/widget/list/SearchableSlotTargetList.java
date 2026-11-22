package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.client.slotlock.SlotTargetGroups;
import net.bananemdnsa.historystages.util.lock.SlotTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a container slot lock is about: blocks with a GUI on the first tab (the precise choice),
 * menu types on the second (for menus with no block behind them, such as backpacks). Both are
 * grouped by mod with a caption per mod, because a flat list of ids gives no idea what exists.
 */
public class SearchableSlotTargetList extends AbstractSearchableList<String> {

    private static final int STICKY_H = 11;
    private static final int CAPTION = 0xFFFFCC00;
    private static final int ICON = 12;

    /** Worked out once per session: creating every block entity to ask it is not free. */
    private static List<String> menuBlocks;
    /** Menu type id to the blocks that open it, so "generic_9x3" can say "Chest, Barrel". */
    private static Map<String, List<String>> blocksByMenu;

    private final Runnable onPlayer;
    private final Runnable onWorldPick;
    private final boolean offerContainers;
    private int activeTab = 0;

    public SearchableSlotTargetList(Consumer<String> onSelect, Runnable onPlayer, Runnable onWorldPick,
                                    boolean offerContainers) {
        super(Component.translatable("editor.historystages.slots.search.blocks").getString(), onSelect, null);
        this.onPlayer = onPlayer;
        this.onWorldPick = onWorldPick;
        this.offerContainers = offerContainers;
    }

    @Override
    protected List<FooterButton> footerButtons() {
        List<FooterButton> buttons = new ArrayList<>();
        buttons.add(new FooterButton(Component.translatable("editor.historystages.slots.target.player").getString(),
                () -> { hide(); onPlayer.run(); }));
        if (offerContainers) {
            buttons.add(new FooterButton(Component.translatable("editor.historystages.slots.pick_in_world").getString(),
                    () -> { hide(); onWorldPick.run(); }));
        }
        return buttons;
    }

    @Override
    protected List<String> ownTabLabels() {
        return List.of(
                Component.translatable("editor.historystages.slots.search.tab.blocks").getString(),
                Component.translatable("editor.historystages.slots.search.tab.menus").getString());
    }

    @Override
    protected void onOwnTabChanged(int index) {
        activeTab = index;
        setPlaceholder(Component.translatable(index == 0
                ? "editor.historystages.slots.search.blocks"
                : "editor.historystages.slots.search.menus").getString());
        reloadEntries();
    }

    @Override
    protected List<String> loadEntries() {
        if (!offerContainers) return List.of();
        scan();
        if (activeTab == 1) {
            List<String> ids = new ArrayList<>();
            for (ResourceLocation key : BuiltInRegistries.MENU.keySet()) ids.add(key.toString());
            return ids;
        }
        return new ArrayList<>(menuBlocks);
    }

    /** The search has run; captions go back in above whatever survived it. */
    @Override
    protected void afterFilter(List<String> filtered) {
        List<String> plain = new ArrayList<>();
        for (String entry : filtered) if (!SlotTargetGroups.isHeader(entry)) plain.add(entry);
        filtered.clear();
        filtered.addAll(SlotTargetGroups.group(plain, SearchableSlotTargetList::modName));
    }

    @Override
    protected boolean isHeaderRow(int index) {
        return index >= 0 && index < filteredEntries.size() && SlotTargetGroups.isHeader(filteredEntries.get(index));
    }

    @Override
    protected void renderHeaderRow(GuiGraphics g, Font font, String entry, int x, int y, int w, int h) {
        g.drawString(font, modName(SlotTargetGroups.namespaceOf(entry)), x + 3, y + 4, CAPTION, false);
        g.fill(x + 3, y + h - 1, x + w - 3, y + h, 0x40FFCC00);
    }

    @Override
    protected int stickyCaptionH() {
        return STICKY_H;
    }

    /** Which mod's rows are going past, once its own caption has scrolled away. */
    @Override
    protected void renderStickyCaption(GuiGraphics g, Font font, int x, int y, int w, int firstVisibleIndex) {
        if (filteredEntries.isEmpty()) return;
        int start = Math.min(Math.max(firstVisibleIndex, 0), filteredEntries.size() - 1);
        if (SlotTargetGroups.isHeader(filteredEntries.get(start))) return;
        for (int i = start; i >= 0; i--) {
            String entry = filteredEntries.get(i);
            if (SlotTargetGroups.isHeader(entry)) {
                g.drawString(font, modName(SlotTargetGroups.namespaceOf(entry)), x + 3, y + 2, 0xFF8A8A8A, false);
                return;
            }
        }
    }

    /** Blocks whose block entity opens a menu. Anything that throws while being asked is left out. */
    public static synchronized List<String> blocksWithMenus() {
        scan();
        return menuBlocks;
    }

    private static synchronized void scan() {
        if (menuBlocks != null) return;
        List<String> ids = new ArrayList<>();
        Map<String, List<String>> byMenu = new LinkedHashMap<>();
        var player = Minecraft.getInstance().player;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof EntityBlock entityBlock)) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            try {
                BlockEntity be = entityBlock.newBlockEntity(net.minecraft.core.BlockPos.ZERO, block.defaultBlockState());
                if (!(be instanceof MenuProvider)) continue;
                ids.add(id);
                if (player == null) continue;
                AbstractContainerMenu menu = SlotTargets.previewMenu(block, player);
                String type = menu != null ? SlotTargets.menuTypeOf(menu) : null;
                if (type != null) byMenu.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
            } catch (Throwable ignored) {
                // A mod block entity that needs a level to exist; it can still be picked in the world.
            }
        }
        menuBlocks = List.copyOf(ids);
        blocksByMenu = byMenu;
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        if (entry.toLowerCase().contains(lowerCaseQuery)) return true;
        if (modName(namespace(entry)).toLowerCase().contains(lowerCaseQuery)) return true;
        if (activeTab == 0) return displayName(entry).toLowerCase().contains(lowerCaseQuery);
        for (String block : blocksOpening(entry)) {
            if (displayName(block).toLowerCase().contains(lowerCaseQuery)) return true;
        }
        return false;
    }

    @Override
    protected String selectionValueOf(String entry) {
        return entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        List<String> openers = activeTab == 0 ? List.of(entry) : blocksOpening(entry);
        if (!openers.isEmpty()) drawIcon(g, openers.get(0), x + 3, y + (h - ICON) / 2);
        String text;
        if (activeTab == 0) {
            text = displayName(entry) + " §8" + entry;
        } else {
            String path = entry.substring(entry.indexOf(':') + 1);
            text = path + " §8· " + (openers.isEmpty()
                    ? Component.translatable("editor.historystages.slots.search.no_block").getString()
                    : String.join(", ", openers.stream().limit(3).map(SearchableSlotTargetList::displayName).toList()));
        }
        drawRowMarqueeText(g, font, text, x + ICON + 4, y, w - ICON - 4, h, hovered, rowIndex);
    }

    private static List<String> blocksOpening(String menuType) {
        scan();
        return blocksByMenu.getOrDefault(menuType, List.of());
    }

    private static void drawIcon(GuiGraphics g, String blockId, int x, int y) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return;
        ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.get(id));
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(ICON / 16f, ICON / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : id;
    }

    /** The mod's own name, or its namespace for ids no loaded mod claims. */
    public static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    /** A block's own name for block ids, the id itself for anything else. */
    public static String displayName(String target) {
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).getName().getString();
        }
        return target;
    }
}
