package com.crux.app.ui

/**
 * User-visible copy for phase 0, drawn verbatim from the copy bank in
 * 02-design/ui-ux-decisions.md. Chisel rules: lowercase, calm, numbers first,
 * no em dashes, no exclamation points. New strings get proposed in Decisions,
 * never invented here.
 */
object Copy {
    // navigation labels (the four tabs, per user-choices.md)
    const val TAB_HOME = "home"
    const val TAB_STACK = "stack"
    const val TAB_PROJECTS = "projects"
    const val TAB_REVIEW = "review"

    // omnibar (copy bank, verbatim). the "ask it anything" half activates in phase 3;
    // in phase 1 everything typed becomes a title-only task.
    const val OMNIBAR_PLACEHOLDER = "add to the stack, or ask it anything…"

    // undo snackbar (copy bank: "done. undo", where undo is the action)
    const val SNACKBAR_DONE = "done."
    const val SNACKBAR_UNDO = "undo"

    // empty states (copy bank, verbatim)
    const val EMPTY_HOME = "clear trail. nothing overdue."
    const val EMPTY_STACK = "nothing here yet. the omnibar is on home."
    const val EMPTY_REVIEW = "no questions today."
    // projects strings: proposed in phase 1 (the copy bank had no projects entries).
    // Logged in DECISIONS.log, pending owner approval. Chisel voice: lowercase, calm, no exclaims.
    const val EMPTY_PROJECTS = "no projects yet. name one to start ranking the stack."
    const val PROJECT_CREATE_PLACEHOLDER = "name a project"
    const val PROJECT_DUPLICATE = "a project with that name exists."
    const val PROJECTS_EDIT = "edit"
    const val PROJECTS_DONE = "done"

    // task detail (pushed screen). Field labels are lowercase eyebrows; proposed in phase 1
    // (logged in DECISIONS.log), pending owner approval.
    const val DETAIL_TITLE_PLACEHOLDER = "untitled"
    const val DETAIL_PROJECT = "project"
    const val DETAIL_PRIORITY = "priority"
    const val DETAIL_DUE = "due"
    const val DETAIL_TIME = "time"
    const val DETAIL_REPEAT = "repeat"
    const val DETAIL_NOTES = "notes"
    const val DETAIL_NOTES_PLACEHOLDER = "notes"
    const val DETAIL_INBOX = "inbox"
    const val DETAIL_NONE = "none"
    const val DETAIL_ALL_DAY = "all day"
    const val DETAIL_SET_DATE = "set a date"
    const val DETAIL_CLEAR = "clear"
    const val DETAIL_DIALOG_OK = "ok"
    const val DETAIL_DIALOG_CANCEL = "cancel"
    // recurrence shapes (RecurrenceType order: DAILY, WEEKDAYS, WEEKLY, MONTHLY)
    val RECURRENCE_LABELS = listOf("daily", "weekdays", "weekly", "monthly")
}
