package org.scottishtecharmy.soundscape.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.text.format.Formatter
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.poole.geo.pmtiles.Reader
import org.json.JSONObject
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon
import org.scottishtecharmy.soundscape.geojsonparser.moshi.GeoJsonObjectMoshiAdapter
import org.scottishtecharmy.soundscape.utils.StorageUtils.StorageSpace
import java.io.File
import kotlin.text.isEmpty

object StorageUtils {

    const val TAG = "StorageUtils"

    data class StorageSpace(
        val path: String,
        val description: String,
        val isExternal: Boolean,
        val isPrimary: Boolean = false,
        val totalBytes: Long,
        val availableBytes: Long,
        val availableString: String,
        val freeBytes: Long, // For reference, usually availableBytes is what you need
    ) {
        override fun toString(): String {
            return """
                Path: $path
                Type: ${if (isExternal) "External" else "Internal"}${if (isPrimary && isExternal) " (Primary)" else ""}
                Total: $totalBytes
                Available: $availableBytes
                Free: $freeBytes
            """.trimIndent()
        }
    }

    /**
     * Gets free space information for all available external storage volumes
     * using app-specific directories.
     * This is generally the recommended approach for handling external storage.
     */
    fun getExternalStorageSpacesAppSpecific(context: Context): List<StorageSpace> {
        val storageSpaces = mutableListOf<StorageSpace>()
        val externalFilesDirs: Array<File> = context.getExternalFilesDirs(null)
        val primaryExternalStoragePath = try {
            Environment.getExternalStorageDirectory()?.absolutePath
        } catch (_: Exception) { null }


        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        for (dir in externalFilesDirs) {
            @Suppress("SENSELESS_COMPARISON")
            if (dir == null) {
                // dir can be null if a storage device is not mounted, etc.
                continue
            }

            try {
                val statFs = StatFs(dir.path)
                val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
                val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
                val freeBytes = statFs.freeBlocksLong * statFs.blockSizeLong
                val isPrimary = primaryExternalStoragePath != null && dir.absolutePath.startsWith(primaryExternalStoragePath)
                val sv: StorageVolume? = sm.getStorageVolume(dir)
                val description = sv?.getDescription(context) ?: "External Storage"

                storageSpaces.add(
                    StorageSpace(
                        path = dir.absolutePath,
                        description = description,
                        isExternal = true,
                        isPrimary = isPrimary,
                        totalBytes = totalBytes,
                        availableBytes = availableBytes,
                        availableString = Formatter.formatFileSize(context, availableBytes),
                        freeBytes = freeBytes)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting external storage space: ${e.message}")
            }
        }
        return storageSpaces
    }
}

fun getOfflineMapStorage(context: Context): List<StorageSpace> {
    var defaultPath = ""

    // Create a list of available storages
    val storages = mutableListOf<StorageSpace>()

// The DownloadManager can't write to internal storage, so we ignore it. Android devices have
// "external storage" that is emulated on internal storage so it's not an issue.
//    val internalSpace = StorageUtils.getInternalStorageSpace(context)
//    internalSpace?.let {
//        defaultPath = it.path
//        storages.add(internalSpace)
//    }

    val externalAppSpecificSpaces = StorageUtils.getExternalStorageSpacesAppSpecific(context)
    for(storage in externalAppSpecificSpaces) {
        storages.add(storage)
        if (storage.isPrimary)
            defaultPath = storage.path
        else if(defaultPath.isEmpty())
            defaultPath = storage.path
    }

    // Check that the preference is set
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    var path = sharedPreferences.getString(MainActivity.SELECTED_STORAGE_KEY, MainActivity.SELECTED_STORAGE_DEFAULT)
    if((path == null) || path.isEmpty()) {
        // Default path is to the first external storage
        path = defaultPath
        sharedPreferences.edit(commit = true) { putString(MainActivity.SELECTED_STORAGE_KEY, path) }
    }

    // Ensure that the directories exist
    val filesDir = File(path)
    if(filesDir.exists() && filesDir.isDirectory) {
        val downloadsDir = File(path, Environment.DIRECTORY_DOWNLOADS)
        if(!downloadsDir.exists()) {
            downloadsDir.mkdir()
        }
    }

    return storages
}

fun getMetadata(pmtilesPath: String) : Feature? {
    val geojsonFile = File("$pmtilesPath.geojson")
    if (geojsonFile.exists() && geojsonFile.isFile) {
        val adapter = GeoJsonObjectMoshiAdapter()
        val feature = adapter.fromJson(geojsonFile.readText())
        if(feature != null) {
            if(feature.type == "Feature")
                return feature as Feature
        }
    }
    return null
}

/**
 * Synthesize the companion metadata Feature for a side-loaded .pmtiles file.
 *
 * Downloaded extracts get their metadata from the server manifest, but an imported
 * file has none, so we derive what we can from the PMTiles header: the coverage
 * bounds become a Polygon (drawn on the extract details map) and the embedded
 * "name" (if any) becomes the display name, falling back to the filename. The file
 * size is read from disk. The "local-filename" property records the exact on-disk
 * name so [OfflineMapsViewModel.delete] can remove it (and its .geojson companion)
 * without relying on the manifest naming convention.
 */
fun buildImportedExtractFeature(context: Context, pmtilesFile: File): Feature {
    val feature = Feature()
    var name = pmtilesFile.name.removeSuffix(".pmtiles")

    try {
        Reader(pmtilesFile).use { reader ->
            // bounds are returned as [left, bottom, right, top] = [minLon, minLat, maxLon, maxLat]
            val b = reader.bounds
            val ring = arrayListOf(
                LngLatAlt(b[0], b[1]),
                LngLatAlt(b[2], b[1]),
                LngLatAlt(b[2], b[3]),
                LngLatAlt(b[0], b[3]),
                LngLatAlt(b[0], b[1]),
            )
            feature.geometry = Polygon(ring)

            try {
                val meta = reader.metadata
                if (meta.isNotBlank()) {
                    val embeddedName = JSONObject(meta).optString("name", "")
                    if (embeddedName.isNotBlank()) name = embeddedName
                }
            } catch (e: Exception) {
                Log.e(StorageUtils.TAG, "No usable embedded name in ${pmtilesFile.name}: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.e(StorageUtils.TAG, "Could not read pmtiles header for ${pmtilesFile.name}: ${e.message}")
    }

    val size = pmtilesFile.length()
    val properties: HashMap<String, Any?> = hashMapOf()
    properties["name"] = name
    properties["extract-size"] = size.toDouble()
    properties["extract-size-string"] = Formatter.formatFileSize(context, size)
    properties["local-filename"] = pmtilesFile.name
    properties["imported"] = true
    feature.properties = properties

    return feature
}

fun findExtracts(path: String) : FeatureCollection? {
    // Find any extracts that we have downloaded
    val extractsDir = File(path)
    if (extractsDir.exists() && extractsDir.isDirectory) {
        // Find files within the directory and filter for those ending with ".pmtiles". It is
        // possible to have metadata for .pmtiles files that failed to download, so we have to
        // search for the .pmtiles files first and then get the metadata for them.
        val files = extractsDir.listFiles { file -> file.name.endsWith(".pmtiles") }?.toList() ?: emptyList()
        val extractCollection = FeatureCollection()
        for(file in files) {
            val feature = getMetadata(file.path)
            if(feature != null) {
                // Validate the extract so callers can show whether it is working or
                // damaged. A damaged extract (its metadata won't decompress) would crash
                // MapLibre if used, so it is excluded from the live map; here we flag it
                // via the "usable" property instead. This does file I/O, so callers
                // should run findExtracts off the main thread.
                val properties = feature.properties ?: HashMap()
                properties["usable"] = isPmtilesUsable(file.path)
                feature.properties = properties
                extractCollection.addFeature(feature)
            } else
                println("No metadata")
        }
        return extractCollection
    }
    return null
}

/**
 * Check that a .pmtiles extract is safe to hand to MapLibre as a tile source.
 *
 * MapLibre's native PMTilesFileSource decompresses the gzip-compressed JSON metadata
 * section as soon as it opens the source. That decompress call
 * (mbgl::util::decompress, compression.cpp:98) is NOT wrapped in a try/catch in
 * PMTilesFileSource::Impl::getMetadata (pmtiles_file_source.cpp:389), so a corrupt or
 * truncated extract makes it throw std::runtime_error. The exception escapes MapLibre's
 * file-source worker thread, reaches std::terminate and aborts the whole process - a
 * native crash we cannot catch from Kotlin.
 *
 * To avoid that we open the file with the same Reader we already depend on and force the
 * identical metadata decompression here, where the exception IS catchable. If it throws,
 * the extract is corrupt and must not be used.
 */
fun isPmtilesUsable(path: String): Boolean {
    return try {
        ch.poole.geo.pmtiles.Reader(File(path)).use { reader ->
            // Forces decompression of the gzip metadata - the exact operation that
            // crashes MapLibre natively when the file is corrupt or truncated.
            reader.metadata
        }
        true
    } catch (e: Exception) {
        Log.e(StorageUtils.TAG, "Rejecting unusable pmtiles extract $path: ${e.message}")
        false
    }
}

fun findExtractPaths(path: String) : List<String> {
    // Find any extracts that we have downloaded
    val extractsDir = File(path)
    if (extractsDir.exists() && extractsDir.isDirectory) {
        // Find files within the directory and filter for those ending with ".pmtiles". It is
        // possible to have metadata for .pmtiles files that failed to download, so we have to
        // search for the .pmtiles files first and then get the metadata for them.
        val fileList = extractsDir.listFiles { file -> file.name.endsWith(".pmtiles") }?.toList()
        if(fileList != null) {
            val returnList = mutableListOf<String>()
            for (file in fileList) {
                returnList += file.path
            }
            return returnList
        }
    }
    return emptyList()
}
