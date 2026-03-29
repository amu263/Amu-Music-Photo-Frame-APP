package com.example.musicframe.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.domain.model.MusicFrameUiState
import com.example.musicframe.domain.model.toDisplayName
import com.example.musicframe.image.FrameMode
import com.example.musicframe.image.MAX_TEXT_SCALE
import com.example.musicframe.image.MIN_TEXT_SCALE
import com.example.musicframe.ui.components.colorSelectionRow

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList")
@Composable
fun frameControls(
    state: MusicFrameUiState,
    frameColorHex: String,
    textColorHex: String,
    headphoneColorHex: String,
    bottomText: String,
    onFrameColorHexChange: (String) -> Unit,
    onTextColorHexChange: (String) -> Unit,
    onHeadphoneColorHexChange: (String) -> Unit,
    onBottomTextChange: (String) -> Unit,
    onApplyFrameHex: () -> Unit,
    onApplyTextHex: () -> Unit,
    onApplyHeadphoneHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        frameModeSection(state = state, onAction = onAction)
        metadataTogglesSection(state = state, onAction = onAction)
        textScaleSlidersSection(state = state, onAction = onAction)
        borderRatioSection(state = state, onAction = onAction)
        frameColorSection(
            frameColorHex = frameColorHex,
            userFrameColor = state.userFrameColor,
            onFrameColorHexChange = onFrameColorHexChange,
            onApplyFrameHex = onApplyFrameHex,
            onAction = onAction
        )
        textColorSection(
            textColorHex = textColorHex,
            userTextColor = state.userTextColor,
            onTextColorHexChange = onTextColorHexChange,
            onApplyTextHex = onApplyTextHex,
            onAction = onAction
        )
        headphoneColorSection(
            headphoneColorHex = headphoneColorHex,
            userHeadphoneTextColor = state.userHeadphoneTextColor,
            showHeadphoneInfo = state.showHeadphoneInfo,
            onHeadphoneColorHexChange = onHeadphoneColorHexChange,
            onApplyHeadphoneHex = onApplyHeadphoneHex,
            onAction = onAction
        )
        customTextSection(bottomText = bottomText, onBottomTextChange = onBottomTextChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun frameModeSection(
    state: MusicFrameUiState,
    onAction: (FrameControlAction) -> Unit
) {
    Text("相框模式")
    val modes = remember { FrameMode.entries }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        modes.forEach { mode ->
            FilterChip(
                selected = state.frameMode == mode,
                onClick = { onAction(FrameControlAction.SetMode(mode)) },
                label = { Text(mode.toDisplayName()) }
            )
        }
    }
    OutlinedButton(onClick = {
        val next = modes[(modes.indexOf(state.frameMode) + 1) % modes.size]
        onAction(FrameControlAction.SetMode(next))
    }) {
        Text("添加模式切换")
    }
}

@Composable
private fun metadataTogglesSection(
    state: MusicFrameUiState,
    onAction: (FrameControlAction) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("仅文字模式")
        Switch(checked = state.overlayOnly, onCheckedChange = { onAction(FrameControlAction.ToggleOverlay(it)) })
    }
    metadataToggleRow(
        label = "显示照片信息",
        checked = state.showPhotoMetadata,
        onCheckedChange = { onAction(FrameControlAction.TogglePhoto(it)) }
    )
    metadataToggleRow(
        label = "显示歌曲信息",
        checked = state.showMusicMetadata,
        onCheckedChange = { onAction(FrameControlAction.ToggleMusic(it)) }
    )
    metadataToggleRow(
        label = "显示耳机信息",
        checked = state.showHeadphoneInfo,
        onCheckedChange = { onAction(FrameControlAction.ToggleHeadphone(it)) }
    )
    metadataToggleRow(
        label = "显示自定义文字",
        checked = state.showCustomText,
        onCheckedChange = { onAction(FrameControlAction.ToggleCustomText(it)) }
    )
}

@Composable
private fun metadataToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun textScaleSlidersSection(
    state: MusicFrameUiState,
    onAction: (FrameControlAction) -> Unit
) {
    Text("信息字体大小（默认最小）")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        textScaleSlider(
            label = "照片信息字号",
            value = state.photoTextScale,
            onValueChange = { onAction(FrameControlAction.UpdatePhotoTextScale(it)) }
        )
        textScaleSlider(
            label = "歌曲信息字号",
            value = state.musicTextScale,
            onValueChange = { onAction(FrameControlAction.UpdateMusicTextScale(it)) }
        )
        textScaleSlider(
            label = "耳机信息字号",
            value = state.headphoneTextScale,
            enabled = state.showHeadphoneInfo,
            onValueChange = { onAction(FrameControlAction.UpdateHeadphoneTextScale(it)) }
        )
        textScaleSlider(
            label = "自定义文字字号",
            value = state.customTextScale,
            enabled = state.showCustomText,
            onValueChange = { onAction(FrameControlAction.UpdateCustomTextScale(it)) }
        )
    }
}

@Composable
private fun textScaleSlider(
    label: String,
    value: Float,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Text("$label ${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = MIN_TEXT_SCALE..MAX_TEXT_SCALE,
        enabled = enabled
    )
}

@Composable
private fun borderRatioSection(
    state: MusicFrameUiState,
    onAction: (FrameControlAction) -> Unit
) {
    Text("相框粗细")
    Slider(
        value = state.frameRatio,
        onValueChange = { onAction(FrameControlAction.FrameRatio(it)) },
        valueRange = 0.02f..0.2f
    )
    val bottomLabel = if (state.frameMode == FrameMode.CUSTOM_CARD) "自定义模式文字区高度" else "底部留白"
    Text(bottomLabel)
    Slider(
        value = state.bottomExtraRatio,
        onValueChange = { onAction(FrameControlAction.BottomExtraRatio(it)) },
        valueRange = if (state.frameMode == FrameMode.CUSTOM_CARD) 0.05f..0.25f else 0f..0.15f
    )
    if (state.frameMode == FrameMode.CUSTOM_CARD) {
        Text(
            text = "图片将缩小置于上方，底部区域用于展示自定义文字与信息，避免遮挡主体。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("静态流光相框")
        Switch(
            checked = state.useStaticFlowFrame,
            onCheckedChange = { onAction(FrameControlAction.ToggleStaticFlow(it)) }
        )
    }
}

@Composable
private fun frameColorSection(
    frameColorHex: String,
    userFrameColor: Int?,
    onFrameColorHexChange: (String) -> Unit,
    onApplyFrameHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Text("相框颜色（默认音乐取色）")
    val frameColors = remember {
        listOf(
            null,
            0xFFFFFFFF.toInt(),
            0xFFFFF0D0.toInt(),
            0xFFE4F1FF.toInt(),
            0xFFD0F5E0.toInt(),
            0xFFF8D7DA.toInt(),
            0xFF333333.toInt()
        )
    }
    colorSelectionRow(
        colors = frameColors,
        selectedColor = userFrameColor,
        autoLabel = "自动",
        onSelected = { onAction(FrameControlAction.FrameColorSelected(it)) }
    )
    OutlinedTextField(
        value = frameColorHex,
        onValueChange = onFrameColorHexChange,
        label = { Text("自定义相框颜色(#RRGGBB或#AARRGGBB)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = onApplyFrameHex) { Text("应用相框颜色") }
}

@Composable
private fun textColorSection(
    textColorHex: String,
    userTextColor: Int?,
    onTextColorHexChange: (String) -> Unit,
    onApplyTextHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Text("文字颜色（默认相框反色）")
    val textColors = remember {
        listOf(
            null,
            0xFFFFFFFF.toInt(),
            0xFFECECEC.toInt(),
            0xFF333333.toInt(),
            0xFF111111.toInt(),
            0xFF008577.toInt(),
            0xFFAA00FF.toInt()
        )
    }
    colorSelectionRow(
        colors = textColors,
        selectedColor = userTextColor,
        autoLabel = "自动反色",
        onSelected = { onAction(FrameControlAction.TextColorSelected(it)) }
    )
    OutlinedTextField(
        value = textColorHex,
        onValueChange = onTextColorHexChange,
        label = { Text("自定义文字颜色(#RRGGBB或#AARRGGBB)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = onApplyTextHex) { Text("应用文字颜色") }
}

@Composable
private fun headphoneColorSection(
    headphoneColorHex: String,
    userHeadphoneTextColor: Int?,
    showHeadphoneInfo: Boolean,
    onHeadphoneColorHexChange: (String) -> Unit,
    onApplyHeadphoneHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Text("耳机信息颜色（默认跟随自动反色）")
    val headphoneColors = remember {
        listOf(
            null,
            0xFFFFFFFF.toInt(),
            0xFFECECEC.toInt(),
            0xFF222222.toInt(),
            0xFF008577.toInt(),
            0xFFAA00FF.toInt()
        )
    }
    colorSelectionRow(
        colors = headphoneColors,
        selectedColor = userHeadphoneTextColor,
        autoLabel = "跟随自动",
        onSelected = { onAction(FrameControlAction.HeadphoneTextColorSelected(it)) }
    )
    OutlinedTextField(
        value = headphoneColorHex,
        onValueChange = onHeadphoneColorHexChange,
        label = { Text("自定义耳机信息颜色(#RRGGBB或#AARRGGBB)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = showHeadphoneInfo
    )
    OutlinedButton(onClick = onApplyHeadphoneHex, enabled = showHeadphoneInfo) { Text("应用耳机信息颜色") }
}

@Composable
private fun customTextSection(
    bottomText: String,
    onBottomTextChange: (String) -> Unit
) {
    OutlinedTextField(
        value = bottomText,
        onValueChange = onBottomTextChange,
        label = { Text("底部自定义文字") },
        singleLine = false,
        modifier = Modifier.fillMaxWidth()
    )
}
