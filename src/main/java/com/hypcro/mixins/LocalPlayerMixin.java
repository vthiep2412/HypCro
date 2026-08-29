package com.hypcro.mixins;

import com.hypcro.config.ConfigManager;
import com.hypcro.farming.MacroInputController;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayer.class, priority = 500)
public class LocalPlayerMixin {

    @ModifyExpressionValue(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z")
    )
    private boolean overrideSprintInput(boolean original) {
        if (MacroInputController.INSTANCE.isInputAllowed()) {
            return MacroInputController.INSTANCE.getSprint();
        }
        // When no macro is active, respect the autoSprint config
        return ConfigManager.INSTANCE.getConfig().getQolConfig().getAutoSprint() || original;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (MacroInputController.INSTANCE.isInputAllowed() && !MacroInputController.INSTANCE.getSprint()) {
            ((LocalPlayer) (Object) this).setSprinting(false);
        }
    }
}