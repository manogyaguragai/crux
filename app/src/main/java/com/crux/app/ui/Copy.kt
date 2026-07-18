package com.crux.app.ui

/**
 * User-visible copy for phase 0, drawn verbatim from the copy bank in
 * 02-design/ui-ux-decisions.md. Chisel rules: lowercase, calm, numbers first,
 * no em dashes, no exclamation points. New strings get proposed in Decisions,
 * never invented here.
 */
object Copy {
    // titled-tab headers: the mono eyebrow above each Display title (mockup .eb). the sublines
    // beneath compose live counts inline (mono, the count portion in ember), so they live in-screen.
    const val STACK_EYEBROW = "every open task"
    const val PROJECTS_EYEBROW = "rank sets the default"
    const val REVIEW_EYEBROW = "on your terms · never mid-capture"
    const val OVERDUE_EYEBROW = "the pile"

    // projects: the blush hint card under the list (mockup .hintcard). ranks read as "R1"-style
    // labels (matching the rank chips), highlighted where the rule is stated.
    const val PROJECTS_HINT_PREFIX = "re-rank in edit. unstated priorities inherit their project's weight: "
    const val PROJECTS_HINT_RULE = "R1 > R2 > R3"
    const val PROJECTS_HINT_SUFFIX = ", then task priority, then due date."

    // review card eyebrows (mockup .peb): the kind of proposal + its project.
    const val REVIEW_CARD_AI_FILED = "ai filed"

    // navigation labels (the four tabs, per user-choices.md)
    const val TAB_HOME = "home"
    const val TAB_STACK = "stack"
    const val TAB_PROJECTS = "projects"
    const val TAB_REVIEW = "review"

    // omnibar (copy bank, verbatim). the "ask it anything" half activates in phase 3;
    // in phase 1 everything typed becomes a title-only task.
    const val OMNIBAR_PLACEHOLDER = "add to the stack, or ask it anything…"

    // omnibar parse chips (phase 2). proposed, logged in DECISIONS.log pending owner approval.
    // chisel voice: lowercase, calm. the "new" prefix marks an unrecognised #tag that will be
    // created on capture; the two-dates notice explains why only the first date stuck.
    const val OMNIBAR_CHIP_NEW = "new"
    const val OMNIBAR_NOTICE_TWO_DATES = "two dates. kept the first"

    // voice capture (phase 4). calm, positive framing: the two models are "lightweight" and
    // "capable", never "less/more accurate". the download is one-time and stays on device.
    const val VOICE_SETUP_TITLE = "set up voice"
    const val VOICE_SETUP_SUB = "a one-time download. after that, hold-to-talk works offline on your device."
    const val VOICE_LIGHT = "lightweight"
    const val VOICE_LIGHT_SUB = "about 100 mb, quick to set up"
    const val VOICE_CAPABLE = "capable"
    const val VOICE_CAPABLE_SUB = "about 150 mb, hears more, best on wifi"
    const val VOICE_SETUP_CANCEL = "not now"
    const val VOICE_DOWNLOADING = "downloading voice"
    const val VOICE_PREPARING = "getting ready"
    const val VOICE_LISTENING = "listening"
    const val VOICE_TRANSCRIBING = "writing it down"
    const val VOICE_HINT = "hold to talk"
    const val VOICE_MIC_DENIED = "voice needs the microphone. enable it in settings."

    // undo snackbar (copy bank: "done. undo", where undo is the action)
    const val SNACKBAR_DONE = "done."
    const val SNACKBAR_UNDO = "undo"

    // empty states (copy bank, verbatim)
    const val EMPTY_HOME = "clear trail. nothing overdue."
    // overdue pile (pushed screen behind the home nudge). proposed, pending owner approval.
    const val OVERDUE_TITLE = "overdue"
    const val OVERDUE_EMPTY = "nothing overdue. clear trail."
    // overdue actions (mockup 04): the gesture hint, the bulk-carry button, and the reset footer.
    const val OVERDUE_HINT = "swipe right → today · hold → reschedule · tick → done anyway"
    const val OVERDUE_CARRY_ALL = "carry all to today"
    const val OVERDUE_FOOTER = "the nudge resets each morning · nothing here badges the icon"
    const val EMPTY_STACK = "nothing here yet. the omnibar is on home."
    // stack view toggle + week view (phase 2). proposed, pending owner approval.
    const val STACK_VIEW_STACK = "stack"
    const val STACK_VIEW_WEEK = "week"
    const val EMPTY_WEEK = "nothing due this week."
    const val STACK_INBOX = "inbox" // the catch-all group header, always last (data-model.md)
    // week view (mockup 03): the gcal sync tag on synced events, and the month handoff line at the
    // bottom — anything past the 7-day ceiling is google calendar's job (chisel voice, arrows per mockup).
    const val WEEK_SYNCED = "synced ↗ gcal"
    const val WEEK_HANDOFF = "full month → google calendar ↗"
    const val EMPTY_REVIEW = "no questions today."
    // projects strings: proposed in phase 1 (the copy bank had no projects entries).
    // Logged in DECISIONS.log, pending owner approval. Chisel voice: lowercase, calm, no exclaims.
    const val EMPTY_PROJECTS = "no projects yet. name one to start ranking the stack."
    const val PROJECT_CREATE_PLACEHOLDER = "name a project"
    const val PROJECT_DUPLICATE = "a project with that name exists."
    const val PROJECTS_EDIT = "edit"
    const val PROJECTS_DONE = "done"

    // settings (pushed screen). proposed in phase 1, pending owner approval.
    const val SETTINGS_TITLE = "settings"
    const val SETTINGS_APPEARANCE = "appearance"
    const val SETTINGS_DEEP = "deep"
    const val SETTINGS_DEEP_SUB = "true-black background, easier on oled"
    const val SETTINGS_TEXT_SIZE = "text size"
    const val SETTINGS_HOME_COUNT = "tasks on home"
    const val SETTINGS_HOME_COUNT_SUB = "how many open tasks the home screen shows"
    const val SETTINGS_DATA = "data"
    const val SETTINGS_RESET = "reset everything"
    const val SETTINGS_RESET_SUB = "erase all tasks, projects, and settings"
    const val SETTINGS_RESET_WARN = "this erases every task, project, and setting. it cannot be undone."
    const val SETTINGS_RESET_CONFIRM = "erase everything"
    const val SETTINGS_CANCEL = "cancel"
    const val SETTINGS_EXPORT = "export a backup"
    const val SETTINGS_EXPORT_SUB = "save everything to a json file"
    const val SETTINGS_IMPORT = "import a backup"
    const val SETTINGS_IMPORT_SUB = "replace everything from a json file"
    const val BACKUP_FILENAME = "crux-backup.json"
    const val SETTINGS_HISTORY = "history"
    const val SETTINGS_HISTORY_SUB = "everything you've finished"
    const val SETTINGS_CLEAR_ARCHIVED = "clear archived projects"
    const val SETTINGS_CLEAR_ARCHIVED_CONFIRM = "clear"
    const val HISTORY_TITLE = "history"
    const val HISTORY_EMPTY = "nothing finished yet. tick something off."
    const val HISTORY_TODAY = "today"
    const val HISTORY_YESTERDAY = "yesterday"
    // about section (mockup 06): the quietest group — name + version, one line.
    const val SETTINGS_ABOUT = "about"
    const val SETTINGS_ABOUT_NAME = "crux"
    const val SETTINGS_ABOUT_VERSION = "0.1 · kathmandu"
    const val SETTINGS_NOTIFICATIONS = "notifications"
    const val NOTIF_MORNING = "morning"
    const val NOTIF_MORNING_SUB = "the day's climb"
    const val NOTIF_DUE = "due"
    const val NOTIF_DUE_SUB = "when a task's time arrives"
    const val NOTIF_WRAP = "wrap"
    const val NOTIF_WRAP_SUB = "an evening summary"

    // intelligence / AI assist (phase 3). proposed, pending owner approval (chisel voice: lowercase,
    // calm, no exclaims). BYOK: the owner picks one provider and pastes their own key.
    const val SETTINGS_AI = "intelligence"
    const val SETTINGS_AI_ASSIST = "ai assist"
    const val SETTINGS_AI_ASSIST_SUB = "let a model fill what the rules miss. off by default."
    const val AI_PROVIDER_PICK = "which provider"
    const val AI_KEY_TITLE = "bring your own key"
    const val AI_KEY_PLACEHOLDER = "paste your api key"
    const val AI_KEY_SAVE = "save"
    const val AI_KEY_GET = "get a key"
    const val AI_KEY_REMOVE = "remove key"
    const val AI_KEY_SET = "key set"
    const val AI_KEY_NEEDED = "add a key to turn this on"
    const val AI_KEY_SAVED_LABEL = "saved key"
    const val AI_KEY_USE = "use this key"
    const val AI_KEY_REPLACE = "paste a new key below to replace it"

    // voice model management (phase 4). shown in the intelligence section of settings.
    const val SETTINGS_VOICE = "voice"
    const val SETTINGS_VOICE_SUB = "hold-to-talk capture, on device"
    const val SETTINGS_VOICE_NONE = "not set up yet"
    const val SETTINGS_VOICE_LIGHT_READY = "lightweight model ready"
    const val SETTINGS_VOICE_CAPABLE_READY = "capable model ready"
    const val SETTINGS_VOICE_HIGH_READY = "high-accuracy model ready"
    const val SETTINGS_VOICE_SWITCH = "switch to" // "switch to capable", "switch to high accuracy"
    const val SETTINGS_VOICE_REMOVE = "remove"
    const val VOICE_HIGH = "high accuracy" // the larger opt-in tier (small multilingual, ~360 mb)

    // command mode + review outcomes (phase 3). proposed, pending owner approval.
    const val AI_NOT_FOUND = "couldn't find that task"
    const val AI_PICK_TITLE = "which one"
    const val AI_ARCHIVE_CONFIRM_TITLE = "archive this task"
    const val AI_ARCHIVE = "archive"
    const val AI_RESCHEDULE_TITLE = "move this task"
    const val AI_RESCHEDULE_APPLY = "move it"
    const val AI_OFFLINE = "offline. parsing by rules."
    // AI status-icon notices, per error kind (chisel voice). the icon breathes them out on failure.
    const val AI_NOTICE_QUOTA = "no api quota. parsing by rules."
    const val AI_NOTICE_RATE = "rate limited. parsing by rules."
    const val AI_NOTICE_NETWORK = "offline. parsing by rules."
    const val AI_NOTICE_AUTH = "key rejected. check settings."
    const val AI_NOTICE_FAILED = "ai unavailable. parsing by rules."
    const val REVIEW_TITLE = "review"
    const val REVIEW_AI_OFF = "turn on ai assist in settings to get suggestions."
    const val REVIEW_SCAN = "sort the inbox"
    const val REVIEW_SCANNING = "reading the inbox…"
    const val REVIEW_FILE = "file it"
    const val REVIEW_DISMISS = "not now"
    const val REVIEW_EMPTY_INBOX = "inbox is clear. nothing to sort."
    const val REVIEW_SECTION_PRIORITY = "reprioritize"
    // review batch actions (mockup 08): the approve-all pill, and the reorder card's keep/move pair.
    const val REVIEW_APPROVE_ALL = "approve all"
    const val REVIEW_REORDER = "reorder"
    const val REVIEW_KEEP_ORDER = "keep order"
    const val REVIEW_MOVE_UP = "move up"

    // capture queue (phase 3, owner request). proposed, pending owner approval.
    const val QUEUE_TITLE = "queue"
    const val QUEUE_EMPTY = "nothing queued. fire away."
    const val QUEUE_CLEAR = "clear finished"
    const val QUEUE_PENDING = "queued"
    const val QUEUE_WORKING = "working…"
    const val QUEUE_ADDED = "added"

    // task detail (pushed screen). Field labels are lowercase eyebrows; proposed in phase 1
    // (logged in DECISIONS.log), pending owner approval.
    const val DETAIL_TITLE_PLACEHOLDER = "untitled"
    // detail chrome (mockup 05): the back breadcrumb, the ai-provenance chip top-right, and the
    // per-task reminder chip on the due row (offset before a timed task, "remind 3:30" style).
    const val DETAIL_BACK = "back"
    const val DETAIL_EDITED_BY_AI = "edited by ai"
    const val DETAIL_REMIND = "remind"
    // reminder offsets before a timed due (minutes). the chip cycles off → 10 → 30 → 60 → off and
    // shows the resulting clock time, e.g. a 4pm task at offset 30 reads "remind 3:30".
    val REMIND_OFFSETS = listOf(10, 30, 60)
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
