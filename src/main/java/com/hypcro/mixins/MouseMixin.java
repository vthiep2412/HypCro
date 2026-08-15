package com.hypcro.mixins;

import com.hypcro.farming.WSFarmEngine;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (WSFarmEngine.INSTANCE.isRunning()) {
            // Completely block scrolling (prevents hotbar slot switching)
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        if (WSFarmEngine.INSTANCE.isRunning()) {
            // Completely block all camera turning from physical mouse movement!
            ci.cancel();
        }
    }
}
