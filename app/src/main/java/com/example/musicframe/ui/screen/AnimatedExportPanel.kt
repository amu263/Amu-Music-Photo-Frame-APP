package com.example.musicframe.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.musicframe.MusicFrameViewModel
import com.example.musicframe.export.ExportManager
import java.io.File

/**
 * 动态图片导出面板
 * 提供格式选择、质量设置、导出进度显示和预览功能
 */
@Composable
fun animatedExportPanel(
    exportFormat: ExportManager.OutputFormat,
    qualityLevel: ExportManager.QualityLevel,
    exportState: MusicFrameViewModel.ExportState,
    previewUri: Uri?,
    onFormatChanged: (ExportManager.OutputFormat) -> Unit,
    onQualityChanged: (ExportManager.QualityLevel) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "动态图片导出",
            style = MaterialTheme.typography.titleMedium
        )

        // 格式选择
        formatSelector(
            selectedFormat = exportFormat,
            onFormatChanged = onFormatChanged,
            enabled = exportState !is MusicFrameViewModel.ExportState.Progress
        )

        // 质量选择
        qualitySelector(
            selectedQuality = qualityLevel,
            onQualityChanged = onQualityChanged,
            enabled = exportState !is MusicFrameViewModel.ExportState.Progress
        )

        // 导出按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                enabled = exportState !is MusicFrameViewModel.ExportState.Progress
            ) {
                Text(
                    when (exportState) {
                        is MusicFrameViewModel.ExportState.Progress -> "导出中..."
                        is MusicFrameViewModel.ExportState.Success -> "重新导出"
                        else -> "导出动态图片"
                    }
                )
            }

            if (exportState is MusicFrameViewModel.ExportState.Success) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("分享")
                }
            }
        }

        // 进度显示
        when (exportState) {
            is MusicFrameViewModel.ExportState.Progress -> {
                progressSection(
                    current = exportState.current,
                    total = exportState.total,
                    phase = exportState.phase
                )
            }
            is MusicFrameViewModel.ExportState.Error -> {
                errorSection(message = exportState.message)
            }
            is MusicFrameViewModel.ExportState.Success -> {
                successSection(
                    outputFile = exportState.outputFile,
                    previewUri = previewUri
                )
            }
            else -> {}
        }

        // 重置按钮
        if (exportState is MusicFrameViewModel.ExportState.Success ||
            exportState is MusicFrameViewModel.ExportState.Error) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除")
            }
        }
    }
}

/**
 * 格式选择器
 */
@Composable
private fun formatSelector(
    selectedFormat: ExportManager.OutputFormat,
    onFormatChanged: (ExportManager.OutputFormat) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "导出格式",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExportManager.OutputFormat.entries.forEach { format ->
                FormatChip(
                    text = format.displayName,
                    selected = selectedFormat == format,
                    onClick = { onFormatChanged(format) },
                    enabled = enabled
                )
            }
        }
    }
}

/**
 * 格式选择芯片
 */
@Composable
private fun FormatChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
        )
    }
}

/**
 * 质量选择器
 */
@Composable
private fun qualitySelector(
    selectedQuality: ExportManager.QualityLevel,
    onQualityChanged: (ExportManager.QualityLevel) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "导出质量: ${selectedQuality.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExportManager.QualityLevel.entries.forEach { quality ->
                QualityButton(
                    text = quality.displayName,
                    selected = selectedQuality == quality,
                    onClick = { onQualityChanged(quality) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 质量按钮
 */
@Composable
private fun QualityButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = if (selected) {
            androidx.compose.material3.ButtonDefaults.buttonColors()
        } else {
            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Text(text)
    }
}

/**
 * 进度显示区
 */
@Composable
private fun progressSection(
    current: Int,
    total: Int,
    phase: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = phase,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$current / $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { if (total > 0) current.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 错误显示区
 */
@Composable
private fun errorSection(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * 成功显示区
 */
@Composable
private fun successSection(
    outputFile: File,
    previewUri: Uri?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "导出成功",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "文件: ${outputFile.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "大小: ${formatFileSize(outputFile.length())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 预览区域
        previewSection(previewUri = previewUri)
    }
}

/**
 * 预览区域
 */
@Composable
private fun previewSection(
    previewUri: Uri?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (previewUri != null) {
            // 显示文件路径信息（实际预览需要加载图片）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "预览已生成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "预览区域",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
