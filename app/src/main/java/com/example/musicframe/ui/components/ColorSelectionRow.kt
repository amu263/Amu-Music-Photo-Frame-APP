package com.example.musicframe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun colorSelectionRow(
    colors: List<Int?>,
    selectedColor: Int?,
    autoLabel: String,
    onSelected: (Int?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(colors) { color ->
            val selected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (color == null) {
                    Text(autoLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
