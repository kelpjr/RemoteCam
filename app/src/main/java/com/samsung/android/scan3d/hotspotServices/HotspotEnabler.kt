package com.samsung.android.scan3d.hotspotServices

import android.content.Context
import android.os.Build

interface HotspotEnabler {

    fun enableTethering()
    fun disableTethering()

}

fun createHotspotEnabler(context: Context): HotspotEnabler =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            QHotspotEnabler(context)
        }
        else -> {
            UpToPHotspotEnabler(context)
        }
    }
