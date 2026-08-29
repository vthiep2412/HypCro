package com.hypcro.mixins;

import com.hypcro.camera.FreecamManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {

    @Redirect(
        method = "emitGizmos",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"
        )
    )
    private CameraType onGetCameraType(Options options) {
        if (FreecamManager.INSTANCE.isFreecamActive()) {
            return CameraType.THIRD_PERSON_BACK;
        }
        return options.getCameraType();
    }
}
