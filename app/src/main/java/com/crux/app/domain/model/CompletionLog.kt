package com.crux.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The record a DONE task leaves behind. Written on tick; the task row itself is
 * deleted at the midnight sweep, so this snapshot is the permanent history.
 */
@Entity(tableName = "completion_log")
data class CompletionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val titleSnapshot: String,
    val projectNameSnapshot: String?,
    val completedAt: Long,
)
