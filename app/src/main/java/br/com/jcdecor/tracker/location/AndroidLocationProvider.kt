package br.com.jcdecor.tracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import br.com.jcdecor.tracker.tracking.RawFix
import br.com.jcdecor.tracker.tracking.TrackingConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class AndroidLocationProvider(
    context: Context,
) : LocationProvider {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private var callback: LocationCallback? = null
    private var singleToken: CancellationTokenSource? = null

    @SuppressLint("MissingPermission")
    override fun start(onUpdate: (RawFix) -> Unit): LocationStartResult {
        if (!hasLocationPermission()) return LocationStartResult.MISSING_PERMISSION
        if (!playServicesAvailable()) return LocationStartResult.PLAY_SERVICES_UNAVAILABLE
        stop()
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TrackingConfig.LOCATION_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(TrackingConfig.MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(TrackingConfig.MIN_DISTANCE_METERS)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location -> onUpdate(location.toRawFix()) }
            }
        }
        callback = locationCallback
        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        requestSingle(onUpdate)
        return LocationStartResult.STARTED
    }

    @SuppressLint("MissingPermission")
    override fun requestSingle(onUpdate: (RawFix) -> Unit) {
        if (!hasLocationPermission() || !playServicesAvailable()) return
        singleToken?.cancel()
        val tokenSource = CancellationTokenSource()
        singleToken = tokenSource
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) onUpdate(location.toRawFix())
            }
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        singleToken?.cancel()
        singleToken = null
    }

    private fun hasLocationPermission(): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine
    }

    private fun playServicesAvailable(): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        return status == ConnectionResult.SUCCESS
    }
}

private fun android.location.Location.toRawFix(): RawFix = RawFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else -1f,
    speedMetersPerSecond = if (hasSpeed()) speed else null,
    bearingDegrees = if (hasBearing()) bearing else null,
    altitudeMeters = if (hasAltitude()) altitude else null,
    recordedAtEpochMs = time,
)
