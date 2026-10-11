package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.api.diagnostics.Kind;
import net.bananemdnsa.historystages.api.diagnostics.RuntimeCategory;

/** History Stages' own runtime log categories. Addons make their own with {@link RuntimeCategory#of}. */
public final class HsRuntimeCategories {

    private HsRuntimeCategories() {}

    private static RuntimeCategory hs(String name, String label, Kind kind) {
        return RuntimeCategory.of("historystages:" + name, label, kind);
    }

    public static final RuntimeCategory ITEM_USE_LOCK = hs("item_use_lock", "Item Use Lock", Kind.LOCK);
    public static final RuntimeCategory ENTITY_ITEM_LOCK = hs("entity_item_lock", "Entity Item Lock", Kind.LOCK);
    public static final RuntimeCategory ENTITY_INTERACTION_LOCK = hs("entity_interaction_lock", "Entity Interaction Lock", Kind.LOCK);
    public static final RuntimeCategory ENCHANTMENT_LOCK = hs("enchantment_lock", "Enchantment Lock", Kind.LOCK);
    public static final RuntimeCategory TRADE_LOCK = hs("trade_lock", "Trade Lock", Kind.LOCK);
    public static final RuntimeCategory FLUID_LOCK = hs("fluid_lock", "Fluid Lock", Kind.LOCK);
    public static final RuntimeCategory BLOCK_LOCK = hs("block_lock", "Block Lock", Kind.LOCK);
    public static final RuntimeCategory CONTAINER_LOCK = hs("container_lock", "Container Lock", Kind.LOCK);
    public static final RuntimeCategory INVENTORY = hs("inventory", "Inventory", Kind.LOCK);
    public static final RuntimeCategory MOB_ATTACK_LOCK = hs("mob_attack_lock", "Mob Attack Lock", Kind.LOCK);
    public static final RuntimeCategory MOB_LOOT_LOCK = hs("mob_loot_lock", "Mob Loot Lock", Kind.LOCK);
    public static final RuntimeCategory LOOT_LOCK = hs("loot_lock", "Loot Lock", Kind.LOCK);
    public static final RuntimeCategory STRUCTURE_LOCK = hs("structure_lock", "Structure Lock", Kind.LOCK);
    public static final RuntimeCategory BIOME_LOCK = hs("biome_lock", "Biome Lock", Kind.LOCK);
    public static final RuntimeCategory DIMENSION_LOCK = hs("dimension_lock", "Dimension Lock", Kind.LOCK);
    public static final RuntimeCategory ZONE_LOCK = hs("zone_lock", "Zone Lock", Kind.LOCK);
    public static final RuntimeCategory CURIOS_LOCK = hs("curios_lock", "Curios Lock", Kind.LOCK);
    public static final RuntimeCategory ACCESSORIES_LOCK = hs("accessories_lock", "Accessories Lock", Kind.LOCK);

    public static final RuntimeCategory STAGE = hs("stage", "Stage", Kind.STAGE);
    public static final RuntimeCategory RELOCK = hs("relock", "Re-lock", Kind.STAGE);
    public static final RuntimeCategory STAGE_LOGIC = hs("stage_logic", "Stage Logic", Kind.STAGE);
    public static final RuntimeCategory TEAM_SHARE = hs("team_share", "Team Share", Kind.STAGE);

    public static final RuntimeCategory STAGE_UNLOCK = hs("stage_unlock", "Stage Unlock", Kind.ADMIN);
    public static final RuntimeCategory STAGE_LOCK = hs("stage_lock", "Stage Lock", Kind.ADMIN);
    public static final RuntimeCategory INDIVIDUAL_UNLOCK = hs("individual_unlock", "Individual Unlock", Kind.ADMIN);
    public static final RuntimeCategory INDIVIDUAL_LOCK = hs("individual_lock", "Individual Lock", Kind.ADMIN);
    public static final RuntimeCategory TEMPORARY = hs("temporary", "Temporary", Kind.ADMIN);
    public static final RuntimeCategory RELOAD = hs("reload", "Reload", Kind.ADMIN);
    public static final RuntimeCategory STAGE_SAVE = hs("stage_save", "Stage Save", Kind.ADMIN);
    public static final RuntimeCategory STAGE_DELETE = hs("stage_delete", "Stage Delete", Kind.ADMIN);
    public static final RuntimeCategory STAGE_FOLDERS = hs("stage_folders", "Stage Folders", Kind.ADMIN);

    public static final RuntimeCategory SERVER = hs("server", "Server", Kind.SYSTEM);
    public static final RuntimeCategory PLAYER_LOGIN = hs("player_login", "Player Login", Kind.SYSTEM);
    public static final RuntimeCategory TRADE_GOODS = hs("trade_goods", "Trade Goods", Kind.SYSTEM);
    public static final RuntimeCategory NBT_MATCHING = hs("nbt_matching", "NBT Matching", Kind.SYSTEM);
    public static final RuntimeCategory FLUID_CONTENT = hs("fluid_content", "Fluid Content", Kind.SYSTEM);
    public static final RuntimeCategory STRUCTURE_CACHE = hs("structure_cache", "Structure Cache", Kind.SYSTEM);
}
