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

    private var activeModeIndex = 0
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
    private val modalW = 380
    private val modalH = 265

    override fun init() {
        modalX = (width - modalW) / 2
        modalY = (height - modalH) / 2

        // 1. Centered Mode Switcher Pill ( [ W/S ] | Vertical )
        modePill = PillToggleWidget(
            modalX + (modalW - 160) / 2, modalY + 26, 160, 18,
            listOf("W/S", "Vertical"), activeModeIndex
        ) { idx ->
            saveFieldValues()
            activeModeIndex = idx
            loadFieldValues()
        }
        addRenderableWidget(modePill)

        // 2. Top-level Crop Selector Dropdown Button
        cropSelectBtn = Button.builder(Component.literal("${cropList[selectedCropIndex].displayName} ▼")) {
            isCropDropdownOpen = !isCropDropdownOpen
        }.bounds(modalX + 115, modalY + 48, 115, 18).build()
        addRenderableWidget(cropSelectBtn)

        // 3. Global Yaw & Pitch Fields
        globalYawField = EditBox(font, modalX + 95, modalY + 88, 50, 16, Component.literal("Global Yaw"))
        globalPitchField = EditBox(font, modalX + 205, modalY + 88, 50, 16, Component.literal("Global Pitch"))
        addRenderableWidget(globalYawField)
        addRenderableWidget(globalPitchField)

        // 4. Crop Angle Pill ( [ Global ] | [ Custom ] )
        anglePill = PillToggleWidget(
            modalX + 115, modalY + 110, 130, 18,
            listOf("Global", "Custom"), 1
        ) { /* Direct state update */ }
        addRenderableWidget(anglePill)

        // 5. Custom Yaw & Pitch Fields
        customYawField = EditBox(font, modalX + 95, modalY + 134, 50, 16, Component.literal("Custom Yaw"))
        customPitchField = EditBox(font, modalX + 205, modalY + 134, 50, 16, Component.literal("Custom Pitch"))
        addRenderableWidget(customYawField)
        addRenderableWidget(customPitchField)

        // 6. Global Speed Field
        globalSpeedField = EditBox(font, modalX + 115, modalY + 174, 55, 16, Component.literal("Global Speed"))
        addRenderableWidget(globalSpeedField)

        // 7. Crop Speed Pill
        speedPill = PillToggleWidget(
            modalX + 115, modalY + 196, 130, 18,
            listOf("Global", "Custom"), 0
        ) { /* Direct state update */ }
        addRenderableWidget(speedPill)

        // 8. Custom Speed Field
        customSpeedField = EditBox(font, modalX + 115, modalY + 220, 55, 16, Component.literal("Custom Speed"))
        addRenderableWidget(customSpeedField)

        // 9. Centered Apply & Cancel Buttons
        val btnW = 65
        val btnH = 18
        val btnY = modalY + modalH - 24

        addRenderableWidget(Button.builder(Component.literal("Apply")) {
            saveFieldValues()
            ConfigManager.save()
            minecraft.setScreen(parent)
        }.bounds(modalX + modalW / 2 - btnW - 6, btnY, btnW, btnH).build())

        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            minecraft.setScreen(parent)
        }.bounds(modalX + modalW / 2 + 6, btnY, btnW, btnH).build())

        // Top right close [X]
        addRenderableWidget(Button.builder(Component.literal("X")) {
            minecraft.setScreen(parent)
        }.bounds(modalX + modalW - 24, modalY + 6, 18, 16).build())

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
        // Main modal container
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF00F172A.toInt())

        // Header title
        graphics.text(font, "Settings: Crop Farming", modalX + 12, modalY + 10, 0xFFE2E8F0.toInt())

        // Selected Crop Selector Header
        graphics.text(font, "Selected Crop:", modalX + 24, modalY + 53, 0xFF94A3B8.toInt())

        // Pitch & Yaw Section Header
        graphics.text(font, "§b[ Pitch & Yaw ]", modalX + 16, modalY + 74, 0xFF38BDF8.toInt())
        graphics.text(font, "Global Yaw:", modalX + 24, modalY + 92, 0xFF94A3B8.toInt())
        graphics.text(font, "Pitch:", modalX + 160, modalY + 92, 0xFF94A3B8.toInt())
        graphics.text(font, "Angle Mode:", modalX + 24, modalY + 115, 0xFF94A3B8.toInt())
        graphics.text(font, "Custom Yaw:", modalX + 24, modalY + 138, 0xFF94A3B8.toInt())
        graphics.text(font, "Pitch:", modalX + 160, modalY + 138, 0xFF94A3B8.toInt())

        // Speed Section Header
        graphics.text(font, "§b[ Speed ]", modalX + 16, modalY + 158, 0xFF38BDF8.toInt())
        graphics.text(font, "Global Speed:", modalX + 24, modalY + 178, 0xFF94A3B8.toInt())
        graphics.text(font, "Speed Mode:", modalX + 24, modalY + 201, 0xFF94A3B8.toInt())
        graphics.text(font, "Custom Speed:", modalX + 24, modalY + 224, 0xFF94A3B8.toInt())

        super.extractRenderState(graphics, mouseX, mouseY, delta)

        // Render Crop Dropdown Menu overlay on top if open
        if (isCropDropdownOpen) {
            val dropX = modalX + 115
            val dropY = modalY + 68
            val dropW = 115
            val itemH = 16
            val dropH = cropList.size * itemH

            graphics.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF334155.toInt())
            graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1E293B.toInt())

            for ((i, crop) in cropList.withIndex()) {
                val iy = dropY + (i * itemH)
                val isHovered = mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)
                if (isHovered || i == selectedCropIndex) {
                    graphics.fill(dropX, iy, dropX + dropW, iy + itemH, if (i == selectedCropIndex) 0xFF38BDF8.toInt() else 0xFF475569.toInt())
                }
                val textColor = if (i == selectedCropIndex) 0xFF0F172A.toInt() else 0xFFF1F5F9.toInt()
                graphics.text(font, crop.displayName, dropX + 6, iy + 4, textColor)
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (isCropDropdownOpen && event.button() == 0) {
            val dropX = modalX + 115
            val dropY = modalY + 68
            val dropW = 115
            val itemH = 16
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
