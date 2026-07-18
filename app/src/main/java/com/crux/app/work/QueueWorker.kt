package com.crux.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crux.app.CruxApplication
import com.crux.app.data.queue.QueueResult

/**
 * Drains the capture queue (data/queue). Runs via WorkManager so it survives the app being closed —
 * it keeps pulling the next PENDING item, processing it through the UI-free CaptureProcessor, and
 * recording the outcome, until the queue is empty. New items enqueued while it runs are picked up by
 * the loop (it re-reads the queue each pass); the unique-work KEEP policy starts a fresh drainer if
 * none is active.
 */
class QueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CruxApplication).container
        val queue = container.captureQueue
        val processor = container.captureProcessor
        while (true) {
            val next = queue.nextPending() ?: break
            queue.markProcessing(next.id)
            val result = try {
                processor.process(next.text, next.dismissed)
            } catch (e: Exception) {
                QueueResult.Failed(e.message ?: "something went wrong")
            }
            queue.markResult(next.id, result)
        }
        return Result.success()
    }
}
