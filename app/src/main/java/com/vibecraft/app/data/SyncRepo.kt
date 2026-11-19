package com.vibecraft.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class FileDto(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val path: String,
    val content: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class CheckpointDto(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val version: Int,
    val title: String,
    val model: String?,
    val changes: JsonObject,
    @SerialName("created_at") val createdAt: String,
)

private fun Long.iso() = Instant.fromEpochMilliseconds(this).toString()
private fun String.ms() = Instant.parse(this).toEpochMilliseconds()

/**
 * Sync: push local changes, then restore anything missing locally.
 * (Conflict resolution for edits made on two phones comes in a later stage.)
 */
class SyncRepo(private val supabase: SupabaseClient, private val dao: VcDao) {

    suspend fun run(): Result<String> = runCatching {
        if (supabase.auth.currentUserOrNull() == null) error("Sign in to sync.")
        var sent = 0

        val ps = dao.dirtyProjects()
        if (ps.isNotEmpty()) {
            supabase.from("projects").upsert(ps.map { ProjectDto(it.id, it.name, it.createdAt.iso(), it.deletedAt?.iso()) })
            dao.cleanProjects(ps.map { it.id }); sent += ps.size
        }
        val fs = dao.dirtyFiles()
        if (fs.isNotEmpty()) {
            supabase.from("project_files").upsert(fs.map { FileDto(it.id, it.projectId, it.path, it.content, it.deletedAt?.iso()) })
            dao.cleanFiles(fs.map { it.id }); sent += fs.size
        }
        val cs = dao.dirtyCheckpoints()
        if (cs.isNotEmpty()) {
            supabase.from("checkpoints").upsert(
                cs.map { CheckpointDto(it.id, it.projectId, it.version, it.title, it.model, Json.parseToJsonElement(it.changes).jsonObject, it.createdAt.iso()) }
            )
            dao.cleanCheckpoints(cs.map { it.id }); sent += cs.size
        }
        val ms = dao.dirtyMessages()
        if (ms.isNotEmpty()) {
            supabase.from("messages").upsert(
                ms.map { MessageDto(it.id, it.projectId, it.role, it.content, it.createdAt.iso(), it.deletedAt?.iso()) }
            )
            dao.cleanMessages(ms.map { it.id }); sent += ms.size
        }

        val remoteProjects = supabase.from("projects")
            .select(Columns.list("id", "name", "created_at", "deleted_at")).decodeList<ProjectDto>()
        dao.addMissingProjects(remoteProjects.map {
            val t = it.createdAt.ms()
            ProjectEntity(it.id, it.name, t, t, it.deletedAt?.ms(), dirty = false)
        })
        dao.addMissingFiles(
            supabase.from("project_files").select(Columns.list("id", "project_id", "path", "content", "deleted_at")).decodeList<FileDto>()
                .map { FileEntity(it.id, it.projectId, it.path, it.content, System.currentTimeMillis(), it.deletedAt?.ms(), dirty = false) }
        )
        dao.addMissingCheckpoints(
            supabase.from("checkpoints").select(Columns.list("id", "project_id", "version", "title", "model", "changes", "created_at")).decodeList<CheckpointDto>()
                .map { CheckpointEntity(it.id, it.projectId, it.version, it.title, it.model, it.changes.toString(), it.createdAt.ms(), dirty = false) }
        )
        dao.addMissingMessages(
            supabase.from("messages").select(Columns.list("id", "project_id", "role", "content", "created_at", "deleted_at")).decodeList<MessageDto>()
                .map { MessageEntity(it.id, it.projectId, it.role, it.content, it.createdAt.ms(), it.deletedAt?.ms(), dirty = false) }
        )
        "Synced: $sent changes sent, ${remoteProjects.size} projects in the cloud."
    }
}
