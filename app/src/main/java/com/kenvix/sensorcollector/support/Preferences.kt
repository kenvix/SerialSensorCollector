package com.kenvix.sensorcollector.support

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "settings")

val Context.bluetoothFavoriteDevicesDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "bluetooth_favorite_devices")

fun main() {

}
