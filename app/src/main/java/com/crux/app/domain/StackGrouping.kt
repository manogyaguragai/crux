package com.crux.app.domain

import com.crux.app.domain.model.Project
import com.crux.app.domain.model.Task

/** One stack group: a project's tasks (or the inbox), already in within-group order. */
data class StackGroup(val projectId: Long?, val title: String, val tasks: List<Task>)

/**
 * Group the stack by project rank, inbox last (data-model.md). Active projects come first in rank
 * order; then a single inbox group holds every task with no project (or one whose project has been
 * archived, so it is no longer active). Empty groups are omitted so the stack never shows a bare
 * header. Within each group the [withinGroupComparator] applies (OPEN before DONE, done sinks).
 */
fun groupStack(tasks: List<Task>, projects: List<Project>, inboxTitle: String): List<StackGroup> {
    val active = projects.sortedBy { it.rank }
    val activeIds = active.mapTo(HashSet()) { it.id }
    val groups = ArrayList<StackGroup>(active.size + 1)
    for (p in active) {
        val ts = tasks.filter { it.projectId == p.id }.sortedWith(withinGroupComparator)
        if (ts.isNotEmpty()) groups += StackGroup(p.id, p.name, ts)
    }
    val inbox = tasks
        .filter { it.projectId == null || it.projectId !in activeIds }
        .sortedWith(withinGroupComparator)
    if (inbox.isNotEmpty()) groups += StackGroup(null, inboxTitle, inbox)
    return groups
}
