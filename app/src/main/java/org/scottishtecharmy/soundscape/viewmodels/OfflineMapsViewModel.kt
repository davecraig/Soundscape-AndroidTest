package org.scottishtecharmy.soundscape.viewmodels

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.scottishtecharmy.soundscape.BuildConfig
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.SoundscapeServiceConnection
import org.scottishtecharmy.soundscape.geoengine.utils.FeatureTree
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.moshi.GeoJsonObjectMoshiAdapter
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.utils.DownloadState
import org.scottishtecharmy.soundscape.utils.OfflineDownloader
import org.scottishtecharmy.soundscape.utils.StorageUtils
import org.scottishtecharmy.soundscape.utils.buildImportedExtractFeature
import org.scottishtecharmy.soundscape.utils.downloadAndParseManifest
import org.scottishtecharmy.soundscape.utils.findExtracts
import org.scottishtecharmy.soundscape.utils.getOfflineMapStorage
import org.scottishtecharmy.soundscape.utils.isPmtilesUsable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed class NearbyExtractsState {
    object Loading : NearbyExtractsState()
    data class Loaded(val nearbyExtracts: FeatureCollection) : NearbyExtractsState()
    object Error : NearbyExtractsState()
}

sealed class ImportState {
    object Idle : ImportState()
    // progress is in tenths of a percent (0..1000) to match DownloadState, or -1 when the
    // source size is unknown (indeterminate progress).
    data class Copying(val progress: Int) : ImportState()
    object Success : ImportState()
    // invalid == true means the file copied fine but isn't a usable .pmtiles extract.
    data class Error(val invalid: Boolean) : ImportState()
}

data class OfflineMapsUiState(
    val downloadingExtractName: String = "",

    // Extracts in manifest to choose from
    val nearbyExtractsState: NearbyExtractsState = NearbyExtractsState.Loading,

    // Offline extracts in storage
    val downloadedExtracts: FeatureCollection? = null,

    // Storage status
    val currentPath: String = "",
    val storages: List<StorageUtils.StorageSpace> = emptyList(),

    // Live user GPS location
    val userLocation: LngLatAlt? = null,

    // Live user heading (degrees) for the map symbol rotation
    val userHeading: Float = 0.0f,

    // Search/marker location used to find nearby extracts
    val markerLocation: LngLatAlt? = null,

    // Display name of the extract currently being imported (side-loaded)
    val importingExtractName: String = ""
)

@HiltViewModel(assistedFactory = OfflineMapsViewModel.Factory::class)
class OfflineMapsViewModel @AssistedInject constructor(
    @param:ApplicationContext val appContext: Context,
    private val soundscapeServiceConnection: SoundscapeServiceConnection,
    @Assisted private val locationDescription: LocationDescription
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineMapsUiState())
    val uiState: StateFlow<OfflineMapsUiState> = _uiState
    lateinit var offlineDownloader: OfflineDownloader
    lateinit var downloadState: StateFlow<DownloadState>

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState
    private var importJob: Job? = null

    // Add this factory interface inside the ViewModel class
    @AssistedFactory
    interface Factory {
        fun create(locationDescription: LocationDescription): OfflineMapsViewModel
    }

    init {
        viewModelScope.launch {
            // Create downloader to handle getting any offline maps
            offlineDownloader = OfflineDownloader()
            downloadState = offlineDownloader.downloadState

            val storages = getOfflineMapStorage(appContext)

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
            var path = sharedPreferences.getString(
                MainActivity.SELECTED_STORAGE_KEY,
                MainActivity.SELECTED_STORAGE_DEFAULT
            )!!
            val extractCollection = withContext(Dispatchers.IO) {
                findExtracts(File(path, Environment.DIRECTORY_DOWNLOADS).path)
            }
            _uiState.value = _uiState.value.copy(
                downloadedExtracts = extractCollection,
                storages = storages,
                currentPath = path,
                markerLocation = locationDescription.location
            )

            val fc = downloadAndParseManifest(appContext)
            if (fc != null) {
                val tree = FeatureTree(fc)

                val location = locationDescription.location
                println("Location $location")
                // Containing polygons gives offline maps that include the current location
                val extracts = tree.getContainingPolygons(location)

                println("Extracts ${extracts.features.size}")
                for (extract in extracts.features) {
                    val size = extract.properties?.get("extract-size") as Double
                    val properties: HashMap<String, Any?> = extract.properties!!
                    properties["extract-size-string"] =
                        Formatter.formatFileSize(appContext, size.toLong())
                    extract.properties = properties

                    Log.d(TAG, "extract: ${extract.properties}")
                }
                _uiState.value = _uiState.value.copy(
                    nearbyExtractsState = NearbyExtractsState.Loaded(extracts)
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    nearbyExtractsState = NearbyExtractsState.Error
                )
            }
        }

        viewModelScope.launch {
            soundscapeServiceConnection.getLocationFlow()?.collect { location ->
                if (location != null) {
                    _uiState.value = _uiState.value.copy(
                        userLocation = LngLatAlt(location.longitude, location.latitude)
                    )
                }
            }
        }
        viewModelScope.launch {
            soundscapeServiceConnection.getOrientationFlow()?.collect { orientation ->
                if (orientation != null) {
                    _uiState.value = _uiState.value.copy(
                        userHeading = orientation.headingDegrees
                    )
                }
            }
        }
    }

    private fun translateToLocalFilenameFrom(filename: String): String {
        return filename.substringAfter("-").substringAfter("-")
    }

    fun delete(feature: Feature) {
        // Imported extracts store their exact on-disk name in "local-filename"; downloaded
        // extracts only have the manifest "filename", which needs translating to the local
        // name. Prefer the former so arbitrary imported filenames (which may contain "-")
        // are deleted correctly.
        val localFilename = (feature.properties?.get("local-filename") as? String)
            ?: (feature.properties?.get("filename") as? String)?.let { translateToLocalFilenameFrom(it) }
        if (localFilename != null) {
            val extractsDir = File(_uiState.value.currentPath, Environment.DIRECTORY_DOWNLOADS)
            if (extractsDir.exists() && extractsDir.isDirectory) {
                val files = extractsDir.listFiles { file ->
                    file.name.startsWith(localFilename)
                }?.toList() ?: emptyList()

                // Delete whatever we find
                for (file in files)
                    file.delete()

                refreshExtracts()
            }
        }
    }

    fun download(name: String, feature: Feature) {
        val filename = feature.properties?.get("filename")
        if (filename != null) {
            val localFilename = translateToLocalFilenameFrom(filename as String)
            val path =
                _uiState.value.currentPath + "/" + Environment.DIRECTORY_DOWNLOADS + "/" + localFilename

            // Write out the feature metadata to a file
            val adapter = GeoJsonObjectMoshiAdapter()
            val metadataOutputFile = FileOutputStream("$path.geojson")
            metadataOutputFile.write(adapter.toJson(feature).toByteArray())
            metadataOutputFile.close()

            val extractSize = feature.properties?.get("extract-size") as Double?
            val fileUrl = "${BuildConfig.EXTRACT_PROVIDER_URL}$filename"
            offlineDownloader.startDownload(
                fileUrl,
                path,
                extractSize
            )
            _uiState.value = _uiState.value.copy(
                downloadingExtractName = name
            )
        }
    }

    fun cancelDownload() {
        offlineDownloader.cancelDownload()
    }

    /**
     * Side-load a .pmtiles extract chosen from the system file picker.
     *
     * The picker hands back a content:// URI, but both the MapLibre pmtiles:// source and
     * the geo engine's Reader need a real file path, so we stream the file into the selected
     * storage's Downloads directory (where findExtractPaths/findExtracts look for extracts).
     * The file is validated with isPmtilesUsable before it is finalised so a corrupt file can
     * never reach (and crash) MapLibre, and a synthesized .geojson metadata companion is
     * written so it appears in the list and can be deleted.
     */
    fun importExtract(uri: Uri) {
        // Don't start a second import on top of a running one.
        if (importJob?.isActive == true) return

        importJob = viewModelScope.launch {
            _importState.value = ImportState.Copying(-1)
            var tempFile: File? = null
            try {
                val (displayName, totalSize) = withContext(Dispatchers.IO) { queryNameAndSize(uri) }
                val filename = safePmtilesFilename(displayName)
                _uiState.value = _uiState.value.copy(
                    importingExtractName = filename.removeSuffix(".pmtiles")
                )

                val downloadsDir = File(_uiState.value.currentPath, Environment.DIRECTORY_DOWNLOADS)

                val finalFile = withContext(Dispatchers.IO) {
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val destination = uniqueExtractFile(downloadsDir, filename)
                    val temp = File(downloadsDir, "${destination.name}.importing")
                    tempFile = temp

                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open $uri")
                    input.use { ins ->
                        FileOutputStream(temp).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            var lastProgress = -1
                            while (true) {
                                ensureActive()
                                val read = ins.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                copied += read
                                if (totalSize > 0) {
                                    val progress = (copied * 1000 / totalSize).toInt()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        _importState.value = ImportState.Copying(progress)
                                    }
                                }
                            }
                        }
                    }

                    // Reject anything that isn't a usable extract before it becomes visible.
                    if (!isPmtilesUsable(temp.path)) {
                        temp.delete()
                        return@withContext null
                    }

                    if (!temp.renameTo(destination)) {
                        temp.delete()
                        throw IOException("Could not finalise ${destination.name}")
                    }

                    // Write the synthesized metadata companion, mirroring download().
                    val feature = buildImportedExtractFeature(appContext, destination)
                    val adapter = GeoJsonObjectMoshiAdapter()
                    FileOutputStream("${destination.path}.geojson").use {
                        it.write(adapter.toJson(feature).toByteArray())
                    }
                    destination
                }

                if (finalFile == null) {
                    _importState.value = ImportState.Error(invalid = true)
                } else {
                    refreshExtracts()
                    _importState.value = ImportState.Success
                }
            } catch (ce: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { tempFile?.delete() }
                _importState.value = ImportState.Idle
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Import failed: ${e.message}")
                withContext(Dispatchers.IO) { tempFile?.delete() }
                _importState.value = ImportState.Error(invalid = false)
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    /** Reads the display name and size of a picked document (either may be unavailable). */
    private fun queryNameAndSize(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = -1L
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return Pair(name, size)
    }

    /** Turn a picked document's display name into a safe single ".pmtiles" filename. */
    private fun safePmtilesFilename(displayName: String?): String {
        var base = (displayName ?: "imported").substringAfterLast('/').substringAfterLast('\\')
        if (base.endsWith(".pmtiles", ignoreCase = true)) {
            base = base.dropLast(".pmtiles".length)
        }
        if (base.isBlank()) base = "imported"
        return "$base.pmtiles"
    }

    /** Pick a non-clashing destination so an import can't overwrite an existing extract. */
    private fun uniqueExtractFile(dir: File, filename: String): File {
        var candidate = File(dir, filename)
        if (!candidate.exists()) return candidate
        val base = filename.removeSuffix(".pmtiles")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index).pmtiles")
            index++
        }
        return candidate
    }

    fun refreshExtracts() {
        // Update the UI to reflect the deletions. findExtracts validates each extract
        // (file I/O), so run it off the main thread.
        viewModelScope.launch {
            val extractsDir = File(_uiState.value.currentPath, Environment.DIRECTORY_DOWNLOADS)
            val extractCollection = withContext(Dispatchers.IO) {
                findExtracts(extractsDir.path)
            }
            _uiState.value = _uiState.value.copy(
                downloadedExtracts = extractCollection
            )
        }
    }

    companion object {
        private const val TAG = "OfflineMapsViewModel"
    }
}