package org.scottishtecharmy.soundscape.screens.markers_routes.screens

import android.content.Context

// SharedPreferences Helper Functions
fun saveSortOrderPreference(context: Context, isSortByName: Boolean) {
    val sharedPreferences = context.getSharedPreferences("SORT_ORDER_PREFS", Context.MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        putBoolean("SORT_ORDER_KEY", isSortByName)
        apply()
    }
}

fun getSortOrderPreference(context: Context): Boolean {
    val sharedPreferences = context.getSharedPreferences("SORT_ORDER_PREFS", Context.MODE_PRIVATE)
    return sharedPreferences.getBoolean("SORT_ORDER_KEY", false) // Default to false (unsorted)
}