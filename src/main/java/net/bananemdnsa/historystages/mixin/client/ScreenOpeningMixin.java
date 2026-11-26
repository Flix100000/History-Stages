package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.ScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raises the screen-opening event before a screen is shown. Closing to the world raises nothing, as on NeoForge. */
@Mixin(Minecraft.class)
public abstract class ScreenOpeningMixin {

    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(@Nullable Screen newScreen, CallbackInfo ci) {
        if (newScreen == null) return;
        if (EventBus.post(new ScreenEvent.Opening(screen, newScreen)).isCanceled()) ci.cancel();
    }
}
