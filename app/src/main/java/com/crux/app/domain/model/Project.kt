package com.crux.app.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A project ranks the tasks filed under it. rank 1 = heaviest; unique among
 * non-archived. name is unique case-insensitive (enforced in the repository, phase 1).
 *
 * description is a free-text note of what the project is; empty by default. It is fed to the
 * LLM alongside the name so task-to-project assignment has real context (phase 4).
 */
@Entity(
    tableName = "projects",
    indices = [Index(value = ["name"], unique = true)],
)
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rank: Int,
    val archived: Boolean = false,
    val createdAt: Long,
    val description: String = "",
)
