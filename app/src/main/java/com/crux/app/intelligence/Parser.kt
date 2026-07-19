package com.crux.app.intelligence

import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The deterministic grammar (intelligence.md, phase 2). A pure, offline, Android-free function:
 * tokens may appear anywhere; extraction order is project, priority, recurrence, date, time; the
 * cleaned remainder is the title. The parser never fails — worst case the whole line is the title.
 *
 * The parser produces LOCAL date/time only; the repository combines them with the device zone into
 * the task's epoch-milli dueAt. Kept side-effect free and covered by the golden table
 * (intelligence/ParserGoldenTest) — the phase-2 spec.
 */

/**
 * A project the parser may match a `#token` against, by case-insensitive prefix. [description] is
 * free-text context (may be blank) shown to the LLM for smarter assignment; the rules ignore it.
 */
data class KnownProject(val id: Long, val name: String, val description: String = "")

/**
 * The five extractable fields. A dismissed chip suppresses its field, so the token's words fall
 * back into the title (the omnibar's tap-to-correct affordance rides on this).
 */
enum class ParseField { PROJECT, PRIORITY, RECURRENCE, DATE, TIME }

/** Gentle ambiguities the grammar surfaces as non-blocking chips (copy lives in the UI). */
enum class ParseNotice { TWO_DATES }

/** The outcome of a `#token`: either it prefix-matched a real project, or it is a new name. */
sealed interface ProjectRef {
    data class Matched(val id: Long, val name: String) : ProjectRef
    /** No project matched; the UI offers a `create # raw?` chip. */
    data class Unknown(val raw: String) : ProjectRef
}

/**
 * Everything the grammar extracted. Unstated fields stay null (priority null => the task default of
 * 3 downstream). [dueDate] + [time] are local; [hasTime] false means all-day. Recurrence carries the
 * shape plus its first due (already folded into [dueDate]). [notices] are gentle chips for ambiguity
 * (e.g. two dates found).
 */
data class ParseResult(
    val title: String,
    val project: ProjectRef? = null,
    val priority: Int? = null,
    val dueDate: LocalDate? = null,
    val hasTime: Boolean = false,
    val time: LocalTime? = null,
    val recurrenceType: RecurrenceType? = null,
    val recurrenceWeekday: Int? = null, // 1..7 Monday=1, WEEKLY only
    val recurrenceDay: Int? = null,     // 1..31, MONTHLY only
    val notices: List<ParseNotice> = emptyList(),
)

private val WEEKDAYS: Map<String, Int> = mapOf(
    "monday" to 1, "mon" to 1,
    "tuesday" to 2, "tue" to 2, "tues" to 2,
    "wednesday" to 3, "wed" to 3,
    "thursday" to 4, "thu" to 4, "thur" to 4, "thurs" to 4,
    "friday" to 5, "fri" to 5,
    "saturday" to 6, "sat" to 6,
    "sunday" to 7, "sun" to 7,
)

private val MONTHS: Map<String, Int> = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
).withIndex().flatMap { (i, full) ->
    listOf(full to i + 1, full.take(3) to i + 1)
}.toMap()

private val PRIORITY = Regex("^!p?([1-4])$")
private val TIME_12H = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)$")
private val TIME_24H = Regex("^(\\d{1,2}):(\\d{2})$")

private fun weekdayOf(w: String?): Int? = w?.let { WEEKDAYS[it] }
private fun monthOf(w: String?): Int? = w?.let { MONTHS[it] }
private fun intIn(w: String?, range: IntRange): Int? =
    w?.toIntOrNull()?.takeIf { it in range }

/**
 * Parse [input] against [today] and the caller's [projects]. Pure: no clock, no zone, no Android.
 * Fields named in [suppress] are skipped, so their tokens stay in the title — this is how the
 * omnibar's chip dismissal "corrects" a false positive without the user retyping.
 */
fun parse(
    input: String,
    today: LocalDate,
    projects: List<KnownProject>,
    suppress: Set<ParseField> = emptySet(),
): ParseResult {
    val raw = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (raw.isEmpty()) return ParseResult(title = "")

    // norm[i] mirrors raw[i], lowercased with trailing sentence punctuation shaved (":" and "!" and
    // "#" stay — they carry meaning for times, priorities, and projects).
    val norm = raw.map { it.lowercase().trimEnd(',', '.', ';') }
    val n = raw.size
    val used = BooleanArray(n)
    fun free(i: Int) = i in 0 until n && !used[i]

    var project: ProjectRef? = null
    var priority: Int? = null
    var recurrence: RecurrenceType? = null
    var recWeekday: Int? = null
    var recDay: Int? = null
    var date: LocalDate? = null
    var time: LocalTime? = null
    var hasTime = false
    val notices = mutableListOf<ParseNotice>()

    // 1. project: the first #token, prefix-matched case-insensitively.
    if (ParseField.PROJECT !in suppress) for (i in 0 until n) {
        if (!free(i)) continue
        val w = norm[i]
        if (w.startsWith("#") && w.length > 1) {
            val name = w.drop(1)
            val hit = projects.firstOrNull { it.name.lowercase().startsWith(name) }
            project = if (hit != null) ProjectRef.Matched(hit.id, hit.name) else ProjectRef.Unknown(name)
            used[i] = true
            break
        }
    }

    // 2. priority: the first !p1..!p4 (or !1..!4).
    if (ParseField.PRIORITY !in suppress) for (i in 0 until n) {
        if (!free(i)) continue
        PRIORITY.matchEntire(norm[i])?.let {
            priority = it.groupValues[1].toInt()
            used[i] = true
        }
        if (priority != null) break
    }

    // 3. recurrence: the four shapes only, first match wins.
    if (ParseField.RECURRENCE !in suppress) run {
        var i = 0
        while (i < n) {
            if (!free(i)) { i++; continue }
            val w = norm[i]
            val next = if (free(i + 1)) norm[i + 1] else null
            when {
                w == "daily" -> { recurrence = RecurrenceType.DAILY; used[i] = true }
                w == "weekdays" -> { recurrence = RecurrenceType.WEEKDAYS; used[i] = true }
                w == "weekly" -> { recurrence = RecurrenceType.WEEKLY; used[i] = true }
                w == "monthly" -> {
                    recurrence = RecurrenceType.MONTHLY; used[i] = true
                    recDay = consumeOnDay(norm, used, i + 1)
                }
                w == "every" && next != null -> when {
                    next == "day" -> { recurrence = RecurrenceType.DAILY; used[i] = true; used[i + 1] = true }
                    next == "weekday" || next == "weekdays" ->
                        { recurrence = RecurrenceType.WEEKDAYS; used[i] = true; used[i + 1] = true }
                    next == "month" -> {
                        recurrence = RecurrenceType.MONTHLY; used[i] = true; used[i + 1] = true
                        recDay = consumeOnDay(norm, used, i + 2)
                    }
                    weekdayOf(next) != null -> {
                        recurrence = RecurrenceType.WEEKLY; recWeekday = weekdayOf(next)
                        used[i] = true; used[i + 1] = true
                    }
                    else -> {}
                }
            }
            if (recurrence != null) break
            i++
        }
    }

    // 4. date: the first date token. A second one is a gentle notice, not an override.
    if (ParseField.DATE !in suppress) run {
        var i = 0
        while (i < n) {
            if (!free(i)) { i++; continue }
            val w = norm[i]
            val next = if (free(i + 1)) norm[i + 1] else null
            var matched: LocalDate? = null
            var span = 1
            when {
                w == "today" || w == "tod" -> matched = today
                w == "tonight" -> { matched = today; time = LocalTime.of(20, 0); hasTime = true }
                w == "tomorrow" || w == "tmr" -> matched = today.plusDays(1)
                w == "in" && intIn(next, 1..365) != null &&
                    (norm.getOrNull(i + 2) == "day" || norm.getOrNull(i + 2) == "days") &&
                    free(i + 2) -> {
                    matched = today.plusDays(next!!.toLong()); span = 3
                }
                w == "next" && next == "week" -> { matched = mondayNextWeek(today); span = 2 }
                w == "next" && weekdayOf(next) != null ->
                    { matched = nextWeekday(today, weekdayOf(next)!!, includeToday = false); span = 2 }
                weekdayOf(w) != null -> matched = nextWeekday(today, weekdayOf(w)!!, includeToday = true)
                monthOf(w) != null && intIn(next, 1..31) != null ->
                    { matched = monthDay(today, monthOf(w)!!, next!!.toInt()); span = 2 }
                intIn(w, 1..31) != null && monthOf(next) != null ->
                    { matched = monthDay(today, monthOf(next)!!, w.toInt()); span = 2 }
            }
            if (matched != null) {
                if (date == null) {
                    date = matched
                    for (k in i until i + span) if (k < n) used[k] = true
                } else {
                    notices += ParseNotice.TWO_DATES
                }
            }
            i++
        }
    }

    // 5. time: the first time token. "at" is a connector for "at 9" / "at 2pm".
    if (ParseField.TIME !in suppress) run {
        var i = 0
        while (i < n) {
            if (!free(i)) { i++; continue }
            val w = norm[i]
            var span = 1
            var t: LocalTime? = null
            if (w == "at" && free(i + 1)) {
                val nx = norm[i + 1]
                val bare = intIn(nx, 0..23)
                when {
                    bare != null -> { t = LocalTime.of(bare, 0); span = 2 }
                    parseTime(nx) != null -> { t = parseTime(nx); span = 2 }
                }
            } else {
                t = parseTime(w)
            }
            if (t != null && time == null) {
                time = t
                hasTime = true
                for (k in i until i + span) if (k < n) used[k] = true
            }
            i++
        }
    }

    // Fold recurrence + a bare time into a concrete first due.
    val rec = recurrence
    val explicitDate = date
    if (rec != null && explicitDate == null) {
        date = firstRecurrenceDue(rec, today, recWeekday, recDay)
    }
    // A recurrence's weekday/day is derived from an explicit date when not stated outright.
    date?.let { d ->
        if (rec == RecurrenceType.WEEKLY && recWeekday == null) recWeekday = d.dayOfWeek.value
        if (rec == RecurrenceType.MONTHLY && recDay == null) recDay = d.dayOfMonth
    }
    // A bare time with no date means today — but never resurrect a date the user dismissed.
    if (time != null && date == null && ParseField.DATE !in suppress) date = today

    val title = raw.filterIndexed { i, _ -> !used[i] }.joinToString(" ").trim()

    return ParseResult(
        title = title,
        project = project,
        priority = priority,
        dueDate = date,
        hasTime = hasTime,
        time = time,
        recurrenceType = recurrence,
        recurrenceWeekday = recWeekday,
        recurrenceDay = recDay,
        notices = notices,
    )
    // Numeric slash dates ("5/1") are deliberately NOT parsed: day-month vs month-day is ambiguous
    // and guessing wrong silently reschedules the user. They fall through and stay in the title.
}

/**
 * Fold a parse result into a persistable [Task]. [projectId] is resolved by the caller (a matched
 * project's id, a freshly-created id for an unknown `#tag`, or null for the inbox). The local
 * due date + time collapse to an epoch-milli instant in [zone]; all-day tasks anchor to midnight.
 * parsedBy is RULES when the grammar filled any field, MANUAL when the line was pure title.
 */
fun ParseResult.toTask(projectId: Long?, zone: ZoneId, now: Long, source: Source = Source.TYPED): Task {
    val dueAt = dueDate?.let { d ->
        val t = if (hasTime) time ?: LocalTime.MIDNIGHT else LocalTime.MIDNIGHT
        d.atTime(t).atZone(zone).toInstant().toEpochMilli()
    }
    val grammarTouched = projectId != null || priority != null || dueAt != null ||
        recurrenceType != null
    return Task(
        projectId = projectId,
        title = title.trim(),
        priority = priority ?: 3,
        dueAt = dueAt,
        hasTime = hasTime && dueAt != null,
        recurrenceType = recurrenceType,
        recurrenceWeekday = recurrenceWeekday,
        recurrenceDay = recurrenceDay,
        createdAt = now,
        source = source,
        parsedBy = if (grammarTouched) ParsedBy.RULES else ParsedBy.MANUAL,
    )
}

/** "on N" following a monthly keyword => day N, consuming both words. Null if absent. */
private fun consumeOnDay(norm: List<String>, used: BooleanArray, i: Int): Int? {
    if (i + 1 >= norm.size || used[i] || used[i + 1]) return null
    if (norm[i] != "on") return null
    val day = intIn(norm[i + 1], 1..31) ?: return null
    used[i] = true
    used[i + 1] = true
    return day
}

private fun parseTime(w: String): LocalTime? {
    TIME_12H.matchEntire(w)?.let { m ->
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val pm = m.groupValues[3] == "pm"
        if (h !in 1..12 || min !in 0..59) return null
        if (h == 12) h = 0
        if (pm) h += 12
        return LocalTime.of(h, min)
    }
    TIME_24H.matchEntire(w)?.let { m ->
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }
    return null
}

/** The next date whose weekday is [target]; today counts only when [includeToday]. */
private fun nextWeekday(today: LocalDate, target: Int, includeToday: Boolean): LocalDate {
    var d = if (includeToday) today else today.plusDays(1)
    while (d.dayOfWeek.value != target) d = d.plusDays(1)
    return d
}

/** Monday of the week after the current one. */
private fun mondayNextWeek(today: LocalDate): LocalDate =
    today.minusDays((today.dayOfWeek.value - 1).toLong()).plusDays(7)

/** [month]/[day] resolved this year, rolling to next year if already past (clamped to month length). */
private fun monthDay(today: LocalDate, month: Int, day: Int): LocalDate {
    var candidate = LocalDate.of(today.year, month, 1)
    candidate = candidate.withDayOfMonth(minOf(day, candidate.lengthOfMonth()))
    if (candidate.isBefore(today)) {
        candidate = candidate.plusYears(1)
        candidate = candidate.withDayOfMonth(minOf(day, candidate.lengthOfMonth()))
    }
    return candidate
}

/** First due for a recurrence stated without a date: the soonest matching day, today inclusive. */
private fun firstRecurrenceDue(
    type: RecurrenceType,
    today: LocalDate,
    weekday: Int?,
    day: Int?,
): LocalDate = when (type) {
    RecurrenceType.DAILY -> today
    RecurrenceType.WEEKDAYS -> {
        var d = today
        while (d.dayOfWeek.value > 5) d = d.plusDays(1)
        d
    }
    RecurrenceType.WEEKLY -> nextWeekday(today, weekday ?: today.dayOfWeek.value, includeToday = true)
    RecurrenceType.MONTHLY -> {
        val d = day ?: today.dayOfMonth
        val thisMonth = today.withDayOfMonth(minOf(d, today.lengthOfMonth()))
        if (!thisMonth.isBefore(today)) thisMonth
        else {
            val nm = today.plusMonths(1)
            nm.withDayOfMonth(minOf(d, nm.lengthOfMonth()))
        }
    }
}
