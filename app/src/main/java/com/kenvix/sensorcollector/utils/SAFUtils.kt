package com.kenvix.sensorcollector.utils

import android.content.Context
import android.net.Uri

fun Context.getFileSize(uri: Uri): Long {
    return contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
        parcelFileDescriptor.statSize
    } ?: 0
}
