package com.crux.app.data.backup

import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

/** All of a backup's rows in one value; ids are preserved so a restore is faithful. */
data class BackupData(
    val projects: List<Project>,
    val tasks: List<Task>,
    val completions: List<CompletionLog>,
)

/**
 * The versioned JSON envelope for export / import (data-model.md). Pure functions (no Android, no
 * Room) so the round-trip is unit-tested on the JVM. Enums are stored by name; nullable fields use
 * JSON null, and a missing key decodes to null so a newer field never breaks an older file.
 */
object BackupJson {
    const val VERSION = 1

    fun encode(data: BackupData, exportedAt: Long): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", exportedAt)
        root.put("projects", JSONArray().apply { data.projects.forEach { put(it.toJson()) } })
        root.put("tasks", JSONArray().apply { data.tasks.forEach { put(it.toJson()) } })
        root.put("completions", JSONArray().apply { data.completions.forEach { put(it.toJson()) } })
        return root.toString(2)
    }

    fun decode(json: String): BackupData {
        val root = JSONObject(json)
        return BackupData(
            projects = root.getJSONArray("projects").objects().map { it.toProject() },
            tasks = root.getJSONArray("tasks").objects().map { it.toTask() },
            completions = root.getJSONArray("completions").objects().map { it.toCompletion() },
        )
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.putOrNull(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }

private fun Project.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("rank", rank); put("archived", archived); put("createdAt", createdAt)
}

private fun JSONObject.toProject() = Project(
    id = getLong("id"),
    name = getString("name"),
    rank = getInt("rank"),
    archived = getBoolean("archived"),
    createdAt = getLong("createdAt"),
)

private fun Task.toJson() = JSONObject().apply {
    put("id", id)
    putOrNull("projectId", projectId)
    put("title", title)
    putOrNull("notes", notes)
    put("priority", priority)
    putOrNull("dueAt", dueAt)
    put("hasTime", hasTime)
    putOrNull("recurrenceType", recurrenceType?.name)
    putOrNull("recurrenceWeekday", recurrenceWeekday)
    putOrNull("recurrenceDay", recurrenceDay)
    put("status", status.name)
    put("createdAt", createdAt)
    putOrNull("completedAt", completedAt)
    put("source", source.name)
    put("parsedBy", parsedBy.name)
    putOrNull("calendarEventId", calendarEventId)
    putOrNull("remindOffsetMinutes", remindOffsetMinutes)
}

private fun JSONObject.toTask() = Task(
    id = getLong("id"),
    projectId = longOrNull("projectId"),
    title = getString("title"),
    notes = stringOrNull("notes"),
    priority = getInt("priority"),
    dueAt = longOrNull("dueAt"),
    hasTime = getBoolean("hasTime"),
    recurrenceType = stringOrNull("recurrenceType")?.let { RecurrenceType.valueOf(it) },
    recurrenceWeekday = intOrNull("recurrenceWeekday"),
    recurrenceDay = intOrNull("recurrenceDay"),
    status = TaskStatus.valueOf(getString("status")),
    createdAt = getLong("createdAt"),
    completedAt = longOrNull("completedAt"),
    source = Source.valueOf(getString("source")),
    parsedBy = ParsedBy.valueOf(getString("parsedBy")),
    calendarEventId = longOrNull("calendarEventId"),
    remindOffsetMinutes = intOrNull("remindOffsetMinutes"),
)

private fun CompletionLog.toJson() = JSONObject().apply {
    put("id", id); put("taskId", taskId); put("titleSnapshot", titleSnapshot)
    putOrNull("projectNameSnapshot", projectNameSnapshot); put("completedAt", completedAt)
}

private fun JSONObject.toCompletion() = CompletionLog(
    id = getLong("id"),
    taskId = getLong("taskId"),
    titleSnapshot = getString("titleSnapshot"),
    projectNameSnapshot = stringOrNull("projectNameSnapshot"),
    completedAt = getLong("completedAt"),
)
