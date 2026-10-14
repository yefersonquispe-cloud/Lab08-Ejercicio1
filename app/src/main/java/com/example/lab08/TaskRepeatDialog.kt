package com.example.lab08

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.text.DateFormat

@Composable
fun TaskRepeatDialog(task: Task, onDismiss: () -> Unit, onSave: (TaskRepeat) -> Unit) {
    var selected by remember(task.id) { mutableStateOf(TaskRepeat.from(task.repeat)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Repetir tarea") }, text = {
        Column {
            Text("La tarea volverá a estar pendiente en cada intervalo, aunque ya la hayas completado.")
            TaskRepeat.entries.forEach { option ->
                FilterChip(selected = selected == option, onClick = { selected = option }, label = { Text(option.label) })
            }
            task.nextRepeatAt?.let { Text("Próxima repetición: " + DateFormat.getDateTimeInstance().format(it)) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Guardar repetición") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
