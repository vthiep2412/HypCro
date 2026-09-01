package com.hypcro.mixins;

import com.hypcro.failsafe.HypcroWatchdog;
import com.hypcro.farming.MacroController;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // Allow the pause (ESC) menu and ChatScreen no matter what, for safety and user interaction!
        if (screen instanceof PauseScreen || screen instanceof ChatScreen) {
            return;
        }

        boolean checkUnfamiliarGui = com.hypcro.config.ConfigManager.INSTANCE.getConfig().getGeneralConfig().getWatchdog().getCheckUnfamiliarGui();
        if (checkUnfamiliarGui && MacroController.INSTANCE.isFarmingActive() && screen != null) {
            // A GUI is attempting to open while the macro is running and unfamiliar GUI check is enabled.
            // We do NOT block the screen from opening so the user can see captchas/menus, but we trigger the staff alarm!
            HypcroWatchdog.INSTANCE.potentialStaffCheck("Unfamiliar GUI opened: " + screen.getClass().getSimpleName());
        }
    }
}
