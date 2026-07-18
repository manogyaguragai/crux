package com.crux.app.data.queue

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.crux.app.intelligence.ParseField
import com.crux.app.work.QueueWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * The capture queue: omnibar submissions land here instantly as PENDING and a [QueueWorker] drains them
 * one by one (rules + optional LLM). Held in a StateFlow the UI observes, and mirrored to a small JSON
 * file so a killed process resumes where it left off — an interrupted PROCESSING item comes back as
 * PENDING, DONE items are dropped. The worker keeps running even if the app is closed (WorkManager).
 */
class CaptureQueue(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    // Monotonic ids, seeded above any persisted id and the clock, so rapid same-millis adds never clash.
    private val idGen = AtomicLong(maxOf(_items.value.maxOfOrNull { it.id } ?: 0L, System.currentTimeMillis()))

    init {
        // Resume any work left pending from a previous run.
        if (_items.value.any { it.status == QueueStatus.PENDING }) scheduleDrain()
    }

    fun enqueue(text: String, dismissed: Set<ParseField>) {
        if (text.isBlank()) return
        val item = QueueItem(idGen.incrementAndGet(), text.trim(), dismissed, QueueStatus.PENDING, null, System.currentTimeMillis())
        mutate { it + item }
        scheduleDrain()
    }

    /** The oldest item still waiting, or null when the queue is drained. Read by the worker loop. */
    fun nextPending(): QueueItem? = _items.value.firstOrNull { it.status == QueueStatus.PENDING }

    fun markProcessing(id: Long) = mutate { list ->
        list.map { if (it.id == id) it.copy(status = QueueStatus.PROCESSING) else it }
    }

    fun markResult(id: Long, result: QueueResult) = mutate { list ->
        list.map {
            if (it.id != id) it else when (result) {
                is QueueResult.Done -> it.copy(status = QueueStatus.DONE, message = result.message)
                is QueueResult.Failed -> it.copy(status = QueueStatus.FAILED, message = result.message)
            }
        }
    }

    /** Swipe-left: forget an item. */
    fun remove(id: Long) = mutate { list -> list.filterNot { it.id == id } }

    /** Swipe-right: re-run a failed (or any) item. */
    fun retry(id: Long) {
        mutate { list -> list.map { if (it.id == id) it.copy(status = QueueStatus.PENDING, message = null) else it } }
        scheduleDrain()
    }

    /** Clear the settled (DONE/FAILED) items, leaving anything still in flight. */
    fun clearFinished() = mutate { list ->
        list.filterNot { it.status == QueueStatus.DONE || it.status == QueueStatus.FAILED }
    }

    /** Part of the hard reset: drop everything and delete the file. */
    fun clear() {
        _items.value = emptyList()
        runCatching { file.delete() }
    }

    private fun scheduleDrain() {
        val request = OneTimeWorkRequestBuilder<QueueWorker>().build()
        // KEEP: a running drainer already re-reads the queue each loop, so it will pick up new items;
        // if none is active this starts one. Either way one drainer runs to empty.
        WorkManager.getInstance(appContext).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun mutate(transform: (List<QueueItem>) -> List<QueueItem>) {
        val next = transform(_items.value)
        _items.value = next
        runCatching { file.writeText(toJson(next)) }
    }

    private fun load(): List<QueueItem> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        fromJson(file.readText()).mapNotNull {
            when (it.status) {
                QueueStatus.DONE -> null // settled successes needn't persist across launches
                QueueStatus.PROCESSING -> it.copy(status = QueueStatus.PENDING) // was interrupted; retry it
                else -> it
            }
        }
    }.getOrDefault(emptyList())

    private fun toJson(items: List<QueueItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("dismissed", JSONArray().apply { item.dismissed.forEach { put(it.name) } })
                    .put("status", item.status.name)
                    .put("message", item.message ?: JSONObject.NULL)
                    .put("createdAt", item.createdAt),
            )
        }
        return arr.toString()
    }

    private fun fromJson(text: String): List<QueueItem> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val dismissed = o.optJSONArray("dismissed")?.let { d ->
                (0 until d.length()).mapNotNull { runCatching { ParseField.valueOf(d.getString(it)) }.getOrNull() }.toSet()
            } ?: emptySet()
            QueueItem(
                id = o.getLong("id"),
                text = o.getString("text"),
                dismissed = dismissed,
                status = runCatching { QueueStatus.valueOf(o.getString("status")) }.getOrDefault(QueueStatus.PENDING),
                message = if (o.isNull("message")) null else o.optString("message"),
                createdAt = o.optLong("createdAt"),
            )
        }
    }

    private companion object {
        const val FILE = "capture_queue.json"
        const val WORK = "capture-queue"
    }
}
