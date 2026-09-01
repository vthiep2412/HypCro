package com.hypcro.mixins;

import com.hypcro.failsafe.HypcroWatchdog;
import com.hypcro.farming.MacroController;
import com.hypcro.farming.MacroInputController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Invoker("startAttack")
    public abstract boolean invokeStartAttack();

    @Invoker("continueAttack")
    public abstract void invokeContinueAttack(boolean down);

    @Invoker("startUseItem")
    public abstract void invokeStartUseItem();

    @Accessor("missTime")
    public abstract void setMissTime(int time);

    @Accessor("rightClickDelay")
    public abstract int getRightClickDelay();

    @Accessor("rightClickDelay")
    public abstract void setRightClickDelay(int delay);

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

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (MacroController.INSTANCE.isRunning()) {
            boolean isChatOpen = client.gui.screen() instanceof ChatScreen;
            boolean isGameActive = client.gui.screen() == null;

            if (isChatOpen || isGameActive) {
                boolean attack = MacroInputController.INSTANCE.getAttack();
                boolean useItem = MacroInputController.INSTANCE.getUseItem();

                if (attack) {
                    setMissTime(0);
                }

            // Keep key mapping states synchronized so Minecraft tick routines do not release item usage
                client.options.keyAttack.setDown(attack);
                client.options.keyUse.setDown(useItem);

                // If in chat, vanilla handleKeybinds is skipped, so we drive the attack and use loops manually
                if (isChatOpen) {
                    if (attack) {
                        if (client.gameMode != null && !client.gameMode.isDestroying()) {
                            invokeStartAttack();
                        }
                        invokeContinueAttack(true);
                    } else {
                        if (client.gameMode != null && client.gameMode.isDestroying()) {
                            client.gameMode.stopDestroyBlock();
                        }
                    }

                    if (useItem) {
                        boolean isDestroying = client.gameMode != null && client.gameMode.isDestroying();
                        if (getRightClickDelay() == 0 && client.player != null && !client.player.isUsingItem() && !isDestroying) {
                            invokeStartUseItem();
                        }
                    } else {
                        if (client.player != null && client.player.isUsingItem() && client.gameMode != null) {
                            client.gameMode.releaseUsingItem(client.player);
                        }
                    }
                }
            }
        }
    }
}

