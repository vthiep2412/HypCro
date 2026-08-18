package com.hypcro.mixins;

import com.hypcro.farming.MacroController;
import com.hypcro.farming.MacroInputController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (MacroController.INSTANCE.isRunning() && MacroInputController.INSTANCE.canPenetrateScreen()) {
            this.keyPresses = MacroInputController.INSTANCE.createInput();
            this.moveVector = MacroInputController.INSTANCE.calculateMoveVector();
        }
    }
}
