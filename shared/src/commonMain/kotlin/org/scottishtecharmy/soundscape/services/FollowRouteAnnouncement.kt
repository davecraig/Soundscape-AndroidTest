package org.scottishtecharmy.soundscape.services

import org.jetbrains.compose.resources.getString
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.FollowedRoute
import org.scottishtecharmy.soundscape.resources.Res
import org.scottishtecharmy.soundscape.resources.route_follow_started
import org.scottishtecharmy.soundscape.resources.route_follow_stopped

/**
 * What to say when the route being followed changes from [previous] to [route] - "Following Loch
 * Ard Sculpture Trail", "Stopped following Loch Ard Sculpture Trail" - shared by the Android and
 * iOS services' followRoute.
 */
suspend fun followRouteAnnouncement(previous: FollowedRoute?, route: FollowedRoute?): String? = when {
    route != null -> getString(Res.string.route_follow_started, route.name)
    previous != null -> getString(Res.string.route_follow_stopped, previous.name)
    else -> null
}
