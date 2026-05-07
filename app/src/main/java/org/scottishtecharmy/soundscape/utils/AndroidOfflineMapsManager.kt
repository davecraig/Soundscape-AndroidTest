package org.scottishtecharmy.soundscape.utils

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import androidx.preference.PreferenceManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.BuildConfig
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.geoengine.utils.FeatureTree
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.GeoMoshi
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.network.DownloadStateCommon
import java.io.File
import java.io.FileOutputStream

private fun DownloadState.toCommon(): DownloadStateCommon = when (this) {
    is DownloadState.Idle -> DownloadStateCommon.Idle
    is DownloadState.Caching -> DownloadStateCommon.Caching
    is DownloadState.Downloading -> DownloadStateCommon.Downloading(progress)
    is DownloadState.Success -> DownloadStateCommon.Success
    is DownloadState.Canceled -> DownloadStateCommon.Canceled
    is DownloadState.Error -> DownloadStateCommon.Error(message)
}

/**
 * Session-scope facade over the offline-map manifest fetcher, file system index,
 * and OfflineDownloader. Implements the surface area the shared NavGraph
 * expects on iOS via OfflineMapManager: full list of manifest extracts,
 * downloaded extracts as a FeatureCollection, current download state, and
 * refresh / containing-query / start / cancel / delete operations.
 */
class AndroidOfflineMapsManager(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _availableExtracts = MutableStateFlow<List<Feature>>(emptyList())
    val availableExtracts: StateFlow<List<Feature>> = _availableExtracts.asStateFlow()

    private val _downloadedExtractsFc = MutableStateFlow(FeatureCollection())
    val downloadedExtractsFc: StateFlow<FeatureCollection> = _downloadedExtractsFc.asStateFlow()

    private val downloader = OfflineDownloader()
    val downloadState: StateFlow<DownloadStateCommon> = downloader.downloadState
        .map { it.toCommon() }
        .stateIn(scope, SharingStarted.Eagerly, DownloadStateCommon.Idle)

    private var manifestTree: FeatureTree? = null

    init {
        scope.launch {
            downloader.downloadState.collect { state ->
                if (state == DownloadState.Success) refreshDownloaded()
            }
        }
    }

    private fun extractsDir(): File {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val path = sharedPreferences.getString(
            MainActivity.SELECTED_STORAGE_KEY,
            MainActivity.SELECTED_STORAGE_DEFAULT,
        )!!
        return File(path, Environment.DIRECTORY_DOWNLOADS)
    }

    fun refresh() {
        scope.launch {
            val fc = downloadAndParseManifest(appContext)
            if (fc != null) {
                manifestTree = FeatureTree(fc)
                fc.features.forEach { feature -> annotateExtractSize(feature) }
                _availableExtracts.value = fc.features.toList()
            } else {
                Log.w(TAG, "Manifest fetch failed; keeping previous availableExtracts")
            }
            refreshDownloaded()
        }
    }

    private fun refreshDownloaded() {
        val dir = extractsDir()
        _downloadedExtractsFc.value = findExtracts(dir.path) ?: FeatureCollection()
    }

    fun getExtractsContaining(location: LngLatAlt): List<Feature> {
        val tree = manifestTree ?: return emptyList()
        return tree.getContainingPolygons(location).features
    }

    fun startDownload(name: String, feature: Feature) {
        val filename = feature.properties?.get("filename") as? String ?: return
        val localFilename = filename.substringAfter("-").substringAfter("-")
        val path = "${extractsDir().path}/$localFilename"
        try {
            val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
            val adapter = moshi.adapter(Feature::class.java)
            FileOutputStream("$path.geojson").use { it.write(adapter.toJson(feature).toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write extract metadata", e)
        }
        val extractSize = (feature.properties?.get("extract-size") as? Number)?.toDouble()
        downloader.startDownload(
            "${BuildConfig.EXTRACT_PROVIDER_URL}$filename",
            path,
            extractSize,
        )
    }

    fun deleteExtractByFeature(feature: Feature) {
        val filename = feature.properties?.get("filename") as? String ?: return
        val localFilename = filename.substringAfter("-").substringAfter("-")
        val dir = extractsDir()
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles { f -> f.name.startsWith(localFilename) }?.forEach { it.delete() }
            refreshDownloaded()
        }
    }

    fun cancelDownload() {
        downloader.cancelDownload()
    }

    private fun annotateExtractSize(feature: Feature) {
        val size = (feature.properties?.get("extract-size") as? Number)?.toLong() ?: return
        val props = feature.properties as? HashMap<String, Any?> ?: return
        props["extract-size-string"] = Formatter.formatFileSize(appContext, size)
        feature.properties = props
    }

    companion object {
        private const val TAG = "AndroidOfflineMaps"
    }
}
