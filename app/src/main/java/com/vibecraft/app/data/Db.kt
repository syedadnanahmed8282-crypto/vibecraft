package com.vibecraft.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

@Entity(tableName = "messages", indices = [Index("projectId")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

@Entity(tableName = "files", indices = [Index("projectId")])
data class FileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val path: String,
    val content: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

/** changes is a JSON object {path: content}; a "__full" key means "replace the whole project with these files". */
@Entity(tableName = "checkpoints", indices = [Index("projectId")])
data class CheckpointEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val version: Int,
    val title: String,
    val model: String?,
    val changes: String,
    val createdAt: Long,
    val dirty: Boolean = true,
)

@Dao
interface VcDao {
    @Query("SELECT * FROM files WHERE projectId = :id AND deletedAt IS NULL ORDER BY path")
    fun files(id: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE projectId = :id")
    suspend fun filesOnce(id: String): List<FileEntity>

    @Query("SELECT * FROM checkpoints WHERE projectId = :id ORDER BY version DESC")
    fun checkpoints(id: String): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints WHERE projectId = :id ORDER BY version")
    suspend fun checkpointsOnce(id: String): List<CheckpointEntity>

    @Query("SELECT MAX(version) FROM checkpoints WHERE projectId = :id")
    suspend fun maxVersion(id: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFile(f: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCheckpoint(c: CheckpointEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMissingFiles(list: List<FileEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMissingCheckpoints(list: List<CheckpointEntity>)

    @Query("SELECT * FROM files WHERE dirty = 1")
    suspend fun dirtyFiles(): List<FileEntity>

    @Query("SELECT * FROM checkpoints WHERE dirty = 1")
    suspend fun dirtyCheckpoints(): List<CheckpointEntity>

    @Query("UPDATE files SET dirty = 0 WHERE id IN (:ids)")
    suspend fun cleanFiles(ids: List<String>)

    @Query("UPDATE checkpoints SET dirty = 0 WHERE id IN (:ids)")
    suspend fun cleanCheckpoints(ids: List<String>)

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun projects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM messages WHERE projectId = :id AND deletedAt IS NULL ORDER BY createdAt")
    fun messages(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE projectId = :id AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun messagesOnce(id: String): List<MessageEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProject(p: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMessage(m: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMissingProjects(list: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMissingMessages(list: List<MessageEntity>)

    @Query("SELECT * FROM projects WHERE dirty = 1")
    suspend fun dirtyProjects(): List<ProjectEntity>

    @Query("SELECT * FROM messages WHERE dirty = 1")
    suspend fun dirtyMessages(): List<MessageEntity>

    @Query("UPDATE projects SET dirty = 0 WHERE id IN (:ids)")
    suspend fun cleanProjects(ids: List<String>)

    @Query("UPDATE messages SET dirty = 0 WHERE id IN (:ids)")
    suspend fun cleanMessages(ids: List<String>)
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS files (id TEXT NOT NULL, projectId TEXT NOT NULL, path TEXT NOT NULL, content TEXT NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, dirty INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_files_projectId ON files (projectId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS checkpoints (id TEXT NOT NULL, projectId TEXT NOT NULL, version INTEGER NOT NULL, title TEXT NOT NULL, model TEXT, changes TEXT NOT NULL, createdAt INTEGER NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_checkpoints_projectId ON checkpoints (projectId)")
    }
}

@Database(entities = [ProjectEntity::class, MessageEntity::class, FileEntity::class, CheckpointEntity::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): VcDao
}
