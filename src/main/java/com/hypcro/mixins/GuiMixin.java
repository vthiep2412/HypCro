package com.hypcro.mixins;

import com.hypcro.HypCroMod;
import com.hypcro.gui.HudOverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.Unique;

@Mixin(Gui.class)
public class GuiMixin {

    @Unique
    private static boolean hypcro$hudRenderFailed = false;

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void hypcro$hideHotbarInFreecam(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (com.hypcro.camera.FreecamManager.INSTANCE.isFreecamActive() &&
            com.hypcro.config.ConfigManager.INSTANCE.getConfig().getQolConfig().getFreecamHideGui()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hypcro$renderHudOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            // Per-frame entity scanning for Pest and Dungeon ESP (throttled to 90 Hz via SCAN_INTERVAL_MS)
            try {
                com.hypcro.pest.PestESP.INSTANCE.tick(client);
            } catch (Throwable t) {
                // Log once or ignore to avoid spamming render thread
            }
            try {
                com.hypcro.dungeon.DungeonESP.INSTANCE.tick(client);
            } catch (Throwable t) {
                // Log once or ignore to avoid spamming render thread
            }
        }

        try {
            HudOverlayRenderer.INSTANCE.render(guiGraphics, deltaTracker);
            hypcro$hudRenderFailed = false;
        } catch (Throwable t) {
            if (!hypcro$hudRenderFailed) {
                hypcro$hudRenderFailed = true;
                HypCroMod.INSTANCE.logError("HUD overlay render failed: " + t);
            }
        }
    }
}
