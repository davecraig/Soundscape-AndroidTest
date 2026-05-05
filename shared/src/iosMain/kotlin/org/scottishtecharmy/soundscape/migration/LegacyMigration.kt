package org.scottishtecharmy.soundscape.migration

import kotlinx.coroutines.runBlocking
import org.scottishtecharmy.soundscape.database.local.MarkersAndRoutesDatabaseProvider

/**
 * Swift entry point for the legacy data migration. The full description
 * of the JSON contract and import semantics lives on
 * `importLegacyPayload` in commonMain.
 *
 * Wraps the suspend importer in `runBlocking` so the Swift caller can
 * sequence cleanup of the legacy Realm files only after a successful
 * return.
 */
fun runLegacyMigrationImport(payloadJson: String): Int = runBlocking {
    val dao = MarkersAndRoutesDatabaseProvider.getInstance().routeDao()
    importLegacyPayload(payloadJson, dao)
}
