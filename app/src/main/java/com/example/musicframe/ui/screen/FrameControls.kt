package com.example.musicframe.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.musicframe.domain.model.FrameControlAction
import com.example.musicframe.domain.model.FrameColorMode
import com.example.musicframe.domain.model.MusicFrameUiState
import com.example.musicframe.domain.model.toDisplayName
import com.example.musicframe.image.FrameMode
import com.example.musicframe.ui.theme.SaltColors

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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 相框模式选择
        frameModeSelector(
            currentMode = state.frameMode,
            onModeSelect = { onAction(FrameControlAction.SetMode(it)) }
        )
        
        // 颜色模式选择
        colorModeSelector(
            currentMode = state.frameColorMode,
            onModeSelect = { onAction(FrameControlAction.SetFrameColorMode(it)) }
        )
        
        // 深色背景开关
        darkBackgroundToggle(
            isEnabled = state.useDarkBackground,
            onToggle = { onAction(FrameControlAction.SetDarkBackground(it)) }
        )
        
        // 自定义颜色输入
        customColorInput(
            value = tempCustomColorHex,
            onValueChange = onFrameColorHexChange,
            onApply = onApplyFrameHex,
            onClear = onClearFrameHex,
            isModified = tempCustomColorHex.isNotBlank() && tempCustomColorHex != state.customFrameColorHex
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun frameModeSelector(
    currentMode: FrameMode,
    onModeSelect: (FrameMode) -> Unit
) {
    val modes = remember { FrameMode.entries }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { mode ->
            ModeChip(
                label = when (mode) {
                    FrameMode.PREMIUM_LEICA -> "莱卡"
                    FrameMode.CUSTOM_LEICA -> "自定义"
                    FrameMode.MUSIC_FLOW -> "流光"
                    FrameMode.MUSIC_SOLID -> "纯色"
                },
                isSelected = currentMode == mode,
                onClick = { onModeSelect(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) SaltColors.Primary else Color.Transparent,
        label = "bg"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (isSelected) SaltColors.Primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun colorModeSelector(
    currentMode: FrameColorMode,
    onModeSelect: (FrameColorMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FrameColorMode.entries.forEach { mode ->
            val isSelected = currentMode == mode
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.95f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "scale"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) SaltColors.Primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) SaltColors.Primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onModeSelect(mode) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 颜色指示点
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when (mode) {
                                    FrameColorMode.ORIGINAL -> SaltColors.Primary
                                    FrameColorMode.DARK -> Color.Black
                                    FrameColorMode.LIGHT -> Color.White
                                },
                                CircleShape
                            )
                            .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (mode) {
                            FrameColorMode.ORIGINAL -> "原"
                            FrameColorMode.DARK -> "深"
                            FrameColorMode.LIGHT -> "浅"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) SaltColors.Primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // 深色背景开关
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (currentMode == FrameColorMode.DARK) SaltColors.Primary.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = currentMode == FrameColorMode.DARK,
                onCheckedChange = { onModeSelect(if (it) FrameColorMode.DARK else FrameColorMode.ORIGINAL) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SaltColors.Primary,
                    checkedTrackColor = SaltColors.Primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.scale(0.7f)
            )
        }
    }
}

@Composable
private fun darkBackgroundToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    // 已整合到 colorModeSelector 中
}

@Composable
private fun customColorInput(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    isModified: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 颜色预览
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    try {
                        if (value.isNotBlank()) Color(android.graphics.Color.parseColor("#$value"))
                        else Color.Transparent
                    } catch (e: Exception) {
                        Color.Transparent
                    }
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // 只允许有效的 HEX 字符
                if (newValue.isEmpty() || newValue.matches(Regex("^[0-9A-Fa-f]*$"))) {
                    onValueChange(newValue)
                }
            },
            placeholder = { Text("HEX", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        
        // 应用按钮
        if (isModified) {
            IconButton(
                onClick = onApply,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SaltColors.Primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "应用",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // 清除按钮
        if (value.isNotBlank()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
