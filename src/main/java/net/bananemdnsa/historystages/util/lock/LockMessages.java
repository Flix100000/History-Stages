package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves a lock-message Component from a config string override with translation-key fallback.
 * Empty config value → Component.translatable(key); otherwise the override with {@code &} codes
 * and its placeholders filled in.
 *
 * <p>The placeholder values are only worked out when an override is set. The built-in texts take
 * none, and most packs never write an override, so a refused click costs no stage lookup.
 */
public final class LockMessages {

    private static final String UNKNOWN = "???";

    private LockMessages() {}

    public static MutableComponent resolve(ModConfigSpec.ConfigValue<String> override, String translationKey) {
        return resolve(override, translationKey, Map::of);
    }

    /**
     * Placeholder-aware form. {@code values} maps a bare name ({@code item}, not {@code {item}})
     * to what replaces it.
     */
    public static MutableComponent resolve(ModConfigSpec.ConfigValue<String> override, String translationKey,
                                           Supplier<Map<String, Component>> values) {
        String raw = override.get();
        if (raw == null || raw.isEmpty()) {
            return Component.translatable(translationKey);
        }
        return format(raw, values.get());
    }

    /**
     * Turns an override into a Component, keeping each placeholder value a Component of its own.
     *
     * <p>Not a plain string replace for two reasons. An item or mob name is a translatable, so it
     * stays in the player's language instead of the server's. And a {@code §} code does not
     * reach past the end of its own text, so each piece gets the colour that was active where it
     * starts — {@code &b{item}} would otherwise lose its aqua at the name.
     */
    static MutableComponent format(String raw, Map<String, Component> values) {
        String text = raw.replace('&', '§');
        MutableComponent out = Component.empty();
        StringBuilder piece = new StringBuilder();
        Style active = Style.EMPTY;
        Style pieceStart = Style.EMPTY;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                int end = text.indexOf('}', i);
                Component value = end > i ? values.get(text.substring(i + 1, end)) : null;
                if (value != null) {
                    if (!piece.isEmpty()) out.append(Component.literal(piece.toString()).withStyle(pieceStart));
                    piece.setLength(0);
                    out.append(Component.empty().withStyle(active).append(value));
                    pieceStart = active;
                    i = end + 1;
                    continue;
                }
            }
            if (c == '§' && i + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(text.charAt(i + 1));
                if (format != null) {
                    active = format == ChatFormatting.RESET ? Style.EMPTY : active.applyLegacyFormat(format);
                }
            }
            piece.append(c);
            i++;
        }
        if (!piece.isEmpty()) out.append(Component.literal(piece.toString()).withStyle(pieceStart));
        return out;
    }

    // ---- the messages --------------------------------------------------------------------

    /** Client side: the stage names arrive already resolved for this player. */
    public static MutableComponent dimensionUnknown(String dimensionId, List<String> stageNames) {
        return resolve(Config.VISUAL.msgDimensionUnknown, "message.historystages.dimension_unknown",
                () -> Map.of("dimension", Component.literal(dimensionId), "stage", joinedNames(stageNames)));
    }

    /** Client side, like {@link #dimensionUnknown}. */
    public static MutableComponent mobUnknown(String entityId, List<String> stageNames) {
        return resolve(Config.VISUAL.msgMobUnknown, "message.historystages.mob_unknown",
                () -> Map.of("mob", entityName(entityId), "stage", joinedNames(stageNames)));
    }

    /** {@code action} is the one that was refused, so {stage} names what blocks exactly that. */
    public static MutableComponent itemLocked(ServerPlayer player, ItemStack stack, String action) {
        return resolve(Config.VISUAL.msgItemLocked, "message.historystages.item_locked",
                () -> Map.of("item", stack.getHoverName(),
                        "stage", stageNames(player, itemActionStages(stack, action, player.getUUID()))));
    }

    /**
     * Its own message rather than the item one, because the two say different things. Holding a
     * gated bucket is "you do not know this item"; scooping from a gated pool is "you do not know
     * what is in there" — the bucket may be perfectly familiar.
     */
    public static MutableComponent fluidLocked(ServerPlayer player, String fluidId, String action) {
        return resolve(Config.VISUAL.msgFluidLocked, "message.historystages.fluid_locked",
                () -> Map.of("fluid", fluidName(fluidId),
                        "stage", stageNames(player, bothScopes(player.getUUID(),
                                scope -> StageLocks.engine().gatingStagesForFluidAction(fluidId, action, scope)))));
    }

    /**
     * Shown only when a merchant's whole list was filtered away.
     *
     * <p>A merchant showing two offers instead of three says nothing, on purpose — not noticing
     * is the point of the category. A merchant showing <em>nothing</em> looks broken, and that is
     * the one case worth explaining. No placeholders: the client draws it and knows neither the
     * offers that were removed nor the stages behind them.
     */
    public static MutableComponent tradeLocked() {
        return resolve(Config.VISUAL.msgTradeLocked, "message.historystages.trade_locked");
    }

    /** {@code blockItem} is the block's item form, which is what the block lock is keyed on. */
    public static MutableComponent blockLocked(ServerPlayer player, ItemStack blockItem, String action) {
        return resolve(Config.VISUAL.msgBlockLocked, "message.historystages.block_locked",
                () -> Map.of("block", blockItem.getHoverName(),
                        "stage", stageNames(player, itemActionStages(blockItem, action, player.getUUID()))));
    }

    /**
     * Bonemeal refused on a locked plant. {@code missingStageIds} is what the crop lock already
     * worked out, so {stage} names exactly the stages that keep this plant from growing.
     */
    public static MutableComponent cropLocked(ServerPlayer player, net.minecraft.world.level.block.state.BlockState plant,
                                              List<String> missingStageIds) {
        return resolve(Config.VISUAL.msgCropLocked, "message.historystages.crop_locked",
                () -> Map.of("block", plant.getBlock().getName(),
                        "stage", stageNames(player, missingStageIds)));
    }

    /** {@code stack} is the first displayed item that is locked. */
    public static MutableComponent entityItemLocked(ServerPlayer player, ItemStack stack) {
        return resolve(Config.VISUAL.msgEntityItemLocked, "message.historystages.entity_item_locked",
                () -> Map.of("item", stack.getHoverName(),
                        "stage", stageNames(player, itemActionStages(stack, "pickup", player.getUUID()))));
    }

    /**
     * A station refused an enchantment.
     *
     * @param enchantmentId null when the refusal is about the item fed in rather than about one
     *                      enchantment — an anvil with a locked book in its right slot. {enchantment}
     *                      then names that item.
     * @param level         the enchantment level, ignored without an id
     * @param station       {@code enchanting_table} or {@code anvil}
     * @param item          the item being enchanted, or the one fed in when there is no id
     */
    public static MutableComponent enchantmentLocked(ServerPlayer player, @Nullable String enchantmentId, int level,
                                                     String station, ItemStack item) {
        return resolve(Config.VISUAL.msgEnchantmentLocked, "message.historystages.enchantment_locked", () -> {
            Map<String, Component> values = new LinkedHashMap<>();
            values.put("item", item.getHoverName());
            if (enchantmentId == null) {
                values.put("enchantment", item.getHoverName());
                values.put("stage", stageNames(player, itemActionStages(item, "use", player.getUUID())));
            } else {
                String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
                values.put("enchantment", enchantmentName(player, enchantmentId));
                values.put("stage", stageNames(player, bothScopes(player.getUUID(),
                        scope -> StageLocks.engine().gatingStagesForEnchantment(
                                enchantmentId, level, station, itemId, scope))));
            }
            return values;
        });
    }

    /**
     * The recipe book refused a recipe. A recipe is locked by its own entry, by its result's
     * {@code recipe} action, or by a fluid it touches; {stage} names whichever of the first two
     * applies and stays {@code ???} for the fluid case, which has no stage list to show.
     */
    public static MutableComponent recipeLocked(ServerPlayer player, ResourceLocation recipeId) {
        return resolve(Config.VISUAL.msgRecipeLocked, "message.historystages.recipe_locked", () -> {
            ItemStack result = recipeResult(player, recipeId);
            List<String> stages = bothScopes(player.getUUID(),
                    scope -> StageLocks.engine().gatingStagesForRecipe(recipeId.toString(), scope));
            if (stages.isEmpty() && !result.isEmpty()) {
                stages = itemActionStages(result, "recipe", player.getUUID());
            }
            return Map.of("item", result.isEmpty() ? Component.literal(UNKNOWN) : result.getHoverName(),
                    "stage", stageNames(player, stages));
        });
    }

    /** Starting research on a stage its logic currently blocks. {@code {stage}} in an override is the name. */
    public static MutableComponent stageBlocked(String stageName) {
        String raw = Config.VISUAL.msgStageBlocked.get();
        if (raw == null || raw.isEmpty()) {
            return Component.translatable("message.historystages.stage_blocked", stageName);
        }
        return format(raw, Map.of("stage", Component.literal(stageName)));
    }

    // ---- placeholder values --------------------------------------------------------------

    private static List<String> itemActionStages(ItemStack stack, String action, UUID playerUuid) {
        if (stack.isEmpty()) return List.of();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return bothScopes(playerUuid, scope -> StageLocks.engine().gatingStagesForItemAction(
                id.toString(), id.getNamespace(), stack, action, scope));
    }

    /** Missing stages for this player, global ones first, each scope resolved on its own state. */
    private static List<String> bothScopes(UUID playerUuid, java.util.function.Function<StageScope, List<String>> gating) {
        List<String> missing = new ArrayList<>(LockResolution.missingStages(StageScope.GLOBAL,
                gating.apply(StageScope.GLOBAL), StageLocks.serverGlobal()));
        missing.addAll(LockResolution.missingStages(StageScope.INDIVIDUAL,
                gating.apply(StageScope.INDIVIDUAL), StageLocks.serverIndividual(playerUuid)));
        return missing;
    }

    /**
     * The stage names this player may see. {@code ???} as soon as one of the stages hides its lock
     * hints — the same rule the "Required: …" suffix follows — or when nothing could be named.
     */
    private static Component stageNames(ServerPlayer player, List<String> stageIds) {
        if (stageIds.isEmpty()) return Component.literal(UNKNOWN);
        List<String> names = new ArrayList<>(stageIds.size());
        for (String stageId : stageIds) {
            if (!StageStates.showsLockHints(stageId)) return Component.literal(UNKNOWN);
            names.add(StageStates.displayNameFor(stageId, player));
        }
        return Component.literal(String.join(", ", names));
    }

    private static Component joinedNames(List<String> names) {
        return Component.literal(names.isEmpty() ? UNKNOWN : String.join(", ", names));
    }

    private static Component entityName(String entityId) {
        return EntityType.byString(entityId)
                .<Component>map(EntityType::getDescription)
                .orElse(Component.literal(entityId));
    }

    private static Component fluidName(String fluidId) {
        ResourceLocation key = ResourceLocation.tryParse(fluidId);
        if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) return Component.literal(fluidId);
        Fluid fluid = BuiltInRegistries.FLUID.get(key);
        return fluid.getFluidType().getDescription();
    }

    private static Component enchantmentName(ServerPlayer player, String enchantmentId) {
        ResourceLocation key = ResourceLocation.tryParse(enchantmentId);
        if (key == null) return Component.literal(enchantmentId);
        return player.serverLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getOptional(key)
                .<Component>map(enchantment -> enchantment.description())
                .orElse(Component.literal(enchantmentId));
    }

    private static ItemStack recipeResult(ServerPlayer player, ResourceLocation recipeId) {
        try {
            return player.server.getRecipeManager().byKey(recipeId)
                    .map((RecipeHolder<?> holder) -> holder.value().getResultItem(player.registryAccess()))
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            // Another mod's recipe computing its own result. Not worth losing the message over.
            return ItemStack.EMPTY;
        }
    }
}
