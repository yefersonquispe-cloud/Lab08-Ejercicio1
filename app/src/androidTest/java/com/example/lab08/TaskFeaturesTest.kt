package com.example.lab08

import android.database.sqlite.SQLiteDatabase
import java.util.Calendar
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFeaturesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val created = mutableMapOf<Int, String>()
    @After fun cleanupOwnTasks() = runBlocking {
        val db = TaskDatabase.getInstance(context)
        for (task in db.taskDao().getAllTasks()) {
            if (created[task.id] == task.description) {
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(task.id))
            }
        }
    }
    private fun recordTask(text: String) = runBlocking {
        val task = TaskDatabase.getInstance(context).taskDao().getAllTasks()
            .filter { it.description == text }.maxBy { it.id }
        created[task.id] = task.description
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun add(text: String) {
        rule.onNodeWithContentDescription("Agregar tarea").performClick()
        rule.onNodeWithTag("newTaskTitle").performTextReplacement(text)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Guardar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Guardar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun designCreateCompleteAndReopen() {
        val text = "Ejercicios"
        rule.onNodeWithContentDescription("Agregar tarea").performClick()
        rule.onNodeWithText("Guardar").assertIsNotEnabled()
        rule.onNodeWithTag("newTaskTitle").performTextReplacement("   ")
        rule.onNodeWithText("Guardar").assertIsNotEnabled()
        rule.onNodeWithTag("newTaskTitle").performTextReplacement(text)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Guardar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Guardar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        recordTask(text)
        rule.onNodeWithContentDescription("Completar $text").performClick()
        rule.waitForIdle()
        recordTask(text)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Completar $text").assertIsOn()
        runBlocking {
            val saved = TaskDatabase.getInstance(context).taskDao().getAllTasks().single { it.description == text }
            assertTrue(saved.isCompleted)
        }
    }
    @Test fun listsStarsAndDetailsPersist() {
        val text = "Estudiar Kotlin"
        rule.onNodeWithContentDescription("Crear nueva lista").performClick()
        rule.onNodeWithText("Nombre de la lista").performTextReplacement("Estudios")
        rule.onNodeWithText("Crear").performClick()
        rule.onNodeWithContentDescription("Agregar tarea").performClick()
        rule.onNodeWithTag("newTaskTitle").performTextReplacement(text)
        rule.onNodeWithContentDescription("Agregar detalles").performClick()
        rule.onNodeWithTag("newTaskNotes").performTextReplacement("Repasar Compose")
        rule.onNodeWithContentDescription("Destacar nueva tarea").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Guardar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Guardar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        recordTask(text)
        rule.onNodeWithContentDescription("Ver tareas destacadas").performClick()
        rule.onNodeWithText(text).assertIsDisplayed()
        recordTask(text)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Repasar Compose").onLast().assertIsDisplayed()
        runBlocking {
            val dao = TaskDatabase.getInstance(context).taskDao()
            val saved = dao.getAllTasks().single { it.description == text }
            assertTrue(saved.isStarred)
            assertEquals("Estudios", saved.listName)
            assertEquals("Repasar Compose", saved.notes)
            assertTrue(dao.getLists().any { it.name == "Estudios" })
        }
    }
    @Test fun dateAndTimePersist() {
        val text = "Entrega de laboratorio"
        rule.onNodeWithContentDescription("Agregar tarea").performClick()
        rule.onNodeWithTag("newTaskTitle").performTextReplacement(text)
        rule.onNodeWithContentDescription("Asignar fecha y hora").performClick()
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
        rule.onNodeWithText("Elegir hora").assertIsEnabled()
        rule.onNodeWithText("Elegir hora").performClick()
        rule.onNodeWithText("Aceptar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Guardar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Guardar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        recordTask(text)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val saved = TaskDatabase.getInstance(context).taskDao().getAllTasks().single { it.description == text }
            assertNotNull(saved.dueAt)
            val date = Calendar.getInstance().apply { timeInMillis = saved.dueAt!! }
            assertEquals(day.toInt(), date.get(Calendar.DAY_OF_MONTH))
        }
    }
    @Test fun editTaskPersists() {
        add("Desayunar")
        recordTask("Desayunar")
        rule.onNodeWithText("Desayunar").performClick()
        rule.onNodeWithTag("editTitle").performTextReplacement("Desayunar temprano")
        rule.onNodeWithTag("editNotes").performTextReplacement("Antes de estudiar")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Desayunar temprano").fetchSemanticsNodes().isNotEmpty() }
        recordTask("Desayunar temprano")
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Desayunar temprano").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Antes de estudiar").assertIsDisplayed()
        runBlocking {
            val saved = TaskDatabase.getInstance(context).taskDao().getAllTasks().single { it.description == "Desayunar temprano" }
            assertEquals("Antes de estudiar", saved.notes)
        }
    }
    @Test fun deleteOneTaskKeepsOthers() {
        add("Eliminar esta tarea"); recordTask("Eliminar esta tarea")
        add("Conservar esta tarea"); recordTask("Conservar esta tarea")
        rule.onNodeWithText("Eliminar esta tarea").performClick()
        rule.onNodeWithText("Eliminar tarea").performClick()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithText("Eliminar esta tarea").assertExists()
        rule.onNodeWithText("Eliminar esta tarea").performClick()
        rule.onNodeWithText("Eliminar tarea").performClick()
        rule.onNodeWithText("Eliminar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Eliminar esta tarea").fetchSemanticsNodes().isEmpty() }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Conservar esta tarea").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Eliminar esta tarea").assertDoesNotExist()
        runBlocking {
            val tasks = TaskDatabase.getInstance(context).taskDao().getAllTasks()
            assertTrue(tasks.any { it.description == "Conservar esta tarea" })
            assertFalse(tasks.any { it.description == "Eliminar esta tarea" })
        }
    }
    @Test fun filtersShowCorrectStates() {
        add("Estudiar pendiente"); recordTask("Estudiar pendiente")
        add("Desayuno completado"); recordTask("Desayuno completado")
        rule.onNodeWithContentDescription("Completar Desayuno completado").performClick()
        rule.onNodeWithText("Pendientes").performClick()
        rule.onNodeWithText("Estudiar pendiente").assertIsDisplayed()
        rule.onNodeWithText("Desayuno completado").assertDoesNotExist()
        rule.onNodeWithText("Completadas").performClick()
        rule.onNodeWithText("Desayuno completado").assertIsDisplayed()
        rule.onNodeWithText("Estudiar pendiente").assertDoesNotExist()
        rule.onNodeWithText("Todas").performClick()
        rule.onNodeWithText("Estudiar pendiente").assertIsDisplayed()
        rule.onNodeWithText("Desayuno completado").assertIsDisplayed()
    }
    @Test fun searchMatchesNameAndDescription() {
        add("Preparar examen"); recordTask("Preparar examen")
        add("Comprar pan"); recordTask("Comprar pan")
        rule.onNodeWithText("Comprar pan").performClick()
        rule.onNodeWithTag("editNotes").performTextReplacement("Examen de cocina")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.onNodeWithTag("search").performTextReplacement("EXAMEN")
        rule.onNodeWithText("Preparar examen").assertIsDisplayed()
        rule.onNodeWithText("Comprar pan").assertIsDisplayed()
        rule.onNodeWithTag("search").performTextReplacement("pan")
        rule.onNodeWithText("Preparar examen").assertDoesNotExist()
        rule.onNodeWithText("Comprar pan").assertIsDisplayed()
        rule.onNodeWithTag("search").performTextReplacement("no coincide")
        rule.onNodeWithText("Sin resultados").assertExists()
        rule.onNodeWithText("Limpiar").performClick()
        rule.onNodeWithText("Preparar examen").assertIsDisplayed()
    }
    @Test fun sortingOrdersRowsAndPreservesCreationDate() {
        val before = System.currentTimeMillis()
        add("Zeta tarea"); recordTask("Zeta tarea")
        add("Alfa tarea"); recordTask("Alfa tarea")
        rule.onNodeWithContentDescription("Completar Alfa tarea").performClick()
        fun choose(label: String) {
            rule.onNodeWithContentDescription("Ordenar tareas").performClick()
            rule.onNodeWithText(label).performClick()
        }
        fun assertAbove(first: String, second: String) {
            assertTrue(rule.onNodeWithText(first).fetchSemanticsNode().boundsInRoot.top <
                rule.onNodeWithText(second).fetchSemanticsNode().boundsInRoot.top)
        }
        choose("Nombre"); assertAbove("Alfa tarea", "Zeta tarea")
        choose("Fecha de creación"); assertAbove("Alfa tarea", "Zeta tarea")
        choose("Estado"); assertAbove("Zeta tarea", "Alfa tarea")
        val dates = runBlocking { TaskDatabase.getInstance(context).taskDao().getAllTasks()
            .filter { it.id in created }.associate { it.id to it.createdAt } }
        assertTrue(dates.values.all { it != null && it >= before && it <= System.currentTimeMillis() })
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Alfa tarea").fetchSemanticsNodes().isNotEmpty() }
        assertAbove("Zeta tarea", "Alfa tarea")
        runBlocking { assertEquals(dates, TaskDatabase.getInstance(context).taskDao().getAllTasks()
            .filter { it.id in created }.associate { it.id to it.createdAt }) }
    }
    @Test fun migrationPreservesExistingTasks() {
        val name = "migration-design-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, description TEXT NOT NULL, is_completed INTEGER NOT NULL)")
            db.execSQL("INSERT INTO tasks VALUES (7, 'Tarea anterior', 1)")
            db.version = 1
        }
        val migrated = Room.databaseBuilder(context, TaskDatabase::class.java, name)
            .addMigrations(TaskDatabase.MIGRATION_1_2, TaskDatabase.MIGRATION_2_3, TaskDatabase.MIGRATION_3_4).build()
        runBlocking {
            val task = migrated.taskDao().getAllTasks().single()
            assertEquals(7, task.id); assertEquals("Tarea anterior", task.description)
            assertTrue(task.isCompleted); assertFalse(task.isStarred)
            assertEquals("Mis tareas", task.listName); assertEquals("", task.notes); assertNull(task.dueAt); assertNull(task.createdAt)
            assertTrue(migrated.taskDao().getLists().any { it.name == "Mis tareas" })
        }
        migrated.close()
        context.deleteDatabase(name)
    }
}
