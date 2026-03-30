package com.example.musicframe.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.domain.model.MusicFrameUiState
import com.example.musicframe.domain.model.toDisplayName
import com.example.musicframe.image.FrameMode
import com.example.musicframe.image.MAX_TEXT_SCALE
import com.example.musicframe.image.MIN_TEXT_SCALE

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun frameControls(
    state: MusicFrameUiState,
    frameColorHex: String,
    headphoneColorHex: String,
    onFrameColorHexChange: (String) -> Unit,
    onApplyFrameHex: () -> Unit,
    onHeadphoneColorHexChange: (String) -> Unit,
    onApplyHeadphoneHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        frameModeSection(state = state, onAction = onAction)
        frameColorSection(
            state = state,
            frameColorHex = frameColorHex,
            onFrameColorHexChange = onFrameColorHexChange,
            onApplyFrameHex = onApplyFrameHex,
            onAction = onAction
        )
        headphoneSection(
            showHeadphoneInfo = state.showHeadphoneInfo,
            headphoneColorHex = headphoneColorHex,
            userHeadphoneTextColor = state.userHeadphoneTextColor,
            onHeadphoneColorHexChange = onHeadphoneColorHexChange,
            onApplyHeadphoneHex = onApplyHeadphoneHex,
            onToggleHeadphone = { onAction(FrameControlAction.ToggleHeadphone(it)) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun frameModeSection(
    state: MusicFrameUiState,
    onAction: (FrameControlAction) -> Unit
) {
    Text("相框模式", style = MaterialTheme.typography.titleMedium)
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
}

@Composable
private fun frameColorSection(
    state: MusicFrameUiState,
    frameColorHex: String,
    onFrameColorHexChange: (String) -> Unit,
    onApplyFrameHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Text("相框颜色", style = MaterialTheme.typography.titleMedium)
    
    // 浅色/深色模式切换
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onAction(FrameControlAction.SetLightFrame(false)) },
            enabled = !state.useLightFrame
        ) {
            Text("深色模式")
        }
        OutlinedButton(
            onClick = { onAction(FrameControlAction.SetLightFrame(true)) },
            enabled = state.useLightFrame
        ) {
            Text("浅色模式")
        }
    }
    
    // 自定义颜色
    Text("自定义徕卡颜色（可选）")
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = frameColorHex,
            onValueChange = onFrameColorHexChange,
            label = { Text("HEX") },
            placeholder = { Text("例如：FF5733") }
        )
        Button(
            onClick = onApplyFrameHex,
            enabled = frameColorHex.isNotBlank() && frameColorHex != state.customFrameColorHex
        ) {
            Text("应用")
        }
        OutlinedButton(
            onClick = { onAction(FrameControlAction.SetCustomFrameColor("")) }
        ) {
            Text("清除")
        }
    }
}

@Composable
private fun headphoneSection(
    showHeadphoneInfo: Boolean,
    headphoneColorHex: String,
    userHeadphoneTextColor: Int?,
    onHeadphoneColorHexChange: (String) -> Unit,
    onApplyHeadphoneHex: () -> Unit,
    onToggleHeadphone: (Boolean) -> Unit
) {
    Text("耳机信息", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("显示耳机信息")
        Switch(
            checked = showHeadphoneInfo,
            onCheckedChange = onToggleHeadphone
        )
    }
    
    if (showHeadphoneInfo) {
        Text("耳机文字颜色（默认自动反色）")
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = headphoneColorHex,
                onValueChange = onHeadphoneColorHexChange,
                label = { Text("HEX") },
                placeholder = { Text("例如：FFFFFF") }
            )
            Button(onClick = onApplyHeadphoneHex) {
                Text("应用")
            }
        }
    }
}
