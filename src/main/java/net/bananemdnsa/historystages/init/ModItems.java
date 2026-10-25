package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.client.tooltip.ScrollTooltipRenderer;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.block.ScrollShelfWood;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.bananemdnsa.historystages.HistoryStages;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            HistoryStages.MOD_ID);

    public static final String CREATIVE_STAGE_ID = "_creative";

    public static final RegistryObject<Item> RESEARCH_SCROLL = ITEMS.register("research_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public Component getName(ItemStack stack) {
                    Component name = ScrollTooltipRenderer.name(stack.getTag());
                    return name != null ? name : super.getName(stack);
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                        TooltipFlag flag) {
                    ScrollTooltipRenderer.append(stack.getTag(), tooltip);
                }
            });

    public static final RegistryObject<Item> RESEARCH_SCROLL_OPEN = ITEMS.register("research_scroll_open",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public Component getName(ItemStack stack) {
                    if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                        String stageId = stack.getTag().getString("StageResearch");
                        var stage = StageManager.getStages().get(stageId);
                        if (stage == null) {
                            stage = StageManager.getIndividualStages().get(stageId);
                        }
                        if (stage != null) {
                            return Component.translatable("tooltip.historystages.research_scroll.named", stage.getDisplayName())
                                    .withStyle(ChatFormatting.AQUA);
                        }
                    }
                    return super.getName(stack);
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                        TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.historystages.research_scroll_open.info")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    public static final RegistryObject<Item> CREATIVE_SCROLL = ITEMS.register("creative_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                        TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info1")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info2")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    public static final RegistryObject<Item> RESEARCH_PEDESTAL_ITEM = ITEMS.register("research_pedestal",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL.get(), new Item.Properties()));

    public static final RegistryObject<Item> RESEARCH_PEDESTAL_TIER_2_ITEM = ITEMS.register("research_pedestal_tier_2",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL_TIER_2.get(), new Item.Properties()));

    public static final RegistryObject<Item> RESEARCH_PEDESTAL_TIER_3_ITEM = ITEMS.register("research_pedestal_tier_3",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL_TIER_3.get(), new Item.Properties()));

    public static final RegistryObject<Item> RESEARCH_PEDESTAL_TIER_4_ITEM = ITEMS.register("research_pedestal_tier_4",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL_TIER_4.get(), new Item.Properties()));

    public static final Map<ScrollShelfWood, RegistryObject<Item>> SCROLL_SHELF_ITEMS = registerScrollShelfItems();

    private static Map<ScrollShelfWood, RegistryObject<Item>> registerScrollShelfItems() {
        Map<ScrollShelfWood, RegistryObject<Item>> items = new EnumMap<>(ScrollShelfWood.class);
        ModBlocks.SCROLL_SHELVES.forEach((wood, block) -> items.put(wood,
                ITEMS.register(wood.blockId(), () -> scrollShelfItem(block.get(), wood))));
        return Collections.unmodifiableMap(items);
    }

    /**
     * Wooden shelves burn like a chiseled bookshelf. NeoForge reads that from a fuel data map;
     * 1.20.1 has none, so the item answers the furnace itself.
     */
    private static Item scrollShelfItem(net.minecraft.world.level.block.Block block, ScrollShelfWood wood) {
        if (!wood.flammable()) return new BlockItem(block, new Item.Properties());
        return new BlockItem(block, new Item.Properties()) {
            @Override
            public int getBurnTime(net.minecraft.world.item.ItemStack stack,
                                   @org.jetbrains.annotations.Nullable net.minecraft.world.item.crafting.RecipeType<?> recipeType) {
                return 300;
            }
        };
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
