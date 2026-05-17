package com.example.musicframe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicframe.image.AspectRatio
import com.example.musicframe.image.CanvasConfig
import com.example.musicframe.image.CropAlignment

@Composable
fun canvasControlsPanel(
    config: CanvasConfig,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAspectRatioSelect: (AspectRatio) -> Unit,
    onPaddingChange: (Float) -> Unit,
    onCropAlignmentSelect: (CropAlignment) -> Unit,
    onCustomRatioW: (Int) -> Unit,
    onCustomRatioH: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📐 画幅与构图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (isExpanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                val ratios = AspectRatio.entries
                val ratioRows = ratios.chunked(5)
                ratioRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { ratio ->
                            val sel = config.aspectRatio == ratio
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable { onAspectRatioSelect(ratio) }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(ratio.label.take(5), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (config.aspectRatio == AspectRatio.CUSTOM) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        var w by remember { mutableStateOf(config.customRatioW.toString()) }
                        var h by remember { mutableStateOf(config.customRatioH.toString()) }
                        Text("宽", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(value = w, onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) { w = it; it.toIntOrNull()?.let(onCustomRatioW) } }, modifier = Modifier.weight(1f).height(40.dp), singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        Text("高", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(value = h, onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) { h = it; it.toIntOrNull()?.let(onCustomRatioH) } }, modifier = Modifier.weight(1f).height(40.dp), singleLine = true)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text("内边距 ${(config.paddingPercent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = config.paddingPercent, onValueChange = onPaddingChange, valueRange = 0f..0.15f, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))

                Text("裁剪对齐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val alignments = CropAlignment.entries
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    alignments.take(5).forEach { align ->
                        val sel = config.cropAlignment == align
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent).border(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).clickable { onCropAlignmentSelect(align) }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(align.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
