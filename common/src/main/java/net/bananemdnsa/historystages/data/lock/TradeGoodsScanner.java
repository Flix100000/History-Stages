package net.bananemdnsa.historystages.data.lock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/**
 * Finds out which items merchants deal in, by running the recipes they are dealt from.
 *
 * <p>Minecraft keeps no list of trade goods. What it keeps is a set of small recipes for
 * <em>rolling</em> an offer — a handful per profession and level — and a recipe cannot be read,
 * only run. Mods add theirs in the same form. So the only honest way to answer "what can be
 * traded" is to run them all and look at what came out.
 *
 * <p><strong>The recipes are run directly, not through a merchant.</strong> Making a merchant and
 * asking for its offers is the obvious way and it is a dead end: that call refuses outright
 * anywhere but on a server, so a scan built on it comes back empty on a client and says nothing
 * about why. Running the recipes carries no such restriction — and it is the better answer in any
 * case, because a merchant is dealt only two of its level's recipes at random, so asking merchants
 * means making hundreds and hoping, while this covers every recipe exactly once.
 *
 * <p>Each recipe is still run several times, because some are random in themselves: the
 * enchanted-book trade picks an enchantment per offer, and a single run would name one book.
 *
 * <p><strong>The answer is good, not complete.</strong> A recipe that refuses to run outside a
 * real trade contributes nothing, one that takes too long to answer is dropped by
 * {@link TradeScanBudget}, and on a dedicated server a client's copy of the table holds only the
 * vanilla recipes — a mod's are added when a world loads its data, which a client never does.
 * That last gap is what the server's own answer is for; see {@code ClientTradeGoods}.
 * Both fail quietly on purpose: this list narrows a picker, and one missing item is a click's
 * worth of annoyance where a scan that breaks the editor is not.
 */
public final class TradeGoodsScanner {

    /**
     * How many times to run each recipe.
     *
     * <p>Once would do for the fixed ones, which are most of them. Eight is for the handful that
     * roll something themselves — a book's enchantment, an armourer's dye — where a single run
     * would put one arbitrary result in the list and leave its siblings out.
     */
    private static final int RUNS_PER_LISTING = 8;

    /** Novice through master. */
    private static final int MAX_LEVEL = 5;

    /**
     * The last scan, kept for the rest of the server's life.
     *
     * <p>Which recipes exist is settled when a world loads its data and does not change while it
     * runs. Cleared when the server stops, because the next one may load others.
     */
    private static List<TradePreview> cached = null;

    private TradeGoodsScanner() {
    }

    /** The scan, done once. Every call after the first is a field read. */
    public static List<TradePreview> cached(Level level) {
        if (cached == null) cached = scan(level);
        return cached;
    }

    /** Just the item ids from a set of offers, for the picker's "trade goods only" filter. */
    public static Set<String> itemIdsOf(List<TradePreview> offers) {
        Set<String> ids = new LinkedHashSet<>();
        for (TradePreview offer : offers) ids.addAll(offer.itemIds());
        return ids;
    }

    /** Forgets the scan. Called when the server stops. */
    public static void clearCache() {
        cached = null;
    }

    /**
     * Every distinct offer the merchant recipes were seen to produce.
     *
     * <p>Both halves of each are kept. A trade entry can gate either side, so throwing the prices
     * away would lose half the answers — and emeralds, which are the payment for nearly
     * everything.
     */
    public static List<TradePreview> scan(Level level) {
        // Keyed by what makes two rolls the same trade, so a recipe that rolls its own price does
        // not appear eight times at eight prices. First roll wins; see TradePreview#identity.
        Map<String, TradePreview> offers = new LinkedHashMap<>();
        // 26.2 trades are loot-driven data: rolling one needs a server level's loot context, and
        // the trade registry is not sent to clients. A client relies on the server's answer.
        if (!(level instanceof ServerLevel serverLevel)) return List.of();

        TradeScanBudget budget = new TradeScanBudget(System::currentTimeMillis);
        long startedAt = budget.now();
        // Someone for the recipes to be dealt to. Several of them ask what kind of villager they
        // are dealing with and hand back nothing for anything else. It never enters the world, and
        // it is never asked for its offers — that is the call that refuses.
        Villager trader = EntityType.VILLAGER.create(level, EntitySpawnReason.LOAD);
        if (trader == null) return List.of();
        int recipes = 0;

        Registry<TradeSet> tradeSets = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
        Holder<VillagerType> plains = level.registryAccess()
                .lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS);

        for (Holder.Reference<VillagerProfession> profession
                : level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).listElements().toList()) {
            if (budget.spent()) break;
            for (int merchantLevel = 1; merchantLevel <= MAX_LEVEL; merchantLevel++) {
                ResourceKey<TradeSet> setKey = profession.value().getTrades(merchantLevel);
                if (setKey == null) continue;
                TradeSet set = tradeSets.getValue(setKey);
                if (set == null) continue;
                // Set before running, because a recipe may read the trader's profession or level.
                trader.setVillagerData(new VillagerData(plains, profession, merchantLevel));
                recipes += run(set.getTrades(), tradeContext(serverLevel, trader), offers,
                        profession.key().identifier().toString(), merchantLevel, budget);
            }
        }

        for (ResourceKey<TradeSet> setKey : List.of(TradeSets.WANDERING_TRADER_BUYING,
                TradeSets.WANDERING_TRADER_COMMON, TradeSets.WANDERING_TRADER_UNCOMMON)) {
            if (budget.spent()) break;
            TradeSet set = tradeSets.getValue(setKey);
            if (set == null) continue;
            // All its tiers are level 1 as far as a lock is concerned, because a wandering trader
            // never has another.
            recipes += run(set.getTrades(), tradeContext(serverLevel, trader), offers,
                    TradePreview.WANDERING_TRADER, 1, budget);
        }

        trader.discard();
        // Only a server will hand over a merchant's offers, so on a client this part is left to
        // the server's answer.
        int ownTableMerchants = level.isClientSide()
                ? 0 : scanProfessionlessMerchants(level, offers, budget);
        // Timed because this runs while a screen opens, and "the game hiccuped once" is otherwise
        // a mystery nobody can attribute.
        DebugLogger.runtime(HsRuntimeCategories.TRADE_GOODS, "scan",
                "ran " + recipes + " merchant recipes and asked " + ownTableMerchants
                        + " merchants without a profession in " + (budget.now() - startedAt)
                        + "ms, found " + offers.size() + " distinct offers");
        if (budget.spent()) {
            DebugLogger.runtime(HsRuntimeCategories.TRADE_GOODS,
                    "the scan ran out of its " + TradeScanBudget.TOTAL_MILLIS + "ms and stopped "
                            + "where it was. Trades behind that point are missing from the picker; "
                            + "locking those items by hand still works.");
        }
        return List.copyOf(offers.values());
    }

    /** The loot context a merchant rolls its trades with, as {@code AbstractVillager} builds it. */
    private static LootContext tradeContext(ServerLevel level, Villager trader) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, trader.position())
                .withParameter(LootContextParams.THIS_ENTITY, trader)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE)).create(java.util.Optional.empty());
    }

    /** @return how many recipes were run */
    private static int run(HolderSet<VillagerTrade> listings, LootContext context,
                           Map<String, TradePreview> offers,
                           String professionId, int level, TradeScanBudget budget) {
        int ran = 0;
        for (Holder<VillagerTrade> holder : listings) {
            VillagerTrade listing = holder.value();
            if (searchesTheWorld(listing)) continue;
            if (budget.spent()) break;
            for (int attempt = 0; attempt < RUNS_PER_LISTING; attempt++) {
                long runStartedAt = budget.now();
                try {
                    MerchantOffer offer = listing.getOffer(context);
                    if (offer == null) continue;
                    TradePreview preview = previewOf(offer, professionId, level);
                    if (preview != null) offers.putIfAbsent(preview.identity(), preview);
                } catch (Throwable recipeRefused) {
                    // One mod's recipe will not run outside a real trade. Give up on that one and
                    // keep the rest — see the note on the class about why this is quiet.
                    rethrowIfFatal(recipeRefused);
                    break;
                }
                if (budget.tooSlow(runStartedAt)) {
                    // Not quiet, unlike the refusal above: this one cost real time and the pack
                    // author is the only person who can do anything about it.
                    DebugLogger.runtime(HsRuntimeCategories.TRADE_GOODS,
                            holder.getRegisteredName() + " took "
                                    + (budget.now() - runStartedAt) + "ms to name one offer and "
                                    + "was not asked again. A trade that searches the world for "
                                    + "its answer cannot be listed in the picker; lock the item "
                                    + "itself instead.");
                    break;
                }
            }
            ran++;
        }
        return ran;
    }

    /**
     * Merchants that have no profession and keep their trades to themselves, like Vinery's
     * wandering winemaker. Their recipes live in a private table of their own, so the only way in
     * is to make one and ask it — which is why this runs on the server only.
     *
     * <p>Nothing marks an entity type as a merchant, so every type is made once and looked at.
     * Each one that turns out to be a merchant is then made a few more times, because it is dealt
     * a random handful of its recipes and one roll would list only that handful.
     *
     * <p>The vanilla wandering trader is skipped: its recipes are already read above.
     *
     * @return how many merchant types were found
     */
    private static int scanProfessionlessMerchants(Level level, Map<String, TradePreview> offers,
                                                   TradeScanBudget budget) {
        int found = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (budget.spent()) break;
            if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER) continue;
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (typeId == null) continue;

            Entity probe = createQuietly(type, level);
            if (probe == null) continue;
            if (!(probe instanceof Merchant) || probe instanceof Villager) {
                probe.discard();
                continue;
            }
            found++;
            Entity merchant = probe;
            for (int attempt = 0; attempt < RUNS_PER_LISTING; attempt++) {
                if (attempt > 0) merchant = createQuietly(type, level);
                if (merchant == null) break;
                long runStartedAt = budget.now();
                boolean answered = rollOffers((Merchant) merchant, typeId.toString(), offers);
                merchant.discard();
                if (!answered || budget.tooSlow(runStartedAt) || budget.spent()) break;
            }
        }
        return found;
    }

    /** @return false when the merchant refused to deal its offers here */
    private static boolean rollOffers(Merchant merchant, String merchantKey,
                                      Map<String, TradePreview> offers) {
        try {
            for (MerchantOffer offer : merchant.getOffers()) {
                TradePreview preview = previewOf(offer, merchantKey, 1);
                if (preview != null) offers.putIfAbsent(preview.identity(), preview);
            }
            return true;
        } catch (Throwable offersRefused) {
            rethrowIfFatal(offersRefused);
            return false;
        }
    }

    /** One entity that never enters the world, or null when its type will not be made here. */
    private static Entity createQuietly(EntityType<?> type, Level level) {
        try {
            return type.create(level, EntitySpawnReason.LOAD);
        } catch (Throwable refused) {
            // Some modded types will not be made outside a real spawn. Not a merchant we can ask.
            rethrowIfFatal(refused);
            return null;
        }
    }

    /**
     * Refusals are not always exceptions. Under Sinytra Connector a Fabric mixin on a vanilla
     * trade recipe can fail to apply and throw a mixin {@link Error} the first time the recipe
     * runs, which used to take the whole editor down with it. Running out of memory or stack is
     * still everyone's problem, so that is passed on.
     */
    private static void rethrowIfFatal(Throwable thrown) {
        if (thrown instanceof VirtualMachineError fatal) throw fatal;
    }

    /**
     * A cartographer's treasure map answers "what do you sell" by going and looking: it searches
     * the world for the structure the map would point at, generating terrain until it finds one.
     * On a large pack that took eighteen seconds here — on the thread that also throws ender
     * pearls and runs commands, so the whole server stood still — and it happened the first time
     * anyone opened the editor, once per world.
     *
     * <p>Left out rather than made cheaper, because there is no cheap way to ask: the offer has no
     * answer until the search finishes. The maps are missing from the trade picker as a result;
     * locking one by its item still works.
     */
    private static boolean searchesTheWorld(VillagerTrade listing) {
        // 26.2 makes the map through an item modifier on a data-driven trade. The field is
        // private; 26.2 is not obfuscated, so the name holds on both loaders.
        if (GIVEN_ITEM_MODIFIERS == null) return false;
        try {
            if (!(GIVEN_ITEM_MODIFIERS.get(listing) instanceof List<?> modifiers)) return false;
            for (Object modifier : modifiers) {
                if (modifier instanceof ExplorationMapFunction) return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static final java.lang.reflect.Field GIVEN_ITEM_MODIFIERS = givenItemModifiersField();

    private static java.lang.reflect.Field givenItemModifiersField() {
        try {
            java.lang.reflect.Field field = VillagerTrade.class.getDeclaredField("givenItemModifiers");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Null when the offer hands over nothing recognisable, which no lock could name anyway. */
    private static TradePreview previewOf(MerchantOffer offer, String professionId, int level) {
        String resultId = idOf(offer.getResult());
        if (resultId == null) return null;
        String costAId = idOf(offer.getCostA());
        String costBId = idOf(offer.getCostB());
        return new TradePreview(professionId, level,
                resultId, offer.getResult().getCount(),
                costAId, costAId == null ? 0 : offer.getCostA().getCount(),
                costBId, costBId == null ? 0 : offer.getCostB().getCount());
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }
}
