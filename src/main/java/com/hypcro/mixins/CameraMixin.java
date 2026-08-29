package com.hypcro.mixins;

import com.hypcro.camera.FreeLookManager;
import com.hypcro.camera.FreecamManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyYaw(float yRot) {
        if (FreecamManager.INSTANCE.isFreecamActive()) {
            return FreecamManager.INSTANCE.getFreecamYaw();
        }
        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            return FreeLookManager.INSTANCE.getFreeYaw();
        }
        return yRot;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float modifyPitch(float xRot) {
        if (FreecamManager.INSTANCE.isFreecamActive()) {
            return FreecamManager.INSTANCE.getFreecamPitch();
        }
        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            return FreeLookManager.INSTANCE.getFreePitch();
        }
        return xRot;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onGetMaxZoom(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        if (FreecamManager.INSTANCE.isFreecamActive()) {
            cir.setReturnValue(0.0f);
            return;
        }
        if (FreeLookManager.INSTANCE.isFreeLookActive()) {
            // Bypass vanilla block collision raycasting!
            cir.setReturnValue(FreeLookManager.INSTANCE.getCameraDistance());
        }
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void onIsDetached(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamManager.INSTANCE.isFreecamActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntity(float partialTicks, CallbackInfo ci) {
        if (FreecamManager.INSTANCE.isFreecamActive()) {
            setPosition(
                FreecamManager.INSTANCE.getRenderX(partialTicks),
                FreecamManager.INSTANCE.getRenderY(partialTicks),
                FreecamManager.INSTANCE.getRenderZ(partialTicks)
            );
            setRotation(
                FreecamManager.INSTANCE.getFreecamYaw(),
                FreecamManager.INSTANCE.getFreecamPitch()
            );
        }
    }
}
