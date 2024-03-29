package com.kenvix.sensorcollector.utils

import android.bluetooth.le.ScanCallback

fun getScanFailureMessage(errorCode: Int): String {
    return when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan failed: already started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed: app registration failed"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed: feature unsupported"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Scan failed: internal error"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scan failed: scanning too frequently"
        else -> "Unknown scan failure error: $errorCode"
    }
}
