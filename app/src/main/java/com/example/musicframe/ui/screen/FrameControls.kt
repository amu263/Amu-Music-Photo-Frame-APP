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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.domain.model.MusicFrameUiState
import com.example.musicframe.domain.model.toDisplayName
import com.example.musicframe.image.FrameMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun frameControls(
    state: MusicFrameUiState,
    frameColorHex: String,
    tempCustomColorHex: String,
    headphoneColorHex: String,
    onFrameColorHexChange: (String) -> Unit,
    onApplyFrameHex: () -> Unit,
    onClearFrameHex: () -> Unit,
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
            tempCustomColorHex = tempCustomColorHex,
            onFrameColorHexChange = onFrameColorHexChange,
            onApplyFrameHex = onApplyFrameHex,
            onClearFrameHex = onClearFrameHex,
            onAction = onAction
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
    tempCustomColorHex: String,
    onFrameColorHexChange: (String) -> Unit,
    onApplyFrameHex: () -> Unit,
    onClearFrameHex: () -> Unit,
    onAction: (FrameControlAction) -> Unit
) {
    Text("相框颜色", style = MaterialTheme.typography.titleMedium)

    // 原色/深色/浅色模式切换
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                onAction(
                    FrameControlAction.SetFrameColorMode(com.example.musicframe.domain.model.FrameColorMode.ORIGINAL)
                )
            },
            enabled = state.frameColorMode != com.example.musicframe.domain.model.FrameColorMode.ORIGINAL
        ) {
            Text("原色")
        }
        OutlinedButton(
            onClick = { onAction(
                FrameControlAction.SetFrameColorMode(com.example.musicframe.domain.model.FrameColorMode.DARK)
            ) },
            enabled = state.frameColorMode != com.example.musicframe.domain.model.FrameColorMode.DARK
        ) {
            Text("深色")
        }
        OutlinedButton(
            onClick = { onAction(
                FrameControlAction.SetFrameColorMode(com.example.musicframe.domain.model.FrameColorMode.LIGHT)
            ) },
            enabled = state.frameColorMode != com.example.musicframe.domain.model.FrameColorMode.LIGHT
        ) {
            Text("浅色")
        }
    }

    // 深色背景模式开关
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("深色背景模式")
        Switch(
            checked = state.useDarkBackground,
            onCheckedChange = { onAction(FrameControlAction.SetDarkBackground(it)) }
        )
    }

    // 自定义颜色
    Text("自定义徕卡颜色（可选）")
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = tempCustomColorHex,
            onValueChange = onFrameColorHexChange,
            label = { Text("HEX") },
            placeholder = { Text("例如：FF5733") }
        )
        Button(
            onClick = onApplyFrameHex,
            enabled = tempCustomColorHex.isNotBlank() && tempCustomColorHex != state.customFrameColorHex
        ) {
            Text("应用")
        }
        OutlinedButton(onClick = onClearFrameHex) {
            Text("清除")
        }
    }
}
