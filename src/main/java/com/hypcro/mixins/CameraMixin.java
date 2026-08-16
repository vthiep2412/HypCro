package com.hypcro.mixins;

import com.hypcro.camera.FreeLookManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyYaw(float yRot) {
        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            return FreeLookManager.INSTANCE.getFreeYaw();
        }
        return yRot;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float modifyPitch(float xRot) {
        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            return FreeLookManager.INSTANCE.getFreePitch();
        }
        return xRot;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onGetMaxZoom(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            // Bypass vanilla block collision raycasting!
            cir.setReturnValue(FreeLookManager.INSTANCE.getCameraDistance());
        }
    }
}
