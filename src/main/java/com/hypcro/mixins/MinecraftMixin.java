package com.hypcro.mixins;

import com.hypcro.failsafe.HypcroWatchdog;
import com.hypcro.farming.WSFarmEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // Allow the pause (ESC) menu no matter what, for safety!
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            return;
        }

        if (WSFarmEngine.INSTANCE.isFarmingActive() && screen != null) {
            // A GUI is attempting to open while the macro is running.
            // Since we intercept manual E presses in KeyboardMixin and stop the macro first,
            // reaching here means it was either forced by the server or an unhandled key.
            // We block the screen from opening and trigger the staff alarm!
            HypcroWatchdog.INSTANCE.potentialStaffCheck("Unexpected GUI opened: " + screen.getClass().getSimpleName());
            ci.cancel();
        }
    }
}
