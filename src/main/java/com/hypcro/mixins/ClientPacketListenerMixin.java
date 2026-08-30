package com.hypcro.mixins;

import com.hypcro.HypCroMod;
import com.hypcro.camera.FreeLookManager;
import com.hypcro.farming.MacroController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                com.hypcro.failsafe.HypcroWatchdog.INSTANCE.onPacketTeleport(packet);
                if (com.hypcro.camera.FreecamManager.INSTANCE.isFreecamActive()) {
                    com.hypcro.camera.FreecamManager.INSTANCE.disable(client);
                }
            } catch (Throwable t) {
                HypCroMod.INSTANCE.logWarn("Error processing MovePlayer packet safely: " + t.getMessage());
            }
        });
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void onHandleRespawn(net.minecraft.network.protocol.game.ClientboundRespawnPacket packet, CallbackInfo ci) {
        handleServerTransferReset();
    }

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void onHandleLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket packet, CallbackInfo ci) {
        handleServerTransferReset();
    }

    private void handleServerTransferReset() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                com.hypcro.util.CropBpsTracker.INSTANCE.resetSession();
            } catch (Throwable t) {
                HypCroMod.INSTANCE.logWarn("Error resetting BPS session on server transfer: " + t.getMessage());
            }

            try {
                com.hypcro.pest.PestTargetTracker.INSTANCE.clearSessionMemory();
            } catch (Throwable t) {
                HypCroMod.INSTANCE.logWarn("Error clearing pest session memory on server transfer: " + t.getMessage());
            }

            try {
                if (com.hypcro.camera.FreecamManager.INSTANCE.isFreecamActive()) {
                    com.hypcro.camera.FreecamManager.INSTANCE.disable(client);
                }
            } catch (Throwable t) {
                HypCroMod.INSTANCE.logWarn("Error disabling freecam on server transfer: " + t.getMessage());
            }

            try {
                if (com.hypcro.farming.MacroController.INSTANCE.isRunning() &&
                    !com.hypcro.pest.PestDestroyerEngine.INSTANCE.isRunning()) {
                    com.hypcro.failsafe.HypcroWatchdog.INSTANCE.potentialStaffCheck("Server Transfer Detected (Staff / Reboot / Hub)");
                }
            } catch (Throwable t) {
                HypCroMod.INSTANCE.logWarn("Error handling transfer watchdog check: " + t.getMessage());
            }
        });
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        boolean blockChat = com.hypcro.config.ConfigManager.INSTANCE.getConfig().getGeneralConfig().getInputLock().getBlockChatAndCommands();
        if (blockChat && com.hypcro.farming.MacroInputController.isAnyMacroRunning()) {
            String target = message.length() < 30 ? message : "chat";
            HypCroMod.INSTANCE.logWarn("Your \"" + target + "\" message is blocked, macro in active.");
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        boolean blockChat = com.hypcro.config.ConfigManager.INSTANCE.getConfig().getGeneralConfig().getInputLock().getBlockChatAndCommands();
        if (blockChat && com.hypcro.farming.MacroInputController.isAnyMacroRunning()) {
            String target = ("/" + command).length() < 30 ? ("/" + command) : "chat";
            HypCroMod.INSTANCE.logWarn("Your \"" + target + "\" command is blocked, macro in active.");
            ci.cancel();
        }
    }
}
