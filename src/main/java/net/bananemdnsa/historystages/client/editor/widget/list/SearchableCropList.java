package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Picker for the plants tab: plant blocks on the first tab, block tags on the second.
 *
 * <p>"Plant" is read generously — anything bonemealable plus the growers that are not (sugar cane,
 * cactus, nether wart, chorus) — because a mod's crop that is missing here could only be added by
 * hand-editing the file. Grass blocks and the like show up too; locking them is harmless.
 */
public class SearchableCropList extends AbstractSearchableList<String> {

    /** 0 = plants, 1 = block tags. */
    private int activeTab = 0;

    public SearchableCropList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.crops").getString(),
                onSelect, alreadyAddedSupplier);
    }

    /** Whether the plants tab and the mod popup offer this block. */
    public static boolean isPlant(Block block) {
        BlockState state = block.defaultBlockState();
        return block instanceof BonemealableBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof NetherWartBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof StemBlock
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS);
    }

    /** Every plant block id in the registry, for the mod popup. */
    public static List<String> allPlantIds() {
        List<String> ids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isPlant(block)) ids.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        ids.sort(String::compareToIgnoreCase);
        return ids;
    }

    @Override
    protected List<String> ownTabLabels() {
        return List.of(
                Component.translatable("editor.historystages.search.tab.crops").getString(),
                Component.translatable("editor.historystages.search.tab.block_tags").getString());
    }

    @Override
    protected void onOwnTabChanged(int index) {
        activeTab = index;
        setPlaceholder(Component.translatable(index == 0
                ? "editor.historystages.search.placeholder.crops"
                : "editor.historystages.search.placeholder.block_tags").getString());
        reloadEntries();
    }

    @Override
    protected List<String> loadEntries() {
        if (activeTab == 0) return allPlantIds();
        List<String> entries = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTagNames().forEach(tag -> entries.add(tag.location().toString()));
        entries.sort(String::compareToIgnoreCase);
        return entries;
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected String getIdForAddedCheck(String entry) {
        return activeTab == 1 ? "#" + entry : entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        if (entry.toLowerCase().contains(lowerCaseQuery)) return true;
        if (activeTab != 0) return false;
        // Let "Weizen" find minecraft:wheat: the translated block name is what people type.
        ResourceLocation id = ResourceLocation.tryParse(entry);
        if (id == null) return false;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block.getName().getString().toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return activeTab == 1 ? "#" + entry : entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        drawRowText(g, font, activeTab == 1 ? "#" + entry : entry, x, y, w, hovered);
    }

    @Override
    protected void renderSelectedRow(GuiGraphics g, Font font, String value, String entry,
                                     int x, int y, int w, int h, boolean hovered, int rowIndex) {
        // The frozen value already carries the "#" when it was picked on the tags tab.
        drawRowText(g, font, value, x, y, w, hovered);
    }
}
