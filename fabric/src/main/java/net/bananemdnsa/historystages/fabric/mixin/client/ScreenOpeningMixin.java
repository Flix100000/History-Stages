package net.bananemdnsa.historystages.fabric.mixin.client;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.ScreenEvent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the screen-opening event NeoForge fires from {@code setScreen}, which lives on {@code Gui}
 * since 26.x. Closing (a null screen) is not reported, the same as NeoForge's.
 */
@Mixin(Gui.class)
public abstract class ScreenOpeningMixin {

    @Shadow
    public abstract Screen screen();

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(@Nullable Screen newScreen, CallbackInfo ci) {
        if (newScreen == null || !Phases.wanted(ScreenEvent.Opening.class)) return;
        if (EventBus.post(new ScreenEvent.Opening(screen(), newScreen)).isCanceled()) ci.cancel();
    }
}
