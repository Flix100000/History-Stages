package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class ContainerClickMixin {

    private static final String FEEDBACK_CATEGORY = "container";

    /**
     * Taking an item out of a container is the same acquisition the ground-pickup handler gates,
     * so it asks the same action rather than "is this item mentioned anywhere" — otherwise an
     * entry narrowed to {@code recipe} still froze every slot holding it (Issue #117).
     *
     * <p>It asks about what the click moves into the player's hands, not about whatever lies in
     * the clicked slot. Going by the slot froze sorting inside the player's own inventory and
     * blocked throwing items away, while a number-key swap onto an empty slot never got looked
     * at (Issue #122).
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!Config.GAMEPLAY.lockContainerInteraction.get()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // The trade window is a container too, and until the eleventh action existed this line
        // was the only thing standing between a player and a gated item on a merchant's counter.
        // It asked "pickup", which is the wrong question now: an entry narrowed to pickup would
        // still block trading it never meant to, and one narrowed to trade would not block the
        // trade it does mean. Trading is guarded before the result is ever produced - see
        // MerchantContainerMixin - so this one steps aside entirely.
        if (menu instanceof MerchantMenu) return;

        // Validate slot index
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        ItemStack stack = historystages$takenFromStorage(menu, menu.slots.get(slotId), clickType, serverPlayer);
        if (stack.isEmpty()) return;

        if (StageLockHelper.isActionLockedByIndividualStage(stack, serverPlayer.getUUID(), "pickup")) {
            ci.cancel();

            ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Container Lock", "container_" + serverPlayer.getUUID() + "_" + itemRL,
                    "<" + serverPlayer.getName().getString() + "> Interaction with locked item '" + itemRL + "' in container blocked");

            LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.itemLocked());
        }
    }

    /**
     * Blocks container clicks that would equip an item locked by the "equip" action
     * (armor and offhand slots). Catches rapid-fire clicks from helper mods like
     * Mouse Tweaks that bypass the post-hoc {@code LivingEquipmentChangeEvent} revert
     * by racing the client-side prediction.
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void historystages$onEquipLockClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!Config.GAMEPLAY.lockItemUsage.get() && !Config.GAMEPLAY.individualLockItemUsage.get()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        Slot slot = menu.slots.get(slotId);

        ItemStack candidate = ItemStack.EMPTY;
        switch (clickType) {
            case PICKUP -> {
                if (historystages$isPlayerEquipmentSlot(slot, serverPlayer)) {
                    candidate = menu.getCarried();
                }
            }
            case QUICK_MOVE -> {
                if (menu instanceof InventoryMenu
                        && !historystages$isPlayerEquipmentSlot(slot, serverPlayer)) {
                    ItemStack source = slot.getItem();
                    EquipmentSlot natural = serverPlayer.getEquipmentSlotForItem(source);
                    if (!source.isEmpty()
                            && (natural.getType() == EquipmentSlot.Type.ARMOR || natural == EquipmentSlot.OFFHAND)
                            && serverPlayer.getItemBySlot(natural).isEmpty()) {
                        candidate = source;
                    }
                }
            }
            case SWAP -> {
                if (historystages$isPlayerEquipmentSlot(slot, serverPlayer)) {
                    Inventory inv = serverPlayer.getInventory();
                    if (button == 40) {
                        candidate = inv.offhand.get(0);
                    } else if (button >= 0 && button < inv.items.size()) {
                        candidate = inv.items.get(button);
                    }
                }
            }
            default -> { return; }
        }

        if (candidate.isEmpty()) return;
        if (!historystages$isEquipActionLocked(candidate, serverPlayer)) return;

        ci.cancel();

        ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(candidate.getItem());
        DebugLogger.runtimeThrottled("Item Use Lock", "equip_click_" + serverPlayer.getUUID() + "_" + itemRL,
                "<" + serverPlayer.getName().getString() + "> Equip via container click for '" + itemRL + "' blocked [action: equip]");

        LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.itemLocked());
    }

    /**
     * The stack this click would move out of storage that is not the player's own, or empty if
     * it moves nothing that way. Mirrors the branches of {@code AbstractContainerMenu.doClick}.
     *
     * <p>A backpack or any other modded storage counts as foreign even though it travels with the
     * player: its slots are not the player's inventory, and trusting a mod's container to say
     * otherwise would turn every storage mod into a way around the lock.
     */
    @Unique
    private static ItemStack historystages$takenFromStorage(AbstractContainerMenu menu, Slot slot,
                                                           ClickType clickType, ServerPlayer player) {
        switch (clickType) {
            case PICKUP -> {
                if (historystages$isPlayerInventory(slot, player)) return ItemStack.EMPTY;
                ItemStack inSlot = slot.getItem();
                ItemStack carried = menu.getCarried();
                // Adding to a stack of the same item puts things in and takes nothing out. Any
                // other click on a filled slot ends with its item on the cursor.
                if (ItemStack.isSameItemSameTags(inSlot, carried) && slot.mayPlace(carried)) {
                    return ItemStack.EMPTY;
                }
                return inSlot;
            }
            case QUICK_MOVE, SWAP, CLONE -> {
                return historystages$isPlayerInventory(slot, player) ? ItemStack.EMPTY : slot.getItem();
            }
            case PICKUP_ALL -> {
                // The double click gathers matching stacks from every slot in the menu, so the
                // clicked one says nothing - it is usually empty.
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) return ItemStack.EMPTY;
                for (Slot other : menu.slots) {
                    if (!historystages$isPlayerInventory(other, player)
                            && other.hasItem()
                            && ItemStack.isSameItemSameTags(other.getItem(), carried)
                            && other.mayPickup(player)
                            && menu.canTakeItemForPickAll(carried, other)) {
                        return carried;
                    }
                }
                return ItemStack.EMPTY;
            }
            default -> {
                return ItemStack.EMPTY;
            }
        }
    }

    @Unique
    private static boolean historystages$isPlayerInventory(Slot slot, ServerPlayer player) {
        return slot.container == player.getInventory();
    }

    @Unique
    private static boolean historystages$isPlayerEquipmentSlot(Slot slot, ServerPlayer player) {
        if (slot.container != player.getInventory()) return false;
        int idx = slot.getContainerSlot();
        return idx >= 36 && idx <= 40;
    }

    @Unique
    private static boolean historystages$isEquipActionLocked(ItemStack stack, ServerPlayer player) {
        return (Config.GAMEPLAY.lockItemUsage.get()
                    && StageLockHelper.isActionLockedForPlayer(stack, player.getUUID(), "equip"))
                || (Config.GAMEPLAY.individualLockItemUsage.get()
                    && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), "equip"));
    }
}
