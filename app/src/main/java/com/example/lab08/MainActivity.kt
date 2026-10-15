package com.example.lab08

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.lab08.ui.theme.Lab08Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val db = TaskDatabase.getInstance(applicationContext)
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskViewModel(db.taskDao(), TaskReminderScheduler(applicationContext), TaskRecurrenceScheduler(applicationContext), CloudSync.get(applicationContext)) as T
        })[TaskViewModel::class.java]
        setContent { Lab08Theme(darkTheme = true, dynamicColor = false) { TaskScreen(model, intent.getStringExtra("list_name") ?: "Mis tareas") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel, initialList: String = "Mis tareas") {
    val tasks by viewModel.tasks.collectAsState()
    val lists by viewModel.lists.collectAsState()
    var showCloud by remember { mutableStateOf(false) }
    val cloud = viewModel.cloud
    var selectedList by rememberSaveable { mutableStateOf(initialList) }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var newTaskDescription by rememberSaveable { mutableStateOf("") }
    var newTaskNotes by rememberSaveable { mutableStateOf("") }
    var newTaskCategory by rememberSaveable { mutableStateOf("") }
    var newTaskPriority by rememberSaveable { mutableStateOf("Media") }
    var newTaskStar by rememberSaveable { mutableStateOf(false) }
    var newTaskDueAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var choosingDate by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = todayForDatePicker())
    val currentTime = remember { Calendar.getInstance() }
    val timeState = rememberTimePickerState(initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE), is24Hour = true)
    var addingList by rememberSaveable { mutableStateOf(false) }
    var newListName by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var repeatTask by remember { mutableStateOf<Task?>(null) }
    var reminderTask by remember { mutableStateOf<Task?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    val categories = (defaultCategories + tasks.map { it.category }.filter { it.isNotBlank() }).distinct()
    var sort by rememberSaveable { mutableStateOf(TaskSort.CREATED) }
    var showSort by remember { mutableStateOf(false) }
    val visibleTasks = sortTasks(filterTasks(tasks, selectedList, favorites, filter, search, categoryFilter), sort)
    val listTitle = if (favorites) "Destacadas" else selectedList
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { addingTask = true }, shape = RoundedCornerShape(18.dp),
                containerColor = Color(0xFF304674), contentColor = Color(0xFFD4E2FF),
                modifier = Modifier.size(60.dp).semantics { contentDescription = "Agregar tarea" }) {
                Text("+", fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 18.dp)) {
                Text("Tareas", Modifier.align(Alignment.Center), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.size(36.dp).align(Alignment.CenterEnd).clip(CircleShape).background(Color(0xFF8D7569)),
                    contentAlignment = Alignment.Center) { Text("Y", color = Color.White, fontSize = 20.sp) }
            }
            if (cloud != null) {
                val account by cloud.session.collectAsState()
                TextButton(onClick = { showCloud = true }, modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Abrir sincronización en la nube"
                }) { Text("☁  ${account.message}", fontSize = 12.sp) }
            }
            LazyRow(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ListTab("★", favorites, "Ver tareas destacadas") { favorites = true } }
                items(lists, key = { it.name }) { list ->
                    ListTab(list.name, !favorites && selectedList == list.name, "Lista ${list.name}") {
                        selectedList = list.name; favorites = false
                    }
                }
                item { ListTab("+  Nueva lista", false, "Crear nueva lista") { addingList = true } }
            }
            HorizontalDivider(color = Color(0xFF3C4045))
            OutlinedTextField(value = search, onValueChange = { search = it },
                placeholder = { Text("Buscar tareas") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search"),
                shape = RoundedCornerShape(24.dp),
                trailingIcon = { if (search.isNotEmpty()) TextButton(onClick = { search = "" }) { Text("Limpiar") } })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskFilter.entries) { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                }
            }
            categoryFilter?.let { category ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Categoría: ${category.ifBlank { "Sin categoría" }}", Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { categoryFilter = null }) { Text("Quitar filtro") }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("taskList"),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 92.dp)) {
                item {
                    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = 0.dp, bottomEnd = 0.dp),
                        color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().height(66.dp).padding(start = 24.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(listTitle, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Box {
                                TextButton(onClick = { showCategories = true }, modifier = Modifier.semantics {
                                    contentDescription = "Filtrar por categoría"
                                }) { Text("Categorías") }
                                DropdownMenu(expanded = showCategories, onDismissRequest = { showCategories = false }) {
                                    DropdownMenuItem(text = { Text("Todas las categorías") }, onClick = { categoryFilter = null; showCategories = false })
                                    DropdownMenuItem(text = { Text("Sin categoría") }, onClick = { categoryFilter = ""; showCategories = false })
                                    categories.forEach { name ->
                                        DropdownMenuItem(modifier = Modifier.semantics { contentDescription = "Filtrar categoría $name" },
                                            text = { Text(name) }, onClick = { categoryFilter = name; showCategories = false })
                                    }
                                }
                            }
                            Box {
                                IconButton(onClick = { showSort = true }, modifier = Modifier.semantics {
                                    contentDescription = "Ordenar tareas"
                                }) { Text("↕", fontSize = 25.sp) }
                                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                    TaskSort.entries.forEach { option ->
                                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                            sort = option; showSort = false
                                        })
                                    }
                                }
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.semantics {
                                    contentDescription = "Opciones de la lista"
                                }) { Text("⋮", fontSize = 25.sp) }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Eliminar todas las tareas") },
                                        enabled = tasks.isNotEmpty(), onClick = { showMenu = false; confirmClear = true })
                                }
                            }
                        }
                    }
                }
                if (visibleTasks.isEmpty()) item {
                    Surface(shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 38.dp, bottom = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyTasksIllustration(Modifier.fillMaxWidth().height(180.dp))
                            Spacer(Modifier.height(28.dp))
                            Text(if (search.isNotBlank()) "Sin resultados"
                                else if (filter != TaskFilter.ALL || categoryFilter != null) "No hay tareas para este filtro"
                                else if (favorites) "No hay tareas destacadas" else "No hay tareas aún",
                                fontSize = 21.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text(if (favorites) "Toca la estrella de una tarea para verla aquí."
                                else "Agrega tareas pendientes y lleva un\nseguimiento de ellas en tu aplicación.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                                lineHeight = 19.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                items(visibleTasks, key = { it.id }) { task ->
                    Surface(color = MaterialTheme.colorScheme.surface,
                        shape = if (task.id == visibleTasks.last().id) RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        else RoundedCornerShape(0.dp)) {
                        Row(Modifier.fillMaxWidth().testTag("task_${task.id}").padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            TaskCompletionButton(task) { viewModel.toggleTaskCompletion(task) }
                            Column(Modifier.weight(1f).clickable { editingTask = task }.padding(horizontal = 8.dp)) {
                                Text(task.description, fontSize = 17.sp,
                                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None)
                                Text("Prioridad ${task.priority}", fontSize = 12.sp,
                                    modifier = Modifier.testTag("priority_${task.id}"), color = priorityColor(task.priority))
                                if (task.notes.isNotBlank()) Text(task.notes, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (task.category.isNotBlank()) Text("Categoría: ${task.category}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (task.repeat != "Ninguna") Text("Repetición ${task.repeat}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary)
                                if (task.reminderAt != null) Text(if (task.remindedAt == null) "Recordatorio activo" else "Recordatorio enviado",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                task.dueAt?.let { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                            }
                            IconButton(onClick = { viewModel.toggleTaskStar(task) }, modifier = Modifier.semantics {
                                contentDescription = if (task.isStarred) "Quitar destacada ${task.description}" else "Destacar ${task.description}"
                            }) { Text(if (task.isStarred) "★" else "☆", fontSize = 27.sp,
                                color = if (task.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
    if (showCloud && cloud != null) CloudAccountDialog(cloud, onDismiss = { showCloud = false })
    if (addingTask) {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { addingTask = false }, sheetState = sheetState, dragHandle = null,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp, bottom = 12.dp)) {
                BasicTextField(value = newTaskDescription, onValueChange = { newTaskDescription = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("newTaskTitle").focusRequester(focusRequester),
                    decorationBox = { field ->
                        if (newTaskDescription.isEmpty()) Text("Nueva tarea", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    })
                if (showNotes) BasicTextField(value = newTaskNotes, onValueChange = { newTaskNotes = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("newTaskNotes"),
                    decorationBox = { field ->
                        if (newTaskNotes.isEmpty()) Text("Agregar detalles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    })
                if (showNotes) TaskCategoryField(newTaskCategory, categories, "newTaskCategory") { newTaskCategory = it }
                PrioritySelector(newTaskPriority) { newTaskPriority = it }
                newTaskDueAt?.let { TextButton(onClick = { choosingDate = true }) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it))
                } }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showNotes = !showNotes }, modifier = Modifier.semantics {
                        contentDescription = "Agregar detalles"
                    }) { TaskOptionIcon("notes") }
                    IconButton(onClick = { keyboard?.hide(); choosingDate = true }, modifier = Modifier.semantics {
                        contentDescription = "Asignar fecha y hora"
                    }) { TaskOptionIcon("clock") }
                    IconButton(onClick = { newTaskStar = !newTaskStar }, modifier = Modifier.semantics {
                        contentDescription = "Destacar nueva tarea"
                    }) { Text(if (newTaskStar) "★" else "☆", fontSize = 27.sp,
                        color = if (newTaskStar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.addTask(newTaskDescription, selectedList, newTaskNotes, newTaskDueAt, newTaskStar, newTaskPriority, newTaskCategory)
                        newTaskDescription = ""; newTaskNotes = ""; newTaskDueAt = null
                        newTaskStar = false; newTaskPriority = "Media"; newTaskCategory = ""; showNotes = false; addingTask = false; favorites = false
                    }, enabled = newTaskDescription.isNotBlank()) { Text("Guardar") }
                }
            }
        }
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    if (choosingDate) DatePickerDialog(onDismissRequest = { choosingDate = false },
        confirmButton = { TextButton(enabled = dateState.selectedDateMillis != null, onClick = {
            choosingDate = false; choosingTime = true
        }) { Text("Elegir hora") } },
        dismissButton = { TextButton(onClick = { choosingDate = false }) { Text("Cancelar") } }) { DatePicker(dateState) }
    if (choosingTime) AlertDialog(onDismissRequest = { choosingTime = false }, title = { Text("Hora de la tarea") },
        text = { TimePicker(timeState) }, confirmButton = { TextButton(onClick = {
            // DatePicker usa medianoche UTC; conservamos su día en la zona horaria local.
            val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateState.selectedDateMillis!! }
            newTaskDueAt = Calendar.getInstance().apply {
                set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            choosingTime = false
        }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { choosingTime = false }) { Text("Cancelar") } })
    editingTask?.let { task ->
        var title by remember(task.id) { mutableStateOf(task.description) }
        var category by remember(task.id) { mutableStateOf(task.category) }
        var priority by remember(task.id) { mutableStateOf(task.priority) }
        var notes by remember(task.id) { mutableStateOf(task.notes) }
        AlertDialog(onDismissRequest = { editingTask = null }, title = { Text("Editar tarea") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nombre") },
                    modifier = Modifier.testTag("editTitle"))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Descripción") },
                    modifier = Modifier.testTag("editNotes"))
                TaskCategoryField(category, categories, "editCategory") { category = it }
                PrioritySelector(priority) { priority = it }
                TextButton(onClick = { repeatTask = task; editingTask = null }) { Text("Repetición") }
                TextButton(enabled = !task.isCompleted, onClick = { reminderTask = task; editingTask = null }) {
                    Text("Recordatorio")
                }
                TextButton(onClick = { taskToDelete = task; editingTask = null }) {
                    Text("Eliminar tarea", color = MaterialTheme.colorScheme.error)
                }
            } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
                viewModel.editTask(task, title, notes, priority, category); editingTask = null
            }) { Text("Guardar cambios") } },
            dismissButton = { TextButton(onClick = { editingTask = null }) { Text("Cancelar") } })
    }
    repeatTask?.let { task ->
        TaskRepeatDialog(task, onDismiss = { repeatTask = null }, onSave = { repeat ->
            viewModel.setRepeat(task, repeat); repeatTask = null
        })
    }
    reminderTask?.let { task ->
        TaskReminderDialog(task, onDismiss = { reminderTask = null }, onSave = { date ->
            viewModel.setReminder(task, date); reminderTask = null
        })
    }
    taskToDelete?.let { task ->
        AlertDialog(onDismissRequest = { taskToDelete = null }, title = { Text("¿Eliminar esta tarea?") },
            text = { Text(task.description) },
            confirmButton = { TextButton(onClick = { viewModel.deleteTask(task); taskToDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Cancelar") } })
    }
    if (addingList) AlertDialog(onDismissRequest = { addingList = false }, title = { Text("Nueva lista") },
        text = { OutlinedTextField(value = newListName, onValueChange = { newListName = it },
            label = { Text("Nombre de la lista") }, singleLine = true) },
        confirmButton = { TextButton(enabled = newListName.isNotBlank(), onClick = {
            val name = newListName.trim(); viewModel.addList(name); selectedList = name; favorites = false
            newListName = ""; addingList = false
        }) { Text("Crear") } }, dismissButton = { TextButton(onClick = { addingList = false }) { Text("Cancelar") } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("¿Eliminar todas las tareas?") }, text = { Text("Se eliminarán las tareas de todas las listas.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteAllTasks(); confirmClear = false }) { Text("Eliminar todas") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } })
}

@Composable
private fun ListTab(text: String, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(role = Role.Tab, onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, Modifier.height(53.dp).wrapContentHeight().padding(horizontal = 12.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (text == "★") 25.sp else 14.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.height(3.dp).width(if (text == "★") 28.dp else 64.dp)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
    }
}

@Composable
private fun TaskCompletionButton(task: Task, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(48.dp).testTag("complete_${task.id}").semantics {
        contentDescription = "Completar ${task.description}"
        toggleableState = if (task.isCompleted) ToggleableState.On else ToggleableState.Off
    }.clickable(role = Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        if (task.isCompleted) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp)
        else Canvas(Modifier.size(23.dp)) { drawCircle(color, style = Stroke(2.dp.toPx())) }
    }
}

// Ilustración vectorial propia dibujada por la aplicación para su estado vacío.
@Composable
private fun EmptyTasksIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = size.width.coerceAtMost(size.height * 1.45f) / 320f
        val ox = (size.width - 320f * scale) / 2f
        val oy = (size.height - 220f * scale) / 2f
        fun point(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)
        val blue = Color(0xFF79CDF8)
        drawCircle(Color(0xFFF4B4B5), 35f * scale, point(72f, 39f))
        val check = Path().apply { moveTo(point(58f,39f).x, point(58f,39f).y); lineTo(point(68f,49f).x,point(68f,49f).y); lineTo(point(89f,27f).x,point(89f,27f).y) }
        drawPath(check, Color(0xFF3D3334), style = Stroke(1.7f * scale))
        drawRoundRect(Color(0xFF69D68C), point(125f,21f), Size(151f*scale,35f*scale), androidx.compose.ui.geometry.CornerRadius(18f*scale))
        drawLine(Color(0xFF18372A), point(146f,38f), point(257f,38f), 1.3f*scale)
        val ribbon = Path().apply {
            moveTo(point(27f,182f).x,point(27f,182f).y)
            cubicTo(point(79f,200f).x,point(79f,200f).y,point(85f,87f).x,point(85f,87f).y,point(129f,87f).x,point(129f,87f).y)
            lineTo(point(235f,87f).x,point(235f,87f).y)
            cubicTo(point(191f,109f).x,point(191f,109f).y,point(193f,220f).x,point(193f,220f).y,point(147f,211f).x,point(147f,211f).y)
            cubicTo(point(121f,205f).x,point(121f,205f).y,point(135f,116f).x,point(135f,116f).y,point(112f,132f).x,point(112f,132f).y)
            cubicTo(point(83f,151f).x,point(83f,151f).y,point(92f,218f).x,point(92f,218f).y,point(67f,211f).x,point(67f,211f).y)
            lineTo(point(27f,211f).x,point(27f,211f).y); close()
        }
        drawPath(ribbon,blue)
        val curl = Path().apply {
            moveTo(point(220f,90f).x,point(220f,90f).y)
            cubicTo(point(250f,71f).x,point(250f,71f).y,point(252f,178f).x,point(252f,178f).y,point(270f,176f).x,point(270f,176f).y)
            lineTo(point(245f,176f).x,point(245f,176f).y)
            cubicTo(point(226f,175f).x,point(226f,175f).y,point(240f,107f).x,point(240f,107f).y,point(220f,90f).x,point(220f,90f).y)
        }
        drawPath(curl,blue,style=Stroke(1.4f*scale))
        drawRoundRect(Color(0xFFFFDA80), point(47f,106f),Size(44f*scale,42f*scale), androidx.compose.ui.geometry.CornerRadius(17f*scale))
        drawLine(blue,point(12f,212f),point(299f,212f),1.2f*scale)
    }
}

@Composable
private fun TaskOptionIcon(kind: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        if (kind == "clock") {
            drawCircle(color, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
            drawLine(color, Offset(size.width / 2, size.height * .22f), center, stroke)
            drawLine(color, center, Offset(size.width * .70f, size.height * .61f), stroke)
        } else {
            drawLine(color, Offset(2f, size.height * .25f), Offset(size.width - 2f, size.height * .25f), stroke)
            drawLine(color, Offset(2f, size.height * .50f), Offset(size.width - 2f, size.height * .50f), stroke)
            drawLine(color, Offset(2f, size.height * .75f), Offset(size.width * .60f, size.height * .75f), stroke)
        }
    }
}

private fun todayForDatePicker(): Long {
    val localDate = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(localDate.get(Calendar.YEAR), localDate.get(Calendar.MONTH), localDate.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
