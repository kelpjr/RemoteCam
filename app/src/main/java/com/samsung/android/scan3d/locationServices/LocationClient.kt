package com.samsung.android.scan3d.locationServices

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationClient{
    fun getLocationUpdates(interval: Long): Flow<Location>

    class LocationException(message: String): Exception()


}