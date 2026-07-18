package com.crux.app.data.queue

import com.crux.app.intelligence.ParseField

/** Where a queued capture is in its life. PROCESSING is transient; DONE/FAILED are terminal. */
enum class QueueStatus { PENDING, PROCESSING, DONE, FAILED }

/**
 * One line the user fired into the omnibar, waiting to be processed (rules + optional LLM). The queue
 * decouples typing from the slow LLM call: the item lands instantly as PENDING and a worker drains it.
 * [message] carries the outcome once terminal — a short result on DONE, the reason on FAILED.
 */
data class QueueItem(
    val id: Long,
    val text: String,
    val dismissed: Set<ParseField>,
    val status: QueueStatus,
    val message: String?,
    val createdAt: Long,
)

/** What processing one queued line produced. */
sealed interface QueueResult {
    /** Succeeded; [message] is a short summary (e.g. "completed water plants") or null for a plain add. */
    data class Done(val message: String?) : QueueResult
    /** Could not complete (e.g. no match for a command); [message] is shown on the item and is retryable. */
    data class Failed(val message: String) : QueueResult
}
