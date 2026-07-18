package com.crux.app.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Combined progress across a model's whole download: bytes so far out of an estimated total. */
data class DownloadProgress(val bytes: Long, val total: Long) {
    val fraction: Float get() = if (total <= 0L) 0f else (bytes.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Downloads a [VoiceModel]'s three files into its store dir, one after another, reporting combined
 * progress. Each file streams to a ".part" sibling and is renamed to its final name only once
 * complete, so [VoiceModelStore.isReady] never sees a half file — and "final file present" is also our
 * completeness check when resuming, so no size query is needed. Resumable: a ".part" left by a dropped
 * connection or a killed process is continued with an HTTP Range header rather than restarting a
 * ~130 MB decoder from zero.
 *
 * Progress is sized against the model's known approximate byte count ([VoiceModel.approxMb]), NOT a
 * separate HEAD request: HuggingFace serves these files via a signed CDN redirect that made a
 * standalone HEAD from HttpURLConnection unreliable (it could come back with no length, pinning the
 * bar at 0%) and added a multi-second stall before the first byte. The estimate is within a few
 * percent of the real total, so the bar moves smoothly and simply snaps to 100% on completion.
 *
 * Cancellable: the copy loop honours coroutine cancellation and leaves the ".part" for a later resume.
 */
class ModelDownloader(private val store: VoiceModelStore) {

    suspend fun download(model: VoiceModel, onProgress: (DownloadProgress) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val dir = store.dirFor(model).apply { mkdirs() }
            val estTotal = model.approxMb * 1024L * 1024L
            var done = 0L
            for (file in model.files) {
                val target = File(dir, file)
                if (target.isFile) { // a prior run finished this one (rename happens only on completion)
                    done += target.length()
                    onProgress(DownloadProgress(done, estTotal))
                    continue
                }
                done += fetchOne(model.urlFor(file), target, done, estTotal, onProgress)
            }
            onProgress(DownloadProgress(maxOf(done, estTotal), estTotal)) // land on 100%
        }

    /**
     * Stream one file to <target>.part (resuming a leftover partial), then rename to [target]. Reports
     * progress as [doneBefore] + this file's bytes, out of [estTotal]. Returns the file's byte size.
     */
    private suspend fun fetchOne(
        url: String,
        target: File,
        doneBefore: Long,
        estTotal: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): Long {
        val part = File(target.parentFile, target.name + PART)
        var have = if (part.isFile) part.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        c.connect()
        // A stale/oversized partial makes the server answer 416; drop it and restart the file clean.
        if (c.responseCode == 416) {
            c.disconnect()
            part.delete()
            return fetchOne(url, target, doneBefore, estTotal, onProgress)
        }
        // If the server ignored our Range (200 rather than 206), start this file from scratch.
        val resuming = c.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!resuming) have = 0L
        onProgress(DownloadProgress(doneBefore + have, estTotal)) // show the resumed position at once
        c.inputStream.use { input ->
            FileOutputStream(part, resuming).use { out ->
                val buf = ByteArray(BUFFER)
                var read = input.read(buf)
                while (read >= 0) {
                    coroutineContext.ensureActive() // cancellation leaves .part for a later resume
                    out.write(buf, 0, read)
                    have += read
                    onProgress(DownloadProgress(doneBefore + have, estTotal))
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
        return target.length()
    }

    private companion object {
        const val PART = ".part"
        const val BUFFER = 64 * 1024
        const val CONNECT_MS = 20_000
        const val READ_MS = 30_000
    }
}
