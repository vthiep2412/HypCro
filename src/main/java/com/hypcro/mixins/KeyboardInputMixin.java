package com.hypcro.mixins;

import com.hypcro.farming.MacroController;
import com.hypcro.farming.MacroInputController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (com.hypcro.camera.FreecamManager.INSTANCE.isFreecamActive() && !MacroInputController.INSTANCE.isInputAllowed()) {
            this.keyPresses = new Input(false, false, false, false, false, false, false);
            this.moveVector = Vec2.ZERO;
            return;
        }

        if (MacroInputController.INSTANCE.isInputAllowed()) {
            boolean lockMovement = com.hypcro.config.ConfigManager.INSTANCE.getConfig().getGeneralConfig().getInputLock().getLockMovement();
            if (lockMovement) {
                this.keyPresses = MacroInputController.INSTANCE.createInput();
                this.moveVector = MacroInputController.INSTANCE.calculateMoveVector();
            } else {
                // When lockMovement is OFF, blend manual physical keys with macro virtual keys
                boolean f = (this.keyPresses != null && this.keyPresses.forward()) || MacroInputController.INSTANCE.getForward();
                boolean b = (this.keyPresses != null && this.keyPresses.backward()) || MacroInputController.INSTANCE.getBackward();
                boolean l = (this.keyPresses != null && this.keyPresses.left()) || MacroInputController.INSTANCE.getLeft();
                boolean r = (this.keyPresses != null && this.keyPresses.right()) || MacroInputController.INSTANCE.getRight();
                boolean j = (this.keyPresses != null && this.keyPresses.jump()) || MacroInputController.INSTANCE.getJump();
                boolean s = (this.keyPresses != null && this.keyPresses.shift()) || MacroInputController.INSTANCE.getShift();
                boolean sp = (this.keyPresses != null && this.keyPresses.sprint()) || MacroInputController.INSTANCE.getSprint();
                this.keyPresses = new Input(f, b, l, r, j, s, sp);

                float forwardImpulse = 0.0f;
                if (f) forwardImpulse += 1.0f;
                if (b) forwardImpulse -= 1.0f;
                float leftImpulse = 0.0f;
                if (l) leftImpulse += 1.0f;
                if (r) leftImpulse -= 1.0f;
                this.moveVector = new Vec2(leftImpulse, forwardImpulse);
            }
        }
    }
}
