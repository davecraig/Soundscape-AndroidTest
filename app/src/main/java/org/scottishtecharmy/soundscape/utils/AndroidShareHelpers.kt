package org.scottishtecharmy.soundscape.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider.getUriForFile
import org.scottishtecharmy.soundscape.MainActivity
import org.scottishtecharmy.soundscape.database.local.model.RouteWithMarkers
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date

fun goToAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.fromParts("package", "org.scottishtecharmy.soundscape", null)
    context.startActivity(intent)
}

fun shareLocation(context: Context, message: String, locationDescription: LocationDescription) {
    val location = locationDescription.location
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TITLE, locationDescription.name)
        val latitude = "%.5f".format(location.latitude)
        val longitude = "%.5f".format(location.longitude)
        val soundscapeUrl =
            "https://links.soundscape.scottishtecharmy.org/v1/sharemarker?" +
                "lat=$latitude&lon=$longitude&name=" +
                URLEncoder.encode(locationDescription.name, Charsets.UTF_8.name())
        val googleMapsUrl = "https://www.google.com/maps/?q=$latitude,$longitude"
        putExtra(
            Intent.EXTRA_TEXT,
            message.format(locationDescription.name, soundscapeUrl, googleMapsUrl)
        )
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

fun shareRoute(context: Context, route: RouteWithMarkers) {
    val routeStorageDir = File("${context.filesDir}/route/").apply { if (!exists()) mkdirs() }
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm").format(Date())
    val safeName = route.route.name.replace(Regex("[/\\\\:*?\"<>|\\x00]"), "_").take(100)
    val outputFile = File(routeStorageDir, "soundscape-route-$safeName-$timeStamp.json")
    outputFile.writeText(routeToShareJson(route))
    val uri: Uri = getUriForFile(context, "${context.packageName}.provider", outputFile)
    (context as MainActivity).shareRoute(uri)
}
