package org.scottishtecharmy.soundscape

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.scottishtecharmy.soundscape.database.local.model.MarkerEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteEntity
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.screens.home.HomeState
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.screens.home.home.BottomButtonFunctions
import org.scottishtecharmy.soundscape.screens.home.home.RouteFunctions
import org.scottishtecharmy.soundscape.screens.home.home.SearchFunctions
import org.scottishtecharmy.soundscape.screens.home.home.SharedHomeScreen
import org.scottishtecharmy.soundscape.screens.home.home.StreetPreviewFunctions
import org.scottishtecharmy.soundscape.screens.markers_routes.screens.routedetailsscreen.SharedRouteDetailsScreen
import org.scottishtecharmy.soundscape.services.RoutePlayerState
import org.scottishtecharmy.soundscape.ui.theme.SoundscapeTheme

// This is very helpful:
// https://developer.android.com/develop/ui/compose/testing/testing-cheatsheet
class DocumentationScreens {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val location = LngLatAlt(
        -4.3178027,
        55.9410791
    )

    private val routeToShops = RouteWithMarkers(
        RouteEntity(
            name = "Route to shops",
            description = "",
            routeId = 1L
        ),
        listOf(
            MarkerEntity(
                name = "Craigton Road",
                longitude = -4.3239319,
                latitude = 55.9446396,
                markerId = 1L
            ),
            MarkerEntity(
                name = "Clober Road",
                longitude = -4.3210534,
                latitude = 55.9417227,
                markerId = 2L
            ),
            MarkerEntity(
                name = "Douglas Street",
                longitude = -4.3194968,
                latitude = 55.9406974,
                markerId = 3L
            ),
            MarkerEntity(
                name = "Underpass to shops",
                longitude = -4.3175668,
                latitude = 55.9399973,
                markerId = 4L
            )
        )
    )

    private fun runScreenTest(
        screenshotFileName: String,
        testCode: @Composable () -> Unit
    ) {

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val themeStateFlow = MutableStateFlow(ThemeState())
        composeTestRule.setContent {
            SoundscapeTheme(themeStateFlow) {
                testCode()
            }
        }

        // Delay to allow the maps to load
        Thread.sleep(5000)

        // Capture screenshot of the root composable
        ScreenshotUtils.captureAndSaveScreenshot(
            context = targetContext, // Use target context
            filename = screenshotFileName
        )
    }

    @Test
    fun homeScreen() {

        runScreenTest("homeScreen") {
            SharedHomeScreen(
                state = HomeState(
                    location = location,
                    heading = 45.0F,
                ),
                onNavigate = {},
                onSelectLocation = {},
                preferencesProvider = null,
                bottomButtonFunctions = BottomButtonFunctions(),
                getCurrentLocationDescription = {
                    LocationDescription(
                        name = "Milngavie",
                        location = location,
                    )
                },
                searchFunctions = SearchFunctions(),
                rateSoundscape = { },
                contactSupport = { },
                shareRecording = { },
                toggleTutorial = {},
                tutorialRunning = false,
                recordingEnabled = false,
                routeFunctions = RouteFunctions(),
                streetPreviewFunctions = StreetPreviewFunctions(),
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                goToAppSettings = { },
                permissionsRequired = false,
                onSleep = {},
            )
        }
    }

    @Test
    fun homeScreenWithRoute() {
        val routePlayerState = RoutePlayerState(
            routeData = routeToShops,
            currentWaypoint = 0
        )

        runScreenTest("homeScreenWithRoute") {
            SharedHomeScreen(
                state = HomeState(
                    location = location,
                    heading = 45.0F,
                    currentRouteData = routePlayerState,
                ),
                onNavigate = {},
                onSelectLocation = {},
                preferencesProvider = null,
                bottomButtonFunctions = BottomButtonFunctions(),
                getCurrentLocationDescription = {
                    LocationDescription(
                        name = "Milngavie",
                        location = location,
                    )
                },
                searchFunctions = SearchFunctions(),
                rateSoundscape = { },
                contactSupport = {},
                shareRecording = {},
                toggleTutorial = {},
                tutorialRunning = false,
                recordingEnabled = false,
                routeFunctions = RouteFunctions(),
                streetPreviewFunctions = StreetPreviewFunctions(),
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                goToAppSettings = { },
                permissionsRequired = false,
                onSleep = {},
            )
        }
    }

    @Test
    fun routeDetailsScreen() {
        val waypoints = routeToShops.markers.map { marker ->
            LocationDescription(
                name = marker.name,
                location = LngLatAlt(marker.longitude, marker.latitude),
            )
        }

        runScreenTest("routeDetails") {
            SharedRouteDetailsScreen(
                routeName = routeToShops.route.name,
                routeDescription = routeToShops.route.description,
                waypoints = waypoints,
                isRoutePlaying = false,
                userLocation = location,
                heading = 45.0F,
                onNavigateUp = { },
                onStartRoute = { },
                onStartRouteInReverse = { },
                onStopRoute = { },
                onEditRoute = { },
                onShareRoute = { },
            )
        }
    }
}
