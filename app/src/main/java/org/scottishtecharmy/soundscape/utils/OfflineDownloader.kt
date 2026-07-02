package org.scottishtecharmy.soundscape.utils

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.BuildConfig
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.GeoMoshi
import org.scottishtecharmy.soundscape.network.DownloadResult
import org.scottishtecharmy.soundscape.network.FileDownloader
import org.scottishtecharmy.soundscape.network.UserAgentInterceptor
import org.scottishtecharmy.soundscape.network.createAndroidFileDownloader
import org.scottishtecharmy.soundscape.network.createAndroidManifestClient
import org.scottishtecharmy.soundscape.utils.OfflineDownloader.Companion.TAG
import java.io.File
import java.lang.Thread.sleep

suspend fun downloadAndParseManifest(applicationContext: Context): FeatureCollection? {

    val manifestClient = createAndroidManifestClient(
        baseUrl = BuildConfig.EXTRACT_PROVIDER_URL,
        userAgent = UserAgentInterceptor.USER_AGENT,
    )
    val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
    val adapter = moshi.adapter(FeatureCollection::class.java)

    for (retry in 1..4) {
        try {
            return withContext(Dispatchers.IO) {
                val json = manifestClient.getManifestJson()
                    ?: throw Exception("Manifest response null")
                adapter.fromJson(json) ?: throw Exception("Manifest parse failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading manifest $retry", e)
        }
        sleep(500)
    }
    // All retries failed
    Log.e(TAG, "Error downloading manifest after all retries")
    return null
}

// --- Download State Management ---
sealed class DownloadState {
    object Idle : DownloadState()
    object Caching : DownloadState()
    data class Downloading(val progress: Int) : DownloadState() // Progress as a per mil (0-1000)
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
    object Canceled : DownloadState()
}

class OfflineDownloader {

    companion object {
        const val TAG = "OfflineDownloader"
    }

    private var downloadJob: Job? = null
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val fileDownloader: FileDownloader = createAndroidFileDownloader(
        userAgent = UserAgentInterceptor.USER_AGENT,
    )

    fun startDownload(
        fileUrl: String,
        outputFilePath: String,
        extractSize: Double?
    ) {
        if (downloadJob?.isActive == true) {
            Log.w(TAG, "Download is already in progress.")
            return
        }

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Starting download for URL: $fileUrl")

            val tempFile = File("$outputFilePath.downloading")
            val finalFile = File(outputFilePath)

            try {
                // Ensure parent directories exist
                tempFile.parentFile?.mkdirs()

                val maxRetries = 10
                var retries = maxRetries
                while (retries > 0) {
                    Log.d(TAG, "Download attempt $retries")
                    _downloadState.value = DownloadState.Caching
                    val result = fileDownloader.download(
                        url = fileUrl,
                        destFile = tempFile,
                        scope = this,
                    ) { progress ->
                        _downloadState.value = DownloadState.Downloading(progress)
                    }
                    when (result) {
                        is DownloadResult.Success -> {
                            // The server caches extracts lazily and can answer with only the
                            // part of the file it has copied so far. If what we actually
                            // received is smaller than the extract size we expect from the
                            // manifest, the file isn't ready yet - back off and retry exactly
                            // as we do for a 503, rather than publishing a truncated extract.
                            if (extractSize != null && tempFile.length() < extractSize.toLong()) {
                                Log.d(
                                    TAG,
                                    "Extract not fully cached yet (${tempFile.length()} of ${extractSize.toLong()} bytes), retrying",
                                )
                                waitBeforeRetry(this, firstAttempt = retries == maxRetries, extractSize = extractSize)
                                --retries
                                continue
                            }

                            // Verify the download is intact before publishing it. A
                            // truncated or corrupt .pmtiles extract crashes MapLibre
                            // natively when opened, so reject it here and let the user
                            // retry rather than persisting a file that will abort the app.
                            if (finalFile.name.endsWith(".pmtiles") &&
                                !isPmtilesUsable(tempFile.path)) {
                                throw Exception("Downloaded extract failed validation (corrupt or truncated)")
                            }
                            retries = 0
                            // Delete any file that already exists
                            finalFile.delete()
                            // Rename the file on successful completion
                            if (tempFile.renameTo(finalFile)) {
                                _downloadState.value = DownloadState.Success
                                Log.i(TAG, "Download successful. File saved to: ${finalFile.path}")
                            } else {
                                throw Exception("Failed to rename file from ${tempFile.name} to ${finalFile.name}")
                            }
                        }

                        is DownloadResult.HttpError -> {
                            if (result.code == 503) {
                                // The server is likely copying the extract into it's cache and is
                                // asking that we try again a little later.
                                waitBeforeRetry(this, firstAttempt = retries == maxRetries, extractSize = extractSize)
                                --retries
                            } else {
                                throw Exception("Download failed with code: ${result.code} and message: ${result.message}")
                            }
                        }
                    }
                }

                // We exhausted every retry without ever publishing the file (e.g. the server
                // kept returning a partial extract), so surface that rather than leaving the UI
                // stuck on "Caching".
                if (_downloadState.value !is DownloadState.Success) {
                    tempFile.delete()
                    throw Exception("Extract was not ready after $maxRetries attempts")
                }
            } catch (e: CancellationException) {
                // Handle coroutine cancellation
                _downloadState.value = DownloadState.Canceled
                tempFile.delete() // Clean up partial file
                Log.i(TAG, "Download was canceled $e")
            } catch (e: Exception) {
                // Handle other errors (network, file I/O, etc.)
                _downloadState.value =
                    DownloadState.Error(e.message ?: "An unknown error occurred")
                tempFile.delete() // Clean up partial file
                Log.e(TAG, "Download failed", e)
            }
        }
    }

    /**
     * Back off before retrying a download, the same way we do when the server returns a 503 to
     * say it is still copying the extract into its cache. We guess that the caching runs at around
     * 10MB/sec, so on the first attempt we wait for an estimate based on the extract size; on later
     * attempts we just poll every 15 seconds.
     */
    private fun waitBeforeRetry(scope: CoroutineScope, firstAttempt: Boolean, extractSize: Double?) {
        var cachingDuration = 15
        if (firstAttempt && extractSize != null) {
            cachingDuration = (extractSize / 10000000.0).toInt()
        }
        Log.d(TAG, "Wait for $cachingDuration seconds before retrying.")
        _downloadState.value = DownloadState.Caching
        while (cachingDuration > 0) {
            scope.ensureActive()
            sleep(1000)
            --cachingDuration
        }
    }

    fun cancelDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
        }
    }
}
