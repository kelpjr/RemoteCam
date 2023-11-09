package com.samsung.android.scan3d.hotspotServices
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
//import com.crashlytics.android.Crashlytics
import java.lang.reflect.Method

class UpToPHotspotEnabler(private val context: Context) : HotspotEnabler {

    override fun enableTethering() {
        setTetheringStatus(context, true)
    }

    override fun disableTethering() {
        setTetheringStatus(context, false)
    }

    private fun setTetheringStatus(context: Context, enabled: Boolean) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        try {
            val method: Method = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(wifiManager, null, enabled) // true to enable, false to disable
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()

//            Crashlytics.log("${UpToPHotspotEnabler::class.java.simpleName}. Error while setting tethering to:$enabled, SDK:${Build.VERSION.SDK_INT}")
//            Crashlytics.logException(e)
        }
    }

}