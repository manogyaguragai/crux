package com.crux.app.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/**
 * Thin intent wrappers that hand a date or an event off to the device's calendar app (google
 * calendar in practice). No new deps — just CalendarContract ACTION_VIEW intents. Every launch is
 * wrapped so a device with no calendar app resolves to a quiet no-op rather than a crash.
 */
object CalendarLauncher {

    /** Open the calendar at [date] (local start-of-day). The "full month → gcal" jump-off. */
    fun openDay(context: Context, date: LocalDate) {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(millis.toString())
            .build()
        launch(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /** Open a specific synced event by its calendar event id. */
    fun openEvent(context: Context, eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        launch(context, Intent(Intent.ACTION_VIEW, uri))
    }

    private fun launch(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // no calendar app resolves this intent — no-op safely.
        }
    }
}
