package com.example.lab08

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "task_cloud_state", indices = [Index(value = ["remoteId"], unique = true)])
data class TaskCloudState(
    @PrimaryKey val localId: Int,
    val remoteId: String,
    @ColumnInfo(defaultValue = "''") val ownerUid: String = "",
    @ColumnInfo(defaultValue = "1") val revision: Long = 1,
    @ColumnInfo(defaultValue = "0") val changedAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

@Entity(tableName = "cloud_deletions")
data class CloudDeletion(@PrimaryKey val remoteId: String, val ownerUid: String, val changedAt: Long)

@Entity(tableName = "cloud_control")
data class CloudControl(@PrimaryKey val id: Int = 1, val importing: Boolean = false)

data class PendingCloudTask(@Embedded val task: Task,
    @Embedded(prefix = "cloud_") val state: TaskCloudState)

@Dao
interface CloudDao {
    @Query("SELECT * FROM task_cloud_state")
    fun observeStates(): Flow<List<TaskCloudState>>
    @Query("SELECT * FROM cloud_deletions")
    fun observeDeletions(): Flow<List<CloudDeletion>>
    @Query("SELECT * FROM task_cloud_state WHERE ownerUid = :uid AND dirty = 1")
    suspend fun pending(uid: String): List<TaskCloudState>
    @Query("SELECT * FROM cloud_deletions WHERE ownerUid = :uid")
    suspend fun deletions(uid: String): List<CloudDeletion>
    @Query("SELECT * FROM task_cloud_state WHERE remoteId = :remoteId AND ownerUid = :uid LIMIT 1")
    suspend fun find(remoteId: String, uid: String): TaskCloudState?
    @Query("UPDATE task_cloud_state SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimLocalTasks(uid: String)
    @Query("UPDATE task_cloud_state SET dirty = 0 WHERE localId = :id AND revision = :revision AND ownerUid = :uid")
    suspend fun markClean(id: Int, revision: Long, uid: String)
    @Query("DELETE FROM cloud_deletions WHERE remoteId = :remoteId AND changedAt = :changedAt")
    suspend fun acknowledgeDeletion(remoteId: String, changedAt: Long)
    @Query("UPDATE cloud_control SET importing = :value WHERE id = 1")
    suspend fun importing(value: Boolean)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: TaskCloudState)
    @Query("DELETE FROM task_cloud_state WHERE localId = :id")
    suspend fun removeState(id: Int)
    @Query("SELECT COUNT(*) FROM cloud_deletions WHERE remoteId = :remoteId AND ownerUid = :uid")
    suspend fun hasDeletion(remoteId: String, uid: String): Int
    @Query("SELECT localId FROM task_cloud_state WHERE ownerUid != '' AND ownerUid != :uid")
    fun hiddenTaskIds(uid: String): Flow<List<Int>>
}
