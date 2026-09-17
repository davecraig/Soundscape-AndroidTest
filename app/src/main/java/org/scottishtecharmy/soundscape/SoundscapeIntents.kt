package org.scottishtecharmy.soundscape

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.intents.IncomingIntent
import org.scottishtecharmy.soundscape.intents.IntentEventBus
import org.scottishtecharmy.soundscape.intents.IntentParser
import org.scottishtecharmy.soundscape.locationprovider.GpxDrivenProvider
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.scottishtecharmy.soundscape.utils.AnalyticsProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.math.round

class SoundscapeIntents(
    private val bus: IntentEventBus,
) {
    private lateinit var geocoder: Geocoder

    private fun useGeocoderToGetAddress(
        location: String,
        context: Context,
        fallbackLat: Double? = null,
        fallbackLon: Double? = null,
    ) {
        geocoder = Geocoder(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val geocodeListener =
                Geocoder.GeocodeListener { addresses ->
                    Log.d(TAG, "getFromLocationName $location has ${addresses.size} result")
                    val address = addresses.firstOrNull()
                    if (address != null) {
                        publishGeocoded(
                            address.getAddressLine(0),
                            address.longitude,
                            address.latitude
                        )
                    } else if (fallbackLat != null && fallbackLon != null) {
                        publishLatLon(fallbackLat, fallbackLon)
                    }
                }
            Log.d(TAG, "Call getFromLocationName on $location")
            geocoder.getFromLocationName(location, 1, geocodeListener)
        } else {
            Log.d(TAG, "Pre-API33: $location")

            @Suppress("DEPRECATION")
            val address = geocoder.getFromLocationName(location, 1)?.firstOrNull()
            if (address != null) {
                publishGeocoded(address.getAddressLine(0), address.longitude, address.latitude)
            } else if (fallbackLat != null && fallbackLon != null) {
                publishLatLon(fallbackLat, fallbackLon)
            }
        }
    }

    private fun publishGeocoded(name: String, longitude: Double, latitude: Double) {
        bus.publish(
            IncomingIntent.OpenLocation(
                LocationDescription(name = name, location = LngLatAlt(longitude, latitude))
            )
        )
    }

    private fun publishLatLon(latitude: Double, longitude: Double) {
        bus.publish(
            IncomingIntent.OpenLocation(
                LocationDescription(
                    name = "$latitude,$longitude",
                    location = LngLatAlt(longitude, latitude),
                )
            )
        )
    }

    private fun getRedirectUrl(
        url: String,
        context: Context?,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            getRedirectUrlSync(url, context)
        }
    }

    fun getRedirectUrlSync(
        url: String,
        context: Context?,
    ): String {
        val urlTmp: URL?
        val connection: HttpURLConnection?

        try {
            Log.d(TAG, "Open URL $url")
            urlTmp = URL(url)

            Log.d(TAG, "Open connection")
            connection = urlTmp.openConnection() as HttpURLConnection
            Log.d(TAG, "Response ${connection.responseCode}")

            val redUrl = connection.url.toString()
            connection.disconnect()

            if (connection.responseCode != 200)
                return ""

            Log.d(TAG, "Maps URL: $redUrl")
            val decodedUrl = URLDecoder.decode(redUrl, "UTF-8")
            Log.d(TAG, "Decoded maps URL: $decodedUrl")

            val placeName =
                decodedUrl.substringAfter("maps/place/").substringBefore("/")
            Log.d(TAG, "Place name: $placeName")
            if (context == null)
                return placeName

            useGeocoderToGetAddress(placeName, context)
            return placeName

        } catch (e: IOException) {
            e.printStackTrace()
            return ""
        }
    }

    /** Handles inbound intents and publishes results to [IntentEventBus]:
     *
     * - geo:/soundscape: URLs with lat,lon → geocoded then OpenLocation (best-effort)
     * - soundscape://feature/... → OpenFeature
     * - soundscape://route/{name|stop} → StartRouteByName / StopRoute
     * - https://links.soundscape.scottishtecharmy.org/v1/sharemarker?... → OpenLocation
     * - text/plain ACTION_SEND containing maps.app.goo.gl → follow + geocode
     * - content:// JSON or GPX route file → ImportRoute
     */
    fun parse(
        intent: Intent,
        mainActivity: MainActivity,
    ) {
        when {
            intent.action == ACTION_REPLAY_GPX -> replayGpx(intent, mainActivity)

            intent.action == Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { plainText ->
                        Log.d(TAG, "Intent text: $plainText")
                        if (plainText.contains("maps.app.goo.gl")) {
                            AnalyticsProvider.getInstance().logEvent("intentGoogleMapShare", null)
                            try {
                                getRedirectUrl(plainText, mainActivity)
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception: $e")
                            }
                        }
                    }
                }
            }

            else -> {
                val uri = intent.data ?: return

                // Try the shared parser first for geo:, soundscape:, and our share marker https URL.
                val parsed = IntentParser.parseUrl(uri.toString())
                if (parsed != null) {
                    handleParsed(parsed, mainActivity)
                    return
                }

                // Fall through: file import via content://
                if (Intent.ACTION_VIEW == intent.action || Intent.ACTION_MAIN == intent.action) {
                    val data = intent.data
                    if (data != null && "content" == data.scheme) {
                        importContentUri(intent, mainActivity)
                    }
                }
            }
        }
    }

    private fun handleParsed(intent: IncomingIntent, mainActivity: MainActivity) {
        when (intent) {
            is IncomingIntent.OpenLatLon -> {
                AnalyticsProvider.getInstance().logEvent("intentGeoSchemaUrl", null)
                val lat = intent.latitude
                val lon = intent.longitude
                try {
                    check(Geocoder.isPresent())
                    useGeocoderToGetAddress(
                        "$lat,$lon",
                        mainActivity,
                        fallbackLat = lat,
                        fallbackLon = lon,
                    )
                } catch (_: Exception) {
                    publishLatLon(lat, lon)
                }
            }

            is IncomingIntent.OpenLocation -> {
                AnalyticsProvider.getInstance().logEvent("intentShareMarker", null)
                bus.publish(intent)
            }

            is IncomingIntent.OpenFeature -> {
                AnalyticsProvider.getInstance().logEvent("intentOpenFeature", null)
                bus.publish(intent)
            }

            IncomingIntent.StopRoute -> {
                AnalyticsProvider.getInstance().logEvent("intentStopRoute", null)
                bus.publish(intent)
            }

            is IncomingIntent.StartRouteByName -> {
                AnalyticsProvider.getInstance().logEvent("intentStartRoute", null)
                bus.publish(intent)
            }

            is IncomingIntent.StartRoute,
            is IncomingIntent.ImportRoute -> {
                bus.publish(intent)
            }
        }
    }

    /**
     * Debug-only GPX replay, used to drive the app over a known route without leaving the desk -
     * for manual testing, and to produce reproducible recordings of the app's audio for the
     * tutorial guides. Declared in app/src/debug's manifest, so release builds never see it.
     *
     *   adb push walk.gpx /sdcard/Android/data/org.scottishtecharmy.soundscape/files/gpx/
     *   adb shell am start -a org.scottishtecharmy.soundscape.REPLAY_GPX \
     *       -e gpx walk.gpx --ef speed 1.4 --ez loop false
     *
     * The app-specific external files directory is used because it needs no storage permission,
     * and the app asks for none. An absolute path is accepted too, for somewhere already readable.
     * Omitting the gpx extra stops a replay and hands the providers back to the phone.
     */
    private fun replayGpx(intent: Intent, mainActivity: MainActivity) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "Ignoring $ACTION_REPLAY_GPX in a non-debug build")
            return
        }

        val name = intent.getStringExtra("gpx")
        if (name.isNullOrEmpty()) {
            Log.d(TAG, "Stopping GPX replay")
            mainActivity.soundscapeServiceConnection.setGpxPlaybackMode(
                gpxFile = null,
                speedMetresPerSecond = GpxDrivenProvider.DEFAULT_SPEED_MPS,
                loop = false,
            )
            return
        }

        val file =
            if (name.startsWith("/")) File(name)
            else File(mainActivity.getExternalFilesDir(GPX_DIRECTORY), name)

        // `am start --ef speed 1.4` gives a float, `-e speed 1.4` a string; accept either so the
        // command line doesn't have to be remembered exactly.
        // Branch on the extra's actual type rather than probing with getFloatExtra: `--ef speed`
        // gives a float and `-e speed` a string, and a default-valued probe cannot tell "absent"
        // from a genuine zero - which matters, because zero means stand still.
        //
        // Rounded because widening the float to a double otherwise turns 1.4 into
        // 1.399999976158142, which then appears in every log line about the replay.
        val speed = if (!intent.hasExtra("speed")) {
            GpxDrivenProvider.DEFAULT_SPEED_MPS
        } else {
            // getStringExtra returns null when the extra isn't a string, so this tries `-e` first
            // and falls through to `--ef`; a float extra reads back exactly, zero included.
            intent.getStringExtra("speed")?.toDoubleOrNull()
                ?: round(
                    intent.getFloatExtra(
                        "speed", GpxDrivenProvider.DEFAULT_SPEED_MPS.toFloat()
                    ).toDouble() * 100.0
                ) / 100.0
        }
        val loop = intent.getBooleanExtra("loop", false) ||
                intent.getStringExtra("loop")?.toBoolean() == true

        Log.d(TAG, "Replaying ${file.path} at $speed m/s, loop=$loop")
        val started = mainActivity.soundscapeServiceConnection.setGpxPlaybackMode(file, speed, loop)
        if (!started) {
            Log.e(TAG, "Failed to start GPX replay of ${file.path}")
        }
    }

    private fun importContentUri(intent: Intent, mainActivity: MainActivity) {
        val uri = intent.data ?: return
        Log.d(TAG, "Import data from content ${uri.path}")
        try {
            val input = mainActivity.contentResolver.openInputStream(uri) ?: return
            input.use { stream ->
                if (stream.available() > 1_000_000) throw Exception("File too large")
                val text = stream.bufferedReader().readText()
                val imported =
                    if (intent.type == "application/json" || intent.type == "application/octet-stream") {
                        IntentParser.parseRouteJson(text) ?: IntentParser.parseGpx(text)
                    } else {
                        IntentParser.parseGpx(text) ?: IntentParser.parseRouteJson(text)
                    }
                if (imported != null) {
                    AnalyticsProvider.getInstance().logEvent("intentJsonImport", null)
                    bus.publish(imported)
                } else {
                    Log.w(TAG, "Failed to parse route file from content uri")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file from intent: $e")
        }
    }

    companion object {
        private const val TAG = "SoundscapeIntents"

        /** Debug-only GPX replay - see [replayGpx]. */
        const val ACTION_REPLAY_GPX = "org.scottishtecharmy.soundscape.REPLAY_GPX"

        /** Subdirectory of the app's external files directory that replay tracks are read from. */
        const val GPX_DIRECTORY = "gpx"
    }
}
