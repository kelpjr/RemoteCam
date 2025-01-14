package com.samsung.android.scan3d.locationServices

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Camera
import android.location.Location
import android.net.ConnectivityManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
//import com.htx.locator.MainActivity
//import com.htx.locator.R
//import com.htx.locator.webserver.WebServer
import com.samsung.android.scan3d.CameraActivity
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.webserver.WebServer
//import com.htx.locator.webserver.WebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.Inet4Address
import java.net.NetworkInterface


class LocationService: Service() {


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private  lateinit var locationClient: LocationClient
//    val port = 9000
//    var webServer : WebServer? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action){
            ACTION_START -> {
                webServer?.start()

                start { location ->
                    webServer?.updateLatestLocation(location)
                }
            }
            ACTION_STOP -> {
                Log.d("WEBSERVER", "onStartCommand: Attempting to stop server")
                stop()
                webServer?.stop()
            }

        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun getLocalIPAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val networkInterface = en.nextElement()
                val enu = networkInterface.inetAddresses
                while (enu.hasMoreElements()) {
                    val inetAddress = enu.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return null
    }
    fun getIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var ipAddr = ""

        val linkAddresses = connectivityManager?.getLinkProperties(connectivityManager.activeNetwork)?.linkAddresses

        val ipV4Address = linkAddresses?.firstOrNull { linkAddress ->
            linkAddress.address.hostAddress?.contains('.') ?: false }?.address?.hostAddress
        return ipV4Address
    }


    @SuppressLint("WrongConstant")
    private fun start(locationUpdateCallback: (Location) -> Unit ){
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existingActivityIntent = Intent(this, CameraActivity::class.java)
        existingActivityIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingExistingActivityIntent = PendingIntent.getActivity(
            this,
            0,
            existingActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = "ACTION_STOP"
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "location")
            .setContentTitle("Location tracking is active!")
            .setContentText("Getting location...")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.baseline_stop_24, "Stop", pendingStopIntent)
            .setContentIntent(pendingExistingActivityIntent)


        locationClient
            .getLocationUpdates(1000L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                locationUpdateCallback(location)
                val lat = location.latitude.toString()
                val long = location.longitude.toString()
                val ipAddr = getLocalIPAddress()
                val ipAddr2 = getIpAddress(this)
                val port = webServer?.getPort()
                val updatedNotification = notification.setContentText(
                    "Location: ($lat, $long)\n" +
                    "Address: ${ipAddr2}:${port}"
                )
                notificationManager.notify(1, updatedNotification.build())
            }
            .launchIn(serviceScope)


        startForeground(1, notification.build())
    }

    private fun stop(){
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        lateinit var webServer: WebServer
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }


}

