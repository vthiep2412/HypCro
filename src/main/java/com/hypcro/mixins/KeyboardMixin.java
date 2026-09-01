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
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }

        int key = event.key();


        if (!com.hypcro.farming.MacroInputController.isAnyMacroRunning()) {
            return;
        }

        // Always allow ESC (Menu) and our toggle key to pass through
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (action == GLFW.GLFW_PRESS) {
                com.hypcro.farming.MacroController.INSTANCE.stopAllMacros("Opened Pause Menu");
            }
            return;
        }
        if (com.hypcro.HypCroMod.INSTANCE.openGuiKey.matches(event)) {
            return;
        }
        if (com.hypcro.HypCroMod.INSTANCE.freeLookKey.matches(event)) {
            return;
        }
        if (com.hypcro.HypCroMod.INSTANCE.freecamKey.matches(event)) {
            return;
        }
        // Allow F1 (Hide HUD), F3 (Debug Screen), and F11 (Fullscreen) to pass through
        if (key == GLFW.GLFW_KEY_F1 || key == GLFW.GLFW_KEY_F3 || key == GLFW.GLFW_KEY_F11) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        com.hypcro.config.InputLockConfig lockConfig = com.hypcro.config.ConfigManager.INSTANCE.getConfig().getGeneralConfig().getInputLock();

        // Catch the inventory key dynamically from user settings first!
        if (client.options.keyInventory.matches(event)) {
            // We only want to process the press down, not the release
            if (action == GLFW.GLFW_PRESS) {
                com.hypcro.farming.MacroController.INSTANCE.stopAllMacros("Open inventory");
            }
            // We allow the key through so the inventory actually opens after stopping the macro
            return;
        }

        // Allow tab / player list view
        if (client.options.keyPlayerList.matches(event)) {
            return;
        }

        // Allow opening chat during macro
        if (client.options.keyChat.matches(event)) {
            return;
        }

        // 1. Hotbar lock check
        if (lockConfig.getLockHotbar()) {
            for (net.minecraft.client.KeyMapping hotbarKey : client.options.keyHotbarSlots) {
                if (hotbarKey.matches(event)) {
                    ci.cancel();
                    return;
                }
            }
        }

        // 2. Movement lock check
        if (lockConfig.getLockMovement()) {
            if (client.options.keyUp.matches(event) ||
                client.options.keyDown.matches(event) ||
                client.options.keyLeft.matches(event) ||
                client.options.keyRight.matches(event) ||
                client.options.keyJump.matches(event) ||
                client.options.keyShift.matches(event) ||
                client.options.keySprint.matches(event)) {
                ci.cancel();
                return;
            }
        }

        // 3. Lock all other keybinds (Intended: requires both hotbar and movement lock to be ON)
        if (lockConfig.getLockAllOtherKeybinds() && lockConfig.getLockHotbar() && lockConfig.getLockMovement()) {
            ci.cancel();
        }
    }
}
