package com.crux.app.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The unit of work. A task with a time is an event; there is no separate event entity.
 * Timestamps are epoch millis in UTC; display converts to the device zone (data-model.md).
 * projectId null = inbox. Unstated priority defaults to 3, so weight is inherited from
 * the project's rank first.
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["projectId"]), Index(value = ["status"])],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val title: String,
    val notes: String? = null,
    val priority: Int = 3,          // 1..4, 1 hottest
    val dueAt: Long? = null,        // null = undated
    val hasTime: Boolean = false,   // false = all-day
    val recurrenceType: RecurrenceType? = null,
    val recurrenceWeekday: Int? = null, // 1..7, Monday = 1 (WEEKLY only)
    val recurrenceDay: Int? = null,     // 1..31 (MONTHLY only)
    val status: TaskStatus = TaskStatus.OPEN,
    val createdAt: Long,
    val completedAt: Long? = null,
    val source: Source = Source.TYPED,
    val parsedBy: ParsedBy = ParsedBy.MANUAL,
    val calendarEventId: Long? = null,
)
