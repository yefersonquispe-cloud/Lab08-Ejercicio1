# Lab08 - Ejercicio 1

Aplicación Android en Kotlin, Jetpack Compose, Room y ViewModel. Interfaz oscura inspirada en Google Tasks.

## Funciones

1. **Editar:** toca una tarea para cambiar su nombre y descripción. Los cambios se guardan en Room.
2. **Eliminar:** abre una tarea, pulsa «Eliminar tarea» y confirma. Las demás tareas permanecen.
3. **Filtrar:** utiliza «Todas», «Pendientes» o «Completadas».
4. **Buscar:** escribe en «Buscar tareas». Busca por nombre y descripción y respeta el filtro y la lista seleccionados.
5. **Ordenar:** pulsa ↕. Nombre: A–Z; fecha de creación: más recientes primero; estado: pendientes primero. Las tareas antiguas mantienen fecha desconocida y aparecen al final al ordenar por fecha. Las nuevas registran su creación real.
6. **Notificaciones:** toca una tarea pendiente → «Recordatorio». Configura «Fecha» y «Hora» o «En 1 minuto», luego «Guardar recordatorio». Puedes quitar el recordatorio desde ese mismo diálogo. Completar o eliminar una tarea cancela su recordatorio y su notificación.

Se conservan las funciones de crear tareas, marcar completadas, destacar tareas y organizar listas. No se agregaron prioridades ni tareas recurrentes.

## Recordatorios y permisos

En Android 13 o superior se solicita `POST_NOTIFICATIONS` al guardar el primer recordatorio. Si se deniega, el diálogo permite abrir los ajustes. El permiso y el canal «Recordatorios de tareas» deben estar habilitados.

Los recordatorios usan WorkManager 2.11.2 y continúan programados después de cerrar la aplicación o reiniciar el dispositivo. Son avisos diferibles: Android puede retrasarlos por ahorro de batería; no requieren acceso a alarmas exactas. Forzar la detención de la aplicación impide ejecutar trabajo hasta volver a abrirla. El trabajador consulta Room antes de avisar, evita repetir recordatorios enviados y omite tareas completadas, eliminadas o reprogramadas.

Documentación: [WorkManager](https://developer.android.com/jetpack/androidx/releases/work), [trabajo programado](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [permiso de notificaciones](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Abrir y ejecutar

Abre la carpeta del proyecto en Android Studio y sincroniza Gradle. Usa el JDK integrado y Android SDK 37. Requiere Android 8.1 (API 27) o superior. Se mantiene Room 2.7.2 y la configuración existente de KSP.

```sh
./gradlew clean
./gradlew assembleDebug
```

Room incluye migraciones 1 → 2 → 3 → 4 → 5 y esquemas versionados. No se borra la base de datos para actualizarla.

## Pruebas

Las pruebas instrumentadas están en `app/src/androidTest/java/com/example/lab08/TaskFeaturesTest.kt`. Verifican persistencia, edición, eliminación individual, filtros, búsqueda combinada con filtros, los tres órdenes, migración desde la base original, configuración/cancelación de recordatorios y recepción de una notificación real mediante WorkManager. Cada prueba elimina únicamente las tareas que creó.

Para ejecutarlas en un emulador ya iniciado sin borrar datos al reinstalar:

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.lab08.TaskFeaturesTest com.example.lab08.test/androidx.test.runner.AndroidJUnitRunner
```

### Verificación final

El 14 de octubre de 2026 se ejecutaron `clean`, `assembleDebug`, `assembleDebugAndroidTest` y `lintDebug` con resultado correcto. Lint terminó sin errores (las advertencias incluyen versiones existentes y recursos de la plantilla). Las 12 pruebas instrumentadas finalizaron con `OK (12 tests)` en 137,024 segundos en el emulador Pixel 10 Pro XL API 37.2. La prueba de notificaciones recibió un aviso real mediante WorkManager y verificó su cancelación al completar la tarea. La migración conservó la tarea existente de la base original.
