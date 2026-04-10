package com.example.musicframe.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicframe.export.ImageExporter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun exportFormatSelector(
    selected: ImageExporter.Format,
    onSelected: (ImageExporter.Format) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("导出格式")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ImageExporter.Format.entries.forEach { format ->
                FilterChip(
                    selected = selected == format,
                    onClick = { onSelected(format) },
                    label = { Text(format.displayName) }
                )
            }
        }
    }
}
