package com.crux.app.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Combined progress across a model's whole download: bytes so far / total bytes across all files. */
data class DownloadProgress(val bytes: Long, val total: Long) {
    val fraction: Float get() = if (total <= 0L) 0f else (bytes.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Downloads a [VoiceModel]'s three files into its store dir, one after another, reporting combined
 * progress. Each file streams to a ".part" sibling and is renamed to its final name only once
 * complete, so [VoiceModelStore.isReady] never sees a half file. Resumable: a ".part" left by a
 * dropped connection is continued with an HTTP Range header rather than restarting a ~130 MB decoder
 * from zero (the HF CDN answers Range requests with 206). Plain HttpURLConnection, matching the app's
 * no-extra-networking-dependency stance (see intelligence/LlmClient).
 *
 * Cancellable: the copy loop honours coroutine cancellation and leaves the ".part" in place so the
 * next attempt resumes. A failure mid-way throws; the caller surfaces it and can retry.
 */
class ModelDownloader(private val store: VoiceModelStore) {

    suspend fun download(model: VoiceModel, onProgress: (DownloadProgress) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val dir = store.dirFor(model).apply { mkdirs() }
            // Learn each file's size first so the progress bar spans the whole set, not one file.
            val sizes = model.files.associateWith { sizeOf(model.urlFor(it)) }
            val total = sizes.values.sum()
            var done = 0L
            for (file in model.files) {
                val target = File(dir, file)
                val size = sizes[file] ?: 0L
                if (target.isFile && size > 0 && target.length() == size) { // already fully there
                    done += size
                    onProgress(DownloadProgress(done, total))
                    continue
                }
                fetchOne(model.urlFor(file), target, done, total, onProgress)
                done += target.length()
            }
            onProgress(DownloadProgress(total, total))
        }

    /** HEAD the URL for its Content-Length (following the HF redirect to the CDN). 0 if unknown. */
    private fun sizeOf(url: String): Long {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
        }
        return try {
            c.connect()
            c.contentLengthLong.coerceAtLeast(0L)
        } finally {
            c.disconnect()
        }
    }

    /**
     * Stream one file to <target>.part (resuming if a partial is present), then rename to [target].
     * Reports progress as [doneBefore] + this file's bytes, out of [total].
     */
    private suspend fun fetchOne(
        url: String,
        target: File,
        doneBefore: Long,
        total: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val part = File(target.parentFile, target.name + PART)
        var have = if (part.isFile) part.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        c.connect()
        // If the server ignored our Range (200 rather than 206), restart the file from scratch.
        val resuming = c.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!resuming) have = 0L
        c.inputStream.use { input ->
            FileOutputStream(part, resuming).use { out ->
                val buf = ByteArray(BUFFER)
                var read = input.read(buf)
                while (read >= 0) {
                    coroutineContext.ensureActive() // cancellation leaves .part for a later resume
                    out.write(buf, 0, read)
                    have += read
                    onProgress(DownloadProgress(doneBefore + have, total))
                    read = input.read(buf)
                }
            }
        }
        c.disconnect()
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) { // cross-filesystem safety net
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }

    private companion object {
        const val PART = ".part"
        const val BUFFER = 64 * 1024
        const val CONNECT_MS = 20_000
        const val READ_MS = 30_000
    }
}
