package com.hypcro.mixins;

import com.hypcro.farming.WSFarmEngine;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
        int key = event.key();
        if (!WSFarmEngine.INSTANCE.isRunning()) {
            return;
        }

        // Always allow ESC (Menu) and our toggle key to pass through
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (action == GLFW.GLFW_PRESS) {
                WSFarmEngine.INSTANCE.stopMacro("Opened Pause Menu");
            }
            return;
        }
        if (com.hypcro.HypCroMod.INSTANCE.getOpenGuiKey().matches(event)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        // Allow tab / player list view
        if (client.options.keyPlayerList.matches(event)) {
            return;
        }

        // Catch the inventory key dynamically from user settings!
        if (client.options.keyInventory.matches(event)) {
            // We only want to process the press down, not the release
            if (action == GLFW.GLFW_PRESS) {
                WSFarmEngine.INSTANCE.stopMacro("Open inventory");
            }
            // We allow the key through so the inventory actually opens after stopping the macro
            return;
        }

        // If macro is running and it's not an excluded key, eat the input
        ci.cancel();
    }
}
