package com.samsung.android.scan3d.webserver

import android.location.Location
import android.util.Log
import io.ktor.server.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.serialization.gson.*
import io.ktor.http.ContentType
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.io.OutputStream

class WebServer(private val port:Int= 9000) {
    private var latestLocation: Location? = null

    fun getPort(): Int {
        return port
    }


    fun updateLatestLocation(location: Location) {
        latestLocation = location
    }

    private fun Route.shutdownRoute(){
        get("/shutdown") {
            stop() // Gracefully stops the server
            call.respond(HttpStatusCode.OK, "Server shutting down")
        }
    }
    var channel = Channel<ByteArray>(2)
    fun producer(): suspend OutputStream.() -> Unit = {
        val o = this
        channel = Channel()
        channel.consumeEach {
            o.write("--FRAME\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            o.write(it)
            o.flush()
        }
    }
    private val server = embeddedServer(Netty, port) {
        install(ContentNegotiation) {
            gson {}
        }
        install(StatusPages)

        routing {
            data class LocationData(val latitude: Double, val longitude: Double)
            shutdownRoute()
            get("/location") {
                val location = latestLocation
                if (location != null) {
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    Log.d("LOCATION", "currentLocation: $locationData")
                    call.respond(
                        mapOf(
                            "lat" to locationData.latitude.toString(),
                            "long" to locationData.longitude.toString()
                        )
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound, "Location data not available")
                }
            }
            get("/") {
                call.respond(mapOf("message" to "Hello world"))
            }
            get("/cam") {
                call.respondText("Ok")
            }
            get("/cam.mjpeg") {
                call.respondOutputStream(
                    ContentType.parse("multipart/x-mixed-replace;boundary=FRAME"),
                    HttpStatusCode.OK,
                    producer()
                )
            }
        }
    }

    fun start() {
        CoroutineScope(coroutineContext).launch {
            Log.d("SERVER Started","Starting server...")
            server.start(wait = true)
        }

    }

    fun stop() {
        server.stop(10, 15, TimeUnit.MILLISECONDS)
    }

}