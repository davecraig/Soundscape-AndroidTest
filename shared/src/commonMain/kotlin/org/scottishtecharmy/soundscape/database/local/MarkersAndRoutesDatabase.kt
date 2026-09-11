package org.scottishtecharmy.soundscape.database.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.scottishtecharmy.soundscape.database.local.dao.RouteDao
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteMarkerCrossRef

/**
 * The first migration this database has ever had, so it is also what establishes that it has any:
 * both platform database builders have to pass it to `addMigrations`, or an existing install throws
 * on open instead of upgrading.
 *
 * Both columns are additive and nullable, so every row already in the table is left exactly as it
 * was - a marker the user saved by hand has a null `source`, which is what marks it as theirs.
 * See MarkerEntity for what each one is for.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE markers ADD COLUMN source TEXT")
        connection.execSQL("ALTER TABLE markers ADD COLUMN reverseDirection TEXT")
    }
}

@Database(
    entities = [RouteEntity::class, MarkerEntity::class, RouteMarkerCrossRef::class],
    version = 2,
    exportSchema = false
)
@ConstructedBy(MarkersAndRoutesDatabaseConstructor::class)
abstract class MarkersAndRoutesDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
}

// Room KMP generates the implementation via KSP
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MarkersAndRoutesDatabaseConstructor :
    RoomDatabaseConstructor<MarkersAndRoutesDatabase> {
    override fun initialize(): MarkersAndRoutesDatabase
}
