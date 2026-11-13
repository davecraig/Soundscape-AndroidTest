package org.scottishtecharmy.soundscape.geoengine.mvttranslation

/**
 * A walking or cycling route relation that a way belongs to - the West Highland Way, a waymarked
 * forest trail, a National Cycle Network route. planetiler-openmaptiles writes every one of them
 * onto the transportation ways as route_1_*, route_2_* ... (see its Transportation layer), best
 * known first: international, national, regional then local networks, walking before cycling.
 *
 * Mappers have moved route names off the ways and onto these relations, which is right for OSM but
 * left the paths themselves unnamed. All of a way's routes are kept, not just the one that names it,
 * because a track can carry five local trails at once and the user will be able to pick one to
 * follow.
 *
 * [type] is the relation's route tag (hiking, foot, walking, bicycle or mtb), [role] the way's role
 * in it (forward/backward on a one-way route), and [rootId]/[rootName] the whole route when this
 * relation is only a section of it - "West Highland Way" for "West Highland Way (Milngavie to
 * Drymen part 1)".
 */
data class OsmRoute(
    val id: Long,
    val type: String,
    val network: String? = null,
    val ref: String? = null,
    val name: String? = null,
    val colour: String? = null,
    val osmcSymbol: String? = null,
    val role: String? = null,
    val rootId: Long? = null,
    val rootName: String? = null,
) {
    /** What the route is called: the whole route rather than the section, then its ref. */
    val displayName: String?
        get() = rootName ?: name ?: ref

    val isWalking: Boolean
        get() = type in WALKING_ROUTE_TYPES

    /** International, national or regional - a route that is signposted as a route in its own right. */
    val isMajor: Boolean
        get() = network in MAJOR_NETWORKS

    companion object {
        private val WALKING_ROUTE_TYPES = setOf("hiking", "foot", "walking")
        private val MAJOR_NETWORKS = setOf("iwn", "nwn", "rwn", "icn", "ncn", "rcn")
    }
}

/**
 * Collects the route_N_* tags of one tile feature as they are read, and builds the [OsmRoute]s
 * from them once the feature's tags are done.
 */
class OsmRouteTags {
    private val routes = HashMap<Int, HashMap<String, Any?>>()

    fun clear() = routes.clear()

    /** Takes [key] if it is one of the route_N_* tags, returning whether it was. */
    fun offer(key: String, value: Any?): Boolean {
        if (!key.startsWith("route_")) return false
        val second = key.indexOf('_', 6)
        if (second < 0) return false
        val index = key.substring(6, second).toIntOrNull() ?: return false
        routes.getOrPut(index) { HashMap() }[key.substring(second + 1)] = value
        return true
    }

    fun build(): List<OsmRoute> {
        if (routes.isEmpty()) return emptyList()
        // In route_N order, which is the order the tiles rank them in
        return routes.keys.sorted().map { routes.getValue(it) }.mapNotNull { tags ->
            val id = tags["id"].asLong() ?: return@mapNotNull null
            OsmRoute(
                id = id,
                type = tags["type"]?.toString() ?: return@mapNotNull null,
                network = tags["network"]?.toString(),
                ref = tags["ref"]?.toString(),
                name = tags["name"]?.toString(),
                colour = tags["colour"]?.toString(),
                osmcSymbol = tags["osmc_symbol"]?.toString(),
                role = tags["role"]?.toString(),
                rootId = tags["root_id"].asLong(),
                rootName = tags["root_name"]?.toString(),
            )
        }
    }

    private fun Any?.asLong(): Long? = (this as? Number)?.toLong() ?: this?.toString()?.toLongOrNull()
}

/**
 * The name an otherwise unnamed way takes from its [routes], or null if none of them names it
 * clearly enough.
 *
 * Walking routes first; cycling routes only for a cycleway, as that is how a cycleway is known and
 * Soundscape is used on foot. Then an international, national or regional route - the best one,
 * which the tiles list first. A local route only names the way when it is the only one: five
 * waymarked trails share one forest track near Aberfoyle, and calling it after any one of them
 * would tell the user it is that trail and not the other four.
 */
fun nameFromRoutes(routes: List<OsmRoute>, isCycleway: Boolean): String? {
    if (routes.isEmpty()) return null
    val candidates = routes.filter { it.isWalking }.ifEmpty {
        if (isCycleway) routes else emptyList()
    }
    candidates.firstOrNull { it.isMajor }?.displayName?.let { return it }
    return candidates.singleOrNull()?.displayName
}
