package com.hypcro.mixins;

import com.hypcro.camera.FreeLookManager;
import com.hypcro.farming.MacroController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }

        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            // Smooth zoom scroll
            FreeLookManager.INSTANCE.onMouseScroll(vertical);
            ci.cancel();
            return;
        }

        if (MacroController.isAnyMacroActive()) {
            // Block scrolling during macro / navigation
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        if (com.hypcro.camera.FreecamManager.INSTANCE.isFreecamActive()) {
            double sens = Minecraft.getInstance().options.sensitivity().get();
            OptionsAccessor options = (OptionsAccessor) Minecraft.getInstance().options;
            boolean invertX = options.getInvertXMouse().get();
            boolean invertY = options.getInvertYMouse().get();

            double dx = this.accumulatedDX;
            double dy = this.accumulatedDY;
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;

            com.hypcro.camera.FreecamManager.INSTANCE.onMouseTurn(dx, dy, sens, invertX, invertY);
            ci.cancel();
            return;
        }

        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            double sens = Minecraft.getInstance().options.sensitivity().get();
            OptionsAccessor options = (OptionsAccessor) Minecraft.getInstance().options;
            boolean invertX = options.getInvertXMouse().get();
            boolean invertY = options.getInvertYMouse().get();

            // Read and immediately apply mouse movement to FreeLookManager
            double dx = this.accumulatedDX;
            double dy = this.accumulatedDY;
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;

            FreeLookManager.INSTANCE.onMouseTurn(dx, dy, sens, invertX, invertY);
            ci.cancel();
            return;
        }

        boolean isAutomated = MacroController.isAnyMacroActive();
        if (isAutomated) {
            boolean lockMouse = com.hypcro.config.ConfigManager.INSTANCE.getConfig().getGeneralConfig().getInputLock().getLockMouse();
            if (lockMouse) {
                this.accumulatedDX = 0.0;
                this.accumulatedDY = 0.0;
                ci.cancel();
            }
            // Intended: Do not notify watchdog here. Any abrupt rotation while macroing must be caught by failsafe.
        }
    }
}
