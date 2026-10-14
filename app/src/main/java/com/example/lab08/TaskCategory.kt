package com.example.lab08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

val defaultCategories = listOf("Personal", "Estudios", "Trabajo", "Salud")

@Composable
fun TaskCategoryField(value: String, categories: List<String>, tag: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(value = value, onValueChange = onChange, label = { Text("Categoría") },
            placeholder = { Text("Sin categoría") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            trailingIcon = { TextButton(onClick = { expanded = true }, modifier = Modifier.semantics {
                contentDescription = "Elegir categoría"
            }) { Text("▾") } })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Sin categoría") }, onClick = { onChange(""); expanded = false })
            categories.forEach { name ->
                DropdownMenuItem(modifier = Modifier.semantics { contentDescription = "Asignar categoría $name" },
                    text = { Text(name) }, onClick = { onChange(name); expanded = false })
            }
        }
    }
}
