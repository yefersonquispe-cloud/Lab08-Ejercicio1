package com.example.lab08

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, TaskList::class, TaskCloudState::class, CloudDeletion::class, CloudControl::class], version = 9)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun cloudDao(): CloudDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: TaskDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN is_starred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN list_name TEXT NOT NULL DEFAULT 'Mis tareas'")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_lists (name TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT OR IGNORE INTO task_lists (name) VALUES ('Mis tareas')")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN due_at INTEGER")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminder_at INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminded_at INTEGER")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'Media'")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeat TEXT NOT NULL DEFAULT 'Ninguna'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN next_repeat_at INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_zone TEXT NOT NULL DEFAULT 'UTC'")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS task_cloud_state (localId INTEGER NOT NULL PRIMARY KEY, remoteId TEXT NOT NULL, ownerUid TEXT NOT NULL DEFAULT '', revision INTEGER NOT NULL DEFAULT 1, changedAt INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_cloud_state_remoteId ON task_cloud_state(remoteId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cloud_deletions (remoteId TEXT NOT NULL PRIMARY KEY, ownerUid TEXT NOT NULL, changedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cloud_control (id INTEGER NOT NULL PRIMARY KEY, importing INTEGER NOT NULL)")
                installCloudTracking(db)
            }
        }
        val SYNC_CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) { installCloudTracking(db) }
        }
        private fun installCloudTracking(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO cloud_control VALUES (1, 0)")
            val clock = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
            db.execSQL("INSERT OR IGNORE INTO task_cloud_state(localId,remoteId,ownerUid,revision,changedAt,dirty) SELECT id,lower(hex(randomblob(16))),'',1,$clock,1 FROM tasks")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS cloud_task_insert AFTER INSERT ON tasks WHEN (SELECT importing FROM cloud_control WHERE id=1)=0 BEGIN INSERT INTO task_cloud_state(localId,remoteId,ownerUid,revision,changedAt,dirty) VALUES(NEW.id,lower(hex(randomblob(16))),'',1,$clock,1); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS cloud_task_update AFTER UPDATE ON tasks WHEN (SELECT importing FROM cloud_control WHERE id=1)=0 BEGIN UPDATE task_cloud_state SET revision=revision+1, changedAt=MAX(changedAt+1,$clock), dirty=1 WHERE localId=NEW.id; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS cloud_task_delete BEFORE DELETE ON tasks WHEN (SELECT importing FROM cloud_control WHERE id=1)=0 BEGIN INSERT OR REPLACE INTO cloud_deletions SELECT remoteId,ownerUid,MAX(changedAt+1,$clock) FROM task_cloud_state WHERE localId=OLD.id AND ownerUid!=''; DELETE FROM task_cloud_state WHERE localId=OLD.id; END")
        }
        fun getInstance(context: Context): TaskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, "task_db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).addCallback(SYNC_CALLBACK).build().also { instance = it }
        }
    }
}
