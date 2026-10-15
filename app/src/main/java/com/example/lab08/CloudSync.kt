package com.example.lab08

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class TaskApplication : Application() {
    override fun onCreate() { super.onCreate(); CloudSync.get(this) }
}

data class CloudSession(val uid: String? = null, val email: String? = null,
    val message: String = "Sincronizar con Firebase", val configured: Boolean = false)

@OptIn(FlowPreview::class)
class CloudSync private constructor(private val context: Context) {
    private val db = TaskDatabase.getInstance(context)
    private val dao = db.cloudDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val preferences = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val firebase = FirebaseApp.initializeApp(context)
    private val auth = firebase?.let { FirebaseAuth.getInstance(it) }
    private val firestore = firebase?.let { FirebaseFirestore.getInstance(it) }
    private var listener: ListenerRegistration? = null
    private val _session = MutableStateFlow(CloudSession(configured = firebase != null))
    val session: StateFlow<CloudSession> = _session.asStateFlow()
    val visibleTasks = combine(db.taskDao().observeTasks(), dao.observeStates(), session) { tasks, states, account ->
        val uid = account.uid ?: preferences.getString("last_uid", "").orEmpty()
        val owners = states.associate { it.localId to it.ownerUid }
        tasks.filter { owners[it.id].isNullOrEmpty() || owners[it.id] == uid }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        auth?.addAuthStateListener { changed ->
            scope.launch { bind(changed.currentUser?.uid, changed.currentUser?.email) }
        }
        scope.launch {
            combine(dao.observeStates(), dao.observeDeletions()) { states, deleted -> states to deleted }
                .debounce(400).collect {
                    val uid = auth?.currentUser?.uid ?: return@collect
                    if (it.first.any { row -> row.ownerUid.isEmpty() || row.ownerUid == uid && row.dirty } ||
                        it.second.any { row -> row.ownerUid == uid }) {
                        enqueueRetry(context)
                        syncNow()
                    }
                }
        }
    }

    private suspend fun bind(uid: String?, email: String?) = lock.withLock {
        if (auth?.currentUser?.uid != uid) return@withLock
        listener?.remove(); listener = null
        if (uid == null) {
            _session.value = CloudSession(configured = firebase != null)
            return@withLock
        }
        preferences.edit().putString("last_uid", uid).apply()
        dao.claimLocalTasks(uid)
        _session.value = CloudSession(uid, email, "Conectando con Firebase…", true)
        listener = firestore!!.collection("users").document(uid).collection("tasks")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    _session.update { if (it.uid == uid) it.copy(message = "No se pudo conectar. Reintenta sincronizar.") else it }
                } else if (snapshot != null) scope.launch {
                    lock.withLock {
                        if (auth?.currentUser?.uid != uid) return@withLock
                        snapshot.documents.filter { !it.metadata.hasPendingWrites() }.forEach { doc ->
                            doc.data?.let { applyRemote(uid, doc.id, it) }
                        }
                    }
                    if (!snapshot.metadata.isFromCache) syncNow()
                }
            }
        enqueueRetry(context)
    }

    suspend fun signInGoogle(token: String) {
        val service = auth ?: error("Falta configurar Firebase en esta aplicación")
        service.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
    }
    suspend fun signInEmail(email: String, password: String, create: Boolean) {
        val service = auth ?: error("Falta configurar Firebase en esta aplicación")
        if (create) service.createUserWithEmailAndPassword(email.trim(), password).await()
        else service.signInWithEmailAndPassword(email.trim(), password).await()
    }
    fun signOut() { auth?.signOut() }
    fun requestSync() { enqueueRetry(context); scope.launch { syncNow() } }

    suspend fun syncNow(): Boolean = lock.withLock {
        val uid = auth?.currentUser?.uid ?: return@withLock true
        try {
            dao.claimLocalTasks(uid)
            val collection = firestore!!.collection("users").document(uid).collection("tasks")
            for (state in dao.pending(uid)) {
                if (auth?.currentUser?.uid != uid) return@withLock true
                val task = db.taskDao().getTask(state.localId) ?: continue
                val payload = CloudTaskCodec.encode(task, state.changedAt)
                val remote = firestore.runTransaction { transaction ->
                    val ref = collection.document(state.remoteId)
                    val existing = transaction.get(ref)
                    if ((existing.getLong("changedAt") ?: -1) > state.changedAt) existing.data
                    else { transaction.set(ref, payload); null }
                }.await()
                dao.markClean(state.localId, state.revision, uid)
                if (remote != null) applyRemote(uid, state.remoteId, remote)
            }
            for (deletion in dao.deletions(uid)) {
                firestore.runTransaction { transaction ->
                    val ref = collection.document(deletion.remoteId)
                    val previous = transaction.get(ref)
                    if ((previous.getLong("changedAt") ?: -1) <= deletion.changedAt)
                        transaction.set(ref, mapOf("deleted" to true, "changedAt" to deletion.changedAt, "schemaVersion" to 1))
                }.await()
                dao.acknowledgeDeletion(deletion.remoteId, deletion.changedAt)
            }
            _session.update { if (it.uid == uid) it.copy(message = "Sincronizado con Firebase") else it }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _session.update { if (it.uid == uid) it.copy(message = "Cambios guardados aquí. Pendientes de sincronizar.") else it }
            false
        }
    }

    private suspend fun applyRemote(uid: String, remoteId: String, data: Map<String, Any?>) {
        if (dao.hasDeletion(remoteId, uid) != 0) return
        val state = dao.find(remoteId, uid)
        val changed = (data["changedAt"] as? Number)?.toLong() ?: return
        if (state?.dirty == true || state != null && state.changedAt >= changed) return
        db.withTransaction {
            dao.importing(true)
            try {
                if (data["deleted"] == true) {
                    state?.let { db.taskDao().getTask(it.localId)?.let { task ->
                        db.taskDao().deleteTask(task)
                        TaskReminderScheduler(context).cancel(task.id)
                        TaskRecurrenceScheduler(context).cancel(task.id)
                    }; dao.removeState(it.localId) }
                } else {
                    val incoming = CloudTaskCodec.decode(data, state?.localId ?: 0) ?: return@withTransaction
                    db.taskDao().insertList(TaskList(incoming.listName))
                    val id = if (state == null) db.taskDao().insertTask(incoming).toInt()
                        else { db.taskDao().updateTask(incoming); incoming.id }
                    dao.put(TaskCloudState(id, remoteId, uid, state?.revision ?: 1, changed, false))
                    TaskReminderScheduler(context).sync(incoming.copy(id = id), replace = false)
                    TaskRecurrenceScheduler(context).sync(incoming.copy(id = id), ExistingWorkPolicy.KEEP)
                }
            } finally { dao.importing(false) }
        }
    }

    companion object {
        @Volatile private var instance: CloudSync? = null
        fun get(context: Context): CloudSync = instance ?: synchronized(this) {
            instance ?: CloudSync(context.applicationContext).also { instance = it }
        }
        fun enqueueRetry(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("firebase-task-sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}

class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (CloudSync.get(applicationContext).syncNow()) Result.success() else Result.retry()
}
