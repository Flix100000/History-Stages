package net.bananemdnsa.historystages.platform.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Which projectile a weapon is about to use. A handler may swap it, or empty it for "no ammo". */
public class LivingGetProjectileEvent extends LivingEvent {

    private final ItemStack projectileWeaponItemStack;
    private ItemStack projectileItemStack;

    public LivingGetProjectileEvent(LivingEntity entity, ItemStack projectileWeaponItemStack,
                                    ItemStack projectileItemStack) {
        super(entity);
        this.projectileWeaponItemStack = projectileWeaponItemStack;
        this.projectileItemStack = projectileItemStack;
    }

    public ItemStack getProjectileWeaponItemStack() {
        return projectileWeaponItemStack;
    }

    public ItemStack getProjectileItemStack() {
        return projectileItemStack;
    }

    public void setProjectileItemStack(ItemStack projectileItemStack) {
        this.projectileItemStack = projectileItemStack;
    }
}
