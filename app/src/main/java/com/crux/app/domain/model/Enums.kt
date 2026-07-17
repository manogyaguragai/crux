package com.crux.app.domain.model

/** Four recurrence shapes only. No RRULE engine (data-model.md). */
enum class RecurrenceType { DAILY, WEEKDAYS, WEEKLY, MONTHLY }

/** ARCHIVED is soft delete; ARCHIVED tasks never render in lists. */
enum class TaskStatus { OPEN, DONE, ARCHIVED }

/** How the task was captured. */
enum class Source { TYPED, VOICE, SYSTEM }

/** Attribution: every AI-touched field is traceable (architecture.md). */
enum class ParsedBy { MANUAL, RULES, AI }
