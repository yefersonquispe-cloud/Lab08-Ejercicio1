package com.example.lab08

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskReminderDialog(task: Task, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    val context = LocalContext.current
    var date by remember(task.id) { mutableStateOf(task.reminderAt?.takeIf { it > System.currentTimeMillis() }
        ?: task.dueAt?.takeIf { it > System.currentTimeMillis() } ?: (System.currentTimeMillis() + 3_600_000)) }
    var choosingDate by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val initial = remember(task.id) { Calendar.getInstance().apply { timeInMillis = date } }
    val utcDay = remember(task.id) { Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis }
    val day = rememberDatePickerState(initialSelectedDateMillis = utcDay)
    val time = rememberTimePickerState(initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE), is24Hour = true)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted && date > System.currentTimeMillis()) onSave(date)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Recordatorio de tarea") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.description)
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date))
            Row {
                TextButton(onClick = {
                    val local = Calendar.getInstance().apply { timeInMillis = date }
                    day.selectedDateMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
                    }.timeInMillis
                    choosingDate = true
                }) { Text("Fecha") }
                TextButton(onClick = {
                    val local = Calendar.getInstance().apply { timeInMillis = date }
                    time.hour = local.get(Calendar.HOUR_OF_DAY); time.minute = local.get(Calendar.MINUTE)
                    choosingTime = true
                }) { Text("Hora") }
                TextButton(onClick = { date = System.currentTimeMillis() + 60_000 }) { Text("En 1 minuto") }
            }
            if (date <= System.currentTimeMillis()) Text("Selecciona una fecha y hora futuras.", color = MaterialTheme.colorScheme.error)
            Text("Android puede retrasar el aviso para ahorrar batería.", style = MaterialTheme.typography.bodySmall)
            if (permissionDenied) {
                Text("Permiso denegado. Activa las notificaciones para recibir el recordatorio.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Abrir ajustes") }
            }
            if (task.reminderAt != null) TextButton(onClick = { onSave(null) }) { Text("Quitar recordatorio") }
        } },
        confirmButton = { TextButton(enabled = date > System.currentTimeMillis(), onClick = {
            if (date > System.currentTimeMillis()) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) {
                    permissionDenied = true
                } else onSave(date)
            }
        }) { Text("Guardar recordatorio") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
    if (choosingDate) DatePickerDialog(onDismissRequest = { choosingDate = false },
        confirmButton = { TextButton(enabled = day.selectedDateMillis != null, onClick = {
            val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day.selectedDateMillis!! }
            date = Calendar.getInstance().apply {
                timeInMillis = date
                set(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
            choosingDate = false
        }) { Text("Aceptar fecha") } },
        dismissButton = { TextButton(onClick = { choosingDate = false }) { Text("Cancelar") } }) { DatePicker(day) }
    if (choosingTime) AlertDialog(onDismissRequest = { choosingTime = false }, title = { Text("Hora del recordatorio") },
        text = { TimePicker(time) }, confirmButton = { TextButton(onClick = {
            date = Calendar.getInstance().apply {
                timeInMillis = date; set(Calendar.HOUR_OF_DAY, time.hour); set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            choosingTime = false
        }) { Text("Aceptar hora") } }, dismissButton = { TextButton(onClick = { choosingTime = false }) { Text("Cancelar") } })
}
