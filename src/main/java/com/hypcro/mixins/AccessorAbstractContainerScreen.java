package com.hypcro.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin invoker for {@link AbstractContainerScreen} container interaction.
 */
@Mixin(AbstractContainerScreen.class)
public interface AccessorAbstractContainerScreen {

    /**
     * Mixin invoker for {@code AbstractContainerScreen#slotClicked}.
     *
     * @param slot the container slot clicked
     * @param slotId the index of the slot
     * @param mouseButton the mouse button index
     * @param containerInput the container input type
     */
    @Invoker("slotClicked")
    void invokeSlotClicked(Slot slot, int slotId, int mouseButton, ContainerInput containerInput);
}
