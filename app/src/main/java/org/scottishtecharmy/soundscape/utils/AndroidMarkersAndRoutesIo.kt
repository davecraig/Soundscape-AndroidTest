package org.scottishtecharmy.soundscape.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android implementation of [MarkersAndRoutesIo]. Builds a zip on disk in the
 * app's `export/` folder and shares it via FileProvider; the file picker is
 * registered against the Activity's [ActivityResultRegistry] so it survives
 * configuration changes.
 */
class AndroidMarkersAndRoutesIo(
    private val appContext: Context,
) : MarkersAndRoutesIo {

    @Volatile
    private var pickContinuation: CompletableDeferred<List<NamedGpx>?>? = null
    private var picker: ActivityResultLauncher<String>? = null

    /** Wire the import file-picker launcher to the host activity's registry. */
    fun attach(activity: ComponentActivity) {
        picker = activity.activityResultRegistry.register(
            "soundscape-markers-and-routes-pick",
            ActivityResultContracts.GetContent(),
        ) { uri ->
            val cont = pickContinuation
            pickContinuation = null
            if (cont == null) return@register
            if (uri == null) {
                cont.complete(null)
                return@register
            }
            try {
                val files = mutableListOf<NamedGpx>()
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".gpx")) {
                                files += NamedGpx(entry.name, zip.readBytes().decodeToString())
                            }
                            entry = zip.nextEntry
                        }
                    }
                }
                cont.complete(files)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read picked zip", e)
                cont.complete(null)
            }
        }
    }

    override suspend fun exportGpxZip(
        files: List<NamedGpx>,
        suggestedFilename: String,
        shareTitle: String,
    ) {
        try {
            val exportDir = File(appContext.filesDir, "export").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val zipFile = File(exportDir, "$suggestedFilename-$timestamp.zip")
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zip ->
                    for (file in files) {
                        zip.putNextEntry(ZipEntry(file.filename))
                        zip.write(file.content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                zipFile,
            )
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/zip"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, shareTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create or share zip file", e)
        }
    }

    override suspend fun pickGpxZip(): List<NamedGpx>? {
        val launcher = picker ?: return null
        // Cancel any earlier pending request so we don't leak deferreds when
        // the user retriggers an import while one is already in-flight.
        pickContinuation?.complete(null)
        val deferred = CompletableDeferred<List<NamedGpx>?>()
        pickContinuation = deferred
        launcher.launch("application/zip")
        return deferred.await()
    }

    companion object {
        private const val TAG = "AndroidMarkersAndRoutesIo"
    }
}
