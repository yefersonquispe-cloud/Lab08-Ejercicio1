package com.example.lab08

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class TaskPriority(val label: String) { HIGH("Alta"), MEDIUM("Media"), LOW("Baja") }

fun priorityColor(priority: String): Color = when (priority) {
    "Alta" -> Color(0xFFFFB4AB)
    "Baja" -> Color(0xFF8AD8A0)
    else -> Color(0xFFA8BEFA)
}

@Composable
fun PrioritySelector(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskPriority.entries.forEach { option ->
            FilterChip(selected = selected == option.label, onClick = { onSelect(option.label) },
                label = { Text(option.label, color = priorityColor(option.label)) })
        }
    }
}
