package org.scottishtecharmy.soundscape.utils

import okio.Path.Companion.toPath
import org.scottishtecharmy.soundscape.geoengine.utils.pmtiles.PmTilesReader
import org.scottishtecharmy.soundscape.platform.systemFileSystem

fun findExtractPaths(path: String): List<String> {
    val dir = path.toPath()
    return try {
        systemFileSystem.list(dir)
            .filter { it.name.endsWith(".pmtiles") }
            .map { it.toString() }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Check that a .pmtiles extract is safe to hand to MapLibre as a tile source.
 *
 * MapLibre's native PMTilesFileSource decompresses the gzip-compressed JSON metadata section as
 * soon as it opens a source. That decompress is not wrapped in a try/catch natively, so a corrupt
 * or truncated extract throws, escapes MapLibre's worker thread, hits std::terminate and aborts the
 * whole process - a native crash we cannot catch from Kotlin.
 *
 * To avoid that we open the file with [PmTilesReader] (which validates the header and root
 * directory) and force the same metadata decompression via [PmTilesReader.readMetadata], where the
 * exception IS catchable. If anything throws, the extract is corrupt and must not be used.
 *
 * This does file I/O, so callers should run it off the main thread.
 */
fun isPmtilesUsable(path: String): Boolean {
    return try {
        val reader = PmTilesReader(path.toPath())
        try {
            reader.readMetadata()
        } finally {
            reader.close()
        }
        true
    } catch (_: Exception) {
        false
    }
}
