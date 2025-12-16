package org.scottishtecharmy.soundscape.utils

import android.location.Address
import org.json.JSONObject
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.screens.home.data.LocationDescription
import org.woheller69.AndroidAddressFormatter.AndroidAddressFormatter

fun Feature.toLocationDescription(): LocationDescription? =
    properties?.let { properties ->
        // We use the AndroidAddressFormatter library to try and generate addresses which are
        // locale correct e.g. street numbers before/after street name
        // It's got a clunky API that takes in JSON which might be a problem if any of our
        // strings aren't JSON friendly. It would be better to have an API which took in the

        val formatter = AndroidAddressFormatter(false, true, false)
        val jsonObject = JSONObject()
        properties.forEach { (key, value) ->
            when (key) {
                "countrycode" -> jsonObject.put("country_code", value.toString())
                "housenumber" -> jsonObject.put("house_number", value.toString())
                "street" -> jsonObject.put("road", value.toString())
                "district" -> jsonObject.put("neighbourhood", value.toString())
                "city",
                "postcode",
                "county",
                "state",
                "country" -> jsonObject.put(key, value.toString())
            }
        }
        var json = jsonObject.toString()
        json = json.replace("\\/", "/")
        println(json)
        val formattedAddress = formatter.format(json)
        LocationDescription(
            name = properties["name"]?.toString() ?: formattedAddress.substringBefore('\n'),
            description = formattedAddress,
            location = (geometry as Point).coordinates
        )
    }

fun Address.toLocationDescription(name: String?): LocationDescription {

    val formatter = AndroidAddressFormatter(false, true, false)
    val jsonObject = JSONObject()
    if (countryName != null) jsonObject.put("country", countryName)
    if (countryCode != null) jsonObject.put("country_code", countryCode)
    if (subThoroughfare != null) jsonObject.put("house_number", subThoroughfare)
    if (thoroughfare != null) jsonObject.put("road", thoroughfare)
    if (subLocality != null) jsonObject.put("neighbourhood", subLocality)
    if (locality != null) jsonObject.put("city", locality)
    if (postalCode != null) jsonObject.put("postcode", postalCode)
    if (subAdminArea != null) jsonObject.put("county", subAdminArea)
    if (adminArea != null) jsonObject.put("state", adminArea)

    var json = jsonObject.toString()
    json = json.replace("\\/", "/")

    val formattedAddress = formatter.format(json)
    return LocationDescription(
        name = name ?: formattedAddress.substringBefore('\n'),
        description = formattedAddress,
        location = LngLatAlt(longitude, latitude)
    )
}