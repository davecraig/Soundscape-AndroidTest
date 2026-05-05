package org.scottishtecharmy.soundscape.utils

import okio.Path.Companion.toPath
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
