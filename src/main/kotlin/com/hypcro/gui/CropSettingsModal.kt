package com.hypcro.gui

import com.hypcro.config.ConfigManager
import com.hypcro.config.CropSetting
import com.hypcro.config.CropType
import com.hypcro.gui.widgets.PillToggleWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class CropSettingsModal(private val parent: Screen) : Screen(Component.literal("Settings: Crop Farming")) {

    private var activeModeIndex = if (ConfigManager.config.activeMethod.equals("VERTICAL", ignoreCase = true)) 1 else 0
    private var selectedCropIndex = 0
    private val cropList = CropType.values().toList()
    private var isCropDropdownOpen = false

    private lateinit var modePill: PillToggleWidget
    private lateinit var cropSelectBtn: Button
    private lateinit var anglePill: PillToggleWidget
    private lateinit var speedPill: PillToggleWidget

    private lateinit var globalYawField: EditBox
    private lateinit var globalPitchField: EditBox
    private lateinit var customYawField: EditBox
    private lateinit var customPitchField: EditBox
    private lateinit var globalSpeedField: EditBox
    private lateinit var customSpeedField: EditBox

    private var modalX = 0
    private var modalY = 0
    private val modalW = 390
    private val modalH = 275

    override fun init() {
        modalX = (width - modalW) / 2
        modalY = (height - modalH) / 2

        // Top Mode Switcher Pill ( [ W/S ] | Vertical )
        modePill = PillToggleWidget(
            modalX + (modalW - 160) / 2, modalY + 24, 160, 18,
            listOf("W/S", "Vertical"), activeModeIndex
        ) { idx ->
            saveFieldValues()
            activeModeIndex = idx
            loadFieldValues()
        }
        addRenderableWidget(modePill)

        // --- SECTION 1: GLOBAL SETTINGS ---
        // Global Yaw & Pitch Fields (Row Y = modalY + 68)
        globalYawField = EditBox(font, modalX + 130, modalY + 66, 52, 16, Component.literal("Global Yaw"))
        globalPitchField = EditBox(font, modalX + 235, modalY + 66, 52, 16, Component.literal("Global Pitch"))
        addRenderableWidget(globalYawField)
        addRenderableWidget(globalPitchField)

        // Global Speed Field (Row Y = modalY + 88)
        globalSpeedField = EditBox(font, modalX + 130, modalY + 88, 52, 16, Component.literal("Global Speed"))
        addRenderableWidget(globalSpeedField)

        // --- SECTION 2: CROP-SPECIFIC CUSTOM SETTINGS ---
        // Crop Selector Dropdown Button (Row Y = modalY + 130)
        cropSelectBtn = Button.builder(Component.literal("${cropList[selectedCropIndex].displayName} ▼")) {
            isCropDropdownOpen = !isCropDropdownOpen
        }.bounds(modalX + 130, modalY + 130, 120, 18).build()
        addRenderableWidget(cropSelectBtn)

        // Crop Angle Mode Pill (Row Y = modalY + 152)
        anglePill = PillToggleWidget(
            modalX + 130, modalY + 152, 130, 18,
            listOf("Global", "Custom"), 1
        ) { /* State updated on save */ }
        addRenderableWidget(anglePill)

        // Custom Yaw & Pitch Fields (Row Y = modalY + 174)
        customYawField = EditBox(font, modalX + 130, modalY + 174, 52, 16, Component.literal("Custom Yaw"))
        customPitchField = EditBox(font, modalX + 235, modalY + 174, 52, 16, Component.literal("Custom Pitch"))
        addRenderableWidget(customYawField)
        addRenderableWidget(customPitchField)

        // Crop Speed Mode Pill (Row Y = modalY + 196)
        speedPill = PillToggleWidget(
            modalX + 130, modalY + 196, 130, 18,
            listOf("Global", "Custom"), 0
        ) { /* State updated on save */ }
        addRenderableWidget(speedPill)

        // Custom Speed Field (Row Y = modalY + 218)
        customSpeedField = EditBox(font, modalX + 130, modalY + 218, 52, 16, Component.literal("Custom Speed"))
        addRenderableWidget(customSpeedField)

        // Bottom Actions: Apply & Cancel
        val btnW = 68
        val btnH = 18
        val btnY = modalY + modalH - 24

        addRenderableWidget(Button.builder(Component.literal("Apply")) {
            saveFieldValues()
            ConfigManager.config.activeMethod = if (activeModeIndex == 1) "VERTICAL" else "WS"
            ConfigManager.save()
            minecraft.setScreen(parent)
        }.bounds(modalX + modalW / 2 - btnW - 6, btnY, btnW, btnH).build())

        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            minecraft.setScreen(parent)
        }.bounds(modalX + modalW / 2 + 6, btnY, btnW, btnH).build())

        // Top right close [X]
        addRenderableWidget(Button.builder(Component.literal("X")) {
            minecraft.setScreen(parent)
        }.bounds(modalX + modalW - 22, modalY + 6, 16, 16).build())

        loadFieldValues()
    }

    private fun loadCropSettings() {
        val modeConfig = if (activeModeIndex == 0) ConfigManager.config.wsConfig else ConfigManager.config.verticalConfig
        val curCrop = cropList[selectedCropIndex]
        val cropSetting = modeConfig.crops.getOrPut(curCrop.name) { CropSetting() }

        cropSelectBtn.message = Component.literal("${curCrop.displayName} ▼")
        anglePill.selectedIndex = if (cropSetting.useCustomAngles) 1 else 0
        customYawField.value = cropSetting.yaw.toString()
        customPitchField.value = cropSetting.pitch.toString()

        speedPill.selectedIndex = if (cropSetting.useCustomSpeed) 1 else 0
        customSpeedField.value = cropSetting.speed.toString()
    }

    private fun saveCropSettings() {
        val modeConfig = if (activeModeIndex == 0) ConfigManager.config.wsConfig else ConfigManager.config.verticalConfig
        val curCrop = cropList[selectedCropIndex]
        val cropSetting = modeConfig.crops.getOrPut(curCrop.name) { CropSetting() }

        cropSetting.useCustomAngles = (anglePill.selectedIndex == 1)
        cropSetting.yaw = customYawField.value.toFloatOrNull() ?: 0.0f
        cropSetting.pitch = customPitchField.value.toFloatOrNull() ?: 0.0f

        cropSetting.useCustomSpeed = (speedPill.selectedIndex == 1)
        cropSetting.speed = customSpeedField.value.toIntOrNull() ?: 100
    }

    private fun loadFieldValues() {
        val modeConfig = if (activeModeIndex == 0) ConfigManager.config.wsConfig else ConfigManager.config.verticalConfig
        globalYawField.value = modeConfig.globalAngles.yaw.toString()
        globalPitchField.value = modeConfig.globalAngles.pitch.toString()
        globalSpeedField.value = modeConfig.globalSpeed.toString()

        loadCropSettings()
    }

    private fun saveFieldValues() {
        val modeConfig = if (activeModeIndex == 0) ConfigManager.config.wsConfig else ConfigManager.config.verticalConfig
        modeConfig.globalAngles.yaw = globalYawField.value.toFloatOrNull() ?: 0.0f
        modeConfig.globalAngles.pitch = globalPitchField.value.toFloatOrNull() ?: 0.0f
        modeConfig.globalSpeed = globalSpeedField.value.toIntOrNull() ?: 100

        saveCropSettings()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Main modal dark background
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF20F172A.toInt())

        // Header title
        graphics.text(font, "Settings: Crop Farming", modalX + 12, modalY + 9, 0xFFE2E8F0.toInt())

        // --- SECTION 1: GLOBAL SETTINGS ---
        graphics.text(font, "§b[ Global Settings ]", modalX + 16, modalY + 50, 0xFF38BDF8.toInt())
        graphics.text(font, "Global Angle: Yaw", modalX + 26, modalY + 70, 0xFF94A3B8.toInt())
        graphics.text(font, "Pitch", modalX + 195, modalY + 70, 0xFF94A3B8.toInt())
        graphics.text(font, "Global Speed:", modalX + 26, modalY + 92, 0xFF94A3B8.toInt())

        // --- SECTION 2: CROP-SPECIFIC CUSTOM SETTINGS ---
        val cropName = cropList[selectedCropIndex].displayName
        graphics.text(font, "§b[ Crop-Specific Custom Settings ]", modalX + 16, modalY + 114, 0xFF38BDF8.toInt())
        graphics.text(font, "Target Crop:", modalX + 26, modalY + 135, 0xFF94A3B8.toInt())
        graphics.text(font, "Angle Mode:", modalX + 26, modalY + 157, 0xFF94A3B8.toInt())
        graphics.text(font, "$cropName Angle: Yaw", modalX + 26, modalY + 178, 0xFF94A3B8.toInt())
        graphics.text(font, "Pitch", modalX + 195, modalY + 178, 0xFF94A3B8.toInt())
        graphics.text(font, "Speed Mode:", modalX + 26, modalY + 201, 0xFF94A3B8.toInt())
        graphics.text(font, "$cropName Speed:", modalX + 26, modalY + 222, 0xFF94A3B8.toInt())

        super.extractRenderState(graphics, mouseX, mouseY, delta)

        // Render Crop Dropdown Menu overlay on top if open
        if (isCropDropdownOpen) {
            val dropX = modalX + 130
            val dropY = modalY + 150
            val dropW = 120
            val itemH = 18
            val dropH = cropList.size * itemH

            graphics.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF334155.toInt())
            graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF0F172A.toInt())

            for ((i, crop) in cropList.withIndex()) {
                val iy = dropY + (i * itemH)
                val isHovered = mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)
                val isSelected = i == selectedCropIndex

                if (isSelected) {
                    graphics.fill(dropX, iy, dropX + dropW, iy + itemH, 0xFF0284C7.toInt())
                } else if (isHovered) {
                    graphics.fill(dropX, iy, dropX + dropW, iy + itemH, 0xFF1E293B.toInt())
                }

                val textColor = if (isSelected) 0xFFFFFFFF.toInt() else if (isHovered) 0xFF38BDF8.toInt() else 0xFFCBD5E1.toInt()
                graphics.text(font, crop.displayName, dropX + 8, iy + 5, textColor)
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (isCropDropdownOpen && event.button() == 0) {
            val dropX = modalX + 130
            val dropY = modalY + 150
            val dropW = 120
            val itemH = 18
            val mouseX = event.x().toInt()
            val mouseY = event.y().toInt()

            for ((i, _) in cropList.withIndex()) {
                val iy = dropY + (i * itemH)
                if (mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)) {
                    saveCropSettings()
                    selectedCropIndex = i
                    loadCropSettings()
                    isCropDropdownOpen = false
                    return true
                }
            }

            // Clicked outside dropdown
            isCropDropdownOpen = false
            return true
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (isCropDropdownOpen) {
                isCropDropdownOpen = false
                return true
            }
            minecraft.setScreen(parent)
            return true
        }
        return super.keyPressed(event)
    }
}
