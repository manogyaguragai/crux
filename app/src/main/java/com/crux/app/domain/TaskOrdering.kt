package com.crux.app.domain

import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus

/**
 * The sort that IS the product (data-model.md). Implement once, test it, use everywhere.
 *
 * Group level (handled by the caller): projects by rank ascending; the inbox group
 * (projectId == null) always last. ARCHIVED tasks never enter lists.
 *
 * Within a group, this comparator:
 *   1. status: OPEN before DONE
 *   2. priority ascending (1 hottest)
 *   3. dueAt ascending, nulls last
 *   4. createdAt ascending
 */
val withinGroupComparator: Comparator<Task> =
    compareBy<Task> { it.status == TaskStatus.DONE }   // false (OPEN) sorts first
        .thenBy { it.priority }
        .thenBy(nullsLast()) { it.dueAt }
        .thenBy { it.createdAt }
