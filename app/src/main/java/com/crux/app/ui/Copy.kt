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

    // empty states (copy bank, verbatim)
    const val EMPTY_HOME = "clear trail. nothing overdue."
    const val EMPTY_STACK = "nothing here yet. the omnibar is on home."
    const val EMPTY_REVIEW = "no questions today."
    // note: the copy bank has no projects empty-state string yet. flagged for phase 1.
}
